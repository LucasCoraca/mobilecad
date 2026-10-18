"""Mesh, MeshPart, Points and Reverse Engineering workbenches (src/Mod/Mesh/Gui/Command.cpp, MeshPart/Gui/Command.cpp,
Points/Gui/Command.cpp, ReverseEngineering/Gui/Command.cpp).  The C++ commands are dialogs around the Mesh / MeshPart /
Points Python API; these ops call the same API with the dialog's values."""
import math
import FreeCAD as App
from FreeCAD import Vector as V
from .bridge import op, _doc
from .partdesign import _sel

def _objs(names):
    d = _doc(); return [d.getObject(n) for n in names if d.getObject(n) is not None]

def _meshes(names):
    ms = [o for o in _objs(names) if o.isDerivedFrom('Mesh::Feature')]
    if not ms: raise RuntimeError('Select a mesh')
    return ms

def _add_mesh(name, mesh):
    f = _doc().addObject('Mesh::Feature', name); f.Mesh = mesh; return f

# ------------------------------------------------------------------ booleans / creation
@op
def mesh_bool(kind, names):
    """Mesh_Union / Difference / Intersection: desktop runs OpenSCAD; the Mesh kernel's own booleans give the result
    without the external program."""
    a, b = _meshes(names)[:2]
    m = {'Union': a.Mesh.unite, 'Difference': a.Mesh.difference, 'Intersection': a.Mesh.intersect}[kind](b.Mesh)
    f = _add_mesh(kind, m); _doc().recompute(); return f.Name

@op
def mesh_from_shape(names, mode='Standard', linear=0.1, angular=28.65, relative=False, max_length=0.0):
    """Mesh_FromPartShape / MeshPart_Mesher (Tessellation dialog): Standard mesher (linear/angular deflection) or
    Mefisto (maximum edge length).  Mesh_FromGeometry: Mesh.Mesh(shape.tessellate(deviation))."""
    import MeshPart, Mesh
    last = None
    for o in _objs(names):
        sh = o.Shape.copy(False); sh.Placement = o.getGlobalPlacement()
        if mode == 'Geometry':
            m = Mesh.Mesh(sh.tessellate(float(linear)))
        elif mode == 'Mefisto':
            m = MeshPart.meshFromShape(Shape=sh, MaxLength=float(max_length) or max(sh.BoundBox.DiagonalLength / 50, 0.1))
        else:
            m = MeshPart.meshFromShape(Shape=sh, LinearDeflection=float(linear), AngularDeflection=math.radians(float(angular)), Relative=bool(relative))
        f = _add_mesh(o.Name + '_Mesh' if mode != 'Geometry' else 'Mesh', m); f.Label = o.Label + ' (Meshed)'; last = f.Name
    return last

@op
def mesh_regular_solid(kind, params):
    """Mesh_BuildRegularSolid: Mesh::Cube / Cylinder / Cone / Sphere / Ellipsoid / Torus feature objects."""
    f = _doc().addObject('Mesh::' + kind, kind)
    for k, v in (params or {}).items():
        if k in f.PropertiesList: setattr(f, k, v)
    _doc().recompute(); return f.Name

