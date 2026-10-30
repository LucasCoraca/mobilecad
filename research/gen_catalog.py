"""Build app asset catalog.json (+ referenced FreeCAD SVG icons) from extracted command/toolbar data."""
import json, os, re, shutil, sys
root, research, assets = sys.argv[1], sys.argv[2], sys.argv[3]
cmds = json.load(open(os.path.join(research, 'freecad_commands.json')))['commands']
tbs = json.load(open(os.path.join(research, 'freecad_toolbars.json')))
# workbenches that build their toolbars in code (BIM, CAM, OpenSCAD): captured on the device by running InitGui
# Initialize() on the headless FreeCADGui (mobilecad.guicmd.gui_workbench_toolbars)
_rt = os.path.join(research, 'freecad_toolbars_runtime.json')
if os.path.exists(_rt):
    for wb, lst in json.load(open(_rt)).items():
        if isinstance(lst, list) and lst and not tbs.get(wb):
            tbs[wb] = [{'toolbar': n, 'items': items} for n, items in lst]
# Python commands' GetResources() (MenuText / ToolTip / Pixmap) captured on the device: the static scan misses
# labels built at runtime (QT_TRANSLATE_NOOP in dicts, shared base classes)
_rr = os.path.join(research, 'freecad_command_resources_runtime.json')
if os.path.exists(_rr):
    for n, r in json.load(open(_rr)).items():
        c = cmds.setdefault(n, {})
        if r.get('MenuText') and (not c.get('sMenuText') or c['sMenuText'].strip('"') in (n, n.split('_', 1)[-1])): c['sMenuText'] = r['MenuText']
        if r.get('ToolTip') and not c.get('sToolTipText'): c['sToolTipText'] = re.sub(r'<[^>]+>', ' ', r['ToolTip']).strip()
        if r.get('Pixmap') and not c.get('sPixmap'): c['sPixmap'] = r['Pixmap']
# C++ group commands whose members are QActions built in code (TechDraw CmdTechDrawStackGroup ...): their members
_cg = os.path.join(research, 'freecad_cpp_groups.json')
if os.path.exists(_cg):
    for n, members in json.load(open(_cg)).items():
        if not cmds.setdefault(n, {}).get('group'): cmds[n]['group'] = members
# C++ commands whose constructor hands the name to a base class (CmdSketcherConstraint("Sketcher_ConstrainDistanceX"),
# GroupCommand ...): the static extraction misses their sMenuText / sToolTipText / sPixmap; read them from the source
def _named_ctor(t):
    """Cls::Cls(const char* initName) : Base(initName), with the name as the declaration's default argument
    (CmdSketcherConstrainCoincidentUnified): rewritten as Cls::Cls() : Base("Sketcher_...")"""
    for cls, name in re.findall(r'\b(\w+)\(\s*const char\s*\*\s*\w+\s*=\s*"(\w+)"\s*\)', t):
        t = re.sub(r'\b%s::%s\(\s*const char\s*\*\s*(\w+)\s*\)\s*:\s*(\w+)\(\s*\1\s*\)' % (cls, cls), r'%s::%s() : \2("%s")' % (cls, cls, name), t)
    return t
def _cpp_resources():
    out = {}
    for dp, _, fs in os.walk(os.path.join(root, 'src')):
        for f in fs:
            if not f.endswith('.cpp'): continue
            t = _named_ctor(open(os.path.join(dp, f), errors='ignore').read())
            for m in re.finditer(r'::\w+\(\)\s*:\s*\w+\(\s*"(\w+)"\s*\)\s*\{(.{0,6000}?)\n\}', t, re.S):
                body = m.group(2); r = {}
                for key in ('sMenuText', 'sToolTipText', 'sPixmap'):
                    mm = re.search(key + r'\s*=\s*(?:QT_TR_NOOP\(|QT_TRANSLATE_NOOP\([^,]*,\s*)?"((?:[^"\\]|\\.)*)"', body)
                    if mm: r[key] = mm.group(1)
                if r: out[m.group(1)] = r
    return out
# C++ dropdown commands (Part_CompCompoundTools, Sketcher_CompConstrainRadDia, FEM_CompEmConstraints ...): an ActionGroup
# whose activated(i) runs the i-th member (runCommandByName) or a GroupCommand built with addCommand(); the toolbar
# button shows the first member's icon, as on the desktop
def _cpp_dropdowns():
    out = {}
    for dp, _, fs in os.walk(os.path.join(root, 'src')):
        for f in fs:
            if not f.endswith('.cpp'): continue
            t = open(os.path.join(dp, f), errors='ignore').read()
            for m in re.finditer(r'(\w+)::\1\(\)\s*:\s*(?:Gui::)?(Group)?Command\(\s*"(\w+)"\s*\)\s*\{(.{0,3000}?)\n\}', t, re.S):
                cls, grp, name, ctor = m.groups()
                if grp: members = re.findall(r'addCommand\(\s*"(\w+)"', ctor)
                else:
                    a = re.search(r'\b%s::activated\(int[^)]*\)\s*\{(.{0,4000}?)\n\}' % cls, t, re.S)
                    members = re.findall(r'runCommandByName\(\s*"(\w+)"', a.group(1)) if a else []
                if members: out[name] = list(dict.fromkeys(members))
    return out
