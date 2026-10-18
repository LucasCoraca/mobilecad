"""FreeCAD toolbar structure per workbench (C++ Workbench.cpp + Python InitGui/init_tools). -> toolbars.json"""
import json, os, re, sys, ast
root, cmds_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
cmds = json.load(open(cmds_path))['commands']
mods = os.path.join(root, 'src', 'Mod')
res = {}

def cpp_toolbars(path):
    s = open(path, errors='ignore').read()
    m = re.search(r'ToolBarItem\*\s*Workbench::setupToolBars\(\)\s*const\s*\{(.*?)\n\}', s, re.S)
    if not m: return []
    def helper(name):
        cands = list(re.finditer(r'void\s+' + name + r'(?:<\s*([\w:]+)\s*>)?\s*\(\s*([\w:<>]+)\s*&\s*(\w+)\s*\)\s*\{(.*?)\n\}', s, re.S))
        for c in cands:
            if 'ToolBarItem' in (c.group(1) or '') + c.group(2): return c.group(3), c.group(4)
        return (cands[0].group(3), cands[0].group(4)) if cands else None
    def expand(body, depth=0):
        out = []
        for st in re.split(r';', body):
            call = re.search(r'(\w+)(?:<[^>]*>)?\(\s*\*?(\w+)\s*\)\s*$', st.strip())
            if call and depth < 3 and not st.strip().startswith(('auto', 'Gui', 'return')) and '<<' not in st:
                h = helper(call.group(1))
                if h:
                    param, hb = h
                    out += [x.replace(param, call.group(2), 1) if x.strip().startswith(param) else x for x in expand(hb, depth + 1)]
                    continue
            out.append(st)
        return out
    tbs = []; cur = {}
    for st in expand(m.group(1)):
        sc = re.search(r'(\w+)->setCommand\(\s*(?:QT_TR_NOOP\()?"([^"]+)"', st)
        if sc:
            t = {'toolbar': sc.group(2).replace('&', ''), 'items': []}; tbs.append(t); cur[sc.group(1)] = t; continue
        mm = re.search(r'(?:^|[\s{}()])\*?(\w+)\s*<<', st)
        if mm:
            var = mm.group(1); t = cur.get(var) or next((cur[v] for v in cur if v.startswith(var) or var.startswith(v)), None)
            if t is None: continue
            for it in re.findall(r'"([\w.]+)"', st):
                if (it in cmds or it == 'Separator') and (it == 'Separator' or it not in t['items']): t['items'].append(it)
    return [t for t in tbs if any(i != 'Separator' for i in t['items']) and t['toolbar'] not in ('Std', 'Edit Mode')]

def py_lists(src):
    lists = {}
    for m in re.finditer(r'(?:self\.)?(\w+)\s*=\s*\[(.*?)\]', src, re.S):
        lists[m.group(1)] = re.findall(r'["\']([\w.]+)["\']', m.group(2))
    for m in re.finditer(r'def\s+(\w+)\(.*?\):\s*\n(.*?)(?=\ndef |\Z)', src, re.S):
        r = re.search(r'return\s*\[(.*?)\]', m.group(2), re.S)
        if r: lists[m.group(1)] = re.findall(r'["\']([\w.]+)["\']', r.group(1))
    return lists

for wb in sorted(os.listdir(mods)):
    d = os.path.join(mods, wb)
    if not os.path.isdir(d): continue
    tbs = []
    wbcpp = os.path.join(d, 'Gui', 'Workbench.cpp')
    if os.path.exists(wbcpp): tbs += cpp_toolbars(wbcpp)
    pysrc = ''
    for dp, _, fs in os.walk(d):
        for f in fs:
            if f.endswith('.py') and ('InitGui' in f or 'init_tools' in f or 'Workbench' in f): pysrc += open(os.path.join(dp, f), errors='ignore').read() + '\n'
    if pysrc:
        lists = py_lists(pysrc)
        for m in re.finditer(r'appendToolbar\(\s*(?:QT_TRANSLATE_NOOP\(\s*["\'][^"\']*["\']\s*,\s*)?(?:translate\(\s*["\'][^"\']*["\']\s*,\s*)?["\']([^"\']+)["\']\s*\)?\s*,\s*(.+?)\)\s*$', pysrc, re.M):
            expr = m.group(2).strip(); items = []
            for part in re.split(r'\s*\+\s*', expr):
                part = part.strip()
                if part.startswith('['): items += re.findall(r'["\']([\w.]+)["\']', part)
                else:
                    key = re.sub(r'\(.*', '', part).split('.')[-1]
                    items += lists.get(key, [])
            items = [i for i in items if i in cmds or i == 'Separator']
            if items: tbs.append({'toolbar': m.group(1), 'items': items})
    if tbs: res[wb] = tbs
json.dump(res, open(out, 'w'), indent=1)
for wb, t in res.items(): print(wb, [(x['toolbar'], len(x['items'])) for x in t])
