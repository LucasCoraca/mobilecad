# Shared settings of the native build scripts (sourced). Everything can be overridden from the environment, so the same
# scripts run on a developer machine (defaults below) and on a clean build server (F-Droid: see fdroid/README.md).
#   FCDEPS        build root: src/ (source archives, engine/sources.lock), <abi>/ (install prefix per ABI), build dirs
#   ANDROID_HOME  Android SDK;  ANDROID_NDK  NDK 29 (default: $ANDROID_HOME/ndk/29.0.14206865)
#   JOBS          parallel jobs (default: all cores)
ENGINE=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
D=${FCDEPS:-$HOME/Android/fcdeps}; SRC=$D/src
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/sdk}
NDK=${ANDROID_NDK:-$ANDROID_HOME/ndk/29.0.14206865}
TB=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
J=${JOBS:-$(nproc)}
# CMake >= 3.25 and Ninja: the system's (Debian), else the host venv's (pip), else the SDK's
if command -v cmake > /dev/null && cmake --version | awk 'NR==1{split($3,v,".");exit !(v[1]>3||(v[1]==3&&v[2]>=25))}'; then CMK=$(command -v cmake)
elif [ -x $D/hostpy/bin/cmake ]; then CMK=$D/hostpy/bin/cmake
else CMK=$ANDROID_HOME/cmake/3.22.1/bin/cmake; fi
NINJA=$(command -v ninja || echo $D/hostpy/bin/ninja); [ -x "$NINJA" ] || NINJA=$ANDROID_HOME/cmake/3.22.1/bin/ninja
abi_triple() { case $1 in x86_64) echo x86_64-linux-android;; arm64-v8a) echo aarch64-linux-android;; *) echo "unknown ABI $1" >&2; return 1;; esac; }
# unpack <archive in $SRC> <directory under $SRC>: extracts once, dropping the archive's top-level directory
# (also for archives whose members start with "./")
unpack() {
  [ -d "$SRC/$2" ] && return 0
  local t="$SRC/$2.tmp"; rm -rf "$t"; mkdir -p "$t"; tar xf "$SRC/$1" -C "$t"
  local top=("$t"/*); if [ ${#top[@]} = 1 ] && [ -d "${top[0]}" ]; then mv "${top[0]}" "$SRC/$2"; rmdir "$t"; else mv "$t" "$SRC/$2"; fi
}
# apply_patch <patch> <directory>: applies a patch once (a stamp file records it)
apply_patch() { [ -f "$2/.patched-$(basename $1)" ] || { patch -d "$2" -p1 --forward < "$1" && touch "$2/.patched-$(basename $1)"; }; }
# freecad_src: prints FreeCAD's source tree, unpacking + patching the locked commit when there is no checkout
freecad_src() {
  local s=${FREECAD_SRC:-$HOME/Android/freecad-src}
  if [ ! -d "$s" ]; then
    s=$SRC/freecad
    if [ ! -d $s ]; then
      local fc og gs; fc=$(cd $SRC && ls freecad-*.tar.gz | head -1); og=$(cd $SRC && ls ondselsolver-*.tar.gz | head -1); gs=$(cd $SRC && ls gsl-*.tar.gz | head -1)
      unpack $fc freecad.unpack; unpack $og freecad.unpack/src/3rdParty/OndselSolver.unpack; unpack $gs freecad.unpack/src/3rdParty/GSL.unpack
      rmdir $SRC/freecad.unpack/src/3rdParty/OndselSolver $SRC/freecad.unpack/src/3rdParty/GSL 2>/dev/null || true
      mv $SRC/freecad.unpack/src/3rdParty/OndselSolver.unpack $SRC/freecad.unpack/src/3rdParty/OndselSolver
      mv $SRC/freecad.unpack/src/3rdParty/GSL.unpack $SRC/freecad.unpack/src/3rdParty/GSL
      patch -d $SRC/freecad.unpack -p1 < $ENGINE/patches/freecad-android.patch >&2 && mv $SRC/freecad.unpack $s
    fi
  fi
  echo $s
}
