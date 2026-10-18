"""CAM commands implemented in C++ (src/Mod/CAM/Gui/Command.cpp): Area, Area workplane, Compound, Shape.  The Python
ones (jobs, operations, dressups, tool bits ...) run through mobilecad.guicmd."""
from .bridge import op, _doc

def _sub_shapes(sel):
    """CmdPathArea / CmdPathShape: whole objects as sources; picked faces / edges become Part::Features of the sub shape
    (PathCommands.findShape, wires for edges)."""
    import PathCommands
    d = _doc(); sources = []; area = None
    for n, subs in (sel or []):
        o = d.getObject(n)
        if o is None or not hasattr(o, 'Shape'): continue
        if not subs:
            if o.isDerivedFrom('Path::FeatureArea') and area is None: area = o
            sources.append(o); continue
        for s in subs:
            if not s.startswith(('Face', 'Edge')): raise RuntimeError('Selected shape is not 2D')
            f = d.addObject('Part::Feature', '%s_%s' % (o.Name, s))
            f.Shape = PathCommands.findShape(o.Shape, s, 'Wires') if s.startswith('Edge') else PathCommands.findShape(o.Shape, s)
            sources.append(f)
    return sources, area

@op
def cam_area(sel=None):
    d = _doc()
    sources, area = _sub_shapes(sel)
    if area is not None and len(sel or []) == 1:          # a single FeatureArea selected: its area view
        v = d.addObject('Path::FeatureAreaView', 'FeatureAreaView'); v.Source = area; d.recompute(); return v.Name
    if not sources: raise RuntimeError('Select shapes, faces or edges')
    a = d.addObject('Path::FeatureArea', 'FeatureArea'); a.Sources = sources; d.recompute(); return a.Name

@op
def cam_area_workplane(sel=None):
    import PathCommands
    d = _doc(); area = None; plane = None
    for n, subs in (sel or []):
        o = d.getObject(n)
        if o is None: continue
        if len(subs or []) > 1: raise RuntimeError('Select one sub shape object for plane only')
        if not subs and o.isDerivedFrom('Path::FeatureArea'):
            if area is not None: raise RuntimeError('Select one feature area only')
            area = o; continue
        if not subs and o.Shape.Shells: raise RuntimeError('Selected shape is not 2D')
        if plane is not None: raise RuntimeError('Select one shape object for plane only')
        if subs and not subs[0].startswith(('Face', 'Edge')): raise RuntimeError('Selected shape is not 2D')
        plane = PathCommands.findShape(o.Shape, subs[0], 'Wires') if subs else PathCommands.findShape(o.Shape)
    if area is None: raise RuntimeError('Please select one FeatureArea')
    if plane is None: raise RuntimeError('Please select one shape object')
    area.WorkPlane = plane; d.recompute(); return area.Name

@op
def cam_compound(sel=None):
    d = _doc(); objs = [d.getObject(n) for n, _ in (sel or []) if d.getObject(n) is not None]
    if not objs: raise RuntimeError('At least one path object must be selected')
    if any(not o.isDerivedFrom('Path::Feature') for o in objs): raise RuntimeError('Only path objects must be selected before running this command')
    c = d.addObject('Path::FeatureCompound', 'PathCompound'); c.Group = objs; d.recompute(); return c.Name

@op
def cam_shape(sel=None):
    d = _doc()
    sources, _ = _sub_shapes(sel)
    if not sources: raise RuntimeError('Select shapes, faces or edges')
    s = d.addObject('Path::FeatureShape', 'PathShape'); s.Sources = sources; d.recompute(); return s.Name
