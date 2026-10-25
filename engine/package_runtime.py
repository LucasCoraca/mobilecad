"""Stage the FreeCAD + CPython runtime into the Android app.

Usage: python3 -I package_runtime.py <abi> <app_dir>   (writes the per-ABI flavor source set: app/src/{x86,arm64}/)

  assets/python/                 CPython stdlib (+ lib-dynload extension modules), trimmed
  assets/freecad-runtime/lib/    FreeCAD libs + Python modules (FreeCAD.so, Part.so, ...) + all dependency .so files
  assets/freecad-runtime/Mod/    FreeCAD module Python code / data
  assets/freecad-runtime/bridge/ mobilecad bridge package
  assets/freecad-runtime/preload.txt   libraries in dependency order (dlopen'd by fcbridge before Python starts)
  jniLibs/<abi>/                 libpython, CPython's openssl/sqlite, libc++_shared (fcbridge itself comes from CMake)
"""
import os, re, shutil, subprocess, sys

abi, app = sys.argv[1], sys.argv[2]
# same defaults / environment overrides as engine/env.sh
home = os.path.expanduser('~/Android')
FCDEPS = os.environ.get('FCDEPS') or os.path.join(home, 'fcdeps')
P = os.path.join(FCDEPS, abi)
NDK = os.environ.get('ANDROID_NDK') or os.path.join(os.environ.get('ANDROID_HOME') or os.path.join(home, 'sdk'), 'ndk/29.0.14206865')
FREECAD_SRC = os.environ.get('FREECAD_SRC') or os.path.join(home, 'freecad-src')
if not os.path.isdir(FREECAD_SRC): FREECAD_SRC = os.path.join(FCDEPS, 'src', 'freecad')
READELF = os.path.join(NDK, 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf')
TRIPLE = {'x86_64': 'x86_64-linux-android', 'arm64-v8a': 'aarch64-linux-android'}[abi]
FLAVOR = {'x86_64': 'x86', 'arm64-v8a': 'arm64'}[abi]          # one Gradle product flavor per ABI
assets = os.path.join(app, 'app/src', FLAVOR, 'assets')
jni = os.path.join(app, 'app/src', FLAVOR, 'jniLibs', abi)
rt = os.path.join(assets, 'freecad-runtime')
here = os.path.dirname(os.path.abspath(__file__))

def reset(d):
    shutil.rmtree(d, ignore_errors=True); os.makedirs(d)

# ---------------------------------------------------------------- CPython
py = os.path.join(P, 'python/prefix')
stdlib = os.path.join(py, 'lib/python3.14')
reset(os.path.join(assets, 'python'))
SKIP = {'test', 'idlelib', 'tkinter', 'turtledemo', 'ensurepip', 'lib2to3', '__pycache__', 'pydoc_data', 'venv'}
def ignore(d, names):
    return [n for n in names if n in SKIP or n.endswith(('.pyc', '.exe'))]
shutil.copytree(stdlib, os.path.join(assets, 'python/lib/python3.14'), ignore=ignore, dirs_exist_ok=True)
# aapt decompresses *.gz assets; keep them intact (Engine.extract strips the trailing '-')
for dp, _, fs in os.walk(os.path.join(assets, 'python')):
    for f in fs:
        if f.endswith('.gz') or f.endswith('-'):
            os.rename(os.path.join(dp, f), os.path.join(dp, f + '-'))

reset(jni)
for f in os.listdir(os.path.join(py, 'lib')):
    if re.match(r'libpython3\.\d+\.so$', f) or f.endswith('_python.so'):
        shutil.copy(os.path.join(py, 'lib', f), jni)
shutil.copy(os.path.join(NDK, 'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib', TRIPLE, 'libc++_shared.so'), jni)
# LLVM OpenMP runtime (FEM / SMESH are compiled with -fopenmp)
OMP = os.path.join(NDK, 'toolchains/llvm/prebuilt/linux-x86_64/lib/clang/21/lib/linux', {'x86_64': 'x86_64', 'arm64-v8a': 'aarch64'}[abi], 'libomp.so')

# ---------------------------------------------------------------- FreeCAD install tree + dependency libraries
fc = os.path.join(P, 'freecad')
reset(rt)
for sub in ('Mod', 'Ext', 'share', 'data'):
    s = os.path.join(fc, sub)
    if os.path.isdir(s):
        # keep data resources (material library, thread/hole tables, templates); drop icons + translations (the app uses its own)
        # and the es-AR TechDraw templates, which are CC-BY-NC-SA (non-commercial) unlike the rest of FreeCAD
        shutil.copytree(s, os.path.join(rt, sub), ignore=shutil.ignore_patterns('__pycache__', '*.pyc', 'icons', 'translations', '*.qm', '*.ts', 'doc', '*tests', 'Test*', 'es-AR'), dirs_exist_ok=True)
# the `freecad` namespace package (freecad.deprecation, freecad.utils ...): CMake installs it into the *host* Python's
# site-packages when INSTALL_TO_SITEPACKAGES is on, so take it from the build tree
shutil.copytree(os.path.join(FCDEPS, 'build-freecad-' + abi, 'Ext', 'freecad'), os.path.join(rt, 'Ext', 'freecad'),
                ignore=shutil.ignore_patterns('__pycache__'), dirs_exist_ok=True)
# App-level Python feature modules that CMake only installs with BUILD_GUI (their Gui imports are guarded by GuiUp)
FCSRC = os.path.join(FREECAD_SRC, 'src', 'Mod')
for m, fs in {'PartDesign': ('InvoluteGearFeature.py', 'SprocketFeature.py')}.items():
    for f in fs: shutil.copy(os.path.join(FCSRC, m, f), os.path.join(rt, 'Mod', m, f))
# every Python command module the app runs (mobilecad/command_modules.py) that CMake installs only with BUILD_GUI
# (MeshPart/Gui/MeshFlatteningCommand.py ...)
import ast as _ast
_cm = open(os.path.join(here, 'python', 'mobilecad', 'command_modules.py')).read()
for wb_, rel_ in set(_ast.literal_eval(_cm[_cm.index('{'):_cm.rindex('}') + 1]).values()):
    src_, dst_ = os.path.join(FCSRC, wb_, rel_), os.path.join(rt, 'Mod', wb_, rel_)
    if os.path.exists(src_) and not os.path.exists(dst_) and os.path.isdir(os.path.join(rt, 'Mod', wb_)):
        os.makedirs(os.path.dirname(dst_), exist_ok=True); shutil.copy(src_, dst_)
# TemplatePyMod (FreeCAD's Python example workbench; CMake installs it only with BUILD_TEMPLATE)
shutil.copytree(os.path.join(FCSRC, 'TemplatePyMod'), os.path.join(rt, 'Mod', 'TemplatePyMod'), dirs_exist_ok=True,
                ignore=shutil.ignore_patterns('CMakeLists.txt', '__pycache__'))
# InitGui.py of every built workbench: several define commands (group commands, CAM toolbars) the app runs through the
# headless FreeCADGui; CMake installs some of them only with BUILD_GUI
for m in os.listdir(os.path.join(rt, 'Mod')):
    src = os.path.join(FCSRC, m, 'InitGui.py'); dst = os.path.join(rt, 'Mod', m, 'InitGui.py')
    if os.path.exists(src) and not os.path.exists(dst): shutil.copy(src, dst)
    # top-level modules the install list leaves out but commands still reference (e.g. BIM/ArchFloor.py)
    if os.path.isdir(os.path.join(FCSRC, m)):
        for f in os.listdir(os.path.join(FCSRC, m)):
            if f.endswith('.py') and not f.startswith('Test') and not os.path.exists(os.path.join(rt, 'Mod', m, f)):
                shutil.copy(os.path.join(FCSRC, m, f), os.path.join(rt, 'Mod', m, f))
        # Python GUI packages (femtaskpanels, femguiutils, femviewprovider ...): task panels run on the Qt emulation
        for d in os.listdir(os.path.join(FCSRC, m)):
            sd = os.path.join(FCSRC, m, d)
            if d not in ('Gui', 'App') and not d.endswith(('test', 'tests')) and os.path.exists(os.path.join(sd, '__init__.py')) \
                    and not os.path.exists(os.path.join(rt, 'Mod', m, d)):
                shutil.copytree(sd, os.path.join(rt, 'Mod', m, d), ignore=shutil.ignore_patterns('__pycache__', '*.ts', '*.qm', '*.svg', '*.png', 'CMakeLists.txt'))
lib = os.path.join(rt, 'lib'); os.makedirs(lib)
shutil.copy(OMP, lib)
for f in os.listdir(os.path.join(fc, 'lib')):
    src = os.path.join(fc, 'lib', f)
    if os.path.isfile(src) and (f.endswith('.so') or '.so.' in f or f.endswith('.py')):
        shutil.copy(src, lib)
    elif os.path.isdir(src) and f not in ('cmake', 'pkgconfig'):
        shutil.copytree(src, os.path.join(lib, f), dirs_exist_ok=True)
for f in os.listdir(os.path.join(P, 'lib')):
    src = os.path.join(P, 'lib', f)
    if os.path.isfile(src) and not os.path.islink(src) and re.search(r'\.so(\.\d+)*$', f):
        soname = subprocess.run([READELF, '-d', src], capture_output=True, text=True).stdout
        m = re.search(r'\(SONAME\)\s+Library soname: \[(.+?)\]', soname)
        shutil.copy(src, os.path.join(lib, m.group(1) if m else f))
shutil.copytree(os.path.join(here, 'python', 'mobilecad'), os.path.join(rt, 'bridge', 'mobilecad'), ignore=shutil.ignore_patterns('__pycache__'))
shutil.copytree(os.path.join(here, 'python', 'shims'), os.path.join(rt, 'bridge'), ignore=shutil.ignore_patterns('__pycache__'), dirs_exist_ok=True)
# pure-Python third-party modules FreeCAD imports (typing_extensions 4.15 for freecad.deprecation)
shutil.copytree(os.path.join(here, 'python', 'vendor'), os.path.join(rt, 'bridge'), ignore=shutil.ignore_patterns('__pycache__'), dirs_exist_ok=True)
# IfcOpenShell (engine/build_ifcopenshell.sh): the ifcopenshell package with its SWIG wrapper, for BIM's IFC / NativeIFC
ios = os.path.join(P, 'ifcopenshell', 'python', 'ifcopenshell')
if os.path.isdir(ios):
    shutil.copytree(ios, os.path.join(rt, 'bridge', 'ifcopenshell'), ignore=shutil.ignore_patterns('__pycache__', 'test', 'tests'), dirs_exist_ok=True)
    for f in os.listdir(os.path.join(rt, 'bridge', 'ifcopenshell')):          # CMake left Python_SOABI empty: "..so"
        if f.startswith('_ifcopenshell_wrapper.') and f.endswith('.so') and f != '_ifcopenshell_wrapper.so':
            os.replace(os.path.join(rt, 'bridge', 'ifcopenshell', f), os.path.join(rt, 'bridge', 'ifcopenshell', '_ifcopenshell_wrapper.so'))
# Qt resource files App-level Python reads through QFile(":/ui/...") (Draft/BIM preference pages -> parameter defaults)
os.makedirs(os.path.join(rt, 'bridge', 'qtres', 'ui'), exist_ok=True)
for m in ('Draft', 'BIM'):
    d = os.path.join(FCSRC, m, 'Resources', 'ui')
    for f in os.listdir(d):
        if f.startswith('preferences'): shutil.copy(os.path.join(d, f), os.path.join(rt, 'bridge', 'qtres', 'ui', f))
# .ui forms modules load from their own directory (SprocketFeature.ui, InvoluteGearFeature.ui, CAM/FEM panels ...)
for dp, _, fs in os.walk(FCSRC):
    for f in fs:
        if f.endswith('.ui') and os.sep + 'Gui' not in dp[len(FCSRC):]:
            dst = os.path.join(rt, 'Mod', os.path.relpath(dp, FCSRC))
            if os.path.isdir(dst): shutil.copy(os.path.join(dp, f), dst)
# Gui/Resources/ui/*.ui that desktop CMake installs as Mod/<m>/Resources/ui (FEM task panels load them by path)
for m in os.listdir(FCSRC):
    gui_ui = os.path.join(FCSRC, m, 'Gui', 'Resources', 'ui')
    if os.path.isdir(gui_ui) and os.path.isdir(os.path.join(rt, 'Mod', m)):
        os.makedirs(os.path.join(rt, 'Mod', m, 'Resources', 'ui'), exist_ok=True)
        for f in os.listdir(gui_ui):
            if f.endswith('.ui'): shutil.copy(os.path.join(gui_ui, f), os.path.join(rt, 'Mod', m, 'Resources', 'ui', f))
# every .ui form the modules' .qrc files compile in (":/panels/...", ":/ui/...") for the task-panel emulation (PySide/_ui.py)
import xml.etree.ElementTree as ET
for dp, _, fs in os.walk(FCSRC):
    for f in fs:
        if not f.endswith('.qrc'): continue
        for q in ET.parse(os.path.join(dp, f)).getroot().iter('qresource'):
            pre = q.get('prefix', '/').strip('/')
            for fe in q.iter('file'):
                if fe.text.endswith('.ui'):
                    dst = os.path.join(rt, 'bridge', 'qtres', pre, fe.get('alias') or fe.text)
                    os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copy(os.path.join(dp, fe.text), dst)

# Gmsh (engine/build_gmsh.sh) as a native library: the only place Android lets an app execute a program from
jni = os.path.join(app, 'app', 'src', {'x86_64': 'x86', 'arm64-v8a': 'arm64'}[abi], 'jniLibs', abi)
# CalculiX (engine/build_ccx.sh): ccx as libccx.so (ARPACK, OpenBLAS and the Fortran runtime are linked in statically)
for exe, src_ in (('libgmsh.so', os.path.join(P, 'gmsh', 'gmsh')), ('libccx.so', os.path.join(P, 'ccx', 'ccx'))):
    if os.path.exists(src_):
        os.makedirs(jni, exist_ok=True); shutil.copy(src_, os.path.join(jni, exe))
        subprocess.run([os.path.join(NDK, 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'), '--strip-unneeded', os.path.join(jni, exe)], check=True)
# Qt's Java side (QtNative) — QtCore's Android backend needs it
os.makedirs(os.path.join(app, 'app/libs'), exist_ok=True)
shutil.copy(os.path.join(P, 'jar', 'Qt6Android.jar'), os.path.join(app, 'app/libs', 'Qt6Android.jar'))

# ---------------------------------------------------------------- strip debug info (the NDK toolchain compiles with -g)
STRIP = os.path.join(NDK, 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip')
for dp, _, fs in os.walk(rt):
    for f in fs:
        if re.search(r'\.so(\.\d+)*$', f): subprocess.run([STRIP, '--strip-unneeded', os.path.join(dp, f)], check=True)
for f in os.listdir(jni):
    subprocess.run([STRIP, '--strip-unneeded', os.path.join(jni, f)], check=True)
for dp, _, fs in os.walk(os.path.join(assets, 'python')):
    for f in fs:
        if f.endswith('.so'): subprocess.run([STRIP, '--strip-unneeded', os.path.join(dp, f)], check=True)

# ---------------------------------------------------------------- preload order (DT_NEEDED topological sort)
libs = {f: os.path.join(lib, f) for f in os.listdir(lib) if os.path.isfile(os.path.join(lib, f)) and re.search(r'\.so(\.\d+)*$', f)}
needed = {}
for f, p in libs.items():
    out = subprocess.run([READELF, '-d', p], capture_output=True, text=True).stdout
    needed[f] = [n for n in re.findall(r'\(NEEDED\)\s+Shared library: \[(.+?)\]', out) if n in libs]
order, state = [], {}
def visit(f):
    if state.get(f) == 2: return
    if state.get(f) == 1: return   # cycle guard
    state[f] = 1
    for n in needed[f]: visit(n)
    state[f] = 2; order.append(f)
for f in sorted(libs): visit(f)
open(os.path.join(rt, 'preload.txt'), 'w').write('\n'.join(order) + '\n')

# ---------------------------------------------------------------- DF_1_GLOBAL
# bionic only puts a library in the global lookup group when its own DT_FLAGS_1 has DF_1_GLOBAL (dlopen's RTLD_GLOBAL is
# not enough).  Without it every module binds its own weak copy of inline-class typeinfo (Py::Exception, OCCT
# Standard_Failure subclasses ...) and libc++abi, which matches catch clauses by typeinfo address, lets those
# exceptions escape across library boundaries -> std::terminate.
import struct
def set_df1_global(path):
    with open(path, 'r+b') as fh:
        e = fh.read(64)
        if e[:4] != b'\x7fELF' or e[4] != 2: return False
        phoff, = struct.unpack_from('<Q', e, 32); phentsize, phnum = struct.unpack_from('<HH', e, 54)
        fh.seek(phoff); ph = fh.read(phentsize * phnum)
        for i in range(phnum):
            ptype, = struct.unpack_from('<I', ph, i * phentsize)
            if ptype != 2: continue                               # PT_DYNAMIC
            off, = struct.unpack_from('<Q', ph, i * phentsize + 8); size, = struct.unpack_from('<Q', ph, i * phentsize + 32)
            fh.seek(off); dyn = bytearray(fh.read(size))
            ents = [struct.unpack_from('<qQ', dyn, j) for j in range(0, len(dyn), 16)]
            idx = next((k for k, (t, _) in enumerate(ents) if t == 0x6ffffffb), None)
            if idx is None:                                       # no DT_FLAGS_1: use a spare trailing DT_NULL
                nulls = [k for k, (t, _) in enumerate(ents) if t == 0]
                if len(nulls) < 2: return False
                idx = nulls[0]; ents[idx] = (0x6ffffffb, 0)
            t, v = ents[idx]; struct.pack_into('<qQ', dyn, idx * 16, t, v | 0x2)
            fh.seek(off); fh.write(dyn); return True
    return False
flagged = sum(set_df1_global(p) for p in libs.values())
print('DF_1_GLOBAL set on %d/%d libs' % (flagged, len(libs)))

size = lambda d: sum(os.path.getsize(os.path.join(a, f)) for a, _, fs in os.walk(d) for f in fs) // (1 << 20)
print('python assets %d MB, freecad runtime %d MB (%d libs), jniLibs %d MB' % (size(os.path.join(assets, 'python')), size(rt), len(libs), size(jni)))
