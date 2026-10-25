package app.mobilecad.freecad

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mobilecad.sketch.SketchEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import app.mobilecad.techdraw.PageView
import app.mobilecad.viewport.ViewportView

/** A FreeCAD selection reference: object + sub-element ("Face3", "Edge7", "Vertex2" or "" for the whole object).
 *  [point] is the picked 3D location (FreeCAD's selection pick point); it is not part of the identity. */
class SelRef(val obj: String, val sub: String = "", val point: FloatArray? = null) {
    override fun toString() = if (sub.isEmpty()) obj else "$obj.$sub"
    override fun equals(other: Any?) = other is SelRef && other.obj == obj && other.sub == sub
    override fun hashCode() = obj.hashCode() * 31 + sub.hashCode()
}

/** Mate creation ⇐ FreeCAD TaskAssemblyCreateJoint: pick two connectors on different components. */
class MateState(val jointType: String) { val picks = mutableStateListOf<SelRef>() }

/** An open feature dialog (FreeCAD task dialog equivalent), bound to an open transaction. */
class EditState(val obj: String, val type: String, val title: String, val created: Boolean) {
    var undoMark = 0
    var props by mutableStateOf<List<FcProperty>>(emptyList())
    var activeQuery by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var autoQuery = created          // a new feature with empty references starts waiting for geometry taps
}

/** In-viewport drag arrow bound to a quantity property (e.g. Pad/Pocket Length). */
class Manipulator(val obj: String, val prop: String, val base: FloatArray, val dir: FloatArray, var value: Double)

/** Draft working plane (WorkingPlane.PlaneBase: position, u, v, axis). */
class WorkPlane(val pos: DoubleArray, val u: DoubleArray, val v: DoubleArray, val w: DoubleArray) {
    fun json() = mapOf("pos" to pos.toList(), "u" to u.toList(), "v" to v.toList(), "w" to w.toList())
    fun local(p: DoubleArray): DoubleArray { val d = DoubleArray(3) { p[it] - pos[it] }; return doubleArrayOf(dot(d, u), dot(d, v), dot(d, w)) }
    fun global(l: DoubleArray) = DoubleArray(3) { pos[it] + u[it] * l[0] + v[it] * l[1] + w[it] * l[2] }
    companion object {
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val XY = WorkPlane(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))
        val XZ = WorkPlane(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, -1.0, 0.0))
        val YZ = WorkPlane(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 0.0))
    }
}

/** A Draft tool collecting points like the desktop Snapper does (gui_base_original / gui_tool_utils.getPoint). */
class DraftTool(val id: String, val label: String, val prompts: List<String>, val minPts: Int, val maxPts: Int,
                val modifier: Boolean = false, val onDone: (List<DoubleArray>) -> Unit) {
    val pts = mutableStateListOf<DoubleArray>()
    var hover by mutableStateOf<DoubleArray?>(null)
    var snapKind by mutableStateOf<String?>(null)
    var copy by mutableStateOf(false)
    /** Streaming tools (FreeCAD commands waiting on Snapper.getPoint): every point is delivered immediately. */
    var onPoint: ((DoubleArray) -> Unit)? = null
    /** Points on the plane through the view target facing the viewer (Mesh_PolyCut & co.), no snapping. */
    var viewPlane = false
    var onCancel: (() -> Unit)? = null
    var prompt2 by mutableStateOf<String?>(null)
    val prompt get() = prompt2 ?: prompts.getOrElse(pts.size) { prompts.lastOrNull() ?: "Pick point" }
}

/** Free-text input (sketch text, labels). */
class TextPrompt(val title: String, val initial: String, val multiline: Boolean = false, val onAccept: (String) -> Unit)

class NumpadRequest(val title: String, val initial: String, val units: List<String>, val onAccept: (String) -> Unit)

