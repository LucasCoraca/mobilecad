#include "xr_session.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "mcxr", __VA_ARGS__)
#else
#define LOG(...) (std::fprintf(stderr, "mcxr: " __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace mcxr {

#ifdef __ANDROID__
// GL_EXT_multisampled_render_to_texture
typedef void (*FbTex2DMsFn)(GLenum, GLenum, GLenum, GLuint, GLint, GLsizei);
typedef void (*RbStorageMsFn)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);
static FbTex2DMsFn fbTex2DMs = nullptr; static RbStorageMsFn rbStorageMs = nullptr;
#endif
static const int kSamples = 4;
// Hand tracking is switched off for now (its grab was too eager on the headset): controllers only. The code stays (hands.h).
static const bool kHandTracking = false;

#define XRCHECK(call) do { XrResult r_ = (call); if (XR_FAILED(r_)) { char s_[XR_MAX_RESULT_STRING_SIZE] = "?"; \
    if (instance) xrResultToString(instance, r_, s_); LOG("%s failed: %s (%d)", #call, s_, (int)r_); return false; } } while (0)

static bool hasExtension(const std::vector<XrExtensionProperties>& exts, const char* name) {
    for (auto& e : exts) if (std::strcmp(e.extensionName, name) == 0) return true;
    return false;
}

bool Headset::create(const PlatformInit& p, const char* appName, std::string* error) {
    auto fail = [&](const char* why) { if (error) *error = why; LOG("%s", why); destroy(); return false; };
#ifdef __ANDROID__
    PFN_xrInitializeLoaderKHR initLoader = nullptr;
    if (XR_SUCCEEDED(xrGetInstanceProcAddr(XR_NULL_HANDLE, "xrInitializeLoaderKHR", (PFN_xrVoidFunction*)&initLoader)) && initLoader) {
        XrLoaderInitInfoAndroidKHR li{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR}; li.applicationVM = p.vm; li.applicationContext = p.activity;
        initLoader((const XrLoaderInitInfoBaseHeaderKHR*)&li);
    }
    const char* gfxExt = XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
#else
    const char* gfxExt = XR_KHR_OPENGL_ENABLE_EXTENSION_NAME;
#endif
    uint32_t n = 0;
    if (XR_FAILED(xrEnumerateInstanceExtensionProperties(nullptr, 0, &n, nullptr))) return fail("No OpenXR runtime is installed");
    std::vector<XrExtensionProperties> exts(n, {XR_TYPE_EXTENSION_PROPERTIES});
    xrEnumerateInstanceExtensionProperties(nullptr, n, &n, exts.data());
    if (!hasExtension(exts, gfxExt)) return fail("The OpenXR runtime has no OpenGL support");
    std::vector<const char*> enable{gfxExt};
    bool ptExt = hasExtension(exts, XR_FB_PASSTHROUGH_EXTENSION_NAME);
#ifdef XR_FB_composition_layer_settings
    layerSettings = hasExtension(exts, XR_FB_COMPOSITION_LAYER_SETTINGS_EXTENSION_NAME);
    if (layerSettings) enable.push_back(XR_FB_COMPOSITION_LAYER_SETTINGS_EXTENSION_NAME);
#endif
    if (ptExt) enable.push_back(XR_FB_PASSTHROUGH_EXTENSION_NAME);
    bool handExt = kHandTracking && hasExtension(exts, XR_EXT_HAND_TRACKING_EXTENSION_NAME);
    if (handExt) enable.push_back(XR_EXT_HAND_TRACKING_EXTENSION_NAME);
    handAim = handExt && hasExtension(exts, XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME);
    if (handAim) enable.push_back(XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME);
    handInteraction = kHandTracking && hasExtension(exts, XR_EXT_HAND_INTERACTION_EXTENSION_NAME);
    if (handInteraction) enable.push_back(XR_EXT_HAND_INTERACTION_EXTENSION_NAME);
#ifdef __ANDROID__
    if (hasExtension(exts, XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME)) enable.push_back(XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME);
    XrInstanceCreateInfoAndroidKHR android{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR}; android.applicationVM = p.vm; android.applicationActivity = p.activity;
#endif
    XrInstanceCreateInfo ci{XR_TYPE_INSTANCE_CREATE_INFO};
#ifdef __ANDROID__
    ci.next = &android;
#endif
    std::snprintf(ci.applicationInfo.applicationName, XR_MAX_APPLICATION_NAME_SIZE, "%s", appName);
    std::snprintf(ci.applicationInfo.engineName, XR_MAX_ENGINE_NAME_SIZE, "MobileCAD XR");
    ci.applicationInfo.applicationVersion = 1; ci.applicationInfo.apiVersion = XR_API_VERSION_1_0;
    ci.enabledExtensionCount = (uint32_t)enable.size(); ci.enabledExtensionNames = enable.data();
    if (XR_FAILED(xrCreateInstance(&ci, &instance))) return fail("Could not start OpenXR");
    XrInstanceProperties ip{XR_TYPE_INSTANCE_PROPERTIES}; xrGetInstanceProperties(instance, &ip); runtimeName = ip.runtimeName;

    XrSystemGetInfo sg{XR_TYPE_SYSTEM_GET_INFO}; sg.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    if (XR_FAILED(xrGetSystem(instance, &sg, &system))) return fail("No headset is connected");
    XrSystemHandTrackingPropertiesEXT htp{XR_TYPE_SYSTEM_HAND_TRACKING_PROPERTIES_EXT};
    XrSystemProperties sp{XR_TYPE_SYSTEM_PROPERTIES}; if (handExt) sp.next = &htp;
    xrGetSystemProperties(instance, system, &sp); systemName = sp.systemName;
    handExt = handExt && htp.supportsHandTracking;
    maxLayers = sp.graphicsProperties.maxLayerCount ? sp.graphicsProperties.maxLayerCount : 16;

    // graphics requirements must be queried before the session (spec); the GL context is the caller's
#ifdef __ANDROID__
    PFN_xrGetOpenGLESGraphicsRequirementsKHR req = nullptr;
    xrGetInstanceProcAddr(instance, "xrGetOpenGLESGraphicsRequirementsKHR", (PFN_xrVoidFunction*)&req);
    XrGraphicsRequirementsOpenGLESKHR gr{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR}; if (req) req(instance, system, &gr);
    XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
    binding.display = p.display; binding.config = p.config; binding.context = p.context;
#else
    PFN_xrGetOpenGLGraphicsRequirementsKHR req = nullptr;
    xrGetInstanceProcAddr(instance, "xrGetOpenGLGraphicsRequirementsKHR", (PFN_xrVoidFunction*)&req);
    XrGraphicsRequirementsOpenGLKHR gr{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR}; if (req) req(instance, system, &gr);
    XrGraphicsBindingOpenGLXlibKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_XLIB_KHR};
    binding.xDisplay = p.display; binding.glxFBConfig = p.fbConfig; binding.glxDrawable = p.drawable; binding.glxContext = p.context;
    binding.visualid = 0;
#endif
    XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO}; sci.next = &binding; sci.systemId = system;
    if (XR_FAILED(xrCreateSession(instance, &sci, &session))) return fail("Could not create the XR session");

    // stage (floor-level) space when the runtime has one, else local (head-level origin)
    uint32_t ns = 0; xrEnumerateReferenceSpaces(session, 0, &ns, nullptr);
    std::vector<XrReferenceSpaceType> spaces(ns); xrEnumerateReferenceSpaces(session, ns, &ns, spaces.data());
    XrReferenceSpaceType type = XR_REFERENCE_SPACE_TYPE_LOCAL;
    for (auto s : spaces) if (s == XR_REFERENCE_SPACE_TYPE_STAGE) type = XR_REFERENCE_SPACE_TYPE_STAGE;
    XrReferenceSpaceCreateInfo rs{XR_TYPE_REFERENCE_SPACE_CREATE_INFO}; rs.poseInReferenceSpace.orientation.w = 1;
    stage = type == XR_REFERENCE_SPACE_TYPE_STAGE;
    rs.referenceSpaceType = type; if (XR_FAILED(xrCreateReferenceSpace(session, &rs, &appSpace))) return fail("No tracking space");
    makePassthrough(ptExt);
    rs.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_VIEW; xrCreateReferenceSpace(session, &rs, &viewSpace);
    if (!makeSwapchains()) return fail("Could not create the eye images");
    if (!makeActions()) return fail("Could not set up the controllers");
    makeHandTrackers(handExt);
    LOG("runtime %s, system %s, %dx%d per eye, %s space", runtimeName.c_str(), systemName.c_str(), eyes[0].w, eyes[0].h,
        type == XR_REFERENCE_SPACE_TYPE_STAGE ? "stage" : "local");
    return true;
}

