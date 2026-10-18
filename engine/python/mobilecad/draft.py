"""Draft workbench for the mobile UI.  The desktop tools (src/Mod/Draft/draftguitools/gui_*.py) collect points with the
Snapper on the working plane and then call the Draft API; the app collects the same points by touch and these ops run
the same Draft API calls with the same arguments."""
import math
import FreeCAD as App
from FreeCAD import Vector as V
from .bridge import op, _doc
from .partdesign import _sel

def _objs(names):
    d = _doc(); return [d.getObject(n) for n in names if d.getObject(n) is not None]

def _wp(wp):
    """WorkingPlane.PlaneBase from {'pos','u','v','w'} (the app's working plane)."""
    import WorkingPlane
    p = WorkingPlane.PlaneBase()
    if wp:
        p.u = V(*wp['u']); p.v = V(*wp['v']); p.axis = V(*wp['w']); p.position = V(*wp['pos'])
        # Draft functions that consult the global working plane (mirror, dimensions ...) see the same plane
        g = WorkingPlane.get_working_plane(update=False)
        g.u, g.v, g.axis, g.position = V(p.u), V(p.v), V(p.axis), V(p.position)
    return p

def _pts(pts):
    return [V(*p) for p in pts]

def _fill():
    from draftutils import params
    return params.get_param("fillmode")

def _pl(wp, base):
    pl = wp.get_placement(); pl.Base = base; return pl

# ------------------------------------------------------------------ creation tools
@op
def draft_make(tool, pts, wp=None, opts=None):
    import Draft, DraftVecUtils
    o = opts or {}; P = _pts(pts); w = _wp(wp); fill = _fill()
    if tool in ('Line', 'Wire'):
        closed = bool(o.get('closed')) or (len(P) > 2 and P[0].isEqual(P[-1], 1e-7))
        if closed and P[0].isEqual(P[-1], 1e-7): P = P[:-1]
        f = Draft.make_wire(P, closed=closed, face=fill if closed else False)
    elif tool == 'Rectangle':                     # gui_rectangles.Rectangle.createObject
        p1, p3 = P[0], P[-1]; diag = p3.sub(p1)
        p2 = p1.add(DraftVecUtils.project(diag, w.v)); p4 = p1.add(DraftVecUtils.project(diag, w.u))
        length = p4.sub(p1).Length; height = p2.sub(p1).Length
        if abs(DraftVecUtils.angle(p4.sub(p1), w.u, w.axis)) > 1: length = -length
        if abs(DraftVecUtils.angle(p2.sub(p1), w.v, w.axis)) > 1: height = -height
        base = p1
        if length < 0: length = -length; base = base.add(p1.sub(p4).negative())
        if height < 0: height = -height; base = base.add(p1.sub(p2).negative())
        f = Draft.make_rectangle(length=length, height=height, placement=_pl(w, base), face=fill)
    elif tool == 'Circle':                        # gui_circles: centre, then a point on the circle
        f = Draft.make_circle(P[1].sub(P[0]).Length, placement=_pl(w, P[0]), face=fill)
    elif tool == 'Arc':                           # gui_arcs.Arc: centre, start point (radius), end point
        c = P[0]; r = P[1].sub(c).Length
        a1 = math.degrees(DraftVecUtils.angle(P[1].sub(c), w.u, w.axis)) * -1
        a2 = math.degrees(DraftVecUtils.angle(P[2].sub(c), w.u, w.axis)) * -1
        f = Draft.make_circle(r, placement=_pl(w, c), face=False, startangle=a1, endangle=a2)
    elif tool == 'Arc_3Points':
        f = Draft.make_arc_3points(P[:3])
    elif tool == 'Ellipse':                       # gui_ellipses: the two picked points are opposite rectangle corners
        p1, p3 = P[0], P[-1]; diag = p3.sub(p1)
        a = abs(DraftVecUtils.project(diag, w.u).Length) / 2; b = abs(DraftVecUtils.project(diag, w.v).Length) / 2
        pl = _pl(w, p1.add(diag.multiply(0.5)))
        if b > a:
            a, b = b, a
            pl.Rotation = App.Rotation(w.axis, 90).multiply(pl.Rotation)
        f = Draft.make_ellipse(a, b, placement=pl, face=fill)
    elif tool == 'Polygon':                       # gui_polygons: centre + vertex point, NumberOfSides from the tool option
        c = P[0]; r = P[1].sub(c).Length
        ang = -DraftVecUtils.angle(P[1].sub(c), w.u, w.axis)
        pl = _pl(w, c); pl.Rotation = App.Rotation(w.axis, math.degrees(ang)).multiply(pl.Rotation)
        f = Draft.make_polygon(int(o.get('sides', 6)), radius=r, inscribed=True, placement=pl, face=fill)
    elif tool == 'BSpline':
        closed = len(P) > 2 and P[0].isEqual(P[-1], 1e-7)
        if closed: P = P[:-1]
        f = Draft.make_bspline(P, closed=closed, face=fill if closed else False)
    elif tool in ('BezCurve', 'CubicBezCurve'):
        f = Draft.make_bezcurve(P, degree=3 if tool == 'CubicBezCurve' else None, closed=False)
    elif tool == 'Point':
        f = Draft.make_point(P[0])
    elif tool == 'Text':
        f = Draft.make_text(o.get('text', 'Text').split('\n'), placement=_pl(w, P[0]))
    elif tool == 'Dimension':                     # gui_dimensions: two measured points + the dimension line point
        if len(P) == 2: P.append(P[1])
        f = Draft.make_linear_dimension(P[0], P[1], P[2])
    elif tool == 'Label':                         # gui_labels: target point, then label position
        target = None
        if o.get('target'):
            obj, sub = o['target']; target = (_doc().getObject(obj), [sub] if sub else [])
        f = Draft.make_label(target_point=P[0], placement=_pl(w, P[1]), target_object=target[0] if target else None,
                             subelements=target[1] if target else None, label_type=o.get('label_type', 'Custom'),
                             custom_text=o.get('text', 'Label'))
    elif tool == 'ShapeString':
        f = Draft.make_shapestring(o.get('text', 'Text'), o.get('font', '/system/fonts/Roboto-Regular.ttf'), float(o.get('size', 10)))
        f.Placement = _pl(w, P[0])
    else:
        raise RuntimeError('Unknown Draft tool ' + tool)
    _doc().recompute()
    return f.Name