/** UI state for one open FreeCAD document; all engine work is funnelled through [eng]. */
class AppState(private val ctx: Context) {
    val engine = Engine.get(ctx)
    val catalog = Catalog.get(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var status by mutableStateOf("Starting the CAD engine…")
    var ready by mutableStateOf(false)
    var docName by mutableStateOf("Unnamed")
    val objects = mutableStateListOf<FcObject>()
    var meshes by mutableStateOf<Map<String, FcMesh>>(emptyMap())
    /** Failed features shown as their last good shape (failed name -> object the mesh came from), drawn red and see-through. */
    var ghosts by mutableStateOf<Map<String, String>>(emptyMap())
    val selection = mutableStateListOf<SelRef>()
    var workspace by mutableStateOf(Workspaces.partStudio)
    var openToolset by mutableStateOf<ToolsetDef?>(null)
    private val recentByWorkspace = androidx.compose.runtime.mutableStateMapOf<String, List<String>>()
    /** Recently used tools of the current tab (kept in the toolbar strip). */
    val recent: List<String> get() = recentByWorkspace[workspace.id] ?: emptyList()
    var edit by mutableStateOf<EditState?>(null)
    var numpad by mutableStateOf<NumpadRequest?>(null)
    var sketch by mutableStateOf<SketchEditor?>(null)
    var mate by mutableStateOf<MateState?>(null)
    var partPicker by mutableStateOf<List<Pair<String, String>>?>(null)
    var manipulator by mutableStateOf<Manipulator?>(null)
    var showGrid by mutableStateOf(true)
    var textPrompt by mutableStateOf<TextPrompt?>(null)
    /** Generic option picker (Carbon copy source, Reorient plane ...). */
    class Choice(val title: String, val options: List<Pair<String, String>>, val onPick: (String) -> Unit)
    var choice by mutableStateOf<Choice?>(null)
    fun choose(title: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
        if (options.isEmpty()) { message = "Nothing to choose from"; return }
        choice = Choice(title, options) { v -> choice = null; onPick(v) }
    }
    /** Multi-select picker (TechDraw view sources, projection set ...). */
    class MultiChoice(val title: String, val options: List<Pair<String, String>>, val initial: Set<String>, val onDone: (List<String>) -> Unit)
    var multiChoice by mutableStateOf<MultiChoice?>(null)
    fun chooseMany(title: String, options: List<Pair<String, String>>, initial: Set<String> = emptySet(), onDone: (List<String>) -> Unit) {
        if (options.isEmpty()) { message = "Nothing to choose from"; return }
        multiChoice = MultiChoice(title, options, initial) { v -> multiChoice = null; if (v.isEmpty()) message = "Nothing selected" else onDone(v) }
    }
    val inAssembly get() = workspace.id == "Assembly"
    /** Part selection gate (Part_VertexSelection / EdgeSelection / FaceSelection): "Vertex", "Edge", "Face" or null. */
    var selFilter by mutableStateOf<String?>(null)
    /** Armed box drag for the next one-finger drag: "faces" (Part_BoxSelection, Std_BoxElementSelection),
     *  "objects" (Std_BoxSelection) or "zoom" (Std_ViewBoxZoom). */
    var boxSelect by mutableStateOf<String?>(null)
    // ---- display state (FreeCAD view-provider / 3D-view settings, kept app-side)
    var drawStyle by mutableStateOf("As Is")
    val colors = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    /** Part_ColorPerFace: (object, face index) -> colour. */
    val faceColors = androidx.compose.runtime.mutableStateMapOf<Pair<String, Int>, Int>()
    /** Std_TextureMapping: object -> texture image.  Std_ViewLoadImage: the image shown in the image viewer. */
    val textures = androidx.compose.runtime.mutableStateMapOf<String, android.graphics.Bitmap>()
    var imageView by mutableStateOf<android.graphics.Bitmap?>(null)
    /** 3D view background (View preferences): 0 plain, 1 linear gradient, 2 radial gradient. */
    var background by mutableStateOf(0)
    val transparent = androidx.compose.runtime.mutableStateListOf<String>()
    var axisCross by mutableStateOf(false)
    var selBoundingBox by mutableStateOf(false)
    var clipPlane by mutableStateOf<FloatArray?>(null)
    /** Not selectable in the 3D view (Std_ToggleSelectability). */
    val unselectable = androidx.compose.runtime.mutableStateListOf<String>()
    /** The 3D view, for view commands (set by the Shell). */
    var viewport: ViewportView? = null
    /** MobileCAD XR: the model is shown in the room around the user; the viewport only turns controller rays into its touch
     *  handling, and [xrChanged] reports when what it would draw changed. */
    var xr = false
    var xrChanged: (() -> Unit)? = null
    /** Std_SelBack / Std_SelForward history. */
    private val selHistory = ArrayList<List<SelRef>>(); private var selPos = -1
    fun recordSelection() { val cur = selection.toList(); if (selHistory.getOrNull(selPos) == cur) return
        while (selHistory.size > selPos + 1) selHistory.removeAt(selHistory.size - 1); selHistory.add(cur); selPos = selHistory.size - 1 }
    fun selStep(d: Int) { val p = selPos + d; if (p !in selHistory.indices) return; selPos = p; selection.clear(); selection.addAll(selHistory[p]); viewport?.sync() }
    /** Read-only report dialog (Part_ShapeInfo / Part_CheckGeometry ...). */
    var info by mutableStateOf<Pair<String, String>?>(null)
    /** Open task panel / dialog of a FreeCAD Python command (mobilecad.taskpanel form JSON), see [GuiCommands]. */
    var taskPanel by mutableStateOf<org.json.JSONObject?>(null)
    /** File chooser request served by the Shell's Storage Access Framework launchers.  [kind]: "open", "merge", "import",
     *  "macro" (pick an existing file) or "save", "savecopy", "export", "screenshot", "pdf", "graph" (create one). */
    class FileRequest(val kind: String, val names: List<String> = emptyList(), val suggested: String = "") {
        val create get() = kind.startsWith("robotexport") || kind in setOf("save", "savecopy", "export", "screenshot", "pdf", "graph", "pagepdf", "pagesvg", "pagedxf", "sheetexport", "panelsave")
    }
    /** Where the document was last saved/opened (Std_Save writes there without asking). */
    var docUri: String? = null
    private val prefs = ctx.getSharedPreferences("mobilecad", Context.MODE_PRIVATE)
    /** Std_RecentFiles: (uri, name), newest first. */
    fun recentFiles(): List<Pair<String, String>> = (prefs.getString("recent", "") ?: "").lines().filter { '\t' in it }.map { it.substringBefore('\t') to it.substringAfter('\t') }
    fun addRecent(uri: String, name: String) = prefs.edit().putString("recent", (listOf(uri to name) + recentFiles().filter { it.first != uri }).take(8)
        .joinToString("\n") { "${it.first}\t${it.second}" }).apply()
    /** Preferences ▸ Theme: "system" (follow Android's dark theme), "light" or "dark". */
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system")
    fun setThemeMode(t: String) { theme = t; prefs.edit().putString("theme", t).apply(); viewport?.sync() }
    var treeOpen by mutableStateOf(true)
    /** Containers folded in the feature tree (Std_TreeCollapse / Std_TreeExpand). */
    val collapsed = mutableStateListOf<String>()
    /** Tree items opened by the user that start collapsed (FreeCAD: features with claimed children, the Origin). */
    val expanded = mutableStateListOf<String>()
    // ---- Spreadsheet (Variables tab)
    val inSheet get() = workspace.id == "Spreadsheet"
    var sheet by mutableStateOf<String?>(null)
    var sheetData by mutableStateOf<org.json.JSONObject?>(null)
    /** Selected cell or range ("B3", "A1:C4"). */
    var sheetSel by mutableStateOf("A1")
    fun refreshSheet() = eng({
        val l = call("sheet_list") as org.json.JSONArray
        val names = List(l.length()) { l.getJSONArray(it).getString(0) }
        val sh = sheet?.takeIf { it in names } ?: names.firstOrNull()
        sh to (sh?.let { call("sheet_data", mapOf("name" to it)) as org.json.JSONObject })
    }) { (sh, d) -> sheet = sh; sheetData = d }

    // ---- TechDraw (Drawing tab)
    val inDrawing get() = workspace.id == "TechDraw"
    var page by mutableStateOf<String?>(null)
    var pageRender by mutableStateOf<org.json.JSONObject?>(null)
    var pageView: PageView? = null
    /** Re-reads the current page's drawing (td_render); picks the first page when none is chosen. */
    fun refreshPage() = eng({
        val pages = call("td_pages") as org.json.JSONArray
        val names = List(pages.length()) { pages.getJSONArray(it).getString(0) }
        val p = page?.takeIf { it in names } ?: names.firstOrNull()
        p to (p?.let { call("td_render", mapOf("page" to it)) as org.json.JSONObject })
    }) { (p, r) -> page = p; pageRender = r; pageView?.invalidate() }

    /** Live drag of a page item: shift its drawing in the cached render until the move is committed. */
    private val dragBase = HashMap<String, DoubleArray>()
    fun pageDragPreview(name: String, dx: Double, dy: Double) {
        val vs = pageRender?.optJSONArray("views") ?: return
        for (i in 0 until vs.length()) { val v = vs.getJSONObject(i); if (v.getString("name") != name) continue
            val (kx, ky) = when { v.has("dim") -> "dim.tx" to "dim.ty"; v.has("balloon") -> "balloon.bx" to "balloon.by"; else -> "x" to "y" }
            fun obj(path: String) = if ('.' in path) v.getJSONObject(path.substringBefore('.')) else v
            val b = dragBase.getOrPut(name) { doubleArrayOf(obj(kx).getDouble(kx.substringAfter('.')), obj(ky).getDouble(ky.substringAfter('.'))) }
            obj(kx).put(kx.substringAfter('.'), b[0] + dx); obj(ky).put(ky.substringAfter('.'), b[1] + dy) }
    }
    /** TechDraw_MoveView-style drag on the page: X / Y of the view (dimension text, balloon bubble) by (dx, dy) mm. */
    fun movePageItem(name: String, dx: Double, dy: Double) { dragBase.remove(name)
        step("Move", { run("o = doc.getObject(args['n'])\nif 'X' in o.PropertiesList:\n    o.X = o.X.Value + args['dx']; o.Y = o.Y.Value + args['dy']",
            mapOf("n" to name, "dx" to dx, "dy" to dy)) }) }

    // ---- Draft
    var draft by mutableStateOf<DraftTool?>(null)
    var workPlane by mutableStateOf(WorkPlane.XY)
    /** Draft snap toggles (Draft_Snap_*); "Lock" is the master switch. */
    val draftSnaps = mutableStateListOf("Lock", "Endpoint", "Midpoint", "Center", "Near", "Intersection", "Perpendicular", "Grid", "WorkingPlane")
    var gridSpacing = 1.0
    /** Snapper targets [x y z kind]* (kind 0 endpoint, 1 midpoint, 2 centre) for the displayed shapes. */
    var snapTargets: FloatArray = FloatArray(0)
    var constructionMode by mutableStateOf(false)
    var autoGroup by mutableStateOf<String?>(null)
    /** Draft annotations drawn in the overlay: name -> (segments [x1 y1 z1 x2 y2 z2]*, texts [(pos, text)]). */
    /** Text height of each annotation in model units (desktop FontSize): text scales with zoom like on the desktop. */
    var annotationSize: Map<String, Float> = emptyMap()
    var annotations by mutableStateOf<Map<String, Pair<FloatArray, List<Pair<FloatArray, String>>>>>(emptyMap())

    fun startDraft(t: DraftTool) {
        edit = null; mate = null; sketch?.let { return }
        draft = t
        eng({ call("snap_points", mapOf("names" to meshes.keys.toList())) as org.json.JSONArray }) { a ->
            snapTargets = FloatArray(a.length() * 4) { i -> a.getJSONArray(i / 4).getDouble(i % 4).toFloat() } }
    }
    fun draftPoint(p: DoubleArray) {
        val t = draft ?: return
        t.onPoint?.let { cb -> t.pts.add(p); t.hover = null; cb(p); viewport?.refresh(); return }
        t.pts.add(p); t.hover = null
        if (t.maxPts > 0 && t.pts.size >= t.maxPts) finishDraft()
        else viewport?.refresh()
    }
    fun finishDraft() {
        val t = draft ?: return
        if (t.pts.size < t.minPts) { message = "${t.label}: ${t.prompt}"; return }
        draft = null; t.onDone(t.pts.toList()); viewport?.refresh()
    }
    fun cancelDraft() { val t = draft; draft = null; t?.onCancel?.invoke(); viewport?.refresh() }

    /** Runs a Draft op as one transaction, then files the result like Draft's AutoGroup / construction mode do. */
    fun draftStep(label: String, block: Engine.() -> Any?) = eng({
        begin(label)
        try {
            val r = block()
            (r as? String)?.let { n ->
                autoGroup?.let { g -> call("draft_sel", mapOf("tool" to "AddToLayer", "sel" to listOf(listOf(n, emptyList<String>())), "opts" to mapOf("layer" to g))) }
                if (constructionMode) call("draft_sel", mapOf("tool" to "AddConstruction", "sel" to listOf(listOf(n, emptyList<String>()))))
            }
            recompute(); commit(); r
        } catch (e: Throwable) { abort(); throw e }
    }) { refresh() }
    var fullscreen by mutableStateOf(false)

    /** Std_Revert / Std_RecentFiles: (re)open a document from its content URI. */
    fun reopen(uri: android.net.Uri) {
        val ctx = viewport?.context ?: return
        val f = java.io.File(ctx.cacheDir, "open.FCStd")
        try { ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } }
        catch (e: Exception) { message = "Cannot open: ${e.message}"; return }
        docUri = uri.toString(); open(f.absolutePath)
    }

