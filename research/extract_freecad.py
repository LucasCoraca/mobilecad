"""Extract FreeCAD command catalog + workbench toolbars from source. Usage: python3 -I extract_freecad.py <freecad-src> <out.json>"""
import json, os, re, sys
root, out = sys.argv[1], sys.argv[2]
mods = os.path.join(root, 'src', 'Mod')

def tr(s):
    m = re.search(r'QT_TRANSLATE_NOOP\(\s*"[^"]*"\s*,\s*"((?:[^"\\]|\\.)*)"', s) or re.search(r'QT_TR_NOOP\(\s*"((?:[^"\\]|\\.)*)"', s) or re.search(r'"((?:[^"\\]|\\.)*)"', s)
    return m.group(1) if m else s.strip()

cmds = {}
# ---- C++ commands
ctor = re.compile(r'(\w+)::\1\(\)\s*:\s*(?:Gui::)?(?:Command|GroupCommand|[A-Za-z:]*Command\w*)\s*\(\s*"([^"]+)"\s*\)\s*\{(.*?)\n\}', re.S)
for dp, _, fs in os.walk(os.path.join(root, 'src')):
    for f in fs:
        if not f.endswith('.cpp'): continue
        p = os.path.join(dp, f); s = open(p, errors='ignore').read()
        for m in ctor.finditer(s):
            body = m.group(3); d = {'file': os.path.relpath(p, root)}
            for k in ('sMenuText', 'sToolTipText', 'sGroup', 'sWhatsThis', 'sPixmap', 'sStatusTip'):
                mm = re.search(k + r'\s*=\s*(.*?);', body, re.S)
                if mm: d[k] = tr(mm.group(1))
            cmds[m.group(2)] = d
# ---- C++ group commands (dropdown buttons): inline ctor ': GroupCommand("Name")' + addCommand("member")
gctor = re.compile(r'(\w+)\(\)\s*:\s*(?:Gui::)?GroupCommand\(\s*"([^"]+)"\s*\)\s*\{(.*?)\n\s*\}', re.S)
for dp, _, fs in os.walk(os.path.join(root, 'src')):
    for f in fs:
        if not f.endswith(('.cpp', '.h')): continue
        p = os.path.join(dp, f); s2 = open(p, errors='ignore').read()
        for m in gctor.finditer(s2):
            body = m.group(3); d = cmds.get(m.group(2), {'file': os.path.relpath(p, root)})
            for k in ('sMenuText', 'sToolTipText', 'sGroup', 'sPixmap'):
                mm = re.search(k + r'\s*=\s*(.*?);', body, re.S)
                if mm: d[k] = tr(mm.group(1))
            d['group'] = re.findall(r'addCommand\(\s*"([\w.]+)"', body)
            cmds[m.group(2)] = d
# ---- Python commands
for dp, _, fs in os.walk(mods):
    for f in fs:
        if not f.endswith('.py'): continue
        p = os.path.join(dp, f); s = open(p, errors='ignore').read()
        if 'addCommand' not in s: continue
        classes = {}
        for cm in re.finditer(r'^class\s+(\w+)[^\n]*:\n(.*?)(?=^\S)', s + '\nEND', re.S | re.M):
            body = cm.group(2); d = {'file': os.path.relpath(p, root)}
            for k, key in (('MenuText', 'sMenuText'), ('ToolTip', 'sToolTipText'), ('Pixmap', 'sPixmap')):
                mm = re.search(r'["\']' + k + r'["\']\s*:\s*(.*?)(?:,\s*\n|\n\s*\})', body, re.S)
                if mm: d[key] = tr(mm.group(1))
            classes[cm.group(1)] = d
        for am in re.finditer(r'addCommand\(\s*["\']([\w.]+)["\']\s*,\s*(\w+)', s):
            d = dict(classes.get(am.group(2), {'file': os.path.relpath(p, root)})); d['py'] = True
            cmds.setdefault(am.group(1), d)

# ---- toolbars: python appendToolbar + C++ "*tb << ..." blocks inside setupToolBars
toolbars = {}
for wb in sorted(os.listdir(mods)):
    d = os.path.join(mods, wb); tbs = []
    if not os.path.isdir(d): continue
    for dp, _, fs in os.walk(d):
        for f in fs:
            p = os.path.join(dp, f); s = open(p, errors='ignore').read() if f.endswith(('.py', '.cpp')) else ''
            if f.endswith('.py'):
                for m in re.finditer(r'appendToolbar\(\s*(?:QT_TRANSLATE_NOOP\([^,]+,\s*)?["\']([^"\']+)["\']\)?\s*,\s*([\w.]+|\[[^\]]*\])', s):
                    items = m.group(2)
                    if items.startswith('['): lst = re.findall(r'["\']([\w.]+)["\']', items)
                    else:
                        mm = re.search(re.escape(items.split('.')[-1]) + r'\s*=\s*\[(.*?)\]', s, re.S)
                        lst = re.findall(r'["\']([\w.]+)["\']', mm.group(1)) if mm else ['<' + items + '>']
                    tbs.append({'toolbar': m.group(1), 'items': lst, 'src': os.path.relpath(p, root)})
            elif f == 'Workbench.cpp':
                mm = re.search(r'setupToolBars\(\)\s*const\s*\{(.*?)\n\}', s, re.S)
                if not mm: continue
                body = mm.group(1); cur = None
                for line in body.splitlines():
                    nm = re.search(r'(\w+)->setCommand\(\s*(?:QT_TR_NOOP\()?"([^"]+)"', line) or re.search(r'new\s+Gui::ToolBarItem\(.*?\)', line)
                    if nm and hasattr(nm, 'group') and nm.lastindex == 2: cur = {'toolbar': nm.group(2), 'items': [], 'src': os.path.relpath(p, root)}; tbs.append(cur)
                    for it in re.findall(r'<<\s*"([\w.]+)"', line):
                        if cur is None: cur = {'toolbar': '?', 'items': [], 'src': os.path.relpath(p, root)}; tbs.append(cur)
                        cur['items'].append(it)
    toolbars[wb] = tbs

json.dump({'commands': cmds, 'toolbars': toolbars}, open(out, 'w'), indent=1)
print(len(cmds), 'commands;', {k: sum(len(t['items']) for t in v) for k, v in toolbars.items() if v})
