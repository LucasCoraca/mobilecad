"""Third-party notices for the APK: assets/licenses/index.json + one text file per license, and the attribution list of
FreeCAD icons that carry their own Creative Commons / public-domain notice. Run before building a release
(publish.sh does). Texts come from the sources the runtime was built from."""
import io, json, os, re, tarfile, shutil
# same build root / source trees as engine/env.sh (FCDEPS, FREECAD_SRC, OCCT_SRC, ANDROID_NDK overrides)
H = os.path.expanduser('~/Android'); SRC = (os.environ.get('FCDEPS') or H + '/fcdeps') + '/src'
FC = os.environ.get('FREECAD_SRC') or H + '/freecad-src'
if not os.path.isdir(FC): FC = SRC + '/freecad'
OCC = os.environ.get('OCCT_SRC') or H + '/occt-src'
if not os.path.isdir(OCC): OCC = SRC + '/occt'
NDK = os.environ.get('ANDROID_NDK') or (os.environ.get('ANDROID_HOME') or H + '/sdk') + '/ndk/29.0.14206865'
R = os.path.dirname(os.path.abspath(__file__)); OUT = os.path.join(R, '..', 'app', 'app', 'src', 'main', 'assets', 'licenses')
TP = os.path.join(R, 'third_party')   # license texts of vendored Python packages (from their wheels)

def tar(path, member):
    with tarfile.open(path) as t:
        for m in t.getmembers():
            if m.name.lstrip('./') == member.lstrip('./'): return t.extractfile(m).read().decode('utf-8', 'replace')
    raise FileNotFoundError(member)
def file(p): return open(p, encoding='utf-8', errors='replace').read()

