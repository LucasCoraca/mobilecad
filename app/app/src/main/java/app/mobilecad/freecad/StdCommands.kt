package app.mobilecad.freecad

import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * FreeCAD's Std_* commands (src/Gui/Command*.cpp) — the ones shared by every workbench.  Document operations go through
 * mobilecad.std; view and selection commands act on the 3D view and the app's selection like the desktop does on Coin.
 * Desktop window management (MDI windows, dock overlays, toolbars, Qt test commands) has no counterpart on a phone and is
 * listed in [notApplicable].
 */
object StdCommands {
    private val views = mapOf("Std_ViewTop" to "Top", "Std_ViewBottom" to "Bottom", "Std_ViewFront" to "Front", "Std_ViewRear" to "Rear",
        "Std_ViewRight" to "Right", "Std_ViewLeft" to "Left", "Std_ViewIsometric" to "Isometric", "Std_ViewDimetric" to "Dimetric",
        "Std_ViewTrimetric" to "Trimetric", "Std_ViewHome" to "Isometric")
    private val drawStyles = listOf("As Is", "Points", "Wireframe", "Hidden Line", "No Shading", "Shaded", "Flat Lines")
    private val urls = mapOf("Std_OnlineHelp" to "https://wiki.freecad.org/Online_Help_Toc", "Std_OnlineHelpWebsite" to "https://wiki.freecad.org",
        "Std_FreeCADWebsite" to "https://www.freecad.org", "Std_FreeCADForum" to "https://forum.freecad.org",
        "Std_FreeCADUserHub" to "https://wiki.freecad.org/User_hub", "Std_FreeCADDonation" to "https://www.freecad.org/donate",
        "Std_DevHandbook" to "https://freecad.github.io/DevelopersHandbook/")   // no Std_ReportBug: FreeCAD's tracker is not for this app

    val ids: Set<String> = views.keys + urls.keys + setOf(
        "Std_ViewFitAll", "Std_ViewFitSelection", "Std_ViewRotateLeft", "Std_ViewRotateRight", "Std_ViewZoomIn", "Std_ViewZoomOut",
        "Std_ViewBoxZoom", "Std_OrthographicCamera", "Std_PerspectiveCamera", "Std_AxisCross", "Std_DrawStyle", "Std_ToggleClipPlane",
        "FEM_ClippingPlaneAdd", "FEM_ClippingPlaneRemoveAll", "Std_ToggleFreeze", "Start_Start",
        "Material_Edit", "Materials_InspectMaterial", "Materials_InspectAppearance", "Part_ColorPerFace",
        "Std_TextureMapping", "Std_ViewLoadImage", "Part_ImportCurveNet",
        "Std_ViewScreenShot", "Std_StoreWorkingView", "Std_RecallWorkingView", "Std_ViewSaveCamera", "Std_ViewRestoreCamera",
        "Std_FreezeViews", "Std_DemoMode", "Std_ViewFullscreen", "Std_MainFullscreen",
        "Std_Open", "Std_Save", "Std_SaveAs", "Std_SaveCopy", "Std_SaveAll", "Std_Revert", "Std_MergeProjects", "Std_ProjectInfo",
        "Std_RecentFiles", "Std_Print", "Std_PrintPdf", "Std_PrintPreview", "Std_Quit", "Std_ProjectUtil",
        "Std_Recompute", "Std_Refresh", "Std_ToggleSkipRecompute", "Std_ToggleSuppress",
        "Std_Copy", "Std_Cut", "Std_Paste", "Std_DuplicateSelection", "Std_SelectAll", "Std_SelectVisibleObjects", "Std_SelBack",
        "Std_SelForward", "Std_SelBoundingBox", "Std_ClarifySelection", "Std_BoxSelection", "Std_BoxElementSelection",
        "Std_ShowSelection", "Std_HideSelection", "Std_ShowObjects", "Std_HideObjects", "Std_ToggleObjects", "Std_ToggleSelectability",
        "Std_ToggleTransparency", "Std_RandomColor", "Std_SetAppearance", "Std_SetMaterial",
        "Std_Placement", "Std_Transform", "Std_TransformManip", "Std_Edit", "Std_Properties",
        "Std_LinkMakeGroup", "Std_LinkMakeRelative", "Std_LinkReplace", "Std_LinkUnlink", "Std_LinkImport", "Std_LinkImportAll",
        "Std_LinkSelectLinked", "Std_LinkSelectLinkedFinal", "Std_LinkSelectAllLinks", "Std_TreeSelectAllInstances", "Std_TreeSelection",
        "Std_MassProperties", "Std_Measure", "Std_UnitsCalculator", "Std_DependencyGraph", "Std_ExportDependencyGraph",
        "Std_CommandLine", "Std_SendToPythonConsole", "Std_DlgMacroExecute", "Std_DlgMacroExecuteDirect", "Std_DlgPreferences",
        "Std_About", "Std_AboutQt", "Std_Workbench", "Std_AlignToSelection", "Std_Alignment", "Std_TreeCollapse", "Std_TreeExpand",
        "Std_PythonHelp", "Std_AnnotationLabel")

