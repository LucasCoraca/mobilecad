// Android glue for the XR core. The headset path runs OpenXR on its own render thread (EGL pbuffer context); without an
// OpenXR runtime (phone, emulator) the same scene is drawn into a GLSurfaceView as a touch preview. In both, the app's UI
// (MobileCAD's Compose shell on a virtual display) arrives as a SurfaceTexture shown on the floating panel; the Kotlin side
// feeds the model (the document's meshes + selection) and what MobileCAD's 3D view draws over it (preselection, overlay,
// the sketch being edited), and receives the controller rays for that view and the panel touches.
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstring>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "gl.h"
#include "interaction.h"
#include "renderer.h"
#include "xr_session.h"
#ifndef GL_TEXTURE_EXTERNAL_OES
#define GL_TEXTURE_EXTERNAL_OES 0x8D65   // GL_OES_EGL_image_external (SurfaceTexture images)
#endif

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "mcxr", __VA_ARGS__)
using namespace mcxr;

namespace {
JavaVM* gVm = nullptr; jobject gActivity = nullptr;
Interaction gIa; Renderer gRenderer;
std::mutex gQueueLock; std::vector<std::function<void()>> gQueue;   // scene / selection changes, applied on the GL thread
std::thread gXrThread; std::atomic<bool> gStop{false};
std::atomic<bool> gPlaceModel{true}, gPlacePanel{true};
std::atomic<int> gPanelW{2220}, gPanelH{1200};                      // the UI's virtual display, in pixels (set by the app)
std::atomic<float> gPxPerDp{1.5f};
std::mutex gSheetLock; std::vector<float> gSheetRects;               // its parts: x y w h each (tree, stage, bars, side)

JNIEnv* env() { JNIEnv* e = nullptr; gVm->GetEnv((void**)&e, JNI_VERSION_1_6); return e; }
jmethodID method(JNIEnv* e, const char* name, const char* sig) {
    jclass c = e->GetObjectClass(gActivity); jmethodID m = e->GetMethodID(c, name, sig); e->DeleteLocalRef(c); return m;
}
void post(std::function<void()> f) { std::lock_guard<std::mutex> l(gQueueLock); gQueue.push_back(std::move(f)); }
void drainQueue() {
    std::vector<std::function<void()>> q; { std::lock_guard<std::mutex> l(gQueueLock); q.swap(gQueue); }
    for (auto& f : q) f();
}

/** MobileCAD's interface as surfaces around the viewer (MobileCAD's XrLayout): one window with the whole interface to the
 *  left-front, turned towards the viewer, clear of the build plate ahead; the keypad (number pad / keyboard, shown only
 *  while one is up) low and close on the right, tilted up towards the eyes like a keyboard. Everything is see-through
 *  where MobileCAD draws nothing. Local frame: the viewer's eyes, -Z ahead. */
void arrangeSheets(const std::vector<float>& r) {
    gIa.panel.sheets.clear();
    if (r.size() < 8) return;
    // sizes as at 1.5 px per dp (1600 px per metre: a dp is ~0.94 mm a metre away); a denser display is only sharper
    const float k = gPxPerDp / 1.5f, ppm = 1600 * k;
    auto sheet = [&](int i) { Sheet s; s.x = r[4 * i]; s.y = r[4 * i + 1]; s.w = r[4 * i + 2]; s.h = r[4 * i + 3]; s.pxPerM = ppm; return s; };
    Sheet win = sheet(0), pad = sheet(1);
    const float D = 0.95f, yaw = 0.62f;                               // ~35 degrees to the left
    Q wq = axisAngle({0, 1, 0}, yaw) * axisAngle({1, 0, 0}, 0.15f);   // its top leaning back a little, like a monitor
    win.local = {rotate(axisAngle({0, 1, 0}, yaw), V3{0, 0, -D}) + V3{0, -0.12f, 0}, wq, 1};
    pad.pxPerM = 2200 * k;                                             // closer, so denser: about as big to the eye
    Q pq = axisAngle({0, 1, 0}, -0.35f) * axisAngle({1, 0, 0}, -0.6f);
    pad.local = {V3{0.24f, -0.42f, -0.50f}, pq, 1};
    for (Sheet* s : {&win, &pad}) s->home = s->local;
    gIa.panel.sheets = {win, pad};
    gIa.panel.seeThrough = true; gIa.panel.badgeSheet = 0;           // the scale badge hangs under the window
}

/** On the GL thread: the UI panel's texture (Java wraps it in a SurfaceTexture and renders MobileCAD's UI into it). */
void createPanel() {
    int pw = gPanelW, ph = gPanelH;
    { std::lock_guard<std::mutex> l(gSheetLock); arrangeSheets(gSheetRects); }
    GLuint tex = 0; glGenTextures(1, &tex); glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    gIa.panel.tex = tex; gIa.panel.external = true; gIa.panel.pxW = pw; gIa.panel.pxH = ph;
    gIa.panel.width = pw / (1600.f * gPxPerDp / 1.5f); gIa.panel.height = gIa.panel.width * ph / pw;   // 1600 px per metre at 1.5 px/dp
    gIa.panel.visible = true;
    JNIEnv* e = env(); e->CallVoidMethod(gActivity, method(e, "createPanelSurface", "(III)V"), (jint)tex, pw, ph);
}
/** Latches the newest UI image; a few times a second, reads back where it is see-through (for the rays). Once a second
 *  it is read back anyway: a mask taken from a moment the UI was hidden must not outlive it when no new frame follows. */
void updatePanel() {
    static double lastMask = 0; static bool changed = false;
    JNIEnv* e = env(); bool fresh = e->CallBooleanMethod(gActivity, method(e, "updatePanelTexture", "()Z"));
    if (fresh) gIa.panelChanged(gRenderer);
    changed |= fresh;
    double now = std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
    if ((changed && now - lastMask > 0.1) || now - lastMask > 1.0) { gIa.updateMask(gRenderer); changed = false; lastMask = now; }
}

void hookCallbacks() {
    gIa.hostSelection = true;
    gIa.onPick = [](const ElemRef& r, V3 p) {
        JNIEnv* e = env();
        std::string obj = r.valid() ? gIa.scene.meshes[r.mesh].name : "", sub = r.valid() ? std::string(r.kind == 'E' ? "Edge" : "Face") + std::to_string(r.id) : "";
        jstring jo = e->NewStringUTF(obj.c_str()), js = e->NewStringUTF(sub.c_str());
        e->CallVoidMethod(gActivity, method(e, "onXrPick", "(Ljava/lang/String;Ljava/lang/String;FFF)V"), jo, js, p.x, p.y, p.z);
        e->DeleteLocalRef(jo); e->DeleteLocalRef(js);
    };
    gIa.onPanelPointer = [](int action, float x, float y) {
        JNIEnv* e = env(); e->CallVoidMethod(gActivity, method(e, "onXrPanelPointer", "(IFF)V"), action, x, y);
    };
    gIa.onMenu = [] { gPlaceModel = true; gPlacePanel = true; };     // menu / A / X: bring the panel and the model back in front
    // the model in the room is MobileCAD's 3D view: rays go to its touch handling (Java coalesces hover to its frame rate)
    gIa.onViewport = [](int action, V3 o, V3 d, float depth) {
        JNIEnv* e = env(); e->CallVoidMethod(gActivity, method(e, "onXrViewport", "(IFFFFFFF)V"), action, o.x, o.y, o.z, d.x, d.y, d.z, depth);
    };
    gIa.onContextMenu = [] { JNIEnv* e = env(); e->CallVoidMethod(gActivity, method(e, "onXrContextMenu", "()V")); };
}

/** The headset loop: EGL pbuffer context (OpenXR renders into its own swapchains), session, frames until told to stop. */
void xrMain() {
    JNIEnv* e = nullptr; gVm->AttachCurrentThread(&e, nullptr);
    auto report = [&](bool ok, const std::string& msg) {
        jstring s = e->NewStringUTF(msg.c_str()); e->CallVoidMethod(gActivity, method(e, "onXrResult", "(ZLjava/lang/String;)V"), (jboolean)ok, s); e->DeleteLocalRef(s);
    };
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY); eglInitialize(dpy, nullptr, nullptr);
    const EGLint cfgAttrs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                               EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 0, EGL_NONE};
    EGLConfig cfg = nullptr; EGLint n = 0; eglChooseConfig(dpy, cfgAttrs, &cfg, 1, &n);
    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext ctx = n ? eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttrs) : EGL_NO_CONTEXT;
    const EGLint pb[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    EGLSurface surf = ctx ? eglCreatePbufferSurface(dpy, cfg, pb) : EGL_NO_SURFACE;
    if (!ctx || !eglMakeCurrent(dpy, surf, surf, ctx)) { report(false, "No OpenGL ES 3 context"); gVm->DetachCurrentThread(); return; }
    Headset hs; PlatformInit p; p.vm = gVm; p.activity = gActivity; p.display = dpy; p.config = cfg; p.context = ctx;
    std::string err;
    if (!hs.create(p, "MobileCAD XR", &err)) {
        report(false, err);
        eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT); eglDestroySurface(dpy, surf); eglDestroyContext(dpy, ctx);
        gVm->DetachCurrentThread(); return;
    }
    gRenderer.contextLost(); gRenderer.init(); hookCallbacks(); createPanel();
    hs.onPlaced = [](const Pose& head) { if (gPlacePanel.exchange(false)) gIa.placePanel(head); };
    report(true, hs.systemName);
    bool quit = false;
    while (!gStop && !quit) {
        hs.pollEvents(&quit);
        drainQueue();
        if (gPlaceModel.exchange(false)) hs.needsPlacement = true;
        if (gPlacePanel) hs.needsPlacement = true;                     // onPlaced puts the panel in front of the head
        if (!hs.running()) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        updatePanel();
        gIa.showControllers = true;
        hs.frame(gIa, gRenderer);
    }
    hs.destroy();
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT); eglDestroySurface(dpy, surf); eglDestroyContext(dpy, ctx);
    if (quit) e->CallVoidMethod(gActivity, method(e, "onXrExit", "()V"));
    gVm->DetachCurrentThread();
}

