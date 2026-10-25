# Qt's native code (QtCore's JNI_OnLoad and its Android backend) looks up these classes and members by name
-keep class org.qtproject.** { *; }
-dontwarn org.qtproject.**