    fun switchWorkspace(id: String) {
        workspace = Workspaces.main(catalog).firstOrNull { it.id == id }
            ?: Workspaces.more.firstOrNull { it.first == id }?.let { Workspaces.forWorkbench(catalog, it.first, it.second) } ?: return
        openToolset = null; cancelMate(); refresh()
    }

    /** Show each object's ShapeMaterial appearance (desktop view providers colour shapes by their material). */
    fun applyMaterialColors(names: List<String>) = eng({ call("material_color", mapOf("names" to names)) as org.json.JSONObject }) { j ->
        j.keys().forEach { k -> val a = j.getJSONArray(k); var c = (a.getInt(0) shl 16) or (a.getInt(1) shl 8) or a.getInt(2)
            if (c == 0) c = 0xA8AEB4            // metals: black diffuse + white specular -> neutral metal grey
            colors[k] = c }
        viewport?.sync()
    }

    /** Std_CommandLine: FreeCAD's Python console (App, doc predefined); each entry is one undoable step. */
    val consoleLog = mutableStateListOf<String>()
    fun console() { textPrompt = TextPrompt("Python console", "") { code ->
        val mark = undoNames.size
        eng({ begin("Python"); try { val r = call("console", mapOf("code" to code)); recompute(); commit(); r } catch (e: Throwable) { abort(); throw e } }) { r ->
            consoleLog.add(">>> $code"); (r as? String)?.trimEnd()?.takeIf { it.isNotEmpty() }?.let { consoleLog.add(it) }
            info = "Python console" to consoleLog.takeLast(30).joinToString("\n"); refresh() } } }

