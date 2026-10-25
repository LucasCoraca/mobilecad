#!/bin/bash
# Builds CPython 3.14.4 for Android entirely from upstream source archives (no prebuilt binaries):
#   1. host ("build") CPython in $FCDEPS/hostpy-base, plus the build-tool venv $FCDEPS/hostpy from
#      engine/host-requirements.txt (both only once: skipped when the venv already imports everything);
#   2. CPython's Android dependencies with NDK 29: bzip2, libffi, xz, zstd (static), OpenSSL, SQLite (shared,
#      renamed lib*_python.so) -- same versions, patches and options as python.org's release build
#      (github.com/beeware/cpython-android-source-deps; patches in engine/patches/python-android/, BSD-3-Clause);
#   3. CPython for Android, configured exactly like the source tree's Android/android.py (configure-host), and
#      packaged like `android.py package` into $FCDEPS/<abi>/python (prefix/ + android.py, android-env.sh, testbed/),
#      i.e. a drop-in replacement for the python.org "python-3.14.4-<triple>.tar.gz" release package.
# Usage: build_python.sh <abi>      abi: x86_64 | arm64-v8a
# Sources (engine/sources.lock, fetched by engine/fetch_sources.sh): Python-3.14.4.tar.xz openssl-3.0.19.tar.gz
#   sqlite-autoconf-3500400.tar.gz bzip2-1.0.8.tar.gz libffi-3.4.4.tar.gz xz-5.4.6.tar.gz zstd-1.5.7.tar.gz pypi/*
# Debian packages: build-essential pkg-config xz-utils perl patch
#   libssl-dev zlib1g-dev libffi-dev libsqlite3-dev libbz2-dev liblzma-dev libzstd-dev   (modules of the host Python)
# Env: FCDEPS, ANDROID_HOME, ANDROID_NDK, JOBS (see env.sh); ANDROID_API_LEVEL (default 26 = the app's minSdk;
#   python.org builds for 24)
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/env.sh"
ABI=${1:?usage: build_python.sh <abi>}; TRIPLE=$(abi_triple $ABI)
API=${ANDROID_API_LEVEL:-26}
PYVER=3.14.4; PYMM=${PYVER%.*}
PATCHES=$ENGINE/patches/python-android
for f in Python-$PYVER.tar.xz openssl-3.0.19.tar.gz sqlite-autoconf-3500400.tar.gz bzip2-1.0.8.tar.gz libffi-3.4.4.tar.gz xz-5.4.6.tar.gz zstd-1.5.7.tar.gz; do
  [ -f $SRC/$f ] || { echo "missing $SRC/$f (run engine/fetch_sources.sh)" >&2; exit 1; }
done
[ -d $NDK/toolchains/llvm/prebuilt/linux-x86_64 ] || { echo "NDK not found: $NDK" >&2; exit 1; }
unpack Python-$PYVER.tar.xz Python-$PYVER; PYSRC=$SRC/Python-$PYVER

# run <log> <command...>: runs quietly, shows the log tail on failure
run() { local log=$1; shift; "$@" > "$log" 2>&1 || { echo "FAILED: $* (log: $log)" >&2; tail -40 "$log" >&2; exit 1; }; }

# ---- 1. host Python + build-tool venv --------------------------------------------------------------------------
HOSTPY=$D/hostpy-base/bin/python$PYMM
if ! $HOSTPY -c "import ssl, ctypes, sqlite3, bz2, lzma, zlib, venv" 2> /dev/null; then
  echo "== host Python $PYVER -> $D/hostpy-base"
  HB=$D/build-hostpy; rm -rf $HB $D/hostpy-base $D/hostpy; mkdir -p $HB; cd $HB
  run $HB/configure.log $(realpath --relative-to=$HB $PYSRC)/configure --prefix=$D/hostpy-base --with-ensurepip=install
  run $HB/make.log make -j$J
  run $HB/install.log make install
  $HOSTPY -c "import ssl, ctypes, sqlite3, bz2, lzma, zlib, venv"
  cd $D && rm -rf $HB
