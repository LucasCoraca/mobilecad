package app.mobilecad.freecad

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Updates from the GitHub releases: checks for a newer version (daily at start, or Help ▸ About ▸ Check for updates),
 *  downloads the APK for this build, verifies it against the release's SHA256SUMS and hands it to Android's installer
 *  (which asks the user to confirm). Only for APKs signed with the MobileCAD release key: F-Droid builds update through
 *  F-Droid (and a GitHub APK could not replace them anyway). MobileCAD XR (app.mobilecad.xr) follows its own repo. */
class Updater(private val ctx: Context, private val scope: CoroutineScope) {
    class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val sumsUrl: String?)

    private val prefs = ctx.getSharedPreferences("mobilecad", Context.MODE_PRIVATE)
    private val xr = ctx.packageName.endsWith(".xr")
    private val repo = if (xr) "LucasCoraca/mobilecad-xr" else "LucasCoraca/mobilecad"
    /** The release asset this build updates from (the APK per ABI; XR has one for the Quest). */
    private val asset = when {
        xr -> "MobileCAD-XR-quest.apk"
        Build.SUPPORTED_ABIS.firstOrNull() == "x86_64" -> "MobileCAD-x86_64.apk"
        else -> "MobileCAD-arm64.apk"
    }
    private val debuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    /** Test feed (debug builds only, `am start --es update_feed URL`): a JSON file in the GitHub release format. */
    var testFeed: String? = null

    val version: String get() = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    val enabled: Boolean get() = releaseSigned() || (debuggable && testFeed != null)
    var auto: Boolean
        get() = prefs.getBoolean("update_auto", true)
        set(v) { prefs.edit().putBoolean("update_auto", v).apply() }

    /** A newer release to offer (the Shell shows it), the download progress 0..1 while downloading, and messages. */
    var offer by mutableStateOf<Release?>(null)
    var progress by mutableStateOf<Float?>(null)
    var message by mutableStateOf<String?>(null)
    private var job: Job? = null
    private var pending: File? = null

    /** At start: at most once a day, quietly (nothing is shown unless there is a newer version). */
    fun checkDaily() {
        if (!enabled || !auto) return
        val now = System.currentTimeMillis()
        if (testFeed == null && now - prefs.getLong("update_checked", 0) < 20 * 3600_000L) return
        prefs.edit().putLong("update_checked", now).apply()
        check(manual = false)
    }

    fun check(manual: Boolean) {
        if (!enabled) { if (manual) message = "Updates for this copy come from where it was installed (e.g. F-Droid)."; return }
        cleanup()
        scope.launch {
            val r = try { withContext(Dispatchers.IO) { latest() } } catch (e: Exception) {
                if (manual) message = "Could not check for updates: ${e.message ?: e.javaClass.simpleName}"; return@launch }
            when {
                r != null && newer(r.version, version) -> offer = r
                manual -> message = "MobileCAD $version is the latest version."
            }
        }
    }

    private fun latest(): Release? {
        val feed = testFeed ?: if (xr) "https://api.github.com/repos/$repo/releases?per_page=10"     // XR ships pre-releases
            else "https://api.github.com/repos/$repo/releases/latest"
        val txt = get(feed).trim()
        val rel = if (txt.startsWith("[")) JSONArray(txt).let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") } }
            else JSONObject(txt)
        rel ?: return null
        val assets = rel.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val apk = assets.firstOrNull { it.getString("name") == asset } ?: return null
        return Release(rel.getString("tag_name").removePrefix("v"), rel.optString("body"), apk.getString("browser_download_url"),
            apk.optLong("size"), assets.firstOrNull { it.getString("name") == "SHA256SUMS" }?.getString("browser_download_url"))
    }

    /** Downloads [r]'s APK (progress in [progress]), checks its SHA-256 and starts the installation. */
    fun download(r: Release) {
        offer = null; progress = 0f
        job = scope.launch {
            val f = File(ctx.cacheDir, "updates/$asset")
            try {
                withContext(Dispatchers.IO) {
                    f.parentFile?.mkdirs()
                    val md = MessageDigest.getInstance("SHA-256")
                    val c = open(r.apkUrl)
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                    c.inputStream.use { inp -> f.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024); var n = 0L
                        while (true) {
                            if (!isActive) throw InterruptedException("cancelled")
                            val k = inp.read(buf); if (k < 0) break
                            out.write(buf, 0, k); md.update(buf, 0, k); n += k
                            if (total > 0) withContext(Dispatchers.Main) { progress = (n.toFloat() / total).coerceIn(0f, 1f) }
                        }
                    } }
                    val sum = md.digest().joinToString("") { "%02x".format(it) }
                    val want = r.sumsUrl?.let { u -> get(u).lines().map { it.trim().split(Regex("\\s+")) }
                        .firstOrNull { it.size >= 2 && it.last().trimStart('*') == asset }?.first()?.lowercase() }
                        ?: throw IllegalStateException("the release has no checksum for $asset")
                    if (want != sum) throw IllegalStateException("the download is damaged (checksum mismatch), try again")
                }
                progress = null; install(f)
            } catch (e: Exception) {
                progress = null; f.delete()
                if (e !is InterruptedException && e !is kotlinx.coroutines.CancellationException) message = "Update failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun cancel() { job?.cancel(); job = null; progress = null }

    /** Android's installer session; it asks the user to confirm (UpdateReceiver starts that screen). Needs the
     *  "install unknown apps" permission for MobileCAD the first time: Settings opens, the install continues once allowed. */
    private fun install(f: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            pending = f
            message = "Allow MobileCAD to install updates, then come back."
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // continue once it is allowed and MobileCAD is back in front (Android blocks the installer's confirmation
            // from the background); works for MobileCAD and MobileCAD XR's activity alike. Give up after 10 minutes.
            scope.launch { repeat(1200) { kotlinx.coroutines.delay(500)
                if (pending !== f) return@launch
                if (ctx.packageManager.canRequestPackageInstalls() && foreground()) { install(f); return@launch } }
                if (pending === f) { pending = null; message = "Update not installed: MobileCAD may not install apps" } }
            return
        }
        pending = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pi = ctx.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(ctx.packageName); setSize(f.length())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                    val id = pi.createSession(params)
                    pi.openSession(id).use { s ->
                        f.inputStream().use { inp -> s.openWrite("update.apk", 0, f.length()).use { out -> inp.copyTo(out); s.fsync(out) } }
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                        s.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags).intentSender)
                    }
                }
                message = "Installing the update…"
            } catch (e: Exception) { message = "Update failed: ${e.message ?: e.javaClass.simpleName}" }
        }
    }

    private fun foreground(): Boolean = android.app.ActivityManager.RunningAppProcessInfo().also { android.app.ActivityManager.getMyMemoryState(it) }
        .importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    /** The installer's confirmation screen; shown once MobileCAD is in front (a background start is blocked). */
    fun confirm(i: Intent) = scope.launch {
        repeat(1200) { if (foreground()) { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return@launch }; kotlinx.coroutines.delay(500) }
    }

    /** Leftovers of earlier updates (the APKs are large). */
    private fun cleanup() { if (job?.isActive != true && pending == null) File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() } }

    private fun releaseSigned(): Boolean = try {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION") val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
        sigs.orEmpty().any { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } == RELEASE_CERT }
    } catch (e: Exception) { false }

    private fun open(u: String): HttpURLConnection {
        var url = URL(u)
        repeat(6) {     // GitHub redirects downloads to its CDN; follow by hand (also across hosts)
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "MobileCAD/$version"); c.setRequestProperty("Accept", "application/vnd.github+json, */*")
            val code = c.responseCode
            if (code in 300..399) { url = URL(url, c.getHeaderField("Location")); c.disconnect(); return@repeat }
            if (code != 200) { c.disconnect(); throw IllegalStateException("HTTP $code") }
            return c
        }
        throw IllegalStateException("too many redirects")
    }
    private fun get(u: String): String = open(u).let { c -> try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() } }

    companion object {
        /** SHA-256 of the MobileCAD release certificate (~/Android/keys/mobilecad-release.jks). */
        const val RELEASE_CERT = "7192f62e9497f0d05048ea95ba16d468f45bfcab5b8707520d2d34c35713e966"
        var current: Updater? = null
        /** "1.10.0" > "1.9.2"; non-numeric parts (e.g. "-rc1") count as 0. */
        fun newer(a: String, b: String): Boolean {
            val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }; val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
            return false
        }
    }
}

/** Results of the installer session: asks the user to confirm, or reports why it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                Updater.current?.confirm(confirm) ?: ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.current?.message = null
            else -> Updater.current?.message = "Update not installed" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                ?.let { ": $it" } ?: if (st == PackageInstaller.STATUS_FAILURE_ABORTED) " (cancelled)" else "")
        }
    }
}
