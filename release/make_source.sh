#!/bin/bash
# Corresponding source for a MobileCAD release (GPL-3.0 §6(d) / LGPL: offered from the same place as the APKs).
# Writes serve/source/: MobileCAD's own tree, FreeCAD at the commit built (+ our patch), and the sources of the
# (L)GPL components in the APK. Permissive components (Boost, ICU, HDF5 ...) are listed in the app's licenses screen.
set -e
R=/home/lucas/Documents/freecadmobile; H=$HOME/Android; S=$H/fcdeps/src; O=$R/serve/source
mkdir -p $O; rm -f $O/*.tmp
FC=$(git -C $H/freecad-src rev-parse --short HEAD)
# MobileCAD itself: app, engine build scripts + patches + Python bridge, catalog generators, branding, release tools.
# Not included: build outputs and the generated runtime staging (app/app/src/{x86,arm64}: made by package_runtime.py).
tar -C $R --exclude='*/build' --exclude='*/.gradle' --exclude='*/.cxx' --exclude='app/app/src/main/assets/freecad-runtime' \
    --exclude='app/app/src/main/assets/python' --exclude='app/app/src/main/jniLibs' --exclude='app/app/src/x86' --exclude='app/app/src/arm64' --exclude='app/local.properties' \
    --exclude='research/gui_scan*' --exclude='__pycache__' --exclude='release/video/clips' --exclude='release/video/build' \
    --transform 's,^,mobilecad/,' -cJf $O/mobilecad-src.tar.xz.tmp \
    LICENSE README.md app engine research branding release
mv $O/mobilecad-src.tar.xz.tmp $O/mobilecad-src.tar.xz
# FreeCAD (LGPL-2.1+): upstream tree at the commit we build, and our changes as a patch
[ -f $O/freecad-$FC.tar.gz ] || git -C $H/freecad-src archive --prefix=freecad-$FC/ -o $O/freecad-$FC.tar.gz HEAD
git -C $H/freecad-src diff > $O/freecad-$FC-mobilecad.patch
# Open CASCADE (LGPL-2.1 + exception)
# (the exact tree we built, 7.8.1)
[ -f $O/occt-7.8.1.tar.gz ] || tar -C $H --exclude='occt-src/build*' --transform 's,^occt-src,occt-7.8.1,' -czf $O/occt-7.8.1.tar.gz occt-src
# Qt (LGPL-3), Gmsh (GPL-2+), CalculiX + SPOOLES (GPL-2 / public domain), MED (LGPL-3): the release tarballs we built from
cp -u $S/qtbase.tar.xz $O/qtbase-everywhere-src-6.8.3.tar.xz
cp -u $S/gmsh/gmsh.tgz $O/gmsh-4.13.1-source.tgz
cp -u $S/ccx/ccx.tar.bz2 $O/ccx-2.22.src.tar.bz2; cp -u $S/ccx/spooles.tgz $O/spooles.2.2.tgz
cp -u $S/med/med-fichier_4.1.0+repack.orig.tar.gz $O/
# IfcOpenShell (LGPL-3): the checkout we built (our changes are engine/patches/ifcopenshell-android.patch)
IOS=$(git -C $S/ifc/ios rev-parse --short HEAD)
[ -f $O/ifcopenshell-$IOS.tar.gz ] || git -C $S/ifc/ios archive --prefix=ifcopenshell-$IOS/ -o $O/ifcopenshell-$IOS.tar.gz HEAD
(cd $O && sha256sum *.tar.* *.tgz *.patch > SHA256SUMS)
du -sh $O; ls $O
