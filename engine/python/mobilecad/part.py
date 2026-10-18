"""Part workbench commands (src/Mod/Part/Gui/Command*.cpp, Dlg*.cpp, Task*.cpp, BOPTools, CompoundTools) and the
remaining PartDesign commands (Clone, CoordinateSystem, MoveFeature, gears ...).  Each op creates the same objects with the
same defaults as the desktop dialog's OK button; the parameters are then edited in the mobile property dialog."""
import FreeCAD as App
from .bridge import op, _doc
from .partdesign import _sel, _body_of, _active_body, _origin

def _objs(sel):
    out = []
    for o, _ in _sel(sel):
        if o not in out: out.append(o)
    return out

def _need(objs, n, what):
    if len(objs) < n: raise RuntimeError(what)
    return objs

def _closed_wires(o):
    """DlgExtrusion/DlgRevolution: 'Create solid' is checked when every wire leaf of the shape is closed."""
    try:
        sh = o.Shape
        ws = sh.Wires or []
        return bool(ws) and all(w.isClosed() for w in ws) and not sh.Solids
    except Exception:
        return False

# ------------------------------------------------------------------ dialog based shape tools
@op
def part_extrude(sel=None):
    """Part_Extrude (DlgExtrusion::apply): Part::Extrusion per shape, DirMode Normal for planar shapes, LengthFwd 10."""
    import Part
    out = []
    for o in _need(_objs(sel), 1, 'Select a shape to extrude'):
        f = _doc().addObject('Part::Extrusion', 'Extrude')
        f.Base = o
        try:
            n = o.Shape.findPlane()       # DlgExtrusion: Normal mode when Extrusion::calculateShapeNormal succeeds
        except Exception:
            n = None
        if n is not None:
            f.DirMode = 'Normal'
        else:
            f.DirMode = 'Custom'; f.Dir = App.Vector(0, 0, 1)
        f.DirLink = None
        f.LengthFwd = 10.0; f.LengthRev = 0.0; f.TaperAngle = 0.0; f.TaperAngleRev = 0.0
        f.Solid = _closed_wires(o); f.Reversed = False; f.Symmetric = False
        o.Visibility = False
        out.append(f.Name)
    _doc().recompute(); return out[-1]

@op
def part_revolve(sel=None):
    """Part_Revolve (DlgRevolution::accept): Part::Revolution, Axis Z through the origin, Angle 360; a selected edge
    becomes AxisLink like the dialog's 'Select reference'."""
    s = _sel(sel)
    shapes = [o for o, subs in s if not any(x.startswith('Edge') for x in subs)]
    axis = next(((o, [x for x in subs if x.startswith('Edge')][:1]) for o, subs in s if any(x.startswith('Edge') for x in subs)), None)
    if not shapes and axis: shapes = [axis[0]]; axis = None
    out = []
    for o in _need(shapes, 1, 'Select a shape to revolve'):
        f = _doc().addObject('Part::Revolution', 'Revolve')
        f.Source = o
        f.Axis = (0, 0, 1); f.Base = (0, 0, 0); f.Angle = 360.0
        f.Solid = _closed_wires(o)
        f.AxisLink = axis
        o.Visibility = False
        out.append(f.Name)
    _doc().recompute(); return out[-1]

@op
def part_mirror(sel=None, plane='XY'):
    """Part_Mirror (PartGui::Mirroring::accept): Part::Mirroring, Normal from the plane combo (XY/XZ/YZ) or a selected
    face/edge as MirrorPlane ('Use selected reference')."""
    s = _sel(sel)
    ref = next(((o, subs) for o, subs in s[1:] if subs), None)
    src = _need([o for o, _ in s[:1]], 1, 'Select a shape to mirror')[0]
    f = _doc().addObject('Part::Mirroring', 'Part__Mirroring')
    f.Source = src
    f.Label = '%s (Mirror #1)' % src.Label
    f.Normal = {'XY': (0, 0, 1), 'XZ': (0, 1, 0), 'YZ': (1, 0, 0)}[plane]
    f.Base = (0, 0, 0)
    if ref: f.MirrorPlane = (ref[0], ref[1][:1])
    src.Visibility = False
    _doc().recompute(); return f.Name

