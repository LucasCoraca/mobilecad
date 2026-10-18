"""Std_* commands that work on the document (src/Gui/CommandDoc.cpp, CommandLink.cpp, CommandFeat.cpp, CommandView.cpp ...),
implemented on FreeCAD's App API.  View/selection-only commands live in the Kotlin UI."""
import FreeCAD as App
from .bridge import op, _doc
from .partdesign import _sel

def _objs(names):
    d = _doc(); return [d.getObject(n) for n in names if d.getObject(n) is not None]

# ------------------------------------------------------------------ clipboard (Std_Copy / Std_Cut / Std_Paste)
# Desktop copies the selection (with dependencies) into a memory buffer through Document::exportObjects and pastes it
# with importObjects; a hidden scratch document gives the same result with copyObject.
_CLIP = '__mobilecad_clipboard__'

def _clip_doc(reset=False):
    d = App.listDocuments().get(_CLIP)
    if d is not None and reset:
        App.closeDocument(_CLIP); d = None
    if d is None:
        cur = App.ActiveDocument
        d = App.newDocument(_CLIP, hidden=True)
        if cur is not None: App.setActiveDocument(cur.Name)
    return d

@op
def clip_copy(names, cut=False):
    objs = _objs(names)
    if not objs: raise RuntimeError('Select objects to copy')
    c = _clip_doc(reset=True)
    c.copyObject(objs, True)
    if cut:
        d = _doc()
        for o in objs:
            if d.getObject(o.Name) is not None: d.removeObject(o.Name)
        d.recompute()
    return len(c.Objects)

@op
def clip_paste():
    c = App.listDocuments().get(_CLIP)
    if c is None or not c.Objects: raise RuntimeError('Nothing to paste')
    roots = [o for o in c.Objects if not o.InList]
    new = _doc().copyObject(roots, True)
    new = list(new) if isinstance(new, (list, tuple)) else [new]
    _doc().recompute()
    return [o.Name for o in new]

@op
def duplicate(names, deps=False):
    """Std_DuplicateSelection (Document::copyObject); deps = also copy the objects they depend on."""
    new = _doc().copyObject(_objs(names), bool(deps))
    new = list(new) if isinstance(new, (list, tuple)) else [new]
    _doc().recompute(); return [o.Name for o in new]

# ------------------------------------------------------------------ document
@op
def doc_info():
    """Std_ProjectInfo: the document's metadata properties."""
    d = _doc()
    keys = ('Label', 'FileName', 'CreatedBy', 'CreationDate', 'LastModifiedBy', 'LastModifiedDate', 'Company', 'License',
            'LicenseURL', 'Comment', 'Uid', 'Id')
    return {k: str(getattr(d, k, '')) for k in keys}

@op
def doc_set_info(key, value):
    setattr(_doc(), key, value); return True

@op
def merge_project(path):
    """Std_MergeProjects: Document.mergeProject."""
    before = {o.Name for o in _doc().Objects}
    _doc().mergeProject(path); _doc().recompute()
    return [o.Name for o in _doc().Objects if o.Name not in before]

@op
def save_copy(path):
    """Std_SaveCopy (with the document's view data, see bridge._write_gui_document)."""
    d = _doc(); d.saveCopy(path)
    from .bridge import _write_gui_document
    try: _write_gui_document(d, path)
    except Exception: pass
    return True

@op
def dependency_graph():
    """Std_DependencyGraph / Std_ExportDependencyGraph: Graphviz text plus a readable listing."""
    d = _doc()
    lines = []
    for o in d.Objects:
        if o.OutList: lines.append('%s → %s' % (o.Label, ', '.join(x.Label for x in o.OutList)))
    return {'dot': d.DependencyGraph, 'text': '\n'.join(lines) or 'No dependencies'}

@op
def toggle_skip_recompute():
    """Std_ToggleSkipRecompute."""
    d = _doc(); d.RecomputesFrozen = not d.RecomputesFrozen; return d.RecomputesFrozen

@op
def force_recompute(names=None):
    """Std_Recompute / Std_Refresh: touch (selected or all) and recompute."""
    d = _doc()
    for o in (_objs(names) if names else d.Objects): o.touch()
    d.recompute(); return True

@op
def toggle_suppress(names):
    """Std_ToggleSuppress: flip the SuppressibleExtension 'Suppressed' property where it is visible."""
    n = 0
    for o in _objs(names):
        if 'Suppressed' in o.PropertiesList and 'Hidden' not in o.getPropertyStatus('Suppressed'):
            o.Suppressed = not o.Suppressed; n += 1
    if not n: raise RuntimeError('The selection cannot be suppressed')
    _doc().recompute(); return n

