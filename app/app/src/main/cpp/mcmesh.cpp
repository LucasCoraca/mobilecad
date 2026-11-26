// Display meshes of FreeCAD shapes, built natively (loaded by the Python bridge with ctypes once Part is imported, so
// OCCT's libraries are already in the process).  Does what desktop FreeCAD's ViewProviderPartExt::updateVisual does:
// BRepMesh_IncrementalMesh (in parallel), the faces' triangulations with normals from the surface, edges as the
// polygons on those triangulations (free edges: their 3D polygon), vertexes, all in FreeCAD's sub-element numbering
// (TopExp::MapShapes order: Face<i>, Edge<i>, Vertex<i>).  Python's per-face tessellate() calls built a Python object
// per mesh node and meshed one face at a time: a large STEP assembly took a quarter of an hour.
//
// A Python Part.Shape (Part::TopoShapePy) holds its TopoShape as the twin pointer of FreeCAD's PyObjectBase, and the
// TopoShape holds its TopoDS_Shape: their offsets are those of this build of FreeCAD (checked by mc_check, which the
// bridge calls on its first shapes before using anything else here; on a mismatch it keeps its Python path).
#include <BRepBndLib.hxx>
#include <BRepLib_ToolTriangulatedShape.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRepAdaptor_Curve.hxx>
#include <BRep_Tool.hxx>
#include <Bnd_Box.hxx>
#include <GCPnts_TangentialDeflection.hxx>
#include <Poly_Connect.hxx>
#include <Poly_Polygon3D.hxx>
#include <Poly_PolygonOnTriangulation.hxx>
#include <Poly_Triangulation.hxx>
#include <Standard_Failure.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedMapOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <unordered_set>
#include <vector>

namespace {
constexpr size_t kTwin = 0x18;    // PyObject* -> PyObjectBase::_pcTwinPointer (vtable, PyObject head, status bits)
constexpr size_t kShape = 0x80;   // TopoShape* -> TopoShape::_Shape
const double kAngle = 0.5;        // desktop's MeshAngularDeflection, 28.65 degrees

const TopoDS_Shape& shapeOf(void* py) {
    char* twin = *reinterpret_cast<char**>(static_cast<char*>(py) + kTwin);
    return *reinterpret_cast<const TopoDS_Shape*>(twin + kShape);
}

// desktop's deflection (Part::Tools::getDeflection, MeshDeviation 0.2): a fraction of the bounding box, not finer than tol
double deflection(const TopoDS_Shape& s, double tol) {
    Bnd_Box b;
    try { BRepBndLib::Add(s, b, Standard_False); } catch (...) { return tol; }
    if (b.IsVoid()) return tol;
    double x0, y0, z0, x1, y1, z1; b.Get(x0, y0, z0, x1, y1, z1);
    return std::max(tol, ((x1 - x0) + (y1 - y0) + (z1 - z0)) / 300.0 * 0.2);
}

bool meshed(const TopoDS_Shape& s) {
    for (TopExp_Explorer x(s, TopAbs_FACE); x.More(); x.Next()) {
        TopLoc_Location l;
        if (BRep_Tool::Triangulation(TopoDS::Face(x.Current()), l).IsNull()) return false;
    }
    return true;
}

void mesh(const TopoDS_Shape& s, double tol, bool parallel) {
    try {
        IMeshTools_Parameters p;
        p.Deflection = deflection(s, tol); p.Angle = kAngle; p.Relative = Standard_False;
        p.InParallel = parallel; p.AllowQualityDecrease = Standard_True;
        BRepMesh_IncrementalMesh m(s, p);
    } catch (...) {}
}

template <class T> void put(std::vector<char>& out, const std::vector<T>& a) {
    int32_t n = int32_t(a.size() * sizeof(T) / 4);
    out.insert(out.end(), reinterpret_cast<const char*>(&n), reinterpret_cast<const char*>(&n) + 4);
    out.insert(out.end(), reinterpret_cast<const char*>(a.data()), reinterpret_cast<const char*>(a.data()) + a.size() * sizeof(T));
}
}