@op
def part_fillet(kind='Fillet', sel=None, radius=1.0):
    """Part_Fillet / Part_Chamfer (DlgFilletEdges::accept): Part::Fillet|Chamfer with Base and Edges=[(id, r1, r2)] for the
    checked edges; with nothing selected all edges are checked (the dialog's 'All' button)."""
    s = _sel(sel)
    base = _need([o for o, _ in s], 1, 'Select edges of a solid')[0]
    ids = sorted({int(x[4:]) for o, subs in s if o == base for x in subs if x.startswith('Edge')})
    if not ids: ids = list(range(1, len(base.Shape.Edges) + 1))
    f = _doc().addObject('Part::' + kind, kind)
    f.Base = base
    f.Edges = [(i, float(radius), float(radius)) for i in ids]
    base.Visibility = False
    _doc().recompute(); return f.Name

@op
def part_loft(sel=None):
    """Part_Loft (LoftWidget::accept): Part::Loft over the selected sections (>=2), Solid checked, Ruled/Closed off."""
    objs = _need(_objs(sel), 2, 'Select at least two profiles (vertex, edge, wire or face)')
    f = _doc().addObject('Part::Loft', 'Loft')
    f.Sections = objs; f.Solid = True; f.Ruled = False; f.Closed = False
    for o in objs: o.Visibility = False
    _doc().recompute(); return f.Name

@op
def part_sweep(sel=None):
    """Part_Sweep (SweepWidget::accept): Part::Sweep, Sections = profiles, Spine = the object whose edges were picked as
    the path (else the last selected object), Solid and Frenet checked."""
    s = _sel(sel)
    path = next(((o, [x for x in subs if x.startswith('Edge')]) for o, subs in s if any(x.startswith('Edge') for x in subs)), None)
    if path is None:
        _need(s, 2, 'Select the profile(s) and then the sweep path'); path = (s[-1][0], [])
    profiles = [o for o, _ in s if o is not path[0]]
    _need(profiles, 1, 'Select at least one profile')
    f = _doc().addObject('Part::Sweep', 'Sweep')
    f.Sections = profiles
    f.Spine = (path[0], path[1] or [''])
    f.Solid = True; f.Frenet = True
    for o in profiles: o.Visibility = False
    _doc().recompute(); return f.Name

@op
def part_scale(sel=None):
    """Part_Scale (DlgScale::apply): Part::Scale, Uniform, UniformScale 1."""
    out = []
    for o in _need(_objs(sel), 1, 'Select a shape to scale'):
        f = _doc().addObject('Part::Scale', 'Scale')
        f.Base = o; f.Uniform = True; f.UniformScale = 1.0; f.XScale = f.YScale = f.ZScale = 1.0
        o.Visibility = False; out.append(f.Name)
    _doc().recompute(); return out[-1]

@op
def part_cross_sections(sel=None, axis='Z', count=1, position=None, distance=None):
    """Part_CrossSections (CrossSections::apply): non-parametric Part::Feature '<name>_cs' with the compound of
    shape.slice(normal, d) wires; default one section through the bounding box centre, spacing = extent / count."""
    import Part
    out = []
    nv = {'X': App.Vector(1, 0, 0), 'Y': App.Vector(0, 1, 0), 'Z': App.Vector(0, 0, 1)}[axis]
    for o in _need(_objs(sel), 1, 'Select a shape'):
        sh = o.Shape; bb = sh.BoundBox
        c = getattr(bb.Center, axis.lower())
        ext = getattr(bb, axis + 'Length')
        pos = c if position is None else float(position)
        dist = (ext / count) if distance is None else float(distance)
        ds = [pos + (i - (count - 1) / 2.0) * dist for i in range(int(count))]
        wires = []
        for d in ds: wires += sh.slice(nv, d)
        f = _doc().addObject('Part::Feature', o.Name + '_cs')
        f.Shape = Part.makeCompound(wires); f.purgeTouched()
        out.append(f.Name)
    return out[-1]