for n, members in _cpp_dropdowns().items():
    c = cmds.setdefault(n, {})
    if not c.get('group') and not c.get('sPixmap'): c['group'] = members   # commands with their own icon stay plain commands
# Python dropdowns (GetCommands()) captured on the device (mobilecad.guicmd.gui_groups)
_pg = os.path.join(research, 'gui_groups.json')
if os.path.exists(_pg):
    for n, members in json.load(open(_pg)).items():
        if members and not cmds.setdefault(n, {}).get('group'): cmds[n]['group'] = members
for n, r in _cpp_resources().items():
    c = cmds.setdefault(n, {})
    for k, v in r.items():
        if not c.get(k) or (k == 'sMenuText' and c[k].strip('"') in (n, n.split('_', 1)[-1])): c[k] = v
cat = json.load(open(os.path.join(research, 'FREECAD_COMMANDS.json')))
os.makedirs(os.path.join(assets, 'icons'), exist_ok=True)
svg = {}
for dp, _, fs in os.walk(os.path.join(root, 'src')):
    if '/icons' not in dp and '/Icons' not in dp: continue
    for f in fs:
        if f.endswith('.svg'): svg.setdefault(f[:-4], os.path.join(dp, f))
def icon(name):
    c = cmds.get(name, {}); px = c.get('sPixmap') or name
    px = re.sub(r'\.svg$', '', os.path.basename(px.strip('"')))
    for cand in (px, name, name.replace('_', '-')):
        if cand in svg:
            shutil.copy(svg[cand], os.path.join(assets, 'icons', cand + '.svg')); return cand
    return None
out_cmds = {}
# action-group commands whose items are not registered commands (CommandPrimitive.cpp)
PRIMS = ['Box', 'Cylinder', 'Sphere', 'Cone', 'Ellipsoid', 'Torus', 'Prism', 'Wedge']
for kind in ('Additive', 'Subtractive'):
    grp = 'PartDesign_CompPrimitive' + kind
    cmds.setdefault(grp, {})['group'] = ['PartDesign_%s%s' % (kind, p) for p in PRIMS]
    for p in PRIMS:
        cmds['PartDesign_%s%s' % (kind, p)] = {'sMenuText': '%s %s' % (kind, p), 'sToolTipText': 'Creates a %s %s primitive' % (kind.lower(), p.lower()), 'sPixmap': 'PartDesign_%s%s' % (kind, p)}
def add(name):
    if name in out_cmds or name == 'Separator': return
    c = cmds.get(name, {})
    out_cmds[name] = {'label': (c.get('sMenuText') or name.split('_', 1)[-1]).replace('&', '').replace('…', '...'),
                      'tip': (c.get('sToolTipText') or '').replace('\\n', ' '), 'icon': icon(name), 'group': c.get('group') or []}
    for m in out_cmds[name]['group']: add(m)
wbs = []
for wb, names in cat.items():
    if wb.startswith('Std'): continue
    toolbars = tbs.get(wb, [])
    listed = {i for t in toolbars for i in t['items']}
    for t in toolbars:
        for i in t['items']: add(i)
    extra = [n.rstrip(' *') for n in names if n.rstrip(' *') not in listed]
    for n in extra: add(n)
    wbs.append({'id': wb, 'toolbars': toolbars, 'other': extra})
for n in cat.get('Std (core, all workbenches)', []): add(n)
# tooltips that speak of the app itself as "FreeCAD" (links to FreeCAD's site/forum keep their names: that is credit)
TIPS = {'BIM_Setup': 'Sets common preferences for a BIM workflow', 'FEM_MaterialEditor': 'Opens the material editor',
        'Std_MergeProjects': 'Merges another document (.FCStd) into the active one',
        'Std_RestartInSafeMode': 'Restarts without any modules or plugins loaded'}
for k, t in TIPS.items():
    if k in out_cmds: out_cmds[k]['tip'] = t
json.dump({'source': 'FreeCAD main ' + os.popen('git -C %s log -1 --format=%%h' % root).read().strip(),
           'workbenches': wbs, 'commands': out_cmds}, open(os.path.join(assets, 'catalog.json'), 'w'), separators=(',', ':'))
n_icons = len(os.listdir(os.path.join(assets, 'icons')))
print(len(wbs), 'workbenches,', len(out_cmds), 'commands,', n_icons, 'icons', sum(1 for c in out_cmds.values() if not c['icon']), 'without icon')
