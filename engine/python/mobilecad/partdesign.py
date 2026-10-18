"""PartDesign / Part commands for the mobile UI.  Each function does what the desktop Gui command does
(src/Mod/PartDesign/Gui/Command*.cpp, src/Mod/Part/Gui/Command*.cpp): same object types, same defaults,
same use of the selection.  Selection arrives as [[objName, [sub, ...]], ...]."""
import FreeCAD as App
from .bridge import op, _doc

def _sel(sel):
    d = _doc()
    return [(d.getObject(n), list(subs)) for n, subs in (sel or []) if d.getObject(n) is not None]

def same_element(src, sub, target):
    """The element of [target]'s shape with the same geometry as src.sub, or None.  A later fillet / chamfer outside the
    Body trims edges and faces, so the target element only has to contain the picked one (closest size wins)."""
    import Part
    picked = src.Shape.getElement(sub); sh = target.Shape
    tol = max(1e-6, sh.BoundBox.DiagonalLength * 1e-6)
    def on(shape, p): return shape.distToShape(Part.Vertex(p))[0] < tol * 10
    if sub.startswith('Vertex'):
        for i, v in enumerate(sh.Vertexes, 1):
            if (v.Point - picked.Point).Length < tol * 10: return 'Vertex%d' % i
    elif sub.startswith('Edge'):
        ps = [picked.valueAt(picked.FirstParameter), picked.valueAt(picked.LastParameter), picked.valueAt(0.5 * (picked.FirstParameter + picked.LastParameter))]
        hits = [(abs(e.Length - picked.Length), i) for i, e in enumerate(sh.Edges, 1) if e.Length >= picked.Length - tol * 10 and all(on(e, p) for p in ps)]
        if hits: return 'Edge%d' % min(hits)[1]
    elif sub.startswith('Face'):
        # sample points of the picked face (its vertices pulled toward the centre, and the centre) must lie on the target
        c = picked.CenterOfMass
        ps = [c] + [c + (v.Point - c) * 0.9 for v in picked.Vertexes[:8]]
        hits = [(abs(f.Area - picked.Area), i) for i, f in enumerate(sh.Faces, 1) if f.Area >= picked.Area - tol * 100 and all(on(f, p) for p in ps)]
        if hits: return 'Face%d' % min(hits)[1]
    return None

def _depends_on(o, body):
    try: return body in o.OutListRecursive
    except Exception: return False

def _body_tip_before(body, feature=None):
    """The Body's current solid: its Tip (or the last solid feature before [feature])."""
    tip = body.Tip
    return tip if tip is not None and not tip.Shape.isNull() else None

def _into_body(body, o, subs):
    """PartDesign commands only work on references inside the active Body (desktop: 'Selection is not in Active Body' +
    'make cross-reference / independent copy' dialog).  Maps a selection from outside:
      - an object built from this Body (e.g. a Part Fillet of it): the same element of the Body's solid; else an error,
        since referencing it would be a dependency cycle (that froze the app);
      - any other object: linked into the Body with a SubShapeBinder (PartDesign_SubShapeBinder)."""
    if o is None or o == body or body.hasObject(o) or o in body.Origin.OutList or o == body.Origin: return o, subs
    if _depends_on(o, body):
        tip = _body_tip_before(body)
        if tip is None: raise RuntimeError('%s is built from %s, which has no solid yet' % (o.Label, body.Label))
        mapped = [same_element(o, s, tip) if s else '' for s in subs]
        bad = [s for s, m in zip(subs, mapped) if m is None]
        if bad:
            raise RuntimeError('%s only exists on %s, which is built from %s - a feature inside %s cannot use it (it would be '
                               'circular). Pick geometry that also exists on %s, or use PartDesign features inside the Body '
                               'instead of Part ones.' % (', '.join(bad), o.Label, body.Label, body.Label, tip.Label))
        return tip, mapped
    b = body.newObject('PartDesign::SubShapeBinder', 'Binder')
    b.Support = [(o, tuple(subs))] if subs and subs != [''] else [(o, ())]
    b.Visibility = False; b.recompute()
    if not subs or subs == ['']: return b, subs
    kinds = [s.rstrip('0123456789') for s in subs]
    count = {}
    out = []
    for k in kinds: count[k] = count.get(k, 0) + 1; out.append('%s%d' % (k, count[k]))
    return b, out