@op
def part_project_on_surface(sel=None):
    """Part_ProjectionOnSurface (TaskProjectOnSurface): Part::ProjectOnSurface, SupportFace = first picked face,
    Projection = the other picked shapes/edges/faces."""
    s = _sel(sel)
    sup = next(((o, [x]) for o, subs in s for x in subs if x.startswith('Face')), None)
    if sup is None: raise RuntimeError('Select the target face first, then the shapes to project')
    rest = [(o, [x for x in subs if (o, [x]) != sup] or ['']) for o, subs in s]
    rest = [(o, subs) for o, subs in rest if not (o == sup[0] and subs == [''] and len(s) > 1)]
    f = _doc().addObject('Part::ProjectOnSurface', 'Projection')
    f.SupportFace = sup
    f.Projection = [(o, tuple(subs)) for o, subs in rest if o != sup[0] or subs != ['']]
    _doc().recompute(); return f.Name

# ------------------------------------------------------------------ BOPTools / CompoundTools
def _hide_children(f):
    for o in f.OutList:
        try: o.Visibility = False
        except Exception: pass

@op
def part_split(kind, sel=None):
    """Part_BooleanFragments / Part_Slice / Part_SliceApart / Part_XOR (BOPTools.SplitFeatures cmdCreate*)."""
    from BOPTools import SplitFeatures
    objs = _objs(sel)
    if kind == 'BooleanFragments':
        _need(objs, 1, 'Select at least one shape')
        f = SplitFeatures.makeBooleanFragments(name='BooleanFragments'); f.Objects = objs; f.Mode = 'Standard'
    elif kind in ('Slice', 'SliceApart'):
        _need(objs, 2, 'Select the shape to slice, then the slicing tools')
        f = SplitFeatures.makeSlice(name='Slice'); f.Base = objs[0]; f.Tools = objs[1:]
        f.Mode = 'Split' if kind == 'SliceApart' else 'Standard'
    elif kind == 'XOR':
        _need(objs, 1, 'Select shapes')
        f = SplitFeatures.makeXOR(name='XOR'); f.Objects = objs
    else:
        raise RuntimeError(kind)
    _doc().recompute(); _hide_children(f)
    if kind == 'SliceApart':
        from CompoundTools import Explode
        Explode.explodeCompound(f); f.Visibility = False; _doc().recompute()
    return f.Name

@op
def part_join(mode, sel=None):
    """Part_JoinConnect / JoinEmbed / JoinCutout (BOPTools.JoinFeatures.cmdCreateJoinFeature)."""
    from BOPTools import JoinFeatures
    objs = _objs(sel)
    f = getattr(JoinFeatures, 'make' + mode)(name=mode)
    if mode in ('Embed', 'Cutout'):
        _need(objs, 2, 'Select the base object, then the tool object')
        f.Base = objs[0]; f.Tool = objs[1]
    else:
        f.Objects = _need(objs, 1, 'Select shapes to connect')
    _doc().recompute(); _hide_children(f); return f.Name

@op
def part_compound_filter(sel=None):
    """Part_CompoundFilter (cmdCreateCompoundFilter): Base = first, Stencil = second ('collision-pass'), else 'window-volume'."""
    from CompoundTools import CompoundFilter
    objs = _need(_objs(sel), 1, 'Select a compound')
    f = CompoundFilter.makeCompoundFilter(name='CompoundFilter')
    f.Base = objs[0]
    if len(objs) == 2:
        f.Stencil = objs[1]; objs[1].Visibility = False; f.FilterType = 'collision-pass'
    else:
        f.FilterType = 'window-volume'
    _doc().recompute(); objs[0].Visibility = False; return f.Name

@op
def part_explode(sel=None):
    """Part_ExplodeCompound (CompoundTools.Explode.explodeCompound per selected object)."""
    from CompoundTools import Explode
    last = None
    for o in _need(_objs(sel), 1, 'Select a compound'):
        r = Explode.explodeCompound(o); o.Visibility = False
        last = r[0].Name if r and hasattr(r[0], 'Name') else last
    _doc().recompute(); return last

@op
def part_tolerance_set(sel=None, minTolerance=1e-7, maxTolerance=0.0):
    """Part_ToleranceSet (ToleranceFeatures.cmdCreateToleranceSetFeature)."""
    from BOPTools import ToleranceFeatures
    f = ToleranceFeatures.makeToleranceSet(name='ToleranceSet')
    f.minTolerance = minTolerance; f.maxTolerance = maxTolerance
    f.Objects = _need(_objs(sel), 1, 'Select shapes')
    _doc().recompute(); _hide_children(f); return f.Name