fi
if ! $D/hostpy/bin/python -c "import cython, mesonbuild, mesonpy, pybind11, yaml, ply, lark" 2> /dev/null; then
  echo "== host venv -> $D/hostpy"
  rm -rf $D/hostpy; $HOSTPY -m venv $D/hostpy
  # offline and hash-checked; the PyYAML sdist builds pure-Python (no libyaml) with setuptools+Cython from $SRC/pypi
  run $D/hostpy-pip.log env PYYAML_FORCE_LIBYAML=0 PIP_DISABLE_PIP_VERSION_CHECK=1 \
    $D/hostpy/bin/python -m pip install --no-index --find-links $SRC/pypi --require-hashes -r $ENGINE/host-requirements.txt
  $D/hostpy/bin/python -c "import cython, mesonbuild, mesonpy, pybind11, yaml, ply, lark"
fi

# ---- 2. Android dependencies -----------------------------------------------------------------------------------
B=$D/build-python-$ABI; rm -rf $B; mkdir -p $B; DEPS=$B/prefix; mkdir -p $DEPS/lib $DEPS/include
# CPython's own Android/android-env.sh (compiler, CFLAGS/LDFLAGS, pkg-config setup), pointed at our NDK
sed -e 's|^ndk=.*|ndk="$NDK"|' $PYSRC/Android/android-env.sh > $B/android-env.sh
# android_env [prefix]: sets up the cross environment in the current (sub)shell
android_env() { export HOST=$TRIPLE ANDROID_API_LEVEL=$API NDK; if [ -n "${1:-}" ]; then export PREFIX=$1; else unset PREFIX; fi; . $B/android-env.sh; }
srcdir() { mkdir -p $B/$1; tar xf $SRC/$2 -C $B/$1 --strip-components=1; cd $B/$1; }
T0=$(date +%s)

echo "== bzip2 ($ABI)"
( android_env; srcdir bzip2 bzip2-1.0.8.tar.gz
  CFLAGS+=" -O2 -fPIC"; export CFLAGS
  run $B/bzip2.log make -e -j$J libbz2.a       # -e: the environment overrides the Makefile's CC/CFLAGS/AR/RANLIB
  cp libbz2.a $DEPS/lib/; cp bzlib.h $DEPS/include/ )

echo "== libffi ($ABI)"
( android_env; srcdir libffi libffi-3.4.4.tar.gz
  patch -p1 -s -i $PATCHES/libffi-long_double.patch; patch -p1 -s -i $PATCHES/libffi-open_temp_exec_file.patch
  run $B/libffi.log ./configure --host=$TRIPLE --prefix=$DEPS --disable-shared --with-pic --disable-docs
  run $B/libffi-make.log make -j$J; run $B/libffi-install.log make install )

echo "== xz ($ABI)"
( android_env; srcdir xz xz-5.4.6.tar.gz
  run $B/xz.log ./configure --host=$TRIPLE --prefix=$DEPS --disable-shared --with-pic
  run $B/xz-make.log make -j$J; run $B/xz-install.log make install )

echo "== zstd ($ABI)"
( android_env; srcdir zstd zstd-1.5.7.tar.gz
  CFLAGS+=" -fPIC"; export CFLAGS
  run $B/zstd.log make -j$J -C lib libzstd.a-release libzstd.pc PREFIX=$DEPS
  run $B/zstd-install.log make -C lib install-static install-pc install-includes PREFIX=$DEPS )

echo "== OpenSSL ($ABI)"
( android_env; srcdir openssl openssl-3.0.19.tar.gz
  for p in at_secure configuration soname; do patch -p1 -s -i $PATCHES/openssl-$p.patch; done
  CFLAGS+=" -O2"; export CFLAGS LDLIBS="-latomic"   # CFLAGS replaces OpenSSL's default flags
  run $B/openssl.log ./Configure android-python64 shared
  run $B/openssl-make.log make -j$J
  run $B/openssl-install.log make install_sw DESTDIR=$B/openssl-root
  cp -a $B/openssl-root/usr/local/. $DEPS/ )

