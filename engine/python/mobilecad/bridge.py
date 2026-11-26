"""JSON request handler called from JNI (fcbridge.cpp).  Every request: {"op": ..., ...} -> JSON reply.
Only FreeCAD's public App API is used, so documents behave exactly as on desktop and save as native .FCStd."""
import json, traceback
import FreeCAD as App

_ops = {}
def op(fn):
    _ops[fn.__name__] = fn
    return fn

def handle(req_json):
    try:
        req = json.loads(req_json)
        res = _ops[req.pop('op')](**req)
        return json.dumps({'ok': True, 'result': res})
    except Exception as e:
        return json.dumps({'ok': False, 'error': '%s: %s' % (type(e).__name__, e), 'trace': traceback.format_exc()})

# ------------------------------------------------------------------ interface language
import _i18n
from _i18n import tr as _tr
try: App.Qt.translate = _i18n.translate     # FreeCAD.Qt.translate (Base::Translate): the catalogs Gui's translator holds
except Exception: pass

@op
def set_language(path=None, language='en'):
    """Preferences > Language: FreeCAD's own strings (task panels, Python commands' messages) in that language,
    looked up by (context, source) as the desktop's QTranslator does; path None = English."""
    _i18n.load(path, language)
    return _i18n.language()

def _doc():
    d = App.ActiveDocument
    if d is None:
        d = App.newDocument('Unnamed')
    return d

# ------------------------------------------------------------------ documents
@op
def new_document(name='Unnamed'):
    d = App.newDocument(name); App.setActiveDocument(d.Name); d.UndoMode = 1
    return d.Name

def _ifc_defaults():
    """NativeIFC on mobile: real shapes (the desktop default 'coin only' has nothing to tessellate) and every element
    loaded (the desktop loads children when the tree item is expanded), unless the user chose otherwise."""
    p = App.ParamGet('User parameter:BaseApp/Preferences/Mod/NativeIFC')
    if 'ShapeMode' not in p.GetInts(): p.SetInt('ShapeMode', 0)
    if 'ImportStrategy' not in p.GetInts(): p.SetInt('ImportStrategy', 2)

@op
def open_document(path):
    from .part import _with_type
    path = _with_type(path)
    if path.lower().endswith('.ifc'): _ifc_defaults()
    if not path.lower().endswith(('.fcstd', '.fcstd1')):     # Std_Open of an IFC / STEP / DXF ...: the registered opener
        import importlib, os
        ext = os.path.splitext(path)[1].lstrip('.').lower()
        mods = App.getImportType(ext) or []
        mods = [mods] if isinstance(mods, str) else sorted(mods, key=lambda m: 'Gui' in m)
        m = importlib.import_module(mods[0]) if mods else None
        if m is not None and hasattr(m, 'open'):
            r = m.open(path); d = r if isinstance(r, App.Document) else App.ActiveDocument
        else:
            d = App.newDocument(os.path.splitext(os.path.basename(path))[0]); App.setActiveDocument(d.Name)
            from .part import import_file
            import_file.__wrapped__(path) if hasattr(import_file, '__wrapped__') else import_file(path)
        d.UndoMode = 1
        return d.Name
    d = App.openDocument(path); App.setActiveDocument(d.Name); d.UndoMode = 1
    try: _view_data[d.Name] = _read_gui_document(d, path)
    except Exception as e: App.Console.PrintWarning('View data of %s: %s\n' % (path, e))
    try: _keep_gui_files(d, path)
    except Exception as e: App.Console.PrintWarning('View data of %s: %s\n' % (path, e))
    return d.Name

_view_data = {}      # document -> what GuiDocument.xml says about each object's look (desktop view providers)

