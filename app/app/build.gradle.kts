import java.util.Properties
// One release = one versionName and one base versionCode (raise both per release; F-Droid's update check reads them):
// 1000000 + major*100000 + minor*1000 + patch*10, plus the ABI digit per APK (arm64 1, x86_64 2). The codes stay above
// the time-based ones of 0.4, and a tag builds to the same versionCode on GitHub and on F-Droid.
val appVersion = "0.5.2"
val baseVersionCode = 1005020
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// release key lives outside the source tree (~/Android/keys); without it, release builds stay unsigned
val keyProps = Properties().apply {
    val f = file(System.getProperty("user.home") + "/Android/keys/mobilecad-release.properties"); if (f.exists()) f.inputStream().use { load(it) } }
android {
    namespace = "app.mobilecad"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.mobilecad"
        minSdk = 26
        targetSdk = 34
        versionName = appVersion
    }
    signingConfigs {
        if (keyProps.containsKey("storeFile")) create("release") {
            storeFile = file(keyProps.getProperty("storeFile")); storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias"); keyPassword = keyProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true; isShrinkResources = true     // R8; Qt reaches its Java classes by name: proguard-rules.pro
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    ndkVersion = "29.0.14206865"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared") } } }
    // The FreeCAD/CPython runtime is ABI-specific: one flavor (and APK) per ABI, staged by engine/package_runtime.py
    flavorDimensions += "abi"
    productFlavors {
        create("arm64") { dimension = "abi"; ndk { abiFilters += "arm64-v8a" }; versionCode = baseVersionCode + 1 }
        create("x86") { dimension = "abi"; ndk { abiFilters += "x86_64" }; versionCode = baseVersionCode + 2 }
    }
    buildFeatures { compose = true }
    packaging { jniLibs { pickFirsts += "**/libc++_shared.so"; useLegacyPackaging = true } }
    // aapt's default pattern drops asset directories starting with "_" (zipfile/_path, _pyrepl, Python packages ...)
    androidResources { ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~" }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation(files("libs/Qt6Android.jar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
