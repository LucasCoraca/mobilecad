"""FEM commands implemented in C++ (src/Mod/Fem/Gui/Command.cpp): constraints, node / element sets, post-processing
pipeline and filters.  The Python ones (analysis, solvers, materials, mesh objects ...) run through mobilecad.guicmd."""
import FreeCAD as App
from FreeCAD import Vector as V
from .bridge import op, _doc
from .partdesign import _sel

# CmdFemConstraint*::activated: object type, base name and the property values the command sets
CONSTRAINTS = {
    'Bearing': ('Fem::ConstraintBearing', 'ConstraintBearing', {}),
    'Contact': ('Fem::ConstraintContact', 'Contact', {'Adjust': 0.0, 'Friction': False, 'FrictionCoefficient': 0.0, 'Scale': 1, 'Slope': '1e6 GPa/m', 'StickSlope': '1e4 GPa/m'}),
    'Displacement': ('Fem::ConstraintDisplacement', 'Displacement', {'Scale': 1}),
    'Fixed': ('Fem::ConstraintFixed', 'Fixed', {'Scale': 1}),
    'RigidBody': ('Fem::ConstraintRigidBody', 'RigidBody', {'Scale': 1}),
    'FluidBoundary': ('Fem::ConstraintFluidBoundary', 'ConstraintFluidBoundary', {'Scale': 1}),
    'Force': ('Fem::ConstraintForce', 'Force', {'Reversed': False, 'Scale': 1, 'Force': '1 N'}),
    'Gear': ('Fem::ConstraintGear', 'ConstraintGear', {'Diameter': 100.0}),
    'Heatflux': ('Fem::ConstraintHeatflux', 'HeatFlux', {'AmbientTemp': 300.0, 'FilmCoef': 10.0, 'Emissivity': 1.0, 'Scale': 1, 'ConstraintType': 'Flux'}),
    'InitialTemperature': ('Fem::ConstraintInitialTemperature', 'InitialTemperature', {'Scale': 1}),
    'PlaneRotation': ('Fem::ConstraintPlaneRotation', 'PlaneRotation', {'Scale': 1}),
    'Pressure': ('Fem::ConstraintPressure', 'Pressure', {'Reversed': False, 'Scale': 1, 'Pressure': '1 MPa'}),
    'Spring': ('Fem::ConstraintSpring', 'Spring', {'NormalStiffness': 1.0, 'TangentialStiffness': 0.0, 'Scale': 1}),
    'Pulley': ('Fem::ConstraintPulley', 'ConstraintPulley', {'Diameter': 300.0, 'OtherDiameter': 100.0, 'CenterDistance': 500.0, 'Force': 100.0, 'TensionForce': 100.0}),
    'Temperature': ('Fem::ConstraintTemperature', 'Temperature', {'Scale': 1}),
    'Transform': ('Fem::ConstraintTransform', 'Transform', {'Scale': 1}),
}

def _analysis():
    try:
        import FemGui
        a = FemGui.getActiveAnalysis()
        if a is not None: return a
    except Exception:
        pass
    a = next((o for o in _doc().Objects if o.isDerivedFrom('Fem::FemAnalysis')), None)
    if a is None: raise RuntimeError('Create an FEM analysis first (FEM ▸ Analysis container)')
    return a

@op
def fem_constraint(kind, sel=None):
    """getConstraintPrerequisits + the command's doCommands; the selected faces / edges / vertexes become References
    (what the task panel's 'Add' does), then the object goes into the active analysis."""
    t, name, props = CONSTRAINTS[kind]
    a = _analysis(); d = _doc()
    c = d.addObject(t, name)
    for k, v in props.items():
        try: setattr(c, k, v)
        except Exception: pass
    refs = [(o, tuple(subs)) for o, subs in ((d.getObject(n), subs) for n, subs in (sel or [])) if o is not None and subs]
    if refs: c.References = refs
    a.addObject(c)
    d.recompute(); return c.Name

def _mesh_obj():
    return next((o for o in _doc().Objects if o.isDerivedFrom('Fem::FemMeshObject')), None)

