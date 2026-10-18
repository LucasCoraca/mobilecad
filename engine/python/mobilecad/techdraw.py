"""TechDraw for the mobile Drawing tab.  Pages, views, dimensions and annotations are TechDraw App objects created as
the desktop commands create them (src/Mod/TechDraw/Gui/Command*.cpp, TechDrawTools/*.py); `td_render` returns what
the desktop QGraphicsScene draws (template SVG with its editable fields filled in, view edges/vertices, dimensions,
balloons, leaders, annotations, symbols) in page millimetres, Y up, for the app's page canvas."""
import math
import xml.etree.ElementTree as ET
import FreeCAD as App
from FreeCAD import Vector as V
from .bridge import op, _doc
from .partdesign import _sel

SVG_NS = 'http://www.w3.org/2000/svg'
FC_NS = 'https://www.freecad.org/wiki/index.php?title=Svg_Namespace'

def _o(name): return _doc().getObject(name)

def _f(q):
    """Quantity (X, Y, Radius ...) -> float mm."""
    return q.Value if hasattr(q, 'Value') else float(q)

def _page_of(o):
    for p in getattr(o, 'InList', []):
        if p.isDerivedFrom('TechDraw::DrawPage'): return p
        if p.isDerivedFrom('TechDraw::DrawProjGroup'): return _page_of(p)
    return None

def _default_template():
    p = App.ParamGet('User parameter:BaseApp/Preferences/Mod/TechDraw/Files').GetString('TemplateFile', '')
    return p or App.getResourceDir() + 'Mod/TechDraw/Templates/Default_Template_A4_Landscape.svg'

# ------------------------------------------------------------------ template (DrawSVGTemplate::getEditableTextsFromTemplate / processTemplate)
def _editables(path):
    out = {}
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return out
    for t in root.iter('{%s}text' % SVG_NS):
        key = t.get('{%s}editable' % FC_NS)
        if not key: continue
        ts = t.find('{%s}tspan' % SVG_NS)
        out[key] = (ts.text if ts is not None else t.text) or ''
    return out

def _autofill(tpl, key):
    d = _doc(); import time
    return {'author': d.CreatedBy, 'date': time.strftime('%Y-%m-%d'), 'organization': d.Company, 'owner': d.Company,
            'title': d.Label, 'company': d.Company, 'license': d.License, 'scale': '', 'sheet': ''}.get(key, '')

def _template_svg(tpl):
    """processTemplate(): the template file with the EditableTexts values substituted."""
    path = tpl.PageResult or tpl.Template
    ET.register_namespace('', SVG_NS); ET.register_namespace('freecad', FC_NS)
    ET.register_namespace('xlink', 'http://www.w3.org/1999/xlink')
    tree = ET.parse(path); root = tree.getroot()
    vals = dict(tpl.EditableTexts)
    for t in root.iter('{%s}text' % SVG_NS):
        key = t.get('{%s}editable' % FC_NS)
        if key and key in vals:
            ts = t.find('{%s}tspan' % SVG_NS)
            if ts is not None: ts.text = vals[key]
            else: t.text = vals[key]
    return ET.tostring(root, encoding='unicode')

@op
def td_new_page(template=None):
    """TechDraw_PageDefault / TechDraw_PageTemplate: DrawPage + DrawSVGTemplate (Template = file)."""
    d = _doc()
    page = d.addObject('TechDraw::DrawPage', 'Page')
    tpl = d.addObject('TechDraw::DrawSVGTemplate', 'Template')
    tpl.Template = template or _default_template()
    page.Template = tpl
    if not tpl.EditableTexts:                     # headless XMLQuery finds nothing: read the fields here
        ed = _editables(tpl.Template)
        tpl.EditableTexts = ed
    d.recompute()
    return page.Name

@op
def td_templates():
    import os
    base = App.getResourceDir() + 'Mod/TechDraw/Templates/'
    out = []
    for dp, _, fs in os.walk(base):
        for f in sorted(fs):
            if f.endswith('.svg'): out.append([os.path.join(dp, f), os.path.relpath(os.path.join(dp, f), base)[:-4]])
    return out

@op
def td_pages():
    return [[o.Name, o.Label] for o in _doc().Objects if o.isDerivedFrom('TechDraw::DrawPage')]

@op
def td_template_fields(page):
    t = _o(page).Template
    return dict(t.EditableTexts) if t else {}

@op
def td_set_template_field(page, key, value):
    t = _o(page).Template; e = dict(t.EditableTexts); e[key] = value; t.EditableTexts = e; return True

# ------------------------------------------------------------------ views
_DIRS = {'Front': (V(0, -1, 0), V(1, 0, 0)), 'Top': (V(0, 0, 1), V(1, 0, 0)), 'Right': (V(1, 0, 0), V(0, 1, 0)),
         'Rear': (V(0, 1, 0), V(-1, 0, 0)), 'Bottom': (V(0, 0, -1), V(1, 0, 0)), 'Left': (V(-1, 0, 0), V(0, -1, 0)),
         'Isometric': (V(1, -1, 1), V(1, 1, 0))}

def _place(page, v, x=None, y=None):
    t = page.Template
    w = t.Width.Value if t else 297; h = t.Height.Value if t else 210
    v.X = w / 2 if x is None else x; v.Y = h / 2 if y is None else y

@op
def td_view(page, sources, direction='Front', x=None, y=None):
    """TechDraw_View (CmdTechDrawView): DrawViewPart of the selected shapes; Direction / XDirection from the chosen
    standard view (desktop: from the 3D view direction)."""
    d = _doc(); pg = _o(page)
    v = d.addObject('TechDraw::DrawViewPart', 'View'); pg.addView(v)
    v.Source = [_o(n) for n in sources]
    dv, xd = _DIRS.get(direction, _DIRS['Front'])
    v.Direction = dv; v.XDirection = xd
    _place(pg, v, x, y); d.recompute(); return v.Name

@op
def td_projection_group(page, sources, views=None, x=None, y=None):
    """TechDraw_ProjectionGroup: DrawProjGroup with Anchor = Front plus the chosen projections."""
    d = _doc(); pg = _o(page)
    g = d.addObject('TechDraw::DrawProjGroup', 'ProjGroup'); pg.addView(g)
    g.Source = [_o(n) for n in sources]
    a = g.addProjection('Front'); g.Anchor = a
    a.Direction = V(0, -1, 0); a.XDirection = V(1, 0, 0)
    for p in (views or ['Top', 'Right']):
        if p != 'Front': g.addProjection(p)
    _place(pg, g, x, y); d.recompute(); return g.Name

