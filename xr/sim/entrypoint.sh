#!/bin/bash
# Starts the virtual display, the window manager, VNC/noVNC and Monado with the qwerty driver; then runs "$@" (if any).
set -e
# Monado keeps its socket and lock in XDG_RUNTIME_DIR; it must exist (private) before the service starts
export XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-/tmp/xdg}; mkdir -p "$XDG_RUNTIME_DIR"; chmod 700 "$XDG_RUNTIME_DIR"
Xvfb :1 -screen 0 ${XRSIM_SCREEN:-1600x900x24} -nolisten tcp >/tmp/xvfb.log 2>&1 &
for i in $(seq 1 50); do xdpyinfo -display :1 >/dev/null 2>&1 && break; sleep 0.1; done
openbox >/tmp/openbox.log 2>&1 &
x11vnc -display :1 -forever -shared -nopw -quiet -rfbport 5900 >/tmp/x11vnc.log 2>&1 &
websockify --web /usr/share/novnc 6080 localhost:5900 >/tmp/novnc.log 2>&1 &
# qwerty = keyboard/mouse HMD + controllers (needs Monado's debug GUI, which is also where you type to move them)
export QWERTY_ENABLE=1 XRT_DEBUG_GUI=1 XRT_COMPOSITOR_LOG=info
# monado-service watches stdin (Enter quits); give it a pipe that never closes, /dev/null cannot be polled
(sleep infinity | monado-service >/tmp/monado.log 2>&1) &
for i in $(seq 1 100); do ls ${XDG_RUNTIME_DIR:-/tmp}/monado_comp_ipc >/dev/null 2>&1 && break; sleep 0.2; done
echo "xrsim ready: noVNC http://<host>:6080/vnc.html  (monado log: /tmp/monado.log)"
if [ $# -gt 0 ]; then exec "$@"; else exec sleep infinity; fi