void Headset::makePassthrough(bool enabled) {
    if (!enabled) return;
    PFN_xrCreatePassthroughFB create = nullptr; PFN_xrCreatePassthroughLayerFB createLayer = nullptr;
    xrGetInstanceProcAddr(instance, "xrCreatePassthroughFB", (PFN_xrVoidFunction*)&create);
    xrGetInstanceProcAddr(instance, "xrCreatePassthroughLayerFB", (PFN_xrVoidFunction*)&createLayer);
    xrGetInstanceProcAddr(instance, "xrPassthroughStartFB", (PFN_xrVoidFunction*)&ptStart);
    xrGetInstanceProcAddr(instance, "xrPassthroughPauseFB", (PFN_xrVoidFunction*)&ptPause);
    if (!create || !createLayer || !ptStart || !ptPause) return;
    XrPassthroughCreateInfoFB pc{XR_TYPE_PASSTHROUGH_CREATE_INFO_FB}; pc.flags = XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB;
    if (XR_FAILED(create(session, &pc, &passthrough))) { passthrough = XR_NULL_HANDLE; LOG("passthrough: not available"); return; }
    XrPassthroughLayerCreateInfoFB lc{XR_TYPE_PASSTHROUGH_LAYER_CREATE_INFO_FB};
    lc.passthrough = passthrough; lc.flags = XR_PASSTHROUGH_IS_RUNNING_AT_CREATION_BIT_FB; lc.purpose = XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB;
    if (XR_FAILED(createLayer(session, &lc, &passthroughLayer))) { passthroughLayer = XR_NULL_HANDLE; LOG("passthrough: no layer"); return; }
    passthroughOn = true; LOG("passthrough: ready");
}

void Headset::makeHandTrackers(bool enabled) {
    if (!enabled) { LOG("hand tracking: not available"); return; }
    PFN_xrCreateHandTrackerEXT create = nullptr;
    xrGetInstanceProcAddr(instance, "xrCreateHandTrackerEXT", (PFN_xrVoidFunction*)&create);
    xrGetInstanceProcAddr(instance, "xrLocateHandJointsEXT", (PFN_xrVoidFunction*)&locateJoints);
    xrGetInstanceProcAddr(instance, "xrDestroyHandTrackerEXT", (PFN_xrVoidFunction*)&destroyTracker);
    if (!create || !locateJoints) return;
    for (int i = 0; i < 2; ++i) {
        XrHandTrackerCreateInfoEXT ci{XR_TYPE_HAND_TRACKER_CREATE_INFO_EXT};
        ci.hand = i == 0 ? XR_HAND_LEFT_EXT : XR_HAND_RIGHT_EXT; ci.handJointSet = XR_HAND_JOINT_SET_DEFAULT_EXT;
        if (XR_FAILED(create(session, &ci, &trackers[i]))) trackers[i] = XR_NULL_HANDLE;
    }
    LOG("hand tracking: %s%s", trackers[0] && trackers[1] ? "ready" : "no trackers", handAim ? ", runtime aim" : "");
}