    /** Saved views (Std_StoreWorkingView, Std_ViewSaveCamera, Std_FreezeViews). */
    val savedViews = androidx.compose.runtime.mutableStateMapOf<String, FloatArray>()
    var fileRequest by mutableStateOf<FileRequest?>(null)
    /** Numpad entry of a plain number (dialog values asked before a command runs, like the desktop's modal dialogs). */
    fun askNumber(title: String, initial: Double, units: List<String> = listOf("mm"), onValue: (Double) -> Unit) {
        numpad = NumpadRequest(title, initial.toString(), units) { v -> numpad = null
            Regex("""-?\d+(\.\d+)?""").find(v)?.value?.toDoubleOrNull()?.let(onValue) ?: run { message = "Not a number: $v" } }
    }
    var message by mutableStateOf<String?>(null)
    var undoNames by mutableStateOf<List<String>>(emptyList())
    var redoNames by mutableStateOf<List<String>>(emptyList())
    val sceneVersion = mutableIntStateOf(0)

    // ------------------------------------------------------------------ document recovery (FreeCAD AutoRecovery)
    // The open document is copied to files/recovery every minute and when the app goes to the background, if it changed
    // since the last save; a normal Save removes the copy.  A copy found at start means the app died with unsaved
    // work: it is reopened (still saving to its original file).
    private val recoveryDir = java.io.File(ctx.filesDir, "recovery")
    private val recoveryFile get() = java.io.File(recoveryDir, "recovery.FCStd")
    private val recoveryMeta get() = java.io.File(recoveryDir, "recovery.json")
    private var savedSig: String? = null; private var autosavedSig: String? = null; private var autosaving = false
    private fun docSig() = undoNames.joinToString("\u0001") + "\u0002" + redoNames.joinToString("\u0001") + "\u0002" + docName
    /** After Std_Save / Save As (and when a document is opened or created): nothing unsaved, no recovery copy. */
    fun markSaved() { savedSig = docSig(); autosavedSig = savedSig; recoveryFile.delete(); recoveryMeta.delete() }
    fun autosave() {
        if (!ready || autosaving) return
        val sig = docSig()
        if (sig == autosavedSig) return
        if (sig == savedSig) { recoveryFile.delete(); recoveryMeta.delete(); autosavedSig = sig; return }
        autosaving = true
        val meta = org.json.JSONObject().put("uri", docUri ?: org.json.JSONObject.NULL).put("name", docName).put("time", System.currentTimeMillis())
        scope.launch {
            try {
                withContext(engine.dispatcher) {
                    recoveryDir.mkdirs(); val tmp = java.io.File(recoveryDir, "saving.FCStd")
                    engine.call("save_copy", mapOf("path" to tmp.absolutePath))
                    tmp.renameTo(recoveryFile); recoveryMeta.writeText(meta.toString())
                }
                autosavedSig = sig
            } catch (e: Throwable) { Log.w("AppState", "autosave", e) } finally { autosaving = false }
        }
    }
    private fun recoverIfCrashed() {
        if (!recoveryFile.exists()) return
        val meta = runCatching { org.json.JSONObject(recoveryMeta.readText()) }.getOrNull()
        val name = meta?.optString("name")?.ifEmpty { null } ?: "Recovered"
        val copy = java.io.File(ctx.cacheDir, "$name.FCStd"); recoveryFile.copyTo(copy, overwrite = true)
        val time = meta?.optLong("time")?.takeIf { it > 0 }?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
        open(copy.absolutePath, recovered = true) {
            docName = name; docUri = meta?.optString("uri")?.takeIf { it.isNotEmpty() && it != "null" }
            message = "Recovered unsaved work${time?.let { " from $it" } ?: ""} (the app had closed unexpectedly). Save to keep it."
        }
    }