def _read_gui_document(d, path):
    """The view-provider data the desktop saves in GuiDocument.xml: visibility (it, not the App property, decides what
    is shown: hidden Origin groups, toggled features), shape colour / transparency (ShapeAppearance material files or the
    older ShapeColor / DiffuseColor) and per-face colours.  Visibility is applied to the objects; colours are returned."""
    import zipfile, struct, xml.etree.ElementTree as ET
    z = zipfile.ZipFile(path)
    if 'GuiDocument.xml' not in z.namelist(): return {}
    root = ET.fromstring(z.read('GuiDocument.xml'))
    def packed(v):          # 0xRRGGBBAA
        v = int(v) & 0xFFFFFFFF
        return [((v >> 24) & 255) / 255.0, ((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0]
    def materials(fname):   # PropertyMaterialList::RestoreDocFile: [count] then 4 packed colours + shininess + transparency each
        b = z.read(fname); off = 0
        n, = struct.unpack_from('<i', b, 0); off = 4
        if n < 0: n, = struct.unpack_from('<I', b, 4); off = 8
        out = []
        for k in range(min(n, 100000)):
            if off + 24 > len(b): break
            amb, dif, spe, emi, shi, tr = struct.unpack_from('<IIIIff', b, off); off += 24
            out.append((packed(dif), tr))
        return out
    def colors(fname):      # PropertyColorList: [count] packed colours
        b = z.read(fname); n, = struct.unpack_from('<I', b, 0)
        return [packed(struct.unpack_from('<I', b, 4 + 4 * k)[0]) for k in range(min(n, (len(b) - 4) // 4))]
    out = {'colors': {}, 'transparency': {}, 'faces': {}, 'font': {}}
    for vp in root.iter('ViewProvider'):
        o = d.getObject(vp.get('name') or '')
        if o is None: continue
        props = {p.get('name'): p for p in vp.iter('Property')}
        v = props.get('Visibility')
        if v is not None and v.find('Bool') is not None:
            vis = v.find('Bool').get('value') == 'true'
            if hasattr(o, 'Visibility') and bool(o.Visibility) != vis:
                try: o.Visibility = vis
                except Exception: pass
        fs = props.get('FontSize')             # Draft / BIM annotation text height (model units)
        if fs is not None:
            for tag in ('PropertyLength', 'Float', 'PropertyFloat'):
                e = fs.find(tag)
                if e is not None and e.get('value'):
                    try: out['font'][o.Name] = float(e.get('value')); break
                    except ValueError: pass
        try:
            mats = None
            ap = props.get('ShapeAppearance')
            if ap is not None and ap.find('MaterialList') is not None and ap.find('MaterialList').get('file'):
                mats = materials(ap.find('MaterialList').get('file'))
            if mats:
                out['colors'][o.Name] = mats[0][0]
                if mats[0][1] > 0.01: out['transparency'][o.Name] = int(round(mats[0][1] * 100))
                if len(mats) > 1 and any(m[0] != mats[0][0] for m in mats): out['faces'][o.Name] = [m[0] for m in mats]
            else:
                sc = props.get('ShapeColor')
                if sc is not None and sc.find('PropertyColor') is not None: out['colors'][o.Name] = packed(sc.find('PropertyColor').get('value'))
                tr = props.get('Transparency')
                if tr is not None and tr.find('Integer') is not None and int(tr.find('Integer').get('value')) > 0:
                    out['transparency'][o.Name] = int(tr.find('Integer').get('value'))
                dc = props.get('DiffuseColor')
                if dc is not None and dc.find('ColorList') is not None and dc.find('ColorList').get('file'):
                    fc = colors(dc.find('ColorList').get('file'))
                    if len(fc) > 1 and any(c != fc[0] for c in fc): out['faces'][o.Name] = fc
        except Exception:
            pass
    # the default grey is not a colour choice: leave those to the app's own default
    grey = [0.8, 0.8, 0.8]
    out['colors'] = {k: c for k, c in out['colors'].items() if any(abs(a - b) > 0.02 for a, b in zip(c, grey))}
    return out

@op
def view_data():
    """Colours / transparency / face colours saved with the active document (see _read_gui_document)."""
    return _view_data.get(_doc().Name, {})

@op
def save_document(path=None):
    d = _doc()
    if path: d.saveAs(path)
    else: d.save()
    try: _write_gui_document(d, d.FileName)
    except Exception as e: App.Console.PrintWarning('Keeping view data: %s\n' % e)
    return d.FileName

_gui_files = {}      # document -> {archive name: bytes}: GuiDocument.xml and the view-provider files it references

def _keep_gui_files(d, path):
    """Headless FreeCAD writes no GuiDocument.xml, so a file saved here would lose every colour, visibility and display
    setting the desktop stored.  Keep the original view data to write it back on save."""
    import zipfile, xml.etree.ElementTree as ET
    z = zipfile.ZipFile(path)
    if 'GuiDocument.xml' not in z.namelist(): return
    data = z.read('GuiDocument.xml'); root = ET.fromstring(data)
    files = {'GuiDocument.xml': data}
    for el in root.iter():
        f = el.get('file')
        if f and f in z.namelist(): files[f] = z.read(f)
    _gui_files[d.Name] = files

def _write_gui_document(d, path):
    """Puts the kept view data back into the saved archive: Visibility follows the objects now, view providers of
    deleted objects are dropped (new objects get the desktop's defaults when it opens the file)."""
    files = _gui_files.get(d.Name)
    if not files or not path: return
    import zipfile, os, shutil, xml.etree.ElementTree as ET
    root = ET.fromstring(files['GuiDocument.xml'])
    for vpl in root.iter('ViewProviderData'):
        for vp in list(vpl):
            o = d.getObject(vp.get('name') or '')
            if o is None: vpl.remove(vp); continue
            for prop in vp.iter('Property'):
                if prop.get('name') == 'Visibility' and prop.find('Bool') is not None and hasattr(o, 'Visibility'):
                    prop.find('Bool').set('value', 'true' if o.Visibility else 'false')
        vpl.set('Count', str(len(list(vpl))))
    gui = b"<?xml version='1.0' encoding='utf-8'?>\n" + ET.tostring(root)
    tmp = path + '.tmp'
    with zipfile.ZipFile(path) as src, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as dst:
        names = set(src.namelist())
        for info in src.infolist():
            if info.filename == 'GuiDocument.xml': continue
            dst.writestr(info, src.read(info.filename))
        dst.writestr('GuiDocument.xml', gui)
        for n, b in files.items():
            if n != 'GuiDocument.xml' and n not in names: dst.writestr(n, b)
    shutil.move(tmp, path)

@op
def info():
    return {'version': App.Version(), 'modules': sorted(App.listImportModules() and []) }

# ------------------------------------------------------------------ objects / tree
def _failed_dependency(o):
    """A feature left uncomputed (Touched, no shape) because something it depends on failed: that object, else None."""
    if 'Touched' not in o.State or 'Invalid' in o.State or 'Error' in o.State or not _has_shape_prop(o): return None
    try:
        if not _shape(o).isNull(): return None
    except Exception: pass
    return next((d for d in o.OutListRecursive if 'Invalid' in d.State or 'Error' in d.State), None)

def _assembly_of(o):
    """The assembly containing o through its group chain (GroupExtension::getGroupOfObject), or None. Links don't count:
    a body linked into an assembly as a component stays a part of the document, like on the desktop."""
    par = _parents[0]
    def parent(o):   # the group holding o, or the Part / Body whose Origin it is
        if par is not None: return par.get(o.Name)
        return next((p for p in o.InList if p.TypeId != 'App::Link' and p.hasExtension('App::GroupExtension')
                     and (o in p.Group or getattr(p, 'Origin', None) == o)), None)
    seen = {o.Name}; o = parent(o)
    while o is not None and o.Name not in seen:
        if o.TypeId == 'Assembly::AssemblyObject': return o.Name
        seen.add(o.Name); o = parent(o)
    return None

def _has_shape_prop(o):
    return 'Shape' in o.PropertiesList

def _obj_info(o):
    blocked = _failed_dependency(o)   # shown and reported as failed, like desktop's error overlay on the chain
    return {'name': o.Name, 'label': o.Label, 'type': o.TypeId,
            # an App::Origin hides its axes / planes when it is hidden (ViewProviderCoordinateSystem), whatever their own flag
            'visible': bool(getattr(o, 'Visibility', True)) and not any(p.TypeId == 'App::Origin' and not p.Visibility for p in o.InList),
            'state': list(o.State), 'invalid': 'Invalid' in o.State or 'Error' in o.State or blocked is not None,
            'parents': [p.Name for p in o.InList],
            'group': [c.Name for c in getattr(o, 'Group', [])] if hasattr(o, 'Group') else [],
            'tip': o.Tip.Name if getattr(o, 'Tip', None) is not None and o.TypeId == 'PartDesign::Body' else None,
            'hasShape': _has_shape(o),
            'assembly': _assembly_of(o),
            'linked': o.LinkedObject.Name if o.TypeId == 'App::Link' and o.LinkedObject is not None else None,
            'error': o.getStatusString() if ('Invalid' in o.State or 'Error' in o.State) else
                     ('%s failed: %s' % (blocked.Label, blocked.getStatusString()) if blocked is not None else None),
            'subtype': ('Joint:' + o.JointType) if hasattr(o, 'JointType') else ('Grounded' if hasattr(o, 'ObjectToGround') else None),
            # App::SuppressibleExtension (PartDesign features ...): the tree strikes it out with the suppressed overlay
            'suppressed': bool(getattr(o, 'Suppressed', False)) if 'Suppressed' in o.PropertiesList else False,
            # Part::AttachExtension (sketches, datums, primitives ...): the tree menu's "Edit Attachment"
            'attachable': o.hasExtension('Part::AttachExtension'),
            # the active body is shown in bold in the tree (Tree.cpp ActiveObject highlight)
            'active': o.TypeId == 'PartDesign::Body' and o is _active_body_now(),
            'claims': _claims(o)}

def _active_body_now():
    try:
        from .partdesign import explicit_pdbody
        return explicit_pdbody()
    except Exception: return None

def _links(v):
    """Objects named by a link property value (Link, LinkSub, LinkList, LinkSubList)."""
    out = []
    for x in (v if isinstance(v, (list, tuple)) and not (len(v) == 2 and isinstance(v[0], App.DocumentObject) and isinstance(v[1], (list, tuple, str))) else [v]):
        if isinstance(x, App.DocumentObject): out.append(x)
        elif isinstance(x, (list, tuple)) and x and isinstance(x[0], App.DocumentObject): out.append(x[0])
    return out

# ViewProvider::claimChildren of the desktop view providers, by type (the tree nests what a feature consumes)
_CLAIM_PROPS = {
    'Part::Cut': ('Base', 'Tool'), 'Part::Fuse': ('Base', 'Tool'), 'Part::Common': ('Base', 'Tool'), 'Part::Section': ('Base', 'Tool'),
    'Part::MultiFuse': ('Shapes',), 'Part::MultiCommon': ('Shapes',), 'Part::Compound': ('Links',), 'Part::Compound2': ('Links',),
    'Part::Fillet': ('Base',), 'Part::Chamfer': ('Base',), 'Part::Extrusion': ('Base',), 'Part::Revolution': ('Source',),
    'Part::Mirroring': ('Source',), 'Part::Offset': ('Source',), 'Part::Offset2D': ('Source',), 'Part::Thickness': ('Faces',),
    'Part::Loft': ('Sections',), 'Part::Sweep': ('Sections', 'Spine'), 'Part::RuledSurface': ('Curve1', 'Curve2'), 'Part::Face': ('Sources',),
    'Part::Refine': ('Source',), 'Part::Reverse': ('Source',), 'Part::Scale': ('Base',), 'Part::FilletArc': (), 'Part::BooleanFragments': ('Objects',),
    'PartDesign::AdditiveLoft': ('Profile', 'Sections'), 'PartDesign::SubtractiveLoft': ('Profile', 'Sections'),
    'PartDesign::AdditivePipe': ('Profile', 'Spine', 'Sections', 'AuxiliarySpine'), 'PartDesign::SubtractivePipe': ('Profile', 'Spine', 'Sections', 'AuxiliarySpine'),
    'PartDesign::Boolean': ('Group',), 'TechDraw::DrawPage': ('Views', 'Template'),
}
_PROFILE_BASED = ('PartDesign::Pad', 'PartDesign::Pocket', 'PartDesign::Revolution', 'PartDesign::Groove', 'PartDesign::Hole',
                  'PartDesign::AdditiveHelix', 'PartDesign::SubtractiveHelix')

def _claims(o):
    """The objects FreeCAD's tree shows under [o] (ViewProvider*::claimChildren)."""
    t = o.TypeId; out = []
    try:
        if t == 'PartDesign::Body':
            out = ([o.Origin] if getattr(o, 'Origin', None) else []) + ([o.BaseFeature] if getattr(o, 'BaseFeature', None) else []) + list(o.Group)
        elif t == 'App::Origin': out = list(o.OriginFeatures)
        elif t in _PROFILE_BASED:
            out = [x for x in _links(o.Profile) if x.isDerivedFrom('Sketcher::SketchObject') or x.isDerivedFrom('Part::Part2DObject')]
        elif t in _CLAIM_PROPS:
            for p in _CLAIM_PROPS[t]:
                if p in o.PropertiesList: out += _links(getattr(o, p))
        elif t.startswith('TechDraw::DrawView') or t in ('TechDraw::DrawProjGroup',):
            # a view claims its dimensions / balloons / hatches / leaders (ViewProviderViewPart::claimChildren)
            out = [x for x in o.InList if x.TypeId.startswith('TechDraw::') and getattr(x, 'References2D', None) is not None and o in _links(x.References2D)]
            out += [x for x in o.InList if getattr(x, 'SourceView', None) == o or getattr(x, 'BaseView', None) == o]
            if hasattr(o, 'Views'): out += list(o.Views)
        elif hasattr(o, 'Group') and isinstance(o.Group, list):
            # App::Part / Assembly: their Origin first, as on desktop
            out = ([o.Origin] if getattr(o, 'Origin', None) is not None and o.Origin not in o.Group else []) + list(o.Group)
            if hasattr(o, 'Proxy'):           # BIM components: base + additions / subtractions as well
                for p in ('Base', 'Additions', 'Subtractions', 'Armatures'):
                    if p in o.PropertiesList: out += _links(getattr(o, p))
        elif hasattr(o, 'Proxy'):
            # Draft / BIM features (view providers claim what they are made from)
            for p in ('Base', 'Additions', 'Subtractions', 'Armatures', 'PathObject', 'Objects' if 'Clone' in type(o.Proxy).__name__ else ''):
                if p and p in o.PropertiesList: out += _links(getattr(o, p))
    except Exception:
        pass
    seen = []
    for x in out:
        if x is not None and x != o and x.Name not in seen: seen.append(x.Name)
    return seen

def _has_shape(o):
    # BIM BuildingPart (Site, Building, Level ...): its Shape is the compound of its children for queries, but its view
    # provider draws only a label / level marker, never the shape (ArchBuildingPart.ViewProviderBuildingPart)
    if type(getattr(o, 'Proxy', None)).__name__ == 'BuildingPart': return False
    if o.TypeId == 'App::Link':
        try:
            import Part
            return not Part.getShape(o, '', transform=True).isNull()
        except Exception:
            return False
    if o.isDerivedFrom('Mesh::Feature'): return o.Mesh.CountFacets > 0
    if o.isDerivedFrom('Points::Feature'): return o.Points.CountPoints > 0
    if o.isDerivedFrom('Fem::FemMeshObject'): return o.FemMesh.NodeCount > 0
    if o.isDerivedFrom('Path::Feature'): return len(o.Path.Commands) > 0
    if o.TypeId == 'Robot::RobotObject': return True
    if o.isDerivedFrom('Fem::FemPostObject') and not o.isDerivedFrom('Fem::FemPostFunction'):
        return True                                    # (an empty data set tessellates to nothing)
    if o.isDerivedFrom('Fem::FemResultObject'):
        return getattr(o, 'Mesh', None) is not None and o.Mesh.FemMesh.NodeCount > 0 and bool(o.NodeNumbers)
    if o.TypeId == 'Inspection::Feature': return getattr(o, 'Actual', None) is not None and len(o.Distances) > 0
    if o.TypeId.startswith('Robot::') and hasattr(o, 'Trajectory'): return len(o.Trajectory.Waypoints) > 0
    return hasattr(o, 'Shape') and not o.Shape.isNull()

_parents = [None]    # during objects(): object name -> the group holding it (a large assembly: one pass, not one per object)
@op
def objects():
    par = {}
    for p in _doc().Objects:
        if p.TypeId != 'App::Link' and p.hasExtension('App::GroupExtension'):
            for c in p.Group: par.setdefault(c.Name, p)
            og = getattr(p, 'Origin', None)
            if og is not None: par.setdefault(og.Name, p)
    _parents[0] = par
    try: return [_obj_info(o) for o in _doc().Objects]
    finally: _parents[0] = None

# ------------------------------------------------------------------ properties (drive the feature dialog)
def _prop_value(o, p):
    v = getattr(o, p)
    if hasattr(v, 'UserString'): return v.UserString            # Quantity -> "10.00 mm"
    if isinstance(v, App.DocumentObject): return v.Name
    if isinstance(v, (list, tuple)) and not v: return []
    if isinstance(v, (list, tuple)) and v and all(isinstance(x, App.DocumentObject) for x in v):   # LinkList
        return [[x.Name, []] for x in v]
    if isinstance(v, (list, tuple)) and v and isinstance(v[0], tuple) and isinstance(v[0][0], App.DocumentObject):   # LinkSubList
        return [[x[0].Name, list(x[1])] for x in v]
    if isinstance(v, tuple) and len(v) == 2 and isinstance(v[0], App.DocumentObject):
        return [[v[0].Name, list(v[1])]]
    if isinstance(v, (bool, int, float, str)): return v
    return str(v)

# shown on desktop only as data the user never types (the shape, Python proxy, material cards, FEM meshes / results,
# sketch geometry edited in the sketcher, hidden links): left out of the feature dialog
_INTERNAL_PROPS = {'Part::PropertyPartShape', 'App::PropertyPythonObject', 'Materials::PropertyMaterial', 'App::PropertyMaterial',
                   'App::PropertyMaterialList', 'Fem::PropertyFemMesh', 'Fem::PropertyPostDataObject', 'Sketcher::PropertyConstraintList',
                   'Part::PropertyGeometryList', 'App::PropertyUUID', 'App::PropertyLinkHidden', 'App::PropertyLinkListHidden',
                   'App::PropertyLinkSubHidden', 'App::PropertyExpressionEngine', 'App::PropertyColorList', 'Mesh::PropertyMeshKernel',
                   'Points::PropertyPointKernel', 'Points::PropertyNormalList', 'Points::PropertyCurvatureList', 'Points::PropertyGreyValueList',
                   'App::PropertyPersistentObject', 'Spreadsheet::PropertySheet', 'Spreadsheet::PropertyColumnWidths', 'Spreadsheet::PropertyRowHeights',
                   'App::PropertyBoolList', 'Path::PropertyPath', 'Path::PropertyTooltable'}
_VECTOR_PROPS = {'App::PropertyVector', 'App::PropertyVectorDistance', 'App::PropertyPosition', 'App::PropertyDirection'}
_TEXT_PROPS = {'App::PropertyString', 'App::PropertyFont', 'App::PropertyPath', 'App::PropertyFile', 'App::PropertyFileIncluded'}
_LIST_PROPS = {'App::PropertyStringList', 'App::PropertyFloatList', 'App::PropertyIntegerList', 'App::PropertyIntegerSet',
               'App::PropertyVectorList', 'App::PropertyPlacementList', 'App::PropertyMap'}

def _fmt(x):
    """Full precision (lists are written back from this text), without a needless '.0'."""
    return (repr(x)[:-2] if repr(x).endswith('.0') else repr(x)) if isinstance(x, float) else str(x)

def _edit_form(o, p, t, d, exprs):
    """How the dialog edits a property that is not a plain number / switch / choice / link (desktop: the property
    editor's vector, placement, color, text and list editors; Part fillet / chamfer edges: DlgFilletEdges).
    'kind' picks the editor; 'parts' are the sub-values (path, label, unit type, value, expression) a numpad edits."""
    v = getattr(o, p)
    L = lambda x: App.Units.Quantity(x, App.Units.Length).UserString
    A = lambda x: App.Units.Quantity(x, App.Units.Angle).UserString
    if t in _VECTOR_PROPS:
        unit = t != 'App::PropertyVector' and t != 'App::PropertyDirection'
        d['kind'] = 'parts'
        d['parts'] = [{'path': '%s.%s' % (p, c), 'label': c.upper(), 'type': 'App::PropertyDistance' if unit else 'App::PropertyFloat',
                       'value': L(getattr(v, c)) if unit else _fmt(getattr(v, c)), 'expression': exprs.get('%s.%s' % (p, c)) or exprs.get('.%s.%s' % (p, c))}
                      for c in 'xyz']
    elif t in ('App::PropertyPlacement', 'App::PropertyRotation'):
        r = v.Rotation if t == 'App::PropertyPlacement' else v
        ypr = r.getYawPitchRoll(); parts = []
        if t == 'App::PropertyPlacement':
            parts += [{'path': '%s.Base.%s' % (p, c), 'label': c.upper(), 'type': 'App::PropertyDistance', 'value': L(getattr(v.Base, c))} for c in 'xyz']
        rp = p + '.Rotation' if t == 'App::PropertyPlacement' else p
        parts += [{'path': '%s.%s' % (rp, n), 'label': n, 'type': 'App::PropertyAngle', 'value': A(a)} for n, a in zip(('Yaw', 'Pitch', 'Roll'), ypr)]
        for x in parts: x['expression'] = exprs.get(x['path']) or exprs.get('.' + x['path'])
        d['kind'] = 'parts'; d['parts'] = parts
    elif t == 'App::PropertyColor':
        d['kind'] = 'color'; d['value'] = '#%02x%02x%02x' % tuple(int(round(c * 255)) for c in v[:3])
    elif t in _TEXT_PROPS:
        d['kind'] = 'file' if t in ('App::PropertyFile', 'App::PropertyFileIncluded') else 'text'; d['value'] = str(v or '')
    elif t in _LIST_PROPS:
        d['kind'] = 'list'
        if t == 'App::PropertyMap': lines = ['%s = %s' % (k, str(x).replace('\\', '\\\\').replace('\n', '\\n')) for k, x in v.items()]
        elif t == 'App::PropertyVectorList': lines = [', '.join(_fmt(c) for c in (x.x, x.y, x.z)) for x in v]
        elif t == 'App::PropertyPlacementList':
            lines = [', '.join(_fmt(c) for c in (x.Base.x, x.Base.y, x.Base.z) + x.Rotation.getYawPitchRoll()) for x in v]
        else: lines = [_fmt(x) for x in v]
        d['text'] = '\n'.join(lines); d['count'] = len(lines)
        d['hint'] = _tr({'App::PropertyMap': 'one "key = value" per line', 'App::PropertyVectorList': 'one "x, y, z" per line',
                     'App::PropertyPlacementList': 'one "x, y, z, yaw, pitch, roll" per line'}.get(t, 'one item per line'))
    elif t == 'Part::PropertyFilletEdges':
        d['kind'] = 'edges'
        d['value'] = [['Edge%d' % e, L(r1), L(r2)] for e, r1, r2 in (v or [])]
        base = getattr(o, 'Base', None); d['base'] = base.Name if base is not None else None
        d['nedges'] = len(base.Shape.Edges) if base is not None and hasattr(base, 'Shape') else 0
        d['chamfer'] = o.isDerivedFrom('Part::Chamfer')

def _parse_text(o, p, t, text):
    """The dialog's text editors back to the property's own type."""
    text = text if isinstance(text, str) else str(text)
    lines = [x.strip() for x in text.splitlines() if x.strip()]
    nums = lambda x: [float(App.Units.Quantity(c.strip()).Value) for c in x.replace(';', ',').split(',') if c.strip()]
    if t == 'App::PropertyColor':
        h = text.strip().lstrip('#')
        if len(h) == 6: return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))
        return tuple(nums(text))
    if t in _TEXT_PROPS: return text
    if t == 'App::PropertyStringList':        # empty entries are kept (they are positional, e.g. per-edge lists)
        return text.split('\n') if text else []
    if t == 'App::PropertyFloatList': return [nums(x)[0] for x in lines]
    if t in ('App::PropertyIntegerList', 'App::PropertyIntegerSet'): return [int(float(x)) for x in lines]
    if t == 'App::PropertyVectorList': return [App.Vector(*nums(x)[:3]) for x in lines]
    if t == 'App::PropertyPlacementList':
        out = []
        for x in lines:
            n = nums(x) + [0.0] * 6
            r = App.Rotation(); r.setYawPitchRoll(n[3], n[4], n[5]); out.append(App.Placement(App.Vector(*n[:3]), r))
        return out
    if t == 'App::PropertyMap':
        unesc = lambda x: x.replace('\\n', '\n').replace('\\\\', '\\')
        return dict((k.strip(), unesc(val.strip())) for k, _, val in (x.partition('=') for x in lines))
    return text

def _set_part(o, path, value):
    """One component of a vector / placement (Placement.Base.x, Placement.Rotation.Yaw, Direction.z ...)."""
    p, *rest = path.split('.')
    q = App.Units.Quantity(value).Value if isinstance(value, str) else float(value)
    v = getattr(o, p)
    if isinstance(v, App.Placement):
        if rest[0] == 'Base': b = v.Base; setattr(b, rest[1], q); v.Base = b
        else:
            ypr = list(v.Rotation.getYawPitchRoll()); ypr[('Yaw', 'Pitch', 'Roll').index(rest[1])] = q
            r = App.Rotation(); r.setYawPitchRoll(*ypr); v.Rotation = r
    elif isinstance(v, App.Rotation):
        ypr = list(v.getYawPitchRoll()); ypr[('Yaw', 'Pitch', 'Roll').index(rest[0])] = q
        v = App.Rotation(); v.setYawPitchRoll(*ypr)
    else: setattr(v, rest[0], q)
    setattr(o, p, v)

@op
def properties(name):
    o = _doc().getObject(name); out = []
    exprs = dict(o.ExpressionEngine)
    for p in o.PropertiesList:
        st = list(o.getPropertyStatus(p))
        try: st += list(o.getTypeOfProperty(p))          # static flags (Prop_ReadOnly | Prop_Output | Prop_Hidden)
        except Exception: pass
        if 'Hidden' in st: continue
        t = o.getTypeIdOfProperty(p)
        if t in _INTERNAL_PROPS: continue
        d = {'name': p, 'type': t, 'group': o.getGroupOfProperty(p), 'doc': o.getDocumentationOfProperty(p),
             'readonly': 'ReadOnly' in st, 'expression': exprs.get(p)}
        try: d['value'] = _prop_value(o, p)
        except Exception as e: d['value'] = None
        try: _edit_form(o, p, t, d, exprs)
        except Exception: pass
        if t == 'App::PropertyEnumeration': d['enum'] = o.getEnumerationsOfProperty(p)
        out.append(d)
    if hasattr(o, 'JointType') and hasattr(o, 'Offset2'):
        # TaskAssemblyCreateJoint's Offset / Rotation / Reverse controls act on Offset2 and flipOnePart()
        off = o.Offset2
        out.append({'name': '@Offset', 'type': 'App::PropertyDistance', 'group': 'Joint', 'doc': 'Offset along the joint axis', 'readonly': False,
                    'expression': None, 'value': App.Units.Quantity(off.Base.z, App.Units.Length).UserString})
        out.append({'name': '@Rotation', 'type': 'App::PropertyAngle', 'group': 'Joint', 'doc': 'Rotation about the joint axis', 'readonly': False,
                    'expression': None, 'value': App.Units.Quantity(off.Rotation.getYawPitchRoll()[0], App.Units.Angle).UserString})
        out.append({'name': '@Reverse', 'type': 'App::PropertyBool', 'group': 'Joint', 'doc': 'Flip the moving part', 'readonly': False,
                    'expression': None, 'value': False})
    return out

@op
def set_property(name, prop, value=None, expression=None):
    o = _doc().getObject(name)
    if prop.startswith('@') or (prop == 'JointType' and hasattr(o, 'Proxy')):
        return _set_joint(o, prop, value)
    if expression is not None:
        o.setExpression(prop, expression or None)
    elif '.' in prop:
        _set_part(o, prop, value)
    else:
        t = o.getTypeIdOfProperty(prop)
        if t in ('App::PropertyLink', 'App::PropertyLinkGlobal'):
            value = _doc().getObject(value) if value else None
        elif t in ('App::PropertyLinkSub', 'App::PropertyLinkSubList', 'App::PropertyLinkSubGlobal', 'App::PropertyLinkSubListGlobal'):
            value = [(_doc().getObject(n), tuple(subs)) for n, subs in value] if value else None
            if t.startswith('App::PropertyLinkSub') and not t.startswith('App::PropertyLinkSubList') and value:
                # a whole object (origin axis / plane, datum) is linked with the sub-name "" as the task panels do;
                # with no sub-name at all patterns / revolutions ignore the reference and fall back to their default
                value = (value[0][0], value[0][1] or ('',))
        elif t in ('App::PropertyLinkList', 'App::PropertyLinkListGlobal', 'App::PropertyXLinkList'):
            value = [_doc().getObject(x[0] if isinstance(x, (list, tuple)) else x) for x in (value or [])]
        elif t == 'Part::PropertyFilletEdges':        # [[EdgeN, r1, r2], ...] (DlgFilletEdges)
            q = lambda x: App.Units.Quantity(x).Value if isinstance(x, str) else float(x)
            value = [(int(str(e).replace('Edge', '')), q(r1), q(r2)) for e, r1, r2 in (value or [])]
        elif t in _TEXT_PROPS or t in _LIST_PROPS or t == 'App::PropertyColor':
            value = _parse_text(o, prop, t, value)
        elif t.startswith('App::PropertyQuantity') or t in ('App::PropertyLength', 'App::PropertyDistance', 'App::PropertyAngle', 'App::PropertyFloat'):
            value = App.Units.Quantity(value) if isinstance(value, str) else value
        elif isinstance(value, str) and o.getTypeIdOfProperty(prop).startswith('App::Property') and _is_unit_prop(o, prop):
            value = App.Units.Quantity(value)
        elif isinstance(value, str) and type(getattr(o, prop)) in (int, float):
            # numbers typed on the numpad (Occurrences, counts, plain floats): to the property's own number type
            q = App.Units.Quantity(value).Value
            value = int(round(q)) if type(getattr(o, prop)) is int else q
        setattr(o, prop, value)
    return True

def _is_unit_prop(o, prop):
    v = getattr(o, prop)
    return hasattr(v, 'Unit') and hasattr(v, 'Value')

def _set_joint(j, prop, value):
    """Joint task-panel controls, applied exactly as TaskAssemblyCreateJoint does, then the assembly is re-solved."""
    import JointObject
    q = lambda v: App.Units.Quantity(v).Value if isinstance(v, str) else float(v)
    if prop == 'JointType': j.Proxy.setJointType(j, value)
    elif prop == '@Offset': j.Offset2.Base = App.Vector(0, 0, q(value))
    elif prop == '@Rotation':
        ypr = j.Offset2.Rotation.getYawPitchRoll(); r = j.Offset2.Rotation; r.setYawPitchRoll(q(value), ypr[1], ypr[2])
        p = j.Offset2; p.Rotation = r; j.Offset2 = p
    elif prop == '@Reverse': j.Proxy.flipOnePart(j)
    asm = j.Proxy.getAssembly(j)
    if asm is not None: JointObject.solveIfAllowed(asm)
    _doc().recompute()
    return True

@op
def recompute():
    d = _doc(); d.recompute()
    return [o.Name for o in d.Objects if 'Invalid' in o.State or 'Error' in o.State]

@op
def last_good(name):
    """For a failed feature: the nearest object it builds on whose shape is good (PartDesign BaseFeature chain, Part
    Base / Shapes links), shown in its place so the model doesn't vanish while the failure is fixed. None if none."""
    o = _doc().getObject(name); seen = set()
    def ok(x):
        bad = 'Invalid' in x.State or 'Error' in x.State
        try: return not bad and not _shape(x).isNull()
        except Exception: return False
    while o is not None and o.Name not in seen:
        seen.add(o.Name)
        if o.Name != name and ok(o): return o.Name
        nxt = None
        for p in ('BaseFeature', 'Base', 'Source', 'Objects', 'Shapes'):
            v = getattr(o, p, None) if p in o.PropertiesList else None
            if isinstance(v, tuple) and v and hasattr(v[0], 'TypeId'): v = v[0]
            if isinstance(v, list) and v: v = v[0]
            if isinstance(v, tuple) and v and hasattr(v[0], 'TypeId'): v = v[0]
            if hasattr(v, 'isDerivedFrom') and hasattr(v, 'Name'): nxt = v; break
        o = nxt
    return None

# ------------------------------------------------------------------ transactions = undo/redo (same as desktop)
@op
def begin(label):
    _doc().openTransaction(label); return True
@op
def commit():
    _doc().commitTransaction(); return True
@op
def abort():
    _doc().abortTransaction(); _doc().recompute(); return True
@op
def undo():
    d = _doc(); d.undo(); d.recompute(); return d.UndoNames
@op
def redo():
    d = _doc(); d.redo(); d.recompute(); return d.RedoNames
@op
def undo_state():
    d = _doc(); return {'undo': d.UndoNames, 'redo': d.RedoNames, 'pos': _history.pos.get(d.Name, 0)}

class _History:
    """Position in each document's undo history: +1 per committed transaction (only those that changed something:
    FreeCAD signals no commit for an empty one), -1 per undo, +1 per redo, whichever code path did it.  The editors
    bound undo by it ("undo stops where the sketch was opened"); the undo list's length cannot tell, because FreeCAD
    keeps at most 20 steps and then drops the oldest."""
    def __init__(self): self.pos = {}
    def _add(self, doc, n): self.pos[doc.Name] = self.pos.get(doc.Name, 0) + n
    def slotCommitTransaction(self, doc): self._add(doc, 1)
    def slotUndoDocument(self, doc): self._add(doc, -1)
    def slotRedoDocument(self, doc): self._add(doc, 1)
_history = _History()
App.addDocumentObserver(_history)

# ------------------------------------------------------------------ generic scripting (mobile command implementations)
@op
def run(script, args=None):
    """Executes a mobile command script (python) with `doc` and `args` in scope; returns `result`."""
    scope = {'App': App, 'doc': _doc(), 'args': args or {}, 'result': None}
    exec(script, scope)
    return scope.get('result')

# ------------------------------------------------------------------ geometry for the viewport
import array, struct, ctypes
def _shape(o):
    import Part
    try:
        return Part.getShape(o, '', transform=True)
    except Exception:
        return o.Shape

def _pack_shape(s, t):
    """Shape [s] (in its own coordinates) at deflection [t] as indexed arrays: vertices (x y z nx ny nz, smooth normals
    per face as FreeCAD's view providers compute them), triangle indices, faces (id, first index, index count), edge
    points (GL_LINES pairs) and edges (id, first point, point count).  NumPy does the per-vertex work (a large STEP
    assembly has millions of triangles: a Python loop per vertex took minutes)."""
    import numpy as np
    V, I, F, L, E = [], [], [], [], []; nv = ni = nl = 0
    for fi, f in enumerate(s.Faces, 1):
        try: pts, idx = f.tessellate(t)
        except Exception: continue
        if not idx: continue
        P = np.array([(p.x, p.y, p.z) for p in pts], dtype=np.float64)
        T = np.asarray(idx, dtype=np.int64).reshape(-1, 3)
        n = np.cross(P[T[:, 1]] - P[T[:, 0]], P[T[:, 2]] - P[T[:, 0]])      # area weighted
        N = np.zeros_like(P)
        for k in range(3): np.add.at(N, T[:, k], n)
        ln = np.linalg.norm(N, axis=1); ok = ln > 1e-12; N[ok] /= ln[ok, None]
        out = np.empty((len(P), 6), dtype=np.float32); out[:, 0:3] = P; out[:, 3:6] = N
        V.append(out.reshape(-1)); I.append((T + nv).astype(np.uint32).reshape(-1))
        F.append((fi, ni, T.size)); nv += len(P); ni += T.size
    for ei, e in enumerate(s.Edges, 1):
        try: pts = e.discretize(Deflection=t)
        except Exception: continue
        if len(pts) < 2: continue
        Q = np.array([(p.x, p.y, p.z) for p in pts], dtype=np.float32)
        seg = np.empty((len(Q) - 1, 2, 3), dtype=np.float32); seg[:, 0] = Q[:-1]; seg[:, 1] = Q[1:]
        L.append(seg.reshape(-1)); E.append((ei, nl, 2 * (len(Q) - 1))); nl += 2 * (len(Q) - 1)
    return _cat(V, np.float32), _cat(I, np.uint32), np.array(F, dtype=np.int32), _cat(L, np.float32), np.array(E, dtype=np.int32)

def _cat(a, dt):
    import numpy as np
    return np.concatenate(a).astype(dt, copy=False) if a else np.zeros(0, dt)

def _runs(ids, per):
    """(id, first, count) of the consecutive runs of [ids] (one per primitive of [per] vertices)."""
    import numpy as np
    ids = np.asarray(ids, dtype=np.int64)
    if not len(ids): return np.zeros((0, 3), np.int32)
    st = np.flatnonzero(np.r_[True, ids[1:] != ids[:-1]]); en = np.r_[st[1:], len(ids)]
    return np.stack([ids[st], st * per, (en - st) * per], axis=1).astype(np.int32)

def _encode(key, mat, ref, V=None, I=None, F=None, L=None, E=None, P=None, C=None, body=None):
    """tessellate's bytes, little endian: b'MCM2', flags (1 = same mesh as [key] sent before: no body), key (int32 length,
    utf-8, padded to 4), the placement (16 float32, column major: world = M * local), then the sections, each [int32
    count][items]: vertices (x y z nx ny nz), triangle indices (uint32), faces (id first count), edge points (x y z, in
    pairs), edges (id first count), vertexes (x y z id), colours (r g b per vertex / point).  Face/edge/vertex ids are
    FreeCAD's 1-based Face<i> / Edge<i> / Vertex<i>."""
    import numpy as np
    kb = key.encode(); kb += b'\0' * (-len(kb) % 4)
    out = [b'MCM2', struct.pack('<ii', 1 if ref else 0, len(key.encode())), kb, np.asarray(mat, dtype=np.float32).tobytes()]
    if body is not None:      # the native mesher's sections (all but the colours)
        out += [body, struct.pack('<i', 0)]
    elif not ref:
        for a, dt in ((V, np.float32), (I, np.uint32), (F, np.int32), (L, np.float32), (E, np.int32), (P, np.float32), (C, np.float32)):
            a = np.zeros(0, dt) if a is None else np.asarray(a, dtype=dt).reshape(-1)
            out += [struct.pack('<i', a.size), a.tobytes()]
    return b''.join(out)

_IDENT = (1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)
# Shapes already sent to the app, by key (their shared geometry, TShape, with the location stripped): another object
# with the same geometry (an assembly's repeated screws) is sent as that key and its own placement.  Holding the shape
# keeps its TShape alive, so its address (in the key) is not reused by a different shape.  The app tells which keys it
# keeps (tess_keep) after each refresh.
_tess_sent = {}          # key -> a shape with that geometry (its TShape)
_tess_bucket = {}        # shape type, orientation, face / edge counts, tolerance -> keys
_tess_count = [0]

def _tess_key(s, tol):
    """(key, sent before) for shape [s]: shapes sharing their geometry (TopoDS TShape: isPartner) share the key.  The
    native mesher gives the TShape itself; else OCC's hash code includes the location, so shapes are bucketed by their
    face count and compared with isPartner (not countElement: it counts FreeCAD's element names, which differ between
    copies)."""
    nat = _native()
    if nat is not None:
        o = ctypes.c_int(0); t = nat.mc_tshape(id(s), ctypes.byref(o))
        if t:
            k = 'T%x:%d:%g' % (t, o.value, tol)
            return k, k in _tess_sent
    b = '%s:%s:%d:%g' % (s.ShapeType, s.Orientation, len(s.Faces), tol)
    for k in _tess_bucket.get(b, ()):
        if _tess_sent[k].isPartner(s): return k, True
    _tess_count[0] += 1
    return '%s#%d' % (b, _tess_count[0]), False

def _tess_forget(k):
    _tess_sent.pop(k, None)
    b = k.rsplit('#', 1)[0]; ks = _tess_bucket.get(b)
    if ks and k in ks:
        ks.remove(k)
        if not ks: del _tess_bucket[b]

# The native mesher (app/app/src/main/cpp/mcmesh.cpp, libmcmesh.so in the app's native libraries): meshes shapes in
# parallel and packs the mesh without a Python object per node, as desktop's view provider does.  None where it is
# missing or does not see the shapes FreeCAD sees (then the Python path below is used).
_nat = [False]
_TOPABS = ('Compound', 'CompSolid', 'Solid', 'Shell', 'Face', 'Wire', 'Edge', 'Vertex')
def _native():
    if _nat[0] is not False: return _nat[0]
    _nat[0] = None
    try:
        import os, Part
        path = os.path.join(os.environ.get('FC_NATIVE_LIB_DIR', ''), 'libmcmesh.so')
        if not os.path.exists(path): return None
        lib = ctypes.CDLL(path)
        lib.mc_check.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_int)]; lib.mc_check.restype = ctypes.c_int
        lib.mc_tshape.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_int)]; lib.mc_tshape.restype = ctypes.c_size_t
        lib.mc_mesh.argtypes = [ctypes.POINTER(ctypes.c_void_p), ctypes.c_int, ctypes.c_double, ctypes.c_int]; lib.mc_mesh.restype = None
        lib.mc_pack.argtypes = [ctypes.c_void_p, ctypes.c_double, ctypes.POINTER(ctypes.c_int64)]; lib.mc_pack.restype = ctypes.c_void_p
        lib.mc_free.argtypes = [ctypes.c_void_p]; lib.mc_free.restype = None
        # the layout it assumes (FreeCAD's Python shape -> TopoShape -> TopoDS_Shape) must show these shapes as FreeCAD does
        for t in (Part.makeBox(1, 2, 3), Part.makeCylinder(1, 2).Faces[0], Part.Compound([Part.makeBox(1, 1, 1), Part.makeSphere(1)])):
            n = ctypes.c_int(-1)
            if lib.mc_check(id(t), ctypes.byref(n)) != _TOPABS.index(t.ShapeType) or n.value != len(t.Faces): return None
        _nat[0] = lib
    except Exception:
        _nat[0] = None
    return _nat[0]

