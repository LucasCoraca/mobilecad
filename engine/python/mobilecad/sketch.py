"""Sketcher operations for the mobile UI.  Geometry/constraint creation mirrors FreeCAD's DrawSketchHandler*
(src/Mod/Sketcher/Gui/DrawSketchHandler*.h) so sketches are identical to ones drawn on desktop."""
import math
import FreeCAD as App
import Part, Sketcher
from .bridge import op, _doc

V = App.Vector
# PointPos ids as stored in FreeCAD: none=0 start=1 end=2 mid=3
NONE, START, END, MID = 0, 1, 2, 3

def _sk(name):
    return _doc().getObject(name)

# ------------------------------------------------------------------ state for the UI
def _geo_json(g, i, sk):
    d = {'i': i, 'construction': sk.getConstruction(i) if i >= 0 else False, 'type': type(g).__name__}
    if i < 0: d['external'] = True
    if isinstance(g, Part.LineSegment):
        d.update(a=[g.StartPoint.x, g.StartPoint.y], b=[g.EndPoint.x, g.EndPoint.y])
    elif isinstance(g, Part.ArcOfCircle):
        d.update(c=[g.Center.x, g.Center.y], r=g.Radius, a0=g.FirstParameter, a1=g.LastParameter,
                 a=[g.StartPoint.x, g.StartPoint.y], b=[g.EndPoint.x, g.EndPoint.y])
    elif isinstance(g, Part.Circle):
        d.update(c=[g.Center.x, g.Center.y], r=g.Radius)
    elif isinstance(g, Part.Point):
        d.update(p=[g.X, g.Y])
    if isinstance(g, Part.BSplineCurve):
        # data for Sketcher_BSplineDegree / Polygon / KnotMultiplicity / PoleWeight / Comb overlays
        try:
            poles = g.getPoles(); knots = g.getKnots(); mults = g.getMultiplicities()
            comb = []
            a, b = g.FirstParameter, g.LastParameter; n = 60
            ks = [g.curvature(a + (b - a) * i / n) for i in range(n + 1)]; kmax = max(max(ks), 1e-9)
            scale = 0.25 * g.length() / kmax if g.length() > 0 else 1.0
            for i in range(n + 1):
                u = a + (b - a) * i / n; p0 = g.value(u)
                try: nn = g.normal(u)
                except Exception: continue
                p1 = p0 + nn * (-ks[i] * scale); comb.append([p0.x, p0.y, p1.x, p1.y])
            d['bspline'] = {'degree': g.Degree, 'poles': [[p.x, p.y] for p in poles], 'weights': list(g.getWeights()),
                            'knots': [[g.value(k).x, g.value(k).y, m] for k, m in zip(knots, mults)], 'comb': comb}
        except Exception:
            pass
    else:
        try:
            pts = g.discretize(64); d['poly'] = [[p.x, p.y] for p in pts]
        except Exception:
            d['poly'] = []
    return d

def _label(c):
    if c.Type in ('Distance', 'DistanceX', 'DistanceY', 'Radius', 'Diameter'):
        return App.Units.Quantity(c.Value, App.Units.Length).UserString
    if c.Type == 'Angle':
        return App.Units.Quantity(math.degrees(c.Value), App.Units.Angle).UserString
    return ''

@op
def sketch_state(name):
    sk = _sk(name)
    pl = sk.getGlobalPlacement()
    m = pl.toMatrix()
    cons = []
    for i, c in enumerate(sk.Constraints):
        cons.append({'i': i, 'type': c.Type, 'first': c.First, 'firstPos': c.FirstPos, 'second': c.Second, 'secondPos': c.SecondPos,
                     'third': c.Third, 'thirdPos': c.ThirdPos, 'value': c.Value, 'driving': c.Driving, 'active': c.IsActive,
                     'name': c.Name, 'virtual': c.InVirtualSpace, 'ld': c.LabelDistance, 'lp': c.LabelPosition,
                     'label': _label(c)})
    return {'placement': [m.A11, m.A12, m.A13, m.A14, m.A21, m.A22, m.A23, m.A24, m.A31, m.A32, m.A33, m.A34],
            'geometry': [_geo_json(g, i, sk) for i, g in enumerate(sk.Geometry)] + _external_json(sk),
            'external': len(sk.ExternalGeometry), 'constraints': cons,
            'dof': sk.DoF, 'fully': bool(sk.FullyConstrained),
            'conflicting': list(sk.ConflictingConstraints), 'redundant': list(sk.RedundantConstraints),
            'malformed': list(sk.MalformedConstraints), 'partially_redundant': list(sk.PartiallyRedundantConstraints)}

def _external_json(sk):
    """Projected / intersected external geometry (Sketcher_Projection / _Intersection): ExternalGeo[k] is geo id -(k+1);
    0 and 1 are the H / V axes."""
    out = []
    try: eg = sk.ExternalGeo
    except Exception: return out
    for k, g in enumerate(eg):
        if k < 2: continue
        try: out.append(_geo_json(g, -(k + 1), sk))
        except Exception: pass
    return out