    val appVersion: String get() = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    /** About ▸ Open-source licenses: the components in the APK (assets/licenses, made by release/gen_notices.py) and their texts. */
    fun showLicenses() {
        val idx = try { org.json.JSONArray(ctx.assets.open("licenses/index.json").bufferedReader().readText()) } catch (e: Exception) { message = "No license notices in this build"; return }
        val items = List(idx.length()) { idx.getJSONObject(it) }
        choose("Open-source licenses", items.mapIndexed { i, o -> i.toString() to "${o.getString("name")} — ${o.getString("license")}" }) { k ->
            val o = items[k.toInt()]; val texts = o.getJSONArray("texts")
            fun show(t: org.json.JSONObject) { info = o.getString("name") to ("License: ${o.getString("license")}\nSource: ${o.getString("source")}\n\n" +
                ctx.assets.open("licenses/" + t.getString("file")).bufferedReader().readText()) }
            when (texts.length()) {
                0 -> info = o.getString("name") to "License: ${o.getString("license")}\nSource: ${o.getString("source")}"
                1 -> show(texts.getJSONObject(0))
                else -> choose(o.getString("name"), List(texts.length()) { it.toString() to texts.getJSONObject(it).getString("title") }) { j -> show(texts.getJSONObject(j.toInt())) }
            }
        }
    }

    private val readyCallbacks = mutableListOf<() -> Unit>()
    fun whenReady(f: () -> Unit) { if (ready) f() else readyCallbacks.add(f) }

    fun start() = scope.launch {
        MobileCommands.commandTypes = catalog.commandTypes
        MobileCommands.catalogRef = catalog
        try {
            engine.start { status = it }
            docName = withContext(engine.dispatcher) { engine.newDocument("Unnamed") }
            ready = true; status = ""
            refresh()
            GuiCommands.load(this@AppState)
            savedSig = docSig(); autosavedSig = savedSig          // the empty start document is not unsaved work
            recoverIfCrashed()
            readyCallbacks.forEach { it() }; readyCallbacks.clear()
            launch { while (true) { kotlinx.coroutines.delay(60_000); autosave() } }
        } catch (e: Throwable) {
            status = "The CAD engine failed to start:\n" + (e.message ?: e.toString()); Log.e("AppState", "start", e)
        }
    }

    /** Runs [block] on the engine thread, then [then] on the main thread; errors become a user message. */
    fun <T> eng(block: Engine.() -> T, then: (T) -> Unit = {}) = scope.launch {
        try { then(withContext(engine.dispatcher) { engine.block() }) }
        catch (e: Throwable) { message = (e.message ?: e.toString()).lineSequence().first(); Log.e("AppState", "engine", e) }
    }