# ------------------------------------------------------------------ non-parametric shape commands
@op
def part_make_solid(sel=None):
    """Part_MakeSolid (CmdPartMakeSolid): Part.Solid(Part.Shell(faces)) for compounds, Part.Solid(shell) for shells."""
    import Part
    last = None
    for o in _need(_objs(sel), 1, 'Select a shell or compound'):
        sh = Part.getShape(o)
        if sh.ShapeType == 'Solid': continue
        if sh.ShapeType in ('Compound', 'CompSolid'): s = Part.Solid(Part.Shell(sh.Faces))
        elif sh.ShapeType == 'Shell': s = Part.Solid(sh)
        else: raise RuntimeError('%s is neither a shell nor a compound' % o.Label)
        n = _doc().addObject('Part::Feature', o.Name + '_solid'); n.Label = o.Label + ' (Solid)'; n.Shape = s
        last = n.Name
    return last

@op
def part_defeaturing(sel=None):
    """Part_Defeaturing (CmdPartDefeaturing): Part::Feature 'Defeatured' = shape.defeaturing(selected faces)."""
    last = None
    for o, subs in _sel(sel):
        faces = [x for x in subs if x.startswith('Face')]
        if not faces: continue
        sh = o.Shape
        nsh = sh.defeaturing([sh.getElement(x) for x in faces])
        if sh.isPartner(nsh): raise RuntimeError('Defeaturing failed')
        f = _doc().addObject('Part::Feature', 'Defeatured'); f.Shape = nsh; o.Visibility = False; last = f.Name
    if last is None: raise RuntimeError('Select faces to remove')
    return last

@op
def part_copy(mode, sel=None):
    """Part_SimpleCopy / TransformedCopy / ElementCopy / RefineShape-copy (_copyShape): Part::Feature with
    Part.getShape(obj, sub, needSubElement, refine)."""
    import Part
    resolve = mode == 'Simple'; need = mode == 'Element'
    last = None
    for o, subs in _sel(sel):
        targets = [''] if (resolve or not subs) else subs
        for sub in targets:
            sh = Part.getShape(o, sub if not resolve else '', needSubElement=need, refine=False)
            if need: sh = sh.copy()
            sobj = o.getSubObject(sub, retType=1) if sub else o
            f = _doc().addObject('Part::Feature', (sobj or o).Name)
            f.Shape = sh; f.Label = (sobj or o).Label; last = f.Name
    if last is None: raise RuntimeError('Select shapes to copy')
    return last

@op
def part_simple_cylinder(radius=2.0, length=10.0):
    """Part_SimpleCylinder (DlgPartCylinderImp): non-parametric Part.makeCylinder."""
    import Part
    f = _doc().addObject('Part::Feature', 'Cylinder')
    f.Shape = Part.makeCylinder(radius, length, App.Vector(0, 0, 0), App.Vector(0, 0, 1)); return f.Name

@op
def part_link_array(kind, sel=None):
    """Part_LinkArray{Linear,Polar,Circular,Path,Point} (createLinkArray): array object with LinkedObject = selection."""
    objs = _need(_objs(sel), 1, 'Select an object to array')
    f = _doc().addObject('Part::LinkArray' + kind, 'LinkArray' + kind)
    f.LinkedObject = objs[0]
    if kind == 'Circular':
        f.BinormalAxis = (None, ['Y_Axis'])     # PatternCircularParametersWidget: tangent axis combo index 1 (object Y)
    if kind in ('Path', 'Point') and len(objs) > 1:
        for p in ('Path', 'PathObject', 'Points', 'PointObject', 'Base'):
            if p in f.PropertiesList and 'Link' in f.getTypeIdOfProperty(p):
                try: setattr(f, p, objs[1]); break
                except Exception: pass
    objs[0].Visibility = False
    _doc().recompute(); return f.Name

