// What the user does in XR, independent of OpenXR: hands in, model / panel poses, highlights and callbacks out. The OpenXR
// session (xr_session) and the headset-free preview both feed it HandInput every frame.
//
//   ray on the UI panel:  trigger = touch (tap, drag)   grip = move the panel (with sheets: that window)
//                         thumbstick up/down = scroll
//   ray on the model:     trigger click = pick the face / edge (into the document's selection)
//                         grip, or trigger held = grab: the model follows the hand (move + rotate)
//   both hands grabbing:  scale and turn the model between the hands
//   thumbstick (not on the panel): up/down scales, left/right turns the model
//   menu / A / X:         onMenu (the app shows / recentres the panel, or switches documents)
//   menu button (round, on the left controller; right ray + trigger): hides all the windows / shows them again in front
//                         of where you look
//
// With `onViewport` set (MobileCAD XR) the space around the panel is the app's 3D view: the trigger anywhere off the panel
// is a finger on that view (tap, drag, double tap) and pointing is a hovering mouse, so every tool of the app (picking,
// the sketcher, Draft points, drag arrows) works on the model in the room; the grip moves the model,
// B / Y opens the view's context menu. The app sends back what its view draws: preselection, overlay, the sketch.
//   scale badge (under the panel): shows the model's scale; its button switches true size (1 mm = 1 mm) on / off
//   walk (badge's WALK button): true size with the model's ground plane on the floor, to walk through big models
//                (buildings) as in VR games: thumbstick forward aims a teleport arc, release = teleport there (the ground
//                or a level surface of the model: a floor, a slab, a roof), pull back = cancel; left / right = snap turn
//   hands (no controllers): see hands.h; pinch = trigger, fist = grip, an index finger touches the windows directly
//   build plate: a tabletop on the model's ground plane (FreeCAD's XY plane) around its origin, with a grid and the axes;
//                the model stands on it. Its rim is a handle: grip on the rim picks the table up and carries it (it
//                turns with your wrist and stays level), trigger on the rim slides it along where the ray points
#pragma once
#include <functional>
#include <map>
#include <string>
#include <vector>
#include "gltf_model.h"
#include "hands.h"
#include "renderer.h"
#include "scene.h"
#include "xmath.h"