def _sel_in(sel, body):
    return [_into_body(body, o, subs) for o, subs in _sel(sel)]

def _body_of(o):
    for p in o.InList if o is not None else []:
        if p.TypeId == 'PartDesign::Body': return p
    return None

def _active_body(sel=None, create=True):
    d = _doc()
    for o, _ in _sel(sel):
        if o.TypeId == 'PartDesign::Body': return o
        b = _body_of(o)
        if b: return b
    bodies = [o for o in d.Objects if o.TypeId == 'PartDesign::Body']
    if bodies: return bodies[-1]
    return d.addObject('PartDesign::Body', 'Body') if create else None

def _origin(body, role):
    for o in body.Origin.OutList:
        if getattr(o, 'Role', None) == role: return o
    raise RuntimeError('origin feature %s not found' % role)

@op
def pd_body():
    """PartDesign_Body."""
    return _doc().addObject('PartDesign::Body', 'Body').Name

@op
def pd_new_sketch(sel=None, plane=None):
    """PartDesign_NewSketch / Sketcher_NewSketch: attach to a selected planar face / datum plane / origin plane (FlatFace)."""
    body = _active_body(sel); s = _sel_in(sel, body)
    sk = body.newObject('Sketcher::SketchObject', 'Sketch')
    support = None
    for o, subs in s:
        if o.TypeId == 'PartDesign::Body': continue
        if subs and subs[0].startswith('Face'): support = (o, [subs[0]]); break
        if getattr(o, 'Role', None) in ('XY_Plane', 'XZ_Plane', 'YZ_Plane') or o.TypeId in ('PartDesign::Plane', 'App::Plane'):
            support = (o, ['']); break
    if support is None:
        support = (_origin(body, plane or 'XY_Plane'), [''])
    sk.AttachmentSupport = support
    sk.MapMode = 'FlatFace'
    _doc().recompute()
    return sk.Name

def _profile(sel, body=None):
    for o, subs in (_sel_in(sel, body) if body is not None else _sel(sel)):
        if o.TypeId == 'Sketcher::SketchObject': return o
        if subs and subs[0].startswith('Face'): return (o, subs)
    for o in reversed(_doc().Objects):          # desktop falls back to asking; we use the last visible sketch
        if o.TypeId == 'Sketcher::SketchObject': return o
    raise RuntimeError('Select a sketch or a planar face first')

@op
def pd_profile_feature(kind, sel=None):
    """PartDesign_Pad/Pocket/Revolution/Groove/Hole/AdditiveHelix/SubtractiveHelix/AdditiveLoft/AdditivePipe/... (prepareProfileBased)."""
    body0 = _active_body(sel)
    prof = _profile(sel, body0)
    if kind == 'Hole' and isinstance(prof, tuple) and prof[1] and prof[1][0].startswith('Face'):
        # a hole needs circles: a flat face without circular edges gets a sketch with one hole circle at its centre
        # (desktop: sketch the hole centres first); the sketch can be opened to move / add holes
        import Part
        face = prof[0].Shape.getElement(prof[1][0])
        if not any(isinstance(e.Curve, Part.Circle) for e in face.Edges):
            hs = body0.newObject('Sketcher::SketchObject', 'Sketch'); hs.AttachmentSupport = [(prof[0], prof[1][0])]; hs.MapMode = 'FlatFace'
            hs.recompute()
            c = hs.getGlobalPlacement().inverse().multVec(face.CenterOfMass)
            hs.addGeometry(Part.Circle(App.Vector(c.x, c.y, 0), App.Vector(0, 0, 1), 3.0), False)
            prof = hs
    sk = prof if not isinstance(prof, tuple) else prof[0]
    body = _body_of(sk) or body0
    f = body.newObject('PartDesign::' + kind, kind)
    f.Profile = prof
    if kind == 'Pad': f.Length = 10.0
    elif kind == 'Pocket': f.Length = 5.0
    elif kind in ('Revolution', 'Groove'):
        if not isinstance(prof, tuple): f.ReferenceAxis = (sk, ['V_Axis'])
        else: f.ReferenceAxis = (_origin(body, 'Y_Axis'), [''])
        f.Angle = 360.0
    elif kind in ('AdditiveHelix', 'SubtractiveHelix'):
        f.ReferenceAxis = (sk, ['V_Axis']) if not isinstance(prof, tuple) else (_origin(body, 'Z_Axis'), [''])
    if not isinstance(prof, tuple):
        sk.Visibility = False                     # finishProfileBased(): hide the sketch
    _doc().recompute()
    return f.Name

