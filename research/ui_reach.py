"""UI reachability audit: every command implemented on mobile (parity.json 'mobile') must be reachable from the UI —
a workspace toolset (FreeCAD toolbars + the "More tools" group = the workbench's other commands), the sketch-mode
toolsets, or the ☰ menu (MainMenu.kt).  Usage: python3 -I ui_reach.py <research-dir> <app-src-dir>"""
import json, os, re, sys
research, appsrc = sys.argv[1], sys.argv[2]
par = json.load(open(os.path.join(research, 'parity.json')))['rows']
cat = json.load(open(os.path.join(appsrc.split('/java/')[0], 'assets', 'freecad', 'catalog.json')))
cmds = cat['commands']
def flat(items, seen=()):
    out = []
    for i in items:
        g = cmds.get(i, {}).get('group') or []
        out += flat(g, seen) if g else [i]
    return out
ws = open(os.path.join(appsrc, 'freecad', 'Workspaces.kt')).read()
wbs = set(re.findall(r'tb\("(\w+)"', ws)) | set(re.findall(r'forWorkbench\(c, "(\w+)"', ws)) | set(re.findall(r'"(\w+)" to "', ws))
reach = set()
for w in cat['workbenches']:
    if w['id'] in wbs: reach |= set(flat([i for t in w['toolbars'] for i in t['items']] + w['other']))
menu = open(os.path.join(appsrc, 'ui', 'MainMenu.kt')).read()
reach |= set(re.findall(r'"(Std_\w+)"', menu))
impl = {n for n, r in par.items() if r['mobile'] and not r.get('na')}
missing = sorted(impl - reach)
print('implemented %d, reachable %d, NOT reachable %d' % (len(impl), len(impl & reach), len(missing)))
for n in missing: print('  ', n, par[n]['wb'])
