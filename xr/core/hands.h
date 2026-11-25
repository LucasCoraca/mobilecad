// Tracked hands (XR_EXT_hand_tracking) turned into the same input a controller gives. (Their models: gltf_model.)
//
//   pinch (thumb + index) ......... trigger: touch the UI / the app's 3D view, tap = click, pinch + move = drag
//   pinch (thumb + middle) ........ B / Y: the view's context menu (a right click)
//   fist (held 0.15 s) ............ grip: grab and move the model / a window / the build plate (two fists: scale + turn)
//   both hands pinching ........... pinch to zoom: scale and turn the model between the hands
//   left palm towards you + pinch . menu (Quest's own menu gesture)
//   index finger on a window ...... touches it directly (poke), as on a touch screen
#pragma once
#include "xmath.h"

namespace mcxr {

// XR_EXT_hand_tracking's joint set
enum HandJoint {
    JPalm = 0, JWrist = 1,
    JThumbMeta = 2, JThumbProx = 3, JThumbDist = 4, JThumbTip = 5,
    JIndexMeta = 6, JIndexProx = 7, JIndexInter = 8, JIndexDist = 9, JIndexTip = 10,
    JMiddleMeta = 11, JMiddleProx = 12, JMiddleInter = 13, JMiddleDist = 14, JMiddleTip = 15,
    JRingMeta = 16, JRingProx = 17, JRingInter = 18, JRingDist = 19, JRingTip = 20,
    JLittleMeta = 21, JLittleProx = 22, JLittleInter = 23, JLittleDist = 24, JLittleTip = 25,
    kHandJoints = 26
};

struct HandInput;

/** Gesture state of one hand between frames (hysteresis, the smoothed ray). */
struct HandGestures {
    bool pinch = false, middle = false, fist = false, palmUp = false, init = false;
    float fistT = 0;            // how long the hand has been closed without pinching
    V3 dir{0, 0, -1};
};

/** Where the ray of a hand starts its line from (an estimated shoulder): the ray runs from there through the hand. */
V3 shoulderPoint(const Pose& head, bool left);
/** Fills a tracked hand's controller input (aim, grip, select, squeeze, menu, context) from its joints, or from the
 *  runtime's own aim and pinch strengths when it gives them (`runtimeAim`). */
void handGestures(HandInput& h, const Pose& head, bool left, HandGestures& g, float dt);

/** A hand's joints posed from its palm (palm frame: -Z towards the fingers, +Y the back of the hand): open, or curled
 *  into a pinch (thumb to index / middle) or a fist; `rest`: how far the middle, ring and little fingers curl anyway
 *  (a relaxed hand pinching). For tests and the simulator, where there are no hand trackers. */
void syntheticHand(HandInput& h, const Pose& palm, bool left, float pinch, float fist, float middlePinch = 0, float rest = 0);
/** The palm pose that puts this synthetic hand's ray (handGestures, without the runtime's aim) along `dir` from the shoulder. */
Pose palmForRay(const Pose& head, bool left, V3 dir, float reach = 0.42f);
/** The palm pose that puts the open synthetic hand's index fingertip at `tip`, the finger pointing along `dir`. */
Pose palmForTip(bool left, V3 tip, V3 dir);

/** The WebXR name of a joint (the hand models' bone names): "wrist", "index-finger-phalanx-proximal", ...; "" for the palm. */
const char* jointName(int joint);

}  // namespace mcxr
