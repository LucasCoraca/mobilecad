package app.mobilecad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import app.mobilecad.freecad.AppState
import app.mobilecad.ui.Shell
import app.mobilecad.ui.OS

class MainActivity : ComponentActivity() {
    /** Developer hook: adb shell am start -n app.mobilecad/.MainActivity --es fcscript "print(App.Version())" (debug builds only). */
    private fun devScript(i: android.content.Intent?) {
        // --es fcopen /path/file.FCStd: open a document as File ▸ Open would (screenshots, tests)
        i?.getStringExtra("fcopen")?.let { p -> if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) app.whenReady { app.open(p) }; return }
        val src = i?.getStringExtra("fcscript") ?: i?.getStringExtra("fcscript64")?.let { String(android.util.Base64.decode(it, 0)) } ?: return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        app.whenReady { app.eng({ run("import traceback\ntry:\n" + src.lines().joinToString("\n") { "    $it" } + "\nexcept Exception:\n    result = traceback.format_exc()") }) { r ->
            android.util.Log.i("fcscript", r?.toString() ?: "None"); app.refresh() } }
    }
    /** Going to the background (home, another app, screen off) may be the last chance: Android can kill us there. */
    override fun onStop() { super.onStop(); if (::app.isInitialized) app.autosave() }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); devScript(intent) }

    private lateinit var app: AppState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        org.qtproject.qt.android.QtHostAccess.attach(this)
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
