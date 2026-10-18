#!/bin/bash
# NumPy for the embedded Android CPython (FEM, CAM and BIM import it).  No Android wheels exist, so: meson cross build
# with NDK clang; the host's Python 3.14 runs the build scripts but reports the Android interpreter's sysconfig, and
# pkg-config resolves the Android python-3.14.pc.  Usage: build_numpy.sh <abi>
set -euo pipefail
ABI=${1:-x86_64}
D=$HOME/Android/fcdeps; P=$D/$ABI; PY=$P/python/prefix; SRC=$D/src/numpy/numpy-2.3.5
NDK=$HOME/Android/sdk/ndk/29.0.14206865; TB=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
case $ABI in x86_64) T=x86_64-linux-android; CPU=x86_64;; arm64-v8a) T=aarch64-linux-android; CPU=aarch64;; esac
B=$D/build-numpy-$ABI; rm -rf $B; mkdir -p $B/tools $B/sysconf
cp $PY/lib/python3.14/_sysconfigdata__android_$T.py $B/sysconf/
# the Android python-3.14.pc carries an unsubstituted "$(BLDLIBRARY)"; use a fixed copy
mkdir -p $B/pc; for f in $PY/lib/pkgconfig/python*.pc; do sed 's/\$(BLDLIBRARY)/-lpython3.14/' $f > $B/pc/$(basename $f); done
cat > $B/tools/python <<PYW
#!/bin/bash
export _PYTHON_SYSCONFIGDATA_NAME=_sysconfigdata__android_$T PYTHONPATH=$B/sysconf
exec $D/hostpy/bin/python "\$@"
PYW
cat > $B/tools/pkg-config <<PKW
#!/bin/bash
export PKG_CONFIG_LIBDIR=$B/pc
exec pkg-config --define-variable=prefix=$PY "\$@"
PKW
chmod +x $B/tools/*
cat > $B/cross.ini <<INI
[binaries]
c = '$TB/${T}26-clang'
cpp = '$TB/${T}26-clang++'
ar = '$TB/llvm-ar'
strip = '$TB/llvm-strip'
pkg-config = '$B/tools/pkg-config'
python = '$B/tools/python'
cython = '$D/hostpy/bin/cython'
[host_machine]
system = 'android'
cpu_family = '$CPU'
cpu = '$CPU'
endian = 'little'
[properties]
longdouble_format = 'IEEE_QUAD_LE'
needs_exe_wrapper = true
[built-in options]
c_link_args = ['-L$PY/lib', '-lpython3.14']
cpp_link_args = ['-L$PY/lib', '-lpython3.14']
INI
export PATH=$D/hostpy/bin:$PATH
cd $B
$D/hostpy/bin/python $SRC/vendored-meson/meson/meson.py setup build $SRC --cross-file cross.ini --prefix=/usr/local --buildtype=release -Dblas=none -Dlapack=none -Dallow-noblas=true \
  -Ddisable-optimization=false -Dcpu-baseline=min -Dcpu-dispatch=none > setup.log 2>&1 || { tail -40 setup.log; exit 1; }
ninja -C build -j24 > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
DESTDIR=$B/stage $D/hostpy/bin/python $SRC/vendored-meson/meson/meson.py install -C build > install.log 2>&1 || { tail -20 install.log; exit 1; }
SP=$(find $B/stage -type d -name numpy -path "*-packages*" | head -1)
rm -rf $PY/lib/python3.14/site-packages/numpy; mkdir -p $PY/lib/python3.14/site-packages; cp -r $SP $PY/lib/python3.14/site-packages/
find $PY/lib/python3.14/site-packages/numpy -depth -type d \( -name tests -o -name _examples \) -exec rm -rf {} +
echo "NUMPY DONE $ABI -> $PY/lib/python3.14/site-packages/numpy"