_prep = {}      # name -> its shape, from the last tess_prepare
@op
def tess_prepare(names, tol=0.05):
    """Meshes the shapes of objects [names] not sent yet, natively and in parallel (the app then asks for each
    object's mesh: tessellate)."""
    _prep.clear()
    nat = _native()
    if nat is None: return 0
    import os
    todo = {}
    for n in names:
        o = _doc().getObject(n)
        if o is None or not _plain_shape(o): continue
        try: s = _shape(o)
        except Exception: continue
        if s.isNull(): continue
        _prep[n] = s
        k, sent = _tess_key(s, tol)
        if not sent and k not in todo: todo[k] = s
    if todo:
        arr = (ctypes.c_void_p * len(todo))(*[id(s) for s in todo.values()])
        nat.mc_mesh(arr, len(todo), tol, max(1, os.cpu_count() or 1))
    return len(todo)

def _plain_shape(o):
    """Objects tessellate draws from their Part shape (not meshes, FEM, paths ...)."""
    t = o.TypeId
    return not (t == 'Inspection::Feature' or t.startswith('Robot::') or o.isDerivedFrom('Fem::FemResultObject') or o.isDerivedFrom('Fem::FemPostObject')
                or o.isDerivedFrom('Mesh::Feature') or o.isDerivedFrom('Points::Feature') or o.isDerivedFrom('Fem::FemMeshObject') or o.isDerivedFrom('Path::Feature'))

