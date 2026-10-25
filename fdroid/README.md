# F-Droid

`app.mobilecad.yml` is MobileCAD's build recipe for [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (it goes to
`metadata/app.mobilecad.yml` there). The store listing (descriptions, icon, screenshots, changelogs) is read from
`fastlane/metadata/android/` in this repository.

## What F-Droid builds

Everything is built from source on F-Droid's build server (Debian 13 "trixie", image
`registry.gitlab.com/fdroid/fdroidserver:buildserver`), with no device or emulator:

1. `engine/fetch_sources.sh` downloads every upstream source archive in `engine/sources.lock` and checks its SHA-256.
2. `engine/build_all.sh <abi>` builds the native runtime for one ABI: a host CPython 3.14 and the build tools pinned in
   `engine/host-requirements.txt`, CPython for Android and its libraries, Qt, Boost, Xerces, fmt, yaml-cpp, Eigen, ICU,
   FreeType, HarfBuzz, VTK, Open CASCADE, HDF5 + MED, FreeCAD (with `engine/patches/freecad-android.patch`), NumPy,
   Gmsh, IfcOpenShell and CalculiX (Fortran with Debian's `flang-21`, with OpenBLAS / ARPACK / the Fortran runtime
   built from source), then stages it into the app (`engine/package_runtime.py`).
3. Gradle builds the flavor of that ABI (`arm64` or `x86`).

F-Droid builds the arm64 APK (phones and tablets); the x86_64 flavor is for emulators and is built locally (`engine/build_all.sh x86_64`).

Generated files kept in the repository, because producing them needs an Android device: HDF5's type and settings
tables (`engine/pregen/hdf5-1.10.11/<abi>/`, output of HDF5's own `H5detect` / `H5make_libsettings`) and the results
of its `try_run` checks (`engine/cmake/hdf5-android-cache.cmake`).

## Building the same way locally

```sh
docker build -t mobilecad-fdroid-build fdroid/          # the build server image + the Debian packages of the recipe
docker run --rm -e FCDEPS=/fcdeps -e ANDROID_HOME=/opt/sdk -v $HOME/Android/fcdeps:/fcdeps \
    -v $ANDROID_HOME:/opt/sdk:ro -v $PWD:/work -w /work mobilecad-fdroid-build engine/build_all.sh arm64-v8a
```

then `cd app && ./gradlew :app:assembleArm64Release` with the same `FCDEPS`. The native build needs about 30 GB of disk
and several hours on a few cores per ABI.

## Releasing a version

1. Raise `appVersion` and `baseVersionCode` in `app/app/build.gradle.kts`, add
   `fastlane/metadata/android/en-US/changelogs/<baseVersionCode + 1>.txt`.
2. Tag the commit `v<appVersion>` and push the tag. F-Droid's update checker (`UpdateCheckMode: Tags`) picks it up and
   adds the build entries itself.