@op
def shape_info(name):
    """Part_ShapeInfo / Part_CheckGeometry: the facts both desktop panels show (topology counts, bounds, volume, area,
    validity and the BOP check result)."""
    import Part
    o = _doc().getObject(name); sh = Part.getShape(o)
    info = {'type': sh.ShapeType, 'valid': sh.isValid(), 'closed': sh.isClosed() if sh.ShapeType in ('Shell', 'Solid', 'Wire', 'Edge') else None,
            'solids': len(sh.Solids), 'shells': len(sh.Shells), 'faces': len(sh.Faces), 'wires': len(sh.Wires),
            'edges': len(sh.Edges), 'vertexes': len(sh.Vertexes), 'volume': sh.Volume, 'area': sh.Area, 'length': sh.Length,
            'bbox': [sh.BoundBox.XMin, sh.BoundBox.YMin, sh.BoundBox.ZMin, sh.BoundBox.XMax, sh.BoundBox.YMax, sh.BoundBox.ZMax]}
    try:
        info['center'] = list(sh.CenterOfMass) if hasattr(sh, 'CenterOfMass') else list(sh.Solids[0].CenterOfMass)
    except Exception:
        pass
    try:
        sh.check(True); info['check'] = 'No errors'
    except Exception as e:
        info['check'] = str(e)
    return info

# ------------------------------------------------------------------ import / export (Part_Import / Part_Export)
@op
def import_file(path):
    """Part_Import / Std_Import: STEP/IGES go through Import.insert (as ImportGui would), BREP through Part.insert."""
    low = path.lower()
    before = {o.Name for o in _doc().Objects}
    if low.endswith(('.step', '.stp', '.iges', '.igs')):
        import Import; Import.insert(path, _doc().Name)
    elif low.endswith(('.stl', '.ply', '.off', '.amf', '.3mf')):
        import Mesh; Mesh.insert(path, _doc().Name)
    elif low.endswith(('.brep', '.brp')):
        import Part; Part.insert(path, _doc().Name)
    else:                                  # FreeCAD's registered importers (IFC, DXF, SVG, OBJ, DAE, glTF, CSG, inp ...)
        if low.endswith('.ifc'):
            from .bridge import _ifc_defaults; _ifc_defaults()
        _registered(App.getImportType, path).insert(path, _doc().Name)
    _doc().recompute()
    return [o.Name for o in _doc().Objects if o.Name not in before]

@op
def export_file(names, path):
    """Part_Export / Std_Export of the selected objects (or all visible shapes)."""
    d = _doc()
    objs = [d.getObject(n) for n in names] if names else [o for o in d.Objects if _export_whole(o)]
    low = path.lower()
    if low.endswith(('.step', '.stp', '.iges', '.igs')):
        import Import; Import.export(objs, path)
    elif low.endswith(('.stl', '.ply', '.off', '.amf', '.3mf')):
        import Mesh; Mesh.export(objs, path)
    elif low.endswith(('.brep', '.brp')):
        import Part; Part.export(objs, path)
    else:                                  # FreeCAD's registered exporters (IFC, DXF, SVG, OBJ, DAE, glTF, PDF ...)
        _registered(App.getExportType, path).export(objs, path)
    return True

def _export_whole(o):
    """With nothing selected the visible shapes are exported once each: a Body stands for its features, and datums
    (origin axes / planes, LCS) are left out as desktop's mesh / STEP exporters do."""
    if not (o.Visibility and hasattr(o, 'Shape')) or o.Shape.isNull(): return False
    if o.TypeId in ('App::Line', 'App::Plane', 'App::Point', 'App::LocalCoordinateSystem') or o.isDerivedFrom('Part::Datum'):
        return False
    g = o.getParentGeoFeatureGroup()
    return not (g and g.isDerivedFrom('PartDesign::Body') and o is not g)

def _registered(kind, path):
    """The module FreeCAD registered for this file type (Init.py addImportType / addExportType), GUI-free ones first."""
    import importlib, os
    ext = os.path.splitext(path)[1].lstrip('.').lower()
    mods = kind(ext) or []
    if isinstance(mods, str): mods = [mods]
    mods = sorted(mods, key=lambda m: 'Gui' in m)
    err = None
    for m in mods:
        try: return importlib.import_module(m)
        except Exception as e: err = e
    raise RuntimeError('No importer/exporter for .%s%s' % (ext, ': %s' % err if err else ''))

