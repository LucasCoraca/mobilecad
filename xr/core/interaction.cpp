#include "interaction.h"
#include <algorithm>
#include <cstdio>
#include "canvas.h"

namespace mcxr {

static V3 forward(const Pose& p) { return rotate(p.rot, V3{0, 0, -1}); }
static Pose unscaled(const Pose& p) { return Pose{p.pos, p.rot, 1}; }
static const float kTrueScale = 0.001f;              // scene mm -> world m
static const float kMenuBtn = 0.032f;                               // the menu button on the left controller (m)
static const float kBadgeW = 0.70f, kBadgeH = 0.06f; static const int kBadgePx = 2000;   // badge size (m), pixels per metre
static const int kSizeBtnW = 280, kRoomBtnW = 316, kWalkBtnW = 200;               // its buttons (px), from the right: size, room, walk
static const float kTpSpeed = 10.f, kSnapTurn = 0.5235988f;                       // teleport arc launch speed (m/s), snap turn (30°)
static Q facing(V3 from, V3 to) {                      // yaw-only rotation whose +Z points from `to` back to `from`
    V3 d = from - to; d.y = 0; if (len(d) < 1e-4f) d = {0, 0, 1};
    d = norm(d); return axisAngle({0, 1, 0}, std::atan2(d.x, d.z));
}

/** Ray against a w x h rectangle centred on `rect` (local X right, Y up): distance and 0..1 coordinates (v down). */
static bool rectRay(const Pose& aim, const Pose& rect, float w, float h, float* t, float* u, float* v, bool clamp) {
    V3 n = rotate(rect.rot, {0, 0, 1}), d = forward(aim);
    float den = dot(d, n); if (std::fabs(den) < 1e-5f) return false;
    float tt = dot(rect.pos - aim.pos, n) / den; if (tt <= 0) return false;
    V3 local = rotate(conj(rect.rot), aim.pos + d * tt - rect.pos);
    float uu = local.x / w + 0.5f, vv = 0.5f - local.y / h;
    if (!clamp && (uu < 0 || uu > 1 || vv < 0 || vv > 1)) return false;
    *t = tt; *u = std::max(0.f, std::min(1.f, uu)); *v = std::max(0.f, std::min(1.f, vv));
    return true;
}

void Interaction::placeModel(const Pose& head) {
    V3 f = forward(head); f.y = 0; f = len(f) > 1e-3f ? norm(f) : V3{0, 0, -1};
    V3 right = cross(f, V3{0, 1, 0});
    float yaw = std::atan2(-f.x, -f.z);
    // FreeCAD is Z-up, OpenXR is Y-up: turn the model upright, then face the viewer
    Q upright = axisAngle({1, 0, 0}, -1.5707963f);
    if (panel.sheets.empty()) {
        if (scene.empty()) return;
        // to the right of the UI panel, ~40 cm across
        float r = std::max(scene.radius(), 1e-3f);
        V3 target = head.pos + f * 0.75f + right * 0.32f + V3{0, -0.2f, 0};
        model = {V3{}, qnorm(axisAngle({0, 1, 0}, yaw) * upright), 0.2f / r};
        model.pos = target - apply(model, scene.center());
    } else {
        // the origin on a table in front of the viewer, below the tool bars, everything within ~28 cm of it
        model = {head.pos + f * 0.75f + V3{0, -0.42f, 0}, qnorm(axisAngle({0, 1, 0}, yaw) * upright), 1};
        fitScale();
    }
    placedEmpty = scene.empty(); placeHead = head; placed = true;
    hover[0] = hover[1] = {};
    if (!hostSelection) selected.clear();
    trueScale = false; fitSaved = false; walk = false; aiming[0] = aiming[1] = false; tpHand = -1; tpArc.clear();
}

void Interaction::fitScale() {
    // everything within ~28 cm of the origin, which stays put
    float reach = 100;
    if (!scene.empty()) {
        reach = 1e-3f;
        for (int k = 0; k < 8; ++k) reach = std::max(reach, len(V3{k & 1 ? scene.max.x : scene.min.x, k & 2 ? scene.max.y : scene.min.y, k & 4 ? scene.max.z : scene.min.z}));
    }
    model.scale = 0.28f / reach; trueScale = false; fitSaved = false;
}

float Interaction::plateRadius() const {
    float rxy = 0;
    if (!scene.empty()) for (int k = 0; k < 4; ++k)
        rxy = std::max(rxy, std::hypot(k & 1 ? scene.max.x : scene.min.x, k & 2 ? scene.max.y : scene.min.y));
    // 15 to 30 cm in the room: a parts table, not a floor (a part reaching past it overhangs; it stays clear of the bars).
    // At true size a small part keeps a small plate (from 5 cm), so it does not vanish on it.
    float sc = std::max(model.scale, 1e-9f), r = std::max(std::min(rxy * 1.15f, 0.30f / sc), (trueScale ? 0.05f : 0.15f) / sc);
    float g = 1; while (g * 10 < r / 8) g *= 10;                     // round up to whole grid cells (a stable size)
    float step = r / 8 <= g ? g : r / 8 <= 2 * g ? 2 * g : r / 8 <= 5 * g ? 5 * g : 10 * g;
    return std::ceil(r / step) * step;
}

float Interaction::plateGrid() const {
    float r = plateRadius(), g = 1; while (g * 10 < r / 8) g *= 10;
    return r / 8 <= g ? g : r / 8 <= 2 * g ? 2 * g : r / 8 <= 5 * g ? 5 * g : 10 * g;
}

Pose Interaction::platePose() const {                                 // on the XY plane at the origin, 2 mm under the model
    return {apply(model, V3{}) - rotate(model.rot, V3{0, 0, 0.002f}), model.rot, 1};
}

bool Interaction::rimRay(const Pose& aim, float* t) const {
    if (!plate) return false;
    float rw = plateRadius() * model.scale, u, v;
    if (!rectRay(aim, platePose(), 2 * rw, 2 * rw, t, &u, &v, false)) return false;
    float r = std::hypot(u - 0.5f, v - 0.5f) * 2 * rw;
    float band = std::max(0.1f * rw, 0.6f * plateGrid() * model.scale);   // as drawn; the handle is at least 2.5 cm wide
    return r <= rw && r >= rw - std::max(0.025f, band);
}

float Interaction::lowestY() const {
    float low = 1e30f;
    for (const Mesh& m : scene.meshes) for (size_t i = 0; i + 2 < m.tris.size(); i += 7)
        low = std::min(low, apply(model, V3{m.tris[i], m.tris[i + 1], m.tris[i + 2]}).y);
    return low;
}

void Interaction::setTrueScale(bool on) {
    if ((scene.empty() && !plate) || on == trueScale) return;
    trueScale = on;
    if (!on) { if (fitSaved) model = fitPose; fitSaved = false; return; }
    fitPose = model; fitSaved = true;
    V3 c = apply(model, plate ? V3{} : scene.center());               // scale about the build plate's centre (the model's)
    model.pos = c + (model.pos - c) * (kTrueScale / model.scale); model.scale = kTrueScale;
    if (scene.empty()) return;                                         // just the plate: it only changes size
    float r = scene.radius() * kTrueScale;
    if (r > 0.45f) {
        // bigger than arm's reach: stand it on the floor, far enough in front of the viewer to walk around it
        V3 d = c - viewer; d.y = 0; d = len(d) > 1e-3f ? norm(d) : V3{0, 0, -1};
        V3 nc = viewer + d * (r + 0.4f); nc.y = c.y;
        model.pos = model.pos + (nc - c);
        model.pos.y += floorY - lowestY();
    } else {
        if (r < 0.15f) {
            // small enough to hold: brought to ~40 cm in front of the eyes, a little below them, to look at closely
            V3 d = c - viewer; d.y = 0; d = len(d) > 1e-3f ? norm(d) : V3{0, 0, -1};
            V3 nc = viewer + d * 0.4f + V3{0, -0.18f, 0}, cm = apply(model, scene.center());
            model.pos = model.pos + (nc - cm);
        }
        float low = lowestY(); if (low < floorY) model.pos.y += floorY - low;    // never below the floor
    }
}

float Interaction::groundY() const { return apply(model, V3{}).y; }

void Interaction::level() {
    V3 x = rotate(model.rot, {1, 0, 0}); x.y = 0;
    float yaw = len(x) > 1e-3f ? std::atan2(-x.z, x.x) : 0.f;
    V3 c = scene.empty() ? V3{} : scene.center(), pivot = apply(model, c);
    model.rot = qnorm(axisAngle({0, 1, 0}, yaw) * axisAngle({1, 0, 0}, -1.5707963f));
    model.pos = pivot - rotate(model.rot, c * model.scale);
}

void Interaction::setWalk(bool on) {
    if (on == walk || (on && scene.empty())) return;
    aiming[0] = aiming[1] = false; tpHand = -1; tpArc.clear();
    if (!on) { walk = false; model = walkReturn; trueScale = walkTrue; if (!walkTrue) fitSaved = false; return; }
    walkReturn = model; walkTrue = trueScale; walk = true;
    if (!trueScale) setTrueScale(true);
    level();
    model.pos.y += floorY - groundY();                                 // its ground on the floor you stand on
}

void Interaction::teleportTo(V3 p) {
    V3 feet{headPose.pos.x, floorY, headPose.pos.z};                  // (the head, between the eyes)
    model.pos = model.pos + (feet - p);
}

void Interaction::teleportArc(const Pose& aim) {
    // a thrown ball's path from the controller: it lands on the ground plane, or on the first surface of the model it meets;
    // one may stand where the surface is level (a floor, a slab, a roof), not on a wall
    tpArc.clear(); tpValid = false;
    V3 p = aim.pos, v = forward(aim) * kTpSpeed; const V3 g{0, -9.81f, 0}; const float dt = 0.025f;
    float gy = groundY(); Pose inv = inverse(model);
    tpArc.push_back(p);
    for (int step = 0; step < 160; ++step) {
        V3 q = p + v * dt + g * (0.5f * dt * dt); v = v + g * dt;
        V3 a = apply(inv, p), b = apply(inv, q), d = b - a; float L = len(d);
        if (L > 1e-6f) {
            Hit h = scene.pick(a, d * (1.f / L), L);
            if (h.mesh >= 0) {
                V3 w = apply(model, h.point), n = norm(rotate(model.rot, h.normal));
                if (dot(n, q - p) > 0) n = n * -1.f;                       // the side the arc comes from
                tpArc.push_back(w); tpPoint = w; tpValid = n.y > 0.8f; return;
            }
        }
        if (p.y > gy && q.y <= gy) {
            V3 w = p + (q - p) * ((p.y - gy) / (p.y - q.y));
            tpArc.push_back(w); tpPoint = w; tpValid = true; return;
        }
        tpArc.push_back(q); p = q;
    }
}

void Interaction::walkStick(int i, const HandInput& h) {
    bool free = h.active && !onUi(i);
    if (aiming[i]) {
        if (!h.active || h.stickY < -0.6f) { aiming[i] = false; tpHand = -1; tpArc.clear(); }       // pulled back: cancel
        else if (std::fabs(h.stickY) < 0.3f && std::fabs(h.stickX) < 0.5f) {                        // let go: go there
            teleportArc(h.aim); if (tpValid) teleportTo(tpPoint);
            aiming[i] = false; tpHand = -1; tpArc.clear();
        } else teleportArc(h.aim);
    } else if (free && tpHand < 0 && h.stickY > 0.7f) { aiming[i] = true; tpHand = i; teleportArc(h.aim); }
    else if (free && snapReady[i] && std::fabs(h.stickX) > 0.7f && std::fabs(h.stickY) < 0.5f) {
        // snap turn about where you stand: stick right turns you right (the world the other way)
        Q turn = axisAngle({0, 1, 0}, h.stickX > 0 ? kSnapTurn : -kSnapTurn);
        V3 c{headPose.pos.x, 0, headPose.pos.z};
        model.rot = qnorm(turn * model.rot); model.pos = c + rotate(turn, model.pos - c);
        snapReady[i] = false;
    }
    if (std::fabs(h.stickX) < 0.3f) snapReady[i] = true;
}

std::string Interaction::scaleText() const {
    float k = model.scale / kTrueScale; char b[32];
    if (std::fabs(k - 1) < 0.005f) return "1:1";
    float v = k < 1 ? 1 / k : k;
    std::snprintf(b, sizeof b, v < 9.95f ? "%.1f" : "%.0f", v);
    std::string n = b; if (n.size() > 2 && n.compare(n.size() - 2, 2, ".0") == 0) n.resize(n.size() - 2);
    return k < 1 ? "1:" + n : n + ":1";
}

Pose Interaction::badgePose() const {                                  // hangs under the panel's (badge sheet's) right-hand corner
    Pose p{panel.pose.pos, panel.pose.rot, 1}; float w = panel.width, h = panel.height;
    if (panel.badgeSheet >= 0 && panel.badgeSheet < (int)panel.sheets.size()) { const Sheet& s = panel.sheets[panel.badgeSheet]; p = sheetPose(s); w = s.w / s.pxPerM; h = s.h / s.pxPerM; }
    p.pos = apply(p, V3{w / 2 - kBadgeW / 2, -h / 2 - 0.018f - kBadgeH / 2, 0});
    return p;
}

V3 Interaction::badgeButton(int which) const {
    float x = 16 + kSizeBtnW / 2.f;                                    // px from the badge's right edge
    if (which == 2) x = 32 + kSizeBtnW + (passthroughAvailable ? 16 + kRoomBtnW : 0) + kWalkBtnW / 2.f;
    return apply(badgePose(), V3{kBadgeW / 2 - x / kBadgePx, 0, 0});
}

int Interaction::badgeButtonAt(float u) const {
    float x = u * kBadgeW * kBadgePx, W = kBadgeW * kBadgePx;
    if (x >= W - 16 - kSizeBtnW && x <= W - 16) return 0;
    if (passthroughAvailable && x >= W - 32 - kSizeBtnW - kRoomBtnW && x <= W - 32 - kSizeBtnW) return 1;
    float w0 = W - 32 - kSizeBtnW - (passthroughAvailable ? 16 + kRoomBtnW : 0);   // walk: left of the others
    if (!scene.empty() && x >= w0 - kWalkBtnW && x <= w0) return 2;
    return -1;
}

void Interaction::placePanel(const Pose& head) {
    if (uiHidden) { uiHidden = false; panel.visible = true; }
    V3 f = forward(head); f.y = 0; f = len(f) > 1e-3f ? norm(f) : V3{0, 0, -1};
    if (!panel.sheets.empty()) {                                     // the sheets surround the viewer, each in its place
        panel.pose = {head.pos, facing(head.pos, head.pos + f), 1};
        for (Sheet& s : panel.sheets) s.local = s.home;
        return;
    }
    V3 right = cross(f, V3{0, 1, 0});
    V3 c = head.pos + f * 0.95f - right * 0.42f + V3{0, -0.12f, 0};
    panel.pose = {c, facing(head.pos, c), 1};
    // tilt back a little, like a drafting table
    panel.pose.rot = qnorm(panel.pose.rot * axisAngle({1, 0, 0}, -0.12f));
}

void Interaction::toggleUi(const Pose& head) {
    if (!uiHidden) { uiHidden = true; panel.visible = false; hiddenPopups = panel.popups.size(); return; }
    uiHidden = false; panel.visible = true;
    // back where you are looking, laid out as you left them (the windows you moved stay where you put them, around you)
    V3 f = forward(head); f.y = 0; f = len(f) > 1e-3f ? norm(f) : V3{0, 0, -1};
    if (!panel.sheets.empty()) panel.pose = {head.pos, facing(head.pos, head.pos + f), 1}; else placePanel(head);
}

Pose Interaction::menuButtonPose() const {                             // on top of the left controller, tilted towards you
    Pose p = unscaled(last[0].grip);
    p.pos = p.pos + rotate(p.rot, {0, 0.035f, 0.045f}); p.rot = qnorm(p.rot * axisAngle({1, 0, 0}, -0.9f));
    return p;
}

bool Interaction::menuButtonRay(const Pose& aim, float* t) const {
    float u, v; return last[0].active && !last[0].hand && rectRay(aim, menuButtonPose(), kMenuBtn, kMenuBtn, t, &u, &v, false);
}

bool Panel::solid(float px, float py) const {
    if (mask.empty()) return true;
    int cx = (int)(px / maskScale), cy = (int)(py / maskScale);
    for (int y = cy - 1; y <= cy + 1; ++y) for (int x = cx - 1; x <= cx + 1; ++x)
        if (x >= 0 && y >= 0 && x < maskW && y < maskH && mask[(size_t)y * maskW + x] > 24) return true;
    return false;
}

Sheet Panel::popupSheet(size_t i) const {
    Sheet p; p.x = popups[4 * i]; p.y = popups[4 * i + 1]; p.w = popups[4 * i + 2]; p.h = popups[4 * i + 3];
    int best = -1; float most = 0;
    for (int k = 0; k < (int)sheets.size(); ++k) {
        const Sheet& s = sheets[k];
        float ox = std::min(p.x + p.w, s.x + s.w) - std::max(p.x, s.x), oy = std::min(p.y + p.h, s.y + s.h) - std::max(p.y, s.y);
        if (ox > 0 && oy > 0 && ox * oy > most) { most = ox * oy; best = k; }
    }
    if (best < 0) return p;
    const Sheet& s = sheets[best]; p.pxPerM = s.pxPerM;
    // kept inside that sheet where it fits (a menu dropping from the bars lands at the top of the view area)
    float x = std::max(s.x, std::min(p.x, s.x + s.w - p.w)), y = std::max(s.y, std::min(p.y, s.y + s.h - p.h));
    if (p.w > s.w) x = s.x; if (p.h > s.h) y = s.y;                  // too big: from its top-left corner
    V3 off{((x + p.w / 2) - (s.x + s.w / 2)) / s.pxPerM, ((s.y + s.h / 2) - (y + p.h / 2)) / s.pxPerM, 0.012f * (1 + i)};
    p.local = compose(s.local, Pose{off, Q{}, 1});
    return p;
}

bool Interaction::panelRay(const Pose& aim, float* t, float* px, float* py, bool clamp, int* sheet) const {
    if (!panel.visible) return false;
    if (panel.sheets.empty()) {
        float u, v; if (!rectRay(aim, panel.pose, panel.width, panel.height, t, &u, &v, clamp)) return false;
        *px = u * panel.pxW; *py = v * panel.pxH;
        return true;
    }
    // a drag stays on the sheet it started on; otherwise the nearest sheet with something drawn where the ray meets it
    bool hit = false; int ns = (int)panel.sheets.size(), np = (int)panel.popups.size() / 4;
    auto inPopup = [&](float x, float y) { for (int k = 0; k < np; ++k) { const float* r = &panel.popups[4 * k];
        if (x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]) return true; } return false; };
    for (int i = 0; i < ns + np; ++i) {
        if (clamp && i != pointerSheet) continue;
        Sheet s = i < ns ? panel.sheets[i] : panel.popupSheet(i - ns); float tt, u, v;
        if (!rectRay(aim, sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM, &tt, &u, &v, clamp) || (hit && tt >= *t)) continue;
        float x = s.x + u * s.w, y = s.y + v * s.h;
        if (!clamp && (!panel.solid(x, y) || (i < ns && inPopup(x, y)))) continue;
        hit = true; *t = tt; *px = x; *py = y; if (sheet) *sheet = i;
    }
    return hit;
}