# ------------------------------------------------------------------ repair / modify (dialogs -> values)
@op
def mesh_modify(kind, names, opts=None):
    o = opts or {}
    for f in _meshes(names):
        m = f.Mesh.copy()
        if kind == 'HarmonizeNormals': m.harmonizeNormals()
        elif kind == 'FlipNormals': m.flipNormals()
        elif kind == 'FillupHoles': m.fillupHoles(int(o.get('length', 3)))
        elif kind == 'Smoothing':                     # DlgSmoothing: Taubin / Laplace / MedianFilter
            meth = o.get('method', 'Taubin')
            if meth == 'Laplace': m.smooth(Method='Laplace', Iteration=int(o.get('iterations', 4)), Lambda=float(o.get('lambda', 0.6307)))
            elif meth == 'MedianFilter': m.smooth(Method='MedianFilter', Iteration=int(o.get('iterations', 4)))
            else: m.smooth(Method='Taubin', Iteration=int(o.get('iterations', 4)), Lambda=float(o.get('lambda', 0.6307)), Micro=float(o.get('mu', 0.0424)))
        elif kind == 'Decimating':                   # DlgDecimating: tolerance + reduction
            m.decimate(float(o.get('tolerance', 0.1)), float(o.get('reduction', 0.9)))
        elif kind == 'Scale':
            k = float(o.get('factor', 1.0)); mat = App.Matrix(); mat.scale(k, k, k); m.transform(mat)
        elif kind == 'RemoveComponents':             # RemoveComponents dialog: delete components with fewer facets
            m.removeComponents(int(o.get('count', 10)))
        elif kind == 'Repair':                        # DlgEvaluateMesh "Repair" buttons
            for step in o.get('steps', ['DuplicatedPoints', 'DuplicatedFacets', 'Degenerations', 'Indices', 'NonManifolds', 'Orientation', 'SelfIntersections', 'Folds']):
                try:
                    {'DuplicatedPoints': m.removeDuplicatedPoints, 'DuplicatedFacets': m.removeDuplicatedFacets,
                     'Degenerations': lambda: m.fixDegenerations(0.0), 'Indices': m.fixIndices, 'NonManifolds': m.removeNonManifolds,
                     'Orientation': m.harmonizeNormals, 'SelfIntersections': m.fixSelfIntersections, 'Folds': m.removeFoldsOnSurface}[step]()
                except Exception:
                    pass
        elif kind == 'Refine': m.refine()
        f.Mesh = m
    _doc().recompute(); return True

@op
def mesh_evaluate(name):
    """Mesh_Evaluation / EvaluateSolid / BoundingBox: what DlgEvaluateMesh analyses."""
    m = _doc().getObject(name).Mesh; bb = m.BoundBox
    return {'points': m.CountPoints, 'facets': m.CountFacets, 'edges': m.CountEdges, 'solid': m.isSolid(),
            'nonManifolds': m.hasNonManifolds(), 'nonUniformOrientation': m.countNonUniformOrientedFacets(),
            'selfIntersections': m.hasSelfIntersections(), 'invalidPoints': m.hasInvalidPoints(), 'pointsOnEdge': m.hasPointsOnEdge(),
            'corruptedFacets': m.hasCorruptedFacets(), 'components': m.countComponents(), 'area': m.Area, 'volume': m.Volume,
            'bbox': [bb.XMin, bb.YMin, bb.ZMin, bb.XMax, bb.YMax, bb.ZMax]}

@op
def mesh_facet_info(name, facet):
    """Mesh_EvaluateFacet / Mesh_CurvatureInfo: facet data at a picked facet (Face<i> = facet i-1)."""
    o = _doc().getObject(name); m = o.Mesh; f = m.Facets[int(facet)]
    out = {'index': int(facet), 'points': [list(p) for p in f.Points], 'normal': list(f.Normal), 'area': f.Area,
           'neighbours': list(f.NeighbourIndices), 'degenerated': f.isDegenerated(), 'deformed': f.isDeformed(-0.99, 0.99)}
    try:
        cv = m.getCurvaturePerVertex(); ks = [cv[i] for i in f.PointIndices]
        out['curvature'] = [[round(a, 6), round(b, 6)] for a, b in ((k[0], k[1]) if isinstance(k, (list, tuple)) else (k, 0) for k in ks)]
    except Exception:
        pass
    return out

@op
def mesh_curvature(names):
    """Mesh_VertexCurvature: Mesh::Curvature feature on each mesh."""
    last = None
    for o in _meshes(names):
        c = _doc().addObject('Mesh::Curvature', 'Curvature'); c.Source = o; last = c.Name
    _doc().recompute(); return last

@op
def mesh_merge(names):
    import Mesh
    m = Mesh.Mesh()
    for o in _meshes(names):
        k = o.Mesh.copy(); m.addMesh(k)
    f = _add_mesh('Mesh', m); _doc().recompute(); return f.Name

@op
def mesh_split_components(names):
    last = None
    for o in _meshes(names):
        for comp in o.Mesh.getSeparateComponents():
            last = _add_mesh('Component', comp).Name
    _doc().recompute(); return last