@op
def sketch_model_refs(name):
    """Model vertices / edges lying on the sketch plane, in sketch coordinates, for snapping while drawing (snaps to part
    edges; FreeCAD needs them projected first - snapping projects them, see _ext_id).  Objects that depend on
    the sketch are skipped (a reference to them would be circular); in a Body only the Body's own features are used, as
    FreeCAD refuses cross-body external geometry by default."""
    sk = _sk(name); inv = sk.getGlobalPlacement().inverse()
    deps = {o.Name for o in sk.InListRecursive} | {sk.Name}
    body = next((o for o in sk.InList if o.isDerivedFrom('PartDesign::Body')), None)
    if body is not None:
        # the solid as it is where the sketch sits in the tree: the last solid feature before it (desktop shows the
        # same while the sketch is edited); later features depend on the sketch or come after it
        grp = list(body.Group); before = grp[:grp.index(sk)] if sk in grp else grp
        feats = [o for o in before if o.Name not in deps and o.isDerivedFrom('PartDesign::Feature')
                 and not o.isDerivedFrom('Sketcher::SketchObject') and not o.Shape.isNull()]
        cand = feats[-1:]
    else:
        cand = [o for o in _doc().Objects if o.Name not in deps and hasattr(o, 'Shape') and o.Visibility
                and not o.isDerivedFrom('Sketcher::SketchObject') and not o.isDerivedFrom('PartDesign::Body')
                and o.TypeId not in ('App::Line', 'App::Plane', 'App::Point', 'App::Origin', 'App::Part') and not o.Shape.isNull()]
    pts, curves = [], []
    for o in cand[:20]:
        sh = o.Shape; tol = max(1e-6, sh.BoundBox.DiagonalLength * 1e-6) * 10
        loc = lambda p: inv.multVec(p)
        for i, v in enumerate(sh.Vertexes, 1):
            q = loc(v.Point)
            if abs(q.z) < tol: pts.append(['%s:Vertex%d' % (o.Name, i), 'vertex', q.x, q.y])
            if len(pts) > 4000: break
        for i, e in enumerate(sh.Edges, 1):
            if len(curves) > 3000: break
            try:
                ps = [loc(p) for p in e.discretize(Deflection=max(tol, e.Length * 0.002))]
            except Exception: continue
            if not ps or any(abs(p.z) > tol for p in ps): continue
            ref = '%s:Edge%d' % (o.Name, i); c = e.Curve; d = {'ref': ref, 'pts': [[p.x, p.y] for p in ps]}
            if isinstance(c, Part.Line): d['kind'] = 'line'
            elif isinstance(c, Part.Circle):
                cc = loc(c.Center); d.update(kind='circle', c=[cc.x, cc.y], r=c.Radius)
            else: d['kind'] = 'curve'
            curves.append(d)
    return {'points': pts, 'curves': curves}

@op
def sketch_set_label(name, index, distance, position):
    """Dragging a dimension's label (ViewProviderSketch::moveConstraint): LabelDistance / LabelPosition are stored in the
    sketch, so the label stays where it was put (also on desktop)."""
    sk = _sk(name); sk.setLabelDistance(int(index), float(distance)); sk.setLabelPosition(int(index), float(position))
    return sketch_state(name)

def _commit(sk):
    sk.solve(); sk.recompute()
    return sketch_state(sk.Name)

# ------------------------------------------------------------------ geometry creators (same results as desktop tools)
def _add(sk, geos, cons, construction=False):
    first = sk.GeometryCount
    sk.addGeometry(geos, construction)
    out = []
    for c in cons:
        args = [a + first if isinstance(a, int) and k in c[2] else a for k, a in enumerate(c[1])]
        out.append(Sketcher.Constraint(c[0], *args))
    if out: sk.addConstraint(out)
    return first

def _ext_id(sk, ref):
    """A model element snapped to while drawing ("Pad:Edge3", implicit projection) -> its external geometry id,
    projecting it first like Sketcher_Projection when it is not external geometry yet.  ExternalGeo[0], [1] are the axes;
    the k-th projected element (ExternalGeometry order) is ExternalGeo[k + 2], i.e. geo id -(k + 3)."""
    obj, sub = ref.split(':', 1)
    def find():
        k = 0
        for o, subs in sk.ExternalGeometry:
            for x in subs:
                if o.Name == obj and x == sub: return -(k + 3)
                k += 1
        return None
    g = find()
    if g is None:
        obj, sub = _add_external(sk, obj, sub); g = find()
    return g

def _auto(sk, auto, first):
    """auto: list of [type, localGeo, pos, otherGeo, otherPos] produced by the UI's inference (FreeCAD autoconstraints).
    otherGeo may be a model element "Obj:SubName" (snapped to the part's edges / vertices): projected on demand."""
    cs = []
    auto = [list(a) for a in (auto or [])]
    for a in auto:
        if len(a) > 3 and isinstance(a[3], str) and ':' in a[3]: a[3] = _ext_id(sk, a[3])
    for a in auto:
        t = a[0]; g = a[1] + first; pos = a[2] if len(a) > 2 else None     # Horizontal / Vertical: [type, localGeo]
        if t == 'Coincident': cs.append(Sketcher.Constraint('Coincident', g, pos, a[3], a[4]))
        elif t == 'PointOnObject': cs.append(Sketcher.Constraint('PointOnObject', g, pos, a[3]))
        elif t in ('Horizontal', 'Vertical'): cs.append(Sketcher.Constraint(t, g))
        elif t == 'Tangent': cs.append(Sketcher.Constraint('Tangent', g, pos, a[3], a[4]))
        # midpoint inference (FreeCAD SnapManager::snapToLineMiddle): symmetric about the line's ends
        elif t == 'Symmetric': cs.append(Sketcher.Constraint('Symmetric', a[3], START, a[3], END, g, pos))
        # values typed while drawing (FreeCAD on-view parameters): [type, localGeo, value]; Angle in radians
        elif t in ('Distance', 'Radius', 'Diameter', 'Angle'): cs.append(Sketcher.Constraint(t, g, float(a[2])))
    if cs: sk.addConstraint(cs)