void Interaction::panelChanged(Renderer& r) {
    uiDirty = true;
    if (!uiLayers && !panel.sheets.empty() && panel.tex) panel.sharp = r.mipmappedCopy(panel.tex, panel.external, panel.pxW, panel.pxH, panel.sharp);
}

std::vector<Interaction::UiQuad> Interaction::uiQuads() const {
    std::vector<UiQuad> out;
    if (!panel.visible) return out;
    for (const Sheet& s : panel.sheets) out.push_back({sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM, s.x, s.y, s.w, s.h, false});
    for (size_t k = 0; k < panel.popups.size() / 4; ++k) { Sheet s = panel.popupSheet(k);
        out.push_back({sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM, s.x, s.y, s.w, s.h, true}); }
    return out;
}

std::vector<V3> Interaction::uiCursors() const {
    std::vector<V3> out;
    for (int i = 0; i < 2; ++i) if (last[i].active && target[i].panel) out.push_back(last[i].aim.pos + forward(last[i].aim) * target[i].t);
    return out;
}

void Interaction::updateMask(Renderer& r) {
    if (!panel.seeThrough || !panel.tex) return;
    int w = panel.pxW / Panel::maskScale, h = panel.pxH / Panel::maskScale; std::vector<uint8_t> m;
    r.readAlpha(panel.tex, panel.external, w, h, m);
    // an image with nothing drawn at all is the UI's window hidden for a moment (another activity in front, a file picker),
    // never the UI itself: keep the last mask, or the rays would pass through every window until the UI redraws
    if (std::none_of(m.begin(), m.end(), [](uint8_t a) { return a > 24; })) return;
    panel.maskW = w; panel.maskH = h; panel.mask = std::move(m);
}