bool Headset::makeEye(Eye& eye, int w, int h) {
    eye.w = w; eye.h = h;
    XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    sc.format = colorFormat; sc.sampleCount = 1; sc.width = eye.w; sc.height = eye.h; sc.faceCount = 1; sc.arraySize = 1; sc.mipCount = 1;
    XRCHECK(xrCreateSwapchain(session, &sc, &eye.swapchain));
    uint32_t ni = 0; xrEnumerateSwapchainImages(eye.swapchain, 0, &ni, nullptr);
#ifdef __ANDROID__
    std::vector<XrSwapchainImageOpenGLESKHR> imgs(ni, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
#else
    std::vector<XrSwapchainImageOpenGLKHR> imgs(ni, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR});
#endif
    XRCHECK(xrEnumerateSwapchainImages(eye.swapchain, ni, &ni, (XrSwapchainImageBaseHeader*)imgs.data()));
    for (auto& i : imgs) eye.images.push_back(i.image);
    glGenRenderbuffers(1, &eye.depth); glBindRenderbuffer(GL_RENDERBUFFER, eye.depth);
#ifdef __ANDROID__
    if (samples > 1) rbStorageMs(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, eye.w, eye.h);
    else glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, eye.w, eye.h);
#else
    glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, eye.w, eye.h);
    glGenRenderbuffers(1, &eye.msColor); glBindRenderbuffer(GL_RENDERBUFFER, eye.msColor);
    glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8, eye.w, eye.h);
    glGenFramebuffers(1, &eye.msFbo); glBindFramebuffer(GL_FRAMEBUFFER, eye.msFbo);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, eye.msColor);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, eye.depth);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
#endif
    glGenFramebuffers(1, &eye.fbo);
    return true;
}

bool Headset::makeSwapchains() {
    uint32_t nv = 0; xrEnumerateViewConfigurationViews(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, 0, &nv, nullptr);
    std::vector<XrViewConfigurationView> views(nv, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
    XRCHECK(xrEnumerateViewConfigurationViews(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, nv, &nv, views.data()));
    if (nv < 2) return false;
    uint32_t nf = 0; xrEnumerateSwapchainFormats(session, 0, &nf, nullptr);
    std::vector<int64_t> fmts(nf); xrEnumerateSwapchainFormats(session, nf, &nf, fmts.data());
    colorFormat = 0;
    for (int64_t want : {(int64_t)GL_SRGB8_ALPHA8, (int64_t)GL_RGBA8})
        for (int64_t f : fmts) if (!colorFormat && f == want) { colorFormat = f; srgb = (f == GL_SRGB8_ALPHA8); }
    if (!colorFormat && nf) colorFormat = fmts[0];
#ifdef __ANDROID__
    const char* glExt = (const char*)glGetString(GL_EXTENSIONS);
    if (glExt && std::strstr(glExt, "GL_EXT_multisampled_render_to_texture")) {
        fbTex2DMs = (FbTex2DMsFn)eglGetProcAddress("glFramebufferTexture2DMultisampleEXT");
        rbStorageMs = (RbStorageMsFn)eglGetProcAddress("glRenderbufferStorageMultisampleEXT");
    }
    samples = fbTex2DMs && rbStorageMs ? kSamples : 1;
#else
    samples = kSamples;
#endif
    LOG("eye images: %d samples", samples);
    for (int e = 0; e < 2; ++e) {
        // 1.3x the recommended size (Quest 3 recommends less than its panels' resolution), within the maximum
        int w = (int)std::min<float>(views[e].recommendedImageRectWidth * 1.3f, (float)views[e].maxImageRectWidth);
        int h = (int)std::min<float>(views[e].recommendedImageRectHeight * 1.3f, (float)views[e].maxImageRectHeight);
        if (!makeEye(eyes[e], w, h)) return false;
    }
    overOk = makeEye(over[0], views[0].recommendedImageRectWidth, views[0].recommendedImageRectHeight) &&
             makeEye(over[1], views[1].recommendedImageRectWidth, views[1].recommendedImageRectHeight);
    eyeW = eyes[0].w; eyeH = eyes[0].h;
    return true;
}

bool Headset::makeChain(Chain& c, int w, int h) {
    XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    sc.format = colorFormat; sc.sampleCount = 1; sc.width = w; sc.height = h; sc.faceCount = 1; sc.arraySize = 1; sc.mipCount = 1;
    XRCHECK(xrCreateSwapchain(session, &sc, &c.sc));
    uint32_t ni = 0; xrEnumerateSwapchainImages(c.sc, 0, &ni, nullptr);
#ifdef __ANDROID__
    std::vector<XrSwapchainImageOpenGLESKHR> imgs(ni, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
#else
    std::vector<XrSwapchainImageOpenGLKHR> imgs(ni, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR});
#endif
    XRCHECK(xrEnumerateSwapchainImages(c.sc, ni, &ni, (XrSwapchainImageBaseHeader*)imgs.data()));
    for (auto& i : imgs) c.images.push_back(i.image);
    c.w = w; c.h = h;
    return true;
}

bool Headset::makeUiChains(int w, int h) {
    if (!makeChain(uiBase, w, h) || !makeChain(uiFull, w, h) || !makeChain(cursorChain, 64, 64)) return false;
    // the cursor: a white dot with a dark ring, premultiplied
    std::vector<uint8_t> px(64 * 64 * 4, 0);
    for (int y = 0; y < 64; ++y) for (int x = 0; x < 64; ++x) {
        float d = std::hypot(x + 0.5f - 32, y + 0.5f - 32); uint8_t* p = &px[(y * 64 + x) * 4];
        float a = std::max(0.f, std::min(1.f, 31.f - d)), inner = std::max(0.f, std::min(1.f, 22.f - d));
        float v = inner * 255; p[0] = p[1] = p[2] = (uint8_t)(v * a); p[3] = (uint8_t)(a * 255);
        if (!inner && a > 0) { p[0] = (uint8_t)(40 * a); p[1] = (uint8_t)(110 * a); p[2] = (uint8_t)(200 * a); }
    }
    uint32_t idx = 0; XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO}; xrAcquireSwapchainImage(cursorChain.sc, &ai, &idx);
    XrSwapchainImageWaitInfo wi{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wi.timeout = XR_INFINITE_DURATION; xrWaitSwapchainImage(cursorChain.sc, &wi);
    glBindTexture(GL_TEXTURE_2D, cursorChain.images[idx]); glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 64, 64, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; xrReleaseSwapchainImage(cursorChain.sc, &ri);
    LOG("UI as compositor layers: %dx%d%s", w, h, layerSettings ? ", supersampled + sharpened" : "");
    return true;
}

/** Copies the UI image into the layers' swapchains when it changed: whole, and without its popups (they are layers). */
void Headset::updateUi(Interaction& ia, Renderer& r) {
    if (!ia.uiDirty) return;
    ia.uiDirty = false;
    std::vector<float> holes;
    for (size_t k = 0; k + 3 < ia.panel.popups.size(); k += 4) { const float* p = &ia.panel.popups[k];
        holes.insert(holes.end(), {p[0] / ia.panel.pxW, p[1] / ia.panel.pxH, (p[0] + p[2]) / ia.panel.pxW, (p[1] + p[3]) / ia.panel.pxH}); }
    for (Chain* c : {&uiBase, &uiFull}) {
        uint32_t idx = 0; XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO}; xrAcquireSwapchainImage(c->sc, &ai, &idx);
        XrSwapchainImageWaitInfo wi{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wi.timeout = XR_INFINITE_DURATION; xrWaitSwapchainImage(c->sc, &wi);
#ifndef __ANDROID__
        if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);                       // (OpenGL ES encodes sRGB targets by itself)
#endif
        r.drawImageInto(c->images[idx], c->w, c->h, ia.panel.tex, ia.panel.external, srgb ? 2.2f : 1.f, c == &uiBase ? holes : std::vector<float>{});
#ifndef __ANDROID__
        glDisable(GL_FRAMEBUFFER_SRGB);
#endif
        XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; xrReleaseSwapchainImage(c->sc, &ri);
    }
}

