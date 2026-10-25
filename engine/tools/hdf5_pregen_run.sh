#!/bin/bash
# CMAKE_CROSSCOMPILING_EMULATOR of the HDF5 cross build: instead of running HDF5's generator programs on a device, writes
# what they print on Android (engine/pregen/hdf5-1.10.11/<abi>: H5Tinit.c from H5detect, H5lib_settings.c from
# H5make_libsettings, generated on the emulator). Usage (by CMake): hdf5_pregen_run.sh <program> <output file>
set -e
P=$(dirname "$(readlink -f "$0")")/../pregen/hdf5-1.10.11/${HDF5_PREGEN_ABI:?}
case $(basename "$1") in
  H5detect) cp "$P/H5Tinit.c" "$2" ;;
  H5make_libsettings) cp "$P/H5lib_settings.c" "$2" ;;
  *) echo "hdf5_pregen_run.sh: no recorded output for $1 (a new try_run check? add its result to engine/cmake/hdf5-android-cache.cmake)" >&2; exit 1 ;;
esac
