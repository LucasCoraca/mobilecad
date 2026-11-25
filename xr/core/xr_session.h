// OpenXR plumbing: instance, system, session, stereo swapchains, controller actions and the frame loop. Built for
// Android headsets (OpenGL ES, XR_KHR_opengl_es_enable) and for Linux against Monado (OpenGL/Xlib, XR_KHR_opengl_enable).
#pragma once
#include <functional>
#include <string>
#include <vector>
#include "gl.h"
#include "interaction.h"
#include "renderer.h"

#ifdef __ANDROID__
#include <EGL/egl.h>
#include <jni.h>
#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES
#else
#include <X11/Xlib.h>
#include <GL/glx.h>
#define XR_USE_PLATFORM_XLIB
#define XR_USE_GRAPHICS_API_OPENGL
#endif
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

namespace mcxr {

struct PlatformInit {
#ifdef __ANDROID__
    JavaVM* vm = nullptr; jobject activity = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY; EGLConfig config = nullptr; EGLContext context = EGL_NO_CONTEXT;
#else
    Display* display = nullptr; GLXFBConfig fbConfig = nullptr; GLXDrawable drawable = 0; GLXContext context = nullptr;
#endif
};

class Headset {
public:
    /** Creates the instance + session. False (with the reason in `error`) when there is no OpenXR runtime / headset. */
    bool create(const PlatformInit& p, const char* appName, std::string* error);
    /** Handles runtime events; sets `quit` when the session ends or the runtime asks the app to exit. */
    void pollEvents(bool* quit);
    bool running() const { return sessionRunning; }
    /** Renders one frame (both eyes) when the runtime wants one; returns false if nothing was rendered. */
    bool frame(Interaction& ia, Renderer& r);
    void destroy();

    std::string runtimeName, systemName;
    bool needsPlacement = true;      // place the model in front of the viewer on the first tracked frame
    // the left eye of the last frame, for a desktop mirror window
    GLuint mirrorTex = 0; int eyeW = 0, eyeH = 0;
    Pose head; bool headValid = false;
    /** Test hook: replaces the controllers' input after it is read (desktop --autotest); t = seconds since the first frame. */
    std::function<void(HandInput hands[2], const Pose& head, double t)> inputOverride;
    /** Called with the head pose while `needsPlacement` is set (the app places its panels; the model is placed here). */
    std::function<void(const Pose& head)> onPlaced;

private:
    XrInstance instance = XR_NULL_HANDLE; XrSystemId system = XR_NULL_SYSTEM_ID; XrSession session = XR_NULL_HANDLE;
    XrSpace appSpace = XR_NULL_HANDLE, viewSpace = XR_NULL_HANDLE;
    XrSessionState state = XR_SESSION_STATE_UNKNOWN; bool sessionRunning = false;
    bool stage = false;              // floor-level tracking space
    // passthrough (XR_FB_passthrough, Quest): a camera layer under the app's image, which is cleared to transparent
    XrPassthroughFB passthrough = XR_NULL_HANDLE; XrPassthroughLayerFB passthroughLayer = XR_NULL_HANDLE; bool passthroughOn = false;
    PFN_xrPassthroughStartFB ptStart = nullptr; PFN_xrPassthroughPauseFB ptPause = nullptr;
    void makePassthrough(bool enabled);
    /** One eye: its swapchain images, a framebuffer and a depth buffer. Rendered with 4x multisampling: on Android through
     *  GL_EXT_multisampled_render_to_texture (resolved on chip, free on tiled GPUs like the Quest's), on desktop into
     *  multisampled buffers (msFbo) blitted to the image. */
    struct Eye { XrSwapchain swapchain = XR_NULL_HANDLE; std::vector<GLuint> images; GLuint depth = 0, fbo = 0, msColor = 0, msFbo = 0; int w = 0, h = 0; };
    int samples = 1;
    /** The UI as compositor quad layers (see Interaction::uiLayers): the image without its popups (`uiBase`), the whole
     *  image (`uiFull`, which the popups show), and a cursor dot. XR_FB_composition_layer_settings: supersampled and
     *  sharpened where the runtime offers it. */
    struct Chain { XrSwapchain sc = XR_NULL_HANDLE; std::vector<GLuint> images; int w = 0, h = 0; };
    Chain uiBase, uiFull, cursorChain; bool uiTried = false, layerSettings = false; uint32_t maxLayers = 16;
    bool makeChain(Chain& c, int w, int h);
    bool makeUiChains(int w, int h);
    void updateUi(Interaction& ia, Renderer& r);
    Eye eyes[2]; int64_t colorFormat = 0; bool srgb = false;
    /** The hands, controllers and rays in a projection layer of their own over the UI's layers (a finger touching a
     *  window is in front of it), at the recommended resolution. */
    Eye over[2]; bool overOk = false;
    bool makeEye(Eye& eye, int w, int h);
    /** Hand tracking (XR_EXT_hand_tracking; aim and pinch from XR_FB_hand_tracking_aim where there is one). */
    XrHandTrackerEXT trackers[2] = {XR_NULL_HANDLE, XR_NULL_HANDLE}; bool handAim = false;
    /** XR_EXT_hand_interaction: the system's pinch / grasp as actions, while a hand (not a controller) is the input. */
    bool handInteraction = false, handProfile[2] = {false, false};
    void readProfiles();
    PFN_xrLocateHandJointsEXT locateJoints = nullptr; PFN_xrDestroyHandTrackerEXT destroyTracker = nullptr;
    void makeHandTrackers(bool enabled);
    XrActionSet actionSet = XR_NULL_HANDLE;
    XrAction aAim = XR_NULL_HANDLE, aGrip = XR_NULL_HANDLE, aSelect = XR_NULL_HANDLE, aSqueeze = XR_NULL_HANDLE, aStick = XR_NULL_HANDLE, aMenu = XR_NULL_HANDLE, aContext = XR_NULL_HANDLE;
    XrPath hands[2] = {XR_NULL_PATH, XR_NULL_PATH}; XrSpace aimSpace[2] = {}, gripSpace[2] = {};
    XrTime lastTime = 0, firstTime = 0;
    bool makeActions();
    bool makeSwapchains();
    void readHands(XrTime t, HandInput out[2]);
};

/** OpenXR pose -> core pose. */
inline Pose toPose(const XrPosef& p) { return {V3{p.position.x, p.position.y, p.position.z}, Q{p.orientation.x, p.orientation.y, p.orientation.z, p.orientation.w}, 1}; }

}  // namespace mcxr
