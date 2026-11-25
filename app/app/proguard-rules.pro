# Qt's native code (QtCore's JNI_OnLoad and its Android backend) looks up these classes and members by name
-keep class org.qtproject.** { *; }
-dontwarn org.qtproject.**
# XR mode: the XR core (libmcxr) calls XrActivity's callbacks by name; the OpenXR loader's Java side is looked up by its native code
-keep class app.mobilecad.xr.** { *; }
-keep class org.khronos.openxr.** { *; }
-dontwarn org.khronos.openxr.**
