package app.mobilecad.freecad

import org.json.JSONArray
import org.json.JSONObject

/**
 * Draft workbench (src/Mod/Draft/draftguitools).  Point tools collect their points with the touch Snapper
 * ([DraftTool], ViewportView.draftSnap) and then run mobilecad.draft, which calls the Draft API exactly as the desktop
 * tool's commit does.
 */
object DraftCommands {
    private fun pts(p: List<DoubleArray>) = p.map { it.toList() }
    private fun names(s: AppState) = s.selection.map { it.obj }.distinct()

    /** Creation tools: id -> (prompts, min points, max points (-1 = until Finish / double tap)). */
    private val makers = mapOf(
        "Line" to Triple(listOf("First point", "Last point"), 2, 2),
        "Wire" to Triple(listOf("First point", "Next point (double tap or Finish to end)"), 2, -1),
        "Rectangle" to Triple(listOf("First corner", "Opposite corner"), 2, 2),
        "Circle" to Triple(listOf("Center", "Point on the circle (radius)"), 2, 2),
        "Arc" to Triple(listOf("Center", "Start point (radius)", "End point"), 3, 3),
        "Arc_3Points" to Triple(listOf("First point", "Second point", "Third point"), 3, 3),
        "Ellipse" to Triple(listOf("First corner", "Opposite corner"), 2, 2),
        "Polygon" to Triple(listOf("Center", "Vertex (radius)"), 2, 2),
        "BSpline" to Triple(listOf("First point", "Next point (double tap or Finish to end)"), 2, -1),
        "BezCurve" to Triple(listOf("First point", "Next point (double tap or Finish to end)"), 2, -1),
        "CubicBezCurve" to Triple(listOf("Start point", "Control point", "Control point", "End point (then continue)"), 4, -1),
        "Point" to Triple(listOf("Point location"), 1, 1),
        "Text" to Triple(listOf("Text location"), 1, 1),
        "Dimension" to Triple(listOf("First point", "Second point", "Dimension line position"), 3, 3),
        "Label" to Triple(listOf("Target point", "Label position"), 2, 2),
        "ShapeString" to Triple(listOf("Text position"), 1, 1),
    )
    /** Modifiers on the selection: id -> (prompts, points). */
    private val modifiers = mapOf(
        "Move" to (listOf("Base point", "Destination") to 2),
        "Rotate" to (listOf("Center of rotation", "Base angle", "Rotation angle") to 3),
        "Scale" to (listOf("Base point") to 1),
        "Mirror" to (listOf("Mirror line start", "Mirror line end") to 2),
        "Offset" to (listOf("Offset point (side and distance)") to 1),
        "Stretch" to (listOf("Box corner", "Opposite box corner", "Base point", "Destination") to 4),
        "Trimex" to (listOf("New end / extrusion point") to 1),
        "Split" to (listOf("Split point on the selected edge") to 1),
        "Edit" to (listOf("Node to move", "New position") to 2),
    )
    private val selTools = setOf("Upgrade", "Downgrade", "Join", "Draft2Sketch", "Clone", "PathArray", "PathLinkArray", "PathTwistedArray",
        "PathTwistedLinkArray", "PointArray", "PointLinkArray", "Shape2DView", "Facebinder", "WireToBSpline", "Heal", "FlipDimension",
        "UpdateShape2DView", "AddConstruction")
    private val snaps = listOf("Lock", "Endpoint", "Midpoint", "Center", "Angle", "Intersection", "Perpendicular", "Extension", "Parallel",
        "Special", "Near", "Ortho", "Grid", "WorkingPlane", "Dimensions")
    private val groups = mapOf("ArcTools" to listOf("Arc", "Arc_3Points"), "BezierTools" to listOf("BezCurve", "CubicBezCurve"),
        "ArrayTools" to listOf("OrthoArray", "PolarArray", "CircularArray", "PathArray", "PathLinkArray", "PointArray", "PointLinkArray",
            "PathTwistedArray", "PathTwistedLinkArray"), "Shape2DViewTools" to listOf("Shape2DView", "UpdateShape2DView"))

