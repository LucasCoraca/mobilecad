# XR mode

On a headset (Meta Quest 3 and other Android OpenXR devices) MobileCAD starts in XR mode: `MainActivity` checks
`XrDevice.isHeadset` (head tracking, Android XR's OpenXR feature, or a system OpenXR runtime) and hands over to
`XrActivity`. Phones and tablets get the normal interface. It used to be a separate app (MobileCAD XR,
`app.mobilecad.xr`); it is now part of MobileCAD.

XR mode is the whole app: MobileCAD's Compose interface (its XR layout, `app.xr`) runs on a see-through virtual display
whose image becomes the window(s) around you, the controller ray is turned into touches on it and into MobileCAD's own 3D
view (`ViewportView.xrPointer`), and what that view draws (the model, preselection, the sketch being edited, Draft
feedback) is drawn in the room. Every workbench and tool behaves as on the phone.

| Path | What |
|---|---|
| `xr/core/` | The XR core, shared by the app and the desktop build: OpenXR session and controller actions (`xr_session`), interaction (`interaction`), hand gestures (`hands`), the hand / controller glTF models (`gltf_model`), GL renderer for OpenGL ES 3 and desktop GL 3.3 (`renderer`), meshes and face picking (`scene`). Third party: cgltf (MIT), stb_image (public domain) |
| `app/app/src/main/cpp/xr_android.cpp` | Android glue (`libmcxr`): the core on an OpenXR thread, or the touch preview |
| `app/app/src/main/java/app/mobilecad/xr/` | `XrDevice` (headset check), `XrActivity` (panel, input, scene sync), `XrKeyboard` |
| `app/app/src/main/assets/models/` | Hand and Quest controller models (WebXR Input Profiles, MIT) |
| `xr/desktop/` | Linux build of the core against any OpenXR runtime, with a mirror window, `--screenshot` and `--autotest` |
| `xr/sim/` | Headset-free test bench: Monado (open-source OpenXR runtime, *qwerty* driver) + Xvfb + noVNC in Docker |
| `xr/scenes/`, `xr/tools/` | FreeCAD's examples exported for the desktop build (`tools/export_scenes.py`, run with `engine/fcrun.sh`) |

## Testing without a headset

- **The whole app on the emulator:** a debug build started with
  `adb shell am start -n app.mobilecad/.MainActivity --ez xr true` opens XR mode; without an OpenXR runtime it shows a
  touch preview of the XR scene (one finger is the controller with its trigger pressed, two fingers look around).
  `--ez xr false` forces the normal interface.
- **The OpenXR path on a simulated headset:** `xr/sim/run.sh` (then http://localhost:6080/vnc.html), or
  `xr/sim/run.sh --test` for the automated check. In the browser, click into the *Monado!* panel: `W A S D` / `Q E` move
  the headset, arrows look, hold `Ctrl` / `Alt` for the left / right controller, left click = trigger, middle click =
  grip, `N` = menu, `T F G H` = thumbstick.