# ------------------------------------------------------------------ modification tools (selection + points)
@op
def draft_modify(tool, names, pts=None, wp=None, opts=None):
    import Draft, DraftVecUtils, DraftGeomUtils
    o = opts or {}; P = _pts(pts or []); w = _wp(wp); objs = _objs(names); copy = bool(o.get('copy'))
    if not objs: raise RuntimeError('Select objects first')
    if tool == 'Move':
        r = Draft.move(objs, P[1].sub(P[0]), copy=copy)
    elif tool == 'Rotate':                        # gui_rotate: centre, start direction, end direction (or typed angle)
        c = P[0]
        if 'angle' in o: ang = float(o['angle'])
        else: ang = math.degrees(DraftVecUtils.angle(P[2].sub(c), P[1].sub(c), w.axis)) * -1
        r = Draft.rotate(objs, ang, center=c, axis=w.axis, copy=copy)
    elif tool == 'Scale':
        k = float(o.get('factor', 1.0))
        r = Draft.scale(objs, V(k, k, k) if 'xyz' not in o else V(*o['xyz']), center=P[0], copy=copy)
    elif tool == 'Mirror':                        # gui_mirror: mirror line through two points, in the working plane
        p2 = P[1] if P[1].sub(P[0]).cross(w.axis).Length > 1e-9 else P[0].add(w.v)
        r = Draft.mirror(objs, P[0], p2)
    elif tool == 'Offset':                        # gui_offset: the picked point gives side and distance
        obj = objs[0]; sh = obj.Shape; pt = P[0]
        t = getattr(obj, 'Proxy', None).__class__.__name__ if hasattr(obj, 'Proxy') else ''
        if len(sh.Edges) == 1 and hasattr(sh.Edges[0].Curve, 'Radius') and 'BSpline' not in obj.TypeId + t:
            c = sh.Edges[0].Curve.Center; rad = pt.sub(c).Length
            if 'distance' in o:
                r1 = sh.Edges[0].Curve.Radius; rad = r1 + float(o['distance']) if rad >= r1 else r1 - float(o['distance'])
            r = Draft.offset(obj, rad, copy=copy)
        elif hasattr(obj, 'Points') and 'BSpline' in (obj.Proxy.__class__.__name__ if hasattr(obj, 'Proxy') else ''):
            dist = DraftGeomUtils.findPerpendicular(pt, sh.Edges)
            d = dist[0].negative(); e = sh.Edges[0]; basetan = DraftGeomUtils.getTangent(e, pt); npts = []
            for p in obj.Points:
                a = -DraftVecUtils.angle(DraftGeomUtils.getTangent(e, p), basetan, w.axis)
                dv = DraftVecUtils.rotate(d, a, w.axis)
                if 'distance' in o: dv = dv.normalize().multiply(float(o['distance']))
                npts.append(p.add(dv))
            r = Draft.offset(obj, npts, copy=copy)
        else:
            dist = DraftGeomUtils.findPerpendicular(pt, sh.Edges)
            if not dist: raise RuntimeError('Pick a point beside the wire')
            d = dist[0].negative()
            v1 = DraftGeomUtils.getTangent(sh.Edges[0], pt); v2 = DraftGeomUtils.getTangent(sh.Edges[dist[1]], pt)
            dv = DraftVecUtils.rotate(d, -DraftVecUtils.angle(v1, v2, w.axis), w.axis)
            if 'distance' in o: dv.normalize(); dv.multiply(float(o['distance']))
            r = Draft.offset(obj, dv, copy=copy, occ=bool(o.get('occ')))
    elif tool == 'Stretch':                       # gui_stretch: points of Draft wires/rectangles inside the box move by the vector
        lo, hi = P[0], P[1]; vec = P[3].sub(P[2])
        def inside(p):
            l = w.get_local_coords(p); a = w.get_local_coords(lo); b = w.get_local_coords(hi)
            return min(a.x, b.x) <= l.x <= max(a.x, b.x) and min(a.y, b.y) <= l.y <= max(a.y, b.y)
        for obj in objs:
            if hasattr(obj, 'Points') and obj.Points:
                g = obj.getGlobalPlacement(); inv = g.inverse()
                obj.Points = [inv.multVec(g.multVec(p).add(vec)) if inside(g.multVec(p)) else p for p in obj.Points]
            elif 'Length' in obj.PropertiesList and 'Height' in obj.PropertiesList and obj.Proxy.__class__.__name__ == 'Rectangle':
                pts4 = [obj.Placement.multVec(v.Point) for v in obj.Shape.Vertexes]
                if all(inside(p) for p in pts4): obj.Placement.Base = obj.Placement.Base.add(vec)
            elif all(inside(v.Point) for v in obj.Shape.Vertexes):
                Draft.move([obj], vec)
        r = objs
    elif tool == 'Trimex':                        # gui_trimex: faces extrude, wires trim/extend their end segment to the point
        sub = o.get('sub', '')
        obj = objs[0]
        if sub.startswith('Face'):
            face = obj.Shape.getElement(sub)
            n = face.normalAt(0, 0); d = P[0].sub(face.CenterOfMass).dot(n)
            r = Draft.extrude(obj, n.multiply(d)) if hasattr(Draft, 'extrude') else None
        elif hasattr(obj, 'Points') and len(obj.Points) >= 2:
            g = obj.getGlobalPlacement(); pts_ = [g.multVec(p) for p in obj.Points]
            end = 0 if pts_[0].sub(P[0]).Length < pts_[-1].sub(P[0]).Length else -1
            a, b = (pts_[1], pts_[0]) if end == 0 else (pts_[-2], pts_[-1])
            dvec = b.sub(a); dvec.normalize(); newp = a.add(dvec.multiply(P[0].sub(a).dot(dvec)))
            pts_[end] = newp; obj.Points = [g.inverse().multVec(p) for p in pts_]; r = obj
        else:
            raise RuntimeError('Trimex works on Draft wires and on faces')
    elif tool == 'Split':                         # gui_split: the edge picked on a wire and the point on it
        sub = o.get('sub', 'Edge1'); r = Draft.split(objs[0], P[0], int(sub[4:]))
    else:
        raise RuntimeError('Unknown Draft modifier ' + tool)
    _doc().recompute()
    if isinstance(r, (list, tuple)): r = r[0] if r else None
    return getattr(r, 'Name', None)