bool Interaction::badgeRay(const Pose& aim, float* t, float* u) const {
    float v; return panel.visible && (!scene.empty() || plate) && rectRay(aim, badgePose(), kBadgeW, kBadgeH, t, u, &v, false);
}

Interaction::Target Interaction::aimAt(const Pose& aim) const {
    Target out;
    if (!scene.empty()) {
        Pose inv = inverse(model);
        V3 o = apply(inv, aim.pos), d = norm(rotate(inv.rot, forward(aim)));
        Hit f = scene.pick(o, d);
        float maxT = f.mesh >= 0 ? f.t * 1.01f + 1e-3f / model.scale : 1e30f;
        Hit e = scene.pickEdge(o, d, 0.012f, maxT);                     // edges win within ~0.7 degrees
        Hit h = e.mesh >= 0 ? e : f;
        if (h.mesh >= 0) { out.t = h.t * model.scale; out.elem = {h.mesh, e.mesh >= 0 ? 'E' : 'F', h.face}; out.point = h.point; }
    }
    float t, px, py;
    int sh = -1;
    // the nearer wins: a window behind a part of the model is hidden by it (renderOverUi), so the ray hits the part
    if (panelRay(aim, &t, &px, &py, false, &sh) && t < out.t) { out = {}; out.panel = true; out.t = t; out.px = px; out.py = py; out.sheet = sh; }
    if (badgeRay(aim, &t, &px) && t < out.t) { out = {}; out.badge = true; out.t = t; out.px = px; }
    if (rimRay(aim, &t) && t < out.t) { out = {}; out.rim = true; out.t = t; }
    return out;
}