# ------------------------------------------------------------------ links (CommandLink.cpp)
@op
def link_group(names):
    """Std_LinkMakeGroup: App::LinkGroup holding links to the selection."""
    g = _doc().addObject('App::LinkGroup', 'LinkGroup')
    g.ElementList = _objs(names); _doc().recompute(); return g.Name

@op
def link_relative(sel):
    """Std_LinkMakeRelative: App::Link to a sub-object (LinkedObject = (obj, sub))."""
    o, subs = _sel(sel)[0]
    l = _doc().addObject('App::Link', 'Link')
    l.LinkedObject = (o, subs[0] if subs else ''); _doc().recompute(); return l.Name

@op
def link_replace(names):
    """Std_LinkReplace: put an App::Link to each object where the object was referenced, keep the object hidden."""
    out = []
    for o in _objs(names):
        l = _doc().addObject('App::Link', o.Name + '_link'); l.LinkedObject = o; l.Placement = o.Placement
        for p in o.InList:
            if p is l: continue
            for prop in p.PropertiesList:
                v = getattr(p, prop, None)
                if v is o: setattr(p, prop, l)
                elif isinstance(v, list) and o in v: setattr(p, prop, [l if x is o else x for x in v])
        o.Visibility = False; out.append(l.Name)
    _doc().recompute(); return out

@op
def link_unlink(names):
    """Std_LinkUnlink: replace each link by its linked object where it is referenced, then delete the link."""
    d = _doc()
    for l in _objs(names):
        tgt = l.getLinkedObject(False)
        if tgt is None or tgt is l: continue
        for p in l.InList:
            for prop in p.PropertiesList:
                v = getattr(p, prop, None)
                if v is l: setattr(p, prop, tgt)
                elif isinstance(v, list) and l in v: setattr(p, prop, [tgt if x is l else x for x in v])
        tgt.Visibility = True
        d.removeObject(l.Name)
    d.recompute(); return True

@op
def link_import(names=None):
    """Std_LinkImport / Std_LinkImportAll: Document.importLinks (external links become local copies)."""
    d = _doc()
    r = d.importLinks(_objs(names)) if names else d.importLinks()
    d.recompute(); return [o.Name for o in (r or [])]

@op
def linked(name, final=False):
    """Std_LinkSelectLinked / Std_LinkSelectLinkedFinal."""
    o = _doc().getObject(name)
    t = o.getLinkedObject(bool(final)) if final else getattr(o, 'LinkedObject', None)
    if isinstance(t, tuple): t = t[0]
    if t is None or t is o: raise RuntimeError('Not a link')
    return t.Name

@op
def links_to(name):
    """Std_LinkSelectAllLinks / Std_TreeSelectAllInstances."""
    o = _doc().getObject(name)
    return [l.Name for l in _doc().Objects if l is not o and l.TypeId.startswith('App::Link') and l.getLinkedObject(True) is o]

# ------------------------------------------------------------------ measurement
@op
def mass_properties(names):
    """Std_MassProperties: volume, area, mass (from ShapeMaterial density), centre of mass and inertia."""
    import Part
    out = []
    for o in _objs(names):
        sh = Part.getShape(o)
        r = {'label': o.Label, 'volume': sh.Volume, 'area': sh.Area}
        try:
            dens = App.Units.Quantity(o.ShapeMaterial.PhysicalProperties['Density']).getValueAs('kg/m^3').Value
        except Exception:
            dens = 0
        if dens > 1e-6: r['density'] = dens; r['mass'] = sh.Volume * 1e-9 * dens     # kg/m³ × mm³
        try:
            r['center'] = list(sh.CenterOfMass if hasattr(sh, 'CenterOfMass') else sh.Solids[0].CenterOfMass)
            m = sh.MatrixOfInertia if hasattr(sh, 'MatrixOfInertia') else sh.Solids[0].MatrixOfInertia
            r['inertia'] = [m.A11, m.A22, m.A33, m.A12, m.A13, m.A23]
        except Exception:
            pass
        out.append(r)
    return out

