package app.mobilecad.xr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent

/** Whether MobileCAD runs on a headset, where it starts in XR mode (XrActivity) instead of the phone / tablet interface. */
object XrDevice {
    fun isHeadset(ctx: Context): Boolean {
        val pm = ctx.packageManager
        return pm.hasSystemFeature("android.hardware.vr.headtracking") ||        // Meta Quest (Horizon OS)
            pm.hasSystemFeature("android.software.xr.api.openxr") ||            // Android XR headsets
            // another headset with a system OpenXR runtime (installable runtimes, e.g. on a phone, do not count)
            runCatching { pm.resolveContentProvider("org.khronos.openxr.system_runtime_broker", 0) != null }.getOrDefault(false)
    }
    /** XR mode for this launch. Debug builds: `--ez xr true` forces it (touch preview without a headset), `--ez xr false` the 2D interface. */
    fun wanted(ctx: Context, i: Intent?): Boolean {
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && i?.hasExtra("xr") == true) return i.getBooleanExtra("xr", false)
        return isHeadset(ctx)
    }
}
