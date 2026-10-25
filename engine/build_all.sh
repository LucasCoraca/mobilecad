#!/bin/bash
# Builds the whole native runtime for one ABI from source and stages it into the app (what F-Droid runs before Gradle).
# Usage: build_all.sh <abi>      (x86_64 | arm64-v8a; settings: engine/env.sh; Debian packages: fdroid/README.md)
# Each finished step leaves $FCDEPS/<abi>/.done-<step>, so a rerun continues where it stopped.
set -euo pipefail
ABI=${1:?usage: build_all.sh <abi>}
E=$(dirname "$(readlink -f "$0")"); . $E/env.sh
mkdir -p $D/$ABI
step() {   # step <name> <command...>
  local s=$D/$ABI/.done-$1; shift
  [ -f $s ] && return 0
  echo "== $(date +%H:%M:%S) $(basename $s | cut -d- -f2-) ($ABI)"
  "$@"; touch $s
}
host_step() { local s=$D/.done-$1; shift; [ -f $s ] && return 0; echo "== $(date +%H:%M:%S) $(basename $s | cut -d- -f2-) (host)"; "$@"; touch $s; }

$E/fetch_sources.sh
step python $E/build_python.sh $ABI                 # host CPython 3.14 + build venv, then CPython for Android
host_step swig $E/build_swig.sh
host_step qt-host $E/build_deps.sh qt-host
for s in qt boost xerces fmt yaml eigen icu freetype harfbuzz vtk occt; do step $s $E/build_deps.sh $s $ABI; done
step med $E/build_med.sh $ABI
step freecad $E/build_freecad.sh $ABI
step numpy $E/build_numpy.sh $ABI
step gmsh $E/build_gmsh.sh $ABI
step ifcopenshell $E/build_ifcopenshell.sh $ABI
step ccx $E/build_ccx.sh $ABI
$D/hostpy/bin/python -I $E/package_runtime.py $ABI $E/../app
echo "== $(date +%H:%M:%S) runtime for $ABI staged"
