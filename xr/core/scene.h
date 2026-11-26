// The CAD model shown in XR: one mesh per FreeCAD object (shared by objects with the same shape, each at its placement),
// as the engine tessellates it (mobilecad.bridge.tessellate), so faces/edges keep their FreeCAD ids (Face<i> / Edge<i>) for picking.
#pragma once
#include <cstdint>
#include <memory>
#include <string>
#include <vector>
#include "xmath.h"

namespace mcxr {

/** One shape's mesh, shared by every object showing it (an assembly's repeated parts), in its own coordinates. */
struct Geom {
    std::vector<float> vtx;       // x y z nx ny nz per vertex
    std::vector<uint32_t> idx;    // triangles
    std::vector<float> lines;     // edge points x y z, in GL_LINES pairs
    std::vector<int> faces;       // (FreeCAD face id, first index, index count) triples, grouped by face
    std::vector<int> edges;       // (FreeCAD edge id, first point, point count)
    V3 lo{1e30f, 1e30f, 1e30f}, hi{-1e30f, -1e30f, -1e30f};   // bounds of its triangles
    unsigned vao = 0, vbo = 0, ibo = 0, evao = 0, evbo = 0;   // GL objects, made by the renderer
    size_t triIndices() const { return idx.size(); }
    size_t linePoints() const { return lines.size() / 3; }
    void bounds();                // lo / hi from vtx
};

/** One FreeCAD object: a mesh at a placement. */
struct Mesh {
    std::string name;
    std::shared_ptr<Geom> g;
    M4 place = identity();      // scene coordinates = place * mesh coordinates
    bool placed = false;        // place is not the identity
    float color[3] = {0.81f, 0.84f, 0.86f};
    float alpha = 1;            // < 1: drawn see-through (transparent objects, a failed feature's last good shape)
    V3 lo{1e30f, 1e30f, 1e30f}, hi{-1e30f, -1e30f, -1e30f};   // bounds of its triangles in scene coordinates (picking skips meshes a ray misses)
    V3 at(V3 p) const { if (!placed) return p; const float* m = place.m;
        return {m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12], m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13], m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]}; }
    size_t triIndices() const { return g ? g->triIndices() : 0; }
    size_t linePoints() const { return g ? g->linePoints() : 0; }
};

struct Hit { int mesh = -1; int face = 0; float t = 0; V3 point, normal; };   // normal: of the triangle hit (unit, either side)
/** A picked element: FreeCAD Face<id> ('F') or Edge<id> ('E') of mesh `mesh`. */
struct ElemRef {
    int mesh = -1; char kind = 0; int id = 0;
    bool valid() const { return mesh >= 0 && id > 0; }
    bool operator==(const ElemRef& o) const { return mesh == o.mesh && kind == o.kind && id == o.id; }
};

struct Scene {
    std::string title;
    std::vector<Mesh> meshes;
    V3 min{1e30f, 1e30f, 1e30f}, max{-1e30f, -1e30f, -1e30f};
    unsigned version = 0;       // bumped on change; the renderer re-uploads

    /** Adds one object from a bundle's tessellation bytes: [int32 n][n floats tris: x y z nx ny nz faceId per corner]
     *  [int32 n][n floats edges: x1 y1 z1 x2 y2 z2 edgeId per segment] ... */
    bool addTessellation(const std::string& name, const uint8_t* data, size_t size, const float rgb[3]);
    /** Adds one object showing mesh [g] at [place] (16 floats, column major; null = identity). */
    void addMesh(const std::string& name, std::shared_ptr<Geom> g, const float* place, const float rgb[3], float alpha);
    void clear() { meshes.clear(); min = {1e30f, 1e30f, 1e30f}; max = {-1e30f, -1e30f, -1e30f}; ++version; }
    bool empty() const { return meshes.empty(); }
    V3 center() const { return (min + max) * 0.5f; }
    float radius() const { return len(max - min) * 0.5f; }
    /** Nearest face hit by a ray given in model coordinates (`dir` unit), before `maxT` (a segment). */
    Hit pick(V3 origin, V3 dir, float maxT = 1e30f) const;
    /** Nearest edge passing within `tanTol` * distance of the ray (an angular tolerance), and before `maxT`. */
    Hit pickEdge(V3 origin, V3 dir, float tanTol, float maxT) const;
    int find(const std::string& name) const { for (size_t i = 0; i < meshes.size(); ++i) if (meshes[i].name == name) return (int)i; return -1; }

    /** Scene bundle (.mcxr): "MCXR1" u32 count, then per object: u16 nameLen, name, 3 x f32 color, u32 size, tessellation. */
    bool loadBundle(const std::string& path);
};

}  // namespace mcxr