@op
def file_types():
    """Every import / export filter FreeCAD registered (Std_Import / Std_Export file dialogs)."""
    return {'import': App.getImportType(), 'export': App.getExportType()}

# ------------------------------------------------------------------ remaining PartDesign commands
@op
def pd_clone(sel=None):
    """PartDesign_Clone (CmdPartDesignClone): new Body whose BaseFeature is a PartDesign::FeatureBase of the solid."""
    o = _need(_objs(sel), 1, 'Select a solid')[0]
    d = _doc()
    body = d.addObject('PartDesign::Body', 'Body')
    clone = d.addObject('PartDesign::FeatureBase', 'Clone')
    body.AllowCompound = True
    body.Group = [clone]; body.Tip = clone
    clone.BaseFeature = o; clone.Placement = o.Placement
    clone.setEditorMode('Placement', 0)
    d.recompute(); return clone.Name

@op
def pd_coordinate_system(sel=None):
    """PartDesign_CoordinateSystem (UnifiedDatumCommand 'PartDesign::CoordinateSystem', 'Local_CS')."""
    import Part
    body = _active_body(sel)
    f = body.newObject('PartDesign::CoordinateSystem', 'Local_CS')
    refs = [(o, subs[0] if subs else '') for o, subs in _sel(sel) if o.TypeId != 'PartDesign::Body']
    if refs:
        f.AttachmentSupport = refs
        ae = Part.AttachEngine(f.AttacherType); ae.readParametersFromFeature(f)
        r = ae.suggestModes()
        if r.get('message') == 'OK': f.MapMode = r['bestFitMode']
    _doc().recompute(); return f.Name

@op
def part_coordinate_system(kind='LocalCoordinateSystem'):
    """Part_CoordinateSystem / Part_DatumPlane / DatumLine / DatumPoint (Part Command.cpp 'Add datum ...')."""
    labels = {'LocalCoordinateSystem': 'LCS', 'DatumPlane': 'DatumPlane', 'DatumLine': 'DatumLine', 'DatumPoint': 'DatumPoint'}
    f = _doc().addObject('Part::' + kind, labels[kind]); f.Label = labels[kind]; f.Visibility = True
    _doc().recompute(); return f.Name

@op
def pd_duplicate(sel=None):
    """PartDesign_DuplicateSelection: Std_DuplicateSelection, then add the allowed copies to the active body."""
    d = _doc(); objs = _need(_objs(sel), 1, 'Select objects')
    body = _active_body(sel, create=False)
    copies = d.copyObject(objs, False)
    if not isinstance(copies, list): copies = [copies]
    if body:
        for c in copies:
            if c.TypeId.startswith('PartDesign::') or c.TypeId == 'Sketcher::SketchObject':
                if _body_of(c) is None: body.addObject(c)
    d.recompute(); return copies[-1].Name

def _relink_to_origin(feat, target):
    """PartDesignGui::relinkToOrigin: attachment refs to the old body's origin features -> same role in the target."""
    if not hasattr(feat, 'AttachmentSupport'): return
    new = []
    for o, subs in feat.AttachmentSupport:
        role = getattr(o, 'Role', None)
        if role and o.TypeId.startswith('App::'):
            o = _origin(target, role)
        for s in (subs or ['']): new.append((o, s))
    feat.AttachmentSupport = new

@op
def pd_bodies():
    return [[o.Name, o.Label] for o in _doc().Objects if o.TypeId == 'PartDesign::Body']

@op
def pd_move_feature(names, target):
    """PartDesign_MoveFeature (CmdPartDesignMoveFeature): removeObject from the source body, addObject to the target
    (at its Tip), relink sketches/datums to the target origin."""
    d = _doc(); tb = d.getObject(target)
    for n in names:
        f = d.getObject(n); src = _body_of(f)
        if src is tb: continue
        if src: src.removeObject(f)
        tb.addObject(f)
        _relink_to_origin(f, tb)
    d.recompute(); return True

