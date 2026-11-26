#include "scene.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

namespace mcxr {

static bool readBlock(const uint8_t*& p, const uint8_t* end, std::vector<float>& out) {
    if (end - p < 4) return false;
    int32_t n; std::memcpy(&n, p, 4); p += 4;
    if (n < 0 || (size_t)(end - p) < (size_t)n * 4) return false;
    out.resize(n); std::memcpy(out.data(), p, (size_t)n * 4); p += (size_t)n * 4;
    return true;
}

void Geom::bounds() {
    for (size_t i = 0; i + 5 < vtx.size(); i += 6)
        for (int k = 0; k < 3; ++k) { float v = vtx[i + k]; if (std::isfinite(v)) { (&lo.x)[k] = std::min((&lo.x)[k], v); (&hi.x)[k] = std::max((&hi.x)[k], v); } }
}

bool Scene::addTessellation(const std::string& name, const uint8_t* data, size_t size, const float rgb[3]) {
    std::vector<float> tris, edges;
    const uint8_t* p = data; const uint8_t* end = data + size;
    if (!readBlock(p, end, tris) || !readBlock(p, end, edges)) return false;   // vertexes/colours that follow are not used in XR
    if (tris.empty() && edges.empty()) return false;
    auto g = std::make_shared<Geom>();
    for (size_t i = 0; i + 6 < tris.size(); i += 7) {               // corners come grouped by face
        int id = (int)tris[i + 6]; uint32_t v = (uint32_t)(i / 7);
        g->vtx.insert(g->vtx.end(), tris.begin() + i, tris.begin() + i + 6); g->idx.push_back(v);
        if (v % 3 == 0) { if (g->faces.empty() || g->faces[g->faces.size() - 3] != id) g->faces.insert(g->faces.end(), {id, (int)v, 0}); g->faces.back() += 3; }
    }
    for (size_t i = 0; i + 6 < edges.size(); i += 7) {              // segments come grouped by edge
        int id = (int)edges[i + 6];
        g->lines.insert(g->lines.end(), edges.begin() + i, edges.begin() + i + 6);
        if (g->edges.empty() || g->edges[g->edges.size() - 3] != id) g->edges.insert(g->edges.end(), {id, (int)(i / 7 * 2), 0});
        g->edges.back() += 2;
    }
    g->bounds();
    addMesh(name, g, nullptr, rgb, 1.f);
    return true;
}

void Scene::addMesh(const std::string& name, std::shared_ptr<Geom> g, const float* place, const float rgb[3], float alpha) {
    Mesh m; m.name = name; m.g = std::move(g); m.alpha = alpha;
    if (rgb) for (int k = 0; k < 3; ++k) m.color[k] = rgb[k];
    if (place) { std::memcpy(m.place.m, place, 64); M4 id = identity(); m.placed = std::memcmp(place, id.m, 64) != 0; }
    const Geom& G = *m.g;
    auto grow = [&](V3 w, V3& a, V3& b) { for (int k = 0; k < 3; ++k) { (&a.x)[k] = std::min((&a.x)[k], (&w.x)[k]); (&b.x)[k] = std::max((&b.x)[k], (&w.x)[k]); } };
    if (G.lo.x <= G.hi.x)
        for (int c = 0; c < 8; ++c) { V3 w = m.at({c & 1 ? G.hi.x : G.lo.x, c & 2 ? G.hi.y : G.lo.y, c & 4 ? G.hi.z : G.lo.z}); grow(w, m.lo, m.hi); grow(w, min, max); }
    for (size_t i = 0; i + 2 < G.lines.size(); i += 3) {           // wires alone (sketches, Draft lines) have extent too
        V3 w = m.at({G.lines[i], G.lines[i + 1], G.lines[i + 2]}); if (std::isfinite(w.x) && std::isfinite(w.y) && std::isfinite(w.z)) grow(w, min, max); }
    meshes.push_back(std::move(m)); ++version;
}

/** [p] in the coordinates of [m]'s mesh (the inverse of its placement: rotation, uniform scale, translation). */
static V3 toLocal(const Mesh& m, V3 p, bool point) {
    if (!m.placed) return p;
    const float* a = m.place.m;
    V3 q = point ? V3{p.x - a[12], p.y - a[13], p.z - a[14]} : p;
    V3 c0{a[0], a[1], a[2]}, c1{a[4], a[5], a[6]}, c2{a[8], a[9], a[10]};
    float s2 = dot(c0, c0); if (s2 < 1e-30f) return q;
    return V3{dot(c0, q), dot(c1, q), dot(c2, q)} * (1.f / s2);
}

/** The ray o + t d meets the box within [0, maxT] (slab test). */
static bool boxRay(V3 lo, V3 hi, V3 o, V3 d, float maxT) {
    float t0 = 0, t1 = maxT;
    for (int k = 0; k < 3; ++k) {
        float ok = (&o.x)[k], dk = (&d.x)[k], a = (&lo.x)[k], b = (&hi.x)[k];
        if (std::fabs(dk) < 1e-12f) { if (ok < a || ok > b) return false; continue; }
        float u = (a - ok) / dk, v = (b - ok) / dk; if (u > v) std::swap(u, v);
        t0 = std::max(t0, u); t1 = std::min(t1, v); if (t0 > t1) return false;
    }
    return true;
}

Hit Scene::pick(V3 o, V3 d, float maxT) const {
    Hit best; best.t = maxT;
    for (size_t mi = 0; mi < meshes.size(); ++mi) {
        const Mesh& m = meshes[mi]; if (!m.triIndices() || !boxRay(m.lo, m.hi, o, d, best.t)) continue;
        // in the mesh's coordinates: distances along the ray are the same (rigid placement)
        V3 lo = toLocal(m, o, true), ld = toLocal(m, d, false);
        const Geom& g = *m.g; const float* v = g.vtx.data();
        for (size_t f = 0; f + 2 < g.faces.size(); f += 3)
            for (int i = g.faces[f + 1], e = i + g.faces[f + 2]; i + 2 < e; i += 3) {   // Möller–Trumbore
                const float *pa = v + 6 * g.idx[i], *pb = v + 6 * g.idx[i + 1], *pc = v + 6 * g.idx[i + 2];
                V3 a{pa[0], pa[1], pa[2]}, b{pb[0], pb[1], pb[2]}, c{pc[0], pc[1], pc[2]};
                V3 e1 = b - a, e2 = c - a, h = cross(ld, e2); float det = dot(e1, h);
                if (std::fabs(det) < 1e-12f) continue;
                float fi = 1.f / det; V3 s = lo - a; float u = fi * dot(s, h); if (u < 0 || u > 1) continue;
                V3 q = cross(s, e1); float w = fi * dot(ld, q); if (w < 0 || u + w > 1) continue;
                float tt = fi * dot(e2, q);
                if (tt > 1e-6f && tt < best.t) {
                    best.t = tt; best.mesh = (int)mi; best.face = g.faces[f]; best.point = o + d * tt;
                    best.normal = norm(m.at(cross(e1, e2)) - m.at(V3{}));
                }
            }
    }
    if (best.mesh < 0) best.t = 0;
    return best;
}

Hit Scene::pickEdge(V3 o, V3 d, float tanTol, float maxT) const {
    Hit best; float bestScore = 1e30f;
    for (size_t mi = 0; mi < meshes.size(); ++mi) {
        const Mesh& m = meshes[mi]; if (!m.linePoints()) continue;
        const Geom& g = *m.g;
        for (size_t k = 0; k + 2 < g.edges.size(); k += 3)
            for (int j = g.edges[k + 1], e = j + g.edges[k + 2]; j + 1 < e; j += 2) {
                const float* L = g.lines.data() + 3 * j;
                V3 a = m.at({L[0], L[1], L[2]}), b = m.at({L[3], L[4], L[5]}), u = b - a;
                // closest points between the ray o + t d and the segment a + s u
                float uu = dot(u, u), ud = dot(u, d); V3 w = a - o;
                float denom = uu - ud * ud;                                   // |d| = 1
                float s = 0, t = 0;
                if (uu < 1e-12f) { s = 0; t = dot(w, d); }
                else if (std::fabs(denom) < 1e-9f) { s = 0; t = dot(w, d); }
                else { s = (ud * dot(w, d) - dot(w, u)) / denom; s = std::max(0.f, std::min(1.f, s)); t = dot(a + u * s - o, d); }
                if (t <= 1e-6f || t > maxT) continue;
                V3 p = a + u * s; float dist = len(p - (o + d * t));
                if (dist > tanTol * t) continue;
                float score = dist / (tanTol * t) + t * 1e-6f;                 // closest to the ray, then nearest
                if (score < bestScore) { bestScore = score; best.mesh = (int)mi; best.face = g.edges[k]; best.t = t; best.point = p; }
            }
    }
    return best;
}

bool Scene::loadBundle(const std::string& path) {
    FILE* f = std::fopen(path.c_str(), "rb"); if (!f) return false;
    std::vector<uint8_t> buf; uint8_t tmp[65536]; size_t n;
    while ((n = std::fread(tmp, 1, sizeof tmp, f)) > 0) buf.insert(buf.end(), tmp, tmp + n);
    std::fclose(f);
    const uint8_t* p = buf.data(); const uint8_t* end = p + buf.size();
    if (buf.size() < 9 || std::memcmp(p, "MCXR1", 5) != 0) return false;
    p += 5; uint32_t count; std::memcpy(&count, p, 4); p += 4;
    clear();
    size_t slash = path.find_last_of('/'); title = path.substr(slash == std::string::npos ? 0 : slash + 1);
    if (title.size() > 5 && title.substr(title.size() - 5) == ".mcxr") title.resize(title.size() - 5);
    for (uint32_t i = 0; i < count; ++i) {
        if (end - p < 2) return false;
        uint16_t nl; std::memcpy(&nl, p, 2); p += 2; if (end - p < nl + 16) return false;
        std::string name((const char*)p, nl); p += nl;
        float rgb[3]; std::memcpy(rgb, p, 12); p += 12;
        uint32_t sz; std::memcpy(&sz, p, 4); p += 4; if ((size_t)(end - p) < sz) return false;
        addTessellation(name, p, sz, rgb); p += sz;
    }
    return !meshes.empty();
}

}  // namespace mcxr
