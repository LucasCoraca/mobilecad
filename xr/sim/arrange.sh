#!/bin/bash
# Lays out the simulator desktop: the headset view (Monado's compositor window, both eyes) on the left, Monado's debug
# GUI on the right (keyboard + mouse go there to drive the simulated headset/controllers), the app's own mirror hidden.
export DISPLAY=${DISPLAY:-:1}
for i in $(seq 1 50); do C=$(xdotool search --name '^Monado$' | head -1); G=$(xdotool search --name 'Monado!' | head -1); [ -n "$C" ] && [ -n "$G" ] && break; sleep 0.2; done
read SW SH < <(xdpyinfo | awk '/dimensions:/{split($2,a,"x"); print a[1], a[2]}')
GW=$((SW / 3)); CW=$((SW - GW)); CH=$((CW * 9 / 16)); [ $CH -gt $SH ] && CH=$SH
[ -n "$C" ] && { xdotool windowmove $C 0 0; xdotool windowsize $C $CW $CH; }
[ -n "$G" ] && { xdotool windowmove $G $CW 0; xdotool windowsize $G $GW $SH; xdotool windowactivate $G 2>/dev/null; }
for M in $(xdotool search --name 'MobileCAD XR'); do xdotool windowminimize $M 2>/dev/null; done
true