@op
def td_section(base, edge_or_dir='Right', x=None, y=None):
    """TechDraw_SectionView: DrawViewSection on the base view; SectionDirection Right/Left/Up/Down through the view
    centre (desktop task panel defaults)."""
    d = _doc(); b = _o(base); pg = _page_of(b)
    s = d.addObject('TechDraw::DrawViewSection', 'Section'); pg.addView(s)
    s.BaseView = b; s.Source = b.Source; s.ScaleType = b.ScaleType; s.Scale = b.Scale
    s.SectionSymbol = chr(ord('A') + sum(1 for o in d.Objects if o.isDerivedFrom('TechDraw::DrawViewSection')) - 1)
    s.SectionOrigin = b.getGeometricCenter() if hasattr(b, 'getGeometricCenter') else V()
    s.SectionDirection = edge_or_dir
    bd = b.Direction; bx = b.XDirection; by = bd.cross(bx)
    nd = {'Right': bx, 'Left': bx.negative(), 'Up': by, 'Down': by.negative()}[edge_or_dir]
    s.SectionNormal = nd; s.Direction = nd; s.XDirection = bx if edge_or_dir in ('Up', 'Down') else by.negative()
    s.X = _f(b.X) + (60 if edge_or_dir in ('Right', 'Left') else 0) if x is None else x
    s.Y = _f(b.Y) + (0 if edge_or_dir in ('Right', 'Left') else -60) if y is None else y
    d.recompute(); return s.Name

@op
def td_complex_section(base, profile, x=None, y=None):
    """TechDraw_ComplexSection: DrawComplexSection with a sketch/wire as the cutting profile."""
    d = _doc(); b = _o(base); pg = _page_of(b)
    s = d.addObject('TechDraw::DrawComplexSection', 'ComplexSection'); pg.addView(s)
    s.BaseView = b; s.Source = b.Source; s.CuttingToolWireObject = _o(profile)
    s.Direction = b.Direction; s.SectionNormal = b.Direction; s.XDirection = b.XDirection; s.SectionSymbol = 'A'
    s.ProjectionStrategy = 'Offset'
    s.X = _f(b.X) + 60 if x is None else x; s.Y = _f(b.Y) if y is None else y
    d.recompute(); return s.Name

@op
def td_detail(base, anchor, radius=10.0, x=None, y=None):
    """TechDraw_DetailView: DrawViewDetail around an anchor point of the base view, Scale x2."""
    d = _doc(); b = _o(base); pg = _page_of(b)
    s = d.addObject('TechDraw::DrawViewDetail', 'Detail'); pg.addView(s)
    s.BaseView = b; s.Source = b.Source; s.Direction = b.Direction; s.XDirection = b.XDirection
    s.AnchorPoint = V(anchor[0], anchor[1], 0); s.Radius = radius; s.ScaleType = 'Custom'; s.Scale = b.Scale * 2
    s.Reference = chr(ord('A') + sum(1 for o in d.Objects if o.isDerivedFrom('TechDraw::DrawViewDetail')) - 1)
    s.X = _f(b.X) + 80 if x is None else x; s.Y = _f(b.Y) + 40 if y is None else y
    d.recompute(); return s.Name

@op
def td_broken_view(base, x=None, y=None):
    d = _doc(); b = _o(base); pg = _page_of(b)
    s = d.addObject('TechDraw::DrawBrokenView', 'BrokenView'); pg.addView(s)
    s.Source = b.Source; s.Direction = b.Direction; s.XDirection = b.XDirection
    s.X = _f(b.X) if x is None else x; s.Y = _f(b.Y) - 60 if y is None else y
    d.recompute(); return s.Name

@op
def td_simple(page, kind, props=None, x=None, y=None):
    """Annotation / RichTextAnnotation / Symbol / Image / SpreadsheetView / DraftView / ArchView / ClipGroup."""
    types = {'Annotation': 'TechDraw::DrawViewAnnotation', 'RichAnno': 'TechDraw::DrawRichAnno', 'Symbol': 'TechDraw::DrawViewSymbol',
             'Image': 'TechDraw::DrawViewImage', 'Spreadsheet': 'TechDraw::DrawViewSpreadsheet', 'Draft': 'TechDraw::DrawViewDraft',
             'Arch': 'TechDraw::DrawViewArch', 'Clip': 'TechDraw::DrawViewClip'}
    d = _doc(); pg = _o(page)
    o = d.addObject(types[kind], kind); pg.addView(o)
    for k, v in (props or {}).items():
        if k in ('Source',) and isinstance(v, str): v = _o(v)
        if k == 'Symbol' and isinstance(v, str) and v.endswith('.svg'): v = open(v, encoding='utf-8').read()
        setattr(o, k, v)
    _place(pg, o, x, y); d.recompute(); return o.Name

