"""Per-workbench command catalog (ordered as in the workbench GUI definition files) -> markdown + json."""
import json, os, re, sys
root, data_path, out_md = sys.argv[1], sys.argv[2], sys.argv[3]
data = json.load(open(data_path)); cmds = data['commands']
mods = os.path.join(root, 'src', 'Mod')
GUI_FILES = re.compile(r'(Workbench\.cpp|InitGui\.py|init_tools\.py|.*Workbench.*\.py|BimWorkbench\.py|Commands\.py)$')
cat = {}
for wb in sorted(os.listdir(mods)):
    d = os.path.join(mods, wb)
    if not os.path.isdir(d): continue
    seen = []
    for dp, _, fs in os.walk(d):
        if '/Test' in dp or '/test' in dp: continue
        for f in sorted(fs):
            if not GUI_FILES.match(f): continue
            s = open(os.path.join(dp, f), errors='ignore').read()
            for name in re.findall(r'["\']([A-Z][A-Za-z0-9]*_[A-Za-z0-9_]+)["\']', s):
                if name in cmds and name not in seen: seen.append(name)
    if seen: cat[wb] = seen
# commands defined in a module dir but not referenced by its GUI files (context-menu / task-only)
for name, c in cmds.items():
    m = re.match(r'src/Mod/(\w+)/', c.get('file', ''))
    if m and m.group(1) in cat and name not in cat[m.group(1)]: cat[m.group(1)].append(name + ' *')
std = [n for n in cmds if n.startswith('Std_')]
cat['Std (core, all workbenches)'] = std
json.dump(cat, open(out_md.replace('.md', '.json'), 'w'), indent=1)
with open(out_md, 'w') as o:
    o.write('# FreeCAD command catalog (from source, main @ %s)\n\n' % os.popen('git -C %s log -1 --format=%%h' % root).read().strip())
    o.write('`*` = command defined in the module but not placed on a toolbar/menu file (context menu, task dialog or hidden).\n\n')
    for wb, names in cat.items():
        o.write('## %s (%d)\n\n| Command | Menu text | Tooltip |\n|---|---|---|\n' % (wb, len(names)))
        for n in names:
            k = n.rstrip(' *'); c = cmds.get(k, {})
            tip = c.get('sToolTipText', '').replace('\n', ' ').replace('|', '/')[:160]
            o.write('| %s | %s | %s |\n' % (n, c.get('sMenuText', '').replace('&', ''), tip))
        o.write('\n')
print({k: len(v) for k, v in cat.items()})
