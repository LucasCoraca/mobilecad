"""Parity matrix: what each FreeCAD command does (from its activated() source) vs. what the mobile app implements.
Usage: python3 -I parity.py <freecad-src> <research-dir> <app-src-dir>  ->  PARITY.md + parity.json"""
import json, os, re, sys
root, research, appsrc = sys.argv[1], sys.argv[2], sys.argv[3]
cmds = json.load(open(os.path.join(research, 'freecad_commands.json')))['commands']
cat = json.load(open(os.path.join(research, 'FREECAD_COMMANDS.json')))

TYPE_RE = re.compile(r"""(?:addObject|newObject)\(\s*\\?["']([A-Z][\w]*::[\w]+)""")
MAKE_RE = re.compile(r"""\b((?:Draft|Arch|BIM|ObjectsFem|Fem|Path|CAM|OpenSCAD|Spreadsheet|TechDraw|Mesh|Points|Assembly|Robot)\w*\.(?:make|create)\w+)\(""")
GUI_HINTS = ('setActiveObject', 'Gui.Control.showDialog', 'showDialog', 'TaskDialog', 'Gui.Selection', 'ViewObject', 'activeView', 'getMainWindow')

act = {}
# C++: map class -> command name, then read Cmd::activated(int)
for dp, _, fs in os.walk(os.path.join(root, 'src')):
    for f in fs:
        if not f.endswith('.cpp'): continue
        s = open(os.path.join(dp, f), errors='ignore').read()
        names = {m.group(1): m.group(2) for m in re.finditer(r'(\w+)::\1\(\)\s*:\s*(?:Gui::)?\w*Command\w*\(\s*"([^"]+)"', s)}
        for m in re.finditer(r'void\s+(\w+)::activated\(\s*int[^)]*\)\s*\{(.*?)\n\}', s, re.S):
            n = names.get(m.group(1))
            if n: act[n] = m.group(2)
# Python: class Activated bodies, bound via addCommand('Name', Class())
for dp, _, fs in os.walk(os.path.join(root, 'src', 'Mod')):
    for f in fs:
        if not f.endswith('.py'): continue
        s = open(os.path.join(dp, f), errors='ignore').read()
        if 'addCommand' not in s: continue
        bodies = {}
        for cm in re.finditer(r'^class\s+(\w+)[^\n]*:\n(.*?)(?=^\S|\Z)', s, re.S | re.M):
            a = re.search(r'def Activated\(self[^)]*\):(.*?)(?=\n    def |\Z)', cm.group(2), re.S)
            if a: bodies[cm.group(1)] = a.group(1)
        for am in re.finditer(r'addCommand\(\s*["\']([\w.]+)["\']\s*,\s*(\w+)', s):
            if am.group(2) in bodies: act.setdefault(am.group(1), bodies[am.group(2)])

def classify(name):
    b = act.get(name, '')
    types = sorted(set(TYPE_RE.findall(b))); makes = sorted(set(MAKE_RE.findall(b)))
    if 'prepareProfileBased' in b or 'prepareTransformed' in b or 'makeChamferOrFillet' in b or 'finishDressupFeature' in b:
        return 'creates', ['PartDesign::' + name.split('_', 1)[1]]
    if types: return 'creates', types
    if makes: return 'make', makes
    if any(h in b for h in ('showDialog', 'TaskDialog', 'Gui.Control')): return 'dialog', []
    if b and any(h in b for h in GUI_HINTS): return 'gui', []
    return ('other' if b else 'unknown'), []

# mobile status: ids handled explicitly in MobileCommands.available() (evaluated by reading the Kotlin source rules)
mc = open(os.path.join(appsrc, 'freecad', 'MobileCommands.kt')).read()
explicit = set(re.findall(r'"((?:[A-Z][A-Za-z]*)_[\w]+)"', mc))
# FEM ids assembled from short names in MobileCommands.kt (femConstraints -> FEM_Constraint<X>, femPost -> FEM_<X>)
for lst, pre in (('femConstraints', 'FEM_Constraint'), ('femPost', 'FEM_')):
    m = re.search(lst + r' = listOf\((.*?)\)', mc, re.S)
    if m: explicit |= {pre + w for w in re.findall(r'"(\w+)"', m.group(1))}
std = open(os.path.join(appsrc, 'freecad', 'StdCommands.kt')).read()
na_block = std[std.index('val notApplicable'):]
na_block = na_block[:na_block.index(')\n')]
notapp = set(re.findall(r'"([A-Z][A-Za-z]*_\w+)"', na_block))
explicit |= set(re.findall(r'"([A-Z][A-Za-z]*_\w+)"', std)) - notapp
dr = open(os.path.join(appsrc, 'freecad', 'DraftCommands.kt')).read()
dr = dr[:dr.index('    fun run(')]             # the id tables (names are written without the Draft_ prefix)
for w in re.findall(r'"(\w+)"', dr):
    explicit |= {'Draft_' + w, 'Draft_Snap_' + w}
explicit |= set(re.findall(r'"((?:Mesh|MeshPart|Points|Reen|Surface)_\w+)"', open(os.path.join(appsrc, 'freecad', 'MeshCommands.kt')).read()))
td = open(os.path.join(appsrc, 'freecad', 'TechDrawCommands.kt')).read()
td = td[:td.index('    // ---------------------------------------------------------------- helpers')]
for w in re.findall(r'"(\w+)"', td):
    explicit |= {'TechDraw_' + w, 'TechDraw_Extension' + w}