# ------------------------------------------------------------------ dimensions (CommandCreateDims.cpp)
@op
def td_dimension(kind, refs, x=None, y=None):
    """TechDraw_LengthDimension / Horizontal / Vertical / Radius / Diameter / Angle / 3PtAngle / Area / extents:
    DrawViewDimension with Type and References2D = [(view, 'Edge3'), ...] (sub names as TechDraw selection gives)."""
    d = _doc()
    view = _o(refs[0][0])
    if kind in ('HorizontalExtent', 'VerticalExtent'):
        import TechDraw
        subs = [s for _, ss in refs for s in ss]
        dim = TechDraw.makeExtentDim(view, subs, 0 if kind == 'HorizontalExtent' else 1)
        d.recompute(); return dim.Name
    # DimensionValidators.cpp: what each dimension type accepts ("Incorrect selection" otherwise)
    subs = [x for _, ss in refs for x in ss]
    nE = sum(1 for x in subs if x.startswith('Edge')); nV = sum(1 for x in subs if x.startswith('Vertex')); nF = sum(1 for x in subs if x.startswith('Face'))
    ok = {'Length': (nE == 1 and nV == 0) or (nV == 2 and nE == 0) or (nE == 2 and nV == 0) or (nE == 1 and nV == 1),
          'Radius': nE == 1 and nV == 0, 'Diameter': nE == 1 and nV == 0, 'ArcLength': nE == 1 and nV == 0,
          'Angle': nE == 2 and nV == 0, '3PtAngle': nV == 3 and nE == 0, 'Area': nF == 1}
    ok['Horizontal'] = ok['Vertical'] = ok['Length']
    if not ok.get(kind, True):
        need = {'Length': 'one edge, two vertexes or two edges', 'Radius': 'one circle or arc', 'Diameter': 'one circle or arc',
                'ArcLength': 'one arc', 'Angle': 'two edges', '3PtAngle': 'three vertexes', 'Area': 'one face'}
        raise RuntimeError('Incorrect selection: select %s' % need.get(kind if kind in need else 'Length'))
    if kind in ('Radius', 'Diameter', 'ArcLength'):
        try: circ = hasattr(view.getEdgeByIndex(int(subs[0][4:])).Curve, 'Radius')
        except Exception: circ = True
        if not circ: raise RuntimeError('Incorrect selection: select a circle or arc')
    dim = d.addObject('TechDraw::DrawViewDimension', 'Dimension')
    dim.Type = {'Length': 'Distance', 'Horizontal': 'DistanceX', 'Vertical': 'DistanceY', 'Radius': 'Radius', 'Diameter': 'Diameter',
                'Angle': 'Angle', '3PtAngle': 'Angle3Pt', 'Area': 'Area', 'ArcLength': 'Distance'}[kind]
    if kind == 'ArcLength': dim.Arbitrary = False
    dim.References2D = [(view, s) for n, ss in refs for s in ss]
    dim.MeasureType = 'Projected'
    pg = _page_of(view); pg.addView(dim)
    d.recompute()
    if x is not None:
        dim.X = x; dim.Y = y
    else:                                         # place the text off the geometry, away from the view centre
        try:
            if dim.Type in ('Radius', 'Diameter'):
                ap = dim.getArcPoints(); c = _flip(ap[0]); r = (ap[1] - ap[0]).Length
                dim.X = c.x + r * 1.1; dim.Y = c.y + r * 1.1
            elif dim.Type in ('Angle', 'Angle3Pt'):
                ap = dim.getAnglePoints(); vx = _flip(ap[2]); m = (_flip(ap[0]) + _flip(ap[1])) * 0.5 - vx
                if m.Length > 1e-9: m.normalize()
                dim.X = vx.x + m.x * 15; dim.Y = vx.y + m.y * 15
            else:
                p1, p2 = [_flip(p) for p in dim.getLinearPoints()]
                mid = (p1 + p2) * 0.5
                if dim.Type == 'DistanceX': n = V(0, 1 if mid.y >= 0 else -1, 0)
                elif dim.Type == 'DistanceY': n = V(1 if mid.x >= 0 else -1, 0, 0)
                else:
                    t = p2 - p1; n = V(-t.y, t.x, 0)
                    if n.Length > 1e-9: n.normalize()
                    if n.dot(mid) < 0: n = n * -1
                dim.X = mid.x + n.x * 7; dim.Y = mid.y + n.y * 7
        except Exception:
            pass
    d.recompute()
    if 'Invalid' in dim.State:
        msg = dim.getStatusString(); d.removeObject(dim.Name); raise RuntimeError('Selection not valid for this dimension: ' + msg)
    return dim.Name

@op
def td_set_dim_format(names, fmt=None, delta_decimals=0, prefix=None, remove_prefix=False):
    """TechDraw_ExtensionIncrease/DecreaseDecimal, InsertDiameter/Square/Repetition, RemovePrefixChar, CustomizeFormat."""
    import re
    for n in names:
        dm = _o(n)
        spec = fmt if fmt is not None else dm.FormatSpec
        if delta_decimals:
            m = re.search(r'%\.(\d+)', spec)
            if m: spec = spec.replace(m.group(0), '%.' + str(max(0, int(m.group(1)) + delta_decimals)))
        if remove_prefix: spec = re.sub(r'^[^%]*', '', spec)
        if prefix: spec = prefix + re.sub(r'^[⌀□]|^\d+×', '', spec)
        dm.FormatSpec = spec
    _doc().recompute(); return True

# ------------------------------------------------------------------ annotations
@op
def td_balloon(view, origin, x, y, text='1'):
    """TechDraw_Balloon: DrawViewBalloon on the view, leader from origin (view coords) to the bubble at x, y."""
    d = _doc(); v = _o(view); pg = _page_of(v)
    b = d.addObject('TechDraw::DrawViewBalloon', 'Balloon'); b.SourceView = v
    b.OriginX, b.OriginY = origin[0], origin[1]; b.X, b.Y = x, y; b.Text = str(text)
    pg.addView(b); d.recompute(); return b.Name

@op
def td_leader(view, points):
    """TechDraw_LeaderLine: DrawLeaderLine on the view; first point is X/Y, WayPoints relative to it (page mm)."""
    d = _doc(); v = _o(view); pg = _page_of(v)
    l = d.addObject('TechDraw::DrawLeaderLine', 'LeaderLine'); l.LeaderParent = v
    p0 = points[0]
    l.X = (p0[0] - _f(v.X)); l.Y = (p0[1] - _f(v.Y))
    l.WayPoints = [V(p[0] - p0[0], p[1] - p0[1], 0) for p in points]
    l.EndSymbol = 0; l.StartSymbol = 0
    pg.addView(l); d.recompute(); return l.Name

@op
def td_weld(leader, props=None):
    """TechDraw_WeldSymbol: DrawWeldSymbol with arrow-side / other-side tiles on a leader."""
    d = _doc(); l = _o(leader); pg = _page_of(l)
    w = d.addObject('TechDraw::DrawWeldSymbol', 'WeldSymbol'); w.Leader = l
    for k, v in (props or {}).items(): setattr(w, k, v)
    t1 = d.addObject('TechDraw::DrawTileWeld', 'TileWeld'); t1.TileParent = w; t1.TileRow = 0
    t2 = d.addObject('TechDraw::DrawTileWeld', 'TileWeld'); t2.TileParent = w; t2.TileRow = -1
    pg.addView(w); d.recompute(); return w.Name

# ------------------------------------------------------------------ cosmetic geometry / centre lines (CommandAnnotate.cpp)
def _vp(name, sub):
    v = _o(name)
    if sub.startswith('Vertex'): return v.getVertexBySelection(sub).Point
    if sub.startswith('Edge'): return v.getEdgeBySelection(sub)
    return None