namespace mcxr {

struct HandInput {
    bool active = false;        // pose tracked this frame
    Pose aim, grip;             // world (stage) space, scale 1; aim points down -Z
    float select = 0;           // trigger value 0..1
    float squeeze = 0;          // grip value 0..1 (0 on controllers without one)
    float stickX = 0, stickY = 0;
    bool menu = false;          // menu / A / X button
    bool context = false;       // B / Y button: the view's context menu
    // a tracked hand (no controller in it): its joints (world; radius in m), the rest read from them (handGestures)
    bool hand = false;
    Pose joints[kHandJoints]; float radii[kHandJoints] = {};
    bool runtimeAim = false;    // aim and pinch strengths from the runtime (XR_FB_hand_tracking_aim)
    float pinchIndex = 0, pinchMiddle = 0; bool menuGesture = false, systemGesture = false;
    /** The system's own pinch and grasp for this hand (XR_EXT_hand_interaction: what the headset's own UI uses). */
    bool sysGestures = false; float sysPinch = 0, sysGrasp = 0;
    bool noRay = false;         // no ray shown or used (the palm turned to the eyes: the menu gesture)
    bool poke = false;          // the index finger is at a window, touching it directly (set by Interaction; no ray shown)
};

/** One part of the UI image shown as its own flat surface: the pixel rectangle (x y w h) at a pose relative to the panel,
 *  `pxPerM` pixels per metre. */
struct Sheet { float x = 0, y = 0, w = 0, h = 0; Pose local, home; float pxPerM = 1600; };   // home: where placePanel puts it

/** The app's interface in the world. Without `sheets` it is one flat surface: local +Z faces the user, X right, Y up, size
 *  in metres. With `sheets` (MobileCAD XR) the image is cut into surfaces arranged around the user, see-through where the
 *  UI draws nothing (`seeThrough`: the image's alpha, read back into `mask`, decides what the ray hits). */
struct Panel {
    bool visible = false;
    Pose pose;                  // centre (with sheets: the place the user stands, facing the UI)
    float width = 1.0f, height = 0.625f;
    int pxW = 1600, pxH = 1000; // texture / touch coordinate space
    GLuint tex = 0; bool external = false;
    GLuint sharp = 0;           // with sheets: a mipmapped copy of the image, what the sheets show (sharper, no shimmer)
    std::vector<Sheet> sheets; bool seeThrough = false; int badgeSheet = -1;   // the scale badge hangs under this sheet
    /** Windows over the UI (menus, popups, dialogs: x y w h px, bottom to top). Each is shown whole, on the plane of the
     *  sheet it mostly covers and a little in front of it, and is left out of the other sheets: a menu dropping from the
     *  bars into the view area is not cut in two. */
    std::vector<float> popups;
    Sheet popupSheet(size_t i) const;
    std::vector<uint8_t> mask; int maskW = 0, maskH = 0;     // alpha at 1/maskScale of the image
    static constexpr int maskScale = 8;
    bool solid(float px, float py) const;                    // something drawn at (or right next to) this pixel
};

enum PanelAction { PanelDown = 0, PanelMove = 1, PanelUp = 2, PanelHover = 3, PanelLeave = 4, PanelScroll = 5 };

class Interaction {
public:
    Scene scene;
    Pose model{};               // scene (mm) -> world (m)
    Panel panel;
    ElemRef hover[2];
    /** The document's selection. With `hostSelection` the app owns it (onPick reports clicks and the app sends the
     *  selection back); otherwise clicks toggle it here. */
    std::vector<ElemRef> selected; bool hostSelection = false;
    std::function<void(const ElemRef& ref, V3 modelPoint)> onPick;          // invalid ref = click on empty space
    std::function<void(int action, float x, float y)> onPanelPointer;      // x, y in panel pixels (scroll: y = amount)
    std::function<void()> onMenu;
    /** True size: the model at its real size, 1 mm = 1 mm. Scaling gestures then only move and turn it (free scaling
     *  outside this mode snaps to 1:1 on the way through). Toggled by the scale badge under the panel. */
    bool trueScale = false;
    void setTrueScale(bool on);
    /** Walk mode (see the top): you move by teleporting, the world is moved under you (the UI stays with you). Off puts
     *  the model back where it was. */
    bool walk = false;
    void setWalk(bool on);
    /** Teleports the viewer to `world` (a point on a floor): the model is moved so that point is under their feet. */
    void teleportTo(V3 world);
    float floorY = 0;            // world height of the floor (0 in stage space)
    /** Passthrough (the headset's cameras: your room behind the model and the UI). `passthroughAvailable` is set by the
     *  session; the badge's PASSTHROUGH button switches `passthrough`. */
    bool passthroughAvailable = false, passthrough = true;
    /** "1:1", "1:4.2", "3:1": world size : real size. */
    std::string scaleText() const;
    /** Centre of a scale badge button (world), for scripted tests: 0 TRUE SIZE / FIT VIEW, 2 WALK. */
    V3 badgeButton(int which = 0) const;
    /** The teleport arc's landing point while aiming (world), and whether one may stand there; for scripted tests. */
    bool teleportTarget(V3* p) const { if (tpHand < 0 || tpArc.empty()) return false; *p = tpPoint; return tpValid; }
    bool tpHandAiming() const { return tpHand >= 0 && !tpArc.empty(); }
    V3 tpArcEnd() const { return tpArc.empty() ? V3{} : tpArc.back(); }
    Pose walkReturnPose() const { return walkReturn; }
    // ---- the app's 3D view (see the top)
    std::function<void(int action, V3 o, V3 d, float depthMm)> onViewport;   // 0 press 1 drag 2 release 3 hover 4 leave 5 cancel; model mm
    std::function<void()> onContextMenu;
    ElemRef hostHover;                       // the app's preselection
    float fade = 1;                          // < 1: the model drawn see-through (the app dims it while a sketch is edited)
    /** What the app's view draws over the model, in model coordinates (mm): lines (x y z x y z r g b), dots (x y z r g b
     *  sizeDp), text labels. */
    struct Overlay { std::vector<float> lines, dots; std::vector<std::pair<V3, std::string>> labels; } overlay;
    /** The sketch being edited, drawn by the app into a texture lying on the sketch plane: corners TL TR BR BL (model mm). */
    GLuint sketchTex = 0; bool sketchVisible = false; V3 sketchCorners[4];
    /** Its text labels (constraint glyphs, dimensions, sizes while drawing, snap hints), facing the viewer at a constant
     *  angular size: point (model mm), box centre offset and size (dp, y down), cell in sketchLabelTex (premultiplied). */
    struct SketchLabel { V3 at; float cx, cy, w, h; float uv[4]; };
    std::vector<SketchLabel> sketchLabels; GLuint sketchLabelTex = 0;
    /** The build plate (see the top). Its radius (model mm) covers the model and is at least 15 cm in the room. */
    bool plate = true;
    float plateRadius() const;
    float plateGrid() const;                                 // grid spacing (mm): 1, 2, 5 x 10^n, ~8 cells to the rim
    // optional info card above the left hand
    GLuint cardTex = 0; int cardW = 0, cardH = 0;