@op
def fem_nodes_set(polygon=None, view_dir=None, elements=False, name=None):
    """FEM_CreateNodesSet / DefineNodesSet / CreateElementsSet / DefineElementsSet: nodes of the FEM mesh inside a screen
    polygon (projected along the view, like the desktop's lasso)."""
    import Part
    d = _doc(); mesh = _mesh_obj()
    if mesh is None: raise RuntimeError('No FEM mesh')
    obj = d.getObject(name) if name else None
    if obj is None:
        obj = d.addObject('Fem::FemSetElementNodesObject' if elements else 'Fem::FemSetNodesObject', 'ElementsSet' if elements else 'NodesSet')
        obj.FemMesh = mesh
    if polygon:
        dvec = V(*view_dir); dvec.normalize(); M = App.Rotation(dvec, V(0, 0, -1)).toMatrix()
        poly = [M.multVec(V(*p)) for p in polygon]; face = Part.Face(Part.makePolygon([V(p.x, p.y, 0) for p in poly] + [V(poly[0].x, poly[0].y, 0)]))
        inside = []
        for nid, p in mesh.FemMesh.Nodes.items():
            q = M.multVec(p)
            if face.isInside(V(q.x, q.y, 0), 1e-6, True): inside.append(nid)
        if elements:
            nodes = set(inside); els = [e for e in mesh.FemMesh.Volumes or mesh.FemMesh.Faces if set(mesh.FemMesh.getElementNodes(e)) <= nodes]
            obj.Elements = els if 'Elements' in obj.PropertiesList else obj.Elements
            if 'Nodes' in obj.PropertiesList: obj.Nodes = inside
        else:
            obj.Nodes = inside
    try:
        _analysis().addObject(obj)
    except Exception:
        pass
    d.recompute(); return obj.Name

@op
def fem_add_part(sel=None):
    """FEM_FemAddPart: analysis + FEM mesh-shape object of the selected part (the desktop then meshes it)."""
    d = _doc(); objs = [d.getObject(n) for n, _ in (sel or []) if d.getObject(n) is not None]
    if not objs: raise RuntimeError('Select a part')
    a = next((o for o in d.Objects if o.isDerivedFrom('Fem::FemAnalysis')), None) or d.addObject('Fem::FemAnalysis', 'Analysis')
    m = d.addObject('Fem::FemMeshShapeObject', objs[0].Name + '_Mesh'); m.Shape = objs[0]
    a.addObject(m); d.recompute(); return m.Name

# ------------------------------------------------------------------ post-processing (Fem::FemPost*)
FILTERS = {'BranchFilter': 'Fem::FemPostBranchFilter', 'FilterCalculator': 'Fem::FemPostCalculatorFilter',
           'FilterClipRegion': 'Fem::FemPostClipFilter', 'FilterClipScalar': 'Fem::FemPostScalarClipFilter',
           'FilterContours': 'Fem::FemPostContoursFilter', 'FilterCutFunction': 'Fem::FemPostCutFilter',
           'FilterDataAlongLine': 'Fem::FemPostDataAlongLineFilter', 'FilterDataAtPoint': 'Fem::FemPostDataAtPointFilter',
           'FilterWarp': 'Fem::FemPostWarpVectorFilter'}
FUNCTIONS = {'Plane': 'Fem::FemPostPlaneFunction', 'Sphere': 'Fem::FemPostSphereFunction', 'Cylinder': 'Fem::FemPostCylinderFunction',
             'Box': 'Fem::FemPostBoxFunction'}

def _pipeline(sel):
    d = _doc()
    for n, _ in (sel or []):
        o = d.getObject(n)
        if o is not None and o.isDerivedFrom('Fem::FemPostPipeline'): return o
    p = next((o for o in reversed(d.Objects) if o.isDerivedFrom('Fem::FemPostPipeline')), None)
    if p is None: raise RuntimeError('Create a result pipeline first (FEM ▸ Pipeline from result)')
    return p

