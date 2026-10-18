package app.mobilecad.freecad

import org.json.JSONArray
import org.json.JSONObject

/**
 * TechDraw workbench (Drawing tab): src/Mod/TechDraw/Gui/Command*.cpp + TechDrawTools.  Page geometry is picked on the
 * page canvas (SelRef(view, "Edge3" / "Vertex1" / "Face0"), whole views / dimensions as SelRef(name)); page points come
 * from the DraftTool picker in page millimetres and are converted to the view's canonical coordinates
 * ((page - view position) / scale), which is what TechDraw stores cosmetic geometry in.
 */
object TechDrawCommands {
    private val dims = mapOf("LengthDimension" to "Length", "HorizontalDimension" to "Horizontal", "VerticalDimension" to "Vertical",
        "RadiusDimension" to "Radius", "DiameterDimension" to "Diameter", "AngleDimension" to "Angle", "3PtAngleDimension" to "3PtAngle",
        "AreaDimension" to "Area", "HorizontalExtentDimension" to "HorizontalExtent", "VerticalExtentDimension" to "VerticalExtent",
        "AxoLengthDimension" to "Length", "ExtensionCreateLengthArc" to "ArcLength")
    private val exts = setOf("CircleCenterLines", "HoleCircle", "ThreadHoleSide", "ThreadBoltSide", "ThreadHoleBottom", "ThreadBoltBottom",
        "ExtendLine", "ShortenLine", "VertexAtIntersection", "CreateHorizChainDimension", "CreateVertChainDimension", "CreateObliqueChainDimension",
        "CreateHorizCoordDimension", "CreateVertCoordDimension", "CreateObliqueCoordDimension", "CascadeHorizDimension", "CascadeVertDimension",
        "CascadeObliqueDimension", "PosHorizChainDimension", "PosVertChainDimension", "PosObliqueChainDimension", "CreateHorizChamferDimension",
        "CreateVertChamferDimension", "AreaAnnotation", "ArcLengthAnnotation", "LockUnlockView", "PositionSectionView")
    /** Dropdown groups: id -> members. */
    private val groups = mapOf(
        "SectionGroup" to listOf("SectionView", "ComplexSection"), "ExtentGroup" to listOf("HorizontalExtentDimension", "VerticalExtentDimension"),
        "CenterLineGroup" to listOf("FaceCenterLine", "2LineCenterLine", "2PointCenterLine"), "CosmeticVertexGroup" to listOf("CosmeticVertex", "Midpoints", "Quadrants"),
        "StackGroup" to listOf("StackTop", "StackBottom", "StackUp", "StackDown"),
        "CommandVertexCreationGroup" to listOf("ExtensionVertexAtIntersection", "CommandAddOffsetVertex"),
        "ExtensionCascadeDimensionGroup" to listOf("ExtensionCascadeHorizDimension", "ExtensionCascadeVertDimension", "ExtensionCascadeObliqueDimension"),
        "ExtensionChamferDimensionGroup" to listOf("ExtensionCreateHorizChamferDimension", "ExtensionCreateVertChamferDimension"),
        "ExtensionCircleCenterLinesGroup" to listOf("ExtensionCircleCenterLines", "ExtensionHoleCircle"),
        "ExtensionCreateChainDimensionGroup" to listOf("ExtensionCreateHorizChainDimension", "ExtensionCreateVertChainDimension", "ExtensionCreateObliqueChainDimension"),
        "ExtensionCreateCoordDimensionGroup" to listOf("ExtensionCreateHorizCoordDimension", "ExtensionCreateVertCoordDimension", "ExtensionCreateObliqueCoordDimension"),
        "ExtensionDrawCirclesGroup" to listOf("CosmeticCircle", "ExtensionDrawCosmCircle", "ExtensionDrawCosmCircle3Points", "ExtensionDrawCosmArc"),
        "ExtensionExtendShortenLineGroup" to listOf("ExtensionExtendLine", "ExtensionShortenLine"),
        "ExtensionIncreaseDecreaseGroup" to listOf("ExtensionIncreaseDecimal", "ExtensionDecreaseDecimal"),
        "ExtensionInsertPrefixGroup" to listOf("ExtensionInsertDiameter", "ExtensionInsertSquare", "ExtensionInsertRepetition", "ExtensionRemovePrefixChar"),
        "ExtensionLinePPGroup" to listOf("ExtensionLineParallel", "ExtensionLinePerpendicular"),
        "ExtensionPosChainDimensionGroup" to listOf("ExtensionPosHorizChainDimension", "ExtensionPosVertChainDimension", "ExtensionPosObliqueChainDimension"),
        "ExtensionThreadsGroup" to listOf("ExtensionThreadHoleSide", "ExtensionThreadBoltSide", "ExtensionThreadHoleBottom", "ExtensionThreadBoltBottom"),
    )
    private val others = setOf("PageDefault", "PageTemplate", "RedrawPage", "PrintAll", "ExportPageSVG", "ExportPagePDF", "ExportPageDXF",
        "ToggleFrame", "ToggleGrid", "View", "ActiveView", "ProjectionGroup", "SectionView", "ComplexSection", "DetailView", "BrokenView",
        "ArchView", "DraftView", "SpreadsheetView", "Symbol", "Image", "ClipGroup", "ClipGroupAdd", "ClipGroupRemove", "ProjectShape",
        "MoveView", "ShareView", "Dimension", "DimensionRepair", "HoleShaftFit", "Annotation", "RichTextAnnotation", "Balloon", "LeaderLine",
        "WeldSymbol", "SurfaceFinishSymbols", "CosmeticVertex", "Midpoints", "Quadrants", "FaceCenterLine", "2LineCenterLine", "2PointCenterLine",
        "2PointCosmeticLine", "CosmeticCircle", "CosmeticEraser", "DecorateLine", "ShowAll", "CommandAddOffsetVertex", "Hatch", "GeometricHatch",
        "StackTop", "StackBottom", "StackUp", "StackDown", "FillTemplateFields", "ExtensionDrawCosmArc", "ExtensionDrawCosmCircle",
        "ExtensionDrawCosmCircle3Points", "ExtensionLineParallel", "ExtensionLinePerpendicular", "ExtensionIncreaseDecimal",
        "ExtensionDecreaseDecimal", "ExtensionInsertDiameter", "ExtensionInsertSquare", "ExtensionInsertRepetition", "ExtensionRemovePrefixChar",
        "ExtensionCustomizeFormat", "ExtensionSelectLineAttributes", "ExtensionChangeLineAttributes", "AlignVertexesHorizontally", "AlignVertexesVertically")

