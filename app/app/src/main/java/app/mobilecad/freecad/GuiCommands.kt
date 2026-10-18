package app.mobilecad.freecad

import org.json.JSONObject

/**
 * FreeCAD's Python commands (BIM, FEM, CAM, OpenSCAD, Assembly, MeshPart, TemplatePyMod ...) run unchanged on the
 * engine's headless FreeCADGui (mobilecad.guicmd).  What a command asks the desktop GUI for comes back here:
 * Snapper.getPoint -> the touch point picker (each point goes straight to the command's callback, Finish / ✗ sends the
 * Snapper's "done"), setEdit / task panels -> the property dialog of the object, selection changes -> the app selection.
 * The whole command, including its point picks, is one undoable transaction.
 */
object GuiCommands {
    /** Python commands the engine could load (filled at startup by [load]). */
    @Volatile var ids: Set<String> = emptySet()

    fun load(s: AppState) = s.eng({
        val names = s.catalog.commands.keys.filter { k -> k.substringBefore('_') in setOf("BIM", "Arch", "IFC", "FEM", "CAM", "OpenSCAD", "Assembly",
            "MeshPart", "TemplatePyMod", "TemplatePyGrp", "TemplatePyGroup", "TemplatePyCheckable", "PartDesign", "Material", "Materials", "Draft") }
        val a = call("gui_available", mapOf("names" to names)) as org.json.JSONArray
        val g = call("gui_groups", mapOf("names" to names.filter { n -> s.catalog.cmd(n)?.group.isNullOrEmpty() })) as JSONObject
        List(a.length()) { a.getString(it) }.toSet() to g.keys().asSequence().associateWith { k -> g.getJSONArray(k).let { m -> List(m.length()) { m.getString(it) } } }
    }) { (i, g) -> ids = i; groups = g }

    /** Python group commands (dropdowns built in Python, BIM_ArcTools / CAM_DressupTools ...) -> their tools. */
    @Volatile var groups: Map<String, List<String>> = emptyMap()

    fun run(s: AppState, id: String) {
        val label = s.catalog.cmd(id)?.label?.replace("&", "") ?: id
        val dir = s.viewport?.cam?.let { c -> val e = c.eye(); listOf((c.tx - e[0]).toDouble(), (c.ty - e[1]).toDouble(), (c.tz - e[2]).toDouble()) }
        s.eng({ begin(label); try { call("gui_command", mapOf("name" to id, "sel" to s.selJson(), "view_dir" to dir, "wp" to s.workPlane.json(),
            "page" to (if (s.inDrawing) s.page else null), "colors" to s.colors.mapValues { (_, c) -> listOf(((c shr 16) and 255) / 255.0, ((c shr 8) and 255) / 255.0, (c and 255) / 255.0) })) as JSONObject } catch (e: Throwable) { abort(); throw e } }) { r ->
            handle(s, label, r) }
    }

    private fun handle(s: AppState, label: String, r: JSONObject?) {
        if (r == null) { finish(s, null); return }
        // selection the command left behind (Gui.Selection.addSelection / clearSelection)
        r.optJSONArray("selection")?.let { a -> s.selection.clear()
            for (i in 0 until a.length()) a.getJSONArray(i).let { s.selection.add(SelRef(it.getString(0), it.optString(1))) } }
        side(s, r)
        // a task panel / dialog is open: the transaction stays open until its OK / Cancel
        r.optJSONObject("panel")?.let { p -> panelLabel = label; s.taskPanel = p; s.refresh(); return }
        if (s.taskPanel != null && r.has("panel")) s.taskPanel = null
        // View.addEventCallback (macros / examples that react to clicks): taps go to the callbacks until ✗
        if (r.optBoolean("events") && r.optJSONObject("point") == null) {
            s.refresh()
            if (s.draft?.id != "GuiEvents") DraftTool("GuiEvents", label, listOf("Tap in the 3D view"), 0, -1) { _ ->
                s.eng({ call("gui_view_event", mapOf("kind" to "escape")) as JSONObject? }) { rr -> finish(s, rr) } }.also { t ->
                t.onPoint = { p -> s.eng({ call("gui_view_event", mapOf("kind" to "tap", "point" to p.toList())) as JSONObject? }) { rr -> handle(s, label, rr) } }
                t.onCancel = { s.eng({ call("gui_view_event", mapOf("kind" to "escape")) as JSONObject? }) { rr -> finish(s, rr) } }
                s.startDraft(t) }
            return
        }
        if (s.draft?.id == "GuiEvents") { finish(s, r); return }
        val pt = r.optJSONObject("point")
        if (pt != null) {
            s.refresh()
            val tool = s.draft?.takeIf { it.id == "GuiCmd" } ?: DraftTool("GuiCmd", label, listOf(pt.optString("title", "Pick point")), 0, -1) { _ ->
                s.eng({ call("gui_point_cancel") as JSONObject? }) { rr -> finish(s, rr) } }.also { t ->
                t.onPoint = { p -> s.eng({ call("gui_point", mapOf("x" to p[0], "y" to p[1], "z" to p[2])) as JSONObject? }) { rr -> handle(s, label, rr) } }
                t.onCancel = { s.eng({ call("gui_point_cancel") as JSONObject? }) { rr -> finish(s, rr) } }
                s.startDraft(t) }
            tool.prompt2 = pt.optString("title", "Pick point")
            return
        }
        finish(s, r)
    }

    private var panelLabel = ""