generic = set(json.load(open(os.path.join(appsrc.split('/java/')[0], 'assets', 'freecad', 'command_types.json'))))
# FreeCAD's own Python commands run on the headless FreeCADGui (mobilecad.guicmd).  research/gui_scan.json is the on-device
# scan (every loadable command run with a solid selected): a command counts when it created objects, started point
# picking, or answered with its selection precondition (the desktop greys it out the same way).  "nothing" (ran without
# effect — dialog-only commands) and errors do not count until fixed individually.
_scan = json.load(open(os.path.join(research, 'gui_scan.json'))) if os.path.exists(os.path.join(research, 'gui_scan.json')) else {}
# targeted scan (gui_scan_ctx.json): commands that need a specific document (pages, trash, open meshes, IFC files, clones)
if os.path.exists(os.path.join(research, 'gui_scan_ctx.json')):
    _scan.update(json.load(open(os.path.join(research, 'gui_scan_ctx.json'))))
guiok = {n for n, v in _scan.items() if v.startswith(('created', 'point', 'delegated', 'needsel', 'panel', 'changed', 'url', 'wp', 'selected', 'view aligned')) or 'needs a different selection' in v
         # report-view answer: the command's own selection precondition message, or its (example) output
         or re.match(r'console (Error: )?Select|console \w+ activated|console TemplatePyGrp|console Enter \w+ creation mode|console Toggle is'
                     r'|console PathCommands.WARNING: Closed loop', v)
         # the command's own message box: its precondition (two documents ...) or its result (already up to date)
         or v.startswith('message ') or v == 'console The active document is already an IFC document'}

def mobile(name):
    if name in guiok: return True
    if name in generic: return True
    wb, n = name.split('_', 1) if '_' in name else (name, '')
    if name in explicit: return True
    if wb == 'PartDesign' and (n in ('Pad', 'Pocket', 'Revolution', 'Groove', 'Hole', 'AdditiveLoft', 'SubtractiveLoft', 'AdditivePipe', 'SubtractivePipe',
                                    'AdditiveHelix', 'SubtractiveHelix', 'Fillet', 'Chamfer', 'Draft', 'Thickness', 'Mirrored', 'LinearPattern', 'PolarPattern')
                               or n.startswith('Additive') or n.startswith('Subtractive')): return True
    if wb == 'Part' and n in ('Box', 'Cylinder', 'Sphere', 'Cone', 'Torus', 'Tube'): return True
    if name.startswith('Assembly_CreateJoint') and name != 'Assembly_CreateJointRigidGroup': return True
    return False

# Python group commands (GetCommands dropdowns, research/gui_groups.json from the device) are not tools either
pygroups = set(json.load(open(os.path.join(research, 'gui_groups.json')))) if os.path.exists(os.path.join(research, 'gui_groups.json')) else set()
pygroups |= {'Arch_RebarTools', 'CAM_3dTools'}       # registered only with the Reinforcement add-on / experimental features
rows = {}; summary = {}
for wb, names in cat.items():
    for raw in names:
        n = raw.rstrip(' *')
        c = cmds.get(n, {})
        if c.get('group') or re.match(r'^\w+_Comp[A-Z]', n) or n in pygroups: continue          # dropdown groups (incl. action-group Comp* commands) are not tools
        kind, what = classify(n)
        rows[n] = {'wb': wb, 'label': c.get('sMenuText', n), 'kind': kind, 'what': what, 'mobile': mobile(n), 'na': n in notapp}
for n, r in rows.items():
    s = summary.setdefault(r['wb'], {'total': 0, 'mobile': 0, 'na': 0, 'creates': 0, 'make': 0, 'dialog': 0, 'gui': 0, 'other': 0, 'unknown': 0})
    if r['na']: s['na'] += 1; continue          # desktop-window commands: not counted in the total
    s['total'] += 1; s['mobile'] += r['mobile']; s[r['kind']] += 1
json.dump({'summary': summary, 'rows': rows}, open(os.path.join(research, 'parity.json'), 'w'), indent=1)
with open(os.path.join(research, 'PARITY.md'), 'w') as o:
    o.write('# Mobile tool parity with FreeCAD (generated by parity.py)\n\n| Workbench | Tools | On mobile | creates object | make*() | task dialog | view/gui | other |\n|---|---|---|---|---|---|---|---|\n')
    T = M = 0
    for wb, s in sorted(summary.items()):
        T += s['total']; M += s['mobile']
        o.write('| %s | %d | %d | %d | %d | %d | %d | %d |\n' % (wb, s['total'], s['mobile'], s['creates'], s['make'], s['dialog'], s['gui'], s['other'] + s['unknown']))
    o.write('| **All** | **%d** | **%d (%.0f%%)** | | | | | |\n\n' % (T, M, 100.0 * M / max(T, 1)))
    o.write('Not counted (desktop window management, build-option-only and internal commands with no phone counterpart, %d): %s\n\n' % (len(notapp), ', '.join(sorted(notapp))))
    for wb in sorted(summary):
        o.write('## %s\n\n| Command | Kind | Creates / calls | Mobile |\n|---|---|---|---|\n' % wb)
        for n, r in sorted(rows.items()):
            if r['wb'] == wb: o.write('| %s | %s | %s | %s |\n' % (n, r['kind'], ', '.join(r['what']), 'yes' if r['mobile'] else ('n/a' if r['na'] else '')))
        o.write('\n')
print(open(os.path.join(research, 'PARITY.md')).read().split('\n## ')[0])