void Interaction::modelRay(const Pose& aim, V3* o, V3* d) const {
    Pose inv = inverse(model); *o = apply(inv, aim.pos); *d = norm(rotate(inv.rot, forward(aim)));
}

float Interaction::depthMm(int hand, const Pose& aim) const {
    if (target[hand].elem.valid()) return target[hand].t / model.scale;
    V3 o, d; modelRay(aim, &o, &d); return std::max(len(scene.center() - o), 1.f);
}

bool Interaction::pokeAt(V3 tip, float* depth, Pose* aim) const {
    // the window (popups first: they are in front) whose face the fingertip is at, from 4 cm behind it to 5 cm in front
    if (!panel.visible) return false;
    auto at = [&](const Pose& p, float w, float h) {
        V3 l = rotate(conj(p.rot), tip - p.pos);
        if (std::fabs(l.x) > w / 2 || std::fabs(l.y) > h / 2 || l.z < -0.04f || l.z > 0.05f) return false;
        V3 n = rotate(p.rot, {0, 0, 1});
        *depth = l.z; *aim = Pose{tip + n * 0.08f, lookRot(n * -1.f), 1};
        return true;
    };
    if (panel.sheets.empty()) return at(panel.pose, panel.width, panel.height);
    for (size_t k = panel.popups.size() / 4; k-- > 0;) { Sheet s = panel.popupSheet(k); if (at(sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM)) return true; }
    float best = 1e9f; Pose a; float d;
    for (const Sheet& s : panel.sheets) if (at(sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM) && std::fabs(*depth) < best) { best = std::fabs(*depth); a = *aim; d = *depth; }
    if (best > 1e8f) return false;
    *aim = a; *depth = d; return true;
}

void Interaction::handInput(HandInput hands[2], float dt) {
    for (int i = 0; i < 2; ++i) {
        HandInput& h = hands[i];
        if (!h.active || !h.hand) { pokeTouch[i] = false; pokeDepth[i] = 1; continue; }
        handGestures(h, headPose, i == 0, gest[i], dt);
        // the index finger at a window: it touches it (a press when it reaches the surface coming from the front), no ray
        float d; Pose aim;
        bool near = pointerHand != i || pokeTouch[i] ? pokeAt(h.joints[JIndexTip].pos, &d, &aim) : false;
        if (near && !gest[i].pinch && !gest[i].fist) {
            if (pokeTouch[i]) pokeTouch[i] = d < 0.012f;
            else if (d < 0.006f && pokeDepth[i] >= 0.006f && aimAt(aim).panel) pokeTouch[i] = true;
            pokeDepth[i] = d;
            h.aim = aim; h.select = pokeTouch[i] ? 1.f : 0.f; h.poke = true; h.context = false;
        } else { pokeTouch[i] = false; pokeDepth[i] = near ? d : 1; }
    }
    // both hands pinching away from the UI: pinch to zoom, a grab with both hands (scale and turn between them). A touch
    // the first hand started on the app's view is called off.
    bool both = hands[0].active && hands[1].active && hands[0].hand && hands[1].hand && !hands[0].poke && !hands[1].poke;
    bool p0 = hands[0].select > 0.55f, p1 = hands[1].select > 0.55f;
    if (!pinchZoom && both && p0 && p1 && pointerHand < 0 && !onUi(0) && !onUi(1)) {
        pinchZoom = true;
        if (viewHand >= 0 && onViewport) { onViewport(5, V3{}, V3{}, 0); viewHand = -1; }
        triggerOnModel[0] = triggerOnModel[1] = false;
    }
    if (pinchZoom && !p0 && !p1) pinchZoom = false;
    if (pinchZoom) for (int i = 0; i < 2; ++i) { if (hands[i].select > 0.55f) hands[i].squeeze = 1; hands[i].select = 0; }
}