@op
def pd_dressup(kind, sel=None):
    """PartDesign_Fillet/Chamfer/Draft/Thickness (makeChamferOrFillet / dress-up commands): Base = (feature, [edges|faces])."""
    s = [(o, subs) for o, subs in _sel_in(sel, _active_body(sel)) if o.TypeId != 'PartDesign::Body']
    if not s: raise RuntimeError('Select edges or faces of a solid first')
    base = s[0][0]
    subs = [x for o, ss in s if o == base for x in ss]
    body = _body_of(base) or _active_body(sel)
    f = body.newObject('PartDesign::' + kind, kind)
    f.Base = (base, subs)
    if not subs and kind in ('Fillet', 'Chamfer'): f.UseAllEdges = True
    base.Visibility = False
    _doc().recompute()
    return f.Name

@op
def pd_transform(kind, sel=None):
    """PartDesign_Mirrored/LinearPattern/PolarPattern (prepareTransformed): Originals = selected features."""
    d = _doc()
    feats = [o for o, _ in _sel(sel) if o.TypeId.startswith('PartDesign::') and o.TypeId != 'PartDesign::Body']
    body = _body_of(feats[0]) if feats else _active_body(sel)
    f = body.newObject('PartDesign::' + kind, kind)
    if feats: f.Originals = feats
    else: f.TransformMode = 'Whole shape'
    sk = next((x.Profile[0] for x in feats if hasattr(x, 'Profile') and x.Profile), None)
    if kind == 'Mirrored':
        f.MirrorPlane = (sk, ['V_Axis']) if sk else (_origin(body, 'YZ_Plane'), [''])
    elif kind == 'LinearPattern':
        f.Direction = (sk, ['H_Axis']) if sk else (_origin(body, 'X_Axis'), [''])
        f.Length = 100; f.Occurrences = 2
    elif kind == 'PolarPattern':
        f.Axis = (sk, ['N_Axis']) if sk else (_origin(body, 'Z_Axis'), [''])
        f.Angle = 360; f.Occurrences = 2
    body.Tip = f
    d.recompute()
    return f.Name

@op
def pd_primitive(kind, sel=None):
    """PartDesign_Additive*/Subtractive* primitives (CmdPrimitive): attached to a selected face/plane, else body origin."""
    body = _active_body(sel)
    f = body.newObject('PartDesign::' + kind, kind.replace('Additive', '').replace('Subtractive', ''))
    for o, subs in _sel_in(sel, body):
        if subs and subs[0].startswith('Face'):
            f.AttachmentSupport = (o, [subs[0]]); f.MapMode = 'FlatFace'; break
    _doc().recompute()
    return f.Name

@op
def pd_datum(kind, sel=None):
    """PartDesign_Plane/Line/Point: datum attached to the selection (attacher picks the mode like the desktop dialog)."""
    import Part
    body = _active_body(sel)
    f = body.newObject('PartDesign::' + kind, 'Datum' + kind)
    refs = [(o, subs[0] if subs else '') for o, subs in _sel_in(sel, body) if o.TypeId != 'PartDesign::Body']
    if refs:
        f.AttachmentSupport = refs
        # same as the desktop attachment dialog: let the attacher suggest the best-fitting mode
        ae = Part.AttachEngine(f.AttacherType)
        ae.readParametersFromFeature(f)
        r = ae.suggestModes()
        if r.get('message') == 'OK':
            f.MapMode = r['bestFitMode']
    _doc().recompute()
    return f.Name