@op
def td_cosmetic(kind, view, refs=None, pts=None, opts=None):
    """CosmeticVertex / Midpoints / Quadrants / CenterLine (face / 2 lines / 2 points) / CosmeticLine (2 points) /
    CosmeticCircle / Arc / 3-point circle / parallel / perpendicular lines / eraser / decorate / show all."""
    v = _o(view); o = opts or {}; refs = refs or []; P = [V(p[0], p[1], 0) for p in (pts or [])]
    out = None
    if kind == 'Vertex':
        for p in P: out = v.makeCosmeticVertex(p)
    elif kind in ('Midpoints', 'Quadrants'):
        for s in refs:
            e = v.getEdgeBySelection(s)
            if kind == 'Midpoints': out = v.makeCosmeticVertex(e.valueAt((e.FirstParameter + e.LastParameter) / 2))
            elif hasattr(e.Curve, 'Center'):
                c = e.Curve.Center; r = e.Curve.Radius
                for dx, dy in ((r, 0), (0, r), (-r, 0), (0, -r)): out = v.makeCosmeticVertex(V(c.x + dx, c.y + dy, 0))
    elif kind == 'FaceCenterLine':
        out = v.makeCenterLine([s for s in refs if s.startswith('Face')], int(o.get('mode', 0)))
    elif kind == '2LineCenterLine':
        out = v.makeCenterLine([s for s in refs if s.startswith('Edge')][:2], int(o.get('mode', 0)))
    elif kind == '2PointCenterLine':
        out = v.makeCenterLine([s for s in refs if s.startswith('Vertex')][:2], int(o.get('mode', 0)))
    elif kind == 'Line':
        a = P[0] if P else v.getVertexBySelection(refs[0]).Point; b = P[1] if len(P) > 1 else v.getVertexBySelection(refs[1]).Point
        out = v.makeCosmeticLine(a, b, int(o.get('style', 1)), float(o.get('weight', 0.5)))
    elif kind == 'Circle':
        c = P[0] if P else v.getVertexBySelection(refs[0]).Point; r = float(o.get('radius', 5)) if len(P) < 2 else (P[1] - c).Length
        out = v.makeCosmeticCircle(c, r)
    elif kind == 'Circle3':
        import Part
        out = v.makeCosmeticCircle(*(lambda c: (c.Center, c.Radius))(Part.Circle(P[0], P[1], P[2])))
    elif kind == 'Arc':                           # centre, start (radius), end
        c = P[0]; r = (P[1] - c).Length
        a1 = math.degrees(math.atan2(P[1].y - c.y, P[1].x - c.x)); a2 = math.degrees(math.atan2(P[2].y - c.y, P[2].x - c.x))
        out = v.makeCosmeticCircleArc(c, r, a1, a2)
    elif kind in ('Parallel', 'Perpendicular'):  # through a vertex, parallel / perpendicular to an edge (CommandExtensionPack)
        e = v.getEdgeBySelection([s for s in refs if s.startswith('Edge')][0]); p = v.getVertexBySelection([s for s in refs if s.startswith('Vertex')][0]).Point
        dvec = e.Vertexes[-1].Point - e.Vertexes[0].Point
        if kind == 'Perpendicular': dvec = V(-dvec.y, dvec.x, 0)
        dvec.normalize(); L = e.Length / 2
        out = v.makeCosmeticLine(p - dvec * L, p + dvec * L, 1, 0.5)
    elif kind == 'Eraser':
        for s in refs:
            if s.startswith('Vertex'):
                cv = v.getCosmeticVertexBySelection(s)
                if cv: v.removeCosmeticVertex(cv.Tag)
            elif s.startswith('Edge'):
                ce = v.getCosmeticEdgeBySelection(s)
                if ce: v.removeCosmeticEdge(ce.Tag); continue
                cl = v.getCenterLineBySelection(s)
                if cl: v.removeCenterLine(cl.Tag)
    elif kind == 'Decorate':                      # TechDraw_DecorateLine / ExtensionChangeLineAttributes
        for s in refs:
            v.formatGeometricEdge(int(s[4:]), int(o.get('style', 1)), float(o.get('weight', 0.5)),
                                  App.Color(*o.get('color', (0, 0, 0))) if 'color' in o else (0, 0, 0), bool(o.get('visible', True)))
    elif kind == 'ShowAll':
        v.HardHidden = not v.HardHidden; v.SmoothVisible = v.HardHidden; v.SeamVisible = v.HardHidden
    _doc().recompute()
    return getattr(out, 'Tag', out) if not isinstance(out, (int, float, str, type(None))) else out

# ------------------------------------------------------------------ hatches
@op
def td_hatch(view, faces, kind='Geom', pattern=None, name=None, scale=1.0):
    """TechDraw_GeometricHatch (DrawGeomHatch, PAT file) / TechDraw_Hatch (DrawHatch, SVG/bitmap pattern)."""
    d = _doc(); v = _o(view)
    if kind == 'Geom':
        h = d.addObject('TechDraw::DrawGeomHatch', 'GeomHatch')
        h.FilePattern = pattern or App.getResourceDir() + 'Mod/TechDraw/PAT/FCPAT.pat'; h.NamePattern = name or 'Diamond'
        h.ScalePattern = scale
    else:
        h = d.addObject('TechDraw::DrawHatch', 'Hatch')
        h.HatchPattern = pattern or App.getResourceDir() + 'Mod/TechDraw/Patterns/simple.svg'; h.HatchScale = scale
    h.Source = (v, list(faces))
    d.recompute(); return h.Name

# ------------------------------------------------------------------ page operations
@op
def td_stack(names, how):
    """TechDraw_StackTop / Bottom / Up / Down: the view's Z order (DrawView StackOrder... desktop keeps it in the GUI; here 'Z' prop)."""
    for n in names:
        o = _o(n)
        if 'StackOrder' not in o.PropertiesList:
            o.addProperty('App::PropertyInteger', 'StackOrder', 'Base', 'Drawing order on the page')
        o.StackOrder = {'Top': 1000, 'Bottom': -1000, 'Up': o.StackOrder + 1, 'Down': o.StackOrder - 1}[how]
    return True

@op
def td_export_dxf(page, path):
    import TechDraw
    TechDraw.writeDXFPage(_o(page), path); return True

@op
def td_redraw(page):
    for v in _o(page).Views: v.touch()
    _doc().recompute(); return True

# ------------------------------------------------------------------ rendering
def _poly(e, defl=0.05):
    try:
        return [[p.x, p.y] for p in e.discretize(Deflection=defl)]
    except Exception:
        return [[e.Vertexes[0].Point.x, e.Vertexes[0].Point.y], [e.Vertexes[-1].Point.x, e.Vertexes[-1].Point.y]]

def _key(e):
    """Direction-independent identity of an edge (getVisibleEdges and getEdgeByIndex return separate copies)."""
    a, b = e.Vertexes[0].Point, e.Vertexes[-1].Point
    m = e.valueAt((e.FirstParameter + e.LastParameter) / 2)
    ends = sorted([(round(a.x, 3), round(a.y, 3)), (round(b.x, 3), round(b.y, 3))])
    return tuple(ends) + ((round(m.x, 3), round(m.y, 3)), round(e.Length, 3))