void Interaction::update(const HandInput in[2], float dt) {
    HandInput hands[2] = {in[0], in[1]};
    // hidden windows come back for a dialog or menu that opens (a context menu, a value to type): it is waiting for you
    if (uiHidden && panel.popups.size() > hiddenPopups) toggleUi(headPose);
    if (uiHidden) hiddenPopups = std::min(hiddenPopups, panel.popups.size());
    handInput(hands, dt);
    if (placedEmpty && !scene.empty()) placeModel(placeHead);
    bool trig[2], squeeze[2], host = (bool)onViewport;
    for (int i = 0; i < 2; ++i) {
        const HandInput& h = hands[i];
        trig[i] = h.active && h.select > 0.55f; squeeze[i] = h.active && h.squeeze > 0.55f;
        target[i] = h.active && !h.noRay ? aimAt(h.aim) : Target{};
        // the menu button on the left controller, for the other hand's ray
        float wt; if (i == 1 && h.active && !h.noRay && menuButtonRay(h.aim, &wt) && wt < target[i].t) { target[i] = {}; target[i].menuButton = true; target[i].t = wt; }
        hover[i] = (target[i].panel || host) ? ElemRef{} : target[i].elem;
        if (h.active && h.menu && !prevMenu[i] && onMenu) onMenu();
        prevMenu[i] = h.active && h.menu;
        if (h.active && h.context && !prevStick[i] && onContextMenu) onContextMenu();
        prevStick[i] = h.active && h.context;
    }
    // ---- the app's view: a pressed trigger off the panel is a finger on it, a ray pointing into the scene a hovering mouse
    if (host) {
        if (viewHand >= 0) {
            int i = viewHand; V3 o, d; modelRay(hands[i].aim, &o, &d);
            if (trig[i] && hands[i].active) onViewport(1, o, d, 0);
            else { onViewport(2, o, d, 0); viewHand = -1; }
        }
        if (viewHand < 0 && pointerHand < 0) for (int i : {1, 0})
            if (trig[i] && !prevTrig[i] && !onUi(i) && !target[i].rim && !grab[i].on) {
                V3 o, d; modelRay(hands[i].aim, &o, &d); viewHand = i; viewHover = false;
                onViewport(0, o, d, depthMm(i, hands[i].aim)); break;
            }
        int hv = -1;
        if (viewHand < 0) for (int i : {1, 0}) if (hands[i].active && !trig[i] && !onUi(i) && !target[i].rim && !grab[i].on) { hv = i; break; }
        if (hv >= 0) { V3 o, d; modelRay(hands[hv].aim, &o, &d); onViewport(3, o, d, depthMm(hv, hands[hv].aim)); viewHover = true; }
        else if (viewHover && viewHand < 0) { onViewport(4, V3{}, V3{}, 0); viewHover = false; }
    }
    // ---- the panel: one touch pointer, owned by the hand that pressed
    if (pointerHand >= 0) {
        int i = pointerHand; float t, px, py;
        if (!trig[i] || !hands[i].active) { if (onPanelPointer) onPanelPointer(PanelUp, hoverX, hoverY); pointerHand = -1; }
        else if (panelRay(hands[i].aim, &t, &px, &py, true, &pointerSheet)) { hoverX = px; hoverY = py; if (onPanelPointer) onPanelPointer(PanelMove, px, py); }
    }
    if (pointerHand < 0) {
        for (int i : {1, 0}) if (trig[i] && !prevTrig[i] && target[i].panel && !grab[i].on) {
            pointerHand = i; hoverX = target[i].px; hoverY = target[i].py; pointerSheet = target[i].sheet;
            if (onPanelPointer) onPanelPointer(PanelDown, hoverX, hoverY);
            break;
        }
    }
    int hv = pointerHand >= 0 ? pointerHand : (target[1].panel ? 1 : target[0].panel ? 0 : -1);
    if (pointerHand < 0) {
        if (hv >= 0) {
            if (hv != hoverHand || std::fabs(target[hv].px - hoverX) + std::fabs(target[hv].py - hoverY) > 0.5f) {
                hoverX = target[hv].px; hoverY = target[hv].py; if (onPanelPointer) onPanelPointer(PanelHover, hoverX, hoverY);
            }
            if (std::fabs(hands[hv].stickY) > 0.2f && onPanelPointer) onPanelPointer(PanelScroll, hoverX, hands[hv].stickY * dt * 12.f);
        } else if (hoverHand >= 0 && onPanelPointer) onPanelPointer(PanelLeave, hoverX, hoverY);
    }
    hoverHand = hv;

    // ---- grabs: grip anywhere on the model / panel, or trigger held on the model
    for (int i = 0; i < 2; ++i) {
        const HandInput& h = hands[i];
        bool trigStart = trig[i] && !prevTrig[i], sqStart = squeeze[i] && !prevSqueeze[i];
        if (trigStart && pointerHand != i) {
            triggerT[i] = 0; triggerStart[i] = h.aim.pos;
            triggerOnModel[i] = !onUi(i); triggerOnBadge[i] = target[i].badge; triggerOnMenuButton[i] = target[i].menuButton;
        }
        if (trig[i]) triggerT[i] += dt;
        if (!grab[i].on) {
            if (sqStart && (target[i].panel || target[i].badge)) {
                // grip on a window moves that window (a menu / dialog, or the scale badge: all of them)
                int sh = target[i].panel && target[i].sheet < (int)panel.sheets.size() ? target[i].sheet : -1;
                grab[i] = {true, unscaled(h.grip), sh >= 0 ? sheetPose(panel.sheets[sh]) : panel.pose, 0, h.aim.pos, true};
                grab[i].sheet = sh;
            }
            else if ((sqStart || (trigStart && pointerHand != i)) && target[i].rim) {
                grab[i] = {true, unscaled(h.grip), model, 0, h.aim.pos, false, true};
                if (!sqStart) { grab[i].slide = true; grab[i].slideStart = h.aim.pos + forward(h.aim) * target[i].t; }
            }
            else if (sqStart || (!host && trigStart && pointerHand != i && target[i].elem.valid()))
                grab[i] = {true, unscaled(h.grip), model, 0, h.aim.pos, false};
        }
        bool holding = grab[i].plate ? (squeeze[i] || trig[i]) : (grab[i].panel || host) ? squeeze[i] : (squeeze[i] || trig[i]);
        if (grab[i].on && !holding) grab[i].on = false;
        // a short trigger press that did not move = click: pick what the ray is on
        if (!host && !trig[i] && prevTrig[i] && triggerOnModel[i] && triggerT[i] < 0.35f && len(h.aim.pos - triggerStart[i]) < 0.02f) {
            ElemRef e = target[i].panel ? ElemRef{} : target[i].elem;
            if (onPick) onPick(e, target[i].point);
            if (!hostSelection) {
                auto it = std::find(selected.begin(), selected.end(), e);
                if (!e.valid()) selected.clear(); else if (it != selected.end()) selected.erase(it); else selected.push_back(e);
            }
            triggerOnModel[i] = false;
        }
        if (!trig[i] && prevTrig[i] && triggerOnBadge[i]) {
            int b = target[i].badge ? badgeButtonAt(target[i].px) : -1;
            if (b == 0) { if (walk) { setWalk(false); if (trueScale) setTrueScale(false); } else setTrueScale(!trueScale); }   // FIT VIEW leaves walking too
            else if (b == 1) passthrough = !passthrough;
            else if (b == 2) setWalk(!walk);
            triggerOnBadge[i] = false;
        }
        if (!trig[i] && prevTrig[i] && triggerOnMenuButton[i]) { if (target[i].menuButton) toggleUi(headPose); triggerOnMenuButton[i] = false; }
        prevTrig[i] = trig[i]; prevSqueeze[i] = squeeze[i];
        // thumbstick, when not scrolling the panel: scale and turn the model about its centre (walking: teleport, snap turn)
        if (walk) walkStick(i, h);
        else if (h.active && !onUi(i) && (std::fabs(h.stickX) > 0.2f || std::fabs(h.stickY) > 0.2f) && (!scene.empty() || plate)) {
            V3 c = apply(model, plate ? V3{} : scene.center());
            // eased: a light push zooms / turns slowly, a full one quickly
            float sy = h.stickY * std::fabs(h.stickY), sx = h.stickX * std::fabs(h.stickX);
            float s = trueScale ? 1.f : std::exp(sy * dt * 1.6f); Q turn = axisAngle({0, 1, 0}, -sx * dt * 2.0f);
            model.rot = qnorm(turn * model.rot); model.scale *= s; model.pos = c + rotate(turn, (model.pos - c) * s);
        }
    }
    bool modelGrab[2] = {grab[0].on && !grab[0].panel, grab[1].on && !grab[1].panel};
    if (modelGrab[0] && modelGrab[1]) {
        V3 a = hands[0].grip.pos, b = hands[1].grip.pos, mid = (a + b) * 0.5f, dir = b - a; dir.y = 0;
        if (!twoHand) { twoHand = true; twoDist = std::max(len(b - a), 1e-3f); twoModel = model; twoMid = mid; twoDir = norm(dir); }
        float s = len(b - a) / twoDist; V3 nd = norm(dir);
        Q turn = axisAngle({0, 1, 0}, std::atan2(cross(twoDir, nd).y, dot(twoDir, nd)));
        if (trueScale) s = 1;
        else if (std::fabs(std::log(twoModel.scale * s / kTrueScale)) < 0.02f) s = kTrueScale / twoModel.scale;   // snap to 1:1
        model.rot = qnorm(turn * twoModel.rot); model.scale = twoModel.scale * s; model.pos = mid + rotate(turn, (twoModel.pos - twoMid) * s);
        triggerOnModel[0] = triggerOnModel[1] = false;                   // not a click
    } else {
        if (twoHand) {                                                 // re-anchor the remaining hand
            for (int i = 0; i < 2; ++i) if (modelGrab[i]) grab[i] = {true, unscaled(hands[i].grip), model, 1, hands[i].aim.pos, false};
            twoHand = false;
        }
        for (int i = 0; i < 2; ++i) if (grab[i].on) {
            Pose delta = compose(unscaled(hands[i].grip), inverse(grab[i].handStart));
            if (grab[i].panel && grab[i].sheet >= 0 && grab[i].sheet < (int)panel.sheets.size()) {
                Pose w = compose(delta, grab[i].objStart); w.scale = 1;
                panel.sheets[grab[i].sheet].local = compose(inverse(Pose{panel.pose.pos, panel.pose.rot, 1}), w);
            } else if (grab[i].panel) { panel.pose = compose(delta, grab[i].objStart); panel.pose.scale = 1; }
            else if (grab[i].plate && grab[i].slide) {
                // slid by the ray: the point it grabbed follows the ray across the horizontal plane it was on
                V3 o = hands[i].aim.pos, d = forward(hands[i].aim); float y = grab[i].slideStart.y;
                if (std::fabs(d.y) > 1e-3f) {
                    float tt = (y - o.y) / d.y;
                    if (tt > 0 && tt < 20) model.pos = grab[i].objStart.pos + (o + d * tt - grab[i].slideStart);
                }
            } else if (grab[i].plate) {
                // carried by its rim: it follows the hand and turns with the wrist about it, but stays level
                auto yaw = [](const Q& q) { V3 x = rotate(q, {1, 0, 0}); return std::atan2(-x.z, x.x); };
                Q turn = axisAngle({0, 1, 0}, yaw(hands[i].grip.rot) - yaw(grab[i].handStart.rot));
                const Pose& s0 = grab[i].objStart;
                model.rot = qnorm(turn * s0.rot); model.scale = s0.scale;
                model.pos = hands[i].grip.pos + rotate(turn, s0.pos - grab[i].handStart.pos);
            }
            else model = compose(delta, grab[i].objStart);
        }
    }
    badgeHot = target[1].badge ? badgeButtonAt(target[1].px) : target[0].badge ? badgeButtonAt(target[0].px) : -1;
    rimHot = target[0].rim || target[1].rim || (grab[0].on && grab[0].plate) || (grab[1].on && grab[1].plate);
    last[0] = hands[0]; last[1] = hands[1];
}

