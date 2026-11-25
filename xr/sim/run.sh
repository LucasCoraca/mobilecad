#!/bin/bash
# Headset-free test of MobileCAD XR: builds the desktop app and runs it against Monado's simulated headset in Docker.
#   sim/run.sh            interactive: open http://localhost:6080/vnc.html (or this machine's IP) and drive it
#   sim/run.sh --test     automated check (scripted controller: pick, grab + move, panel button, true size on / off; then
#                         the same with tracked hands, and a two-hand pinch zoom; then walk mode on the BIM example:
#                         WALK, teleport, a wall refused, snap turn, WALK off; then the windows: the model in
#                         front of one, the left controller's menu button hiding / showing them); exit 0 = pass
#   sim/run.sh --hands    interactive, the controllers shown as hands (trigger = pinch, grip = fist)
#
# Controls in the browser (click into the right-hand "Monado!" panel first):
#   W A S D / Q E ... move the headset          arrows or right-drag ... look around
#   hold Ctrl ....... drive the LEFT controller  hold Alt ............ drive the RIGHT controller
#   left click ...... trigger (click = select a face, hold = grab and move the model)
#   middle click .... grip                       N ... menu (next file)   T F G H ... thumbstick
set -e
R=$(cd "$(dirname "$0")/.." && pwd)
docker image inspect mobilecad-xrsim >/dev/null 2>&1 || docker build -t mobilecad-xrsim "$R/sim"
docker run --rm -v "$R":/src -w /src --entrypoint bash mobilecad-xrsim -c \
  "cmake -S desktop -B build/desktop -DCMAKE_BUILD_TYPE=Release >/dev/null && cmake --build build/desktop -j\$(nproc) 2>&1 | grep -E 'error|Built target'"
docker rm -f mobilecad-xrsim-run >/dev/null 2>&1 || true
if [ "$1" = "--test" ]; then
  docker run --rm --name mobilecad-xrsim-run -v "$R":/src -w /src mobilecad-xrsim bash -c \
    "timeout 120 build/desktop/mobilecad-xr-desktop --autotest --screenshot build/autotest.ppm --frames 1 scenes/PartDesignExample.mcxr 2>&1 | grep -E 'runtime|autotest|wrote'; c=\${PIPESTATUS[0]}
     timeout 120 build/desktop/mobilecad-xr-desktop --autotest --hands scenes/PartDesignExample.mcxr 2>&1 | grep -E 'autotest'; h=\${PIPESTATUS[0]}
     timeout 120 build/desktop/mobilecad-xr-desktop --autotest --walk --screenshot build/walktest.ppm --frames 1 scenes/BIMExample.mcxr 2>&1 | grep -E 'autotest|wrote'; w=\${PIPESTATUS[0]}
     timeout 120 build/desktop/mobilecad-xr-desktop --autotest --sheets --ui scenes/PartDesignExample.mcxr 2>&1 | grep -E 'autotest'; u=\${PIPESTATUS[0]}
     [ \$c = 0 ] && [ \$h = 0 ] && [ \$w = 0 ] && [ \$u = 0 ]"
  exit $?
fi
docker run -d --name mobilecad-xrsim-run -p 6080:6080 -v "$R":/src -w /src mobilecad-xrsim \
  bash -c "(sleep 6; xrsim-arrange) & build/desktop/mobilecad-xr-desktop $1 scenes > /tmp/app.log 2>&1; sleep infinity" >/dev/null
echo "MobileCAD XR is running on a simulated headset."
echo "Open http://localhost:6080/vnc.html (or http://$(hostname -I | awk '{print $1}'):6080/vnc.html) and press Connect."
echo "Stop it with: docker rm -f mobilecad-xrsim-run"