def _view_geometry(v):
    """Edges (index, visible, polyline), vertexes (index, x, y), centred at the view origin, Y up, in page mm: the
    Python geometry API returns canonical (unscaled, unrotated) coordinates, so Scale and Rotation are applied here."""
    sc = _f(getattr(v, 'Scale', 1.0)); rot = math.radians(_f(getattr(v, 'Rotation', 0.0)))
    cr, sr = math.cos(rot), math.sin(rot)
    def tf(x, y): return [sc * (x * cr - y * sr), sc * (x * sr + y * cr)]
    vis = {}
    for e in v.getVisibleEdges(): vis[_key(e)] = True
    edges = []; i = 0
    while True:
        try: e = v.getEdgeByIndex(i)
        except Exception: break
        if e is None: break
        edges.append([i, 1 if _key(e) in vis else 0, [tf(*q) for q in _poly(e)]]); i += 1
    verts = []; i = 0
    while True:
        try: p = v.getVertexByIndex(i)
        except Exception: break
        if p is None: break
        verts.append([i] + tf(p.Point.x, p.Point.y)); i += 1
    # faces (DrawViewPart::extractFaces: EdgeWalker over the visible edges, outer boundary dropped) -> Face<i>
    faces = []
    try:
        import TechDraw
        for w in (TechDraw.edgeWalker(v.getVisibleEdges(), False) or []):
            faces.append([tf(q.x, q.y) for q in w.discretize(Deflection=0.05)])
    except Exception:
        pass
    return edges, verts, faces

def _flip(p):
    """TechDraw scene point (Y down) -> page orientation (Y up)."""
    return V(p.x, -p.y, 0)

def _dim_render(dm):
    """Lines (page mm, relative to the parent view origin) + text + text position, like QGIViewDimension draws."""
    v = dm.References2D[0][0] if dm.References2D else None
    t = dm.Type; txt = dm.getText() if hasattr(dm, 'getText') else ''
    tx, ty = _f(dm.X), _f(dm.Y)
    lines = []; arrows = []
    try:
        if t in ('Distance', 'DistanceX', 'DistanceY'):
            p1, p2 = [_flip(p) for p in dm.getLinearPoints()]
            if t == 'DistanceX': dvec = V(1, 0, 0)
            elif t == 'DistanceY': dvec = V(0, 1, 0)
            else: dvec = (p2 - p1); dvec.normalize()
            n = V(-dvec.y, dvec.x, 0)
            off1 = (V(tx, ty, 0) - p1).dot(n)
            a = p1 + n * off1; b = a + dvec * (p2 - p1).dot(dvec)
            lines += [[[p1.x, p1.y], [a.x, a.y]], [[p2.x, p2.y], [b.x, b.y]], [[a.x, a.y], [b.x, b.y]]]
            arrows += [[a.x, a.y, a.x - b.x, a.y - b.y], [b.x, b.y, b.x - a.x, b.y - a.y]]   # tips point out to the extension lines
        elif t in ('Radius', 'Diameter'):
            ap = dm.getArcPoints(); c = _flip(ap[0]); r = (ap[1] - ap[0]).Length   # [center, onCurve1, onCurve2, arcEnd1, arcEnd2, midArc]
            tp = V(tx, ty, 0); dvec = tp - c; dvec.normalize()
            onArc = c + dvec * r
            start = c - dvec * r if t == 'Diameter' else c
            lines.append([[start.x, start.y], [tp.x, tp.y]])
            arrows.append([onArc.x, onArc.y, onArc.x - c.x, onArc.y - c.y])
            if t == 'Diameter': arrows.append([start.x, start.y, start.x - c.x, start.y - c.y])
        elif t in ('Angle', 'Angle3Pt'):
            ap = dm.getAnglePoints(); f1, f2, vx = _flip(ap[0]), _flip(ap[1]), _flip(ap[2])   # [first, second, vertex]
            r = (V(tx, ty, 0) - vx).Length
            a1 = math.atan2(f1.y - vx.y, f1.x - vx.x); a2 = math.atan2(f2.y - vx.y, f2.x - vx.x)
            if a2 < a1: a1, a2 = a2, a1
            if a2 - a1 > math.pi: a1, a2 = a2, a1 + 2 * math.pi
            pts = [[vx.x + r * math.cos(a1 + (a2 - a1) * k / 24), vx.y + r * math.sin(a1 + (a2 - a1) * k / 24)] for k in range(25)]
            lines += [pts, [[f1.x, f1.y], [vx.x + r * math.cos(a1), vx.y + r * math.sin(a1)]]]
        elif t == 'Area':
            ap = dm.getAreaPoints(); c = _flip(ap[0] if isinstance(ap, (list, tuple)) else ap)
            lines.append([[c.x, c.y], [tx, ty]])
    except Exception:
        pass
    return {'lines': lines, 'arrows': arrows, 'text': txt, 'tx': tx, 'ty': ty}

