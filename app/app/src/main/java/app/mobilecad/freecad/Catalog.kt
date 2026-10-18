package app.mobilecad.freecad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.caverock.androidsvg.SVG
import org.json.JSONObject

/** One FreeCAD command, as defined in FreeCAD's sources (see research/gen_catalog.py). */
class FcCommand(val id: String, val label: String, val tip: String, val icon: String?, val group: List<String>) {
    val workbench: String get() = id.substringBefore('_')
}
class FcToolbar(val name: String, val items: List<String>)
class FcWorkbench(val id: String, val toolbars: List<FcToolbar>, val other: List<String>)

/** FreeCAD's command catalog + toolbars for every workbench, generated from FreeCAD main. */
class Catalog private constructor(val source: String, val workbenches: List<FcWorkbench>, val commands: Map<String, FcCommand>, private val ctx: Context) {
    private val icons = HashMap<String, ImageBitmap?>()

    /** FreeCAD command → document object type it creates (research/parity.py, from each command's activated() source). */
    val commandTypes: Map<String, String> by lazy {
        val j = JSONObject(ctx.assets.open("freecad/command_types.json").bufferedReader().readText())
        j.keys().asSequence().associateWith { j.getString(it) }
    }

    fun wb(id: String) = workbenches.firstOrNull { it.id == id }
    fun cmd(id: String) = commands[id]

    /** Group commands (FreeCAD dropdowns, e.g. Sketcher_CompCreateConic) are flattened: the tool panel shows every tool. */
    fun flatten(items: List<String>): List<String> = items.flatMap { id ->
        val c = commands[id]
        if (c != null && c.group.isNotEmpty()) flatten(c.group) else listOf(id)
    }.filter { it != "Separator" }.distinct()

    fun search(q: String): List<FcCommand> {
        val s = q.trim().lowercase(); if (s.isEmpty()) return emptyList()
        return commands.values.filter { it.group.isEmpty() && (it.label.lowercase().contains(s) || it.id.lowercase().contains(s) || it.tip.lowercase().contains(s)) }
            .sortedBy { if (it.label.lowercase().startsWith(s)) 0 else 1 }
    }

    fun icon(name: String?, px: Int = 96): ImageBitmap? {
        if (name == null) return null
        return icons.getOrPut(name) {
            try {
                ctx.assets.open("freecad/icons/$name.svg").use { st ->
                    val svg = SVG.getFromInputStream(st)
                    svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    svg.renderToCanvas(Canvas(bmp)); bmp.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    companion object {
        @Volatile private var inst: Catalog? = null
        fun get(ctx: Context): Catalog = inst ?: synchronized(this) {
            inst ?: load(ctx.applicationContext).also { inst = it }
        }
        private fun load(ctx: Context): Catalog {
            val j = JSONObject(ctx.assets.open("freecad/catalog.json").bufferedReader().readText())
            val cj = j.getJSONObject("commands"); val cmds = HashMap<String, FcCommand>()
            for (k in cj.keys()) {
                val o = cj.getJSONObject(k); val g = o.optJSONArray("group")
                val raw = o.optString("label", k)
                val label = if (raw == k || raw.isBlank()) k.substringAfter('_').removePrefix("Create").replace(Regex("([a-z])([A-Z])"), "$1 $2").let {
                    if (it.startsWith("Joint ")) it.removePrefix("Joint ") + " Joint" else it } else raw.replace("%1", "MobileCAD")   // "About %1"
                cmds[k] = FcCommand(k, label, o.optString("tip"), if (o.isNull("icon")) null else o.optString("icon"),
                    if (g == null) emptyList() else List(g.length()) { g.getString(it) })
            }
            val wa = j.getJSONArray("workbenches")
            val wbs = List(wa.length()) { i ->
                val w = wa.getJSONObject(i); val ta = w.getJSONArray("toolbars"); val oa = w.getJSONArray("other")
                FcWorkbench(w.getString("id"), List(ta.length()) { t ->
                    val tj = ta.getJSONObject(t); val it = tj.getJSONArray("items")
                    FcToolbar(tj.getString("toolbar"), List(it.length()) { x -> it.getString(x) })
                }, List(oa.length()) { oa.getString(it) })
            }
            return Catalog(j.optString("source"), wbs, cmds, ctx)
        }
    }
}
