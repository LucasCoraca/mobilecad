#include "scene.h"
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

bool Scene::addTessellation(const std::string& name, const uint8_t* data, size_t size, const float rgb[3]) {
    Mesh m; m.name = name;
    if (rgb) for (int k = 0; k < 3; ++k) m.color[k] = rgb[k];
    const uint8_t* p = data; const uint8_t* end = data + size;
    if (!readBlock(p, end, m.tris) || !readBlock(p, end, m.edges)) return false;   // vertexes/colours that follow are not used in XR
    if (m.tris.empty() && m.edges.empty()) return false;
    for (const auto* arr : {&m.tris, &m.edges})
        for (size_t i = 0; i + 6 < arr->size(); i += 7)
            for (int k = 0; k < 3; ++k) {
                float v = (*arr)[i + k]; if (!std::isfinite(v)) continue;
                (&min.x)[k] = std::min((&min.x)[k], v); (&max.x)[k] = std::max((&max.x)[k], v);
                if (arr == &m.edges) { float w = (*arr)[i + 3 + k]; if (std::isfinite(w)) { (&min.x)[k] = std::min((&min.x)[k], w); (&max.x)[k] = std::max((&max.x)[k], w); } }
            }
    for (size_t i = 0; i + 6 < m.tris.size(); i += 7)
        for (int k = 0; k < 3; ++k) { float v = m.tris[i + k]; if (std::isfinite(v)) { (&m.lo.x)[k] = std::min((&m.lo.x)[k], v); (&m.hi.x)[k] = std::max((&m.hi.x)[k], v); } }
    for (size_t v = 0; v < m.triVerts(); ++v) {                      // vertices come grouped by face
        int id = (int)m.tris[v * 7 + 6]; if (id < 0 || id > 1000000) continue;
        if ((int)m.faceFirst.size() <= id) { m.faceFirst.resize(id + 1, -1); m.faceCount.resize(id + 1, 0); }
        if (m.faceFirst[id] < 0) m.faceFirst[id] = (int)v;
        m.faceCount[id] = (int)v - m.faceFirst[id] + 1;
    }
    for (size_t sgm = 0; sgm < m.edgeSegments(); ++sgm) {           // segments come grouped by edge
        int id = (int)m.edges[sgm * 7 + 6]; if (id < 0 || id > 1000000) continue;
        if ((int)m.edgeFirst.size() <= id) { m.edgeFirst.resize(id + 1, -1); m.edgeCount.resize(id + 1, 0); }
        if (m.edgeFirst[id] < 0) m.edgeFirst[id] = (int)sgm;
        m.edgeCount[id] = (int)sgm - m.edgeFirst[id] + 1;
    }
    meshes.push_back(std::move(m)); ++version;
    return true;
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
        if (!boxRay(meshes[mi].lo, meshes[mi].hi, o, d, best.t)) continue;
        const auto& t = meshes[mi].tris;
        for (size_t i = 0; i + 20 < t.size(); i += 21) {               // Möller–Trumbore
            V3 a{t[i], t[i + 1], t[i + 2]}, b{t[i + 7], t[i + 8], t[i + 9]}, c{t[i + 14], t[i + 15], t[i + 16]};
            V3 e1 = b - a, e2 = c - a, h = cross(d, e2); float det = dot(e1, h);
            if (std::fabs(det) < 1e-12f) continue;
            float f = 1.f / det; V3 s = o - a; float u = f * dot(s, h); if (u < 0 || u > 1) continue;
            V3 q = cross(s, e1); float v = f * dot(d, q); if (v < 0 || u + v > 1) continue;
            float tt = f * dot(e2, q);
            if (tt > 1e-6f && tt < best.t) { best.t = tt; best.mesh = (int)mi; best.face = (int)t[i + 6]; best.point = o + d * tt; best.normal = norm(cross(e1, e2)); }
        }
    }
    if (best.mesh < 0) best.t = 0;
    return best;
}

Hit Scene::pickEdge(V3 o, V3 d, float tanTol, float maxT) const {
    Hit best; float bestScore = 1e30f;
    for (size_t mi = 0; mi < meshes.size(); ++mi) {
        const auto& e = meshes[mi].edges;
        for (size_t i = 0; i + 6 < e.size(); i += 7) {
            V3 a{e[i], e[i + 1], e[i + 2]}, b{e[i + 3], e[i + 4], e[i + 5]}, u = b - a;
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
            if (score < bestScore) { bestScore = score; best.mesh = (int)mi; best.face = (int)e[i + 6]; best.t = t; best.point = p; }
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

bool Scene::writeBundle(const std::string& path, const Scene& s) {
    FILE* f = std::fopen(path.c_str(), "wb"); if (!f) return false;
    std::fwrite("MCXR1", 1, 5, f); uint32_t c = (uint32_t)s.meshes.size(); std::fwrite(&c, 4, 1, f);
    for (const auto& m : s.meshes) {
        uint16_t nl = (uint16_t)m.name.size(); std::fwrite(&nl, 2, 1, f); std::fwrite(m.name.data(), 1, nl, f);
        std::fwrite(m.color, 4, 3, f);
        int32_t nt = (int32_t)m.tris.size(), ne = (int32_t)m.edges.size(); uint32_t sz = 8 + 4 * (nt + ne);
        std::fwrite(&sz, 4, 1, f); std::fwrite(&nt, 4, 1, f); std::fwrite(m.tris.data(), 4, nt, f);
        std::fwrite(&ne, 4, 1, f); std::fwrite(m.edges.data(), 4, ne, f);
    }
    std::fclose(f); return true;
}

}  // namespace mcxr
