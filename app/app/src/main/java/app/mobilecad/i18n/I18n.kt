package app.mobilecad.i18n

import android.content.Context
import java.util.Locale

/**
 * Interface translations.
 *
 * The English text is the key: each catalog line is `English<TAB>translation` (UTF-8, `#` starts a
 * comment line). A string the catalog does not hold stays English, so a partial catalog never breaks
 * the interface, and user data (document, object and layer names) is never touched because only the
 * literals and engine labels listed in the catalogs are looked up.
 *
 * The language follows the system locale: a Chinese locale (`zh`, simplified or not) loads
 * `assets/i18n/zh-Hans.tsv`; every other locale stays English. The preference ("system", "en" or
 * "zh-Hans") overrides it; [setLanguage] saves the choice and recreates the activity so every screen
 * is drawn again.
 */
object I18n {
    private const val PREFS = "interface"
    private const val KEY = "language"
    private var table: Map<String, String> = emptyMap()
    private var current = "en"

    /** Values the language picker stores: "system" follows the device, the others pin a language. */
    val choices: List<String> = listOf("system", "en", "zh-Hans")

    /** Reads the saved choice (following the system by default) and loads its catalog. Call once, before the UI. */
    fun init(ctx: Context) {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"
        current = when (saved) {
            "en", "zh-Hans" -> saved
            else -> systemLanguage()
        }
        table = if (current == "en") emptyMap() else load(ctx, current)
    }

    /** The choice stored in the preferences ("system", "en" or "zh-Hans"). */
    fun saved(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system") ?: "system"

    /** A picker label for a stored choice: "System language (简体中文)" says what "system" resolves to. */
    fun choiceLabel(value: String): String = when (value) {
        "system" -> tf("System language (%s)", if (systemLanguage() == "zh-Hans") "简体中文" else "English")
        "zh-Hans" -> "简体中文"
        else -> "English"
    }

    /** Saves [value] ("system", "en" or "zh-Hans") and restarts the activity so the interface redraws. */
    fun setLanguage(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
        (ctx as? android.app.Activity)?.recreate()
    }

    /** The translation of [text], or [text] itself when the catalog has none (or the language is English). */
    fun t(text: String): String = if (table.isEmpty()) text else table[text] ?: text

    /** [t] for a template with `%s` / `%d` placeholders: the template is translated first, then filled in. */
    fun tf(template: String, vararg args: Any?): String = String.format(t(template), *args)

    private fun systemLanguage(): String = if (Locale.getDefault().language.equals("zh", ignoreCase = true)) "zh-Hans" else "en"

    private fun load(ctx: Context, language: String): Map<String, String> {
        val out = HashMap<String, String>(4096)
        try {
            ctx.assets.open("i18n/$language.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    out.putIfAbsent(line.substring(0, tab), line.substring(tab + 1))   // the app's own wording comes first
                }
            }
        } catch (e: Exception) {
            return emptyMap()
        }
        return out
    }
}