# (component, license, source, [(title, loader)]) — the components linked into or shipped with the app
C = [
 ('MobileCAD', 'GPL-3.0-or-later', 'the MobileCAD source archive on the download page', [('GPL-3.0', lambda: file(os.path.join(R, '..', 'LICENSE')))]),
 ('FreeCAD', 'LGPL-2.1-or-later', 'https://github.com/FreeCAD/FreeCAD', [('LGPL-2.1', lambda: file(FC + '/LICENSE'))]),
 ('Open CASCADE Technology', 'LGPL-2.1 with Open CASCADE exception', 'https://github.com/Open-Cascade-SAS/OCCT',
  [('LGPL-2.1', lambda: file(OCC + '/LICENSE_LGPL_21.txt')), ('Open CASCADE exception', lambda: file(OCC + '/OCCT_LGPL_EXCEPTION.txt'))]),
 ('Qt 6 (Core, Gui, Widgets, Xml, Network, OpenGL, PrintSupport, Concurrent)', 'LGPL-3.0-only', 'https://download.qt.io/archive/qt/6.8/6.8.3/submodules/',
  [('LGPL-3.0', lambda: tar(SRC + '/qtbase.tar.xz', 'qtbase-everywhere-src-6.8.3/LICENSES/LGPL-3.0-only.txt')),
   ('GPL-3.0 (referenced by LGPL-3.0)', lambda: tar(SRC + '/qtbase.tar.xz', 'qtbase-everywhere-src-6.8.3/LICENSES/GPL-3.0-only.txt'))]),
 ('Python 3.14', 'PSF-2.0', 'https://www.python.org/downloads/source/', [('Python license', lambda: tar(SRC + '/Python-3.14.4.tar.xz', 'Python-3.14.4/LICENSE'))]),
 ('NumPy 2.3.5', 'BSD-3-Clause (+ bundled)', 'https://github.com/numpy/numpy', [('NumPy license', lambda: file(SRC + '/numpy/numpy-2.3.5/LICENSE.txt')),
   ('NumPy bundled', lambda: file(SRC + '/numpy/numpy-2.3.5/LICENSES_bundled.txt'))]),
 ('Gmsh 4.13.1', 'GPL-2.0-or-later with linking exception', 'https://gmsh.info', [('Gmsh license', lambda: file(SRC + '/gmsh/gmsh-4.13.1-source/LICENSE.txt'))]),
 ('CalculiX CrunchiX 2.22', 'GPL-2.0', 'http://www.calculix.de', [('GPL-2.0', lambda: file(SRC + '/ccx/CalculiX/ccx_2.22/src/gpl-2.0.txt'))]),
 ('SPOOLES 2.2', 'Public domain', 'https://netlib.org/linalg/spooles/', []),
 ('SALOME SMESH (FreeCAD 3rdParty)', 'LGPL-2.1-or-later', 'https://github.com/FreeCAD/FreeCAD/tree/main/src/3rdParty/salomesmesh',
  [('LGPL-2.1', lambda: file(FC + '/src/3rdParty/salomesmesh/LICENCE.lgpl.txt'))]),
 ('OndselSolver', 'LGPL-2.1-or-later', 'https://github.com/Ondsel-Development/OndselSolver', [('OndselSolver license', lambda: file(FC + '/src/3rdParty/OndselSolver/LICENSE'))]),
 ('MED-fichier 4.1.0', 'LGPL-3.0-or-later', 'https://www.salome-platform.org', [('MED license', lambda: file(SRC + '/med/med-fichier-4.1.0+repack/COPYING.LESSER'))]),
 ('HDF5 1.10.11', 'BSD-3-Clause-style', 'https://github.com/HDFGroup/hdf5', [('HDF5 license', lambda: file(SRC + '/hdf5/hdf5-1.10.11/COPYING'))]),
 ('Boost 1.87', 'BSL-1.0', 'https://www.boost.org', [('Boost Software License', lambda: tar(SRC + '/boost.tar.gz', 'boost-1.87.0/LICENSE_1_0.txt'))]),
 ('{fmt} 11.1.4', 'MIT', 'https://github.com/fmtlib/fmt', [('fmt license', lambda: tar(SRC + '/fmt.tar.gz', 'fmt-11.1.4/LICENSE'))]),
 ('FreeType 2.13.3', 'FTL', 'https://freetype.org', [('FreeType License', lambda: tar(SRC + '/freetype.tar.xz', 'freetype-2.13.3/docs/FTL.TXT'))]),
 ('HarfBuzz 10.4.0', 'MIT-style', 'https://github.com/harfbuzz/harfbuzz', [('HarfBuzz license', lambda: tar(SRC + '/harfbuzz.tar.xz', 'harfbuzz-10.4.0/COPYING'))]),
 ('ICU 76', 'Unicode-3.0', 'https://icu.unicode.org', [('ICU license', lambda: tar(SRC + '/icu.tgz', 'icu/LICENSE'))]),
 ('Xerces-C 3.2.5', 'Apache-2.0', 'https://xerces.apache.org', [('Apache-2.0', lambda: tar(SRC + '/xerces.tar.gz', 'xerces-c-3.2.5/LICENSE'))]),
 ('yaml-cpp 0.8.0', 'MIT', 'https://github.com/jbeder/yaml-cpp', [('yaml-cpp license', lambda: tar(SRC + '/yaml.tar.gz', 'yaml-cpp-0.8.0/LICENSE'))]),
 ('Eigen 3.4.0', 'MPL-2.0', 'https://eigen.tuxfamily.org', [('MPL-2.0', lambda: tar(SRC + '/eigen.tar.gz', 'eigen-3.4.0/COPYING.MPL2'))]),
 ('OpenBLAS 0.3.34', 'BSD-3-Clause', 'https://github.com/OpenMathLib/OpenBLAS', [('OpenBLAS license', lambda: tar(SRC + '/openblas/OpenBLAS-0.3.34.tar.gz', 'OpenBLAS-0.3.34/LICENSE'))]),
 ('arpack-ng 3.9.1', 'BSD-3-Clause', 'https://github.com/opencollab/arpack-ng', [('arpack-ng license', lambda: tar(SRC + '/arpack/arpack-ng-3.9.1.tar.gz', 'arpack-ng-3.9.1/COPYING'))]),
 ('LLVM Flang runtime 21.1.8 (in CalculiX)', 'Apache-2.0 WITH LLVM-exception', 'https://github.com/llvm/llvm-project',
  [('flang-rt license', lambda: tar(SRC + '/llvm/llvm-project-21.1.8.src.tar.xz', 'llvm-project-21.1.8.src/flang-rt/LICENSE.TXT'))]),
 ('OpenSSL 3.0.19 (Python ssl / hashlib)', 'Apache-2.0', 'https://www.openssl.org', [('OpenSSL license', lambda: tar(SRC + '/openssl-3.0.19.tar.gz', 'openssl-3.0.19/LICENSE.txt'))]),
 ('SQLite 3.50.4 (Python sqlite3)', 'Public domain', 'https://sqlite.org', []),
 ('libffi 3.4.4 (Python ctypes)', 'MIT', 'https://github.com/libffi/libffi', [('libffi license', lambda: tar(SRC + '/libffi-3.4.4.tar.gz', 'libffi-3.4.4/LICENSE'))]),
 ('XZ Utils liblzma 5.4.6 (Python lzma)', 'Public domain', 'https://tukaani.org/xz/', [('XZ Utils copying', lambda: tar(SRC + '/xz-5.4.6.tar.gz', 'xz-5.4.6/COPYING'))]),
 ('Zstandard 1.5.7 (Python compression.zstd)', 'BSD-3-Clause', 'https://github.com/facebook/zstd', [('zstd license', lambda: tar(SRC + '/zstd-1.5.7.tar.gz', 'zstd-1.5.7/LICENSE'))]),
 ('bzip2 1.0.8 (Python bz2)', 'bzip2-1.0.6', 'https://sourceware.org/bzip2/', [('bzip2 license', lambda: tar(SRC + '/bzip2-1.0.8.tar.gz', 'bzip2-1.0.8/LICENSE'))]),
 ('LLVM libc++ / OpenMP runtime (Android NDK)', 'Apache-2.0 WITH LLVM-exception', 'https://android.googlesource.com/toolchain/llvm-project',
  [('NDK toolchain notices', lambda: file(NDK + '/NOTICE.toolchain'))]),
 ('IfcOpenShell 0.8.4', 'LGPL-3.0-or-later', 'https://github.com/IfcOpenShell/IfcOpenShell (source archive on the download page)',
  [('IfcOpenShell license', lambda: file(SRC + '/ifc/ios/COPYING.LESSER'))]),
 ('Pivy (FreeCAD 3rdParty)', 'ISC', 'https://github.com/coin3d/pivy', [('Pivy license', lambda: file(FC + '/src/3rdParty/pivy/LICENSE'))]),
 ('requests', 'Apache-2.0', 'https://github.com/psf/requests', [('requests license', lambda: file(TP + '/requests-notice.txt'))]),
 ('urllib3', 'MIT', 'https://github.com/urllib3/urllib3', [('urllib3 license', lambda: file(TP + '/urllib3.txt'))]),
 ('certifi', 'MPL-2.0', 'https://github.com/certifi/python-certifi', [('certifi license', lambda: file(TP + '/certifi.txt'))]),
 ('charset-normalizer', 'MIT', 'https://github.com/jawah/charset_normalizer', [('charset-normalizer license', lambda: file(TP + '/charset-normalizer.txt'))]),
 ('idna', 'BSD-3-Clause', 'https://github.com/kjd/idna', [('idna license', lambda: file(TP + '/idna.txt'))]),
 ('python-dateutil', 'Apache-2.0 AND BSD-3-Clause', 'https://github.com/dateutil/dateutil', [('python-dateutil license', lambda: file(TP + '/python-dateutil.txt'))]),
 ('isodate', 'BSD-3-Clause', 'https://github.com/gweis/isodate', [('isodate license', lambda: file(TP + '/isodate.txt'))]),
 ('Lark', 'MIT', 'https://github.com/lark-parser/lark', [('Lark license', lambda: file(TP + '/lark.txt'))]),
 ('packaging', 'Apache-2.0 OR BSD-2-Clause', 'https://github.com/pypa/packaging', [('packaging license', lambda: file(TP + '/packaging.txt'))]),
 ('PLY', 'BSD-3-Clause', 'https://github.com/dabeaz/ply', [('PLY license', lambda: file(TP + '/ply.txt'))]),
 ('PyYAML', 'MIT', 'https://github.com/yaml/pyyaml', [('PyYAML license', lambda: file(TP + '/pyyaml.txt'))]),
 ('six', 'MIT', 'https://github.com/benjaminp/six', [('six license', lambda: file(TP + '/six.txt'))]),
 ('typing_extensions', 'PSF-2.0', 'https://github.com/python/typing_extensions', [('typing_extensions license', lambda: file(TP + '/typing-extensions.txt'))]),
 ('AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines', 'Apache-2.0', 'https://source.android.com', [('Apache-2.0', lambda: tar(SRC + '/xerces.tar.gz', 'xerces-c-3.2.5/LICENSE'))]),
 ('AndroidSVG 1.4', 'Apache-2.0', 'https://github.com/BigBadaboom/androidsvg', [('Apache-2.0', lambda: tar(SRC + '/xerces.tar.gz', 'xerces-c-3.2.5/LICENSE'))]),
]