    /** Desktop-window commands without a phone counterpart (reported separately in research/PARITY.md). */
    val notApplicable = setOf("Materials_MigrateToExternal",   // only with the BUILD_MATERIAL_EXTERNAL database option
        "Part_PickCurveNet",                                     // its activated is empty in FreeCAD
        "PartDesign_WizardShaftCallBack",                        // internal callback of the Shaft wizard's table, not a tool
        "TemplatePyMod_Cmd5",                                    // opens pivy's external SoGui viewer window: a desktop example
        "Std_ActivateNextWindow", "Std_ActivatePrevWindow", "Std_CascadeWindows", "Std_TileWindows",
        "Std_CloseActiveWindow", "Std_CloseAllWindows", "Std_Windows", "Std_WindowsMenu", "Std_MDITest1", "Std_MDITest2", "Std_MDITest3",
        "Std_Test1", "Std_Test2", "Std_Test3", "Std_Test4", "Std_Test5", "Std_Test6", "Std_TestCmdFuncs", "Std_TestConsoleOutput",
        "Std_TestProgress1", "Std_TestProgress2", "Std_TestProgress3", "Std_TestProgress4", "Std_TestProgress5", "Std_TestQM",
        "Std_TestReloadQM", "Std_ViewExample1", "Std_ViewExample2", "Std_ViewExample3", "Std_ViewIvIssueCamPos", "Std_DockOverlayAll",
        "Std_DockOverlayMouseTransparent", "Std_DockOverlayToggle", "Std_DockOverlayToggleBottom", "Std_DockOverlayToggleLeft",
        "Std_DockOverlayToggleRight", "Std_DockOverlayToggleTop", "Std_DockOverlayToggleTransparent", "Std_DockOverlayTransparentAll",
        "Std_DockViewMenu", "Std_ToolBarMenu", "Std_ToggleToolBarLock", "Std_ReloadStyleSheet", "Std_RestartInSafeMode",
        "Std_MacroAttachDebugger", "Std_SceneInspector", "Std_DlgCustomize", "Std_ViewDock", "Std_ViewUndock", "Std_ViewDockUndockFullscreen",
        "Std_ViewStatusBar", "Std_ToggleBottomPanels", "Std_UserInterface", "Std_ViewCreate", "Std_WhatsThis", "Std_TreeMultiDocument",
        "Std_TreeSingleDocument", "Std_TreePreSelection", "Std_TreeRecordSelection", "Std_TreeSyncPlacement", "Std_TreeSyncSelection",
        "Std_TreeSyncView", "Std_TreeDrag", "Std_OpenGroup", "Std_UserEditMode", "Std_ToggleNavigation", "Std_DlgParameter",
        "Std_OpenMacrosFolder", "Std_RecentMacros", "Std_DlgMacroRecord", "Std_TreeCollapseDocument")

    private fun names(s: AppState) = s.selection.map { it.obj }.distinct()
    private fun needSel(s: AppState, f: (List<String>) -> Unit) { val n = names(s); if (n.isEmpty()) s.message = "Select objects first" else f(n) }

