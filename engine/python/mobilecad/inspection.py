"""Inspection workbench (src/Mod/Inspection): Inspection_VisualInspection creates Inspection::Feature objects (actual vs
nominal geometry, search radius, thickness) whose Distances the desktop view provider shows as a colour map on the actual
geometry's sample points; Inspection_InspectElement reads the distance at a picked point."""
import FreeCAD as App
from .bridge import op, _doc

def samples(o):
    """The actual geometry's sample points in the order Inspection::Feature computes Distances (InspectActualMesh /
    InspectActualPoints / InspectActualShape: mesh points, cloud points, or the shape's faces tessellated at
    TopoShape::getAccuracy()), its triangles (if any), the distances and the colour range (search radius)."""
    a = o.Actual; D = list(o.Distances); r = float(o.SearchRadius) or 1.0
    if a.isDerivedFrom('Mesh::Feature'):
        m = a.Mesh.copy(); m.transform(a.Placement.toMatrix())
        pts, facets = m.Topology
        return [App.Vector(*p) for p in pts], [tuple(f) for f in facets], D, r
    if a.isDerivedFrom('Points::Feature'):
        pl = a.Placement
        return [pl.multVec(App.Vector(*p)) for p in a.Points.Points], [], D, r
    sh = a.Shape
    bb = sh.BoundBox; acc = (bb.XLength + bb.YLength + bb.ZLength) / 300.0 * 0.2
    if sh.Faces:
        pts, facets = sh.tessellate(acc)
        return list(pts), [tuple(f) for f in facets], D, r
    pts = [v.Point for v in sh.Vertexes]
    return pts, [], D, r

def color(d, r):
    """ViewProviderInspection's colour bar: blue (-radius) .. green (0) .. red (+radius); grey where nothing was found."""
    if abs(d) > 1e29: return (0.6, 0.6, 0.6)
    t = max(-1.0, min(1.0, d / r))
    if t < 0: return (0.0, 1.0 + t, -t) if t > -1 else (0.0, 0.0, 1.0)
    return (t, 1.0 - t, 0.0)

@op
def inspection_create(actual, nominals, radius=0.05, thickness=0.0):
    """TaskVisualInspection::accept: an Inspection::Group with one Inspection::Feature per actual geometry."""
    d = _doc()
    g = d.addObject('Inspection::Group', 'Inspection'); out = []
    for n in actual:
        f = g.newObject('Inspection::Feature', n + '_Inspect')
        f.Actual = d.getObject(n); f.SearchRadius = float(radius); f.Thickness = float(thickness)
        f.Nominals = [d.getObject(x) for x in nominals if d.getObject(x) is not None]
        out.append(f.Name)
    d.recompute()
    for n in list(actual) + list(nominals):
        if d.getObject(n) is not None: d.getObject(n).Visibility = False
    return out

@op
def inspection_value(name, point):
    """Inspection_InspectElement: distance at the sample point nearest the picked point."""
    o = _doc().getObject(name)
    P, _, D, _ = samples(o)
    q = App.Vector(*point)
    i = min(range(len(P)), key=lambda k: (P[k] - q).Length) if P else -1
    if i < 0 or i >= len(D): return None
    d = D[i]
    return {'distance': None if abs(d) > 1e29 else d, 'point': list(P[i])}