# ------------------------------------------------------------------ selection tools
@op
def draft_sel(tool, sel=None, opts=None):
    import Draft
    o = opts or {}; d = _doc()
    s = _sel(sel); objs = []
    for x, _ in s:
        if x not in objs: objs.append(x)
    name = None
    if tool == 'Upgrade':
        r = Draft.upgrade(objs, delete=True); name = r[0][0].Name if r and r[0] else None
    elif tool == 'Downgrade':
        r = Draft.downgrade(objs, delete=True); name = r[0][0].Name if r and r[0] else None
    elif tool == 'Join':
        r = Draft.join_wires(objs); name = objs[0].Name
    elif tool == 'Draft2Sketch':                  # gui_draft2sketch: Draft -> Sketch, Sketch -> Draft
        sk = [x for x in objs if x.TypeId == 'Sketcher::SketchObject']; dr = [x for x in objs if x not in sk]
        if dr: name = Draft.make_sketch(dr, autoconstraints=True).Name
        if sk: r = Draft.draftify(sk, delete=False); name = name or (r[0].Name if isinstance(r, list) and r else getattr(r, 'Name', None))
    elif tool == 'Clone':
        r = Draft.make_clone(objs); name = (r[0] if isinstance(r, list) else r).Name
    elif tool == 'OrthoArray':                    # TaskPanelOrthoArray defaults: 2x2x1, interval 100 mm (Draft "array" params)
        n = o.get('n', [2, 2, 1]); iv = o.get('interval', [100, 100, 100])
        r = Draft.make_ortho_array(objs[0], V(iv[0], 0, 0), V(0, iv[1], 0), V(0, 0, iv[2]), n[0], n[1], n[2], use_link=o.get('link', True))
        name = r.Name
    elif tool == 'PolarArray':
        r = Draft.make_polar_array(objs[0], int(o.get('n', 5)), float(o.get('angle', 360)), center=V(*o.get('center', [0, 0, 0])),
                                   use_link=o.get('link', True)); name = r.Name
    elif tool == 'CircularArray':
        r = Draft.make_circular_array(objs[0], r_distance=float(o.get('r', 100)), tan_distance=float(o.get('t', 50)),
                                      number=int(o.get('rings', 3)), symmetry=1, center=V(*o.get('center', [0, 0, 0])),
                                      use_link=o.get('link', True)); name = r.Name
    elif tool in ('PathArray', 'PathLinkArray'):
        path = (s[1][0], s[1][1]) if len(s) > 1 else None
        r = Draft.make_path_array(objs[0], path[0], count=int(o.get('n', 4)), subelements=path[1] or None,
                                  use_link=tool == 'PathLinkArray'); name = r.Name
    elif tool in ('PathTwistedArray', 'PathTwistedLinkArray'):
        r = Draft.make_path_twisted_array(objs[0], objs[1], count=int(o.get('n', 15)), use_link=tool == 'PathTwistedLinkArray'); name = r.Name
    elif tool in ('PointArray', 'PointLinkArray'):
        r = Draft.make_point_array(objs[0], objs[1], use_link=tool == 'PointLinkArray'); name = r.Name
    elif tool == 'Shape2DView':
        for x, subs in s:
            faces = [f for f in subs if f.startswith('Face')]
            r = Draft.make_shape2dview(x, V(*o.get('dir', [0, 0, 1])), facenumbers=[int(f[4:]) - 1 for f in faces] if faces else [])
            name = r.Name
    elif tool == 'Facebinder':
        r = Draft.make_facebinder([(x, tuple(subs)) for x, subs in s]); name = r.Name
    elif tool == 'WireToBSpline':                 # gui_wire2spline: Wire <-> BSpline keeping the points
        for x in objs:
            pts = [x.Placement.multVec(p) for p in x.Points]
            n = Draft.make_bspline(pts, closed=x.Closed) if x.Proxy.__class__.__name__ == 'Wire' else Draft.make_wire(pts, closed=x.Closed)
            d.removeObject(x.Name); name = n.Name
    elif tool == 'Heal':
        Draft.heal(objs or None)
    elif tool == 'Fillet':
        r = Draft.make_fillet(objs[:2], radius=float(o.get('radius', 1)), chamfer=bool(o.get('chamfer')), delete=bool(o.get('delete')))
        name = getattr(r, 'Name', None)
    elif tool == 'Hatch':
        r = Draft.make_hatch(objs[0], o.get('file') or App.getResourceDir() + 'Mod/TechDraw/PAT/FCPAT.pat', o.get('pattern', 'Diagonal4'),
                             float(o.get('scale', 10)), float(o.get('rotation', 0))); name = r.Name
    elif tool == 'Layer':
        r = Draft.make_layer(o.get('name', 'Layer')); name = r.Name
        if objs: r.Group = objs
    elif tool == 'AddToLayer':
        lay = d.getObject(o['layer'])
        for x in objs:
            for l in d.Objects:
                if getattr(l.Proxy, 'Type', '') == 'Layer' and x in l.Group: l.Group = [y for y in l.Group if y is not x]
            if lay is not None: lay.Group = lay.Group + [x]
    elif tool == 'AddToGroup':
        g = d.getObject(o['group']) if o.get('group') else None
        for x in objs:
            for p in x.InList:
                if p.isDerivedFrom('App::DocumentObjectGroup'): p.removeObject(x)
            if g is not None: g.addObject(x)
    elif tool == 'AddConstruction':               # gui_groups.AddToConstruction: the "Draft_Construction" group
        g = d.getObject('Draft_Construction') or d.addObject('App::DocumentObjectGroup', 'Draft_Construction')
        g.Label = 'Construction'
        for x in objs: g.addObject(x)
        name = g.Name
    elif tool == 'Slope':                         # gui_lineslope: z of wire points from a slope percentage
        pct = float(o.get('slope', 0))
        for x in objs:
            if not hasattr(x, 'Points'): continue
            pts = list(x.Points); last = pts[0]; nz = []; z = pts[0].z
            for i, p in enumerate(pts):
                if i: z += pct / 100.0 * V(p.x - last.x, p.y - last.y, 0).Length
                nz.append(V(p.x, p.y, z)); last = p
            x.Points = nz
    elif tool == 'FlipDimension':
        for x in objs:
            if 'Normal' in x.PropertiesList: x.Normal = x.Normal.negative()
    elif tool == 'UpdateShape2DView':
        for x in d.Objects:
            if getattr(getattr(x, 'Proxy', None), 'Type', '') == 'Shape2DView': x.touch()
    elif tool == 'WorkingPlaneProxy':
        r = Draft.make_working_plane_proxy(App.Placement(V(*o['pos']), App.Rotation(V(*o['u']), V(*o['v']), V(*o['w']), 'ZXY'))); name = r.Name
    else:
        raise RuntimeError('Unknown Draft tool ' + tool)
    d.recompute()
    return name