@op
def mesh_remove_by_facets(name, facets):
    """Mesh_RemoveCompByHand: remove the components containing the tapped facets."""
    o = _doc().getObject(name); m = o.Mesh.copy()
    segs = []; idx = {}                           # components by neighbour flood fill
    # flood fill by neighbours
    for start in range(m.CountFacets):
        if start in idx: continue
        stack = [start]; cid = len(segs); segs.append([])
        while stack:
            i = stack.pop()
            if i in idx: continue
            idx[i] = cid; segs[cid].append(i)
            for n in m.Facets[i].NeighbourIndices:
                if n < m.CountFacets and n not in idx: stack.append(n)
    drop = {idx[int(f)] for f in facets if int(f) in idx}
    rm = [i for c in drop for i in segs[c]]
    m.removeFacets(rm); o.Mesh = m; _doc().recompute(); return len(rm)

@op
def mesh_add_facet(name, pts):
    """Mesh_AddFacet: a triangle through three picked points."""
    o = _doc().getObject(name); m = o.Mesh.copy()
    m.addFacet(V(*pts[0]), V(*pts[1]), V(*pts[2])); o.Mesh = m; _doc().recompute(); return True

@op
def mesh_fill_hole(name, near):
    """Mesh_FillInteractiveHole: fill the boundary loop nearest to the tapped point (fillupHoles limited to it)."""
    o = _doc().getObject(name); m = o.Mesh.copy(); before = m.CountFacets
    m.fillupHoles(1000); o.Mesh = m; _doc().recompute(); return m.CountFacets - before

# ------------------------------------------------------------------ polygon tools along the view direction
def _view_matrix(view_dir):
    d = V(*view_dir); d.normalize()
    r = App.Rotation(d, V(0, 0, -1))                  # view direction -> -Z, so screen polygons lie in XY
    return r.toMatrix()

@op
def mesh_poly(kind, names, polygon, view_dir, inner=True):
    """Mesh_PolyCut / PolyTrim / PolySegm / PolySplit: the polygon drawn on screen, projected along the view."""
    import Mesh
    M = _view_matrix(view_dir); Mi = M.inverse()
    poly = [M.multVec(V(*p)) for p in polygon]
    poly2 = [V(p.x, p.y, 0) for p in poly]
    last = None
    for o in _meshes(names):
        m = o.Mesh.copy(); m.transform(M)
        if kind in ('Cut', 'Trim'):
            (m.cut if kind == 'Cut' else m.trim)(poly2, 1 if inner else 2)
            m.transform(Mi); o.Mesh = m
        else:
            import Part
            face = Part.Face(Part.makePolygon(poly2 + [poly2[0]]))
            ins = [i for i, f in enumerate(m.Facets)
                   if face.isInside(V(sum(p[0] for p in f.Points) / 3, sum(p[1] for p in f.Points) / 3, 0), 1e-6, True)]
            seg = m.meshFromSegment(ins); seg.transform(Mi)
            if kind == 'Segm':
                last = _add_mesh('Segment', seg).Name
            else:                                       # Split: inside part becomes its own mesh, the rest stays
                m.removeFacets(ins); m.transform(Mi); o.Mesh = m; last = _add_mesh(o.Name + '_split', seg).Name
    _doc().recompute(); return last

# ------------------------------------------------------------------ segmentation
@op
def mesh_segmentation(name, kind='Curvature', opts=None):
    """Mesh_Segmentation (curvature) / Mesh_SegmentationBestFit (plane / cylinder / sphere fits): one Mesh feature per
    segment, like the dialogs' 'Create segments'."""
    o = _doc().getObject(name); m = o.Mesh; x = opts or {}; out = []
    if kind == 'Curvature':
        segs = m.getSegmentsByCurvature([('Plane', float(x.get('tol', 0.1)), 0.0, 0.0, int(x.get('min', 20)))])
    else:
        segs = m.getSegmentsOfType(kind, float(x.get('dev', 0.1)), int(x.get('min', 20)))
    for s in segs:
        if len(s) >= 3: out.append(_add_mesh('Segment', m.meshFromSegment(s)).Name)
    _doc().recompute(); return out

