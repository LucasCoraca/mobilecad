# Runs INSIDE MobileCAD (debug build) via mobilecad/engine/fcrun.sh: opens each .FCStd in files/examples and writes a
# scene bundle (.mcxr) with what MobileCAD would show - same tessellation, visibility rules and colours - to files/user/xr/.
# Those bundles feed the desktop XR test app (they are FreeCAD's example projects, so they ship in scenes/).
import os, struct, FreeCAD as App
from mobilecad import bridge
HIDDEN = {'PartDesign::Body', 'App::Part', 'App::Origin', 'App::DocumentObjectGroup', 'App::Line', 'App::Plane', 'App::Point'}
def shown(objs):
    by = {o['name']: o for o in objs}
    if any(o['type'] == 'Assembly::AssemblyObject' for o in objs):        # the Assembly tab shows the components
        return [o for o in objs if o['assembly'] and o['type'] == 'App::Link' and o['visible']]
    out = []
    for o in objs:
        if not (o['hasShape'] and o['visible']) or o['assembly'] or o['type'] in HIDDEN: continue
        if any(by.get(p, {}).get('type') == 'App::Origin' for p in o['parents']): continue
        if any(p['type'] == 'PartDesign::Body' and o['name'] in p['group'] and not p['visible'] for p in objs): continue
        out.append(o)
    return out
src = os.path.join(App.getUserAppDataDir(), '..', 'examples'); dst = os.path.join(App.getUserAppDataDir(), 'xr'); os.makedirs(dst, exist_ok=True)
report = []
for f in sorted(os.listdir(src)):
    if not f.endswith('.FCStd'): continue
    name = bridge.open_document(os.path.join(src, f)); App.ActiveDocument.recompute()
    cols = (bridge.view_data() or {}).get('colors', {})
    parts = []
    for o in shown(bridge.objects()):
        blob = bridge.tessellate(o['name'])
        if len(blob) < 12 or struct.unpack_from('<i', blob, 0)[0] == 0: continue
        c = cols.get(o['name'], (0.81, 0.84, 0.86))[:3]
        parts.append((o['label'].encode()[:65535], c, blob))
    with open(os.path.join(dst, f[:-6] + '.mcxr'), 'wb') as out:
        out.write(b'MCXR1' + struct.pack('<I', len(parts)))
        for lab, c, blob in parts:
            out.write(struct.pack('<H', len(lab)) + lab + struct.pack('<3f', *c) + struct.pack('<I', len(blob)) + blob)
    report.append('%s: %d objects' % (f, len(parts)))
    App.closeDocument(name)
result = report
