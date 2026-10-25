# HDF5 1.10 configure checks that run test programs (try_run), answered for Android (x86_64 and arm64-v8a give the
# same results; measured on the emulator). Preloaded with `cmake -C` so the cross build needs no device.
set(H5_PRINTF_LL_WIDTH "\"ll\"" CACHE INTERNAL "")
set(H5_LDOUBLE_TO_LLONG_ACCURATE 1 CACHE INTERNAL "")
set(H5_LLONG_TO_LDOUBLE_CORRECT 1 CACHE INTERNAL "")
set(H5_NO_ALIGNMENT_RESTRICTIONS 1 CACHE INTERNAL "")
set(H5_DISABLE_SOME_LDOUBLE_CONV "" CACHE INTERNAL "")
set(H5_LDOUBLE_TO_LONG_SPECIAL "" CACHE INTERNAL "")
set(H5_LONG_TO_LDOUBLE_SPECIAL "" CACHE INTERNAL "")