@op
def draft_groups():
    """Groups and layers (Draft_AddToGroup / AddToLayer / SelectGroup)."""
    d = _doc()
    return {'groups': [[x.Name, x.Label] for x in d.Objects if x.isDerivedFrom('App::DocumentObjectGroup') and getattr(getattr(x, 'Proxy', None), 'Type', '') not in ('Layer', 'LayerContainer')],
            'layers': [[x.Name, x.Label] for x in d.Objects if getattr(getattr(x, 'Proxy', None), 'Type', '') == 'Layer']}

@op
def group_members(name):
    g = _doc().getObject(name); return [x.Name for x in getattr(g, 'Group', [])]

@op
def face_plane(obj, sub):
    """Draft_SelectPlane on a face: WorkingPlane.align_to_face."""
    import WorkingPlane
    p = WorkingPlane.PlaneBase()
    if not p.align_to_face(_doc().getObject(obj).Shape.getElement(sub)): raise RuntimeError('Not a planar face')
    return {'pos': list(p.position), 'u': list(p.u), 'v': list(p.v), 'w': list(p.axis)}

# ------------------------------------------------------------------ snapping + annotation display
@op
def snap_points(names):
    """Snapper targets per visible shape: endpoints (vertexes), edge midpoints, circle centres — Draft_Snap_Endpoint /
    Midpoint / Center.  Returns [x, y, z, kind] with kind 0 endpoint, 1 midpoint, 2 centre."""
    out = []
    for o in _objs(names):
        sh = getattr(o, 'Shape', None)
        if sh is None or sh.isNull(): continue
        for v in sh.Vertexes: out.append([v.X, v.Y, v.Z, 0])
        for e in sh.Edges[:2000]:
            try:
                m = e.valueAt(e.FirstParameter + (e.LastParameter - e.FirstParameter) / 2); out.append([m.x, m.y, m.z, 1])
                if hasattr(e.Curve, 'Center'): c = e.Curve.Center; out.append([c.x, c.y, c.z, 2])
            except Exception:
                pass
    return out