    val ids: Set<String> = (makers.keys + modifiers.keys + selTools + snaps.map { "Snap_$it" } + groups.keys + setOf("OrthoArray", "PolarArray",
        "CircularArray", "Fillet", "Hatch", "Layer", "AddToLayer", "AddToGroup", "SelectGroup", "LayerManager", "AutoGroup", "SelectPlane",
        "WorkingPlaneProxy", "ToggleConstructionMode", "ToggleGrid", "ToggleDisplayMode", "ShowSnapBar", "Slope", "Hyperlink",
        "SetStyle", "ApplyStyle", "AnnotationStyleEditor", "SubelementHighlight")).map { "Draft_$it" }.toSet()

    fun run(s: AppState, id: String): Boolean {
        val n = id.removePrefix("Draft_")
        val wp = s.workPlane.json()
        when {
            n in groups -> s.choose(s.catalog.cmd(id)?.label ?: n, groups.getValue(n).map { "Draft_$it" to (s.catalog.cmd("Draft_$it")?.label ?: it) }) { c -> run(s, c) }
            n in makers -> { val (prompts, mn, mx) = makers.getValue(n)
                fun start(opts: Map<String, Any?>) = s.startDraft(DraftTool(n, s.catalog.cmd(id)?.label ?: n, prompts, mn, mx) { p ->
                    s.draftStep(n) { call("draft_make", mapOf("tool" to n, "pts" to pts(p), "wp" to wp, "opts" to opts)) } })
                when (n) {
                    "Polygon" -> s.askNumber("Number of sides", 6.0, emptyList()) { k -> start(mapOf("sides" to k.toInt().coerceAtLeast(3))) }
                    "Text" -> s.textPrompt = TextPrompt("Text (new line = \\n)", "Text") { t -> start(mapOf("text" to t.replace("\\n", "\n"))) }
                    "ShapeString" -> s.textPrompt = TextPrompt("Shape string text", "Text") { t -> s.askNumber("Height", 10.0) { h -> start(mapOf("text" to t, "size" to h)) } }
                    "Label" -> s.textPrompt = TextPrompt("Label text", "Label") { t ->
                        start(mapOf("text" to t, "target" to s.selection.firstOrNull()?.let { listOf(it.obj, it.sub) })) }
                    else -> start(emptyMap())
                }
            }
            n in modifiers -> { val sel = names(s)
                if (sel.isEmpty()) { s.message = "Select objects first"; return true }
                val (prompts, k) = modifiers.getValue(n); val sub = s.selection.firstOrNull { it.sub.isNotEmpty() }?.sub
                val tool = DraftTool(n, s.catalog.cmd(id)?.label ?: n, prompts, k, k, modifier = true) { p ->
                    val t = s.draft
                    fun go(opts: Map<String, Any?>) = s.draftStep(n) {
                        if (n == "Edit") run("""
o = doc.getObject(args['n']); g = o.getGlobalPlacement(); P = [g.multVec(p) for p in o.Points]
a = App.Vector(*args['p'][0]); i = min(range(len(P)), key=lambda k: (P[k] - a).Length)
P[i] = App.Vector(*args['p'][1]); o.Points = [g.inverse().multVec(p) for p in P]""", mapOf("n" to sel.first(), "p" to pts(p)))
                        else call("draft_modify", mapOf("tool" to n, "names" to sel, "pts" to pts(p), "wp" to wp, "opts" to opts)) }
                    val copy = mapOf("copy" to (t?.copy == true), "sub" to sub)
                    when (n) {
                        "Scale" -> s.askNumber("Scale factor", 2.0, emptyList()) { f -> go(copy + ("factor" to f)) }
                        else -> go(copy)
                    }
                }
                if (n == "Edit") s.startDraft(tool).also { s.draftSnaps.let { if ("Endpoint" !in it) it.add("Endpoint") } } else s.startDraft(tool)
            }
            n in selTools -> if (s.selection.isEmpty() && n != "UpdateShape2DView" && n != "Heal") s.message = "Select objects first"
                else s.draftStep(n) { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson())) }
            n == "OrthoArray" -> need(s) { s.askNumber("Copies along X", 2.0, emptyList()) { nx -> s.askNumber("Copies along Y", 2.0, emptyList()) { ny ->
                s.askNumber("Interval", 100.0) { iv -> s.draftStep("Array") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(),
                    "opts" to mapOf("n" to listOf(nx.toInt(), ny.toInt(), 1), "interval" to listOf(iv, iv, iv)))) } } } } }
            n == "PolarArray" -> need(s) { s.askNumber("Number of elements", 5.0, emptyList()) { k -> s.askNumber("Polar angle", 360.0, listOf("°")) { a ->
                s.draftStep("Polar array") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("n" to k.toInt(), "angle" to a))) } } } }
            n == "CircularArray" -> need(s) { s.askNumber("Radial distance", 100.0) { r -> s.askNumber("Tangential distance", 50.0) { t -> s.askNumber("Number of circular layers", 3.0, emptyList()) { c ->
                s.draftStep("Circular array") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("r" to r, "t" to t, "rings" to c.toInt()))) } } } } }
            n == "Fillet" -> need(s) { s.askNumber("Fillet radius", 1.0) { r -> s.choose("Fillet", listOf("f" to "Fillet", "c" to "Chamfer")) { c ->
                s.draftStep("Fillet") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("radius" to r, "chamfer" to (c == "c")))) } } } }
            n == "Hatch" -> need(s) { s.choose("Hatch pattern", listOf("Diagonal4", "Diagonal1", "Diagonal2", "Square", "Brick", "Steel", "Cross", "Diamond").map { it to it }) { pat ->
                s.askNumber("Pattern scale", 10.0, emptyList()) { k -> s.draftStep("Hatch") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("pattern" to pat, "scale" to k))) } } } }
            n == "Layer" -> s.textPrompt = TextPrompt("New layer", "Layer") { t -> s.draftStep("New layer") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("name" to t))) } }
            n == "AddToLayer" || n == "AutoGroup" || n == "LayerManager" -> s.eng({ call("draft_groups") as JSONObject }) { j ->
                val ls = j.getJSONArray("layers").let { a -> List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } } }
                when (n) {
                    "AddToLayer" -> need(s) { s.choose("Move to layer", ls) { l -> s.draftStep("Add to layer") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("layer" to l))) } } }
                    "AutoGroup" -> s.choose("Put new objects in", listOf("" to "None") + ls) { l -> s.autoGroup = l.ifEmpty { null } }
                    else -> s.choose("Layers (tap to show / hide)", ls) { l -> s.obj(l)?.let { o -> s.eng({ call("set_visibility", mapOf("names" to listOf(l), "visible" to !o.visible)) }) { s.refresh() } } }
                } }
            n == "AddToGroup" || n == "SelectGroup" -> s.eng({ call("draft_groups") as JSONObject }) { j ->
                val gs = j.getJSONArray("groups").let { a -> List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } } }
                if (n == "AddToGroup") need(s) { s.choose("Add to group", listOf("" to "Ungroup") + gs) { g ->
                    s.draftStep("Add to group") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("group" to g.ifEmpty { null }))) } } }
                else s.choose("Select group", gs) { g -> s.eng({ call("group_members", mapOf("name" to g)) as JSONArray }) { a ->
                    s.selection.clear(); for (i in 0 until a.length()) s.selection.add(SelRef(a.getString(i))); s.recordSelection(); s.viewport?.sync() } } }
            n == "SelectPlane" -> s.choose("Working plane", listOf("XY" to "Top (XY)", "XZ" to "Front (XZ)", "YZ" to "Side (YZ)", "face" to "Selected face", "view" to "Current view")) { p ->
                when (p) {
                    "XY" -> s.workPlane = WorkPlane.XY; "XZ" -> s.workPlane = WorkPlane.XZ; "YZ" -> s.workPlane = WorkPlane.YZ
                    "face" -> s.selection.firstOrNull { it.sub.startsWith("Face") }?.let { f -> s.eng({ call("face_plane", mapOf("obj" to f.obj, "sub" to f.sub)) as JSONObject }) { j ->
                        fun v(k: String) = j.getJSONArray(k).let { a -> DoubleArray(3) { a.getDouble(it) } }
                        s.workPlane = WorkPlane(v("pos"), v("u"), v("v"), v("w")); s.message = "Working plane: face" } } ?: run { s.message = "Select a planar face first" }
                    "view" -> s.viewport?.cam?.let { c -> val (r, u) = c.basis(); val e = c.eye()
                        val w = doubleArrayOf((e[0] - c.tx).toDouble(), (e[1] - c.ty).toDouble(), (e[2] - c.tz).toDouble()).let { a -> val l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); DoubleArray(3) { a[it] / l } }
                        s.workPlane = WorkPlane(doubleArrayOf(c.tx.toDouble(), c.ty.toDouble(), c.tz.toDouble()), DoubleArray(3) { r[it].toDouble() }, DoubleArray(3) { u[it].toDouble() }, w) }
                } }
            n == "WorkingPlaneProxy" -> s.draftStep("Working plane proxy") { call("draft_sel", mapOf("tool" to n, "opts" to wp)) }
            n == "ToggleConstructionMode" -> { s.constructionMode = !s.constructionMode; s.message = "Construction mode ${if (s.constructionMode) "on" else "off"}" }
            n == "ToggleGrid" -> { s.showGrid = !s.showGrid; s.viewport?.sync() }
            n == "ToggleDisplayMode" -> { s.drawStyle = if (s.drawStyle == "Wireframe") "As Is" else "Wireframe"; s.viewport?.sync() }
            n.startsWith("Snap_") -> n.removePrefix("Snap_").let { k -> if (k in s.draftSnaps) s.draftSnaps.remove(k) else s.draftSnaps.add(k)
                s.message = "$k snap ${if (k in s.draftSnaps) "on" else "off"}" }
            n == "ShowSnapBar" -> s.choose("Snaps (tap to toggle)", snaps.map { it to "${if (it in s.draftSnaps) "✓" else "  "}  $it" }) { k ->
                if (k in s.draftSnaps) s.draftSnaps.remove(k) else s.draftSnaps.add(k); run(s, id) }
            n == "Slope" -> need(s) { s.askNumber("Slope", 0.0, listOf("%")) { p -> s.draftStep("Slope") { call("draft_sel", mapOf("tool" to n, "sel" to s.selJson(), "opts" to mapOf("slope" to p))) } } }
            n == "Hyperlink" -> need(s) { s.eng({ run("result = [l for o in [doc.getObject(n) for n in args['n']] for t in getattr(o, 'Text', []) for l in t.split() if '://' in l]", mapOf("n" to names(s))) }) { r ->
                (r as? JSONArray)?.optString(0)?.takeIf { it.isNotEmpty() }?.let { u -> s.viewport?.context?.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    ?: run { s.message = "No links in the selected texts" } } }
            n == "SetStyle" || n == "ApplyStyle" || n == "AnnotationStyleEditor" -> StdCommands.run(s, "Std_SetAppearance")
            n == "SubelementHighlight" -> { s.selBoundingBox = !s.selBoundingBox; s.viewport?.sync() }
            else -> return false
        }
        return true
    }

    private fun need(s: AppState, f: () -> Unit) { if (s.selection.isEmpty()) s.message = "Select objects first" else f() }
}
