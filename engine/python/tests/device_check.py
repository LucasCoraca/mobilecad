"""On-device checks for the mesh cache and bridge validation.

Not discovered by unittest (the filename does not start with test_). Run it on a
debug build through the engine's scripting hook:

    adb shell am start -n app.mobilecad/app.mobilecad.MainActivity \
        --es fcscript64 "$(base64 < engine/python/tests/device_check.py)"

The script prints one JSON object per line in logcat (tag: fcscript). It covers:
shape edits, placement moves, App::Link updates and cache clearing on a new
document -- the cases where a stale cache would be visible.
"""
from mobilecad import bridge
import FreeCAD as App
import json
import Part
import time

print("ping=" + bridge.handle('{"op":"ping"}'))
print("badarg=" + bridge.handle('{"op":"properties","nmae":"Box"}'))

App.newDocument("DeviceCheck")
base = Part.makeBox(10, 10, 10)
for i in range(60):
    base = base.fuse(Part.makeBox(10, 10, 10, App.Vector(i * 12, 0, 0)))
Part.show(base, "Big")

t0 = time.monotonic()
first = bridge.tessellate("Big")
t1 = time.monotonic()
second = bridge.tessellate("Big")
t2 = time.monotonic()
print(json.dumps({"faces": len(base.Faces), "first_s": round(t1 - t0, 4),
                  "cached_s": round(t2 - t1, 6), "bytes": len(first),
                  "same": first == second, "cache": bridge._mesh_cache.stats()}))

obj = App.ActiveDocument.getObject("Big")
obj.Placement.Base.x = 5
App.ActiveDocument.recompute()
print(json.dumps({"placement_changes_mesh": bridge.tessellate("Big") != second}))

doc = App.ActiveDocument
link = doc.addObject("App::Link", "Link")
link.LinkedObject = doc.getObject("Big")
doc.recompute()
before = bridge.tessellate("Link")
obj.Shape = Part.makeBox(10, 10, 10)
doc.recompute()
print(json.dumps({"link_updates": bridge.tessellate("Link") != before}))

entries = bridge._mesh_cache.stats()["entries"]
bridge.handle('{"op":"new_document","name":"DeviceCheck2"}')
print(json.dumps({"clear_on_new_document": bridge._mesh_cache.stats()["entries"] == 0,
                  "entries_before": entries}))