@op
def td_render(page):
    pg = _o(page)
    t = pg.Template
    out = {'width': t.Width.Value if t else 297, 'height': t.Height.Value if t else 210, 'template': '', 'views': []}
    try:
        if t is not None and t.isDerivedFrom('TechDraw::DrawSVGTemplate'): out['template'] = _template_svg(t)
    except Exception as e:
        out['template_error'] = str(e)
    def add(o, ox=0.0, oy=0.0, parent=None):
        x, y = _f(o.X) + ox, _f(o.Y) + oy
        r = {'name': o.Name, 'label': o.Label, 'type': o.TypeId, 'x': x, 'y': y, 'scale': _f(getattr(o, 'Scale', 1.0)),
             'rot': _f(getattr(o, 'Rotation', 0.0)), 'z': getattr(o, 'StackOrder', 0)}
        tid = o.TypeId
        try:
            if tid == 'TechDraw::DrawProjGroup':
                for it in o.Views: add(it, x, y, o)
                return
            if hasattr(o, 'getVisibleEdges'):
                r['edges'], r['verts'], r['faces'] = _view_geometry(o)
                if tid == 'TechDraw::DrawViewSection': r['caption'] = 'Section %s-%s' % (o.SectionSymbol, o.SectionSymbol)
                if tid == 'TechDraw::DrawViewDetail': r['caption'] = 'Detail %s' % o.Reference
            elif tid == 'TechDraw::DrawViewDimension' or tid == 'TechDraw::DrawViewDimExtent':
                base = o.References2D[0][0] if o.References2D else None
                if base is not None:
                    bx, by = _f(base.X), _f(base.Y)
                    par = base.InList[0] if base.InList and base.InList[0].TypeId == 'TechDraw::DrawProjGroup' else None
                    if par is not None: bx += _f(par.X); by += _f(par.Y)
                    r['x'], r['y'] = bx, by
                r['dim'] = _dim_render(o)
            elif tid == 'TechDraw::DrawViewBalloon':
                base = o.SourceView; bx, by = (_f(base.X), _f(base.Y)) if base else (0, 0)
                r['x'], r['y'] = bx, by
                r['balloon'] = {'ox': _f(o.OriginX), 'oy': _f(o.OriginY), 'bx': _f(o.X), 'by': _f(o.Y), 'text': o.Text, 'shape': o.BubbleShape}
            elif tid == 'TechDraw::DrawLeaderLine':
                base = o.LeaderParent; bx, by = (_f(base.X), _f(base.Y)) if base else (0, 0)
                r['x'], r['y'] = bx + _f(o.X), by + _f(o.Y)
                r['leader'] = [[p.x, p.y] for p in o.WayPoints]
            elif tid == 'TechDraw::DrawViewAnnotation':
                r['text'] = '\n'.join(o.Text); r['size'] = o.TextSize
            elif tid == 'TechDraw::DrawRichAnno':
                import re
                r['text'] = re.sub(r'<[^>]+>', '', o.AnnoText or '').strip(); r['size'] = 5
                if o.AnnoParent: r['x'] += _f(o.AnnoParent.X); r['y'] += _f(o.AnnoParent.Y)
            elif tid in ('TechDraw::DrawViewSymbol', 'TechDraw::DrawViewSpreadsheet', 'TechDraw::DrawViewDraft', 'TechDraw::DrawViewArch'):
                r['svg'] = o.Symbol
            elif tid == 'TechDraw::DrawViewImage':
                r['image'] = o.ImageFile; r['w'] = o.Width.Value; r['h'] = o.Height.Value
            elif tid == 'TechDraw::DrawWeldSymbol':
                l = o.Leader; base = l.LeaderParent if l else None
                bx, by = (_f(base.X) + _f(l.X), _f(base.Y) + _f(l.Y)) if base else (0, 0)
                r['x'], r['y'] = bx, by
                tiles = [x for x in o.InList if x.TypeId == 'TechDraw::DrawTileWeld']
                r['text'] = ' '.join(filter(None, [t_.LeftText + ' ' + t_.CenterText + ' ' + t_.RightText for t_ in tiles])).strip() or 'weld'
            elif tid == 'TechDraw::DrawViewClip':
                r['clip'] = [o.Width.Value, o.Height.Value]
                for it in o.Views: add(it, x, y, o)
            elif tid in ('TechDraw::DrawHatch', 'TechDraw::DrawGeomHatch'):
                return
        except Exception as e:
            r['error'] = str(e)
        out['views'].append(r)
    for o in pg.Views:
        if o.TypeId in ('TechDraw::DrawProjGroupItem',) and o.InList and o.InList[0].TypeId == 'TechDraw::DrawProjGroup':
            continue
        if o.isDerivedFrom('TechDraw::DrawTileWeld'): continue
        add(o)
    # hatches: which faces of which view (the app fills the view's face outlines)
    out['hatches'] = []
    for h in _doc().Objects:
        if h.TypeId in ('TechDraw::DrawHatch', 'TechDraw::DrawGeomHatch') and h.Source:
            v, subs = h.Source if isinstance(h.Source[0], App.DocumentObject) else h.Source[0]
            if _page_of(v) is not pg: continue
            out['hatches'].append({'name': h.Name, 'view': v.Name, 'faces': [int(x[4:]) for x in subs if x.startswith('Face')],
                                   'pattern': getattr(h, 'NamePattern', '') or 'svg', 'scale': _f(getattr(h, 'ScalePattern', getattr(h, 'HatchScale', 1.0)))})
    def clean(x):
        if hasattr(x, 'Value') and hasattr(x, 'Unit'): return x.Value
        if isinstance(x, dict): return {k: clean(v) for k, v in x.items()}
        if isinstance(x, (list, tuple)): return [clean(v) for v in x]
        return x
    return clean(out)

# ------------------------------------------------------------------ TechDraw_Extension* (Gui/CommandExtensionPack.cpp, CommandExtensionDims.cpp)
_ATTR = {'cascade': 7.0, 'stretch': 2.0, 'style': 1, 'weight': 0.35}     # TaskSelectLineAttributes defaults

def _lin_dim(v, a, b, typ):
    d = _doc(); dm = d.addObject('TechDraw::DrawViewDimension', 'Dimension')
    dm.Type = typ; dm.References2D = [(v, a), (v, b)] if b else [(v, a)]; dm.MeasureType = 'Projected'
    _page_of(v).addView(dm); d.recompute(); return dm

def _sorted_vertexes(v, subs, axis):
    vs = [(s, v.getVertexBySelection(s).Point) for s in subs if s.startswith('Vertex')]
    return sorted(vs, key=lambda t: t[1][axis])

