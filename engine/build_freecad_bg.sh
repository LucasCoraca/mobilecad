#!/bin/bash
# configure + build + install FreeCAD for one ABI (background helper)
ABI=$1
/home/lucas/Documents/freecadmobile/engine/build_freecad.sh $ABI configure > /dev/null 2>&1
B=$HOME/Android/fcdeps/build-freecad-$ABI
cd $B && $HOME/Android/sdk/cmake/3.22.1/bin/cmake --build . -j30 -- -k 0 > build.log 2>&1
$HOME/Android/sdk/cmake/3.22.1/bin/cmake --install . > install.log 2>&1
echo "FREECAD BUILD DONE $(grep -c '^FAILED' build.log) failures"