void Interaction::drawPlate(Renderer& r, const M4& vp) {
    // the build plate: a see-through disc with a grid (bolder every 5 cells) and a rim, then the axes from the origin.
    // The texture depends only on how many cells reach the rim (and the rim's highlight), so zooming reuses a few.
    float R = plateRadius(), g = plateGrid(); int cells = (int)std::lround(R / g);
    int key = cells * 2 + (int)rimHot; GLuint& plateTex = plateTexs[key];
    if (!plateTex || !glIsTexture(plateTex)) {
        const int N = 512; Canvas c(N, N); float band = std::max(0.1f * cells, 0.6f), px = 2.f * cells / N;   // in cells
        for (int y = 0; y < N; ++y) for (int x = 0; x < N; ++x) {
            float mx = (x + 0.5f) * px - cells, my = cells - (y + 0.5f) * px, rr = std::hypot(mx, my);
            if (rr > cells) continue;
            uint8_t* p = &c.px[(y * N + x) * 4];
            auto set = [&](int r_, int g_, int b_, int a) { p[0] = r_; p[1] = g_; p[2] = b_; p[3] = a; };
            if (rr > cells - band) { if (rimHot) set(66, 133, 230, 210); else set(78, 88, 100, 190); continue; }
            set(34, 39, 46, 120);
            auto on = [&](float v, float step, float w) { float m = std::fabs(std::fmod(std::fabs(v) + step / 2, step) - step / 2); return m < w * px; };
            if (on(mx, 5, 1.2f) || on(my, 5, 1.2f)) set(150, 160, 172, 190);
            else if (on(mx, 1, 0.7f) || on(my, 1, 0.7f)) set(105, 115, 128, 160);
        }
        plateTex = r.makeTexture(N, N, c.px.data());
    }
    float w = 2 * R * model.scale;
    r.drawPanel(plateTex, false, platePose(), w, w, w / 2, vp);
    {   // the grid spacing, lying on the rim nearest the viewer's side of the model (its -Y edge)
        char lab[32]; std::snprintf(lab, sizeof lab, g < 1000 ? "GRID %g MM" : "GRID %g M", g < 1000 ? g : g / 1000);
        auto it = labelTex.find(lab);
        if (it == labelTex.end()) {
            int W = Canvas::textWidth(lab, 2) + 16, H = 36; Canvas cv(W, H); cv.text(8, 2, lab, 2, 235, 238, 242);
            it = labelTex.emplace(lab, std::make_pair(r.makeTexture(W, H, cv.px.data()), std::make_pair(W, H))).first;
        }
        float band = std::max(0.1f * cells, 0.6f) * g * model.scale, lh = band * 0.55f, lw = lh * it->second.second.first / it->second.second.second;
        Pose lp = platePose(); lp.pos = lp.pos + rotate(model.rot, V3{0, -(R * model.scale - band / 2), 0.0005f});
        r.drawPanel(it->second.first, false, lp, lw, lh, 0, vp);
    }
    Lines ax;
    ax.add(apply(model, {0, 0, 0}), apply(model, {R, 0, 0}), {0.90f, 0.30f, 0.30f});
    ax.add(apply(model, {0, 0, 0}), apply(model, {0, R, 0}), {0.35f, 0.80f, 0.35f});
    ax.add(apply(model, {0, 0, 0}), apply(model, {0, 0, 0.35f * R}), {0.35f, 0.55f, 0.95f});
    r.drawLines(ax, vp);
}