extern "C" {

// The shape's type (TopAbs_ShapeEnum) and face count as seen through the offsets above (the bridge compares them with
// what FreeCAD reports); -1 for a null shape.
int mc_check(void* py, int* nfaces) {
    const TopoDS_Shape& s = shapeOf(py);
    if (s.IsNull()) return -1;
    TopTools_IndexedMapOfShape fm; TopExp::MapShapes(s, TopAbs_FACE, fm);
    *nfaces = fm.Extent();
    return int(s.ShapeType());
}

// Identity of the shape's geometry: its TShape (shapes sharing it are the same geometry at other places; OCC's
// IsPartner) and orientation.  0 for a null shape.
uintptr_t mc_tshape(void* py, int* orientation) {
    const TopoDS_Shape& s = shapeOf(py);
    if (s.IsNull()) return 0;
    *orientation = int(s.Orientation());
    return reinterpret_cast<uintptr_t>(s.TShape().get());
}

// Meshes [n] shapes at once on [threads] threads.  Shapes sharing a face or an edge with an earlier one (the mesh is
// stored in them) and large shapes (meshed with OCCT's own parallel mesher) are done one after the other.
void mc_mesh(void** py, int n, double tol, int threads) {
    std::vector<TopoDS_Shape> big, small;
    std::unordered_set<const void*> seen;
    for (int i = 0; i < n; i++) {
        TopoDS_Shape s = shapeOf(py[i]).Located(TopLoc_Location());
        if (s.IsNull() || meshed(s)) continue;
        bool shared = false; int faces = 0; std::vector<const void*> mine;
        for (TopAbs_ShapeEnum t : {TopAbs_FACE, TopAbs_EDGE})
            for (TopExp_Explorer x(s, t); x.More(); x.Next()) {
                const void* k = x.Current().TShape().get(); mine.push_back(k);
                if (seen.count(k)) shared = true;
                if (t == TopAbs_FACE) faces++;
            }
        seen.insert(mine.begin(), mine.end());
        (shared || faces >= 64 ? big : small).push_back(s);
    }
    std::atomic<size_t> next{0};
    auto work = [&] { for (size_t i; (i = next++) < small.size();) mesh(small[i], tol, false); };
    std::vector<std::thread> pool;
    for (int t = 1; t < std::max(1, threads); t++) pool.emplace_back(work);
    work();
    for (auto& t : pool) t.join();
    for (auto& s : big) mesh(s, tol, true);
}

// The shape's display mesh, its location stripped (the placement goes with each object), as the sections of the
// bridge's MCM2 layout: vertices (x y z nx ny nz), triangle indices, faces (id first count), edge points (GL_LINES
// pairs), edges (id first count), vertexes (x y z id).  malloc'd (mc_free), its size in [size]; null on failure.
void* mc_pack(void* py, double tol, int64_t* size) {
    try {
        TopoDS_Shape s = shapeOf(py).Located(TopLoc_Location());
        if (s.IsNull()) return nullptr;
        if (!meshed(s)) mesh(s, tol, true);
        TopTools_IndexedMapOfShape fm, em, vm;
        TopExp::MapShapes(s, TopAbs_FACE, fm); TopExp::MapShapes(s, TopAbs_EDGE, em); TopExp::MapShapes(s, TopAbs_VERTEX, vm);
        std::vector<float> V, L, P; std::vector<uint32_t> I; std::vector<int32_t> F, E;
        std::vector<std::vector<float>> edgePts(em.Extent() + 1);
        std::vector<char> done(em.Extent() + 1, 0);
        Poly_Connect pc;
        for (int fi = 1; fi <= fm.Extent(); fi++) {
            const TopoDS_Face& f = TopoDS::Face(fm(fi));
            TopLoc_Location loc;
            Handle(Poly_Triangulation) tri = BRep_Tool::Triangulation(f, loc);
            if (tri.IsNull() || tri->NbTriangles() == 0) continue;
            const bool ident = loc.IsIdentity(); const gp_Trsf tr = loc.Transformation();
            const bool rev = f.Orientation() == TopAbs_REVERSED;
            try { BRepLib_ToolTriangulatedShape::ComputeNormals(f, tri, pc); } catch (...) {}
            const uint32_t base = uint32_t(V.size() / 6);
            for (int k = 1; k <= tri->NbNodes(); k++) {
                gp_Pnt p = tri->Node(k); gp_Dir n = tri->HasNormals() ? tri->Normal(k) : gp_Dir(0, 0, 1);
                if (!ident) { p.Transform(tr); n.Transform(tr); }
                if (rev) n.Reverse();
                V.insert(V.end(), {float(p.X()), float(p.Y()), float(p.Z()), float(n.X()), float(n.Y()), float(n.Z())});
            }
            const int32_t first = int32_t(I.size());
            for (int k = 1; k <= tri->NbTriangles(); k++) {
                int a, b, c; tri->Triangle(k).Get(a, b, c);
                if (rev) std::swap(a, b);
                I.insert(I.end(), {base + a - 1, base + b - 1, base + c - 1});
            }
            F.insert(F.end(), {fi, first, int32_t(I.size()) - first});
            // the edges of this face not drawn yet: their polygons on its triangulation
            for (TopExp_Explorer x(f, TopAbs_EDGE); x.More(); x.Next()) {
                const TopoDS_Edge& e = TopoDS::Edge(x.Current());
                int ei = em.FindIndex(e);
                if (ei <= 0 || done[ei] || BRep_Tool::Degenerated(e)) continue;
                Handle(Poly_PolygonOnTriangulation) pol = BRep_Tool::PolygonOnTriangulation(e, tri, loc);
                if (pol.IsNull()) continue;
                auto& q = edgePts[ei];
                for (int k = 1; k <= pol->NbNodes(); k++) {
                    gp_Pnt p = tri->Node(pol->Node(k)); if (!ident) p.Transform(tr);
                    q.insert(q.end(), {float(p.X()), float(p.Y()), float(p.Z())});
                }
                done[ei] = 1;
            }
        }
        // edges without a polygon on a face (free edges, or none was made): their 3D polygon, else the curve discretized
        const double d = deflection(s, tol);
        for (int ei = 1; ei <= em.Extent(); ei++) {
            if (done[ei]) continue;
            const TopoDS_Edge& e = TopoDS::Edge(em(ei));
            if (BRep_Tool::Degenerated(e)) continue;
            auto& q = edgePts[ei];
            try {
                TopLoc_Location loc; Handle(Poly_Polygon3D) pol = BRep_Tool::Polygon3D(e, loc);
                if (!pol.IsNull()) {
                    for (int k = 1; k <= pol->NbNodes(); k++) { gp_Pnt p = pol->Nodes()(k); if (!loc.IsIdentity()) p.Transform(loc.Transformation());
                        q.insert(q.end(), {float(p.X()), float(p.Y()), float(p.Z())}); }
                } else {
                    BRepAdaptor_Curve c(e);
                    GCPnts_TangentialDeflection g(c, kAngle, d);
                    for (int k = 1; k <= g.NbPoints(); k++) { gp_Pnt p = g.Value(k); q.insert(q.end(), {float(p.X()), float(p.Y()), float(p.Z())}); }
                }
            } catch (...) { q.clear(); }
        }
        for (int ei = 1; ei <= em.Extent(); ei++) {
            const auto& q = edgePts[ei]; size_t np = q.size() / 3;
            if (np < 2) continue;
            const int32_t first = int32_t(L.size() / 3);
            for (size_t k = 0; k + 1 < np; k++) L.insert(L.end(), {q[3 * k], q[3 * k + 1], q[3 * k + 2], q[3 * k + 3], q[3 * k + 4], q[3 * k + 5]});
            E.insert(E.end(), {ei, first, int32_t(2 * (np - 1))});
        }
        for (int vi = 1; vi <= vm.Extent(); vi++) {
            gp_Pnt p = BRep_Tool::Pnt(TopoDS::Vertex(vm(vi)));
            P.insert(P.end(), {float(p.X()), float(p.Y()), float(p.Z()), float(vi)});
        }
        std::vector<char> out;
        out.reserve(4 * (V.size() + I.size() + F.size() + L.size() + E.size() + P.size()) + 24);
        put(out, V); put(out, I); put(out, F); put(out, L); put(out, E); put(out, P);
        void* r = std::malloc(out.size());
        if (!r) return nullptr;
        std::memcpy(r, out.data(), out.size()); *size = int64_t(out.size());
        return r;
    } catch (...) { return nullptr; }
}

void mc_free(void* p) { std::free(p); }

}