@op
def pd_features(name):
    """Features of the body containing `name`, for 'Move Feature After…'."""
    b = _body_of(_doc().getObject(name)); return [[o.Name, o.Label] for o in (b.Group if b else []) if o.Name != name]

@op
def pd_move_in_tree(names, after):
    """PartDesign_MoveFeatureInTree: body.insertObject(feat, target, True) for each feature, keeping order."""
    d = _doc(); tgt = d.getObject(after); b = _body_of(tgt)
    for n in reversed(names):
        f = d.getObject(n)
        b.removeObject(f); b.insertObject(f, tgt, True)
    d.recompute(); return True

@op
def pd_gear(kind):
    """PartDesign_InvoluteGear / PartDesign_Sprocket: the feature module's make*() and the active body/part group."""
    if kind == 'InvoluteGear':
        import InvoluteGearFeature; f = InvoluteGearFeature.makeInvoluteGear('InvoluteGear')
    else:
        import SprocketFeature, types
        if not hasattr(SprocketFeature, 'FreeCADGui'):
            # Sprocket.__init__ reads the reference names from the .ui combo box; they are column 3 of its own table
            table = SprocketFeature.Sprocket.SprocketReferenceRollerTable
            class _Combo:
                i = 0
                def setCurrentIndex(self, i): self.i = i
                def currentText(self): return table[self.i][3]
            form = types.SimpleNamespace(comboBox_SprocketReference=_Combo())
            SprocketFeature.FreeCADGui = types.SimpleNamespace(PySideUic=types.SimpleNamespace(loadUi=lambda *a: form))
        f = SprocketFeature.makeSprocket('Sprocket')
    body = _active_body(None, create=False)
    if body: body.Group = body.Group + [f]
    _doc().recompute(); return f.Name

@op
def pd_migrate():
    """PartDesign_Migrate: only pre-0.16 PartDesign files need it; report whether anything is legacy."""
    legacy = [o.Name for o in _doc().Objects if o.TypeId.startswith('PartDesign::') and _body_of(o) is None
              and o.TypeId not in ('PartDesign::Body',)]
    if not legacy: return 'Nothing to migrate'
    body = _doc().addObject('PartDesign::Body', 'Body')
    for n in legacy: body.addObject(_doc().getObject(n))
    _doc().recompute(); return 'Migrated %d features' % len(legacy)

# ------------------------------------------------------------------ Part_SectionCut / mesh conversions
@op
def part_section_cut(axis='X', position=None):
    """Part_SectionCut (PartGui::SectionCut): visible shapes -> 'SectionCutCompound', a Part::Box 'SectionCutBox<axis>'
    one unit larger than the compound and starting at the cut position, 'SectionCut<axis>' = Part::Cut(compound, box);
    the originals are hidden.  Applying it again moves the existing box."""
    d = _doc()
    cut = d.getObject('SectionCut' + axis)
    if cut is None:
        objs = [o for o in d.Objects if o.Visibility and hasattr(o, 'Shape') and not o.Shape.isNull()
                and not o.TypeId.startswith(('App::', 'PartDesign::Body')) and not o.Name.startswith('SectionCut')]
        comp = d.getObject('SectionCutCompound') or d.addObject('Part::Compound', 'SectionCutCompound')
        if not comp.Links: comp.Links = objs
        d.recompute()
        box = d.addObject('Part::Box', 'SectionCutBox' + axis)
        cut = d.addObject('Part::Cut', 'SectionCut' + axis); cut.Base = comp; cut.Tool = box
        for o in objs: o.Visibility = False
        comp.Visibility = False; box.Visibility = False
    box = cut.Tool; bb = cut.Base.Shape.BoundBox
    i = 'XYZ'.index(axis)
    lo = [bb.XMin, bb.YMin, bb.ZMin]; hi = [bb.XMax, bb.YMax, bb.ZMax]
    pos = (lo[i] + hi[i]) / 2 if position is None else float(position)
    box.Length, box.Width, box.Height = bb.XLength + 1, bb.YLength + 1, bb.ZLength + 1
    base = [lo[0] - 0.5, lo[1] - 0.5, lo[2] - 0.5]; base[i] = pos
    box.Placement.Base = App.Vector(*base)
    d.recompute(); return cut.Name