void Interaction::render(Renderer& r, const M4& vp, V3 eye) {
    viewer = eye;
    r.upload(scene);
    Lines l;
    bool room = passthroughAvailable && passthrough;               // the real room shows: no floor grid
    r.seeThrough = room;
    for (int x = -10; x <= 10 && !room; ++x) {                        // 4 m floor grid, 20 cm cells
        V3 c = (x == 0) ? V3{0.32f, 0.36f, 0.42f} : V3{0.22f, 0.25f, 0.29f};
        l.add({x * 0.2f, 0, -2}, {x * 0.2f, 0, 2}, c); l.add({-2, 0, x * 0.2f}, {2, 0, x * 0.2f}, c);
    }
    if (!scene.empty()) r.drawScene(scene, model, vp, eye, hoverRef(), selected, fade);
    if (plate) drawPlate(r, vp);
    r.drawLines(l, vp);
    // what the app's view draws over the model: always on top, as on a screen
    glDisable(GL_DEPTH_TEST);
    if (sketchVisible && sketchTex) {
        V3 c[4]; for (int k = 0; k < 4; ++k) c[k] = apply(model, sketchCorners[k]);
        V3 ax = c[1] - c[0], ay = c[0] - c[3]; float w = len(ax), h = len(ay);
        if (w > 1e-6f && h > 1e-6f) {
            V3 x = ax * (1 / w), y = norm(ay - x * mcxr::dot(ay, x)), z = cross(x, y);
            r.drawPanel(sketchTex, false, Pose{(c[0] + c[2]) * 0.5f, fromBasis(x, y, z), 1}, w, h, 0, vp);
        }
    }
    if (sketchVisible && sketchLabelTex) for (const SketchLabel& L : sketchLabels) {
        // a dp is ~0.075 degrees: about 3x a phone's, readable at arm's length
        V3 p = apply(model, L.at); float dist = std::max(len(eye - p), 0.05f), k = 0.0013f * dist;
        V3 z = norm(eye - p), x = norm(cross(V3{0, 1, 0}, z)); if (len(x) < 0.5f) x = {1, 0, 0}; V3 y = cross(z, x);
        r.drawPanel(sketchLabelTex, false, Pose{p + x * (L.cx * k) - y * (L.cy * k), fromBasis(x, y, z), 1}, L.w * k, L.h * k, 0, vp, 1, L.uv, true, true);
    }
    if (!overlay.lines.empty()) {
        Lines ol; const auto& v = overlay.lines;
        for (size_t i = 0; i + 8 < v.size(); i += 9) ol.add(apply(model, {v[i], v[i + 1], v[i + 2]}), apply(model, {v[i + 3], v[i + 4], v[i + 5]}), {v[i + 6], v[i + 7], v[i + 8]});
        r.drawLines(ol, vp);
    }
    {   // dots, by size
        const auto& v = overlay.dots; std::map<int, std::vector<float>> bySize;
        for (size_t i = 0; i + 6 < v.size(); i += 7) { V3 p = apply(model, {v[i], v[i + 1], v[i + 2]}); bySize[(int)v[i + 6]].insert(bySize[(int)v[i + 6]].end(), {p.x, p.y, p.z, v[i + 3], v[i + 4], v[i + 5]}); }
        for (auto& [sz, pts] : bySize) r.drawDots(pts, sz * 1.5f, vp);
    }
    glDisable(GL_DEPTH_TEST);                                           // (drawDots turns it back on)
    if (labelTex.size() > 256) { for (auto& [k, t] : labelTex) glDeleteTextures(1, &t.first); labelTex.clear(); }
    for (auto& [p0, text] : overlay.labels) {                         // text labels: billboards, about 1 degree tall
        auto it = labelTex.find(text);
        if (it == labelTex.end()) {
            std::string t = text.substr(0, 48); int W = Canvas::textWidth(t, 2) + 24, H = 44;
            Canvas cv(W, H); cv.rect(0, 0, W, H, 34, 39, 45, 10); cv.text(12, 6, t, 2, 235, 238, 242);
            it = labelTex.emplace(text, std::make_pair(r.makeTexture(W, H, cv.px.data()), std::make_pair(W, H))).first;
        }
        V3 p = apply(model, p0); float dist = std::max(len(eye - p), 0.05f);
        V3 z = norm(eye - p), x = norm(cross(V3{0, 1, 0}, z)); if (len(x) < 0.5f) x = {1, 0, 0}; V3 y = cross(z, x);
        float h = 0.02f * dist, w = h * it->second.second.first / it->second.second.second;
        r.drawPanel(it->second.first, false, Pose{p + x * (w * 0.5f + 0.012f * dist) + y * (h * 0.7f), fromBasis(x, y, z), 1}, w, h, h * 0.25f, vp);
    }
    glEnable(GL_DEPTH_TEST);
    if (panel.visible && panel.tex && !panel.sheets.empty() && !uiLayers) {
        // the UI's surfaces, see-through where the UI draws nothing. They are hidden only by the model in front of them (not
        // by the plate or the overlays): the depth buffer is reset to the model's alone, and the windows don't write to it
        glClear(GL_DEPTH_BUFFER_BIT);
        if (!scene.empty()) {
            glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);
            r.drawScene(scene, model, vp, eye, ElemRef{}, {}, 1.f);
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        }
        glEnable(GL_DEPTH_TEST); glDepthMask(GL_FALSE);
        size_t np = panel.popups.size() / 4;
        for (size_t k = 0; k < np && k < 4; ++k) { const float* p = &panel.popups[4 * k];
            r.holes.insert(r.holes.end(), {p[0] / panel.pxW, p[1] / panel.pxH, (p[0] + p[2]) / panel.pxW, (p[1] + p[3]) / panel.pxH}); }
        auto draw = [&](const Sheet& s) {
            // half a pixel in from the edges, so filtering does not bleed the neighbouring part of the image in
            float uv[4] = {(s.x + 0.5f) / panel.pxW, (s.y + 0.5f) / panel.pxH, (s.w - 1) / panel.pxW, (s.h - 1) / panel.pxH};
            if (panel.sharp) r.drawPanel(panel.sharp, false, sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM, 0, vp, 1, uv, panel.seeThrough, panel.external);
            else r.drawPanel(panel.tex, panel.external, sheetPose(s), s.w / s.pxPerM, s.h / s.pxPerM, 0, vp, 1, uv, panel.seeThrough);
        };
        for (const Sheet& s : panel.sheets) draw(s);
        r.holes.clear();
        for (size_t k = 0; k < np; ++k) draw(panel.popupSheet(k));
        glDepthMask(GL_TRUE);
    } else if (panel.visible && panel.tex && panel.sheets.empty()) {
        // a dark back plate with a slim border, then the UI itself
        static GLuint plate = 0;
        if (!plate) { const unsigned char px[4] = {14, 17, 21, 255}; plate = r.makeTexture(1, 1, px); }
        Pose back = panel.pose; back.pos = back.pos - rotate(back.rot, {0, 0, 0.004f});
        r.drawPanel(plate, false, back, panel.width + 0.02f, panel.height + 0.02f, 0.03f, vp);
        r.drawPanel(panel.tex, panel.external, panel.pose, panel.width, panel.height, 0.022f, vp);
    }
    if (panel.visible && (!scene.empty() || plate)) {
        // the scale badge: "SCALE 1:4.2   [WALK] [PASSTHROUGH] [TRUE SIZE]", or "SCALE 1:1 TRUE SIZE   [WALK] [PASSTHROUGH] [FIT VIEW]"
        std::string ratio = scaleText(), button = trueScale ? "FIT VIEW" : "TRUE SIZE";
        std::string key = ratio + "|" + button + "|" + std::to_string(badgeHot) + (passthroughAvailable ? passthrough ? "R" : "r" : "") + (walk ? "W" : scene.empty() ? "-" : "w");
        if (key != badgeDrawn || !badgeTex || !glIsTexture(badgeTex)) {
            int W = (int)(kBadgeW * kBadgePx), H = (int)(kBadgeH * kBadgePx);
            Canvas c(W, H); c.rect(0, 0, W, H, 14, 17, 21);
            c.text(32, (H - 48) / 2, "SCALE", 3, 138, 150, 163);
            if (trueScale) c.text(180, (H - 64) / 2, "1:1", 4, 110, 200, 140), c.text(180 + 4 * 32, (H - 48) / 2, "TRUE SIZE", 3, 110, 200, 140);
            else c.text(180, (H - 64) / 2, ratio, 4, 235, 238, 242);
            auto pill = [&](int x0, int bw, const std::string& label, bool on, bool hot) {
                // on: filled blue; off: outlined grey
                if (on) c.rect(x0, 14, x0 + bw, H - 14, hot ? 58 : 42, hot ? 125 : 111, hot ? 221 : 201, (H - 28) / 2);
                else { c.rect(x0, 14, x0 + bw, H - 14, hot ? 96 : 70, hot ? 104 : 78, hot ? 114 : 88, (H - 28) / 2);
                       c.rect(x0 + 3, 17, x0 + bw - 3, H - 17, 14, 17, 21, (H - 34) / 2); }
                c.text(x0 + (bw - Canvas::textWidth(label, 3)) / 2, (H - 48) / 2, label, 3, 255, 255, 255);
            };
            pill(W - 16 - kSizeBtnW, kSizeBtnW, button, true, badgeHot == 0);
            if (passthroughAvailable) pill(W - 32 - kSizeBtnW - kRoomBtnW, kRoomBtnW, "PASSTHROUGH", passthrough, badgeHot == 1);
            int w0 = W - 32 - kSizeBtnW - (passthroughAvailable ? 16 + kRoomBtnW : 0);
            if (!scene.empty()) pill(w0 - kWalkBtnW, kWalkBtnW, "WALK", walk, badgeHot == 2);
            badgeTex = r.makeTexture(W, H, c.px.data(), glIsTexture(badgeTex) ? badgeTex : 0); badgeDrawn = key;
        }
        r.drawPanel(badgeTex, false, badgePose(), kBadgeW, kBadgeH, kBadgeH / 2, vp);
    }
    if (cardTex && last[0].active) {                                   // info card floats above the left hand, facing it
        Pose p = unscaled(last[0].grip);
        p.pos = p.pos + rotate(p.rot, {0, 0.09f, -0.02f}); p.rot = qnorm(p.rot * axisAngle({1, 0, 0}, -0.6f));
        float w = 0.16f; r.drawPanel(cardTex, false, p, w, w * cardH / std::max(cardW, 1), 0.01f, vp);
    }
    if (!pointersSeparate) renderPointers(r, vp, eye);
}