bool Headset::makeActions() {
    XrActionSetCreateInfo as{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::strcpy(as.actionSetName, "cad"); std::strcpy(as.localizedActionSetName, "CAD");
    XRCHECK(xrCreateActionSet(instance, &as, &actionSet));
    xrStringToPath(instance, "/user/hand/left", &hands[0]); xrStringToPath(instance, "/user/hand/right", &hands[1]);
    auto make = [&](XrAction* a, const char* name, const char* label, XrActionType t) {
        XrActionCreateInfo ac{XR_TYPE_ACTION_CREATE_INFO}; ac.actionType = t; std::strcpy(ac.actionName, name); std::strcpy(ac.localizedActionName, label);
        ac.countSubactionPaths = 2; ac.subactionPaths = hands; return XR_SUCCEEDED(xrCreateAction(actionSet, &ac, a));
    };
    if (!make(&aAim, "aim", "Aim", XR_ACTION_TYPE_POSE_INPUT) || !make(&aGrip, "grip", "Hand", XR_ACTION_TYPE_POSE_INPUT) ||
        !make(&aSelect, "select", "Select / grab", XR_ACTION_TYPE_FLOAT_INPUT) || !make(&aSqueeze, "squeeze", "Grab", XR_ACTION_TYPE_FLOAT_INPUT) ||
        !make(&aStick, "stick", "Scale / turn", XR_ACTION_TYPE_VECTOR2F_INPUT) || !make(&aMenu, "menu", "Recenter", XR_ACTION_TYPE_BOOLEAN_INPUT) ||
        !make(&aContext, "context", "Context menu", XR_ACTION_TYPE_BOOLEAN_INPUT))
        return false;
    // suggested bindings per controller; a profile the runtime does not know is simply skipped
    struct B { XrAction a; const char* path; };
    auto suggest = [&](const char* profile, std::vector<B> bs) {
        XrPath prof; xrStringToPath(instance, profile, &prof);
        std::vector<XrActionSuggestedBinding> v;
        for (auto& b : bs) { XrPath p; if (XR_SUCCEEDED(xrStringToPath(instance, b.path, &p))) v.push_back({b.a, p}); }
        XrInteractionProfileSuggestedBinding sb{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        sb.interactionProfile = prof; sb.countSuggestedBindings = (uint32_t)v.size(); sb.suggestedBindings = v.data();
        XrResult r = xrSuggestInteractionProfileBindings(instance, &sb);
        if (XR_FAILED(r)) LOG("bindings for %s not accepted (%d)", profile, (int)r);
    };
    const char* L = "/user/hand/left/input/"; const char* R = "/user/hand/right/input/";
    std::vector<std::string> s; s.reserve(64);    // backing storage for the path strings (no reallocation: pointers stay valid)
    auto pr = [&](const std::string& x) { s.push_back(x); return s.back().c_str(); };
    suggest("/interaction_profiles/khr/simple_controller", {
        {aAim, pr(std::string(L) + "aim/pose")}, {aAim, pr(std::string(R) + "aim/pose")},
        {aGrip, pr(std::string(L) + "grip/pose")}, {aGrip, pr(std::string(R) + "grip/pose")},
        {aSelect, pr(std::string(L) + "select/click")}, {aSelect, pr(std::string(R) + "select/click")},
        {aMenu, pr(std::string(L) + "menu/click")}, {aMenu, pr(std::string(R) + "menu/click")}});
    suggest("/interaction_profiles/oculus/touch_controller", {
        {aAim, pr(std::string(L) + "aim/pose")}, {aAim, pr(std::string(R) + "aim/pose")},
        {aGrip, pr(std::string(L) + "grip/pose")}, {aGrip, pr(std::string(R) + "grip/pose")},
        {aSelect, pr(std::string(L) + "trigger/value")}, {aSelect, pr(std::string(R) + "trigger/value")},
        {aSqueeze, pr(std::string(L) + "squeeze/value")}, {aSqueeze, pr(std::string(R) + "squeeze/value")},
        {aStick, pr(std::string(L) + "thumbstick")}, {aStick, pr(std::string(R) + "thumbstick")},
        {aMenu, pr(std::string(L) + "x/click")}, {aMenu, pr(std::string(R) + "a/click")},
        {aContext, pr(std::string(L) + "y/click")}, {aContext, pr(std::string(R) + "b/click")}});
    suggest("/interaction_profiles/microsoft/motion_controller", {
        {aAim, pr(std::string(L) + "aim/pose")}, {aAim, pr(std::string(R) + "aim/pose")},
        {aGrip, pr(std::string(L) + "grip/pose")}, {aGrip, pr(std::string(R) + "grip/pose")},
        {aSelect, pr(std::string(L) + "trigger/value")}, {aSelect, pr(std::string(R) + "trigger/value")},
        {aSqueeze, pr(std::string(L) + "squeeze/click")}, {aSqueeze, pr(std::string(R) + "squeeze/click")},
        {aStick, pr(std::string(L) + "thumbstick")}, {aStick, pr(std::string(R) + "thumbstick")},
        {aMenu, pr(std::string(L) + "menu/click")}, {aMenu, pr(std::string(R) + "menu/click")},
        {aContext, pr(std::string(L) + "thumbstick/click")}, {aContext, pr(std::string(R) + "thumbstick/click")}});
    suggest("/interaction_profiles/valve/index_controller", {
        {aAim, pr(std::string(L) + "aim/pose")}, {aAim, pr(std::string(R) + "aim/pose")},
        {aGrip, pr(std::string(L) + "grip/pose")}, {aGrip, pr(std::string(R) + "grip/pose")},
        {aSelect, pr(std::string(L) + "trigger/value")}, {aSelect, pr(std::string(R) + "trigger/value")},
        {aSqueeze, pr(std::string(L) + "squeeze/value")}, {aSqueeze, pr(std::string(R) + "squeeze/value")},
        {aStick, pr(std::string(L) + "thumbstick")}, {aStick, pr(std::string(R) + "thumbstick")},
        {aMenu, pr(std::string(L) + "b/click")}, {aMenu, pr(std::string(R) + "b/click")},
        {aContext, pr(std::string(L) + "a/click")}, {aContext, pr(std::string(R) + "a/click")}});
    if (handInteraction)   // hands: the system's pinch (its "select") and grasp, as on its own UI
        suggest("/interaction_profiles/ext/hand_interaction_ext", {
            {aAim, pr(std::string(L) + "aim/pose")}, {aAim, pr(std::string(R) + "aim/pose")},
            {aGrip, pr(std::string(L) + "grip/pose")}, {aGrip, pr(std::string(R) + "grip/pose")},
            {aSelect, pr(std::string(L) + "aim_activate_ext/value")}, {aSelect, pr(std::string(R) + "aim_activate_ext/value")},
            {aSqueeze, pr(std::string(L) + "grasp_ext/value")}, {aSqueeze, pr(std::string(R) + "grasp_ext/value")}});
    for (int i = 0; i < 2; ++i) {
        XrActionSpaceCreateInfo sc{XR_TYPE_ACTION_SPACE_CREATE_INFO}; sc.subactionPath = hands[i]; sc.poseInActionSpace.orientation.w = 1;
        sc.action = aAim; XRCHECK(xrCreateActionSpace(session, &sc, &aimSpace[i]));
        sc.action = aGrip; XRCHECK(xrCreateActionSpace(session, &sc, &gripSpace[i]));
    }
    XrSessionActionSetsAttachInfo at{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO}; at.countActionSets = 1; at.actionSets = &actionSet;
    XRCHECK(xrAttachSessionActionSets(session, &at));
    return true;
}

void Headset::pollEvents(bool* quit) {
    XrEventDataBuffer ev{XR_TYPE_EVENT_DATA_BUFFER};
    while (instance && xrPollEvent(instance, &ev) == XR_SUCCESS) {
        if (ev.type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
            auto* sc = (XrEventDataSessionStateChanged*)&ev; state = sc->state;
            LOG("session state %d", (int)state);
            if (state == XR_SESSION_STATE_READY) {
                XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO}; bi.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                if (XR_SUCCEEDED(xrBeginSession(session, &bi))) sessionRunning = true;
            } else if (state == XR_SESSION_STATE_STOPPING) {
                xrEndSession(session); sessionRunning = false;
            } else if (state == XR_SESSION_STATE_EXITING || state == XR_SESSION_STATE_LOSS_PENDING) {
                if (quit) *quit = true;
            }
        } else if (ev.type == XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED) {
            readProfiles();
        } else if (ev.type == XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) {
            if (quit) *quit = true;
        }
        ev = {XR_TYPE_EVENT_DATA_BUFFER};
    }
}

void Headset::readProfiles() {
    XrPath hp = XR_NULL_PATH; xrStringToPath(instance, "/interaction_profiles/ext/hand_interaction_ext", &hp);
    for (int i = 0; i < 2; ++i) {
        XrInteractionProfileState st{XR_TYPE_INTERACTION_PROFILE_STATE};
        handProfile[i] = XR_SUCCEEDED(xrGetCurrentInteractionProfile(session, hands[i], &st)) && st.interactionProfile == hp && hp != XR_NULL_PATH;
    }
    LOG("input: left %s, right %s", handProfile[0] ? "hand" : "controller", handProfile[1] ? "hand" : "controller");
}

void Headset::readHands(XrTime t, HandInput out[2]) {
    XrActiveActionSet active{actionSet, XR_NULL_PATH};
    XrActionsSyncInfo si{XR_TYPE_ACTIONS_SYNC_INFO}; si.countActiveActionSets = 1; si.activeActionSets = &active;
    xrSyncActions(session, &si);
    for (int i = 0; i < 2; ++i) {
        HandInput& h = out[i]; h = {};
        XrSpaceLocation la{XR_TYPE_SPACE_LOCATION}, lg{XR_TYPE_SPACE_LOCATION};
        xrLocateSpace(aimSpace[i], appSpace, t, &la); xrLocateSpace(gripSpace[i], appSpace, t, &lg);
        const XrSpaceLocationFlags need = XR_SPACE_LOCATION_POSITION_VALID_BIT | XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;
        h.active = (la.locationFlags & need) == need;
        h.aim = toPose(la.pose); h.grip = (lg.locationFlags & need) == need ? toPose(lg.pose) : h.aim;
        XrActionStateGetInfo gi{XR_TYPE_ACTION_STATE_GET_INFO}; gi.subactionPath = hands[i];
        XrActionStateFloat f{XR_TYPE_ACTION_STATE_FLOAT};
        gi.action = aSelect; if (XR_SUCCEEDED(xrGetActionStateFloat(session, &gi, &f)) && f.isActive) h.select = f.currentState;
        f = {XR_TYPE_ACTION_STATE_FLOAT};
        gi.action = aSqueeze; if (XR_SUCCEEDED(xrGetActionStateFloat(session, &gi, &f)) && f.isActive) h.squeeze = f.currentState;
        XrActionStateVector2f v{XR_TYPE_ACTION_STATE_VECTOR2F};
        gi.action = aStick; if (XR_SUCCEEDED(xrGetActionStateVector2f(session, &gi, &v)) && v.isActive) { h.stickX = v.currentState.x; h.stickY = v.currentState.y; }
        XrActionStateBoolean b{XR_TYPE_ACTION_STATE_BOOLEAN};
        gi.action = aMenu; if (XR_SUCCEEDED(xrGetActionStateBoolean(session, &gi, &b)) && b.isActive) h.menu = b.currentState;
        b = {XR_TYPE_ACTION_STATE_BOOLEAN};
        gi.action = aContext; if (XR_SUCCEEDED(xrGetActionStateBoolean(session, &gi, &b)) && b.isActive) h.context = b.currentState;
        // a tracked hand (the runtime tracks hands while no controller is held): its joints replace that side's input
        if (!trackers[i] || !locateJoints) continue;
        XrHandJointLocationEXT jl[XR_HAND_JOINT_COUNT_EXT];
        XrHandJointLocationsEXT locs{XR_TYPE_HAND_JOINT_LOCATIONS_EXT}; locs.jointCount = XR_HAND_JOINT_COUNT_EXT; locs.jointLocations = jl;
        XrHandTrackingAimStateFB aim{XR_TYPE_HAND_TRACKING_AIM_STATE_FB}; if (handAim) locs.next = &aim;
        XrHandJointsLocateInfoEXT li{XR_TYPE_HAND_JOINTS_LOCATE_INFO_EXT}; li.baseSpace = appSpace; li.time = t;
        if (XR_FAILED(locateJoints(trackers[i], &li, &locs)) || !locs.isActive) continue;
        if (!(jl[XR_HAND_JOINT_PALM_EXT].locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT)) continue;
        float sysPinch = h.select, sysGrasp = h.squeeze;
        h = {}; h.active = h.hand = true;
        if (handProfile[i]) { h.sysGestures = true; h.sysPinch = sysPinch; h.sysGrasp = sysGrasp; }
        for (int k = 0; k < kHandJoints && k < (int)XR_HAND_JOINT_COUNT_EXT; ++k) {
            h.joints[k] = toPose(jl[k].pose); h.radii[k] = jl[k].radius;
            if (!(jl[k].locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT)) h.joints[k].rot = Q{};
        }
        if (handAim && (aim.status & XR_HAND_TRACKING_AIM_VALID_BIT_FB)) {
            h.runtimeAim = true; h.aim = toPose(aim.aimPose);
            h.pinchIndex = aim.pinchStrengthIndex; h.pinchMiddle = aim.pinchStrengthMiddle;
            h.menuGesture = (aim.status & XR_HAND_TRACKING_AIM_MENU_PRESSED_BIT_FB) != 0;
            h.systemGesture = (aim.status & XR_HAND_TRACKING_AIM_SYSTEM_GESTURE_BIT_FB) != 0;
        }
    }
}

bool Headset::frame(Interaction& ia, Renderer& r) {
    if (!sessionRunning) return false;
    XrFrameState fs{XR_TYPE_FRAME_STATE}; XrFrameWaitInfo wi{XR_TYPE_FRAME_WAIT_INFO};
    if (XR_FAILED(xrWaitFrame(session, &wi, &fs))) return false;
    XrFrameBeginInfo bi{XR_TYPE_FRAME_BEGIN_INFO}; xrBeginFrame(session, &bi);
    XrCompositionLayerProjectionView pv[2] = {{XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW}, {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW}};
    XrCompositionLayerProjection layer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
    XrCompositionLayerProjectionView opv[2] = {{XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW}, {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW}};
    XrCompositionLayerProjection overLayer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
    bool rendered = false, overRendered = false;
    if (fs.shouldRender) {
        float dt = lastTime ? (float)((fs.predictedDisplayTime - lastTime) * 1e-9) : 0.f; lastTime = fs.predictedDisplayTime;
        if (dt < 0 || dt > 0.1f) dt = 1.f / 72.f;
        XrSpaceLocation hl{XR_TYPE_SPACE_LOCATION}; xrLocateSpace(viewSpace, appSpace, fs.predictedDisplayTime, &hl);
        headValid = (hl.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) && (hl.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT);
        if (headValid) head = toPose(hl.pose);
        if (needsPlacement && headValid) {
            if (onPlaced) onPlaced(head);
            if (!ia.scene.empty() || ia.plate) { ia.placeModel(head); needsPlacement = false; }
        }
        HandInput hin[2]; readHands(fs.predictedDisplayTime, hin);
        if (!firstTime) firstTime = fs.predictedDisplayTime;
        if (inputOverride) inputOverride(hin, head, (fs.predictedDisplayTime - firstTime) * 1e-9);
        ia.floorY = stage ? 0.f : -1.5f;                                 // local space: origin at the head, floor ~1.5 m below
        ia.passthroughAvailable = passthroughLayer != XR_NULL_HANDLE;
        if (ia.passthroughAvailable && ia.passthrough != passthroughOn) {          // the badge switched it: run the cameras only when shown
            if (ia.passthrough) ptStart(passthrough); else ptPause(passthrough);
            passthroughOn = ia.passthrough;
        }
        ia.headPose = head;
        ia.update(hin, dt);
        // the UI's windows as compositor layers, once the app's UI is up (they need its size)
        if (!uiTried && !ia.panel.sheets.empty() && ia.panel.tex) { uiTried = true; ia.uiLayers = makeUiChains(ia.panel.pxW, ia.panel.pxH); }
        if (ia.uiLayers) updateUi(ia, r);

        XrViewLocateInfo li{XR_TYPE_VIEW_LOCATE_INFO}; li.viewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
        li.displayTime = fs.predictedDisplayTime; li.space = appSpace;
        XrViewState vs{XR_TYPE_VIEW_STATE}; XrView views[2] = {{XR_TYPE_VIEW}, {XR_TYPE_VIEW}}; uint32_t nv = 0;
        if (XR_SUCCEEDED(xrLocateViews(session, &li, &vs, 2, &nv, views)) && nv == 2 &&
            (vs.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) && (vs.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT)) {
            r.gamma = srgb ? 2.2f : 1.f;
#ifndef __ANDROID__
            if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
#endif
            for (int e = 0; e < 2; ++e) {
                Eye& eye = eyes[e]; uint32_t idx = 0;
                XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO}; xrAcquireSwapchainImage(eye.swapchain, &ai, &idx);
                XrSwapchainImageWaitInfo wti{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wti.timeout = XR_INFINITE_DURATION; xrWaitSwapchainImage(eye.swapchain, &wti);
                glBindFramebuffer(GL_FRAMEBUFFER, eye.fbo);
#ifdef __ANDROID__
                if (samples > 1) fbTex2DMs(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0, samples);
                else glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, eye.depth);
                GLuint target = eye.fbo;
#else
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0);
                GLuint target = eye.msFbo;
#endif
                Pose ep = toPose(views[e].pose);
                M4 proj = projectionFov(views[e].fov.angleLeft, views[e].fov.angleRight, views[e].fov.angleUp, views[e].fov.angleDown, 0.02f, 50.f);
                M4 vp = mul(proj, viewFrom(ep));
                r.beginView(target, 0, 0, eye.w, eye.h, true);
                ia.render(r, vp, ep.pos);
#ifdef __ANDROID__
                // the depth buffer need not be written back to memory
                const GLenum discard[] = {GL_DEPTH_ATTACHMENT}; glInvalidateFramebuffer(GL_FRAMEBUFFER, 1, discard);
#else
                glBindFramebuffer(GL_READ_FRAMEBUFFER, eye.msFbo); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, eye.fbo);
                glBlitFramebuffer(0, 0, eye.w, eye.h, 0, 0, eye.w, eye.h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
#endif
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                if (e == 0) mirrorTex = eye.images[idx];
                XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; xrReleaseSwapchainImage(eye.swapchain, &ri);
                pv[e].pose = views[e].pose; pv[e].fov = views[e].fov;
                pv[e].subImage.swapchain = eye.swapchain; pv[e].subImage.imageRect = {{0, 0}, {eye.w, eye.h}};
            }
            ia.pointersSeparate = overOk && ia.uiLayers;
            if (ia.pointersSeparate) {
                // the layer over the UI's windows: transparent; the model where it is in front of a window, then the hands (the model's
                // depth first, so a hand behind the model is hidden by it)
                bool st = r.seeThrough; r.seeThrough = true;
                for (int e = 0; e < 2; ++e) {
                    Eye& eye = over[e]; uint32_t idx = 0;
                    XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO}; xrAcquireSwapchainImage(eye.swapchain, &ai, &idx);
                    XrSwapchainImageWaitInfo wti{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wti.timeout = XR_INFINITE_DURATION; xrWaitSwapchainImage(eye.swapchain, &wti);
                    glBindFramebuffer(GL_FRAMEBUFFER, eye.fbo);
#ifdef __ANDROID__
                    if (samples > 1) fbTex2DMs(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0, samples);
                    else glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0);
                    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, eye.depth);
                    GLuint target = eye.fbo;
#else
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[idx], 0);
                    GLuint target = eye.msFbo;
#endif
                    Pose ep = toPose(views[e].pose);
                    M4 proj = projectionFov(views[e].fov.angleLeft, views[e].fov.angleRight, views[e].fov.angleUp, views[e].fov.angleDown, 0.02f, 50.f);
                    M4 vp = mul(proj, viewFrom(ep));
                    r.beginView(target, 0, 0, eye.w, eye.h, true);
                    ia.renderOverUi(r, vp, ep.pos);                 // the model in front of a window covers it
                    if (!ia.scene.empty()) {
                        glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);
                        r.drawScene(ia.scene, ia.model, vp, ep.pos, ElemRef{}, {}, 1.f);
                        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
                    }
                    ia.renderPointers(r, vp, ep.pos);
