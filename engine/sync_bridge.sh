#!/bin/bash
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}   # the emulator, not a phone that may be plugged in
# Dev loop: copy engine/python/{mobilecad,shims} into the installed debug app's runtime and restart it.
ADB=$HOME/Android/sdk/platform-tools/adb; E=/home/lucas/Documents/freecadmobile/engine/python
T=/data/local/tmp/fcbridge; $ADB shell rm -rf $T; $ADB shell mkdir -p $T
$ADB push $E/mobilecad $T/ > /dev/null; $ADB push $E/shims/. $T/ > /dev/null; $ADB push $E/vendor/. $T/ > /dev/null
Q=/tmp/claude-1000/fcqtres; rm -rf $Q; python3 - "$Q" <<'PY'
import os, sys, shutil, xml.etree.ElementTree as ET
S = os.path.expanduser('~/Android/freecad-src/src/Mod'); Q = sys.argv[1]
for m in ('Draft', 'BIM'):
    d = os.path.join(S, m, 'Resources', 'ui')
    for f in os.listdir(d):
        if f.startswith('preferences'): os.makedirs(Q + '/ui', exist_ok=True); shutil.copy(os.path.join(d, f), Q + '/ui/' + f)
for dp, _, fs in os.walk(S):
    for f in fs:
        if f.endswith('.qrc'):
            for q in ET.parse(os.path.join(dp, f)).getroot().iter('qresource'):
                pre = q.get('prefix', '/').strip('/')
                for fe in q.iter('file'):
                    if fe.text.endswith('.ui'):
                        dst = os.path.join(Q, pre, fe.get('alias') or fe.text); os.makedirs(os.path.dirname(dst), exist_ok=True)
                        shutil.copy(os.path.join(dp, fe.text), dst)
PY
$ADB push $Q $T/qtres > /dev/null
M=/tmp/claude-1000/fcmodui; rm -rf $M; mkdir -p $M; (cd $HOME/Android/freecad-src/src/Mod && find . -name '*.ui' -not -path '*/Gui/*' -exec cp --parents {} $M/ \;)
(cd $HOME/Android/freecad-src/src/Mod && cp -r --parents Fem/femguiobjects Fem/femviewprovider Fem/femtaskpanels Fem/fempreferencepages Fem/femguiutils TechDraw/TechDrawTools MeshPart/Gui/MeshFlatteningCommand.py Part/BasicShapes/CommandShapes.py $M/ && cp -r TemplatePyMod $M/ && for g in */Gui/Resources/ui; do m=${g%%/*}; mkdir -p $M/$m/Resources/ui; cp $g/*.ui $M/$m/Resources/ui/; done)
$ADB shell rm -rf /data/local/tmp/fcmodui; $ADB push $M /data/local/tmp/fcmodui > /dev/null
$ADB shell "run-as app.mobilecad sh -c 'cp -r /data/local/tmp/fcmodui/* files/freecad/Mod/'" 
$ADB shell "run-as app.mobilecad sh -c 'cp -r $T/* files/freecad/bridge/ && rm -rf files/freecad/bridge/mobilecad/__pycache__'"
$ADB shell am force-stop app.mobilecad
echo synced