bool Interaction::setModel(const std::string& name, const std::vector<uint8_t>& glb) {
    GltfModel* m = name == "hand-left" ? &handModel[0] : name == "hand-right" ? &handModel[1] :
                   name == "touch-plus-left" ? &ctrlModel[0] : name == "touch-plus-right" ? &ctrlModel[1] : nullptr;
    if (!m) return false;
    *m = GltfModel{}; std::string err;
    if (!m->load(glb, &err)) { std::fprintf(stderr, "mcxr: model %s: %s\n", name.c_str(), err.c_str()); return false; }
    return true;
}

ElemRef Interaction::hoverRef() const { return onViewport ? hostHover : hover[1].valid() ? hover[1] : hover[0]; }

void Interaction::renderOverUi(Renderer& r, const M4& vp, V3 eye) {
    auto quads = uiQuads();
    if ((scene.empty() && !plate) || quads.empty()) return;
    // depth: "nearest" everywhere except on the windows, which get their own; the model and the build plate then pass
    // only in front of them
    glClearDepthf(0.f); glClear(GL_DEPTH_BUFFER_BIT); glClearDepthf(1.f);
    glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE); glDepthFunc(GL_ALWAYS);
    for (const UiQuad& q : quads) r.drawRect(q.pose, q.w, q.h, vp);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE); glDepthFunc(GL_LEQUAL);
    if (!scene.empty()) r.drawScene(scene, model, vp, eye, hoverRef(), selected, fade);
    if (plate) drawPlate(r, vp);
    glClear(GL_DEPTH_BUFFER_BIT);
}

void Interaction::renderPointers(Renderer& r, const M4& vp, V3 eye) {
    Lines l; std::vector<float> dots;
    if (last[0].active && !last[0].hand && (!scene.empty() || plate || panel.tex)) {
        // the menu button: blue with the windows shown, outlined while they are hidden
        bool hot = target[1].menuButton; int key = (int)uiHidden * 2 + (int)hot;
        GLuint& tex = menuBtnTex[key];
        if (!tex || !glIsTexture(tex)) {
            const int S = 128; Canvas c(S, S);
            uint8_t br = hot ? 58 : 42, bg = hot ? 125 : 111, bb = hot ? 221 : 201;
            if (uiHidden) { c.rect(0, 0, S, S, hot ? 120 : 96, hot ? 128 : 104, hot ? 138 : 114, S / 2); c.rect(6, 6, S - 6, S - 6, 14, 17, 21, S / 2 - 6); }
            else c.rect(0, 0, S, S, br, bg, bb, S / 2);
            for (int k = 0; k < 3; ++k) c.rect(36, 40 + k * 20, S - 36, 40 + k * 20 + 9, 255, 255, 255, 4);   // the menu icon
            tex = r.makeTexture(S, S, c.px.data());
        }
        r.drawPanel(tex, false, menuButtonPose(), kMenuBtn, kMenuBtn, kMenuBtn / 2, vp);
    }
    auto dot = [&](V3 p, V3 c) { dots.insert(dots.end(), {p.x, p.y, p.z, c.x, c.y, c.z}); };
    bool room = passthroughAvailable && passthrough;
    for (int i = 0; i < 2; ++i) {
        const HandInput& h = last[i]; if (!h.active) continue;
        const Target& t = target[i]; bool hit = t.panel || t.elem.valid() || t.rim || t.menuButton;
        if (tpHand == i && tpArc.size() > 1) {
            // the teleport arc (green where you may land, red on a wall or nowhere) and a ring where you would stand
            V3 col = tpValid ? V3{0.35f, 0.85f, 0.55f} : V3{0.95f, 0.35f, 0.32f};
            for (size_t k = 0; k + 1 < tpArc.size(); k += 1) if (k % 2 == 0 || k + 2 >= tpArc.size()) l.add(tpArc[k], tpArc[k + 1], col);   // dashed
            if (tpValid) for (int k = 0; k < 32; ++k) {
                float a0 = k * 6.2831853f / 32, a1 = (k + 1) * 6.2831853f / 32;
                for (float rr : {0.28f, 0.30f}) l.add(tpPoint + V3{rr * std::cos(a0), 0.01f, rr * std::sin(a0)}, tpPoint + V3{rr * std::cos(a1), 0.01f, rr * std::sin(a1)}, col);
            }
            dot(tpArc.back(), col);
        } else if (!h.noRay && !h.poke) {
            // a hand's ray starts a little ahead of it and is fainter: it is a pointer, the hand is what you see
            float len_ = hit ? t.t : (h.hand ? 0.8f : 1.5f); V3 f = forward(h.aim), end = h.aim.pos + f * len_;
            V3 col = t.panel || t.menuButton ? V3{0.92f, 0.94f, 0.97f} : t.elem.valid() ? V3{0.35f, 0.75f, 1.f} : V3{0.55f, 0.60f, 0.66f};
            if (h.hand && h.select > 0.55f) col = V3{0.40f, 0.68f, 1.f};
            l.add(h.aim.pos + f * (h.hand ? 0.06f : 0.f), end, col);
            if (hit && (!t.panel || !uiLayers)) dot(end, t.panel ? (pointerHand == i ? V3{0.26f, 0.56f, 0.87f} : V3{1, 1, 1}) : V3{0.35f, 0.75f, 1.f});
        } else if (h.poke && !uiLayers && t.panel) dot(h.aim.pos + forward(h.aim) * t.t, pokeTouch[i] ? V3{0.26f, 0.56f, 0.87f} : V3{1, 1, 1});
        // In passthrough your real hands and controllers are in view: no models over them (the cameras' view of things this
        // close is reprojected a few cm off, so a model never lines up with the real thing); the rays and cursors stay.
        if (room) continue;
        if (h.hand) {
            // the hand model, its bones on the tracked joints
            handModel[i].drawSkinned(r, vp, eye, [&](const std::string& name, Pose* p) {
                for (int k = 1; k < kHandJoints; ++k) if (name == jointName(k)) { *p = Pose{h.joints[k].pos, h.joints[k].rot, 1}; return true; }
                return false;
            }, V3{0.86f, 0.88f, 0.92f}, 0.95f);
        } else if (showControllers) {
            // the controller at its grip, its trigger, grip, stick and buttons moving as they are used
            ctrlModel[i].draw(r, h.grip, vp, eye, [&](const std::string& v) {
                if (v == "xr_standard_trigger_pressed") return h.select;
                if (v == "xr_standard_squeeze_pressed") return h.squeeze;
                if (v == "xr_standard_thumbstick_xaxis_pressed") return (h.stickX + 1) / 2;
                if (v == "xr_standard_thumbstick_yaxis_pressed") return (1 - h.stickY) / 2;     // (WebXR: up is -1)
                if (v == "a_button_pressed" || v == "x_button_pressed") return h.menu ? 1.f : 0.f;
                if (v == "b_button_pressed" || v == "y_button_pressed") return h.context ? 1.f : 0.f;
                return 0.f;
            });
        }
    }
    r.drawLines(l, vp);
    r.drawDots(dots, 14.f, vp);
}

}  // namespace mcxr