@op
def td_ext(kind, view, subs=None, names=None, opts=None):
    import TechDraw
    o = opts or {}; d = _doc(); v = _o(view) if view else None; subs = subs or []
    sc = _f(getattr(v, 'Scale', 1.0)) if v else 1.0
    if kind == 'SelectLineAttributes':
        _ATTR.update({k: o[k] for k in o if k in _ATTR}); return dict(_ATTR)
    if kind == 'CircleCenterLines':                       # execCircleCenterLines: crosses 2 mm past the circle
        for s in subs:
            e = v.getEdgeBySelection(s)
            if not hasattr(e.Curve, 'Radius'): continue
            c = e.Curve.Center; r = e.Curve.Radius + 2.0
            v.makeCosmeticLine(V(c.x + r, c.y, 0), V(c.x - r, c.y, 0), 4, _ATTR['weight'])
            v.makeCosmeticLine(V(c.x, c.y + r, 0), V(c.x, c.y - r, 0), 4, _ATTR['weight'])
    elif kind == 'HoleCircle':                            # execHoleCircle: bolt circle through 3+ hole centres
        import Part
        cs = [v.getEdgeBySelection(s).Curve for s in subs if hasattr(v.getEdgeBySelection(s).Curve, 'Radius')]
        if len(cs) < 3: raise RuntimeError('Select three or more circles')
        big = Part.Circle(cs[0].Center, cs[1].Center, cs[2].Center)
        v.makeCosmeticCircle(big.Center, big.Radius, 4, _ATTR['weight'])
        for c in cs:
            dv = c.Center - big.Center; dv.normalize(); dv = dv * (c.Radius * 1.1)
            v.makeCosmeticLine(c.Center + dv, c.Center - dv, 4, _ATTR['weight'])
    elif kind in ('ThreadHoleSide', 'ThreadBoltSide'):    # _createThreadLines, factor 1.176 / 0.85
        f = 1.176 if kind == 'ThreadHoleSide' else 0.85
        es = [v.getEdgeBySelection(s) for s in subs if s.startswith('Edge')][:2]
        if len(es) < 2: raise RuntimeError('Select two parallel edges')
        s0, e0 = es[0].Vertexes[0].Point, es[0].Vertexes[-1].Point; s1, e1 = es[1].Vertexes[0].Point, es[1].Vertexes[-1].Point
        if (e0 - s0).dot(e1 - s1) < 0: s1, e1 = e1, s1
        kd = (s1 - s0).Length; dv = (s1 - s0); dv.normalize(); dv = dv * ((kd * f - kd) / 2)
        v.makeCosmeticLine(s0 - dv, e0 - dv, 1, 0.25); v.makeCosmeticLine(s1 + dv, e1 + dv, 1, 0.25)
    elif kind in ('ThreadHoleBottom', 'ThreadBoltBottom'):  # _createThreadCircle: 3/4 arc 15°..285°
        f = 1.176 if kind == 'ThreadHoleBottom' else 0.85
        for s in subs:
            c = v.getEdgeBySelection(s).Curve
            if hasattr(c, 'Radius'): v.makeCosmeticCircleArc(c.Center, c.Radius * f, 15.0, 285.0, 1, 0.25)
    elif kind in ('ExtendLine', 'ShortenLine'):           # execExtendShortenLine: by the line stretch value
        st = _ATTR['stretch'] * (1 if kind == 'ExtendLine' else -1)
        for s in subs:
            ce = v.getCosmeticEdgeBySelection(s)
            e = v.getEdgeBySelection(s); a, b = e.Vertexes[0].Point, e.Vertexes[-1].Point
            dv = b - a; dv.normalize(); dv = dv * st
            if ce: v.removeCosmeticEdge(ce.Tag)
            cl = v.getCenterLineBySelection(s)
            if cl: cl.Extension = cl.Extension + st; v.replaceCenterLine(cl) if hasattr(v, 'replaceCenterLine') else None; continue
            v.makeCosmeticLine(a - dv, b + dv, 1, _ATTR['weight'])
    elif kind == 'VertexAtIntersection':                  # cosmetic vertexes where two edges cross
        es = [v.getEdgeBySelection(s) for s in subs if s.startswith('Edge')][:2]
        dist, pts, _ = es[0].distToShape(es[1])
        if dist > 1e-6: raise RuntimeError('The edges do not intersect')
        for p, _q in pts: v.makeCosmeticVertex(p)
    elif kind in ('CreateHorizChainDimension', 'CreateVertChainDimension', 'CreateObliqueChainDimension'):
        axis = 1 if 'Vert' in kind else 0; typ = {'Horiz': 'DistanceX', 'Vert': 'DistanceY', 'Oblique': 'Distance'}[kind[6:].split('Chain')[0]]
        vs = _sorted_vertexes(v, subs, axis)
        if len(vs) < 2: raise RuntimeError('Select two or more vertexes')
        y0 = None
        for (a, pa), (b, pb) in zip(vs, vs[1:]):
            dm = _lin_dim(v, a, b, typ); mid = (pa + pb) * (0.5 * sc)
            if axis == 0:
                dm.X = mid.x; y0 = y0 if y0 is not None else mid.y + 7.0; dm.Y = y0
            else:
                dm.Y = mid.y; y0 = y0 if y0 is not None else mid.x + 7.0; dm.X = y0
    elif kind in ('CreateHorizCoordDimension', 'CreateVertCoordDimension', 'CreateObliqueCoordDimension'):
        axis = 1 if 'Vert' in kind else 0; typ = {'Horiz': 'DistanceX', 'Vert': 'DistanceY', 'Oblique': 'Distance'}[kind[6:].split('Coord')[0]]
        vs = _sorted_vertexes(v, subs, axis)
        base = vs[0]
        for n, (b, pb) in enumerate(vs[1:]):
            dm = _lin_dim(v, base[0], b, typ); mid = (base[1] + pb) * (0.5 * sc)
            if axis == 0: dm.X = mid.x; dm.Y = base[1].y * sc + 7.0 + n * _ATTR['cascade']
            else: dm.Y = mid.y; dm.X = base[1].x * sc + 7.0 + n * _ATTR['cascade']
    elif kind in ('CascadeHorizDimension', 'CascadeVertDimension', 'CascadeObliqueDimension'):
        dims = sorted((_o(n) for n in (names or [])), key=lambda x: abs(_f(x.Y)) if 'Horiz' in kind else abs(_f(x.X)))
        if not dims: raise RuntimeError('Select dimensions')
        y0 = _f(dims[0].Y) if 'Horiz' in kind else _f(dims[0].X)
        for n, dm in enumerate(dims):
            if 'Horiz' in kind: dm.Y = y0 + n * _ATTR['cascade'] * (1 if y0 >= 0 else -1)
            else: dm.X = y0 + n * _ATTR['cascade'] * (1 if y0 >= 0 else -1)
    elif kind in ('PosHorizChainDimension', 'PosVertChainDimension', 'PosObliqueChainDimension'):
        dims = [_o(n) for n in (names or [])]
        if 'Horiz' in kind:
            y = sum(_f(x.Y) for x in dims) / len(dims)
            for dm in dims: dm.Y = y
        else:
            x = sum(_f(q.X) for q in dims) / len(dims)
            for dm in dims: dm.X = x
    elif kind in ('CreateHorizChamferDimension', 'CreateVertChamferDimension'):   # length + "×45°" style text
        vs = [(s, v.getVertexBySelection(s).Point) for s in subs if s.startswith('Vertex')][:2]
        if len(vs) < 2: raise RuntimeError('Select the two chamfer vertexes')
        dvec = vs[1][1] - vs[0][1]; ang = math.degrees(math.atan2(abs(dvec.y), abs(dvec.x)))
        typ = 'DistanceX' if 'Horiz' in kind else 'DistanceY'
        dm = _lin_dim(v, vs[0][0], vs[1][0], typ)
        if 'Vert' in kind: ang = 90 - ang
        dm.FormatSpec = '%.2f × ' + ('%.0f' % ang) + '°'
        mid = (vs[0][1] + vs[1][1]) * (0.5 * sc)
        if 'Horiz' in kind: dm.X = mid.x; dm.Y = mid.y + 7.0
        else: dm.Y = mid.y; dm.X = mid.x + 7.0
    elif kind == 'CreateLengthArc':                       # arc length dimension: '◠' + length
        e = v.getEdgeBySelection(subs[0])
        dm = _lin_dim(v, subs[0], None, 'Distance'); dm.Arbitrary = True
        dm.FormatSpec = '◠ %.2f' % (e.Length * 1.0); d.recompute()
    elif kind in ('AreaAnnotation', 'ArcLengthAnnotation'):  # balloon-like text annotation with the value
        val = 0.0; anchor = V()
        for s in subs:
            if kind == 'AreaAnnotation' and s.startswith('Face'):
                k = int(s[4:]); wires = TechDraw.edgeWalker(v.getVisibleEdges(), False)
                import Part
                f = Part.Face(wires[k]); val += f.Area; anchor = f.CenterOfMass
            elif s.startswith('Edge'):
                e = v.getEdgeBySelection(s); val += e.Length; anchor = e.valueAt((e.FirstParameter + e.LastParameter) / 2)
        txt = ('%.2f mm²' if kind == 'AreaAnnotation' else '◠ %.2f mm') % (val * (sc * sc if kind == 'AreaAnnotation' else sc))
        b = d.addObject('TechDraw::DrawViewBalloon', 'Balloon'); b.SourceView = v; b.BubbleShape = 'Rectangle'
        b.OriginX, b.OriginY = anchor.x * sc, anchor.y * sc; b.X, b.Y = anchor.x * sc + 15, anchor.y * sc + 15; b.Text = txt
        _page_of(v).addView(b)
    elif kind == 'LockUnlockView':
        for n in (names or [view]):
            x = _o(n); x.LockPosition = not x.LockPosition
    elif kind == 'PositionSectionView':                   # align a section view with its base view (horizontally / vertically)
        for n in (names or []):
            s = _o(n); b = s.BaseView
            if s.SectionDirection in ('Right', 'Left'): s.Y = _f(b.Y)
            else: s.X = _f(b.X)
    elif kind == 'ChangeLineAttributes':
        for s in subs:
            ce = v.getCosmeticEdgeBySelection(s)
            if ce:
                fm = ce.Format; ce.Format = (int(o.get('style', _ATTR['style'])), float(o.get('weight', _ATTR['weight'])), fm[2], fm[3])
            else:
                v.formatGeometricEdge(int(s[4:]), int(o.get('style', _ATTR['style'])), float(o.get('weight', _ATTR['weight'])), (0, 0, 0), True)
    elif kind == 'AddOffsetVertex':                       # TechDrawTools TaskAddOffsetVertex: vertex + (dx, dy)
        p = v.getVertexBySelection(subs[0]).Point
        v.makeCosmeticVertex(V(p.x + float(o.get('dx', 0)), p.y + float(o.get('dy', 0)), 0))
    else:
        raise RuntimeError('Unknown TechDraw extension ' + kind)
    d.recompute(); return True

