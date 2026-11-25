// Small vector / quaternion / matrix helpers for the XR core (column-major 4x4 matrices, OpenGL conventions).
#pragma once
#include <cmath>
#include <algorithm>

namespace mcxr {

struct V3 { float x = 0, y = 0, z = 0; };
inline V3 v3(float x, float y, float z) { return {x, y, z}; }
inline V3 operator+(V3 a, V3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
inline V3 operator-(V3 a, V3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
inline V3 operator*(V3 a, float s) { return {a.x * s, a.y * s, a.z * s}; }
inline float dot(V3 a, V3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
inline V3 cross(V3 a, V3 b) { return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x}; }
inline float len(V3 a) { return std::sqrt(dot(a, a)); }
inline V3 norm(V3 a) { float l = len(a); return l > 1e-9f ? a * (1.f / l) : V3{0, 0, 0}; }

struct Q { float x = 0, y = 0, z = 0, w = 1; };
inline Q operator*(Q a, Q b) {
    return {a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y, a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w, a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z};
}
inline Q conj(Q q) { return {-q.x, -q.y, -q.z, q.w}; }
inline Q qnorm(Q q) { float l = std::sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w); return l > 1e-9f ? Q{q.x / l, q.y / l, q.z / l, q.w / l} : Q{}; }
inline V3 rotate(Q q, V3 v) { V3 u{q.x, q.y, q.z}; V3 t = cross(u, v) * 2.f; return v + t * q.w + cross(u, t); }
inline Q axisAngle(V3 axis, float a) { V3 n = norm(axis); float s = std::sin(a / 2); return {n.x * s, n.y * s, n.z * s, std::cos(a / 2)}; }

/** Rotation taking the unit axes to the orthonormal basis x, y, z (z = x cross y). */
inline Q fromBasis(V3 x, V3 y, V3 z) {
    float tr = x.x + y.y + z.z;
    if (tr > 0) { float s = std::sqrt(tr + 1) * 2; return qnorm(Q{(y.z - z.y) / s, (z.x - x.z) / s, (x.y - y.x) / s, s / 4}); }
    if (x.x > y.y && x.x > z.z) { float s = std::sqrt(1 + x.x - y.y - z.z) * 2; return qnorm(Q{s / 4, (y.x + x.y) / s, (z.x + x.z) / s, (y.z - z.y) / s}); }
    if (y.y > z.z) { float s = std::sqrt(1 + y.y - x.x - z.z) * 2; return qnorm(Q{(y.x + x.y) / s, s / 4, (z.y + y.z) / s, (z.x - x.z) / s}); }
    float s = std::sqrt(1 + z.z - x.x - y.y) * 2; return qnorm(Q{(z.x + x.z) / s, (z.y + y.z) / s, s / 4, (x.y - y.x) / s});
}

/** Orientation whose -Z points along `d`, +Y as close to world up as it can be. */
inline Q lookRot(V3 d) {
    V3 z = norm(d * -1.f), x = cross(V3{0, 1, 0}, z);
    x = len(x) < 1e-4f ? V3{1, 0, 0} : norm(x);
    return fromBasis(x, cross(z, x), z);
}
inline V3 lerp(V3 a, V3 b, float t) { return a + (b - a) * t; }

/** Rigid transform with uniform scale: p' = pos + rot * (scale * p). */
struct Pose { V3 pos; Q rot; float scale = 1; };
inline V3 apply(const Pose& t, V3 p) { return t.pos + rotate(t.rot, p * t.scale); }
inline Pose compose(const Pose& a, const Pose& b) {             // a ∘ b
    return {apply(a, b.pos), qnorm(a.rot * b.rot), a.scale * b.scale};
}
inline Pose inverse(const Pose& t) {
    Q ri = conj(t.rot); float si = 1.f / t.scale; return {rotate(ri, t.pos * -1.f) * si, ri, si};
}

struct M4 { float m[16]; };
inline M4 identity() { M4 r{}; r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1; return r; }
inline M4 mul(const M4& a, const M4& b) {
    M4 r{};
    for (int c = 0; c < 4; ++c) for (int rr = 0; rr < 4; ++rr) {
        float s = 0; for (int k = 0; k < 4; ++k) s += a.m[k * 4 + rr] * b.m[c * 4 + k]; r.m[c * 4 + rr] = s; }
    return r;
}
inline M4 fromPose(const Pose& t) {
    const Q& q = t.rot; float s = t.scale;
    float xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z, xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z, wx = q.w * q.x, wy = q.w * q.y, wz = q.w * q.z;
    M4 r{};
    r.m[0] = (1 - 2 * (yy + zz)) * s; r.m[1] = 2 * (xy + wz) * s; r.m[2] = 2 * (xz - wy) * s;
    r.m[4] = 2 * (xy - wz) * s; r.m[5] = (1 - 2 * (xx + zz)) * s; r.m[6] = 2 * (yz + wx) * s;
    r.m[8] = 2 * (xz + wy) * s; r.m[9] = 2 * (yz - wx) * s; r.m[10] = (1 - 2 * (xx + yy)) * s;
    r.m[12] = t.pos.x; r.m[13] = t.pos.y; r.m[14] = t.pos.z; r.m[15] = 1;
    return r;
}
/** View matrix of an eye at `eye` (world pose, scale 1). */
inline M4 viewFrom(const Pose& eye) { return fromPose(inverse(Pose{eye.pos, eye.rot, 1})); }
/** OpenXR asymmetric field of view (angles in radians) -> projection. */
inline M4 projectionFov(float left, float right, float up, float down, float n, float f) {
    float l = std::tan(left), r = std::tan(right), u = std::tan(up), d = std::tan(down);
    float w = r - l, h = u - d; M4 p{};
    p.m[0] = 2 / w; p.m[5] = 2 / h; p.m[8] = (r + l) / w; p.m[9] = (u + d) / h;
    p.m[10] = -(f + n) / (f - n); p.m[11] = -1; p.m[14] = -(2 * f * n) / (f - n);
    return p;
}

}  // namespace mcxr