@op
def pd_set_tip(name):
    """PartDesign_MoveTip — the rollback bar."""
    o = _doc().getObject(name); b = _body_of(o)
    b.Tip = o; _doc().recompute(); return True

# ------------------------------------------------------------------ Part workbench
@op
def part_primitive(kind):
    """Part_Box/Cylinder/Sphere/Cone/Torus/Tube/Prism/Wedge."""
    f = _doc().addObject('Part::' + kind, kind); _doc().recompute(); return f.Name

@op
def part_boolean(kind, sel=None):
    """Part_Fuse (MultiFuse) / Part_Common (MultiCommon) / Part_Cut (Base, Tool)."""
    objs = []
    for o, _ in _sel(sel):
        if o not in objs: objs.append(o)
    if len(objs) < 2: raise RuntimeError('Select at least two shapes')
    d = _doc()
    if kind == 'Cut':
        f = d.addObject('Part::Cut', 'Cut'); f.Base = objs[0]; f.Tool = objs[1]
    else:
        f = d.addObject('Part::Multi' + kind, kind); f.Shapes = objs
    for o in objs: o.Visibility = False
    d.recompute(); return f.Name

@op
def delete_objects(names):
    """Std_Delete."""
    d = _doc()
    for n in names:
        if d.getObject(n) is not None: d.removeObject(n)
    d.recompute(); return True

@op
def set_visibility(names, visible):
    """Std_ToggleVisibility / Std_ShowSelection / Std_HideSelection."""
    for n in names: _doc().getObject(n).Visibility = bool(visible)
    return True

# ------------------------------------------------------------------ manipulators (drag arrows)
@op
def manipulator(name):
    """Linear manipulator for Pad/Pocket Length: base = profile centre, direction = extrusion direction (as FeatureExtrude
    computes it from the sketch normal, Reversed and Midplane). Returns None for unsupported features."""
    o = _doc().getObject(name)
    if o is not None and o.TypeId in ('PartDesign::Fillet', 'PartDesign::Chamfer'): return _dressup_manipulator(o)
    if o is None or o.TypeId not in ('PartDesign::Pad', 'PartDesign::Pocket') or not o.Profile: return None
    if getattr(o, 'Type', 'Length') not in ('Length', 'Dimension'): return None
    prof = o.Profile[0]
    try:
        shape = prof.Shape
        bb = shape.BoundBox
        base = App.Vector(bb.Center)
        n = prof.getGlobalPlacement().Rotation.multVec(App.Vector(0, 0, 1))
    except Exception:
        return None
    sign = -1 if o.TypeId == 'PartDesign::Pocket' else 1
    if o.Reversed: sign = -sign
    d = n * sign
    length = o.Length.Value
    start = base - d * (length / 2) if o.Midplane else base
    return {'base': [start.x, start.y, start.z], 'dir': [d.x, d.y, d.z], 'value': length, 'prop': 'Length',
            'scale': 1.0, 'midplane': bool(o.Midplane)}

def _dressup_manipulator(o):
    """Fillet radius / chamfer size drag handle: from the middle of the first rounded edge (on the base shape, where the
    sharp edge was) into the material along the bisector of its two faces; its length is the radius / size."""
    import Part
    try:
        base, subs = o.Base[0], [x for x in o.Base[1] if x.startswith('Edge')]
        sh = base.Shape
        e = sh.getElement(subs[0]) if subs else sh.Edges[0]
        mid = e.valueAt(0.5 * (e.FirstParameter + e.LastParameter))
        faces = sh.ancestorsOfType(e, Part.Face)
        if len(faces) < 2: return None
        n = App.Vector()
        for f in faces[:2]:
            u, v = f.Surface.parameter(mid); nn = f.normalAt(u, v)
            n += nn
        if n.Length < 1e-9: return None
        d = -n.normalize()                      # into the material
    except Exception:
        return None
    prop = 'Radius' if o.TypeId == 'PartDesign::Fillet' else 'Size'
    return {'base': [mid.x, mid.y, mid.z], 'dir': [d.x, d.y, d.z], 'value': getattr(o, prop).Value, 'prop': prop, 'scale': 1.0, 'midplane': False}