@op
def td_fill_template_fields(page):
    """TechDraw_FillTemplateFields (TechDrawTools/TaskFillTemplateFields.py): author, date, title, scale, sheet from
    the document properties."""
    import time
    pg = _o(page); t = pg.Template; d = _doc()
    vals = dict(t.EditableTexts)
    pages = [o for o in d.Objects if o.isDerivedFrom('TechDraw::DrawPage')]
    fill = {'author': d.CreatedBy, 'designed_by': d.CreatedBy, 'date': time.strftime('%Y-%m-%d'), 'title': d.Label,
            'drawing_title': d.Label, 'scale': '1:%g' % (1 / pg.Scale) if pg.Scale < 1 else '%g:1' % pg.Scale,
            'sheet': '%d / %d' % (pages.index(pg) + 1, len(pages)), 'company': d.Company, 'owner': d.Company}
    for k in vals:
        lk = k.lower()
        for key, val in fill.items():
            if key in lk and val: vals[k] = val
    t.EditableTexts = vals; d.recompute(); return vals

@op
def td_move_view(view, page):
    """TechDraw_MoveView / TechDraw_ShareView (TechDrawTools): move or share a view to another page."""
    v = _o(view); src = _page_of(v); dst = _o(page)
    dst.addView(v)
    return True

@op
def td_remove_from_page(view, share_only=True):
    v = _o(view); p = _page_of(v)
    if p: p.removeView(v)
    return True

@op
def td_clip_add(view, clip, remove=False):
    v = _o(view); c = _o(clip)
    if remove: c.removeView(v); _page_of(c).addView(v)
    else: c.addView(v)
    _doc().recompute(); return True

@op
def td_project_shape(sources, direction=(0, 0, 1)):
    """TechDraw_ProjectShape (TaskProjection): visible/hidden 2D projection of shapes into Part features."""
    import TechDraw
    d = _doc(); out = []
    for n in sources:
        s = _o(n).Shape
        vis, vso, vho, hid, hso, hho = TechDraw.projectEx(s, V(*direction))[:6]
        for nm, sh in (('_visible', vis), ('_hidden', hid)):
            if sh and not sh.isNull():
                f = d.addObject('Part::Feature', n + nm); f.Shape = sh; out.append(f.Name)
    d.recompute(); return out

@op
def td_export_svg(page, path):
    """TechDraw_ExportPageSVG: an SVG of what the page shows (template + views + annotations), mm units."""
    r = td_render(page)
    W, H = r['width'], r['height']
    def P(x, y): return '%.3f,%.3f' % (x, H - y)
    out = ['<svg xmlns="http://www.w3.org/2000/svg" width="%gmm" height="%gmm" viewBox="0 0 %g %g">' % (W, H, W, H)]
    if r.get('template'):
        tpl = r['template']; i = tpl.find('>', tpl.find('<svg')) + 1; j = tpl.rfind('</svg>')
        out.append('<g>%s</g>' % tpl[i:j])
    for v in r['views']:
        bx, by = v['x'], v['y']
        for e in v.get('edges', []):
            pts = ' '.join(P(bx + q[0], by + q[1]) for q in e[2])
            out.append('<polyline fill="none" stroke="black" stroke-width="%s"%s points="%s"/>' % (
                '0.35' if e[1] else '0.18', '' if e[1] else ' stroke-dasharray="2,1"', pts))
        if 'dim' in v:
            dm = v['dim']
            for l in dm['lines']: out.append('<polyline fill="none" stroke="black" stroke-width="0.25" points="%s"/>' % ' '.join(P(bx + q[0], by + q[1]) for q in l))
            x, y = P(bx + dm['tx'], by + dm['ty']).split(',')
            out.append('<text x="%s" y="%s" font-size="3.5" text-anchor="middle">%s</text>' % (x, y, dm['text']))
        if 'text' in v and 'dim' not in v:
            x, y = P(bx, by).split(',')
            for k, line in enumerate(v['text'].split('\n')):
                out.append('<text x="%s" y="%.3f" font-size="%g">%s</text>' % (x, float(y) + k * v.get('size', 5) * 1.25, v.get('size', 5), line))
        if 'balloon' in v:
            b = v['balloon']; cx, cy = P(bx + b['bx'], by + b['by']).split(',')
            out.append('<line x1="%s" y1="%s" x2="%s" y2="%s" stroke="black" stroke-width="0.25"/>' % (tuple(P(bx + b['ox'], by + b['oy']).split(',')) + (cx, cy)))
            out.append('<circle cx="%s" cy="%s" r="4" fill="white" stroke="black" stroke-width="0.25"/><text x="%s" y="%.3f" font-size="4" text-anchor="middle">%s</text>' % (cx, cy, cx, float(cy) + 1.4, b['text']))
    out.append('</svg>')
    open(path, 'w', encoding='utf-8').write('\n'.join(out)); return True