# ------------------------------------------------------------------ MeshPart cuts / sections
@op
def mesh_plane_op(kind, names, base, normal):
    """MeshPart_TrimByPlane / SectionByPlane: trim keeps the side opposite the normal; section makes a wire."""
    import Part
    last = None
    for o in _meshes(names):
        m = o.Mesh.copy()
        if kind == 'Trim':
            m.trimByPlane(V(*base), V(*normal)); o.Mesh = m
        else:
            pts = m.crossSections([(V(*base), V(*normal))], 1e-6)
            wires = [Part.makePolygon(poly) for sec in pts for poly in sec if len(poly) > 1]
            f = _doc().addObject('Part::Feature', 'Section'); f.Shape = Part.makeCompound(wires); last = f.Name
    _doc().recompute(); return last

@op
def mesh_cross_sections(names, axis='Z', count=10):
    """MeshPart_CrossSections: parallel sections along an axis across the bounding box."""
    import Part
    last = None
    n = V(*{'X': (1, 0, 0), 'Y': (0, 1, 0), 'Z': (0, 0, 1)}[axis])
    for o in _meshes(names):
        bb = o.Mesh.BoundBox; lo = getattr(bb, axis + 'Min'); hi = getattr(bb, axis + 'Max')
        planes = []
        for i in range(int(count)):
            v = lo + (hi - lo) * (i + 0.5) / count; b = V(0, 0, 0); setattr(b, axis.lower(), v); planes.append((b, n))
        secs = o.Mesh.crossSections(planes, 1e-6)
        wires = [Part.makePolygon(poly) for sec in secs for poly in sec if len(poly) > 1]
        f = _doc().addObject('Part::Feature', o.Name + '_cs'); f.Shape = Part.makeCompound(wires); last = f.Name
    _doc().recompute(); return last

@op
def mesh_curve_on_mesh(name, pts):
    """MeshPart_CurveOnMesh / Surface_CurveOnMesh: picked points on the mesh -> projected polyline -> B-spline."""
    import Part, MeshPart
    o = _doc().getObject(name)
    P = [V(*p) for p in pts]
    w = Part.makePolygon(P)
    try:
        proj = MeshPart.projectShapeOnMesh(w, o.Mesh, 0.0)
        flat = [p for seg in proj for p in seg]
    except Exception:
        flat = P
    bs = Part.BSplineCurve(); bs.interpolate(flat)
    f = _doc().addObject('Part::Spline', 'Spline'); f.Shape = bs.toShape(); _doc().recompute(); return f.Name

# ------------------------------------------------------------------ Points workbench
@op
def points_op(kind, names, opts=None):
    import Points, Mesh
    o = opts or {}; d = _doc(); last = None
    objs = _objs(names)
    if kind == 'Convert':                 # Points_Convert: shape / mesh vertexes -> point cloud
        for x in objs:
            pts = [v.Point for v in x.Shape.Vertexes] if hasattr(x, 'Shape') and not x.isDerivedFrom('Mesh::Feature') else [V(*p) for p in x.Mesh.Topology[0]]
            if hasattr(x, 'Shape') and x.Shape.Faces and o.get('dense', True):
                pts = [p for f in x.Shape.Faces for p in f.tessellate(float(o.get('tol', 0.1)))[0]]
            f = d.addObject('Points::Feature', x.Name + '_Points'); f.Points = Points.Points(pts); last = f.Name
    elif kind == 'Merge':
        p = Points.Points()
        for x in objs:
            if x.isDerivedFrom('Points::Feature'): p.addPoints(x.Points.Points)
        f = d.addObject('Points::Feature', 'Points'); f.Points = p; last = f.Name
    elif kind == 'Structure':             # Points_Structure: structured (grid) copy
        for x in objs:
            f = d.addObject('Points::Structured', x.Name + '_Structured'); f.Points = x.Points
            f.Width = int(o.get('width', 1)); f.Height = int(o.get('height', x.Points.CountPoints)); last = f.Name
    d.recompute(); return last

