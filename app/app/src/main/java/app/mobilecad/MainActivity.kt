package app.mobilecad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import app.mobilecad.freecad.AppState
import app.mobilecad.i18n.Language
import app.mobilecad.ui.Shell
import app.mobilecad.ui.OS
import app.mobilecad.xr.XrActivity
import app.mobilecad.xr.XrDevice

class MainActivity : ComponentActivity() {
    /** Developer hook: adb shell am start -n app.mobilecad/.MainActivity --es fcscript "print(App.Version())" (debug builds only). */
    private fun devScript(i: android.content.Intent?) {
        // --es update_feed URL: check for updates against a test feed (GitHub release JSON) instead of GitHub
        i?.getStringExtra("update_feed")?.let { u -> if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            app.updater.testFeed = u; app.updater.check(manual = true) }; return }
        // --es fcimport /path/file.step: File ▸ Import, as the menu does
        i?.getStringExtra("fcimport")?.let { p -> if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) app.whenReady { app.importFile(java.io.File(p)) }; return }
        // --es fcopen /path/file.FCStd: open a document as File ▸ Open would (screenshots, tests)
        i?.getStringExtra("fcopen")?.let { p -> if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) app.whenReady { app.open(p) }; return }
        val src = i?.getStringExtra("fcscript") ?: i?.getStringExtra("fcscript64")?.let { String(android.util.Base64.decode(it, 0)) } ?: return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        app.whenReady { app.eng({ run("import traceback\ntry:\n" + src.lines().joinToString("\n") { "    $it" } + "\nexcept Exception:\n    result = traceback.format_exc()") }) { r ->
            android.util.Log.i("fcscript", r?.toString() ?: "None"); app.refresh() } }
    }
    /** Going to the background (home, another app, screen off) may be the last chance: Android can kill us there. */
    override fun onStop() { super.onStop(); if (::app.isInitialized) app.autosave() }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); if (::app.isInitialized) devScript(intent) }

    private lateinit var app: AppState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // on a headset MobileCAD starts in XR mode: the same app, in the room (XrActivity); everywhere else the 2D interface
        if (XrDevice.wanted(this, intent)) {
            startActivity(android.content.Intent(this, XrActivity::class.java).apply { intent.extras?.let { putExtras(it) } })
            finish(); return
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        org.qtproject.qt.android.QtHostAccess.attach(this)
        Language.init(this)          // the interface language, before anything is drawn
        app = AppState(this)
        app.start()
        devScript(intent)
        setContent {
            val dark = app.theme == "dark" || (app.theme == "system" && androidx.compose.foundation.isSystemInDarkTheme())
            OS.dark = dark
            androidx.compose.runtime.LaunchedEffect(dark) {
                app.viewport?.sync()
                // the status bar continues the title bar: same colour, dark icons on the light theme
                @Suppress("DEPRECATION") window.statusBarColor = OS.TopBar.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
            }
            val scheme = if (dark) androidx.compose.material3.darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8AB4F8),
                surface = androidx.compose.ui.graphics.Color(0xFF1E2227), surfaceContainer = androidx.compose.ui.graphics.Color(0xFF242930),
                background = androidx.compose.ui.graphics.Color(0xFF181B1F))
                else androidx.compose.material3.lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF2A6FC9))
            androidx.compose.material3.MaterialTheme(colorScheme = scheme) { Shell(app) }
        }
    }
}