    /** Puts the model ~0.7 m in front of the viewer, a little below eye height, about 40 cm across. */
    void placeModel(const Pose& head);
    /** A new document: the build plate stays where it is, sized for the new contents. */
    void fitScale();
    bool placed = false;                     // placeModel ran (the user has a table to keep)
    /** Puts the UI panel to the left-front of the viewer, turned towards them (with sheets: each back to its home). */
    void placePanel(const Pose& head);
    void update(const HandInput hands[2], float dt);
    void render(Renderer& r, const M4& viewProj, V3 eye);
    /** The hands, controllers and their rays. Drawn by render() unless `pointersSeparate` (the headset then draws them in a
     *  layer of their own over the UI's windows, so a finger touching a window is seen in front of it). */
    void renderPointers(Renderer& r, const M4& viewProj, V3 eye);
    /** The UI's windows are compositor layers over the scene: this draws, into a transparent image over them, the parts of
     *  the model that are nearer than a window, so a part held in front of a window hides it (as it would in the room). */
    void renderOverUi(Renderer& r, const M4& viewProj, V3 eye);
    ElemRef hoverRef() const;               // the preselected element the scene highlights
    /** The menu button on the left controller (the right ray's trigger on it): hides all the windows, or brings them back
     *  in front of where you look. A dialog or menu opening while they are hidden brings them back too. */
    void toggleUi(const Pose& head);
    bool uiHidden = false;
    Pose menuButtonPose() const;            // world pose of the menu button (for scripted tests too)
    bool pointersSeparate = false;
    bool showControllers = true;
    /** The hand / controller models (assets/models: hand-left, hand-right, touch-plus-left, touch-plus-right .glb). */
    bool setModel(const std::string& name, const std::vector<uint8_t>& glb);             // false in the phone preview: its "controller" is your finger at the eye
    Pose headPose{V3{0, 1.6f, 0}, Q{}, 1};   // the viewer's head (set by the session each frame): for hand rays
    /** Reads the panel image's alpha for hit testing (call when the image changed, at most a few times a second). */
    void updateMask(Renderer& r);
    /** The panel image changed: refresh its mipmapped copy (with sheets), or mark it for the compositor layers. */
    void panelChanged(Renderer& r);
    /** The headset shows the UI's sheets and popups as compositor quad layers (sharper: sampled once, at display
     *  resolution) instead of drawing them into the eye images; `uiDirty`: the image changed since the layers got it. */
    bool uiLayers = false, uiDirty = true;
    struct UiQuad { Pose pose; float w, h; float x, y, pw, ph; bool popup; };    // world centre / size (m), image rect (px)
    std::vector<UiQuad> uiQuads() const;
    /** Where the rays meet the UI (world), for cursors drawn over the layers. */
    std::vector<V3> uiCursors() const;
    Pose sheetPose(const Sheet& s) const { return compose(Pose{panel.pose.pos, panel.pose.rot, 1}, s.local); }

private:
    // hands: gestures, touching windows with the index finger, pinching with both hands to zoom; their meshes
    HandGestures gest[2];
    bool pokeTouch[2] = {false, false}; float pokeDepth[2] = {1, 1};
    bool pokeAt(V3 tip, float* depth, Pose* aim) const;
    void handInput(HandInput hands[2], float dt);
    bool pinchZoom = false;
    GltfModel handModel[2], ctrlModel[2];
    struct Grab { bool on = false; Pose handStart, objStart; float t = 0; V3 aimStart; bool panel = false, plate = false; bool slide = false; V3 slideStart; int sheet = -1; };
    Grab grab[2];
    bool twoHand = false; float twoDist = 0; Pose twoModel; V3 twoMid, twoDir;
    bool prevTrig[2] = {false, false}, prevSqueeze[2] = {false, false}, prevMenu[2] = {false, false};
    int pointerHand = -1;                     // hand holding a touch on the panel
    int hoverHand = -1; float hoverX = 0, hoverY = 0;
    float triggerT[2] = {0, 0}; V3 triggerStart[2]; bool triggerOnModel[2] = {false, false};
    Pose fitPose; bool fitSaved = false;
    bool placedEmpty = false; Pose placeHead;  // placed before the document had a shape: size it again when it gets one      // the pose to return to when true size is switched off
    V3 viewer{0, 1.6f, 0};
    bool triggerOnMenuButton[2] = {false, false}; size_t hiddenPopups = 0; GLuint menuBtnTex[4] = {0, 0, 0, 0};
    bool menuButtonRay(const Pose& aim, float* t) const;
    bool onUi(int i) const { return target[i].panel || target[i].badge || target[i].menuButton; }
    bool triggerOnBadge[2] = {false, false}; int badgeHot = -1;    // the badge button pointed at (0 true size, 1 passthrough, 2 walk)
    // walk mode: the teleport arc of the hand aiming (`tpHand`), where it lands and whether one may stand there
    bool aiming[2] = {false, false}, snapReady[2] = {true, true}; int tpHand = -1;
    std::vector<V3> tpArc; bool tpValid = false; V3 tpPoint;
    Pose walkReturn; bool walkTrue = false;                        // the view to go back to
    void walkStick(int i, const HandInput& h);
    void teleportArc(const Pose& aim);
    float groundY() const;                                         // world height of the model's ground plane (its XY plane)
    void level();                                                  // the model upright (its Z up), turned only about the vertical
    int badgeButtonAt(float u) const;
    int viewHand = -1; bool viewHover = false; bool prevStick[2] = {false, false};
    std::map<std::string, std::pair<GLuint, std::pair<int, int>>> labelTex;
    void modelRay(const Pose& aim, V3* o, V3* d) const;
    float depthMm(int hand, const Pose& aim) const;
    GLuint badgeTex = 0; std::string badgeDrawn;
    Pose badgePose() const;
    float lowestY() const;
    struct Target { bool panel = false, badge = false, rim = false, menuButton = false; float t = 1e30f; float px = 0, py = 0; int sheet = -1; ElemRef elem; V3 point; };
    std::map<int, GLuint> plateTexs; bool rimHot = false;    // plate textures by cell count (and highlight)
    Pose platePose() const;
    bool rimRay(const Pose& aim, float* t) const;
    void drawPlate(Renderer& r, const M4& vp);   // the build plate's disc, grid label and axes
    Target target[2];
    HandInput last[2];
    Target aimAt(const Pose& aim) const;
    bool panelRay(const Pose& aim, float* t, float* px, float* py, bool clamp, int* sheet = nullptr) const;
    int pointerSheet = -1;                    // the sheet a touch started on (drags stay on its plane)
    bool badgeRay(const Pose& aim, float* t, float* u) const;
};

}  // namespace mcxr