@op
def measure(sel):
    """Std_Measure: what the Measure workbench reports for the selected elements — position, length, radius, area,
    distance (minimum, plus dX/dY/dZ between points) and angle between two linear edges / planar faces."""
    s = []
    for o, subs in _sel(sel):
        for x in (subs or ['']):
            s.append((o, x, o.Shape.getElement(x) if x else o.Shape))
    if not s: raise RuntimeError('Select elements to measure')
    res = []
    if len(s) == 1:
        o, x, e = s[0]
        t = e.ShapeType
        if t == 'Vertex': res.append(('Position', '(%.4f, %.4f, %.4f) mm' % (e.X, e.Y, e.Z)))
        elif t == 'Edge':
            res.append(('Length', '%.4f mm' % e.Length))
            c = e.Curve
            if hasattr(c, 'Radius'): res.append(('Radius', '%.4f mm' % c.Radius)); res.append(('Diameter', '%.4f mm' % (2 * c.Radius)))
        elif t == 'Face':
            res.append(('Area', '%.4f mm²' % e.Area))
            if hasattr(e.Surface, 'Radius'): res.append(('Radius', '%.4f mm' % e.Surface.Radius))
        else:
            res.append(('Volume', '%.4f mm³' % e.Volume)); res.append(('Area', '%.4f mm²' % e.Area))
    else:
        (o1, x1, a), (o2, x2, b) = s[0], s[1]
        dist, pairs, _ = a.distToShape(b)
        res.append(('Distance', '%.4f mm' % dist))
        if pairs:
            p, q = pairs[0]
            res.append(('ΔX ΔY ΔZ', '%.4f  %.4f  %.4f mm' % (q.x - p.x, q.y - p.y, q.z - p.z)))
        def direction(e):
            if e.ShapeType == 'Edge' and e.Curve.TypeId == 'Part::GeomLine': return e.Curve.Direction
            if e.ShapeType == 'Face' and e.Surface.TypeId == 'Part::GeomPlane': return e.normalAt(0, 0)
            return None
        da, db = direction(a), direction(b)
        if da is not None and db is not None:
            import math
            res.append(('Angle', '%.4f°' % math.degrees(da.getAngle(db))))
    return [[k, v] for k, v in res]

# ------------------------------------------------------------------ appearance / material / preferences
@op
def materials():
    """Std_SetMaterial: the material library (uuid, name), sorted by name."""
    import Materials
    mm = Materials.MaterialManager()
    return sorted([[u, m.Name] for u, m in mm.Materials.items()], key=lambda x: x[1].lower())

@op
def set_material(names, uuid):
    import Materials
    m = Materials.MaterialManager().getMaterial(uuid)
    n = 0
    for o in _objs(names):
        if 'ShapeMaterial' in o.PropertiesList: o.ShapeMaterial = m; n += 1
    if not n: raise RuntimeError('The selection has no material')
    return m.Name

def _mat_dict(m):
    return {'uuid': m.UUID, 'name': m.Name, 'library': m.LibraryName, 'directory': m.Directory, 'author': m.Author,
            'license': m.License, 'description': m.Description, 'parent': m.Parent, 'url': m.URL,
            'physical': {k: str(v) for k, v in m.PhysicalProperties.items()},
            'appearance': {k: str(v) for k, v in m.AppearanceProperties.items()}}

@op
def material_info(uuid):
    """Material_Edit (MatGui::MaterialsEditor): a library material with its physical and appearance properties."""
    import Materials
    return _mat_dict(Materials.MaterialManager().getMaterial(uuid))

@op
def material_save(uuid, name, values):
    """MaterialsEditor 'Save': the edited material as a new material in the User library (inherits the original)."""
    import Materials, re
    mm = Materials.MaterialManager(); base = mm.getMaterial(uuid)
    m = mm.inheritMaterial(uuid) if hasattr(mm, 'inheritMaterial') else base
    m.Name = name
    for k, v in (values or {}).items():
        if base.hasPhysicalProperty(k): m.setPhysicalValue(k, v)
        elif base.hasAppearanceProperty(k): m.setAppearanceValue(k, v)
    mm.save('User', m, '/' + re.sub(r'[^\w\- ]', '_', name) + '.FCMat', True, True, True)
    return m.UUID

@op
def material_inspect(names, appearance=False):
    """Materials_InspectMaterial / Materials_InspectAppearance: the material assigned to each selected object."""
    out = []
    for o in _objs(names):
        m = getattr(o, 'ShapeMaterial', None)
        if m is None: out.append('%s: no material' % o.Label); continue
        d = _mat_dict(m)
        props = d['appearance'] if appearance else dict(d['physical'], **{'(appearance) ' + k: v for k, v in d['appearance'].items()})
        out.append('%s — %s\nLibrary: %s  UUID: %s\n%s' % (o.Label, d['name'], d['library'], d['uuid'],
                   '\n'.join('  %s = %s' % kv for kv in sorted(props.items()))))
    return '\n\n'.join(out)