#ifdef __ANDROID__
                    const GLenum discard[] = {GL_DEPTH_ATTACHMENT}; glInvalidateFramebuffer(GL_FRAMEBUFFER, 1, discard);
#else
                    glBindFramebuffer(GL_READ_FRAMEBUFFER, eye.msFbo); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, eye.fbo);
                    glBlitFramebuffer(0, 0, eye.w, eye.h, 0, 0, eye.w, eye.h, GL_COLOR_BUFFER_BIT, GL_NEAREST);
#endif
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO}; xrReleaseSwapchainImage(eye.swapchain, &ri);
                    opv[e].pose = views[e].pose; opv[e].fov = views[e].fov;
                    opv[e].subImage.swapchain = eye.swapchain; opv[e].subImage.imageRect = {{0, 0}, {eye.w, eye.h}};
                }
                r.seeThrough = st;
                overLayer.space = appSpace; overLayer.viewCount = 2; overLayer.views = opv; overRendered = true;
                overLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
            }
#ifndef __ANDROID__
            glDisable(GL_FRAMEBUFFER_SRGB);
#endif
            layer.space = appSpace; layer.viewCount = 2; layer.views = pv; rendered = true;
        }
    }
    // passthrough: the camera layer first, the app's image over it, blended by its alpha (premultiplied)
    XrCompositionLayerPassthroughFB ptl{XR_TYPE_COMPOSITION_LAYER_PASSTHROUGH_FB}; ptl.layerHandle = passthroughLayer;
    bool room = passthroughLayer && ia.passthrough;
    if (room) layer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
    std::vector<const XrCompositionLayerBaseHeader*> layers;
    if (room) layers.push_back((XrCompositionLayerBaseHeader*)&ptl);
    if (rendered) layers.push_back((XrCompositionLayerBaseHeader*)&layer);
    // the UI's windows and popups over the scene, then the cursors on them
    std::vector<XrCompositionLayerQuad> quads; quads.reserve(24);