@op
def fem_post(kind, sel=None):
    d = _doc()
    if kind == 'PipelineFromResult':                 # CmdFemPostPipelineFromResult: pipeline.load(result)
        res = next((d.getObject(n) for n, _ in (sel or []) if d.getObject(n) is not None and d.getObject(n).isDerivedFrom('Fem::FemResultObject')), None) \
            or next((o for o in d.Objects if o.isDerivedFrom('Fem::FemResultObject')), None)
        if res is None: raise RuntimeError('Select a FEM result')
        p = d.addObject('Fem::FemPostPipeline', 'ResultPipeline'); p.load(res)
        try: _analysis().addObject(p)
        except Exception: pass
        d.recompute(); return p.Name
    if kind == 'ApplyChanges':
        for o in d.Objects:
            if o.isDerivedFrom('Fem::FemPostObject'): o.touch()
        d.recompute(); return True
    p = _pipeline(sel)
    if kind in FUNCTIONS:                              # FEM_PostCreateFunctions: implicit function for cut / clip filters
        prov = next((o for o in p.Group if o.isDerivedFrom('Fem::FemPostFunctionProvider')), None) if hasattr(p, 'Group') else None
        if prov is None:
            prov = d.addObject('Fem::FemPostFunctionProvider', 'Functions'); p.addObject(prov) if hasattr(p, 'addObject') else None
        f = d.addObject(FUNCTIONS[kind], kind); prov.addObject(f) if hasattr(prov, 'addObject') else None
        d.recompute(); return f.Name
    if kind == 'FilterLinearizedStresses':             # Python feature in femobjects
        import ObjectsFem
        f = ObjectsFem.makePostLineFilterLinearizedStresses(d) if hasattr(ObjectsFem, 'makePostLineFilterLinearizedStresses') else None
        if f is None: raise RuntimeError('Linearized stresses needs a data-along-line filter')
        p.addObject(f); d.recompute(); return f.Name
    f = d.addObject(FILTERS[kind], kind.replace('Filter', '') or kind)
    p.addObject(f)
    d.recompute(); return f.Name


# ------------------------------------------------------------------ result display (desktop: FEM_ResultShow colours the mesh)
def surface_faces(fm):
    """Outer faces (node id tuples) of a FemMesh: volume element faces used once, or the shell faces.  The face search
    of femmesh2mesh.femmesh_2_mesh, without its side effect (it adds a 'Mesh2Fem' object to the document)."""
    from femmesh.femmesh2mesh import face_dicts
    count = {}
    if fm.VolumeCount > 0:
        for ele in fm.Volumes:
            en = fm.getElementNodes(ele); fd = face_dicts.get(len(en))
            if not fd: continue
            for key in fd:
                f = tuple(en[i] for i in fd[key])
                k = tuple(sorted(f[:4] if len(f) in (4, 8) else f[:3]))
                count[k] = None if k in count else f
    else:
        for ele in fm.Faces:
            en = fm.getElementNodes(ele)
            f = tuple(en[:4] if len(en) in (4, 8) else en[:3]); count[tuple(sorted(f))] = f
    return [f for f in count.values() if f is not None]

def mesh_triangles(fm):
    """FemMesh outer surface as a flat list of triangle corner points (for the viewport)."""
    nodes = fm.Nodes; out = []
    for f in surface_faces(fm):
        c = f[:4] if len(f) in (4, 8) else f[:3]
        out.extend((nodes[c[0]], nodes[c[1]], nodes[c[2]]))
        if len(c) == 4: out.extend((nodes[c[0]], nodes[c[2]], nodes[c[3]]))
    return out

def mesh_edges(fm):
    """Element edges on the FemMesh's outer surface as point pairs (desktop draws meshes 'Faces & Wireframe')."""
    nodes = fm.Nodes; seen = set(); out = []
    for f in surface_faces(fm):
        c = f[:4] if len(f) in (4, 8) else f[:3]
        for a, b in zip(c, c[1:] + c[:1]):
            k = (a, b) if a < b else (b, a)
            if k not in seen: seen.add(k); out.append((nodes[a], nodes[b]))
    return out