echo "== SQLite ($ABI)"
( android_env; srcdir sqlite sqlite-autoconf-3500400.tar.gz
  patch -p1 -s -i $PATCHES/sqlite-soname.patch
  # the flags of CPython's macOS installer build (Mac/BuildScript/build-installer.py), as python.org's Android build
  CFLAGS+=" -Os -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_FTS4 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_PERCENTILE -DSQLITE_ENABLE_RTREE -DSQLITE_OMIT_AUTOINIT -DSQLITE_TCL=0"
  export CFLAGS
  run $B/sqlite.log ./configure --host=$TRIPLE --disable-static
  run $B/sqlite-make.log make -j$J
  run $B/sqlite-install.log make install prefix=$DEPS )
T1=$(date +%s)

# ---- 3. CPython for Android (= android.py configure-host + make-host) -----------------------------------------
echo "== CPython $PYVER ($ABI, API $API)"
BUILD=$($PYSRC/config.guess)     # before android_env: config.guess looks at $CC
mkdir -p $B/python; cd $B/python
( android_env $DEPS
  run $B/python-configure.log $(realpath --relative-to=$B/python $PYSRC)/configure \
    --host=$TRIPLE --build=$BUILD --with-build-python=$HOSTPY --without-ensurepip \
    --enable-shared --without-static-libpython --with-openssl=$DEPS )
run $B/python-make.log make -j$J       # the environment is captured in the Makefile by configure (as in android.py)
run $B/python-install.log make install prefix=$DEPS
T2=$(date +%s)

# ---- package (= android.py package): $D/$ABI/python ------------------------------------------------------------
OUT=$D/$ABI/python; rm -rf $OUT; mkdir -p $OUT/prefix/lib/pkgconfig $OUT/prefix/include
cp -a $PYSRC/Android/. $OUT/
cd $DEPS
for f in include/openssl* include/python* include/sqlite* lib/engines-3 lib/libcrypto*.so lib/libpython* lib/libsqlite* \
         lib/libssl*.so lib/ossl-modules lib/python* lib/pkgconfig/*crypto* lib/pkgconfig/*ssl* lib/pkgconfig/*python* lib/pkgconfig/*sqlite*; do
  [ -e "$f" ] || [ -L "$f" ] || continue
  cp -a "$f" "$OUT/prefix/$f"
done
find $OUT/prefix -name __pycache__ -type d -prune -exec rm -rf {} +
# python.org's package carries these as plain files (copies of the *_python.so libraries); libsqlite3.so.0 stays a link
cd $OUT/prefix/lib
for l in libcrypto.so libssl.so libsqlite3.so; do [ -L $l ] && cp --remove-destination "$(readlink -f $l)" $l; done
find $OUT -name '*.so' -type f -exec $TB/llvm-strip {} +
# check: the modules the app needs, and their libraries
DYN=$OUT/prefix/lib/python$PYMM/lib-dynload
for m in _ssl _hashlib _sqlite3 _ctypes _lzma _bz2 _zstd zlib _decimal pyexpat _socket _json _struct math select; do
  [ -f $DYN/$m.cpython-${PYMM/./}-$TRIPLE.so ] || { echo "module $m was not built" >&2; exit 1; }
done
[ -f $OUT/prefix/lib/python$PYMM/_sysconfigdata__android_$TRIPLE.py ] && [ -f $OUT/prefix/lib/pkgconfig/python-$PYMM.pc ]
for l in libssl_python.so libcrypto_python.so libsqlite3_python.so libpython$PYMM.so; do [ -f $OUT/prefix/lib/$l ]; done
cd $D && rm -rf $B
echo "DONE python $ABI: deps $((T1-T0))s, CPython $((T2-T1))s -> $OUT"