@op
def points_poly_cut(names, polygon, view_dir, inner=True):
    """Points_PolyCut: remove the points inside (or outside) the screen polygon."""
    import Points, Part
    M = _view_matrix(view_dir)
    poly2 = [V(M.multVec(V(*p)).x, M.multVec(V(*p)).y, 0) for p in polygon]
    face = Part.Face(Part.makePolygon(poly2 + [poly2[0]]))
    for x in _objs(names):
        keep = []
        for p in x.Points.Points:
            q = M.multVec(p); inside = face.isInside(V(q.x, q.y, 0), 1e-6, True)
            if inside != bool(inner): keep.append(p)
        x.Points = Points.Points(keep)
    _doc().recompute(); return True

# ------------------------------------------------------------------ Reverse Engineering
def _cloud(x):
    if x.isDerivedFrom('Points::Feature'): return list(x.Points.Points)
    if x.isDerivedFrom('Mesh::Feature'): return [V(*p) for p in x.Mesh.Topology[0]]
    return [v.Point for v in x.Shape.Vertexes]

@op
def reen_fit(kind, names, opts=None):
    """Reen_ApproxPlane / ApproxCylinder / ApproxSphere / ApproxPolynomial / ApproxSurface / ApproxCurve (ReverseEngineering
    Gui/Command.cpp): least-squares fits of the selected points / mesh vertexes."""
    import Part
    o = opts or {}; d = _doc(); last = None
    for x in _objs(names):
        P = _cloud(x)
        if len(P) < 3: raise RuntimeError('Not enough points')
        c = V()
        for p in P: c = c + p
        c = c * (1.0 / len(P))
        if kind == 'Plane':                          # covariance eigenvector of the smallest eigenvalue
            import numpy as np
            A = np.array([[p.x - c.x, p.y - c.y, p.z - c.z] for p in P]); _, _, vt = np.linalg.svd(A, full_matrices=False)
            n = V(*vt[2]); u = V(*vt[0]); w = n.cross(u)
            ext = [(p - c) for p in P]; us = [e.dot(u) for e in ext]; ws = [e.dot(w) for e in ext]
            pts = [c + u * a + w * b for a, b in ((min(us), min(ws)), (max(us), min(ws)), (max(us), max(ws)), (min(us), max(ws)))]
            f = d.addObject('Part::Feature', 'Plane_fit'); f.Shape = Part.Face(Part.makePolygon(pts + [pts[0]]))
        elif kind == 'Sphere':                       # algebraic least squares
            import numpy as np
            A = np.array([[2 * p.x, 2 * p.y, 2 * p.z, 1.0] for p in P]); b = np.array([p.x * p.x + p.y * p.y + p.z * p.z for p in P])
            s, *_ = np.linalg.lstsq(A, b, rcond=None); cen = V(*s[:3]); r = math.sqrt(s[3] + cen.dot(cen))
            f = d.addObject('Part::Sphere', 'Sphere_fit'); f.Radius = r; f.Placement.Base = cen
        elif kind == 'Cylinder':                     # axis = principal direction, radius = mean distance to the axis
            import numpy as np
            A = np.array([[p.x - c.x, p.y - c.y, p.z - c.z] for p in P]); _, _, vt = np.linalg.svd(A, full_matrices=False)
            ax = V(*vt[0]); ts = [(p - c).dot(ax) for p in P]
            r = sum(((p - c) - ax * t).Length for p, t in zip(P, ts)) / len(P)
            f = d.addObject('Part::Cylinder', 'Cylinder_fit'); f.Radius = r; f.Height = max(ts) - min(ts)
            f.Placement = App.Placement(c + ax * min(ts), App.Rotation(V(0, 0, 1), ax))
        elif kind == 'Polynomial':                   # Mesh.polynomialFit: z = f(x, y) quadric (report, like the desktop)
            import Mesh
            r = Mesh.polynomialFit(P)
            return {'coefficients': list(r.get('Coefficients', [])), 'residuals': list(r.get('Residuals', []))[:10]}
        elif kind == 'Surface':                      # ReverseEngineering.approxSurface (B-spline)
            import ReverseEngineering as RE
            s = RE.approxSurface(Points=[tuple(p) for p in P], UDegree=int(o.get('udeg', 3)), VDegree=int(o.get('vdeg', 3)),
                                 NbUPoles=int(o.get('upoles', 6)), NbVPoles=int(o.get('vpoles', 6)), Smooth=True, Weight=0.1)
            f = d.addObject('Part::Spline', 'Surface'); f.Shape = s.toShape()
        elif kind == 'Curve':
            import ReverseEngineering as RE
            cv = RE.approxCurve(Points=[tuple(p) for p in P], Closed=bool(o.get('closed', False)))
            f = d.addObject('Part::Spline', 'Curve'); f.Shape = cv.toShape() if hasattr(cv, 'toShape') else cv
        else:
            raise RuntimeError(kind)
        last = f.Name
    d.recompute(); return last