@op
def part_shape_from_mesh(sel=None, tolerance=0.1, sew=True):
    """Part_ShapeFromMesh (PartGui::ShapeFromMesh::perform): Part::Feature '<mesh>' with makeShapeFromMesh(Topology, tol, sew)."""
    import Part
    last = None
    for o in _objs(sel):
        if not o.isDerivedFrom('Mesh::Feature'): continue
        sh = Part.Shape(); sh.makeShapeFromMesh(o.Mesh.Topology, float(tolerance), bool(sew))
        f = _doc().addObject('Part::Feature', o.Name); f.Shape = sh; f.purgeTouched(); last = f.Name
    if last is None: raise RuntimeError('Select a mesh')
    return last

@op
def part_points_from_shape(sel=None, distance=None):
    """Part_PointsFromMesh 'Points From Shape' (BasicShapes.Utils.showCompoundFromPoints): sampled points, default spacing
    = bounding box diagonal / 20."""
    from BasicShapes import Utils
    last = None
    for o in _objs(sel):
        dist = float(distance) if distance else (o.Shape.BoundBox.DiagonalLength / 20 if hasattr(o, 'Shape') else 1.0)
        r = Utils.showCompoundFromPoints(o, dist)
        if r is not None: last = r.Name
    if last is None: raise RuntimeError('Select a shape')
    return last

@op
def part_builder(mode, sel=None, planar=True, refine=False):
    """Part_Builder (PartGui::ShapeBuilderWidget): edge from 2 vertexes, wire from edges, face from vertexes/edges
    (planar -> Part.Face, else makeFilledFace), shell from faces, solid from a shell; result is a Part::Feature."""
    import Part
    s = _sel(sel)
    def elems(prefix):
        out = []
        for o, subs in s:
            for x in subs:
                if x.startswith(prefix): out.append(o.Shape.getElement(x))
            if not subs and prefix == 'Face': out += o.Shape.Faces
        return out
    if mode == 'Edge':
        v = elems('Vertex')
        if len(v) != 2: raise RuntimeError('Select two vertexes')
        sh = Part.makeLine(v[0].Point, v[1].Point)
    elif mode == 'Wire':
        sh = Part.Wire(Part.__sortEdges__(elems('Edge') or _need([], 1, 'Select edges')))
    elif mode == 'FaceVertexes':
        pts = [v.Point for v in elems('Vertex')]
        if len(pts) < 3: raise RuntimeError('Select at least three vertexes')
        sh = Part.Face(Part.makePolygon(pts, True)) if planar else Part.makeFilledFace(Part.makePolygon(pts, True).Edges)
    elif mode == 'Face':
        e = elems('Edge') or _need([], 1, 'Select edges')
        sh = Part.Face(Part.Wire(Part.__sortEdges__(e))) if planar else Part.makeFilledFace(Part.__sortEdges__(e))
    elif mode == 'Shell':
        sh = Part.Shell(elems('Face') or _need([], 1, 'Select faces'))
        if refine: sh = sh.removeSplitter()
    elif mode == 'Solid':
        shell = next((o.Shape.Shells[0] for o, _ in s if o.Shape.Shells), None)
        if shell is None: raise RuntimeError('Select a shell')
        sh = Part.Solid(shell)
        if refine: sh = sh.removeSplitter()
    else:
        raise RuntimeError(mode)
    name = {'FaceVertexes': 'Face'}.get(mode, mode)
    f = _doc().addObject('Part::Feature', name); f.Shape = sh; return f.Name

@op
def part_import_curve_net(path):
    """Part_ImportCurveNet: a Part::CurveNet reading the curve network of a STEP / IGES / BREP file."""
    import os
    f = _doc().addObject('Part::CurveNet', os.path.splitext(os.path.basename(path))[0] or 'CurveNet')
    f.FileName = path
    _doc().recompute(); return f.Name

@op
def face_edges(obj, face):
    """DlgFilletEdges 'Select faces': the edges (EdgeN of the object's shape) bounding a face."""
    sh = _doc().getObject(obj).Shape; f = sh.getElement(face)
    return ['Edge%d' % i for i, e in enumerate(sh.Edges, 1) if any(e.isSame(x) for x in f.Edges)]