def result_surface(res, field='vonMises', scale=0.0):
    """Outer surface triangles of a result's FEM mesh with a per-node value (von Mises, displacement ...), optionally
    deformed by the displacement vectors times `scale`.  Returns (points, triangles, values, (min, max))."""
    fm = res.Mesh.FemMesh
    nodes = fm.Nodes
    disp = dict(zip(res.NodeNumbers, res.DisplacementVectors)) if scale and res.DisplacementVectors else {}
    vals = dict(zip(res.NodeNumbers, getattr(res, field, []) or []))
    faces = surface_faces(fm)
    ids = sorted({n for f in faces for n in (f[:4] if len(f) in (4, 8) else f[:3])})
    index = {n: i for i, n in enumerate(ids)}
    pts = [nodes[n] + disp[n] * scale if n in disp else nodes[n] for n in ids]
    tris = []
    for f in faces:
        c = f[:4] if len(f) in (4, 8) else f[:3]
        tris.append((index[c[0]], index[c[1]], index[c[2]]))
        if len(c) == 4: tris.append((index[c[0]], index[c[2]], index[c[3]]))
    v = [vals.get(n, 0.0) for n in ids]
    return pts, tris, v, ((min(v), max(v)) if v else (0.0, 1.0))

def rainbow(t):
    """FreeCAD's default result colour bar: blue (min) -> cyan -> green -> yellow -> red (max)."""
    t = max(0.0, min(1.0, t)); x = 4 * t
    if x < 1: return (0.0, x, 1.0)
    if x < 2: return (0.0, 1.0, 2 - x)
    if x < 3: return (x - 2, 1.0, 0.0)
    return (1.0, 4 - x, 0.0)

_display = {}       # result name -> (field, scale) chosen with FEM_ResultShow

@op
def fem_result_show(name, field='vonMises', scale=0.0):
    """FEM_ResultShow: which result field colours the mesh and the deformation scale."""
    o = _doc().getObject(name)
    if o is None or not o.isDerivedFrom('Fem::FemResultObject'): raise RuntimeError('Select a FEM result')
    _display[name] = (field, float(scale)); o.touch(); _doc().recompute()
    v = getattr(o, field, []) or []
    return {'min': min(v) if v else 0, 'max': max(v) if v else 0, 'fields': [f for f in ('vonMises', 'DisplacementLengths', 'MaxShear',
            'PrincipalMax', 'PrincipalMin', 'Temperature', 'MassFlowRate', 'NetworkPressure', 'Peeq') if getattr(o, f, None)]}