@op
def annotations():
    """Draft texts, dimensions and labels are view-provider drawings on desktop; this returns what to draw:
    {name: {'kind', 'lines': [[p1, p2], ...], 'texts': [[pos, text], ...]}}."""
    import DraftVecUtils
    out = {}
    for o in _doc().Objects:
        t = getattr(getattr(o, 'Proxy', None), 'Type', '') or o.TypeId
        if not getattr(o, 'Visibility', True): continue
        try:
            if t == 'Text':
                out[o.Name] = {'kind': 'text', 'lines': [], 'texts': [[list(o.Placement.Base), '\n'.join(o.Text)]]}
            elif t in ('LinearDimension', 'Dimension', 'AngularDimension', 'RadialDimension') or 'Dimension' in t:
                if not (hasattr(o, 'Start') and hasattr(o, 'End')): continue
                p1, p2, dl = o.Start, o.End, o.Dimline
                base = p2.sub(p1); n = base.cross(V(0, 0, 1)) if base.cross(V(0, 0, 1)).Length > 1e-9 else V(0, 1, 0)
                n.normalize(); off = dl.sub(p1).dot(n)
                q1 = p1.add(V(n).multiply(off)); q2 = p2.add(V(n).multiply(off))
                val = getattr(o, 'Distance', None)
                txt = val.UserString if hasattr(val, 'UserString') else '%.2f mm' % base.Length
                out[o.Name] = {'kind': 'dim', 'lines': [[list(p1), list(q1)], [list(p2), list(q2)], [list(q1), list(q2)]],
                               'texts': [[list(q1.add(q2).multiply(0.5)), txt]]}
            elif o.TypeId == 'App::AnnotationLabel':
                out[o.Name] = {'kind': 'label', 'lines': [[list(o.BasePosition), list(o.TextPosition)]],
                               'texts': [[list(o.TextPosition), '\n'.join(o.LabelText)]]}
            elif t == 'Label':
                pts = [list(p) for p in o.Points] if getattr(o, 'Points', None) else [list(o.TargetPoint), list(o.Placement.Base)]
                out[o.Name] = {'kind': 'label', 'lines': [[pts[i], pts[i + 1]] for i in range(len(pts) - 1)],
                               'texts': [[list(o.Placement.Base), '\n'.join(o.Text)]]}
        except Exception:
            pass
    # text height in model units, as the desktop draws it (view property FontSize, else Draft's "textheight" preference)
    from .bridge import _view_data
    fonts = _view_data.get(_doc().Name, {}).get('font', {})
    dflt = App.ParamGet('User parameter:BaseApp/Preferences/Mod/Draft').GetFloat('textheight', 3.5)
    for n, a in out.items(): a['size'] = fonts.get(n, dflt)
    return out