    /** Material_Edit (MaterialsEditor): property list of a material; editing values and saving makes a User-library material. */
    private fun materialEditor(s: AppState, uuid: String, edits: MutableMap<String, String>) {
        s.eng({ call("material_info", mapOf("uuid" to uuid)) as JSONObject }) { m ->
            val props = mutableListOf<Pair<String, String>>()
            if (edits.isNotEmpty()) props += "__save" to "Save as new material (${edits.size} change${if (edits.size > 1) "s" else ""})"
            for (grp in listOf("physical", "appearance")) m.optJSONObject(grp)?.let { o -> o.keys().forEach { k -> props += k to "$k = ${edits[k] ?: o.getString(k)}" } }
            props += "__info" to "About: ${m.optString("library")} / ${m.optString("author")}"
            s.choose(m.optString("name"), props) { k ->
                when (k) {
                    "__save" -> s.textPrompt = TextPrompt("Name of the new material", m.optString("name") + " (edited)") { nm ->
                        s.eng({ call("material_save", mapOf("uuid" to uuid, "name" to nm, "values" to edits)) as String }) { s.message = "Saved $nm to the User library" } }
                    "__info" -> s.info = m.optString("name") to listOf("Library: " + m.optString("library"), "Author: " + m.optString("author"), "License: " + m.optString("license"),
                        "UUID: $uuid", "Parent: " + m.optString("parent"), "", m.optString("description")).joinToString("\n")
                    else -> { val cur = edits[k] ?: m.optJSONObject("physical")?.optString(k)?.takeIf { it.isNotEmpty() } ?: m.optJSONObject("appearance")?.optString(k) ?: ""
                        s.textPrompt = TextPrompt(k, cur) { v -> edits[k] = v; materialEditor(s, uuid, edits) } }
                }
            }
        }
    }