#ifdef XR_FB_composition_layer_settings
    XrCompositionLayerSettingsFB settings{XR_TYPE_COMPOSITION_LAYER_SETTINGS_FB};
    settings.layerFlags = XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SUPER_SAMPLING_BIT_FB | XR_COMPOSITION_LAYER_SETTINGS_NORMAL_SHARPENING_BIT_FB;
    const void* quadNext = layerSettings ? &settings : nullptr;
#else
    const void* quadNext = nullptr;
#endif
    auto toXr = [](const Pose& p) { XrPosef o; o.position = {p.pos.x, p.pos.y, p.pos.z}; o.orientation = {p.rot.x, p.rot.y, p.rot.z, p.rot.w}; return o; };
    if (rendered && ia.uiLayers) {
        for (const auto& q : ia.uiQuads()) {
            if (layers.size() + quads.size() + 3 >= maxLayers) break;
            XrCompositionLayerQuad l{XR_TYPE_COMPOSITION_LAYER_QUAD}; l.next = quadNext;
            l.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT; l.space = appSpace; l.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
            const Chain& c = q.popup ? uiFull : uiBase;
            l.subImage.swapchain = c.sc;
            l.subImage.imageRect = {{(int32_t)q.x, (int32_t)(c.h - (q.y + q.ph))}, {(int32_t)q.pw, (int32_t)q.ph}};   // GL images: from the bottom
            l.pose = toXr(q.pose); l.size = {q.w, q.h};
            quads.push_back(l);
        }
        for (V3 p : ia.uiCursors()) {
            if (layers.size() + quads.size() + 1 >= maxLayers) break;
            V3 z = norm(head.pos - p), x = norm(cross(V3{0, 1, 0}, z)); if (len(x) < 0.5f) x = {1, 0, 0}; V3 y = cross(z, x);
            float d = std::max(len(head.pos - p), 0.1f), size = 0.012f * d;           // ~0.7 degrees
            XrCompositionLayerQuad l{XR_TYPE_COMPOSITION_LAYER_QUAD};
            l.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT; l.space = appSpace; l.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
            l.subImage.swapchain = cursorChain.sc; l.subImage.imageRect = {{0, 0}, {64, 64}};
            l.pose = toXr(Pose{p, fromBasis(x, y, z), 1}); l.size = {size, size};
            quads.push_back(l);
        }
        for (auto& q : quads) layers.push_back((XrCompositionLayerBaseHeader*)&q);
    }
    if (rendered && overRendered) layers.push_back((XrCompositionLayerBaseHeader*)&overLayer);   // the hands, over everything
    XrFrameEndInfo ei{XR_TYPE_FRAME_END_INFO}; ei.displayTime = fs.predictedDisplayTime;
    ei.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE; ei.layerCount = (uint32_t)layers.size(); ei.layers = layers.data();
    xrEndFrame(session, &ei);
    return rendered;
}