# ------------------------------------------------------------------ generic command implementation
_LINK_TYPES = ('App::PropertyLink', 'App::PropertyLinkGlobal', 'App::PropertyLinkSub', 'App::PropertyLinkSubGlobal',
               'App::PropertyLinkList', 'App::PropertyLinkListGlobal', 'App::PropertyLinkSubList', 'App::PropertyLinkSubListGlobal',
               'App::PropertyXLink', 'App::PropertyXLinkSub', 'App::PropertyXLinkSubList')

@op
def create_object(type, label=None, sel=None):
    """Generic implementation of a FreeCAD 'create <Type>' command: the same document object the desktop command creates
    (inside the active Body for PartDesign types), with the selection assigned to its link properties in declaration order —
    what the desktop task panel's selection step does. Parameters are then edited in the property dialog."""
    d = _doc(); s = _sel(sel)
    if type.startswith('PartDesign::'):
        f = _active_body(sel).newObject(type, label or type.split('::')[1])
    else:
        f = d.addObject(type, label or type.split('::')[1])
    if s:
        # consume the selection in order: single links take one item each (Base, then Tool ...), list links take the rest
        rest = list(s)
        for p in f.PropertiesList:
            if not rest: break
            t = f.getTypeIdOfProperty(p)
            if t not in _LINK_TYPES or 'ReadOnly' in f.getPropertyStatus(p) or p in ('ExpressionEngine', 'Label2'): continue
            try:
                if t.startswith('App::PropertyLinkList'):
                    setattr(f, p, [o for o, _ in rest]); rest = []
                elif 'SubList' in t:
                    setattr(f, p, [(o, tuple(subs) or ('',)) for o, subs in rest]); rest = []
                elif 'Sub' in t:
                    o, subs = rest[0]; setattr(f, p, (o, subs or [''])); rest = rest[1:]
                else:
                    setattr(f, p, rest[0][0]); rest = rest[1:]
            except Exception:
                continue
    d.recompute()
    return f.Name

@op
def map_to_base(feature, sub):
    """A face / edge picked on a feature's result -> the same geometry on its base feature (desktop: while a dress-up
    feature is edited the base shape is shown and picked).  Returns [object, sub] or None."""
    d = _doc(); f = d.getObject(feature)
    b = getattr(f, 'Base', None)
    base = getattr(f, 'BaseFeature', None) or (b[0] if isinstance(b, tuple) and b else b if isinstance(b, App.DocumentObject) else None)
    if base is None or not sub: return None
    picked = f.Shape.getElement(sub); bs = base.Shape
    tol = max(1e-6, bs.BoundBox.DiagonalLength * 1e-6)
    def on(shape, p): return shape.distToShape(__import__('Part').Vertex(p))[0] < tol
    if sub.startswith('Edge'):
        ps = [picked.valueAt(picked.FirstParameter), picked.valueAt(picked.LastParameter),
              picked.valueAt(0.5 * (picked.FirstParameter + picked.LastParameter))]
        for i, e in enumerate(bs.Edges, 1):
            if all(on(e, p) for p in ps): return [base.Name, 'Edge%d' % i]
    elif sub.startswith('Face'):
        c = picked.CenterOfMass; n = picked.normalAt(*picked.Surface.parameter(c)[:2]) if hasattr(picked, 'Surface') else None
        best = None
        for i, fc in enumerate(bs.Faces, 1):
            if on(fc, c) and (best is None or abs(fc.Area - picked.Area) < best[0]): best = (abs(fc.Area - picked.Area), i)
        if best: return [base.Name, 'Face%d' % best[1]]
    elif sub.startswith('Vertex'):
        for i, v in enumerate(bs.Vertexes, 1):
            if (v.Point - picked.Point).Length < tol: return [base.Name, 'Vertex%d' % i]
    return None

