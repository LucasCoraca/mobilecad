#include "gltf_model.h"
#include <cmath>
#include <cstring>
#define CGLTF_IMPLEMENTATION
#include "third_party/cgltf.h"
#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_PNG
#define STBI_ONLY_JPEG
#define STBI_NO_STDIO
#include "third_party/stb_image.h"

namespace mcxr {

static V3 xform(const M4& m, V3 p, float w) {
    return {m.m[0] * p.x + m.m[4] * p.y + m.m[8] * p.z + m.m[12] * w, m.m[1] * p.x + m.m[5] * p.y + m.m[9] * p.z + m.m[13] * w,
            m.m[2] * p.x + m.m[6] * p.y + m.m[10] * p.z + m.m[14] * w};
}
/** Inverse of a 4x4 matrix (general: an inverse bind matrix may carry scale). */
static M4 invert(const M4& a) {
    const float* m = a.m; float inv[16];
    inv[0] = m[5]*m[10]*m[15] - m[5]*m[11]*m[14] - m[9]*m[6]*m[15] + m[9]*m[7]*m[14] + m[13]*m[6]*m[11] - m[13]*m[7]*m[10];
    inv[4] = -m[4]*m[10]*m[15] + m[4]*m[11]*m[14] + m[8]*m[6]*m[15] - m[8]*m[7]*m[14] - m[12]*m[6]*m[11] + m[12]*m[7]*m[10];
    inv[8] = m[4]*m[9]*m[15] - m[4]*m[11]*m[13] - m[8]*m[5]*m[15] + m[8]*m[7]*m[13] + m[12]*m[5]*m[11] - m[12]*m[7]*m[9];
    inv[12] = -m[4]*m[9]*m[14] + m[4]*m[10]*m[13] + m[8]*m[5]*m[14] - m[8]*m[6]*m[13] - m[12]*m[5]*m[10] + m[12]*m[6]*m[9];
    inv[1] = -m[1]*m[10]*m[15] + m[1]*m[11]*m[14] + m[9]*m[2]*m[15] - m[9]*m[3]*m[14] - m[13]*m[2]*m[11] + m[13]*m[3]*m[10];
    inv[5] = m[0]*m[10]*m[15] - m[0]*m[11]*m[14] - m[8]*m[2]*m[15] + m[8]*m[3]*m[14] + m[12]*m[2]*m[11] - m[12]*m[3]*m[10];
    inv[9] = -m[0]*m[9]*m[15] + m[0]*m[11]*m[13] + m[8]*m[1]*m[15] - m[8]*m[3]*m[13] - m[12]*m[1]*m[11] + m[12]*m[3]*m[9];
    inv[13] = m[0]*m[9]*m[14] - m[0]*m[10]*m[13] - m[8]*m[1]*m[14] + m[8]*m[2]*m[13] + m[12]*m[1]*m[10] - m[12]*m[2]*m[9];
    inv[2] = m[1]*m[6]*m[15] - m[1]*m[7]*m[14] - m[5]*m[2]*m[15] + m[5]*m[3]*m[14] + m[13]*m[2]*m[7] - m[13]*m[3]*m[6];
    inv[6] = -m[0]*m[6]*m[15] + m[0]*m[7]*m[14] + m[4]*m[2]*m[15] - m[4]*m[3]*m[14] - m[12]*m[2]*m[7] + m[12]*m[3]*m[6];
    inv[10] = m[0]*m[5]*m[15] - m[0]*m[7]*m[13] - m[4]*m[1]*m[15] + m[4]*m[3]*m[13] + m[12]*m[1]*m[7] - m[12]*m[3]*m[5];
    inv[14] = -m[0]*m[5]*m[14] + m[0]*m[6]*m[13] + m[4]*m[1]*m[14] - m[4]*m[2]*m[13] - m[12]*m[1]*m[6] + m[12]*m[2]*m[5];
    inv[3] = -m[1]*m[6]*m[11] + m[1]*m[7]*m[10] + m[5]*m[2]*m[11] - m[5]*m[3]*m[10] - m[9]*m[2]*m[7] + m[9]*m[3]*m[6];
    inv[7] = m[0]*m[6]*m[11] - m[0]*m[7]*m[10] - m[4]*m[2]*m[11] + m[4]*m[3]*m[10] + m[8]*m[2]*m[7] - m[8]*m[3]*m[6];
    inv[11] = -m[0]*m[5]*m[11] + m[0]*m[7]*m[9] + m[4]*m[1]*m[11] - m[4]*m[3]*m[9] - m[8]*m[1]*m[7] + m[8]*m[3]*m[5];
    inv[15] = m[0]*m[5]*m[10] - m[0]*m[6]*m[9] - m[4]*m[1]*m[10] + m[4]*m[2]*m[9] + m[8]*m[1]*m[6] - m[8]*m[2]*m[5];
    float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
    M4 r{}; if (std::fabs(det) < 1e-20f) return identity();
    for (int i = 0; i < 16; ++i) r.m[i] = inv[i] / det;
    return r;
}
static Q slerp(Q a, Q b, float t) {
    float d = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    if (d < 0) { b = {-b.x, -b.y, -b.z, -b.w}; d = -d; }
    if (d > 0.9995f) return qnorm(Q{a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, a.w + (b.w - a.w) * t});
    float th = std::acos(d), s = std::sin(th), wa = std::sin((1 - t) * th) / s, wb = std::sin(t * th) / s;
    return {a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb, a.w * wa + b.w * wb};
}

bool GltfModel::load(const std::vector<uint8_t>& glb, std::string* error) {
    auto fail = [&](const char* why) { if (error) *error = why; nodes.clear(); return false; };
    cgltf_options opt{}; cgltf_data* d = nullptr;
    if (cgltf_parse(&opt, glb.data(), glb.size(), &d) != cgltf_result_success) return fail("not a glTF file");
    if (cgltf_load_buffers(&opt, d, nullptr) != cgltf_result_success) { cgltf_free(d); return fail("glTF buffers missing"); }
    nodes.assign(d->nodes_count, Node{});
    for (size_t i = 0; i < d->nodes_count; ++i) {
        const cgltf_node& n = d->nodes[i]; Node& o = nodes[i];
        o.name = n.name ? n.name : "";
        if (n.parent) o.parent = (int)(n.parent - d->nodes);
        if (n.has_translation) o.t = {n.translation[0], n.translation[1], n.translation[2]};
        if (n.has_rotation) o.r = {n.rotation[0], n.rotation[1], n.rotation[2], n.rotation[3]};
        if (n.has_scale) o.s = {n.scale[0], n.scale[1], n.scale[2]};
        if (n.mesh) o.mesh = (int)(n.mesh - d->meshes);
    }
    meshes.assign(d->meshes_count, {});
    for (size_t m = 0; m < d->meshes_count; ++m) for (size_t p = 0; p < d->meshes[m].primitives_count; ++p) {
        const cgltf_primitive& pr = d->meshes[m].primitives[p];
        if (pr.type != cgltf_primitive_type_triangles) continue;
        const cgltf_accessor *pos = nullptr, *nrm = nullptr, *uv = nullptr, *jo = nullptr, *we = nullptr;
        for (size_t a = 0; a < pr.attributes_count; ++a) {
            const cgltf_attribute& at = pr.attributes[a];
            if (at.type == cgltf_attribute_type_position) pos = at.data;
            else if (at.type == cgltf_attribute_type_normal) nrm = at.data;
            else if (at.type == cgltf_attribute_type_texcoord && at.index == 0) uv = at.data;
            else if (at.type == cgltf_attribute_type_joints && at.index == 0) jo = at.data;
            else if (at.type == cgltf_attribute_type_weights && at.index == 0) we = at.data;
        }
        if (!pos) continue;
        // unindexed triangles, as the renderer draws them
        size_t n = pr.indices ? pr.indices->count : pos->count;
        Prim out; out.v.reserve(n * 8);
        bool skin = jo && we && meshes.size() && m == 0 && p == 0 && d->skins_count;
        for (size_t k = 0; k < n; ++k) {
            size_t i = pr.indices ? cgltf_accessor_read_index(pr.indices, k) : k;
            float v[8] = {0, 0, 0, 0, 0, 1, 0, 0};
            cgltf_accessor_read_float(pos, i, v, 3);
            if (nrm) cgltf_accessor_read_float(nrm, i, v + 3, 3);
            if (uv) cgltf_accessor_read_float(uv, i, v + 6, 2);
            out.v.insert(out.v.end(), v, v + 8);
            if (skin) {
                cgltf_uint j[4] = {0, 0, 0, 0}; float w[4] = {0, 0, 0, 0};
                cgltf_accessor_read_uint(jo, i, j, 4); cgltf_accessor_read_float(we, i, w, 4);
                for (int c = 0; c < 4; ++c) { vJoints.push_back((uint16_t)j[c]); vWeights.push_back(w[c]); }
            }
        }
        meshes[m].push_back(std::move(out));
    }
    if (d->skins_count) {
        const cgltf_skin& s = d->skins[0];
        for (size_t k = 0; k < s.joints_count; ++k) {
            skinJoints.push_back((int)(s.joints[k] - d->nodes));
            M4 ib = identity(); if (s.inverse_bind_matrices) cgltf_accessor_read_float(s.inverse_bind_matrices, k, ib.m, 16);
            inverseBind.push_back(ib);
        }
    }
    // a skinned hand ends in a cut-off forearm: each vertex's opacity, fading out over the 5 cm that end 1 cm before the wrist
    // (in the bind pose, along the wrist's +Z, which points up the arm)
    for (size_t k = 0; k < skinJoints.size(); ++k) {
        if (nodes[skinJoints[k]].name != "wrist" || meshes.empty() || meshes[0].empty()) continue;
        M4 wm = invert(inverseBind[k]); V3 wp{wm.m[12], wm.m[13], wm.m[14]}, wz = norm(V3{wm.m[8], wm.m[9], wm.m[10]});
        const std::vector<float>& v = meshes[0][0].v;
        for (size_t i = 0; i + 7 < v.size(); i += 8) {
            float s = dot(V3{v[i], v[i + 1], v[i + 2]} - wp, wz);
            fade.push_back(std::max(0.f, std::min(1.f, (-0.01f - s) / 0.05f)));
        }
    }
    // the base colour: a texture (the controllers) or a factor
    if (d->materials_count) {
        const cgltf_material& mt = d->materials[0];
        if (mt.has_pbr_metallic_roughness) {
            const float* f = mt.pbr_metallic_roughness.base_color_factor; baseColor = {f[0], f[1], f[2]};
            const cgltf_texture* t = mt.pbr_metallic_roughness.base_color_texture.texture;
            if (t && t->image && t->image->buffer_view) {
                const cgltf_buffer_view* bv = t->image->buffer_view;
                const uint8_t* src = (const uint8_t*)bv->buffer->data + bv->offset; int w, h, c;
                if (uint8_t* px = stbi_load_from_memory(src, (int)bv->size, &w, &h, &c, 4)) {
                    image.assign(px, px + (size_t)w * h * 4); imageW = w; imageH = h; stbi_image_free(px);
                }
            }
        }
    }
    cgltf_free(d);
    return !nodes.empty() ? true : fail("glTF without nodes");
}

M4 GltfModel::local(int i, const std::vector<Node>& ns) const {
    const Node& n = ns[i]; M4 m = fromPose(Pose{n.t, n.r, 1});
    for (int k = 0; k < 3; ++k) { m.m[k] *= n.s.x; m.m[4 + k] *= n.s.y; m.m[8 + k] *= n.s.z; }
    return m;
}
M4 GltfModel::world(int i, const std::vector<Node>& ns) const {
    M4 m = local(i, ns);
    for (int p = ns[i].parent; p >= 0; p = ns[p].parent) m = mul(local(p, ns), m);
    return m;
}

void GltfModel::prepare(Renderer& r) {
    for (auto& mesh : meshes) for (auto& p : mesh) if (!p.gpu.vao || !glIsVertexArray(p.gpu.vao)) r.uploadMesh(p.gpu, p.v, 1, 8);
    if (!image.empty() && (!tex || !glIsTexture(tex))) tex = r.makeTexture(imageW, imageH, image.data());
}

void GltfModel::draw(Renderer& r, const Pose& pose, const M4& vp, V3 eye, const std::function<float(const std::string&)>& value) {
    if (!loaded()) return;
    prepare(r);
    // visual responses: each value node between its min and max nodes
    std::vector<Node> ns = nodes;
    const std::string suffix = "_value";
    for (Node& n : ns) {
        if (n.name.size() <= suffix.size() || n.name.compare(n.name.size() - suffix.size(), suffix.size(), suffix)) continue;
        std::string base = n.name.substr(0, n.name.size() - suffix.size());
        const Node *lo = nullptr, *hi = nullptr;
        for (const Node& o : nodes) { if (o.name == base + "_min") lo = &o; else if (o.name == base + "_max") hi = &o; }
        if (!lo || !hi) continue;
        float t = std::max(0.f, std::min(1.f, value(base)));
        n.t = lerp(lo->t, hi->t, t); n.r = slerp(lo->r, hi->r, t); n.s = lerp(lo->s, hi->s, t);
    }
    M4 root = fromPose(Pose{pose.pos, pose.rot, 1});
    for (size_t i = 0; i < ns.size(); ++i) {
        if (ns[i].mesh < 0) continue;
        M4 m = mul(root, world((int)i, ns));
        for (auto& p : meshes[ns[i].mesh]) r.drawMesh(p.gpu, m, vp, eye, tex ? V3{1, 1, 1} : baseColor, 1.f, tex);
    }
}

void GltfModel::drawSkinned(Renderer& r, const M4& vp, V3 eye, const std::function<bool(const std::string&, Pose*)>& jointPose, V3 color, float alpha) {
    if (!loaded() || meshes.empty() || meshes[0].empty() || skinJoints.empty()) return;
    // each joint's matrix: its tracked pose (or, without one, where its parent's pose puts it), times its inverse bind
    std::vector<M4> jw(nodes.size(), identity()); std::vector<char> done(nodes.size(), 0);
    std::function<M4(int)> worldOf = [&](int i) -> M4 {
        if (done[i]) return jw[i];
        Pose p; M4 m = jointPose(nodes[i].name, &p) ? fromPose(p) : nodes[i].parent >= 0 ? mul(worldOf(nodes[i].parent), local(i, nodes)) : local(i, nodes);
        done[i] = 1; return jw[i] = m;
    };
    std::vector<M4> skin(skinJoints.size());
    for (size_t k = 0; k < skinJoints.size(); ++k) skin[k] = mul(worldOf(skinJoints[k]), inverseBind[k]);
    const std::vector<float>& src = meshes[0][0].v; size_t nv = src.size() / 8;
    skinned.resize(src.size());
    for (size_t v = 0; v < nv; ++v) {
        V3 p{src[v * 8], src[v * 8 + 1], src[v * 8 + 2]}, n{src[v * 8 + 3], src[v * 8 + 4], src[v * 8 + 5]}, op{}, on{};
        for (int c = 0; c < 4; ++c) {
            float w = vWeights.size() > v * 4 + c ? vWeights[v * 4 + c] : 0; if (w <= 0) continue;
            const M4& m = skin[std::min<size_t>(vJoints[v * 4 + c], skin.size() - 1)];
            op = op + xform(m, p, 1) * w; on = on + xform(m, n, 0) * w;
        }
        on = norm(on);
        float* o = &skinned[v * 8];
        o[0] = op.x; o[1] = op.y; o[2] = op.z; o[3] = on.x; o[4] = on.y; o[5] = on.z; o[6] = v < fade.size() ? fade[v] : 1.f; o[7] = 0;
    }
    Prim& p = meshes[0][0];
    r.uploadMesh(p.gpu, skinned, ++skinVersion, 8);
    // its depth first, then its colour on the nearest surface only: a see-through hand without its insides showing
    r.fadeUv = true;
    glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE); r.drawMesh(p.gpu, identity(), vp, eye, color, 1.f, 0);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    r.drawMesh(p.gpu, identity(), vp, eye, color, alpha, 0);
    r.fadeUv = false;
    // (the next prepare() must not mistake the skinned buffer for the bind pose: it only re-uploads a lost buffer)
}

}  // namespace mcxr
