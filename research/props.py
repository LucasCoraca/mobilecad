"""Extract feature properties (name, type, default, group, doc) + enums from FreeCAD App sources."""
import os, re, sys, json
root, out = sys.argv[1], sys.argv[2]
res = {}
for mod in ('PartDesign', 'Part', 'Sketcher', 'Surface', 'TechDraw', 'Assembly', 'Spreadsheet', 'Mesh'):
    d = os.path.join(root, 'src/Mod', mod, 'App')
    if not os.path.isdir(d): continue
    for f in sorted(os.listdir(d)):
        if not f.endswith('.cpp'): continue
        s = open(os.path.join(d, f), errors='ignore').read()
        enums = {m.group(1): re.findall(r'"([^"]+)"', m.group(2)) for m in re.finditer(r'(\w+)\[\]\s*=\s*\{(.*?)\}', s, re.S)}
        for cm in re.finditer(r'(\w+)::\1\(\)[^{]*\{(.*?)\n\}', s, re.S):
            props = []
            for p in re.finditer(r'ADD_PROPERTY(?:_TYPE)?\(\s*(\w+)\s*,\s*\((.*?)\)\s*(?:,\s*"([^"]*)"\s*,\s*([\w:|() ]+?)\s*,\s*"((?:[^"\\]|\\.)*)")?\s*\)', cm.group(2), re.S):
                props.append({'name': p.group(1), 'default': p.group(2).strip()[:60], 'group': p.group(3), 'doc': (p.group(5) or '')[:150]})
            if not props: continue
            for pr in props:
                em = re.search(re.escape(pr['name']) + r'\.setEnums\((\w+)\)', cm.group(2))
                if em and em.group(1) in enums: pr['enum'] = enums[em.group(1)]
            res['%s::%s' % (mod, cm.group(1))] = props
json.dump(res, open(out, 'w'), indent=1)
md = open(out.replace('.json', '.md'), 'w')
md.write('# FreeCAD feature properties (from App sources)\n\n')
for k, v in res.items():
    md.write('## %s\n' % k)
    for p in v:
        md.write('- **%s** = `%s`%s — %s\n' % (p['name'], p['default'], (' enum: ' + ', '.join(p['enum'])) if 'enum' in p else '', p['doc'].replace('\n', ' ')))
    md.write('\n')
print(len(res), 'classes')