# ------------------------------------------------------------------ post-processing display (pipelines and filters)
# Desktop: VTK renders Fem::FemPost* objects.  Here FemPostObject.writeVTK writes the object's data set (VTK XML,
# base64 + zlib), this reader takes points / cells / point data back, and the outer surface is coloured by a field.
def _vtk_array(el, header_type, compressed):
    import base64, zlib, numpy as np
    dt = {'Float32': np.float32, 'Float64': np.float64, 'Int8': np.int8, 'UInt8': np.uint8, 'Int16': np.int16,
          'UInt16': np.uint16, 'Int32': np.int32, 'UInt32': np.uint32, 'Int64': np.int64, 'UInt64': np.uint64}[el.get('type')]
    ht = np.uint64 if header_type == 'UInt64' else np.uint32; hs = np.dtype(ht).itemsize
    text = (el.text or '').strip()
    if el.get('format') == 'ascii':
        return np.array(text.split(), dtype=dt)
    if not compressed:
        raw = base64.b64decode(text)
        return np.frombuffer(raw[hs:], dtype=dt)
    # compressed: base64(header) + base64(blocks); header = [nblocks, blocksize, lastsize, csize_1..csize_n]
    first = base64.b64decode(text[:((3 * hs + 2) // 3) * 4])
    nb = int(np.frombuffer(first[:hs], dtype=ht)[0])
    hlen = ((hs * (3 + nb) + 2) // 3) * 4
    header = np.frombuffer(base64.b64decode(text[:hlen]), dtype=ht)[:3 + nb]
    data = base64.b64decode(text[hlen:])
    out, pos = [], 0
    for cs in header[3:]:
        out.append(zlib.decompress(data[pos:pos + int(cs)])); pos += int(cs)
    return np.frombuffer(b''.join(out), dtype=dt)

def vtk_xml_read(path):
    """points (N,3), [(cell type, node ids)], {point field: array} from a .vtu / .vtp / .vtm written by writeVTK."""
    import os, xml.etree.ElementTree as ET, numpy as np
    root = ET.parse(path).getroot()
    if root.get('type') == 'vtkMultiBlockDataSet':
        P, C, D = np.zeros((0, 3)), [], {}
        for ds in root.iter('DataSet'):
            f = ds.get('file')
            if not f: continue
            p2, c2, d2 = vtk_xml_read(os.path.join(os.path.dirname(path), f))
            off = len(P); P = np.vstack([P, p2]); C += [(t, [n + off for n in ids]) for t, ids in c2]
            for k, v in d2.items(): D[k] = np.concatenate([D[k], v]) if k in D else v
        return P, C, D
    ht = root.get('header_type', 'UInt32'); comp = bool(root.get('compressor'))
    P, C, D = [], [], {}
    for piece in root.iter('Piece'):
        pts = _vtk_array(piece.find('Points/DataArray'), ht, comp).reshape(-1, 3)
        off = sum(len(p) for p in P); P.append(pts)
        def arr(sec, name):
            e = piece.find(sec)
            if e is None: return None
            for a in e.findall('DataArray'):
                if a.get('Name') == name: return _vtk_array(a, ht, comp)
            return None
        conn, offs, types = arr('Cells', 'connectivity'), arr('Cells', 'offsets'), arr('Cells', 'types')
        if conn is not None:
            s = 0
            for t, o in zip(types, offs): C.append((int(t), [int(x) + off for x in conn[s:o]])); s = o
        for sec, t in (('Polys', 7), ('Strips', 6), ('Lines', 3)):
            c2, o2 = arr(sec, 'connectivity'), arr(sec, 'offsets')
            if c2 is None: continue
            s = 0
            for o in o2: C.append((t, [int(x) + off for x in c2[s:o]])); s = o
        pd = piece.find('PointData')
        if pd is not None:
            for a in pd.findall('DataArray'):
                v = _vtk_array(a, ht, comp); nc = int(a.get('NumberOfComponents', 1))
                v = v.reshape(-1, nc) if nc > 1 else v
                D[a.get('Name')] = np.concatenate([D[a.get('Name')], v]) if a.get('Name') in D else v
    return (np.vstack(P) if P else np.zeros((0, 3))), C, D

# VTK cell faces (corner nodes) for the outer surface of volume cells
_VTK_FACES = {10: [(0, 1, 2), (0, 3, 1), (1, 3, 2), (0, 2, 3)], 24: [(0, 1, 2), (0, 3, 1), (1, 3, 2), (0, 2, 3)],
              12: [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)],
              25: [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)],
              13: [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)], 26: [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)],
              14: [(0, 3, 2, 1), (0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4)]}