    /** Messages (QMessageBox.information ...), errors and URLs (QDesktopServices.openUrl: help, tutorials) of a step. */
    private fun side(s: AppState, r: JSONObject) {
        if (r.has("background") && r.optInt("background") != s.background) { s.background = r.optInt("background"); s.viewport?.sync() }
        // WorkingPlane.align_view (BIM_WPView, Draft_SelectPlane ...): look straight at the working plane
        r.optJSONArray("align")?.let { a -> val n = a.getJSONArray(0); val o = a.getJSONArray(1)
            s.viewport?.let { v -> v.cam.lookAlong(DoubleArray(3) { n.getDouble(it) }, DoubleArray(3) { o.getDouble(it) }); v.refresh() } }
        // view properties the command wrote (ViewObject.ShapeColor / ShapeAppearance / Transparency)
        r.optJSONObject("view")?.let { v -> v.keys().forEach { n -> val p = v.getJSONObject(n)
            p.optJSONArray("color")?.let { c -> s.colors[n] = (Math.round(c.getDouble(0) * 255).toInt() shl 16) or (Math.round(c.getDouble(1) * 255).toInt() shl 8) or Math.round(c.getDouble(2) * 255).toInt() }
            if (p.has("transparency")) { if (p.getInt("transparency") > 0) { if (n !in s.transparent) s.transparent.add(n) } else s.transparent.remove(n) } }
            s.viewport?.sync() }
        r.optJSONArray("messages")?.let { m -> if (m.length() > 0) s.message = (0 until m.length()).joinToString("\n") { m.getString(it) } }
        r.optString("error").takeIf { it.isNotEmpty() }?.let { s.message = it }
        // the command's report-view output (Console.Print*, print): last line as a message, all of it in the console log
        r.optString("console").takeIf { it.isNotBlank() }?.let { c -> s.consoleLog.add(c); if (s.message == null) s.message = c.lines().last { it.isNotBlank() } }
        r.optString("url").takeIf { it.startsWith("http") || it.startsWith("file") }?.let { u ->
            runCatching { s.viewport?.context?.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** The user changed widget [wid] of the open panel (value: number / text / index / checked / null for a click). */
    fun panelEvent(s: AppState, wid: Int, kind: String, value: Any?) {
        val p = s.taskPanel ?: return
        s.eng({ call("panel_event", mapOf("pid" to p.getInt("id"), "wid" to wid, "kind" to kind, "value" to value, "sel" to s.selJson())) as JSONObject? }) { r -> afterPanel(s, r) }
    }

    /** Selection changed while the panel is open: its Gui.Selection observers pick the references. */
    fun panelSelection(s: AppState) {
        val p = s.taskPanel ?: return
        s.eng({ call("panel_selection", mapOf("pid" to p.getInt("id"), "sel" to s.selJson())) as JSONObject? }) { r -> afterPanel(s, r, keepSel = true) }
    }

    /** The file a command's QFileDialog asked for (copied into the app cache; null = the user cancelled the picker). */
    fun panelFile(s: AppState, path: String?, after: () -> Unit = {}) {
        if (path == null) { panelButton(s, "cancel"); return }
        panelButton(s, "ok", path, after)
    }

    /** OK / Cancel / Apply / Close of the panel. */
    fun panelButton(s: AppState, which: String, value: String? = null, after: () -> Unit = {}) {
        val p = s.taskPanel ?: return
        s.eng({ call("panel_button", mapOf("pid" to p.getInt("id"), "which" to which, "sel" to s.selJson(), "value" to value)) as JSONObject? }) { r ->
            after()
            if (r?.optJSONObject("panel") == null) { s.taskPanel = null
                if (which in setOf("cancel", "close", "no") && r?.optJSONArray("created")?.length() == 0) { s.eng({ abort(); undoState() }) { h -> s.setHistory(h); s.refresh() }; return@eng }
                finish(s, r?.apply { put("edit", JSONObject.NULL) }) }
            else afterPanel(s, r) }
    }

    private fun afterPanel(s: AppState, r: JSONObject?, keepSel: Boolean = false) {
        if (r == null) return
        side(s, r)
        val p = r.optJSONObject("panel")
        if (p == null) { s.taskPanel = null; finish(s, r.apply { put("edit", JSONObject.NULL) }); return }
        s.taskPanel = p
        if (r.optJSONObject("point") != null) { handle(s, panelLabel, r.apply { remove("panel") }); s.taskPanel = p; return }
        s.refresh()
    }

    private fun finish(s: AppState, r: JSONObject?) {
        if (s.draft?.id == "GuiCmd" || s.draft?.id == "GuiEvents") s.draft = null
        // working plane the command set (BIM_SetWP*): becomes the app's Draft working plane
        r?.optJSONObject("wp")?.let { j -> fun v(k: String) = j.getJSONArray(k).let { a -> DoubleArray(3) { a.getDouble(it) } }
            runCatching { s.workPlane = WorkPlane(v("pos"), v("u"), v("v"), v("w")) } }
        // commands it delegated with FreeCADGui.runCommand (BIM_Fuse -> Part_Fuse ...) run through the app
        r?.optJSONArray("commands")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let { c -> s.viewport?.post { s.run(c) } } }
        s.eng({ recompute(); commit(); undoState() }) { h -> s.setHistory(h)
            s.refresh { r?.optString("edit")?.takeIf { it.isNotEmpty() && it != "null" }?.let { s.openEditor(it) } } }
    }
}