    val ids: Set<String> = (dims.keys + groups.keys + others + exts.map { "Extension$it" }).map { "TechDraw_$it" }.toSet()

    // ---------------------------------------------------------------- helpers
    private fun page(s: AppState, f: (String) -> Unit) = s.page?.let(f) ?: run { s.message = "Create a page first (New Page)" }
    private fun viewSel(s: AppState) = s.selection.firstOrNull()?.obj
    private fun subs(s: AppState) = s.selection.filter { it.sub.isNotEmpty() }.map { it.sub }
    private fun needView(s: AppState, f: (String, List<String>) -> Unit) = viewSel(s)?.let { v -> f(v, s.selection.filter { it.obj == v && it.sub.isNotEmpty() }.map { it.sub }) }
        ?: run { s.message = "Select a view (or its edges / vertexes) on the page first" }
    private fun viewInfo(s: AppState, name: String): JSONObject? = s.pageRender?.optJSONArray("views")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.getString("name") == name } }
    /** Page point -> the view's canonical (unscaled) coordinates. */
    private fun canon(s: AppState, view: String, p: DoubleArray): List<Double> { val v = viewInfo(s, view); val sc = v?.optDouble("scale", 1.0) ?: 1.0
        return listOf((p[0] - (v?.optDouble("x") ?: 0.0)) / sc, (p[1] - (v?.optDouble("y") ?: 0.0)) / sc) }
    // drawable parts: not the origin's axes / planes (App::Line, App::Plane ...) that every Part / Assembly carries
    private fun shapes(s: AppState) = s.objects.filter { it.hasShape && it.visible && !it.type.startsWith("TechDraw") &&
        it.type !in setOf("App::Origin", "App::Line", "App::Plane", "App::Point", "App::LocalCoordinateSystem") }.map { it.name to it.label }
    private fun pick(s: AppState, label: String, prompts: List<String>, n: Int, min: Int = if (n < 0) 1 else n, onDone: (List<DoubleArray>) -> Unit) =
        s.startDraft(DraftTool("TD", label, prompts, min, n, onDone = onDone))
    private fun td(s: AppState, label: String, op: String, args: Map<String, Any?>) = s.step(label, { call(op, args) })
    private fun ext(s: AppState, kind: String, opts: Map<String, Any?> = emptyMap()) = needView(s) { v, sb ->
        td(s, kind, "td_ext", mapOf("kind" to kind, "view" to v, "subs" to sb, "names" to s.selection.map { it.obj }.distinct(), "opts" to opts)) }
    private fun dimNames(s: AppState) = s.selection.filter { it.sub.isEmpty() }.map { it.obj }.filter { s.obj(it)?.type?.contains("Dimension") == true }

    fun run(s: AppState, id: String): Boolean {
        val n = id.removePrefix("TechDraw_")
        val label = s.catalog.cmd(id)?.label ?: n
        when {
            n in groups -> s.choose(label, groups.getValue(n).map { "TechDraw_$it" to (s.catalog.cmd("TechDraw_$it")?.label ?: it) }) { c -> s.run(c) }
            n in dims -> needView(s) { v, sb -> if (sb.isEmpty() && dims.getValue(n) != "Area") s.message = "Select edges or vertexes of a view" else
                s.step(label, { call("td_dimension", mapOf("kind" to dims.getValue(n), "refs" to listOf(listOf(v, sb)))) }) }
            n.startsWith("Extension") && n.removePrefix("Extension") in exts -> ext(s, n.removePrefix("Extension"))

            // ---- pages
            n == "PageDefault" -> s.eng({ begin("New page"); val p = call("td_new_page") as String; commit(); p }) { p -> s.page = p; s.refresh() }
            n == "PageTemplate" -> s.eng({ call("td_templates") as JSONArray }) { a -> s.choose("Template", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { t ->
                s.eng({ begin("New page"); val p = call("td_new_page", mapOf("template" to t)) as String; commit(); p }) { p -> s.page = p; s.refresh() } } }
            n == "RedrawPage" -> page(s) { p -> td(s, "Redraw page", "td_redraw", mapOf("page" to p)) }
            n == "PrintAll" || n == "ExportPagePDF" -> page(s) { s.fileRequest = AppState.FileRequest("pagepdf", listOf(it), "${s.docName}.pdf") }
            n == "ExportPageSVG" -> page(s) { s.fileRequest = AppState.FileRequest("pagesvg", listOf(it), "${s.docName}.svg") }
            n == "ExportPageDXF" -> page(s) { s.fileRequest = AppState.FileRequest("pagedxf", listOf(it), "${s.docName}.dxf") }
            n == "ToggleFrame" -> s.pageView?.let { it.showFrames = !it.showFrames; it.invalidate() }
            n == "ToggleGrid" -> s.pageView?.let { it.showGrid = !it.showGrid; it.invalidate() }
            n == "FillTemplateFields" -> page(s) { p -> s.eng({ begin("Fill template fields"); val r = call("td_fill_template_fields", mapOf("page" to p)) as JSONObject; commit(); r }) { j ->
                s.refresh(); s.choose("Template fields (tap to edit)", j.keys().asSequence().map { k -> k to "$k: ${j.optString(k)}" }.toList()) { k ->
                    s.textPrompt = TextPrompt(k, j.optString(k)) { t -> td(s, "Edit template", "td_set_template_field", mapOf("page" to p, "key" to k, "value" to t)) } } } }

            // ---- views
            n == "View" -> page(s) { p -> s.chooseMany("Parts in the view", shapes(s)) { src -> s.choose("Direction", listOf("Front", "Top", "Right", "Rear", "Bottom", "Left", "Isometric").map { it to it }) { dir ->
                td(s, "New view", "td_view", mapOf("page" to p, "sources" to src, "direction" to dir)) } } }
            n == "ProjectionGroup" -> page(s) { p -> s.chooseMany("Parts in the projection group", shapes(s)) { src ->
                s.chooseMany("Projections", listOf("Front", "Top", "Right", "Left", "Rear", "Bottom", "FrontTopLeft", "FrontTopRight", "FrontBottomLeft", "FrontBottomRight").map { it to it }, setOf("Front", "Top", "Right")) { pv ->
                    td(s, "Projection group", "td_projection_group", mapOf("page" to p, "sources" to src, "views" to pv)) } } }
            n == "ActiveView" -> page(s) { p -> s.viewport?.screenshot { bmp ->
                val f = java.io.File(s.viewport!!.context.filesDir, "activeview_${System.currentTimeMillis()}.png"); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                td(s, "Active view", "td_simple", mapOf("page" to p, "kind" to "Image", "props" to mapOf("ImageFile" to f.absolutePath, "Width" to 100.0, "Height" to 100.0 * bmp.height / bmp.width))) } }
            n == "SectionView" -> needView(s) { v, _ -> s.choose("Section direction", listOf("Right", "Left", "Up", "Down").map { it to it }) { d -> td(s, "Section view", "td_section", mapOf("base" to v, "edge_or_dir" to d)) } }
            n == "ComplexSection" -> needView(s) { v, _ -> s.choose("Cutting profile (sketch or wire)", s.objects.filter { it.type.contains("Sketch") || it.type.contains("Part2DObject") }.map { it.name to it.label }) { pr ->
                td(s, "Complex section", "td_complex_section", mapOf("base" to v, "profile" to pr)) } }
            n == "DetailView" -> needView(s) { v, _ -> pick(s, label, listOf("Detail centre on the base view"), 1) { p ->
                s.askNumber("Detail radius", 10.0) { r -> td(s, "Detail view", "td_detail", mapOf("base" to v, "anchor" to canon(s, v, p[0]), "radius" to r)) } } }
            n == "BrokenView" -> needView(s) { v, _ -> td(s, "Broken view", "td_broken_view", mapOf("base" to v)) }
            n == "ArchView" || n == "DraftView" || n == "SpreadsheetView" -> page(s) { p ->
                val kind = mapOf("ArchView" to "Arch", "DraftView" to "Draft", "SpreadsheetView" to "Spreadsheet").getValue(n)
                val opts = s.objects.filter { o -> when (kind) { "Spreadsheet" -> o.type == "Spreadsheet::Sheet"; "Arch" -> o.label.contains("Section") || o.type.contains("SectionPlane"); else -> o.hasShape } }.map { it.name to it.label }
                s.choose("Source", opts) { src -> td(s, label, "td_simple", mapOf("page" to p, "kind" to kind, "props" to mapOf("Source" to src))) } }
            n == "Symbol" || n == "SurfaceFinishSymbols" -> page(s) { p -> s.eng({ run("""
import os
base = App.getResourceDir() + 'Mod/TechDraw/Symbols/'
sub = args['sub']
result = [[os.path.join(dp, f), os.path.relpath(os.path.join(dp, f), base)[:-4]] for dp, _, fs in os.walk(base + sub) for f in sorted(fs) if f.endswith('.svg')]""",
                mapOf("sub" to if (n == "Symbol") "" else "surface-roughness-symbols")) as JSONArray }) { a ->
                s.choose(label, List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { f -> td(s, label, "td_simple", mapOf("page" to p, "kind" to "Symbol", "props" to mapOf("Symbol" to f))) } } }
            n == "Image" -> page(s) { s.fileRequest = AppState.FileRequest("tdimage", listOf(it)) }
            n == "ClipGroup" -> page(s) { p -> td(s, "Clip group", "td_simple", mapOf("page" to p, "kind" to "Clip")) }
            n == "ClipGroupAdd" || n == "ClipGroupRemove" -> needView(s) { v, _ -> s.choose("Clip group", s.objects.filter { it.type == "TechDraw::DrawViewClip" }.map { it.name to it.label }) { c ->
                td(s, label, "td_clip_add", mapOf("view" to v, "clip" to c, "remove" to (n == "ClipGroupRemove"))) } }
            n == "ProjectShape" -> s.chooseMany("Shapes to project", shapes(s)) { src -> s.choose("Direction", listOf("0,0,1" to "Top (Z)", "0,-1,0" to "Front (-Y)", "1,0,0" to "Right (X)")) { d ->
                td(s, "Project shape", "td_project_shape", mapOf("sources" to src, "direction" to d.split(",").map { it.toDouble() })) } }
            n == "MoveView" || n == "ShareView" -> needView(s) { v, _ -> s.eng({ call("td_pages") as JSONArray }) { a ->
                s.choose(if (n == "MoveView") "Move to page" else "Share with page", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }.filter { it.first != s.page }) { dst ->
                    s.step(label, { if (n == "MoveView") call("td_remove_from_page", mapOf("view" to v)); call("td_move_view", mapOf("view" to v, "page" to dst)) }) } } }

            // ---- dimensions
            n == "Dimension" -> needView(s) { v, sb ->            // TechDraw_Dimension: the type follows the selection
                val e = sb.count { it.startsWith("Edge") }; val vx = sb.count { it.startsWith("Vertex") }; val f = sb.count { it.startsWith("Face") }
                val k = when { f >= 1 -> "Area"; e == 2 -> "Angle"; vx == 3 -> "3PtAngle"; else -> null }
                if (k != null) s.step(label, { call("td_dimension", mapOf("kind" to k, "refs" to listOf(listOf(v, sb)))) })
                else s.eng({ run("e = doc.getObject(args['v']).getEdgeBySelection(args['s']) if args['s'].startswith('Edge') else None\nresult = bool(e is not None and hasattr(e.Curve, 'Radius'))", mapOf("v" to v, "s" to (sb.firstOrNull() ?: ""))) }) { circ ->
                    if (circ == true) s.choose("Dimension", listOf("Diameter" to "Diameter", "Radius" to "Radius")) { k2 -> s.step(label, { call("td_dimension", mapOf("kind" to k2, "refs" to listOf(listOf(v, sb)))) }) }
                    else s.choose("Dimension", listOf("Length" to "Length", "Horizontal" to "Horizontal", "Vertical" to "Vertical")) { k2 -> s.step(label, { call("td_dimension", mapOf("kind" to k2, "refs" to listOf(listOf(v, sb)))) }) } } }
            n == "DimensionRepair" -> { val d = dimNames(s).firstOrNull(); val geo = s.selection.filter { it.sub.isNotEmpty() }
                if (d == null || geo.isEmpty()) s.message = "Select the dimension and its new edges / vertexes" else
                    s.step(label, { run("o = doc.getObject(args['d']); o.References2D = [(doc.getObject(v), s) for v, s in args['r']]", mapOf("d" to d, "r" to geo.map { listOf(it.obj, it.sub) })) }) }
            n == "HoleShaftFit" -> dimNames(s).takeIf { it.isNotEmpty() }?.let { ds -> s.choose("Fit (TechDrawTools/TaskHoleShaftFit)", listOf("H7/g6", "H7/h6", "H7/k6", "H7/n6", "H7/p6", "H8/f7", "H11/c11", "h6", "H7").map { it to it }) { fit ->
                s.step(label, { run("for n in args['d']:\n    o = doc.getObject(n); o.FormatSpec = o.FormatSpec.split(' ')[0] + ' ' + args['f']", mapOf("d" to ds, "f" to fit)) }) } } ?: run { s.message = "Select a dimension" }
            n == "ExtensionIncreaseDecimal" || n == "ExtensionDecreaseDecimal" -> dimNames(s).takeIf { it.isNotEmpty() }?.let { ds ->
                td(s, label, "td_set_dim_format", mapOf("names" to ds, "delta_decimals" to if (n.contains("Increase")) 1 else -1)) } ?: run { s.message = "Select dimensions" }
            n in setOf("ExtensionInsertDiameter", "ExtensionInsertSquare", "ExtensionInsertRepetition") -> dimNames(s).takeIf { it.isNotEmpty() }?.let { ds ->
                val pre = when (n) { "ExtensionInsertDiameter" -> "⌀"; "ExtensionInsertSquare" -> "□"; else -> "" }
                if (n == "ExtensionInsertRepetition") s.askNumber("Number of repetitions", 2.0, emptyList()) { k -> td(s, label, "td_set_dim_format", mapOf("names" to ds, "prefix" to "${k.toInt()}× ")) }
                else td(s, label, "td_set_dim_format", mapOf("names" to ds, "prefix" to pre)) } ?: run { s.message = "Select dimensions" }
            n == "ExtensionRemovePrefixChar" -> dimNames(s).takeIf { it.isNotEmpty() }?.let { ds -> td(s, label, "td_set_dim_format", mapOf("names" to ds, "remove_prefix" to true)) } ?: run { s.message = "Select dimensions" }
            n == "ExtensionCustomizeFormat" -> dimNames(s).firstOrNull()?.let { d -> s.textPrompt = TextPrompt("Format (printf, e.g. ⌀%.2f H7)", "%.2f") { f -> td(s, label, "td_set_dim_format", mapOf("names" to listOf(d), "fmt" to f)) } }
                ?: run { s.message = "Select a dimension" }

            // ---- annotations
            n == "Annotation" || n == "RichTextAnnotation" -> page(s) { p -> s.textPrompt = TextPrompt("Text (new line = \\n)", "Text") { t -> pick(s, label, listOf("Text position"), 1) { pt ->
                if (n == "Annotation") td(s, label, "td_simple", mapOf("page" to p, "kind" to "Annotation", "props" to mapOf("Text" to t.split("\\n")), "x" to pt[0][0], "y" to pt[0][1]))
                else td(s, label, "td_simple", mapOf("page" to p, "kind" to "RichAnno", "props" to mapOf("AnnoText" to "<html><body><p>${t.replace("\\n", "</p><p>")}</p></body></html>"), "x" to pt[0][0], "y" to pt[0][1])) } } }
            n == "Balloon" -> needView(s) { v, _ -> pick(s, label, listOf("Point the balloon refers to", "Balloon position"), 2) { p ->
                val vi = viewInfo(s, v); val vx = vi?.optDouble("x") ?: 0.0; val vy = vi?.optDouble("y") ?: 0.0
                val num = s.objects.count { it.type == "TechDraw::DrawViewBalloon" } + 1
                td(s, label, "td_balloon", mapOf("view" to v, "origin" to listOf(p[0][0] - vx, p[0][1] - vy), "x" to p[1][0] - vx, "y" to p[1][1] - vy, "text" to num.toString())) } }
            n == "LeaderLine" -> needView(s) { v, _ -> pick(s, label, listOf("Arrow point", "Next point (Finish to end)"), -1, 2) { p -> td(s, label, "td_leader", mapOf("view" to v, "points" to p.map { listOf(it[0], it[1]) })) } }
            n == "WeldSymbol" -> viewSel(s)?.takeIf { s.obj(it)?.type == "TechDraw::DrawLeaderLine" }?.let { l ->
                s.choose("Weld symbol", listOf("" to "Plain", "fillet" to "Fillet", "square" to "Square", "v" to "V groove", "bevel" to "Bevel")) { k ->
                    td(s, label, "td_weld", mapOf("leader" to l, "props" to mapOf("AllAround" to false, "FieldWeld" to false))) } } ?: run { s.message = "Select a leader line" }

            // ---- cosmetic geometry
            n == "CosmeticVertex" -> needView(s) { v, _ -> pick(s, label, listOf("Vertex position (Finish when done)"), -1) { p ->
                td(s, label, "td_cosmetic", mapOf("kind" to "Vertex", "view" to v, "pts" to p.map { canon(s, v, it) })) } }
            n == "Midpoints" || n == "Quadrants" || n == "FaceCenterLine" || n == "2LineCenterLine" || n == "2PointCenterLine" || n == "CosmeticEraser" || n == "ShowAll" ->
                needView(s) { v, sb -> td(s, label, "td_cosmetic", mapOf("kind" to mapOf("CosmeticEraser" to "Eraser").getOrDefault(n, n), "view" to v, "refs" to sb)) }
            n == "2PointCosmeticLine" -> needView(s) { v, sb -> if (sb.count { it.startsWith("Vertex") } >= 2) td(s, label, "td_cosmetic", mapOf("kind" to "Line", "view" to v, "refs" to sb))
                else pick(s, label, listOf("Line start", "Line end"), 2) { p -> td(s, label, "td_cosmetic", mapOf("kind" to "Line", "view" to v, "pts" to p.map { canon(s, v, it) })) } }
            n == "CosmeticCircle" || n == "ExtensionDrawCosmCircle" -> needView(s) { v, _ -> pick(s, label, listOf("Centre", "Point on the circle"), 2) { p ->
                td(s, label, "td_cosmetic", mapOf("kind" to "Circle", "view" to v, "pts" to p.map { canon(s, v, it) })) } }
            n == "ExtensionDrawCosmCircle3Points" -> needView(s) { v, _ -> pick(s, label, listOf("First point", "Second point", "Third point"), 3) { p ->
                td(s, label, "td_cosmetic", mapOf("kind" to "Circle3", "view" to v, "pts" to p.map { canon(s, v, it) })) } }
            n == "ExtensionDrawCosmArc" -> needView(s) { v, _ -> pick(s, label, listOf("Centre", "Start point (radius)", "End point"), 3) { p ->
                td(s, label, "td_cosmetic", mapOf("kind" to "Arc", "view" to v, "pts" to p.map { canon(s, v, it) })) } }
            n == "ExtensionLineParallel" || n == "ExtensionLinePerpendicular" -> needView(s) { v, sb ->
                td(s, label, "td_cosmetic", mapOf("kind" to if (n.endsWith("Parallel")) "Parallel" else "Perpendicular", "view" to v, "refs" to sb)) }
            n == "DecorateLine" || n == "ExtensionChangeLineAttributes" -> needView(s) { v, sb -> s.choose("Line style", listOf("1" to "Continuous", "2" to "Dashed", "3" to "Dotted", "4" to "Dash-dot", "5" to "Dash-dot-dot")) { st ->
                s.choose("Line weight", listOf("0.18" to "Thin 0.18", "0.35" to "Medium 0.35", "0.5" to "Thick 0.5", "0.7" to "Extra thick 0.7")) { w ->
                    if (n == "DecorateLine") td(s, label, "td_cosmetic", mapOf("kind" to "Decorate", "view" to v, "refs" to sb, "opts" to mapOf("style" to st.toInt(), "weight" to w.toDouble())))
                    else td(s, label, "td_ext", mapOf("kind" to "ChangeLineAttributes", "view" to v, "subs" to sb, "opts" to mapOf("style" to st.toInt(), "weight" to w.toDouble()))) } } }
            n == "ExtensionSelectLineAttributes" -> s.askNumber("Cascade spacing", 7.0) { cs -> s.askNumber("Line stretch", 2.0) { ls ->
                s.eng({ call("td_ext", mapOf("kind" to "SelectLineAttributes", "view" to null, "opts" to mapOf("cascade" to cs, "stretch" to ls))) }) { s.message = "Line attributes set" } } }
            n == "CommandAddOffsetVertex" -> needView(s) { v, sb -> if (sb.none { it.startsWith("Vertex") }) s.message = "Select a vertex" else
                s.askNumber("Offset X", 0.0) { dx -> s.askNumber("Offset Y", 0.0) { dy -> td(s, label, "td_ext", mapOf("kind" to "AddOffsetVertex", "view" to v, "subs" to sb, "opts" to mapOf("dx" to dx, "dy" to dy))) } } }
            n == "AlignVertexesHorizontally" || n == "AlignVertexesVertically" -> needView(s) { v, sb ->   // TechDrawTools TDToolsMovers: rotate the view so the two vertexes line up
                s.step(label, { run("""
import math
o = doc.getObject(args['v']); vs = [o.getVertexBySelection(x).Point for x in args['s'] if x.startswith('Vertex')]
if len(vs) < 2:
    e = o.getEdgeBySelection([x for x in args['s'] if x.startswith('Edge')][0]); vs = [e.Vertexes[0].Point, e.Vertexes[-1].Point]
a = math.degrees(math.atan2(vs[1].y - vs[0].y, vs[1].x - vs[0].x))
target = 0.0 if args['h'] else 90.0
o.Rotation = o.Rotation.Value + (target - a)""", mapOf("v" to v, "s" to sb, "h" to (n == "AlignVertexesHorizontally"))) }) }

            // ---- hatching
            n == "Hatch" || n == "GeometricHatch" -> needView(s) { v, sb -> if (sb.none { it.startsWith("Face") }) s.message = "Tap inside faces of a view to select them" else
                if (n == "Hatch") td(s, label, "td_hatch", mapOf("view" to v, "faces" to sb.filter { it.startsWith("Face") }, "kind" to "Svg"))
                else s.choose("Pattern", listOf("Diamond", "Diagonal1", "Diagonal2", "Diagonal3", "Diagonal4", "Square", "Brick", "Steel", "Cross", "Hatch45L", "Hatch45R").map { it to it }) { pat ->
                    td(s, label, "td_hatch", mapOf("view" to v, "faces" to sb.filter { it.startsWith("Face") }, "kind" to "Geom", "name" to pat)) } }

            // ---- stacking
            n in setOf("StackTop", "StackBottom", "StackUp", "StackDown") -> needView(s) { v, _ -> td(s, label, "td_stack", mapOf("names" to listOf(v), "how" to n.removePrefix("Stack"))) }
            else -> return false
        }
        return true
    }
}