// ---- headset-free preview: you stand at the origin; one finger is the controller with its trigger pressed (on the panel
// and on the model / into the scene alike), two fingers look around
struct Preview {
    float yaw = 0, pitch = -0.38f, fov = 0.62f;     // looking down at the build plate
    int mode = 0;                 // 0 idle, 1 trigger held, 2 looking around
    float lastX = 0, lastY = 0, tx = 0, ty = 0;
    bool pressSeen = false, releasePending = false;   // a quick tap is held for at least one frame
    int w = 1, h = 1;
} gPv;
const Pose kHead{V3{0, 1.6f, 0}, Q{}, 1};
Pose previewEye() { return {kHead.pos, qnorm(axisAngle({0, 1, 0}, gPv.yaw) * axisAngle({1, 0, 0}, gPv.pitch)), 1}; }
Pose previewRay(float sx, float sy) {               // screen point (pixels) -> aim pose from the eye
    Pose eye = previewEye(); float aspect = (float)gPv.w / std::max(gPv.h, 1), fy = gPv.fov, fx = std::atan(std::tan(fy) * aspect);
    float nx = sx / gPv.w * 2 - 1, ny = 1 - sy / gPv.h * 2;
    V3 d = norm(rotate(eye.rot, V3{nx * std::tan(fx), ny * std::tan(fy), -1}));
    V3 z = d * -1.f, xa = norm(cross(V3{0, 1, 0}, z)), ya = cross(z, xa); float tr = xa.x + ya.y + z.z; Q q{};
    if (tr > 0) { float s = std::sqrt(tr + 1) * 2; q = {(ya.z - z.y) / s, (z.x - xa.z) / s, (xa.y - ya.x) / s, s / 4}; }
    return {eye.pos, qnorm(q), 1};
}
}  // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) { gVm = vm; return JNI_VERSION_1_6; }

JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeAttach(JNIEnv* e, jobject self) {
    if (gActivity) e->DeleteGlobalRef(gActivity);
    gActivity = e->NewGlobalRef(self);
}
/** The UI's virtual display size and its parts (x y w h px: tree, stage, bars, side); before the XR start. */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetLayout(JNIEnv* e, jobject, jint w, jint h, jfloat pxPerDp, jfloatArray rects) {
    gPanelW = w; gPanelH = h; gPxPerDp = pxPerDp;
    std::vector<float> r(e->GetArrayLength(rects)); e->GetFloatArrayRegion(rects, 0, (jsize)r.size(), r.data());
    std::lock_guard<std::mutex> l(gSheetLock); gSheetRects = r;
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeStartXr(JNIEnv*, jobject) {
    if (gXrThread.joinable()) return;
    gStop = false; gXrThread = std::thread(xrMain);
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeStop(JNIEnv*, jobject) {
    gStop = true; if (gXrThread.joinable()) gXrThread.join();
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeBeginScene(JNIEnv* e, jobject, jstring title) {
    const char* t = e->GetStringUTFChars(title, nullptr); std::string s(t); e->ReleaseStringUTFChars(title, t);
    post([s] { Pose keep = gIa.model; gIa.scene.clear(); gIa.scene.title = s; gIa.model = keep; });
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeAddMesh(JNIEnv* e, jobject, jstring name, jfloatArray tris, jfloatArray edges,
                                                                      jfloat r, jfloat g, jfloat b, jfloat alpha) {
    const char* n = e->GetStringUTFChars(name, nullptr); std::string sname(n); e->ReleaseStringUTFChars(name, n);
    jsize nt = e->GetArrayLength(tris), ne = e->GetArrayLength(edges);              // engine layout: [n][tris][n][edges]
    std::vector<uint8_t> blob(8 + 4 * (nt + ne)); int32_t a = nt, c = ne;
    memcpy(blob.data(), &a, 4); e->GetFloatArrayRegion(tris, 0, nt, (jfloat*)(blob.data() + 4));
    memcpy(blob.data() + 4 + 4 * nt, &c, 4); e->GetFloatArrayRegion(edges, 0, ne, (jfloat*)(blob.data() + 8 + 4 * nt));
    post([sname, blob = std::move(blob), r, g, b, alpha] { float rgb[3] = {r, g, b};
        if (gIa.scene.addTessellation(sname, blob.data(), blob.size(), rgb)) gIa.scene.meshes.back().alpha = alpha; });
}
/** `place`: a new document - put the model in front of the viewer (an edit of the same document keeps where it is). */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeEndScene(JNIEnv*, jobject, jboolean place) {
    // the first one is put in front of the viewer; later ones keep the table where the user put it, sized for them
    if (place) post([] { if (gIa.placed) gIa.fitScale(); else gPlaceModel = true; });
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetSelection(JNIEnv* e, jobject, jobjectArray objs, jobjectArray subs) {
    std::vector<std::pair<std::string, std::string>> sel;
    for (jsize i = 0, n = e->GetArrayLength(objs); i < n; ++i) {
        auto o = (jstring)e->GetObjectArrayElement(objs, i), s = (jstring)e->GetObjectArrayElement(subs, i);
        const char* co = e->GetStringUTFChars(o, nullptr); const char* cs = e->GetStringUTFChars(s, nullptr);
        sel.emplace_back(co, cs); e->ReleaseStringUTFChars(o, co); e->ReleaseStringUTFChars(s, cs);
        e->DeleteLocalRef(o); e->DeleteLocalRef(s);
    }
    post([sel] {
        gIa.selected.clear();
        for (auto& [o, s] : sel) {
            int m = gIa.scene.find(o); if (m < 0) continue;
            if (s.rfind("Face", 0) == 0) gIa.selected.push_back({m, 'F', std::atoi(s.c_str() + 4)});
            else if (s.rfind("Edge", 0) == 0) gIa.selected.push_back({m, 'E', std::atoi(s.c_str() + 4)});
        }
    });
}

/** What MobileCAD's view draws over the model: preselection, fade (sketch editing), overlay lines / dots / labels. */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetView(JNIEnv* e, jobject, jstring hoverObj, jstring hoverSub, jfloat fade,
        jfloatArray lines, jfloatArray dots, jfloatArray labelPos, jobjectArray labelText) {
    auto str = [&](jstring j) { const char* c = e->GetStringUTFChars(j, nullptr); std::string s(c); e->ReleaseStringUTFChars(j, c); return s; };
    auto floats = [&](jfloatArray a) { std::vector<float> v(e->GetArrayLength(a)); e->GetFloatArrayRegion(a, 0, (jsize)v.size(), v.data()); return v; };
    std::string ho = str(hoverObj), hs = str(hoverSub);
    Interaction::Overlay ov; ov.lines = floats(lines); ov.dots = floats(dots);
    std::vector<float> lp = floats(labelPos);
    for (jsize i = 0, n = e->GetArrayLength(labelText); i < n && 3 * i + 2 < (jsize)lp.size(); ++i) {
        auto t = (jstring)e->GetObjectArrayElement(labelText, i); ov.labels.emplace_back(V3{lp[3 * i], lp[3 * i + 1], lp[3 * i + 2]}, str(t)); e->DeleteLocalRef(t);
    }
    post([ho, hs, fade, ov = std::move(ov)]() mutable {
        int m = gIa.scene.find(ho); char k = hs.rfind("Face", 0) == 0 ? 'F' : hs.rfind("Edge", 0) == 0 ? 'E' : 0;
        gIa.hostHover = (m >= 0 && k) ? ElemRef{m, k, std::atoi(hs.c_str() + 4)} : ElemRef{};
        gIa.fade = fade; gIa.overlay = std::move(ov);
    });
}
/** The sketch being edited, drawn by MobileCAD into a bitmap (premultiplied RGBA) lying on the sketch plane with the given
 *  corners (TL TR BR BL, model mm); null pixels hide it. */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetSketch(JNIEnv* e, jobject, jobject pixels, jint w, jint h, jfloatArray corners) {
    if (!pixels) { post([] { gIa.sketchVisible = false; }); return; }
    const uint8_t* src = (const uint8_t*)e->GetDirectBufferAddress(pixels); if (!src) return;
    std::vector<uint8_t> px(src, src + (size_t)w * h * 4);
    for (size_t i = 0; i < px.size(); i += 4) { unsigned a = px[i + 3];                 // un-premultiply for straight alpha blending
        if (a && a < 255) for (int k = 0; k < 3; ++k) px[i + k] = (uint8_t)std::min(255u, px[i + k] * 255u / a); }
    float c[12]; e->GetFloatArrayRegion(corners, 0, 12, c);
    post([px = std::move(px), w, h, cs = std::vector<float>(c, c + 12)] {
        gIa.sketchTex = gRenderer.makeTexture(w, h, px.data(), gIa.sketchTex);
        for (int k = 0; k < 4; ++k) gIa.sketchCorners[k] = {cs[3 * k], cs[3 * k + 1], cs[3 * k + 2]};
        gIa.sketchVisible = true;
    });
}

/** The windows over the UI (menus, popups, dialogs), x y w h px each, bottom to top. */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetPopups(JNIEnv* e, jobject, jfloatArray rects) {
    std::vector<float> r(e->GetArrayLength(rects)); e->GetFloatArrayRegion(rects, 0, (jsize)r.size(), r.data());
    post([r] { gIa.panel.popups = r; });
}

/** A hand / controller model (.glb bytes), parsed on the GL thread. */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetModel(JNIEnv* e, jobject, jstring name, jbyteArray glb) {
    const char* c = e->GetStringUTFChars(name, nullptr); std::string n = c; e->ReleaseStringUTFChars(name, c);
    std::vector<uint8_t> b(e->GetArrayLength(glb)); e->GetByteArrayRegion(glb, 0, (jsize)b.size(), (jbyte*)b.data());
    post([n, b] { gIa.setModel(n, b); });
}

/** The sketch's text labels: their texture (premultiplied RGBA; null = none) and per label: point (model mm), box centre
 *  offset and size (dp), texture cell (u0 v0 u1 v1). */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativeSetSketchLabels(JNIEnv* e, jobject, jobject pixels, jint w, jint h, jfloatArray labels) {
    if (!pixels) { post([] { gIa.sketchLabels.clear(); }); return; }
    const uint8_t* src = (const uint8_t*)e->GetDirectBufferAddress(pixels); if (!src) return;
    std::vector<uint8_t> px(src, src + (size_t)w * h * 4);
    std::vector<float> v(e->GetArrayLength(labels)); e->GetFloatArrayRegion(labels, 0, (jsize)v.size(), v.data());
    post([px = std::move(px), w, h, v = std::move(v)] {
        gIa.sketchLabelTex = gRenderer.makeTexture(w, h, px.data(), gIa.sketchLabelTex);
        gIa.sketchLabels.clear();
        for (size_t i = 0; i + 10 < v.size(); i += 11)
            gIa.sketchLabels.push_back({V3{v[i], v[i + 1], v[i + 2]}, v[i + 3], v[i + 4], v[i + 5], v[i + 6], {v[i + 7], v[i + 8], v[i + 9] - v[i + 7], v[i + 10] - v[i + 8]}});
    });
}

JNIEXPORT jfloat JNICALL Java_app_mobilecad_xr_XrActivity_nativeModelScale(JNIEnv*, jobject) { return gIa.model.scale; }

// ---- preview (GLSurfaceView thread) ----
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativePreviewInit(JNIEnv*, jobject) {
    gRenderer.contextLost(); gRenderer.init(); hookCallbacks(); createPanel(); gPlaceModel = true; gPlacePanel = true;
}
/** Touch on the preview (pixels): 0 down, 1 move, 2 up of one finger = the trigger at that point; 3 two fingers moving
 *  (their centre) = look around, 4 the second finger landed (the press so far is cancelled: a release at once). */
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativePreviewTouch(JNIEnv*, jobject, jint action, jfloat x, jfloat y) {
    post([action, x, y] {
        if (action == 0) { gPv.tx = x; gPv.ty = y; gPv.mode = 1; gPv.pressSeen = false; gPv.releasePending = false; }
        else if (action == 1) { if (gPv.mode == 1) { gPv.tx = x; gPv.ty = y; } }
        else if (action == 2) { if (gPv.mode == 1 && !gPv.pressSeen) gPv.releasePending = true; gPv.mode = 0; }
        else if (action == 4) { gPv.mode = 2; gPv.releasePending = false; gPv.lastX = x; gPv.lastY = y; }
        else if (action == 3 && gPv.mode == 2) {
            gPv.yaw += (x - gPv.lastX) * 0.004f; gPv.pitch = std::max(-1.2f, std::min(1.2f, gPv.pitch + (y - gPv.lastY) * 0.004f));
            gPv.lastX = x; gPv.lastY = y;
        }
    });
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativePreviewLook(JNIEnv*, jobject, jfloat yaw, jfloat pitch, jfloat fov) {
    post([yaw, pitch, fov] { gPv.yaw = yaw; gPv.pitch = pitch; gPv.fov = fov; });
}
JNIEXPORT void JNICALL Java_app_mobilecad_xr_XrActivity_nativePreviewDraw(JNIEnv*, jobject, jint w, jint h) {
    gPv.w = w; gPv.h = h;
    drainQueue();
    if (gPlacePanel.exchange(false)) gIa.placePanel(kHead);
    if (gPlaceModel.exchange(false)) gIa.placeModel(kHead);
    updatePanel();
    HandInput hands[2];
    hands[1].active = true; hands[1].aim = previewRay(gPv.tx, gPv.ty); hands[1].grip = hands[1].aim;
    bool press = gPv.mode == 1 || gPv.releasePending;
    if (press && gPv.pressSeen) gPv.releasePending = false, press = gPv.mode == 1;
    if (press) gPv.pressSeen = true;
    hands[1].select = press ? 1.f : 0.f;
    gIa.showControllers = false;
    gIa.update(hands, 1.f / 60.f);
    Pose eye = previewEye(); float aspect = h > 0 ? (float)w / h : 1.f, fy = gPv.fov, fx = std::atan(std::tan(fy) * aspect);
    M4 vp = mul(projectionFov(-fx, fx, fy, -fy, 0.02f, 50.f), viewFrom(eye));
    gRenderer.gamma = 1.f; gRenderer.beginView(0, 0, 0, w, h, true);
    gIa.render(gRenderer, vp, eye.pos);
}

}  // extern "C"
