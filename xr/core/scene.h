// The CAD model shown in XR: one mesh per FreeCAD object, in the engine's tessellation format (mobilecad.bridge.tessellate),
// so faces/edges keep their FreeCAD ids (Face<i> / Edge<i>) for picking.
#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include "xmath.h"

namespace mcxr {

struct Mesh {
    std::string name;
    std::vector<float> tris;    // x y z nx ny nz faceId   per vertex, 3 vertices per triangle
    std::vector<float> edges;   // x1 y1 z1 x2 y2 z2 edgeId   per segment (7 floats)
    float color[3] = {0.81f, 0.84f, 0.86f};
    float alpha = 1;            // < 1: drawn see-through (transparent objects, a failed feature's last good shape)
    std::vector<int> faceFirst, faceCount;         // per FreeCAD face id (1-based): its vertex range in tris
    std::vector<int> edgeFirst, edgeCount;         // per FreeCAD edge id (1-based): its segment range in edges
    V3 lo{1e30f, 1e30f, 1e30f}, hi{-1e30f, -1e30f, -1e30f};   // bounds of its triangles (picking skips meshes a ray misses)
    unsigned vao = 0, vbo = 0, evao = 0, evbo = 0;   // GL objects, made by the renderer
    size_t triVerts() const { return tris.size() / 7; }
    size_t edgeSegments() const { return edges.size() / 7; }
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

    /** Adds one object from the engine's tessellation bytes: [int32 n][n floats tris][int32 n][n floats edges] ... */
    bool addTessellation(const std::string& name, const uint8_t* data, size_t size, const float rgb[3]);
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
    static bool writeBundle(const std::string& path, const Scene& s);
};

}  // namespace mcxr