void Headset::destroy() {
    for (Eye* ep : {&eyes[0], &eyes[1], &over[0], &over[1]}) { Eye& e = *ep;
        if (e.swapchain) xrDestroySwapchain(e.swapchain); e.swapchain = XR_NULL_HANDLE;
        if (e.msFbo) glDeleteFramebuffers(1, &e.msFbo); if (e.msColor) glDeleteRenderbuffers(1, &e.msColor); e.msFbo = e.msColor = 0;
        e.images.clear();
    }
    overOk = false;
    for (auto& t : trackers) { if (t && destroyTracker) destroyTracker(t); t = XR_NULL_HANDLE; }
    passthroughLayer = XR_NULL_HANDLE; passthrough = XR_NULL_HANDLE; passthroughOn = false;   // (destroyed with the session)
    for (Chain* c : {&uiBase, &uiFull, &cursorChain}) { if (c->sc) xrDestroySwapchain(c->sc); *c = Chain{}; }
    uiTried = false;
    for (int i = 0; i < 2; ++i) { if (aimSpace[i]) xrDestroySpace(aimSpace[i]); if (gripSpace[i]) xrDestroySpace(gripSpace[i]); aimSpace[i] = gripSpace[i] = XR_NULL_HANDLE; }
    if (appSpace) xrDestroySpace(appSpace); if (viewSpace) xrDestroySpace(viewSpace); appSpace = viewSpace = XR_NULL_HANDLE;
    if (actionSet) xrDestroyActionSet(actionSet); actionSet = XR_NULL_HANDLE;
    if (session) xrDestroySession(session); session = XR_NULL_HANDLE;
    if (instance) xrDestroyInstance(instance); instance = XR_NULL_HANDLE;
    sessionRunning = false;
}

}  // namespace mcxr