@op
def reen_mesh_boundary(names):
    """Reen_MeshBoundary: wires along the open boundaries of a mesh (MeshPart.wireFromMesh)."""
    import MeshPart, Part
    last = None
    for x in _meshes(names):
        ws = MeshPart.wireFromMesh(x.Mesh)
        f = _doc().addObject('Part::Feature', x.Name + '_Boundary'); f.Shape = Part.makeCompound(ws); last = f.Name
    _doc().recompute(); return last

@op
def reen_segmentation_from_components(names):
    """Reen_SegmentationFromComponents: one Part face set per mesh component."""
    import Part
    last = None
    for x in _meshes(names):
        for comp in x.Mesh.getSeparateComponents():
            sh = Part.Shape(); sh.makeShapeFromMesh(comp.Topology, 0.1)
            f = _doc().addObject('Part::Feature', 'Segment'); f.Shape = sh; last = f.Name
    _doc().recompute(); return last

# ------------------------------------------------------------------ external programs shipped with the app
def gmsh_binary():
    """The Gmsh executable packaged as jniLibs/<abi>/libgmsh.so (Android only executes files from the app's native
    library directory)."""
    import os
    p = os.path.join(os.environ.get('FC_NATIVE_LIB_DIR', ''), 'libgmsh.so')
    return p if os.path.exists(p) else None

@op
def mesh_remesh_gmsh(name, algorithm=2, max_size=0.0, min_size=0.0, angle=40.0):
    """Mesh_RemeshGmsh (MeshGui::RemeshGmsh): the mesh as STL, Gmsh classifies its surfaces along sharp edges,
    reparametrizes and remeshes them (same .geo as the desktop dialog), the result replaces the mesh."""
    import os, subprocess, tempfile, Mesh
    exe = gmsh_binary()
    if exe is None: raise RuntimeError('Gmsh is not available on this device')
    o = _doc().getObject(name)
    tmp = tempfile.mkdtemp(prefix='remesh')
    stl, geo = os.path.join(tmp, 'mesh.stl'), os.path.join(tmp, 'mesh.geo')
    o.Mesh.write(stl)
    with open(geo, 'w') as f:
        f.write('// geo file for meshing with Gmsh meshing software created by FreeCAD\n'
                'Merge "%s";\n\nMesh.Algorithm = %d;\n\nMesh.CharacteristicLengthMax = %g;\nMesh.CharacteristicLengthMin = %g;\n\n'
                'angle = DefineNumber[%g, Min 20, Max 120, Step 1, Name "Parameters/Angle for surface detection" ];\n\n'
                'forceParametrizablePatches = DefineNumber[0, Choices{0,1}, Name "Parameters/Create surfaces guaranteed to be parametrizable"];\n\n'
                'includeBoundary = 1;\nClassifySurfaces{angle * Pi/180, includeBoundary, forceParametrizablePatches};\nCreateGeometry;\n\n'
                'Surface Loop(1) = Surface{:};\nVolume(1) = {1};\n' % (stl, int(algorithm), float(max_size) or 1.0e22, float(min_size), float(angle)))
    r = subprocess.run([exe, '-', '-bin', '-2', geo, '-o', stl], capture_output=True, text=True, timeout=600)
    if r.returncode != 0: raise RuntimeError('Gmsh failed: ' + (r.stderr or r.stdout)[-500:])
    m = Mesh.Mesh(stl); m.harmonizeNormals()
    o.Mesh = m; _doc().recompute()
    return {'facets': m.CountFacets, 'log': r.stdout[-300:]}