def _group_matrix(o):
    """Placement of the groups holding [o] (App::Part, Body ...: GeoFeatureGroupExtension), which desktop applies to
    their children in the 3D view: the object's shape is in its group's coordinates."""
    try:
        g = o.getParentGeoFeatureGroup()
        if g is not None:
            pl = g.getGlobalPlacement()
            if not pl.isIdentity(): return pl.toMatrix()
    except Exception: pass
    return None

@op
def tess_keep(keys):
    keep = set(keys)
    for k in [k for k in _tess_sent if k not in keep]: _tess_forget(k)

@op
def tess_drop(keys):
    for k in keys: _tess_forget(k)

def tessellate(name, tol=0.05):
    """The object's display mesh as bytes (layout: _encode)."""
    o = _doc().getObject(name)
    tris = array.array('f'); edges = array.array('f'); verts = array.array('f'); cols = array.array('f')
    s = None
    if o.TypeId == 'Inspection::Feature':               # deviation of the actual geometry, coloured per sample point
        from .inspection import samples, color
        P, F, D, r = samples(o)
        if F:
            for fi, (a, b, c) in enumerate(F, 1):
                p0, p1, p2 = P[a], P[b], P[c]
                n = (p1 - p0).cross(p2 - p0)
                if n.Length > 1e-12: n.normalize()
                for k in (a, b, c):
                    q = P[k]; tris.extend((q.x, q.y, q.z, n.x, n.y, n.z, fi)); cols.extend(color(D[k] if k < len(D) else 1e30, r))
        else:
            for vi, q in enumerate(P, 1): verts.extend((q.x, q.y, q.z, vi)); cols.extend(color(D[vi - 1] if vi - 1 < len(D) else 1e30, r))
    elif o.isDerivedFrom('Fem::FemResultObject'):      # FEM result: mesh surface coloured by the shown field
        from .fem import result_surface, rainbow, _display
        field, scale = _display.get(o.Name, ('vonMises', 0.0))
        P, F, Vv, (lo, hi) = result_surface(o, field, scale)
        span = (hi - lo) or 1.0
        for fi, (a, b2, c) in enumerate(F, 1):
            p0, p1, p2 = P[a], P[b2], P[c]
            n = (p1 - p0).cross(p2 - p0)
            if n.Length > 1e-12: n.normalize()
            for k in (a, b2, c):
                q = P[k]; tris.extend((q.x, q.y, q.z, n.x, n.y, n.z, fi)); cols.extend(rainbow((Vv[k] - lo) / span))
    elif o.isDerivedFrom('Fem::FemPostObject'):        # FEM pipeline / filter: its VTK data's surface, coloured
        from .fem import post_surface, rainbow, _display
        try: P, F, Vv, _, _ = post_surface(o, _display.get(o.Name, (None, 0))[0])
        except Exception: P, F, Vv = [], [], None
        lo, hi = (float(Vv.min()), float(Vv.max())) if Vv is not None and len(Vv) else (0.0, 1.0)
        span = (hi - lo) or 1.0
        for fi, (a, b2, c) in enumerate(F, 1):
            p0, p1, p2 = App.Vector(*P[a]), App.Vector(*P[b2]), App.Vector(*P[c])
            n = (p1 - p0).cross(p2 - p0)
            if n.Length > 1e-12: n.normalize()
            for k, q in ((a, p0), (b2, p1), (c, p2)):
                tris.extend((q.x, q.y, q.z, n.x, n.y, n.z, fi)); cols.extend(rainbow((Vv[k] - lo) / span) if Vv is not None else (0.7, 0.7, 0.7))
    elif o.isDerivedFrom('Mesh::Feature'):                 # Mesh: facets (Facet<i> = face id i+1)
        m = o.Mesh.copy(); m.transform(o.Placement.toMatrix()) if hasattr(o, 'Placement') else None
        for fi, f in enumerate(m.Facets, 1):
            n = f.Normal
            for p in f.Points: tris.extend((p[0], p[1], p[2], n.x, n.y, n.z, fi))
    elif o.isDerivedFrom('Points::Feature'):           # point cloud
        pl = o.Placement
        for vi, p in enumerate(o.Points.Points, 1):
            q = pl.multVec(p); verts.extend((q.x, q.y, q.z, vi))
    elif o.isDerivedFrom('Fem::FemMeshObject'):        # FEM mesh: its outer surface
        try:
            from .fem import mesh_triangles, mesh_edges
            m = mesh_triangles(o.FemMesh)
            import Mesh
            mm = Mesh.Mesh(m)
            for fi, f in enumerate(mm.Facets, 1):
                n = f.Normal
                for p in f.Points: tris.extend((p[0], p[1], p[2], n.x, n.y, n.z, fi))
            for ei, (a, b) in enumerate(mesh_edges(o.FemMesh), 1): edges.extend((a.x, a.y, a.z, b.x, b.y, b.z, ei))
        except Exception:
            for vi, p in enumerate(o.FemMesh.Nodes.values(), 1): verts.extend((p.x, p.y, p.z, vi))
    elif o.TypeId == 'Robot::RobotObject':             # robot: its kinematic chain (stick figure), joints as vertexes
        from .robot import joint_points
        P = joint_points(o)
        for i, (a, b) in enumerate(zip(P, P[1:]), 1): edges.extend((a.x, a.y, a.z, b.x, b.y, b.z, i))
        for i, p in enumerate(P, 1): verts.extend((p.x, p.y, p.z, i))
    elif o.TypeId.startswith('Robot::') and hasattr(o, 'Trajectory'):   # trajectory: waypoint polyline
        pl = o.Placement if hasattr(o, 'Placement') else App.Placement()
        P = [pl.multVec(w.Pos.Base) for w in o.Trajectory.Waypoints]
        for i, (a, b) in enumerate(zip(P, P[1:]), 1): edges.extend((a.x, a.y, a.z, b.x, b.y, b.z, i))
        for i, p in enumerate(P, 1): verts.extend((p.x, p.y, p.z, i))
    elif o.isDerivedFrom('Path::Feature'):             # CAM toolpath: G0/G1 lines, G2/G3 arcs as chords
        import math
        pos = App.Vector(); pl = o.Placement if hasattr(o, 'Placement') else App.Placement()
        for ci, c in enumerate(o.Path.Commands, 1):
            prm = c.Parameters; nm = c.Name.upper()
            if nm not in ('G0', 'G00', 'G1', 'G01', 'G2', 'G02', 'G3', 'G03'): continue
            end = App.Vector(prm.get('X', pos.x), prm.get('Y', pos.y), prm.get('Z', pos.z))
            pts = [pos, end]
            if nm in ('G2', 'G02', 'G3', 'G03') and ('I' in prm or 'J' in prm):
                cen = App.Vector(pos.x + prm.get('I', 0), pos.y + prm.get('J', 0), pos.z)
                a0 = math.atan2(pos.y - cen.y, pos.x - cen.x); a1 = math.atan2(end.y - cen.y, end.x - cen.x); r = (pos - cen).Length
                cw = nm in ('G2', 'G02')
                if cw and a1 >= a0: a1 -= 2 * math.pi
                if not cw and a1 <= a0: a1 += 2 * math.pi
                k = max(2, int(abs(a1 - a0) / 0.2))
                pts = [App.Vector(cen.x + r * math.cos(a0 + (a1 - a0) * t / k), cen.y + r * math.sin(a0 + (a1 - a0) * t / k), pos.z + (end.z - pos.z) * t / k) for t in range(k + 1)]
            for a, b in zip(pts, pts[1:]):
                a2, b2 = pl.multVec(a), pl.multVec(b); edges.extend((a2.x, a2.y, a2.z, b2.x, b2.y, b2.z, ci))
            pos = end
    else:
        s = _prep.pop(o.Name, None)
        if s is None: s = _shape(o)
    G = _group_matrix(o)
    if s is not None and not s.isNull():
        import Part, numpy as np
        # the shape's own geometry (its location stripped) is triangulated once and shared by every object showing it
        M = s.Matrix if G is None else G.multiply(s.Matrix)
        m = M.A; mat = [m[r * 4 + c] for c in range(4) for r in range(4)]
        key, sent = _tess_key(s, tol)
        if sent: return _encode(key, mat, True)
        nat = _native()
        if nat is not None:
            n = ctypes.c_int64(0); ptr = nat.mc_pack(id(s), tol, ctypes.byref(n))
            if ptr:
                try: body = ctypes.string_at(ptr, n.value)
                finally: nat.mc_free(ptr)
                _tess_sent[key] = s
                return _encode(key, mat, False, body=body)
        ls = Part.Shape(s); ls.Placement = App.Placement()
        # desktop's deflection (Part::Tools::getDeflection with the default MeshDeviation 0.2), not finer than [tol]
        bb = ls.BoundBox; t = max(tol, (bb.XLength + bb.YLength + bb.ZLength) / 300.0 * 0.2) if bb.isValid() else tol
        V, I, F, L, E = _pack_shape(ls, t)
        P = np.array([(v.X, v.Y, v.Z, vi) for vi, v in enumerate(ls.Vertexes, 1)], dtype=np.float32)
        _tess_sent[key] = s; _tess_bucket.setdefault(key.rsplit('#', 1)[0], []).append(key)
        return _encode(key, mat, False, V, I, F, L, E, P)
    # other objects (meshes, results, paths ...): world coordinates, one vertex per triangle corner
    import numpy as np
    T = np.frombuffer(tris.tobytes(), dtype=np.float32).reshape(-1, 7); S = np.frombuffer(edges.tobytes(), dtype=np.float32).reshape(-1, 7)
    gm = _IDENT if G is None else [G.A[r * 4 + c] for c in range(4) for r in range(4)]
    return _encode('', gm, False, T[:, 0:6], np.arange(len(T), dtype=np.uint32), _runs(T[:, 6][::3], 3),
                   S[:, 0:6], _runs(S[:, 6], 2), np.frombuffer(verts.tobytes(), dtype=np.float32), np.frombuffer(cols.tobytes(), dtype=np.float32))


# external programs shipped as native libraries (Gmsh ...): they load the FreeCAD runtime libraries (OCCT ...)
def _setup_programs():
    import os
    nat = os.environ.get('FC_NATIVE_LIB_DIR', '')
    libs = [os.path.join(App.getHomePath(), 'lib'), nat]
    os.environ['LD_LIBRARY_PATH'] = ':'.join([p for p in libs if p] + [os.environ.get('LD_LIBRARY_PATH', '')]).strip(':')
    for exe, grp, key in (('libgmsh.so', 'Gmsh', 'gmshBinaryPath'), ('libccx.so', 'Ccx', 'ccxBinaryPath')):
        path = os.path.join(nat, exe)
        if nat and os.path.exists(path):
            p = App.ParamGet('User parameter:BaseApp/Preferences/Mod/Fem/' + grp)
            if not p.GetString(key, '') or not os.path.exists(p.GetString(key, '')): p.SetString(key, path)
try:
    _setup_programs()
except Exception:
    pass

# register Sketcher / PartDesign ops
from . import sketch, partdesign, assembly, part, std, draft, techdraw, guicmd, mesh, spreadsheet, fem, robot, taskpanel, cam, inspection  # noqa: E402,F401