    fun run(s: AppState, id: String): Boolean {
        val v = s.viewport
        when {
            id in views -> v?.stdView(views.getValue(id))
            id in urls -> s.viewport?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urls.getValue(id))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            id == "Std_ViewFitAll" -> v?.fitAll()
            id == "Std_ViewFitSelection" -> v?.fitSelection()
            id == "Std_ViewRotateLeft" -> v?.rotateView(90f)
            id == "Std_ViewRotateRight" -> v?.rotateView(-90f)
            id == "Std_ViewZoomIn" -> v?.zoom(1 / 1.25f)
            id == "Std_ViewZoomOut" -> v?.zoom(1.25f)
            id == "Std_ViewBoxZoom" -> s.boxSelect = "zoom"
            id == "Std_OrthographicCamera" -> v?.let { it.cam.orthographic = true; it.refresh() }
            id == "Std_PerspectiveCamera" -> v?.let { it.cam.orthographic = false; it.refresh() }
            id == "Std_AxisCross" -> { s.axisCross = !s.axisCross; v?.sync() }
            id == "Std_DrawStyle" -> s.choose("Draw style", drawStyles.map { it to it }) { st -> s.drawStyle = st; v?.sync() }
            id == "Std_ToggleFreeze" -> needSel(s) { n -> s.step("Toggle freeze", { call("toggle_freeze", mapOf("names" to n)) }) }
            // Start page: new / open / recent documents
            id == "Start_Start" -> s.choose("Start", listOf("new" to "New document", "open" to "Open a file…") + s.recentFiles().map { it.first to "Recent: ${it.second}" }) { k ->
                when (k) { "new" -> s.run("Std_New"); "open" -> s.run("Std_Open"); else -> s.reopen(Uri.parse(k)) } }
            id == "Materials_InspectMaterial" || id == "Materials_InspectAppearance" -> needSel(s) { n ->
                s.eng({ call("material_inspect", mapOf("names" to n, "appearance" to (id == "Materials_InspectAppearance"))) as String }) { t ->
                    s.info = (if (id == "Materials_InspectMaterial") "Material" else "Appearance") to t } }
            id == "Material_Edit" -> s.eng({ call("materials") as JSONArray }) { a ->
                s.choose("Material editor", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { u -> materialEditor(s, u, mutableMapOf()) } }
            // FEM's clipping planes (Coin SoClipPlane on the scene root) are the view's clipping plane here
            id == "FEM_ClippingPlaneRemoveAll" -> { s.clipPlane = null; v?.sync() }
            id == "Std_ToggleClipPlane" || id == "FEM_ClippingPlaneAdd" -> if (s.clipPlane != null && id == "Std_ToggleClipPlane") { s.clipPlane = null; v?.sync() } else
                s.choose("Clipping plane", listOf("X" to "Normal X", "Y" to "Normal Y", "Z" to "Normal Z")) { ax ->
                    s.askNumber("Clip offset along $ax", 0.0) { d -> val n = FloatArray(3).also { it["XYZ".indexOf(ax)] = 1f }
                        s.clipPlane = floatArrayOf(n[0], n[1], n[2], -d.toFloat()); v?.sync() } }
            id == "Std_ViewScreenShot" -> s.fileRequest = AppState.FileRequest("screenshot", suggested = "${s.docName}.png")
            id == "Std_StoreWorkingView" || id == "Std_ViewSaveCamera" -> v?.let { s.savedViews["Working view"] = it.cameraState(); s.message = "View stored" }
            id == "Std_RecallWorkingView" || id == "Std_ViewRestoreCamera" -> s.savedViews["Working view"]?.let { v?.restoreCamera(it) } ?: run { s.message = "No stored view" }
            id == "Std_FreezeViews" -> s.choose("Frozen views", listOf("+" to "Freeze current view") + s.savedViews.keys.filter { it != "Working view" }.map { it to it }) { k ->
                if (k == "+") v?.let { s.savedViews["View ${s.savedViews.size + 1}"] = it.cameraState() } else s.savedViews[k]?.let { v?.restoreCamera(it) } }
            id == "Std_DemoMode" -> v?.let { it.turntable = !it.turntable }
            id == "Std_ViewFullscreen" || id == "Std_MainFullscreen" -> (v?.context as? android.app.Activity)?.window?.let { w ->
                val c = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView); s.fullscreen = !s.fullscreen
                if (s.fullscreen) { c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()); c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
                else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }

            // ---- document
            id == "Std_Open" -> s.fileRequest = AppState.FileRequest("open")
            id == "Std_Save" || id == "Std_SaveAll" -> s.fileRequest = AppState.FileRequest("save")
            id == "Std_SaveAs" -> s.fileRequest = AppState.FileRequest("save", suggested = "${s.docName}.FCStd")
            id == "Std_SaveCopy" -> s.fileRequest = AppState.FileRequest("savecopy", suggested = "${s.docName} copy.FCStd")
            id == "Std_MergeProjects" -> s.fileRequest = AppState.FileRequest("merge")
            id == "Std_Revert" -> s.docUri?.let { u -> s.reopen(Uri.parse(u)) } ?: run { s.message = "The document has not been saved yet" }
            id == "Std_RecentFiles" -> s.choose("Open recent", s.recentFiles()) { u -> s.reopen(Uri.parse(u)) }
            id == "Std_Print" || id == "Std_PrintPdf" || id == "Std_PrintPreview" -> s.fileRequest = AppState.FileRequest("pdf", suggested = "${s.docName}.pdf")
            id == "Std_Quit" -> (v?.context as? android.app.Activity)?.finishAndRemoveTask()
            id == "Std_ProjectInfo" || id == "Std_ProjectUtil" -> s.eng({ call("doc_info") as JSONObject }) { j ->
                s.choose("Document information", j.keys().asSequence().map { k -> k to "$k: ${j.optString(k)}" }.toList()) { k ->
                    if (k in setOf("Label", "CreatedBy", "LastModifiedBy", "Company", "License", "LicenseURL", "Comment"))
                        s.textPrompt = TextPrompt(k, j.optString(k)) { t -> s.step("Edit $k", { call("doc_set_info", mapOf("key" to k, "value" to t)) }) } } }
            id == "Std_Recompute" || id == "Std_Refresh" -> s.step("Recompute", { call("force_recompute", mapOf("names" to names(s).ifEmpty { null })) })
            id == "Std_ToggleSkipRecompute" -> s.eng({ call("toggle_skip_recompute") }) { r -> s.message = if (r == true) "Recomputes skipped" else "Recomputes enabled" }
            id == "Std_ToggleSuppress" -> needSel(s) { n -> s.step("Toggle suppressed", { call("toggle_suppress", mapOf("names" to n)) }) }

            // ---- edit / selection
            id == "Std_Copy" -> needSel(s) { n -> s.eng({ call("clip_copy", mapOf("names" to n)) }) { s.message = "Copied" } }
            id == "Std_Cut" -> needSel(s) { n -> s.selection.clear(); s.step("Cut", { call("clip_copy", mapOf("names" to n, "cut" to true)) }) }
            id == "Std_Paste" -> s.step("Paste", { call("clip_paste") })
            id == "Std_DuplicateSelection" -> needSel(s) { n -> s.choose("Duplicate", listOf("no" to "Selection only (keep links to originals)", "deps" to "With dependencies")) { d ->
                s.step("Duplicate", { call("duplicate", mapOf("names" to n, "deps" to (d == "deps"))) }) } }
            id == "Std_SelectAll" -> { s.selection.clear(); s.meshes.keys.filter { it !in s.unselectable }.forEach { s.selection.add(SelRef(it)) }; s.recordSelection(); v?.sync() }
            id == "Std_SelectVisibleObjects" -> { s.selection.clear(); s.objects.filter { it.visible && it.name in s.meshes }.forEach { s.selection.add(SelRef(it.name)) }; s.recordSelection(); v?.sync() }
            id == "Std_SelBack" -> s.selStep(-1)
            id == "Std_SelForward" -> s.selStep(1)
            id == "Std_SelBoundingBox" -> { s.selBoundingBox = !s.selBoundingBox; v?.sync() }
            id == "Std_ClarifySelection" -> v?.pickAll()?.takeIf { it.isNotEmpty() }?.let { refs ->
                s.choose("Select", refs.map { it.toString() to "${s.obj(it.obj)?.label ?: it.obj}  ${it.sub}" }) { k ->
                    refs.firstOrNull { it.toString() == k }?.let { r -> s.selection.clear(); s.selection.add(r); s.recordSelection(); v.sync() } } }
                ?: run { s.message = "Tap the model first, then Clarify Selection lists everything under that point" }
            id == "Std_BoxSelection" -> s.boxSelect = "objects"
            id == "Std_BoxElementSelection" -> s.boxSelect = "faces"
            id == "Std_ShowSelection" -> needSel(s) { n -> s.eng({ call("set_visibility", mapOf("names" to n, "visible" to true)) }) { s.refresh() } }
            id == "Std_HideSelection" -> needSel(s) { n -> s.eng({ call("set_visibility", mapOf("names" to n, "visible" to false)) }) { s.refresh() } }
            id == "Std_ShowObjects" || id == "Std_HideObjects" -> s.eng({ call("set_visibility", mapOf("names" to s.objects.filter { it.hasShape }.map { it.name }, "visible" to (id == "Std_ShowObjects"))) }) { s.refresh() }
            id == "Std_ToggleObjects" -> s.eng({ s.objects.filter { it.hasShape }.forEach { o -> call("set_visibility", mapOf("names" to listOf(o.name), "visible" to !o.visible)) } }) { s.refresh() }
            id == "Std_ToggleSelectability" -> needSel(s) { n -> n.forEach { if (it in s.unselectable) s.unselectable.remove(it) else s.unselectable.add(it) }; s.selection.clear(); v?.sync() }
            id == "Std_ToggleTransparency" -> needSel(s) { n -> n.forEach { if (it in s.transparent) s.transparent.remove(it) else s.transparent.add(it) }; v?.sync() }
            id == "Std_RandomColor" -> needSel(s) { n -> n.forEach { s.colors[it] = android.graphics.Color.HSVToColor(floatArrayOf((Math.random() * 360).toFloat(), .45f, .85f)) and 0xFFFFFF }; v?.sync() }
            // Std_TextureMapping (DlgTextureMapping): an image on the selected objects; again on a textured object removes it
            id == "Std_TextureMapping" -> needSel(s) { n -> if (n.all { it in s.textures }) { n.forEach { s.textures.remove(it) }; v?.sync() }
                else s.fileRequest = AppState.FileRequest("texture", n) }
            id == "Std_ViewLoadImage" -> s.fileRequest = AppState.FileRequest("viewimage")
            id == "Part_ImportCurveNet" -> s.fileRequest = AppState.FileRequest("curvenet")
            // Part_ColorPerFace (TaskFaceAppearances): colour of the selected faces
            id == "Part_ColorPerFace" -> { val faces = s.selection.filter { it.sub.startsWith("Face") }.map { it.obj to it.sub.removePrefix("Face").toInt() }
                if (faces.isEmpty()) s.message = "Select faces first" else
                s.choose("Face colour", palette.map { (k, _) -> k to k } + ("Default" to "Default")) { c ->
                    faces.forEach { f -> palette[c]?.let { s.faceColors[f] = it } ?: s.faceColors.remove(f) }; s.selection.clear(); v?.sync() } }
            id == "Std_SetAppearance" -> needSel(s) { n -> s.choose("Appearance", palette.map { (k, _) -> k to k } + ("Default" to "Default")) { c ->
                n.forEach { o -> palette[c]?.let { s.colors[o] = it } ?: s.colors.remove(o) }; v?.sync() } }
            id == "Std_SetMaterial" -> needSel(s) { n -> s.eng({ call("materials") as JSONArray }) { a ->
                s.choose("Material", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { u ->
                    s.step("Set material", { call("set_material", mapOf("names" to n, "uuid" to u)) }) { s.applyMaterialColors(n) } } } }
            id == "Std_Placement" || id == "Std_Transform" || id == "Std_TransformManip" || id == "Std_Edit" || id == "Std_Properties" ->
                s.selection.firstOrNull()?.let { s.openEditor(it.obj) } ?: run { s.message = "Select an object first" }

            // ---- links
            id == "Std_LinkMakeGroup" -> needSel(s) { n -> s.step("Make link group", { call("link_group", mapOf("names" to n)) }) }
            id == "Std_LinkMakeRelative" -> if (s.selection.any { it.sub.isNotEmpty() }) s.step("Make sub-link", { call("link_relative", mapOf("sel" to s.selJson())) }) else s.message = "Select a face, edge or vertex"
            id == "Std_LinkReplace" -> needSel(s) { n -> s.step("Replace with link", { call("link_replace", mapOf("names" to n)) }) }
            id == "Std_LinkUnlink" -> needSel(s) { n -> s.step("Unlink", { call("link_unlink", mapOf("names" to n)) }) }
            id == "Std_LinkImport" -> needSel(s) { n -> s.step("Import links", { call("link_import", mapOf("names" to n)) }) }
            id == "Std_LinkImportAll" -> s.step("Import all links", { call("link_import") })
            id == "Std_LinkSelectLinked" || id == "Std_LinkSelectLinkedFinal" -> s.selection.firstOrNull()?.let { r ->
                s.eng({ call("linked", mapOf("name" to r.obj, "final" to (id == "Std_LinkSelectLinkedFinal"))) as String }) { t -> s.selection.clear(); s.selection.add(SelRef(t)); s.recordSelection(); v?.sync() } }
            id == "Std_LinkSelectAllLinks" || id == "Std_TreeSelectAllInstances" -> s.selection.firstOrNull()?.let { r ->
                s.eng({ call("links_to", mapOf("name" to r.obj)) as JSONArray }) { a -> s.selection.clear(); for (i in 0 until a.length()) s.selection.add(SelRef(a.getString(i))); s.recordSelection(); v?.sync() } }
            id == "Std_TreeSelection" -> s.treeOpen = true
            id == "Std_TreeCollapse" || id == "Std_TreeExpand" -> { val sel = names(s).ifEmpty { s.objects.map { it.name } }
                sel.forEach { n -> if (id == "Std_TreeCollapse") { if (n !in s.collapsed) s.collapsed.add(n); s.expanded.remove(n) }
                    else { s.collapsed.remove(n); if (n !in s.expanded) s.expanded.add(n) } } }
            id == "Std_PythonHelp" -> s.viewport?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wiki.freecad.org/FreeCAD_Scripting_Basics")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // Std_AlignToSelection: look straight at the selected face / along the selected edge
            id == "Std_AlignToSelection" -> s.selection.firstOrNull { it.sub.isNotEmpty() }?.let { r ->
                s.eng({ run("""
import Part
e = doc.getObject(args['o']).getSubObject(args['s'])
if e.ShapeType == 'Face':
    u0, u1, v0, v1 = e.ParameterRange; n = e.normalAt((u0 + u1) / 2, (v0 + v1) / 2); c = e.CenterOfMass
elif e.ShapeType == 'Edge':
    n = e.tangentAt(e.FirstParameter); c = e.CenterOfMass
else:
    n = App.Vector(0, 0, 1); c = e.Point
result = [list(n), list(c)]""", mapOf("o" to r.obj, "s" to r.sub)) as JSONArray }) { a ->
                    fun vec(i: Int) = a.getJSONArray(i).let { x -> DoubleArray(3) { x.getDouble(it) } }
                    v?.cam?.lookAlong(vec(0), vec(1)); v?.refresh() } } ?: run { s.message = "Select a face or an edge" }
            // Std_Alignment (ManualAlignment): pick point pairs, first on the selected (moving) object, then on the fixed one
            id == "Std_Alignment" -> needSel(s) { n -> s.choose("Alignment", listOf("1" to "1 point pair (translate)", "2" to "2 point pairs", "3" to "3 point pairs (full)")) { k ->
                val pairs = k.toInt()
                s.startDraft(DraftTool("Alignment", "Alignment", List(pairs * 2) { i -> if (i % 2 == 0) "Point ${i / 2 + 1} on the moving object" else "Matching point ${i / 2 + 1} on the fixed object" },
                    pairs * 2, pairs * 2) { p -> s.step("Alignment", { call("align_objects", mapOf("names" to n, "pts" to p.map { it.toList() })) }) }) } }
            // Std_AnnotationLabel: App::AnnotationLabel (BasePosition, TextPosition, LabelText)
            id == "Std_AnnotationLabel" -> s.textPrompt = TextPrompt("Label text", "Label") { t ->
                s.startDraft(DraftTool("AnnotationLabel", "Annotation label", listOf("Point the label refers to", "Label position"), 2, 2) { p ->
                    s.step("Annotation label", { run("o = doc.addObject('App::AnnotationLabel', 'Label'); o.LabelText = args['t'].split('\\n'); " +
                        "o.BasePosition = App.Vector(*args['p'][0]); o.TextPosition = App.Vector(*args['p'][1]); result = o.Name", mapOf("t" to t, "p" to p.map { it.toList() })) }) }) }

            // ---- tools
            id == "Std_MassProperties" -> needSel(s) { n -> s.eng({ call("mass_properties", mapOf("names" to n)) as JSONArray }) { a ->
                fun f(d: Double) = "%.5g".format(d)
                s.info = "Mass properties" to (0 until a.length()).joinToString("\n\n") { i -> val j = a.getJSONObject(i); buildString {
                    appendLine(j.getString("label")); appendLine("Volume ${f(j.getDouble("volume"))} mm³   Area ${f(j.getDouble("area"))} mm²")
                    if (j.has("mass")) appendLine("Density ${f(j.getDouble("density"))} kg/m³   Mass ${f(j.getDouble("mass"))} kg")
                    j.optJSONArray("center")?.let { c -> appendLine("Center of mass (${f(c.getDouble(0))}, ${f(c.getDouble(1))}, ${f(c.getDouble(2))}) mm") }
                    j.optJSONArray("inertia")?.let { m -> append("Inertia Ixx ${f(m.getDouble(0))}  Iyy ${f(m.getDouble(1))}  Izz ${f(m.getDouble(2))}  Ixy ${f(m.getDouble(3))}  Ixz ${f(m.getDouble(4))}  Iyz ${f(m.getDouble(5))}") }
                } } } }
            id == "Std_Measure" -> if (s.selection.isEmpty()) s.message = "Select one or two elements to measure" else
                s.eng({ call("measure", mapOf("sel" to s.selJson())) as JSONArray }) { a ->
                    s.info = "Measure" to (0 until a.length()).joinToString("\n") { i -> a.getJSONArray(i).let { "${it.getString(0)}: ${it.getString(1)}" } } }
            id == "Std_UnitsCalculator" -> s.textPrompt = TextPrompt("Convert (e.g. 2 in to mm)", "1 in to mm") { t ->
                val (q, u) = t.split(Regex("\\s+to\\s+")).let { it[0] to it.getOrElse(1) { "mm" } }
                s.eng({ call("convert_units", mapOf("text" to q, "unit" to u)) as String }) { r -> s.info = "Units converter" to r } }
            id == "Std_DependencyGraph" -> s.eng({ (call("dependency_graph") as JSONObject).getString("text") }) { t -> s.info = "Dependency graph" to t }
            id == "Std_ExportDependencyGraph" -> s.fileRequest = AppState.FileRequest("graph", suggested = "${s.docName}.gv")
            id == "Std_CommandLine" || id == "Std_SendToPythonConsole" -> s.console()
            id == "Std_DlgMacroExecute" || id == "Std_DlgMacroExecuteDirect" -> s.fileRequest = AppState.FileRequest("macro")
            id == "Std_DlgPreferences" -> s.choose("Preferences", listOf("units" to "Unit system", "theme" to "Theme (current: ${s.theme.replaceFirstChar { it.uppercase() }})")) { page ->
                if (page == "theme") s.choose("Theme", listOf("system" to "System (follow Android)", "light" to "Light", "dark" to "Dark")) { t -> s.setThemeMode(t) }
                else s.eng({ call("unit_schemas") as JSONObject }) { j -> val a = j.getJSONArray("schemas")
                    s.choose("Unit system (current: ${a.getString(j.getInt("current"))})", List(a.length()) { i -> i.toString() to a.getString(i) }) { i ->
                        s.eng({ call("set_unit_schema", mapOf("index" to i.toInt())) }) { s.message = "Units: ${a.getString(i.toInt())}" } } } }
            id == "Std_About" || id == "Std_AboutQt" -> s.choose("MobileCAD", listOf("about" to "About MobileCAD", "licenses" to "Open-source licenses")) { what ->
                if (what == "licenses") s.showLicenses()
                else s.eng({ run("import FreeCAD; result = ' '.join(FreeCAD.Version()[:4])") }) { ver ->
                    s.info = "MobileCAD ${s.appVersion}" to ("CAD for Android, built on FreeCAD $ver (LGPL-2.1-or-later) with Open CASCADE, Qt 6 and Python 3.14.\n\n" +
                        "MobileCAD is an independent project. It is not affiliated with, sponsored or endorsed by the FreeCAD project or the " +
                        "FreeCAD Project Association. FreeCAD is a trademark of the FreeCAD Project Association.\n\n" +
                        "MobileCAD is free software under the GNU General Public License v3 or later: you may share and modify it, and its " +
                        "source code is available from where you downloaded the app. See \"Open-source licenses\" for the components it includes.") } }
            id == "Std_Workbench" -> s.choose("Workbench", Workspaces.main(s.catalog).map { it.id to it.label } + Workspaces.more) { w -> s.switchWorkspace(w) }
            else -> return false
        }
        return true
    }

    /** Std_SetAppearance colour choices (FreeCAD's standard material colours). */
    private val palette = linkedMapOf("Steel" to 0xB0B7BD, "Aluminium" to 0xD4D8DC, "Brass" to 0xC9A64B, "Copper" to 0xC27C4E,
        "Red" to 0xD9534F, "Orange" to 0xF0A030, "Yellow" to 0xF2D45C, "Green" to 0x5CB85C, "Blue" to 0x4A90D9, "Purple" to 0x8E6BBF,
        "Black" to 0x3A3F44, "White" to 0xF4F6F8)
}