@op
def sketch_polyline(name, points, closed=False, construction=False, auto=None, chain=False):
    """chain=True: first point is coincident with the end of the previous geometry (continuing a polyline)."""
    sk = _sk(name); pts = [V(x, y, 0) for x, y in points]; prev = sk.GeometryCount - 1
    geos = [Part.LineSegment(a, b) for a, b in zip(pts, pts[1:])]
    if closed and len(pts) > 2: geos.append(Part.LineSegment(pts[-1], pts[0]))
    cons = [('Coincident', (i, END, i + 1, START), (0, 2)) for i in range(len(geos) - 1)]
    if closed and len(geos) > 2: cons.append(('Coincident', (len(geos) - 1, END, 0, START), (0, 2)))
    first = _add(sk, geos, cons, construction); _auto(sk, auto, first)
    if chain and prev >= 0: sk.addConstraint(Sketcher.Constraint('Coincident', first, START, prev, END))
    return _commit(sk)

@op
def sketch_rectangle(name, c1, c3, construction=False, auto=None):
    """DrawSketchHandlerRectangle, Diagonal method: CCW corners, 4 coincidences, H/V alignment."""
    sk = _sk(name); x1, y1 = c1; x3, y3 = c3
    if (x3 - x1) * (y3 - y1) > 0: c2, c4 = (x3, y1), (x1, y3); typeA, typeB = 'Horizontal', 'Vertical'
    else: c4, c2 = (x3, y1), (x1, y3); typeA, typeB = 'Vertical', 'Horizontal'
    cs = [V(*c1, 0), V(*c2, 0), V(x3, y3, 0), V(*c4, 0)]
    geos = [Part.LineSegment(cs[i], cs[(i + 1) % 4]) for i in range(4)]
    cons = [('Coincident', (i, END, (i + 1) % 4, START), (0, 2)) for i in range(4)]
    cons += [(typeA, (0,), (0,)), (typeA, (2,), (0,)), (typeB, (1,), (0,)), (typeB, (3,), (0,))]
    first = _add(sk, geos, cons, construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_center_rectangle(name, center, corner, construction=False, auto=None):
    """CenterAndCorner method: rectangle + construction center point, symmetric about it."""
    sk = _sk(name); cx, cy = center; x3, y3 = corner
    sk_first = sk.GeometryCount
    sketch_rectangle(name, (2 * cx - x3, 2 * cy - y3), (x3, y3), construction)
    sk = _sk(name)
    p = sk.addGeometry(Part.Point(V(cx, cy, 0)), True)
    sk.addConstraint(Sketcher.Constraint('Symmetric', sk_first, START, sk_first + 1, END, p, START))
    _auto(sk, auto, sk_first)
    return _commit(sk)

@op
def sketch_circle(name, center, radius, construction=False, auto=None):
    sk = _sk(name); first = _add(sk, [Part.Circle(V(*center, 0), V(0, 0, 1), radius)], [], construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_circle3(name, p1, p2, p3, construction=False, auto=None):
    sk = _sk(name); c = Part.Circle(V(*p1, 0), V(*p2, 0), V(*p3, 0))
    first = _add(sk, [Part.Circle(c.Center, V(0, 0, 1), c.Radius)], [], construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_arc_center(name, center, start, end, construction=False, auto=None):
    """DrawSketchHandlerArc (center, start, end), counter-clockwise from start."""
    sk = _sk(name); cx, cy = center; r = math.hypot(start[0] - cx, start[1] - cy)
    a0 = math.atan2(start[1] - cy, start[0] - cx); a1 = math.atan2(end[1] - cy, end[0] - cx)
    if a1 <= a0: a1 += 2 * math.pi
    arc = Part.ArcOfCircle(Part.Circle(V(cx, cy, 0), V(0, 0, 1), r), a0, a1)
    first = _add(sk, [arc], [], construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_arc3(name, start, end, onarc, construction=False, auto=None):
    """3-point arc (start, end, point on arc) — FreeCAD's Sketcher_Create3PointArc."""
    sk = _sk(name); a = Part.Arc(V(*start, 0), V(*onarc, 0), V(*end, 0)).toShape().Curve
    c = Part.Circle(V(a.Center.x, a.Center.y, 0), V(0, 0, 1), a.Radius)
    pa = math.atan2(start[1] - a.Center.y, start[0] - a.Center.x); pb = math.atan2(end[1] - a.Center.y, end[0] - a.Center.x)
    pm = math.atan2(onarc[1] - a.Center.y, onarc[0] - a.Center.x)
    norm = lambda t: t % (2 * math.pi)
    if norm(pm - pa) < norm(pb - pa): a0, a1 = pa, pa + norm(pb - pa)
    else: a0, a1 = pb, pb + norm(pa - pb)
    first = _add(sk, [Part.ArcOfCircle(c, a0, a1)], [], construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_point(name, p, auto=None):
    sk = _sk(name); first = _add(sk, [Part.Point(V(*p, 0))], []); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_slot(name, c1, c2, radius, construction=False, auto=None):
    """DrawSketchHandlerSlot: two arcs + two tangent lines + equal radii."""
    sk = _sk(name); (x1, y1), (x2, y2) = c1, c2; ang = math.atan2(y2 - y1, x2 - x1); r = radius
    arc1 = Part.ArcOfCircle(Part.Circle(V(x1, y1, 0), V(0, 0, 1), r), ang + math.pi / 2, ang + 3 * math.pi / 2)
    arc2 = Part.ArcOfCircle(Part.Circle(V(x2, y2, 0), V(0, 0, 1), r), ang - math.pi / 2, ang + math.pi / 2)
    n = V(-math.sin(ang), math.cos(ang), 0) * r
    l1 = Part.LineSegment(V(x1, y1, 0) + n, V(x2, y2, 0) + n); l2 = Part.LineSegment(V(x1, y1, 0) - n, V(x2, y2, 0) - n)
    cons = [('Tangent', (0, START, 2, START), (0, 2)), ('Tangent', (0, END, 3, START), (0, 2)),
            ('Tangent', (1, START, 3, END), (0, 2)), ('Tangent', (1, END, 2, END), (0, 2)), ('Equal', (0, 1), (0, 1))]
    first = _add(sk, [arc1, arc2, l1, l2], cons, construction); _auto(sk, auto, first)
    return _commit(sk)

@op
def sketch_polygon(name, center, corner, sides, construction=False, auto=None):
    """DrawSketchHandlerRegularPolygon: n lines + construction circumcircle, PointOnObject + Equal."""
    sk = _sk(name); cx, cy = center; r = math.hypot(corner[0] - cx, corner[1] - cy); a0 = math.atan2(corner[1] - cy, corner[0] - cx)
    pts = [V(cx + r * math.cos(a0 + 2 * math.pi * k / sides), cy + r * math.sin(a0 + 2 * math.pi * k / sides), 0) for k in range(sides)]
    geos = [Part.LineSegment(pts[k], pts[(k + 1) % sides]) for k in range(sides)]
    cons = [('Coincident', (k, END, (k + 1) % sides, START), (0, 2)) for k in range(sides)]
    first = _add(sk, geos, cons, construction)
    circ = sk.addGeometry(Part.Circle(V(cx, cy, 0), V(0, 0, 1), r), True)
    cs = [Sketcher.Constraint('PointOnObject', first + k, START, circ) for k in range(sides)]
    cs += [Sketcher.Constraint('Equal', first, first + k) for k in range(1, sides)]
    sk.addConstraint(cs); _auto(sk, auto, first)
    return _commit(sk)

# ------------------------------------------------------------------ editing
@op
def sketch_constrain(name, ctype, args, value=None):
    """Adds Sketcher.Constraint(ctype, *args[, value]).  value may be a quantity string ('25 mm', '30 deg')."""
    sk = _sk(name); a = list(args)
    if value is not None:
        q = App.Units.Quantity(value) if isinstance(value, str) else value
        a.append(q)
    sk.addConstraint(Sketcher.Constraint(ctype, *a))
    return _commit(sk)

@op
def sketch_set_datum(name, index, value):
    sk = _sk(name); sk.setDatum(index, App.Units.Quantity(value) if isinstance(value, str) else value)
    return _commit(sk)

@op
def sketch_set_datum_expression(name, index, expression):
    sk = _sk(name); sk.setExpression('Constraints[%d]' % index, expression or None)
    return _commit(sk)

@op
def sketch_toggle_driving(name, index):
    sk = _sk(name); sk.toggleDriving(index); return _commit(sk)

@op
def sketch_rename_constraint(name, index, label):
    """Constraint list ▸ Rename (named constraints can be used in expressions: Sketch.Constraints.width)."""
    sk = _sk(name); sk.renameConstraint(int(index), label or ''); return _commit(sk)

@op
def sketch_delete(name, geometry=(), constraints=()):
    sk = _sk(name)
    if constraints: sk.delConstraints(sorted(constraints, reverse=True))
    own = sorted(g for g in geometry if g >= 0); ext = sorted((g for g in geometry if g <= -3), reverse=True)
    if own: sk.delGeometries(own)
    for g in ext: sk.delExternal(-g - 3)          # external geometry -3, -4 ... is ExternalGeometry[0, 1 ...]
    return _commit(sk)

@op
def sketch_toggle_construction(name, geometry):
    sk = _sk(name)
    for g in geometry: sk.toggleConstruction(g)
    return _commit(sk)

@op
def sketch_move_point(name, geo, pos, x, y, relative=False):
    """Drag a point/edge like desktop (unconstrained DoF move with the solver)."""
    sk = _sk(name); sk.moveGeometry(geo, pos, V(x, y, 0), relative)
    return _commit(sk)

@op
def sketch_trim(name, geo, x, y):
    sk = _sk(name); sk.trim(geo, V(x, y, 0)); return _commit(sk)

@op
def sketch_fillet(name, geo1, geo2, p1, p2, radius):
    sk = _sk(name); sk.fillet(geo1, geo2, V(*p1, 0), V(*p2, 0), radius, True, True); return _commit(sk)

@op
def sketch_external(name, obj, sub):
    sk = _sk(name); sk.addExternal(obj, sub); return _commit(sk)

# ======================================================================================================================
# Remaining Sketcher tools.  Creators finish like DrawSketchHandler* (addGeometry + exposeInternalGeometry for conics and
# B-splines); editing tools call the same SketchObject methods the desktop commands call.
# ======================================================================================================================
def _expose(sk, g):
    sk.exposeInternalGeometry(g)

@op
def sketch_ellipse(name, center, major, minor_pt, construction=False):
    """Sketcher_CreateEllipseByCenter: center, end of major axis, a point fixing the minor radius."""
    sk = _sk(name); c = V(*center, 0); a = V(*major, 0)
    ax = a - c; R = ax.Length
    if R < 1e-9: raise ValueError('degenerate ellipse')
    d = ax.normalize(); p = V(*minor_pt, 0) - c
    r = abs(-d.y * p.x + d.x * p.y)
    if r < 1e-9 or r >= R: r = min(max(r, R * 0.1), R * 0.999)
    e = Part.Ellipse(c + d * R, c + V(-d.y, d.x, 0) * r, c)
    g = sk.addGeometry(e, construction); _expose(sk, g)
    return _commit(sk)

@op
def sketch_ellipse3(name, p1, p2, p3, construction=False):
    """Sketcher_CreateEllipseBy3Points: two ends of the major axis + a point on the ellipse."""
    c = [(p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2]
    return sketch_ellipse(name, c, p1, p3, construction)

@op
def sketch_arc_of_ellipse(name, center, major, start, end, construction=False):
    """Sketcher_CreateArcOfEllipse (center, major axis end, start, end)."""
    sk = _sk(name); c = V(*center, 0); ax = V(*major, 0) - c; R = ax.Length; d = V(ax).normalize()
    ps = V(*start, 0) - c
    lx, ly = ps.dot(d), -d.y * ps.x + d.x * ps.y
    r = abs(ly) / math.sqrt(max(1e-12, 1 - min(0.999999, (lx / R) ** 2)))
    r = min(max(r, R * 0.05), R * 0.999)
    e = Part.Ellipse(c + d * R, c + V(-d.y, d.x, 0) * r, c)
    def param(p):
        q = V(*p, 0) - c; return math.atan2((-d.y * q.x + d.x * q.y) / r, q.dot(d) / R)
    a0, a1 = param(start), param(end)
    if a1 <= a0: a1 += 2 * math.pi
    g = sk.addGeometry(Part.ArcOfEllipse(e, a0, a1), construction); _expose(sk, g)
    return _commit(sk)

@op
def sketch_arc_of_hyperbola(name, center, major, minor_pt, end, construction=False):
    """Sketcher_CreateArcOfHyperbola (center, major vertex, minor point, end of arc)."""
    sk = _sk(name); c = V(*center, 0); ax = V(*major, 0) - c; A = ax.Length; d = V(ax).normalize()
    q = V(*minor_pt, 0) - c; B = max(abs(-d.y * q.x + d.x * q.y), A * 0.05)
    h = Part.Hyperbola(c + d * A, c + V(-d.y, d.x, 0) * B, c)
    pe = V(*end, 0) - c; t = math.asinh((-d.y * pe.x + d.x * pe.y) / B)
    a0, a1 = (-abs(t), abs(t)) if abs(t) > 1e-6 else (-1.0, 1.0)
    g = sk.addGeometry(Part.ArcOfHyperbola(h, a0, a1), construction); _expose(sk, g)
    return _commit(sk)

@op
def sketch_arc_of_parabola(name, focus, vertex, start, end, construction=False):
    """Sketcher_CreateArcOfParabola (focus, vertex, start, end)."""
    sk = _sk(name); f = V(*focus, 0); v = V(*vertex, 0); ax = f - v; F = ax.Length; d = V(ax).normalize()
    p = Part.Parabola(); p.Focal = F; p.Center = v; p.Axis = V(0, 0, 1); p.XAxis = d
    def param(pt):
        q = V(*pt, 0) - v; return -d.y * q.x + d.x * q.y
    a0, a1 = sorted((param(start), param(end)))
    if abs(a1 - a0) < 1e-6: a1 = a0 + F
    g = sk.addGeometry(Part.ArcOfParabola(p, a0, a1), construction); _expose(sk, g)
    return _commit(sk)

@op
def sketch_bspline(name, poles, periodic=False, interpolate=False, construction=False):
    """Sketcher_CreateBSpline / CreatePeriodicBSpline (control points) and ...ByInterpolation (knots), degree 3 like desktop."""
    sk = _sk(name); pts = [V(x, y, 0) for x, y in poles]
    bs = Part.BSplineCurve()
    if interpolate:
        bs.interpolate(Points=pts, PeriodicFlag=periodic)
    else:
        deg = min(3, len(pts) - 1)
        bs.buildFromPoles(pts, bool(periodic), deg)
    g = sk.addGeometry(bs, construction); _expose(sk, g)
    return _commit(sk)

@op
def sketch_oblong(name, c1, c3, radius=None, construction=False):
    """Sketcher_CreateOblong (rounded rectangle): 4 lines + 4 corner arcs, tangent, equal radii, H/V like DrawSketchHandlerRectangle."""
    sk = _sk(name); x1, x3 = sorted((c1[0], c3[0])); y1, y3 = sorted((c1[1], c3[1]))
    w, h = x3 - x1, y3 - y1; r = radius if radius else 0.2 * min(w, h)
    r = max(1e-3, min(r, 0.49 * min(w, h)))
    first = sk.GeometryCount
    L = [Part.LineSegment(V(x1 + r, y1, 0), V(x3 - r, y1, 0)), Part.LineSegment(V(x3, y1 + r, 0), V(x3, y3 - r, 0)),
         Part.LineSegment(V(x3 - r, y3, 0), V(x1 + r, y3, 0)), Part.LineSegment(V(x1, y3 - r, 0), V(x1, y1 + r, 0))]
    cs = [V(x3 - r, y1 + r, 0), V(x3 - r, y3 - r, 0), V(x1 + r, y3 - r, 0), V(x1 + r, y1 + r, 0)]
    A = [Part.ArcOfCircle(Part.Circle(cs[k], V(0, 0, 1), r), -math.pi / 2 + k * math.pi / 2, k * math.pi / 2) for k in range(4)]
    sk.addGeometry(L + A, construction)
    cons = []
    for k in range(4):
        cons.append(Sketcher.Constraint('Tangent', first + k, END, first + 4 + k, START))
        cons.append(Sketcher.Constraint('Tangent', first + 4 + k, END, first + (k + 1) % 4, START))
    cons += [Sketcher.Constraint('Horizontal', first), Sketcher.Constraint('Horizontal', first + 2),
             Sketcher.Constraint('Vertical', first + 1), Sketcher.Constraint('Vertical', first + 3)]
    cons += [Sketcher.Constraint('Equal', first + 4, first + 4 + k) for k in range(1, 4)]
    sk.addConstraint(cons)
    return _commit(sk)

@op
def sketch_arc_slot(name, center, start, end, width, construction=False):
    """Sketcher_CreateArcSlot: concentric arcs joined by two end caps (tangent), as DrawSketchHandlerArcSlot."""
    sk = _sk(name); c = V(*center, 0); R = math.hypot(start[0] - center[0], start[1] - center[1])
    a0 = math.atan2(start[1] - center[1], start[0] - center[0]); a1 = math.atan2(end[1] - center[1], end[0] - center[0])
    if a1 <= a0: a1 += 2 * math.pi
    w = max(1e-3, min(abs(width), R * 0.99))
    first = sk.GeometryCount
    outer = Part.ArcOfCircle(Part.Circle(c, V(0, 0, 1), R + w / 2), a0, a1)
    inner = Part.ArcOfCircle(Part.Circle(c, V(0, 0, 1), R - w / 2), a0, a1)
    pc0 = c + V(math.cos(a0), math.sin(a0), 0) * R; pc1 = c + V(math.cos(a1), math.sin(a1), 0) * R
    cap0 = Part.ArcOfCircle(Part.Circle(pc0, V(0, 0, 1), w / 2), a0 + math.pi, a0 + 2 * math.pi)
    cap1 = Part.ArcOfCircle(Part.Circle(pc1, V(0, 0, 1), w / 2), a1, a1 + math.pi)
    sk.addGeometry([outer, inner, cap0, cap1], construction)
    sk.addConstraint([Sketcher.Constraint('Coincident', first, MID, first + 1, MID),
                      Sketcher.Constraint('Tangent', first + 2, START, first, START), Sketcher.Constraint('Tangent', first + 2, END, first + 1, START),
                      Sketcher.Constraint('Tangent', first + 3, START, first, END), Sketcher.Constraint('Tangent', first + 3, END, first + 1, END)])
    return _commit(sk)

# ---------------------------------------------------------------------------------------------------- editing tools
@op
def sketch_fillet_vertex(name, geo, pos, radius, chamfer=False):
    """Sketcher_CreateFillet / Sketcher_CreateChamfer on a corner vertex (trim, no corner point)."""
    sk = _sk(name); sk.fillet(geo, pos, float(radius), True, False, bool(chamfer)); return _commit(sk)

@op
def sketch_extend(name, geo, increment, end_point):
    sk = _sk(name); sk.extend(geo, float(increment), end_point); return _commit(sk)

@op
def sketch_split(name, geo, x, y):
    sk = _sk(name); sk.split(geo, V(x, y, 0)); return _commit(sk)

@op
def sketch_join(name, geo1, pos1, geo2, pos2):
    sk = _sk(name); sk.join(geo1, pos1, geo2, pos2); return _commit(sk)

@op
def sketch_symmetry(name, geometry, ref_geo, ref_pos=0):
    """Sketcher_Symmetry: mirrored copies about a line / point (ref_pos 0 = edge)."""
    sk = _sk(name); sk.addSymmetric(list(geometry), ref_geo, ref_pos); return _commit(sk)

@op
def sketch_translate(name, geometry, dx, dy, copy=False):
    """Sketcher_Translate: move (or copy) the selected geometry by a vector."""
    sk = _sk(name)
    if copy: sk.addCopy(list(geometry), V(dx, dy, 0), False)
    else: sk.addMove(list(geometry), V(dx, dy, 0))
    return _commit(sk)

@op
def sketch_array(name, geometry, dx, dy, rows, cols):
    """Sketcher_RectangularArray."""
    sk = _sk(name); sk.addRectangularArray(list(geometry), V(dx, dy, 0), False, int(rows), int(cols), True, 1.0); return _commit(sk)

def _transform_geos(sk, geometry, mat, copy):
    new = []
    for g in geometry:
        geo = sk.Geometry[g].copy(); geo.transform(mat); new.append(geo)
    constr = [sk.getConstruction(g) for g in geometry]
    if not copy: sk.delGeometries(sorted(geometry))
    for geo, c in zip(new, constr): sk.addGeometry(geo, c)

@op
def sketch_rotate(name, geometry, cx, cy, angle_deg, copy=False):
    """Sketcher_Rotate: rotate (or copy) selected geometry about a centre."""
    sk = _sk(name); m = App.Matrix(); m.move(V(-cx, -cy, 0)); r = App.Matrix(); r.rotateZ(math.radians(angle_deg)); m = r.multiply(m)
    t = App.Matrix(); t.move(V(cx, cy, 0)); _transform_geos(sk, geometry, t.multiply(m), copy); return _commit(sk)

@op
def sketch_scale(name, geometry, cx, cy, factor, copy=False):
    """Sketcher_Scale: scale selected geometry about a centre."""
    sk = _sk(name); m = App.Matrix(); m.move(V(-cx, -cy, 0)); s = App.Matrix(); s.scale(float(factor), float(factor), 1); m = s.multiply(m)
    t = App.Matrix(); t.move(V(cx, cy, 0)); _transform_geos(sk, geometry, t.multiply(m), copy); return _commit(sk)

@op
def sketch_offset(name, geometry, distance, construction=False):
    """Sketcher_Offset: offset curves of the selected (connected) geometry, via Part offset2D like DrawSketchHandlerOffset."""
    sk = _sk(name); edges = [sk.Geometry[g].toShape() for g in geometry]
    wires = Part.sortEdges(edges)
    for w in wires:
        wire = Part.Wire(w)
        try: off = wire.makeOffset2D(float(distance), 2, False, not wire.isClosed(), False)
        except Exception: off = wire.makeOffset2D(float(distance))
        for e in off.Edges: sk.addGeometry(e.Curve.trim(*e.ParameterRange) if hasattr(e.Curve, 'trim') else e.Curve, construction)
    return _commit(sk)

@op
def sketch_remove_axes_alignment(name, geometry):
    sk = _sk(name); sk.removeAxesAlignment(list(geometry)); return _commit(sk)

from .partdesign import same_element as _same_element  # noqa: E402

def _add_external(sk, obj, sub, defining=False, intersection=False):
    """Sketcher_Projection, plus what a desktop user does by hand when FreeCAD refuses geometry from outside the sketch's
    Body ("... is not allowed as external geometry"): use the same element of the Body's own solid if it exists, otherwise
    link it into the Body with a SubShapeBinder (PartDesign_SubShapeBinder) and project the binder."""
    try:
        sk.addExternal(obj, sub, bool(defining), bool(intersection)); return obj, sub
    except Exception as err:
        body = next((o for o in sk.InList if o.isDerivedFrom('PartDesign::Body')), None)
        src = _doc().getObject(obj)
        if body is None or src is None: raise
        deps = {o.Name for o in sk.InListRecursive}
        grp = list(body.Group); before = grp[:grp.index(sk)] if sk in grp else grp
        solids = [o for o in before if o.Name not in deps and o.isDerivedFrom('PartDesign::Feature') and not o.Shape.isNull()]
        if solids:
            m = _same_element(src, sub, solids[-1])
            if m:
                sk.addExternal(solids[-1].Name, m, bool(defining), bool(intersection)); return solids[-1].Name, m
        if src.Name in deps:
            raise ValueError('This %s exists only on %s, which is built from this Body, so a sketch inside the Body cannot use it '
                             '(it would be circular). Use PartDesign features (e.g. PartDesign Fillet) inside the Body, or pick an '
                             'edge that also exists on %s.' % (sub.rstrip('0123456789').lower(), src.Label, solids[-1].Label if solids else 'the Body')) from err
        b = body.newObject('PartDesign::SubShapeBinder', 'Binder')
        b.Support = [(src, (sub,))]; b.Visibility = False
        # keep it before the sketch so the sketch may use it
        try: body.removeObject(b); body.insertObject(b, sk, False)
        except Exception: pass
        b.recompute()
        k = 'Vertex1' if sub.startswith('Vertex') else 'Edge1' if sub.startswith('Edge') else 'Face1'
        if sub.startswith('Face'):
            # a face projects as its outline
            for i in range(1, len(b.Shape.Edges) + 1): sk.addExternal(b.Name, 'Edge%d' % i, bool(defining), bool(intersection))
            return b.Name, 'Edge1'
        sk.addExternal(b.Name, k, bool(defining), bool(intersection)); return b.Name, k

@op
def sketch_external(name, obj, sub, intersection=False, defining=False):
    """Sketcher_Projection / Sketcher_Intersection (external geometry)."""
    sk = _sk(name); _add_external(sk, obj, sub, defining, intersection); return _commit(sk)

@op
def sketch_carbon_copy(name, other, construction=True):
    sk = _sk(name); sk.carbonCopy(other, bool(construction)); return _commit(sk)

@op
def sketch_bspline_op(name, op, geo, value=None):
    """B-spline tools: ConvertToNURBS, Increase/DecreaseDegree, Increase/DecreaseKnotMultiplicity, InsertKnot, JoinCurves handled separately."""
    sk = _sk(name)
    if op == 'ConvertToNURBS': sk.convertToNURBS(geo)
    elif op == 'IncreaseDegree': sk.increaseBSplineDegree(geo, 1)
    elif op == 'DecreaseDegree': sk.decreaseBSplineDegree(geo, 1)
    elif op in ('IncreaseKnotMultiplicity', 'DecreaseKnotMultiplicity'):
        sk.modifyBSplineKnotMultiplicity(geo, int(value or 1), 1 if op.startswith('Increase') else -1)
    elif op == 'InsertKnot': sk.insertBSplineKnot(geo, float(value), 1)
    return _commit(sk)

@op
def sketch_toggle(name, what, index):
    """Sketcher_ToggleDrivingConstraint / ToggleActiveConstraint / SwitchVirtualSpace / RestoreInternalAlignmentGeometry."""
    sk = _sk(name)
    if what == 'driving': sk.toggleDriving(index)
    elif what == 'active': sk.toggleActive(index)
    elif what == 'virtual': sk.toggleVirtualSpace(index)
    elif what == 'internal': sk.exposeInternalGeometry(index)
    return _commit(sk)

@op
def sketch_validate(name):
    """Sketcher_ValidateSketch: add missing coincidences and horizontal/vertical constraints, remove redundants."""
    sk = _sk(name)
    n1 = sk.detectMissingPointOnPointConstraints(); sk.makeMissingPointOnPointCoincident()
    n2 = sk.detectMissingVerticalHorizontalConstraints(); sk.makeMissingVerticalHorizontal()
    sk.autoRemoveRedundants(True)
    st = _commit(sk); st['validated'] = [n1, n2]; return st

@op
def sketch_delete_all(name, what):
    sk = _sk(name)
    if what == 'geometry': sk.deleteAllGeometry()
    else: sk.deleteAllConstraints()
    return _commit(sk)

@op
def sketch_reorient(name, plane, reverse=False):
    """Sketcher_ReorientSketch: attach to an origin plane of the body."""
    from .partdesign import _body_of, _origin
    sk = _sk(name); b = _body_of(sk)
    sk.AttachmentSupport = (_origin(b, plane), ['']) if b else None
    sk.MapMode = 'FlatFace'; sk.MapReversed = bool(reverse)
    _doc().recompute(); return sketch_state(name)

@op
def sketch_merge(names):
    """Sketcher_MergeSketches: new sketch containing all geometry and constraints of the selected sketches."""
    d = _doc(); src = [d.getObject(n) for n in names]
    m = d.addObject('Sketcher::SketchObject', 'Sketch')
    m.Placement = src[0].Placement
    for s in src:
        off = m.GeometryCount
        m.addGeometry(list(s.Geometry))
        for c in s.Constraints:
            c = c.clone() if hasattr(c, 'clone') else c
            for a in ('First', 'Second', 'Third'):
                v = getattr(c, a, -2000)
                if v >= 0: setattr(c, a, v + off)
            m.addConstraint(c)
    for b in [p for p in src[0].InList if p.TypeId == 'PartDesign::Body'][:1]: b.addObject(m)
    d.recompute(); return m.Name

@op
def sketch_mirror(names, axis='V_Axis'):
    """Sketcher_MirrorSketch: mirrored copy of each sketch about its V axis / H axis / origin."""
    d = _doc(); out = []
    for n in names:
        s = d.getObject(n); m = d.addObject('Sketcher::SketchObject', s.Name + '_mirror'); m.Placement = s.Placement
        m.addGeometry(list(s.Geometry))
        ref = {'V_Axis': (-2, 0), 'H_Axis': (-1, 0), 'RootPoint': (-1, 1)}[axis]
        m.addSymmetric(list(range(len(s.Geometry))), ref[0], ref[1])
        m.delGeometries(list(range(len(s.Geometry))))
        out.append(m.Name)
    d.recompute(); return out

@op
def sketch_insert_knot(name, geo, x, y):
    """Sketcher_BSplineInsertKnot at the tapped location (parameter of the closest curve point)."""
    sk = _sk(name); c = sk.Geometry[geo]
    sk.insertBSplineKnot(geo, c.parameter(V(x, y, 0)), 1); return _commit(sk)

@op
def sketch_knot_multiplicity(name, point_geo, delta):
    """Sketcher_BSpline{Increase,Decrease}KnotMultiplicity on a knot point (internal alignment BSplineKnotPoint)."""
    sk = _sk(name)
    # knots are exposed as Part.Point internal geometry (poles are circles), aligned to the B-spline with an index
    for c in sk.Constraints:
        if c.Type == 'InternalAlignment' and c.First == point_geo and isinstance(sk.Geometry[point_geo], Part.Point) \
                and isinstance(sk.Geometry[c.Second], Part.BSplineCurve):
            bs = sk.Geometry[c.Second]; pt = sk.Geometry[point_geo]; pv = V(pt.X, pt.Y, 0)
            knots = bs.getKnots()
            k = min(range(len(knots)), key=lambda i: (bs.value(knots[i]) - pv).Length)   # knot index by position (1-based below)
            sk.modifyBSplineKnotMultiplicity(c.Second, k + 1, int(delta)); return _commit(sk)
    raise ValueError('Select a B-spline knot point')

_clip = []
@op
def sketch_clipboard(name, action, geometry=(), dx=0.0, dy=0.0):
    """Sketcher_CopyClipboard / Sketcher_Cut / Sketcher_Paste (geometry clipboard shared between sketches)."""
    global _clip
    sk = _sk(name)
    if action in ('copy', 'cut'):
        _clip = [(sk.Geometry[g].copy(), sk.getConstruction(g)) for g in geometry]
        if action == 'cut': sk.delGeometries(sorted(geometry))
    elif action == 'paste':
        for g, c in _clip:
            g = g.copy(); g.translate(V(dx, dy, 0)); sk.addGeometry(g, c)
    return _commit(sk)

@op
def sketch_group(name, geometry):
    """Sketcher_ConstrainGroup: construction frame line along the selection bounding box + 'Group' constraint (as CmdSketcherConstrainGroup)."""
    sk = _sk(name); geometry = [g for g in geometry if g >= 0]
    if len(geometry) < 2: raise ValueError('Select at least two geometries')
    bb = None
    for g in geometry:
        b = sk.Geometry[g].toShape().BoundBox
        bb = b if bb is None else bb.united(b)
    frame = sk.addGeometry(Part.LineSegment(V(bb.XMin, bb.YMin, 0), V(bb.XMin, bb.YMax, 0)), True)
    elements = [frame, 0]
    for g in geometry: elements += [g, 0]
    sk.addConstraint(Sketcher.Constraint('Group', elements))
    return _commit(sk)

ANDROID_FONT = '/system/fonts/Roboto-Regular.ttf'

@op
def sketch_text(name, start, end, text, font=None, height=True, construction=False):
    """Sketcher_CreateText (DrawSketchHandlerText): handle line, 'Text' constraint, then setTextAndFont generates the outlines."""
    sk = _sk(name); font = font or ANDROID_FONT
    handle = sk.addGeometry(Part.LineSegment(V(*start, 0), V(*end, 0)), True)
    sk.addConstraint(Sketcher.Constraint('Text', [handle, 0], text, font, bool(height)))
    sk.setTextAndFont(len(sk.Constraints) - 1, text, font, bool(height), bool(construction))
    return _commit(sk)

@op
def sketch_snells_law(name, geo1, pos1, geo2, pos2, boundary, ratio=1.0):
    """Sketcher_ConstrainSnellsLaw: ray end points meeting on a boundary curve, refraction index ratio n2/n1."""
    sk = _sk(name)
    sk.addConstraint(Sketcher.Constraint('SnellsLaw', geo1, pos1, geo2, pos2, boundary, float(ratio)))
    return _commit(sk)

@op
def sketch_dof_elements(name):
    """Sketcher_SelectElementsWithDoFs: (geo, pos) pairs that still have degrees of freedom."""
    sk = _sk(name); sk.solve()
    return [[g, p] for g, p in sk.getGeometryWithDependentParameters()]
