import java.util.Properties
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
        // rises with every build (minutes since 2026-01-01), so each published APK installs as an update
        versionCode = ((System.currentTimeMillis() / 60000L) - 29454720L).toInt()
        versionName = "0.4"
    }
    signingConfigs {
        if (keyProps.containsKey("storeFile")) create("release") {
            storeFile = file(keyProps.getProperty("storeFile")); storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias"); keyPassword = keyProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false          // the Python/FreeCAD runtime and Qt reach Java by name
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    ndkVersion = "29.0.14206865"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig { externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_shared") } } }
    // The FreeCAD/CPython runtime is ABI-specific: one flavor (and APK) per ABI, staged by engine/package_runtime.py
    flavorDimensions += "abi"
    productFlavors {
        create("arm64") { dimension = "abi"; ndk { abiFilters += "arm64-v8a" } }
        create("x86") { dimension = "abi"; ndk { abiFilters += "x86_64" } }
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