def icon_credits():
    """FreeCAD icons whose SVG metadata names a license other than FreeCAD's LGPL: title, creator, license."""
    d = os.path.join(R, '..', 'app', 'app', 'src', 'main', 'assets', 'freecad', 'icons'); out = []
    for f in sorted(os.listdir(d)):
        s = file(os.path.join(d, f))
        m = re.search(r'creativecommons\.org/(licenses/[a-z-]+/[0-9.]+|publicdomain/[a-z/0-9.]*|licenses/publicdomain)', s)
        if not m: continue
        who = re.findall(r'<dc:creator>.*?<dc:title>([^<]*)</dc:title>', s, re.S) or re.findall(r'<dc:rights>.*?<dc:title>([^<]*)</dc:title>', s, re.S)
        out.append('%s — %s — https://creativecommons.org/%s' % (f, (who[0].strip() if who else 'FreeCAD contributors'), m.group(1)))
    return out

shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT)
idx, texts = [], {}
for name, lic, src, files in C:
    refs = []
    for title, load in files:
        body = load(); key = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-') + '.txt'
        if key in texts and texts[key] != body: key = re.sub(r'[^a-z0-9]+', '-', (name + ' ' + title).lower()).strip('-')[:60] + '.txt'
        texts[key] = body; refs.append({'title': title, 'file': key})
    idx.append({'name': name, 'license': lic, 'source': src, 'texts': refs})
for k, v in texts.items(): open(os.path.join(OUT, k), 'w').write(v)
ic = icon_credits()
open(os.path.join(OUT, 'freecad-icons.txt'), 'w').write(
    'Toolbar and command icons are from FreeCAD (https://github.com/FreeCAD/FreeCAD), LGPL-2.1-or-later unless noted below.\n'
    'Icons with their own license (file — author — license):\n\n' + '\n'.join(ic) + '\n')
idx.append({'name': 'FreeCAD icons', 'license': 'LGPL-2.1-or-later; some CC-BY / CC-BY-SA / public domain', 'source': 'https://github.com/FreeCAD/FreeCAD',
            'texts': [{'title': 'Icon credits', 'file': 'freecad-icons.txt'}]})
json.dump(idx, open(os.path.join(OUT, 'index.json'), 'w'), indent=1)
print('%d components, %d texts, %d credited icons' % (len(idx), len(texts) + 1, len(ic)))