    /** Re-reads the document tree and re-tessellates every displayed shape (as FreeCAD's view providers would). */
    fun refresh(after: () -> Unit = {}) = eng({
        val objs = objects()
        val shown = objs.filter { (it.hasShape || it.invalid) && it.visible && displayable(it, objs) }   // a failed feature has no shape, but shows its ghost
        // a failed feature keeps the model on screen: its last good predecessor, drawn as a red ghost
        val gh = shown.filter { it.invalid }.mapNotNull { o -> (call("last_good", mapOf("name" to o.name)) as? String)?.let { o.name to it } }.toMap()
        val m = shown.associate { it.name to tessellate(gh[it.name] ?: it.name) }
        val hist = undoState()
        val ann = if (objs.any { it.type == "App::FeaturePython" || it.type == "App::AnnotationLabel" }) call("annotations") as? org.json.JSONObject else null
        Triple(objs, m to gh, hist) to ann
    }) { (t, ann) -> val (objs, mg, ur) = t; val (m, gh) = mg
        objects.clear(); objects.addAll(objs); ghosts = gh; meshes = m
        annotations = ann?.let { j -> j.keys().asSequence().associateWith { k -> val o = j.getJSONObject(k)
            val ls = o.getJSONArray("lines"); val seg = FloatArray(ls.length() * 6) { i -> ls.getJSONArray(i / 6).getJSONArray(i % 6 / 3).getDouble(i % 3).toFloat() }
            val ts = o.getJSONArray("texts"); seg to List(ts.length()) { i -> ts.getJSONArray(i).let { tx -> val p = tx.getJSONArray(0)
                floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.getDouble(2).toFloat()) to tx.getString(1) } } } } ?: emptyMap()
        annotationSize = ann?.let { j -> j.keys().asSequence().associateWith { k -> j.getJSONObject(k).optDouble("size", 3.5).toFloat() } } ?: emptyMap()
        setHistory(ur)
        selection.removeAll { s -> objs.none { it.name == s.obj } }
        sceneVersion.intValue++
        if (inDrawing) refreshPage()
        if (inSheet) refreshSheet()
        after()
    }

    /** FreeCAD draws features, not their containers: a Body/Part's shape is its Tip's, already shown by the feature. */
    private fun displayable(o: FcObject, all: List<FcObject>): Boolean {
        // tabs: the Assembly tab shows the assembly's components, other tabs show the parts themselves
        if (inAssembly) return o.assembly != null && o.type == "App::Link"
        if (o.assembly != null) return false
        if (o.type in setOf("PartDesign::Body", "App::Part", "App::Origin", "App::DocumentObjectGroup", "App::Line", "App::Plane", "App::Point")) return false
        // origin features are hidden by default in FreeCAD (and their axes are infinite)
        if (o.parents.any { p -> all.firstOrNull { it.name == p }?.type == "App::Origin" }) return false
        // inside a hidden body → hidden
        return all.none { p -> p.type == "PartDesign::Body" && o.name in p.group && !p.visible }
    }

    fun obj(name: String) = objects.firstOrNull { it.name == name }

    // ------------------------------------------------------------------ selection
    fun select(ref: SelRef?, additive: Boolean = true) {
        val m = mate
        if (m != null && ref != null) { mateAdd(m, ref); return }
        val e = edit
        if (e?.activeQuery != null && ref != null) { addToQuery(e, ref); return }
        val sk = sketch
        if (sk != null) return
        if (ref == null) return
        if (ref.obj in unselectable) return
        if (ref in selection) selection.remove(ref) else { if (!additive) selection.clear(); selection.add(ref) }
        recordSelection()
    }
    fun clearSelection() { selection.clear() }
    fun selJson(): List<List<Any>> = selection.groupBy { it.obj }.map { (o, refs) -> listOf(o, refs.map { it.sub }.filter { it.isNotEmpty() }) }

    // ------------------------------------------------------------------ undo / redo (FreeCAD transactions, global like desktop)
    // Every user action is its own transaction (a sketch line, a constraint, a property change, a new feature), so the top-bar
    // undo/redo works everywhere — in the sketcher, in a feature dialog and in the model. Cancel (✗) in a sketch or dialog undoes
    // back to the position recorded on entry, which is what FreeCAD's abortTransaction gives on desktop.
    // While a sketch or dialog is open, undo / redo stay within the steps made there: one more undo would remove the sketch
    // or feature being edited (its creation step) and close the editor with everything in it.
    private var undoMark = 0
    var undoPos by mutableIntStateOf(0); private set
    private var editFloor = 0          // history position the open sketch / dialog started from (after its creation step)
    private var editRedo = 0           // redo steps that were already there on entry (they belong to the model, not the editor)
    private val editing get() = sketch != null || edit != null
    val canUndo get() = undoNames.isNotEmpty() && (!editing || undoPos > editFloor)
    val canRedo get() = redoNames.isNotEmpty() && (!editing || redoNames.size > editRedo)
    /** Bounds undo / redo to the editor being opened: [mark] = position before its creation step (null: an existing object). */
    private fun enterEditHistory(mark: Int?) { editFloor = if (mark != null) mark + 1 else undoPos; editRedo = if (mark != null) 0 else redoNames.size }

    fun undo() { if (canUndo) eng({ undo(); undoState() }) { h -> setHistory(h); afterHistory() } }
    fun redo() { if (canRedo) eng({ redo(); undoState() }) { h -> setHistory(h); afterHistory() } }
    fun setHistory(h: History) { undoNames = h.undo; redoNames = h.redo; undoPos = h.pos }

    private fun afterHistory() = refresh {
        // a drawing tool's placed points refer to geometry the undo may have removed: start the tool over
        sketch?.let { s -> if (objects.none { it.name == s.name }) sketch = null else { s.finishChain(); s.reload(keepView = true) } }
        edit?.let { e -> if (objects.none { it.name == e.obj }) edit = null else reloadProps(e) }
    }

    /** Runs [block] as one undoable step named [label], then refreshes. */
    fun step(label: String, block: Engine.() -> Unit, after: () -> Unit = {}) = eng({
        begin(label)
        try { block(); recompute(); commit() } catch (e: Throwable) { abort(); throw e }
    }) { refresh(after) }

    private fun rollbackTo(mark: Int) = eng({
        var h = undoState()
        while (h.pos > mark && h.undo.isNotEmpty()) { undo(); h = undoState() }
        recompute(); h
    }) { h -> setHistory(h); refresh() }

    fun save(path: String) = eng({ saveDocument(path) }) { message = "Saved $it" }
    /** Opens a document with the look the desktop saved for it (GuiDocument.xml: colours, transparency, per-face colours,
     *  what is hidden) and frames it.  Nothing of the previous document's look is kept. */
    fun open(path: String, recovered: Boolean = false, done: () -> Unit = {}): kotlinx.coroutines.Job { closeTools(); return eng({ val n = openDocument(path); n to call("view_data") as org.json.JSONObject? }) { (n, vd) ->
        docName = n; selection.clear()
        colors.clear(); transparent.clear(); faceColors.clear(); textures.clear()
        vd?.optJSONObject("colors")?.let { c -> c.keys().forEach { k -> val a = c.getJSONArray(k)
            colors[k] = (Math.round(a.getDouble(0) * 255).toInt() shl 16) or (Math.round(a.getDouble(1) * 255).toInt() shl 8) or Math.round(a.getDouble(2) * 255).toInt() } }
        vd?.optJSONObject("transparency")?.keys()?.forEach { transparent.add(it) }
        vd?.optJSONObject("faces")?.let { f -> f.keys().forEach { k -> val a = f.getJSONArray(k)
            for (i in 0 until a.length()) a.getJSONArray(i).let { c -> faceColors[k to i + 1] = (Math.round(c.getDouble(0) * 255).toInt() shl 16) or
                (Math.round(c.getDouble(1) * 255).toInt() shl 8) or Math.round(c.getDouble(2) * 255).toInt() } } }
        viewport?.refitOnNextSync()
        // open on the tab that shows the document: Assembly when it has one, Part otherwise
        refresh { val asm = objects.any { it.type == "Assembly::AssemblyObject" }
            if (asm && !inAssembly) switchWorkspace("Assembly") else if (!asm && workspace.id != "partstudio" && workspace.id in setOf("Assembly", "TechDraw", "Spreadsheet")) switchWorkspace("partstudio")
            // an opened file has nothing unsaved; a recovered one keeps its recovery copy until it is saved
            if (recovered) { savedSig = null; autosavedSig = null } else markSaved()
            done() }
    } }

    // ------------------------------------------------------------------ feature dialog
    /** Creates a feature as one undoable step and opens its dialog (desktop: command + task panel). */
    fun createFeature(label: String, script: Engine.() -> String) = eng({
        val mark = undoState().pos
        begin(label)
        try { val n = script(); recompute(); commit(); n to mark } catch (e: Throwable) { abort(); throw e }
    }) { (name, mark) -> selection.clear(); refresh { openEditor(name, mark) } }

    fun openEditor(name: String, mark: Int? = null) {
        val o = obj(name) ?: return
        if (o.type == "Sketcher::SketchObject") { editSketch(name, mark); return }
        val e = EditState(name, o.type, o.label, mark != null)
        e.undoMark = mark ?: undoPos
        enterEditHistory(mark)
        edit = e
        reloadProps(e)
    }

    fun reloadProps(e: EditState) = eng({ properties(e.obj) to call("manipulator", mapOf("name" to e.obj)) }) { (p, m) ->
        e.props = p
        if (e.autoQuery) {
            e.autoQuery = false
            p.firstOrNull { it.name == "References" && it.value.let { v -> v == null || v.toString() in setOf("", "[]", "null") } }?.let { e.activeQuery = it.name }
        }
        manipulator = (m as? org.json.JSONObject)?.let { j ->
            fun v(k: String) = j.getJSONArray(k).let { a -> FloatArray(3) { a.getDouble(it).toFloat() } }
            Manipulator(e.obj, j.getString("prop"), v("base"), v("dir"), j.getDouble("value"))
        }
        e.error = objects.firstOrNull { it.name == e.obj }?.takeIf { it.invalid }?.let { it.error ?: "${it.label} failed to recompute" }
    }

    fun setProp(e: EditState, p: FcProperty, value: Any?) = step("Edit ${p.name.removePrefix("@")}", { setProperty(e.obj, p.name, value) }) { reloadProps(e) }

    /** A vector / placement component ("Placement.Base.x", "Direction.z" ...): a value, or an expression bound to that path. */
    fun setPart(e: EditState, path: String, text: String) = step("Edit ${path.substringBefore('.')}", {
        if (Regex("""^-?[\d.]+\s*[a-zA-Z°µ]*$""").matches(text.trim())) setProperty(e.obj, path, text.trim()) else setExpression(e.obj, path, text.trim())
    }) { reloadProps(e) }

    fun setExpr(e: EditState, p: FcProperty, text: String) = step("Edit ${p.name.removePrefix("@")}", {
        // numbers with units are plain values; anything else is a FreeCAD expression
        if (Regex("""^-?[\d.]+\s*[a-zA-Z°µ]*$""").matches(text.trim())) setProperty(e.obj, p.name, text.trim()) else setExpression(e.obj, p.name, text.trim())
    }) { reloadProps(e) }

    private fun addToQuery(e: EditState, ref: SelRef) {
        val p = e.props.firstOrNull { it.name == e.activeQuery } ?: return
        if (e.props.any { it.name == "JointType" } && p.name in setOf("Reference1", "Reference2")) { repickJointRef(e, p.name, ref); return }
        val cur = (p.value as? JSONArray)?.let { a -> (0 until a.length()).map { i -> a.getJSONArray(i) }
            .flatMap { x -> val subs = x.getJSONArray(1); if (subs.length() == 0) listOf(SelRef(x.getString(0))) else (0 until subs.length()).map { SelRef(x.getString(0), subs.getString(it)) } } }
            ?: (p.value as? String)?.takeIf { it.isNotEmpty() }?.let { listOf(SelRef(it)) } ?: emptyList()
        // a pick on the edited feature's own result means the same geometry of its base (dress-up features)
        if (ref.obj == e.obj && ref.sub.isNotEmpty()) {
            eng({ call("map_to_base", mapOf("feature" to e.obj, "sub" to ref.sub)) as? JSONArray }) { m ->
                if (m == null) message = "That geometry does not exist on the base feature" else addToQuery(e, SelRef(m.getString(0), m.getString(1))) }
            return
        }
        // Part Fillet / Chamfer (DlgFilletEdges): a tapped edge of the base shape is added with the current radius, or removed
        if (p.type == "Part::PropertyFilletEdges") {
            val base = p.extra?.optString("base")
            if (ref.sub.isEmpty() || ref.sub.startsWith("Vertex") || (base != null && ref.obj != base)) { message = "Pick an edge or face of ${base ?: "the base shape"}"; return }
            val rows = (p.value as? JSONArray)?.let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { listOf(it.getString(0), it.getString(1), it.getString(2)) } } } ?: emptyList()
            val r = rows.firstOrNull()?.get(1) ?: "1 mm"
            // an edge toggles; a face toggles all its edges (all on unless every one already is)
            fun apply(edges: List<String>) {
                val on = rows.map { it[0] }.toSet()
                setProp(e, p, if (edges.all { it in on }) rows.filter { it[0] !in edges } else rows + edges.filter { it !in on }.map { listOf(it, r, r) })
            }
            if (ref.sub.startsWith("Face")) eng({ call("face_edges", mapOf("obj" to ref.obj, "face" to ref.sub)) as JSONArray }) { a -> apply((0 until a.length()).map { a.getString(it) }) }
            else apply(listOf(ref.sub))
            return
        }
        // PropertyLinkSub: one object, several sub-elements (Fillet / Chamfer edges): tap toggles a sub-element
        val next = when {
            p.type == "App::PropertyLink" -> listOf(ref)
            p.type == "App::PropertyLinkSub" -> if (cur.isNotEmpty() && cur.all { it.obj == ref.obj } && ref.sub.isNotEmpty())
                (if (ref in cur) cur - ref else cur + ref).ifEmpty { cur } else listOf(ref)
            ref in cur -> cur - ref
            else -> cur + ref
        }
        val value: Any? = when (p.type) {
            "App::PropertyLink", "App::PropertyLinkGlobal" -> next.firstOrNull()?.obj
            else -> next.groupBy { it.obj }.map { (o, rs) -> listOf(o, rs.map { it.sub }.filter { it.isNotEmpty() }) }
        }
        setProp(e, p, value)
    }

    /** Joint connector re-pick (TaskAssemblyCreateJoint selection), keeping the other connector. */
    private fun repickJointRef(e: EditState, which: String, ref: SelRef) {
        if (ref.sub.isEmpty()) { message = "Pick a face, edge or vertex"; return }
        fun current(name: String): List<Any?>? = (e.props.firstOrNull { it.name == name }?.value as? JSONArray)?.optJSONArray(0)?.let { x ->
            listOf(x.getString(0), x.optJSONArray(1)?.optString(0) ?: "", null) }
        val r1 = if (which == "Reference1") listOf(ref.obj, ref.sub, ref.point?.map { it.toDouble() }) else current("Reference1")
        val r2 = if (which == "Reference2") listOf(ref.obj, ref.sub, ref.point?.map { it.toDouble() }) else current("Reference2")
        e.activeQuery = null
        step("Edit joint", { call("asm_set_refs", mapOf("joint" to e.obj, "refs" to listOfNotNull(r1, r2))) }) { reloadProps(e) }
    }

    fun commitEdit() { edit = null; manipulator = null; refresh() }
    fun cancelEdit() { val e = edit ?: return; edit = null; manipulator = null; rollbackTo(e.undoMark) }

    // ------------------------------------------------------------------ manipulator drag = one transaction
    private var manipBusy = false
    private var manipPending: Double? = null
    fun manipStart(m: Manipulator) = eng({ begin("Edit ${m.prop}") })
    fun manipDrag(m: Manipulator, value: Double) {
        m.value = value
        if (manipBusy) { manipPending = value; return }
        manipBusy = true
        eng({ setProperty(m.obj, m.prop, "%.4f mm".format(java.util.Locale.ROOT, value)); recompute() }) {
            manipBusy = false
            refresh()
            manipPending?.let { manipPending = null; manipDrag(m, it) }
        }
    }
    fun manipEnd(m: Manipulator) = eng({
        setProperty(m.obj, m.prop, "%.4f mm".format(java.util.Locale.ROOT, m.value)); recompute(); commit(); undoState()
    }) { h -> setHistory(h); refresh { edit?.let { reloadProps(it) } } }

    // ------------------------------------------------------------------ sketch edit mode
    /** Camera before entering a sketch (desktop returns to it when the sketch is closed). */
    private var preSketchCamera: FloatArray? = null

    fun editSketch(name: String, mark: Int? = null) {
        preSketchCamera = viewport?.cameraState()
        val ed = SketchEditor(this, name)
        sketch = ed; selection.clear(); openToolset = null
        undoMark = mark ?: undoPos
        enterEditHistory(mark)
        ed.reload(); ed.loadModelRefs()
    }

    /** Before another document replaces this one: an open feature dialog / sketch / joint / Draft tool is cancelled
     *  (desktop FreeCAD closes the task panel of a document that goes away). */
    fun closeTools() {
        cancelDraft(); if (mate != null) cancelMate(); if (sketch != null) leaveSketch(false); cancelEdit()
    }

    fun leaveSketch(save: Boolean) {
        sketch = null
        preSketchCamera?.let { c -> viewport?.restoreCamera(c) }; preSketchCamera = null
        if (save) eng({ recompute() }) { refresh() } else rollbackTo(undoMark)
    }

    // ------------------------------------------------------------------ assembly
    fun startMate(type: String) {
        if (objects.none { it.type == "Assembly::AssemblyObject" }) { message = "Create an assembly first (Assembly ▸ Create Assembly)"; return }
        selection.clear(); edit = null; mate = MateState(type)
    }

    private fun mateAdd(m: MateState, ref: SelRef) {
        if (ref.sub.isEmpty()) { message = "Pick a face, edge or vertex"; return }
        if (m.picks.any { it.obj == ref.obj }) { message = "Pick the second connector on a different component"; return }
        m.picks.add(ref); selection.add(ref)
        if (m.picks.size == 2) {
            val refs = m.picks.map { listOf(it.obj, it.sub, it.point?.map { v -> v.toDouble() }) }
            mate = null; selection.clear()
            createFeature("${m.jointType} joint") { call("asm_joint", mapOf("jtype" to m.jointType, "refs" to refs)) as String }
        }
    }

    fun cancelMate() { mate = null; selection.clear() }

    fun openPartPicker() = eng({ call("asm_parts") as org.json.JSONArray }) { a ->
        if (objects.none { it.type == "Assembly::AssemblyObject" }) { message = "Create an assembly first"; return@eng }
        partPicker = List(a.length()) { i -> a.getJSONObject(i).let { it.getString("name") to it.getString("label") } }
    }

    fun insertPart(name: String) { partPicker = null
        eng({ begin("Insert"); val r = call("asm_insert", mapOf("part" to name)); commit(); r }) { refresh() } }

    /** Free-drag a component: move, then OndselSolver satisfies the joints (FreeCAD dragger). */
    fun dragComponent(link: String, d: FloatArray) = eng({ call("asm_drag", mapOf("link" to link, "dx" to d[0].toDouble(), "dy" to d[1].toDouble(), "dz" to d[2].toDouble())) }) { refresh() }

    // ------------------------------------------------------------------ commands
    fun run(id: String) {
        openToolset = null
        val ok = MobileCommands.run(this, id)
        if (ok) recentByWorkspace[workspace.id] = (listOf(id) + recent.filter { it != id }).take(6)
        else message = "${catalog.cmd(id)?.label ?: id} is not available on mobile yet"
    }
}