def post_surface(obj, field=None):
    """Outer surface of a FemPost object's data with a per-point scalar (vectors as magnitude)."""
    import os, tempfile, numpy as np
    d = tempfile.mkdtemp(prefix='post'); base = os.path.join(d, 'data.vtk')
    obj.writeVTK(base)
    f = next(os.path.join(d, x) for x in os.listdir(d) if x.startswith('data.'))
    P, C, D = vtk_xml_read(f)
    count = {}
    for t, ids in C:
        if t in _VTK_FACES:
            for fc in _VTK_FACES[t]:
                face = tuple(ids[i] for i in fc); k = tuple(sorted(face))
                count[k] = None if k in count else face
        elif t in (5, 9, 7, 22, 23, 28):               # triangle / quad / polygon (and quadratic 2D: corners)
            corners = ids[:3] if t == 22 else ids[:4] if t in (23, 28) else ids
            count[tuple(sorted(corners)) + (len(count),)] = tuple(corners)
    tris = []
    for face in (f for f in count.values() if f is not None):
        for i in range(1, len(face) - 1): tris.append((face[0], face[i], face[i + 1]))
    import numpy as _np
    st = next((D[k] for k in ('STRESS', 'Stress', 'Stress tensor') if k in D and _np.ndim(D[k]) == 2 and D[k].shape[1] == 6), None)
    if st is not None and 'von Mises Stress' not in D:      # derived like CalculiXTools._generate_derived_result
        sx, sy, sz, sxy, syz, szx = (st[:, i].astype(float) for i in range(6))
        D['von Mises Stress'] = _np.sqrt(0.5 * ((sx - sy) ** 2 + (sy - sz) ** 2 + (sz - sx) ** 2 + 6 * (sxy ** 2 + syz ** 2 + szx ** 2)))
    names = list(D)
    pick = field if field in D else next((n for n in ('von Mises Stress', 'Displacement', 'DISP', 'Temperature', 'NDTEMP', 'Potential') if n in D), names[0] if names else None)
    vals = None
    if pick is not None:
        v = np.asarray(D[pick], dtype=float)
        vals = np.linalg.norm(v, axis=1) if v.ndim > 1 else v
    return P, tris, vals, pick, names

@op
def fem_post_show(name, field=None):
    """Which point field colours a pipeline / filter (desktop: the Field selector of the post object's task panel)."""
    o = _doc().getObject(name)
    _display[name] = (field, 0.0); o.touch(); _doc().recompute()
    P, tris, vals, pick, names = post_surface(o, field)
    return {'field': pick, 'fields': names, 'min': float(vals.min()) if vals is not None and len(vals) else 0,
            'max': float(vals.max()) if vals is not None and len(vals) else 0}


def _load_vtk_results_native(self):
    """calculixtools.CalculiXTools._load_vtk_results without VTK's Python wrapping: Fem.frdToVTK converts the .frd,
    FemPostPipeline.read (C++) loads the .vtm.  (Von Mises etc. are derived when shown, see post_surface.)"""
    import os, Fem
    keep = self.fem_param.GetGroup("General").GetBool("KeepResultsOnReRun", False)
    pipeline = next((r for r in self.obj.Results if r.isDerivedFrom("Fem::FemPostPipeline")), None)
    if not pipeline or keep:
        pipeline = self.obj.Document.addObject("Fem::FemPostPipeline", self.obj.Name + "Result")
        self.analysis.addObject(pipeline)
        self.obj.Results = self.obj.Results + [pipeline]
    prefix = os.path.join(self.obj.WorkingDirectory, self.input_deck)
    Fem.frdToVTK(prefix + ".frd", self.fem_param.GetGroup("Ccx").GetBool("BinaryOutput", False))
    for f in sorted(os.listdir(self.obj.WorkingDirectory)):
        if f.endswith(".vtm"):
            pipeline.read(os.path.join(self.obj.WorkingDirectory, f)); break
    try:                                                    # the desktop's field names (DISP -> Displacement ...)
        pipeline.renameArrays(self.frd_var_conversion(self.obj.AnalysisType))
        self._set_time_info(pipeline)
    except Exception as e:
        App.Console.PrintWarning('FEM result fields: %s\n' % e)
    pipeline.recomputeChildren(); self.obj.Document.recompute()

def patch_solver_tools():
    try:
        import vtkmodules.vtkIOXML as _x
        if not getattr(_x, '__file__', None):               # the placeholder module of shims/_gui_finder.py
            raise ImportError
        return                                              # real VTK Python: FreeCAD's own code works
    except Exception:
        pass
    try:
        from femsolver.calculix import calculixtools
        calculixtools.CalculiXTools._load_vtk_results = _load_vtk_results_native
    except Exception:
        pass
