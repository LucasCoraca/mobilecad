#include "hands.h"
#include <cmath>
#include "interaction.h"

namespace mcxr {

static V3 fwd(const Q& q) { return rotate(q, V3{0, 0, -1}); }
static float clamp01(float v) { return std::max(0.f, std::min(1.f, v)); }

V3 shoulderPoint(const Pose& head, bool left) {
    V3 f = fwd(head.rot); f.y = 0; f = len(f) > 1e-3f ? norm(f) : V3{0, 0, -1};
    V3 right = cross(f, V3{0, 1, 0});
    return head.pos + right * (left ? -0.17f : 0.17f) + V3{0, -0.2f, 0} + f * -0.03f;
}

// ---- gestures

/** The palm's normal out of its back (dorsal), from where the fingers' bases are. */
static V3 dorsal(const HandInput& h, bool left) {
    V3 w = h.joints[JWrist].pos, a = h.joints[JIndexProx].pos - w, b = h.joints[JLittleProx].pos - w;
    V3 n = norm(cross(a, b));                        // right hand: out of the palm
    return left ? n : n * -1.f;
}

void handGestures(HandInput& h, const Pose& head, bool left, HandGestures& g, float dt) {
    const Pose* j = h.joints;
    // pinch: the system's own (what its UI reacts to) when it gives it, else from the fingertips
    float dI = len(j[JThumbTip].pos - j[JIndexTip].pos), dM = len(j[JThumbTip].pos - j[JMiddleTip].pos);
    if (h.sysGestures) g.pinch = g.pinch ? h.sysPinch > 0.5f : h.sysPinch > 0.9f;
    else if (h.runtimeAim) g.pinch = g.pinch ? h.pinchIndex > 0.5f : h.pinchIndex > 0.9f;
    else g.pinch = g.pinch ? dI < 0.032f : dI < 0.018f;
    if (h.runtimeAim) g.middle = g.middle ? h.pinchMiddle > 0.5f : (h.pinchMiddle > 0.9f && !g.pinch);
    else g.middle = g.middle ? dM < 0.032f : (dM < 0.018f && dI > 0.03f);
    // grab: the system's grasp, else all four fingers curled into the palm (a pinch curls the others, never the index)
    bool closed;
    if (h.sysGestures) closed = g.fist ? h.sysGrasp > 0.5f : h.sysGrasp > 0.9f;
    else {
        float palmLen = std::max(len(j[JMiddleProx].pos - j[JWrist].pos), 0.04f), curl = 0;
        for (int tip : {JIndexTip, JMiddleTip, JRingTip, JLittleTip}) curl += len(j[tip].pos - j[JPalm].pos);
        curl /= 4 * palmLen;
        closed = g.fist ? curl < 0.85f : curl < 0.6f && dI > 0.025f;
    }
    // a pinch wins: a grab starts only once the hand has stayed closed, not pinching, for 0.15 s; held, it lasts until the
    // hand opens (the thumb touching the index inside a fist is no pinch)
    if (g.fist) g.fist = closed;
    else { g.fistT = closed && !g.pinch && !g.middle ? g.fistT + dt : 0; g.fist = g.fistT >= 0.15f; }
    if (g.fist) g.pinch = g.middle = false;
    // the palm turned towards the eyes: Quest's menu gesture (left hand); no ray then
    V3 n = dorsal(h, left), toHead = norm(head.pos - j[JPalm].pos);
    float facing = -dot(n, toHead);                  // > 0: the palm (not the back) faces the head
    g.palmUp = g.palmUp ? facing > 0.45f : facing > 0.7f;

    h.grip = Pose{j[JPalm].pos, j[JPalm].rot, 1};
    if (!h.runtimeAim) {
        // the ray: from the shoulder through the hand's knuckles, smoothed (fingers moving in a pinch hardly turn it)
        V3 o = j[JPalm].pos * 0.3f + j[JIndexProx].pos * 0.7f, d = norm(o - shoulderPoint(head, left));
        g.dir = g.init ? norm(g.dir * 0.55f + d * 0.45f) : d; g.init = true;
        h.aim = Pose{o, lookRot(g.dir), 1};
    }
    bool menuPose = left && (h.runtimeAim ? h.systemGesture || h.menuGesture : g.palmUp);
    h.select = g.pinch && !menuPose && !h.systemGesture ? 1.f : 0.f;
    h.squeeze = g.fist ? 1.f : 0.f;
    h.context = g.middle && !menuPose;
    h.menu = h.runtimeAim ? h.menuGesture : (menuPose && g.pinch);
    h.stickX = h.stickY = 0;
    h.noRay = menuPose;
}

// ---- a synthetic hand (tests, simulator)

namespace {
struct Finger { V3 meta, knuckle; float l1, l2, l3, r; };
// right hand, palm frame: -Z to the fingers, +Y the back of the hand, the thumb at -X
const Finger kFingers[4] = {
    {{-0.022f, 0, 0.035f}, {-0.025f, 0, -0.045f}, 0.040f, 0.025f, 0.022f, 0.0095f},
    {{-0.006f, 0, 0.036f}, {-0.006f, 0, -0.050f}, 0.044f, 0.028f, 0.024f, 0.0098f},
    {{0.010f, 0, 0.035f}, {0.011f, 0, -0.046f}, 0.040f, 0.026f, 0.023f, 0.0092f},
    {{0.022f, 0, 0.032f}, {0.027f, 0, -0.036f}, 0.032f, 0.020f, 0.020f, 0.0082f}};
}

void syntheticHand(HandInput& h, const Pose& palm, bool left, float pinch, float fist, float middlePinch, float rest) {
    float sx = left ? -1.f : 1.f;
    auto P = [&](V3 l) { return apply(Pose{palm.pos, palm.rot, 1}, V3{l.x * sx, l.y, l.z}); };
    V3 local[kHandJoints]; V3 dirs[kHandJoints]; float radius[kHandJoints];
    local[JPalm] = {0, 0, 0}; radius[JPalm] = 0.022f; local[JWrist] = {0, 0, 0.045f}; radius[JWrist] = 0.02f;
    for (int f = 0; f < 4; ++f) {
        const Finger& F = kFingers[f]; int b = JIndexMeta + f * 5;
        float c = clamp01(fist + (f == 0 ? 0.45f * pinch : f == 1 ? 0.45f * middlePinch + rest : rest));
        float a[3] = {c * 1.45f, c * 1.6f, c * 1.05f}, L[3] = {F.l1, F.l2, F.l3}, th = 0;
        local[b] = F.meta; local[b + 1] = F.knuckle;
        for (int k = 0; k < 3; ++k) { th += a[k]; local[b + 2 + k] = local[b + 1 + k] + V3{0, -std::sin(th), -std::cos(th)} * L[k]; }
        for (int k = 0; k < 5; ++k) radius[b + k] = F.r * (k == 0 ? 1.1f : 1.f - 0.08f * (k - 1));
    }
    // the thumb, reaching for the index (or middle) fingertip when pinching, over the curled fingers in a fist
    V3 tm{-0.020f, -0.012f, 0.025f}, tp{-0.040f, -0.018f, -0.005f}, tipOpen{-0.068f, -0.020f, -0.052f};
    V3 target = tipOpen; float k = 0;
    if (fist > 0.01f) { target = local[JIndexInter] + V3{0.004f, -0.014f, 0.004f}; k = fist; }
    else if (middlePinch > pinch) { target = local[JMiddleTip] + V3{-0.004f, -0.006f, 0}; k = middlePinch; }
    else if (pinch > 0) { target = local[JIndexTip] + V3{-0.004f, -0.006f, 0}; k = pinch; }
    V3 tip = lerp(tipOpen, target, k);
    local[JThumbMeta] = tm; local[JThumbProx] = tp; local[JThumbTip] = tip;
    local[JThumbDist] = lerp(tp, tip, 0.55f) + V3{-0.006f, -0.003f, 0};
    radius[JThumbMeta] = 0.012f; radius[JThumbProx] = 0.0105f; radius[JThumbDist] = 0.0095f; radius[JThumbTip] = 0.0085f;
    // orientations: -Z along the bone to the next joint, +Y towards the back of the hand
    for (int i = 0; i < kHandJoints; ++i) {
        int next = (i == JPalm) ? JMiddleProx : (i == JWrist) ? JPalm : (i == JThumbTip || (i >= JIndexMeta && (i - JIndexMeta) % 5 == 4)) ? -1 : i + 1;
        V3 d = next >= 0 ? local[next] - local[i] : local[i] - local[i - 1];
        dirs[i] = norm(d);
    }
    for (int i = 0; i < kHandJoints; ++i) {
        V3 z = dirs[i] * -1.f, up{0, 1, 0}, y = up - z * dot(up, z);
        if (len(y) < 0.2f) y = V3{0, 0, 1} - z * z.z;
        y = norm(y); V3 x = cross(y, z);
        V3 wx = rotate(palm.rot, V3{x.x * sx, x.y, x.z}), wy = rotate(palm.rot, V3{y.x * sx, y.y, y.z}), wz = rotate(palm.rot, V3{z.x * sx, z.y, z.z});
        if (left) wx = wx * -1.f;                                     // (mirrored: keep the basis right-handed)
        h.joints[i] = Pose{P(local[i]), fromBasis(wx, wy, wz), 1}; h.radii[i] = radius[i];
    }
    h.active = h.hand = true; h.runtimeAim = false;
}

Pose palmForRay(const Pose& head, bool left, V3 dir, float reach) {
    Q r = lookRot(dir); float sx = left ? -1.f : 1.f;
    V3 o = shoulderPoint(head, left) + norm(dir) * reach, knuckle = V3{kFingers[0].knuckle.x * sx, 0, kFingers[0].knuckle.z} * 0.7f;
    return Pose{o - rotate(r, knuckle), r, 1};
}

Pose palmForTip(bool left, V3 tip, V3 dir) {
    Q r = lookRot(dir); const Finger& F = kFingers[0];
    V3 t{F.knuckle.x * (left ? -1.f : 1.f), 0, F.knuckle.z - (F.l1 + F.l2 + F.l3)};
    return Pose{tip - rotate(r, t), r, 1};
}

const char* jointName(int j) {
    static const char* names[kHandJoints] = {"", "wrist",
        "thumb-metacarpal", "thumb-phalanx-proximal", "thumb-phalanx-distal", "thumb-tip",
        "index-finger-metacarpal", "index-finger-phalanx-proximal", "index-finger-phalanx-intermediate", "index-finger-phalanx-distal", "index-finger-tip",
        "middle-finger-metacarpal", "middle-finger-phalanx-proximal", "middle-finger-phalanx-intermediate", "middle-finger-phalanx-distal", "middle-finger-tip",
        "ring-finger-metacarpal", "ring-finger-phalanx-proximal", "ring-finger-phalanx-intermediate", "ring-finger-phalanx-distal", "ring-finger-tip",
        "pinky-finger-metacarpal", "pinky-finger-phalanx-proximal", "pinky-finger-phalanx-intermediate", "pinky-finger-phalanx-distal", "pinky-finger-tip"};
    return j >= 0 && j < kHandJoints ? names[j] : "";
}

}  // namespace mcxr