@op
def toggle_freeze(names):
    """Std_ToggleFreeze: frozen objects are excluded from recomputation."""
    out = []
    for o in _objs(names):
        if o.isFreezed(): o.unfreeze()
        else: o.freeze()
        out.append([o.Name, o.isFreezed()])
    return out

@op
def material_color(names):
    """Diffuse colour of each object's ShapeMaterial appearance (what the desktop view provider shows)."""
    out = {}
    for o in _objs(names):
        try:
            c = o.ShapeMaterial.AppearanceProperties.get('DiffuseColor')
            if c:
                v = [float(x) for x in c.strip('()').split(',')[:3]]
                out[o.Name] = [int(round(x * 255)) for x in v]
        except Exception:
            pass
    return out

@op
def unit_schemas():
    return {'schemas': App.Units.listSchemas(), 'current': App.ParamGet('User parameter:BaseApp/Preferences/Units').GetInt('UserSchema', 0),
            'decimals': App.ParamGet('User parameter:BaseApp/Preferences/Units').GetInt('Decimals', 2)}

@op
def set_unit_schema(index=None, decimals=None):
    """Std_DlgPreferences > General > Units."""
    p = App.ParamGet('User parameter:BaseApp/Preferences/Units')
    if index is not None: p.SetInt('UserSchema', int(index)); App.Units.setSchema(int(index))
    if decimals is not None: p.SetInt('Decimals', int(decimals))
    return True

@op
def convert_units(text, unit):
    """Std_UnitsCalculator."""
    q = App.Units.Quantity(text)
    return '%s = %.8g %s' % (q.UserString, q.getValueAs(unit).Value, unit)

@op
def run_macro(path):
    """Std_DlgMacroExecute: run a .FCMacro / .py file like the desktop macro executor (App/doc predefined)."""
    g = {'__name__': '__main__', '__file__': path, 'App': App, 'FreeCAD': App}
    with open(path, encoding='utf-8') as f: code = f.read()
    exec(compile(code, path, 'exec'), g)
    _doc().recompute(); return True

@op
def console(code):
    """Std_CommandLine / Python console: exec, or eval for expressions; returns the printed output and value."""
    import io, contextlib
    g = globals().setdefault('_console_ns', {'App': App, 'FreeCAD': App, '__name__': '__console__'})
    g['doc'] = App.ActiveDocument
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
        try:
            val = eval(compile(code, '<console>', 'eval'), g)
            if val is not None: print(repr(val))
        except SyntaxError:
            exec(compile(code, '<console>', 'exec'), g)
    if App.ActiveDocument: App.ActiveDocument.recompute()
    return buf.getvalue()


@op
def align_objects(names, pts):
    """Std_Alignment (Gui/ManualAlignment.cpp): point pairs (moving, fixed); 1 pair translates, 2 pairs also align the
    direction, 3 pairs give the full rigid transform (frame fit; exact when the pairs are congruent)."""
    P = [App.Vector(*p) for p in pts]
    mov, fix = P[0::2], P[1::2]
    n = len(mov)
    if n == 1:
        pl = App.Placement(fix[0] - mov[0], App.Rotation())
    elif n == 2:        # align the pair axis with the minimal rotation, then move the first point onto its match
        r = App.Rotation(mov[1] - mov[0], fix[1] - fix[0])
        pl = App.Placement(fix[0] - r.multVec(mov[0]), r)
    else:               # 3 pairs: map the frame spanned by the moving points onto the fixed points' frame
        def frame(a, b, c):
            x = (b - a).normalize(); z = x.cross(c - a).normalize(); y = z.cross(x)
            return App.Rotation(x, y, z, 'ZXY')
        r = frame(*fix[:3]).multiply(frame(*mov[:3]).inverted())
        cm = (mov[0] + mov[1] + mov[2]) * (1 / 3.0); cf = (fix[0] + fix[1] + fix[2]) * (1 / 3.0)
        pl = App.Placement(cf - r.multVec(cm), r)
    for o in _objs(names):
        o.Placement = pl.multiply(o.Placement)
    _doc().recompute(); return True
