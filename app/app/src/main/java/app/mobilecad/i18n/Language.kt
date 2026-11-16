package app.mobilecad.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Interface language — desktop FreeCAD's Preferences ▸ General ▸ Language.
 *
 * Each language is one asset, `assets/i18n/<code>.json` (research/gen_i18n.py), holding
 *  - "app": MobileCAD's own strings, English text → translation (written by hand in app/i18n/<code>.json);
 *  - "commands": command name → [menu text, tooltip], looked up in FreeCAD's catalogs with the context the desktop
 *    uses (the C++ command class, or the Python command's name);
 *  - "contexts": FreeCAD's catalogs as (context, source) → translation, for property names ("App::Property"),
 *    toolbar names ("Workbench"), the NaviCube faces, and the engine's task panels (handed to it as a file).
 *
 * As on the desktop the change applies at once: [current] is Compose state, so every [tr] in the interface reads
 * it and redraws; the open document and the engine session are untouched.
 */
object Language {
    /** The languages the app has its own translation for, in their own names. */
    val available = listOf("en" to "English", "zh-Hans" to "简体中文")
    private const val PREF = "language"            // "" (or absent): follow Android's language

    /** The language the interface is drawn in. */
    var current by mutableStateOf("en"); private set

    private var app: Map<String, String> = emptyMap()
    private var commands: Map<String, Pair<String, String>> = emptyMap()
    private var contexts: Map<String, Map<String, String>> = emptyMap()
    private var anyContext: Map<String, String> = emptyMap()
    private var appContext: Context? = null

    private fun prefs(c: Context) = c.getSharedPreferences("mobilecad", Context.MODE_PRIVATE)

    /** The saved choice: "" follows Android, otherwise a code from [available]. */
    fun choice(c: Context): String = prefs(c).getString(PREF, "") ?: ""

    /** Android's language when the app has it, else English. Simplified Chinese is zh with the Hans script, or a
     *  zh locale without a script in a region that writes Simplified (CN, SG, MY); Traditional stays English. */
    fun system(): String {
        val l = Locale.getDefault()
        if (l.language != "zh") return "en"
        val hans = when (l.script) { "Hans" -> true; "Hant" -> false; else -> l.country.ifEmpty { "CN" } in setOf("CN", "SG", "MY") }
        return if (hans) "zh-Hans" else "en"
    }

    fun init(c: Context) { appContext = c.applicationContext; load(c, choice(c).ifEmpty { system() }) }

    /** Saves the choice ("" = follow Android) and switches the interface to it. */
    fun choose(c: Context, code: String) {
        prefs(c).edit().putString(PREF, code).apply()
        load(c, code.ifEmpty { system() })
    }

    fun name(code: String) = available.firstOrNull { it.first == code }?.second ?: code

    private fun load(c: Context, code: String) {
        if (code == "en" || available.none { it.first == code }) {
            app = emptyMap(); commands = emptyMap(); contexts = emptyMap(); anyContext = emptyMap(); current = "en"; return
        }
        try {
            val j = JSONObject(c.assets.open("i18n/$code.json").bufferedReader(Charsets.UTF_8).readText())
            app = j.getJSONObject("app").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            commands = j.getJSONObject("commands").let { o -> o.keys().asSequence().associateWith { k ->
                o.getJSONArray(k).let { it.getString(0) to it.getString(1) } } }
            val cj = j.getJSONObject("contexts"); val any = HashMap<String, String>()
            contexts = cj.keys().asSequence().associateWith { k -> cj.getJSONObject(k).let { o ->
                o.keys().asSequence().associateWith { s -> o.getString(s).also { any.putIfAbsent(s, it) } } } }
            j.optJSONObject("common")?.let { o -> o.keys().forEach { any[it] = o.getString(it) } }
            anyContext = any
            current = code
        } catch (e: Exception) {
            android.util.Log.w("MobileCAD", "language $code: ${e.message}")
            app = emptyMap(); commands = emptyMap(); contexts = emptyMap(); anyContext = emptyMap(); current = "en"
        }
    }

    /** The catalog file the engine reads (mobilecad.bridge.set_language), or null for English. */
    fun engineCatalog(): String? {
        val c = appContext ?: return null
        val code = current; if (code == "en") return null
        val f = File(c.filesDir, "i18n/$code.json")
        return try {
            val bytes = c.assets.open("i18n/$code.json").use { it.readBytes() }
            if (!f.exists() || f.length() != bytes.size.toLong()) { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
            f.absolutePath
        } catch (e: Exception) { null }
    }

    /** MobileCAD's wording first, then FreeCAD's (most common translation of that English text). */
    fun lookup(text: String): String? = if (current == "en") null else app[text] ?: anyContext[text]

    /** QCoreApplication::translate(context, text): FreeCAD's translation in that context, else [lookup]. */
    fun inContext(context: String, text: String): String =
        if (current == "en") text else contexts[context]?.get(text) ?: lookup(text) ?: text

    /** A command's menu text / tooltip as the desktop shows them in this language. */
    fun commandLabel(id: String, english: String): String =
        if (current == "en") english else commands[id]?.first?.ifEmpty { null } ?: lookup(english) ?: english
    fun commandTip(id: String, english: String): String =
        if (current == "en") english else commands[id]?.second?.ifEmpty { null } ?: lookup(english) ?: english

    /** The property editor's name column (PropertyItem::setPropertyName): CamelCase split at lower→upper, then
     *  translated in the "App::Property" context. */
    fun property(name: String): String {
        val sb = StringBuilder()
        for (ch in name) { if (ch.isUpperCase() && sb.isNotEmpty() && sb.last().isLowerCase()) sb.append(' '); sb.append(ch) }
        return inContext("App::Property", sb.toString())
    }
}

/**
 * The interface text for [text] in the current language (English when it has no translation). Placeholders follow
 * Qt's QString::arg: `%1`, `%2` … are replaced by [args], so a translation can put them in its own order.
 * Only the program's own strings go through here — document, object and file names never do.
 */
fun tr(text: String, vararg args: Any?): String {
    val t = Language.lookup(text) ?: text
    if (args.isEmpty()) return t
    val sb = StringBuilder(); var i = 0
    while (i < t.length) {
        val c = t[i]
        if (c == '%' && i + 1 < t.length && t[i + 1] in '1'..'9') {
            val n = t[i + 1] - '1'
            sb.append(if (n < args.size) args[n].toString() else t.substring(i, i + 2)); i += 2
        } else { sb.append(c); i++ }
    }
    return sb.toString()
}
