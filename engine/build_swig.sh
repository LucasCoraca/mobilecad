#!/bin/bash
# SWIG 4.1.1 as a host tool for the IfcOpenShell Python wrapper (FreeCAD itself uses the system's SWIG).  -> $FCDEPS/swig41
# Debian packages: build-essential, libpcre2-dev, bison
set -euo pipefail
. "$(dirname "$(readlink -f "$0")")/env.sh"
[ -x $D/swig41/bin/swig ] && { echo "swig 4.1 present"; exit 0; }
unpack swig-4.1.1.tar.gz swig-4.1.1
B=$D/build-swig; rm -rf $B; mkdir -p $B; cd $B
$SRC/swig-4.1.1/configure --prefix=$D/swig41 --without-alllang --with-python3 > configure.log 2>&1 || { tail -20 configure.log; exit 1; }
make -j$J > build.log 2>&1 && make install > install.log 2>&1 || { tail -20 build.log; exit 1; }
cd $D && rm -rf $B; echo "SWIG DONE"