@op
def pd_absorb_dressup(name):
    """'Move into Body': a Part Fillet / Chamfer made on a PartDesign Body (Part_Fillet keeps it outside the Body's history)
    becomes the Body's own PartDesign Fillet / Chamfer, inserted where it was created (after the last Body feature older
    than it, by object ID), same edges (mapped onto that feature's shape) and radii.  Binders / sketch attachments that
    pointed at it are redirected to the Body.  Returns the new feature names."""
    d = _doc(); f = d.getObject(name)
    kind = 'Fillet' if f.isDerivedFrom('Part::Fillet') else 'Chamfer' if f.isDerivedFrom('Part::Chamfer') else None
    base = getattr(f, 'Base', None)
    body = base if base is not None and base.isDerivedFrom('PartDesign::Body') else _body_of(base)
    if kind is None or body is None: raise RuntimeError('%s is not a Part fillet / chamfer of a Body' % f.Label)
    feats = [o for o in body.Group if o.isDerivedFrom('PartDesign::Feature') and not o.isDerivedFrom('Sketcher::SketchObject')
             and not o.isDerivedFrom('PartDesign::SubShapeBinder')]
    older = [o for o in feats if o.ID < f.ID]
    after = older[-1] if older else body.Tip
    src = base if base.isDerivedFrom('PartDesign::Feature') else body
    # group the edges by radius: one PartDesign feature per radius (its Radius / Size is a single value)
    groups = {}
    for e, r1, r2 in f.Edges:
        m = same_element(src, 'Edge%d' % e, after)
        if m is None: raise RuntimeError('Edge%d of %s no longer exists at %s' % (e, f.Label, after.Label))
        groups.setdefault(round(r1, 9), []).append(m)
    made = []; prev = after
    for r, edges in groups.items():
        n = d.addObject('PartDesign::' + kind, kind)
        body.insertObject(n, prev, True)
        n.Base = (prev, edges)
        if kind == 'Fillet': n.Radius = r
        else: n.Size = r
        made.append(n); prev = n
    d.recompute()
    # whatever used the Part fillet now uses the Body: sketches projecting a binder of it are re-pointed to the same
    # geometry of the solid just before them, then the binders go
    for bnd in [o for o in f.InList if o.isDerivedFrom('PartDesign::SubShapeBinder') and body.hasObject(o)]:
        for sk in [u for u in bnd.InList if u.isDerivedFrom('Sketcher::SketchObject')]:
            grp = list(body.Group); before = [o for o in grp[:grp.index(sk)] if o.isDerivedFrom('PartDesign::Feature')
                                              and not o.isDerivedFrom('PartDesign::SubShapeBinder') and not o.Shape.isNull()]
            if not before: continue
            tgt = before[-1]
            ext = [(o, list(subs)) for o, subs in sk.ExternalGeometry]
            k = 0
            for o, subs in ext:
                for sub in subs:
                    if o == bnd:
                        m = same_element(bnd, sub, tgt)
                        if m:
                            try: sk.delExternal(k); sk.addExternal(tgt.Name, m); continue
                            except Exception: pass
                    k += 1
            if sk.AttachmentSupport and any(o == bnd for o, _ in sk.AttachmentSupport):
                sk.AttachmentSupport = [(tgt, tuple(same_element(bnd, x, tgt) or x for x in subs)) if o == bnd else (o, subs) for o, subs in sk.AttachmentSupport]
        if not [u for u in bnd.InList if u != body]: d.removeObject(bnd.Name)
    vis = f.Visibility; label = f.Label
    d.removeObject(f.Name)
    if made: made[0].Label = label
    body.Visibility = vis or body.Visibility
    # only the Tip shows the Body's solid (PartDesign hides the features below it)
    for n in made:
        if n != body.Tip: n.Visibility = False
    d.recompute()
    return [n.Name for n in made]
