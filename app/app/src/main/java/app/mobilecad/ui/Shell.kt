package app.mobilecad.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.GuiCommands
import app.mobilecad.freecad.FcObject
import app.mobilecad.freecad.MobileCommands
import app.mobilecad.freecad.SelRef
import app.mobilecad.freecad.ToolsetDef
import app.mobilecad.freecad.Workspaces
import app.mobilecad.sketch.SketchTool
import app.mobilecad.viewport.ViewportView
import java.io.File
import app.mobilecad.R
import app.mobilecad.freecad.DraftTool
import app.mobilecad.freecad.NumpadRequest
import app.mobilecad.freecad.TextPrompt
import app.mobilecad.sketch.SketchEditor
import app.mobilecad.techdraw.PageView

@Composable
fun Shell(app: AppState) {
    val ctx = LocalContext.current
    // Messages are toasts, except on a secondary display (MobileCAD XR draws this UI on a private virtual display, where the
    // system cannot show toasts): there they appear in the UI itself, at the bottom.
    val onSecondary = androidx.compose.ui.platform.LocalView.current.display?.displayId.let { it != null && it != android.view.Display.DEFAULT_DISPLAY }
    var inUiMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(app.message) { app.message?.let {
        app.message = null
        if (onSecondary) inUiMessage = it else Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
    } }
    // (its own effect: clearing app.message above restarts that one)
    LaunchedEffect(inUiMessage) { if (inUiMessage != null) { kotlinx.coroutines.delay(3500); inUiMessage = null } }
    if (!app.ready) { Splash(app.status); return }

    var view by remember { mutableStateOf<ViewportView?>(null) }
    var menu by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // Back = FreeCAD's Esc: closes what is open (numpad, tool panel, picking, dialog, sketch), innermost first.  With
    // nothing open the app goes to the background instead of finishing, so the open document is never lost.
    androidx.activity.compose.BackHandler {
        when {
            app.numpad != null -> app.numpad = null
            app.textPrompt != null -> app.textPrompt = null
            app.choice != null -> app.choice = null
            menu != null -> menu = null
            app.openToolset != null -> app.openToolset = null
            app.draft != null -> app.cancelDraft()
            app.mate != null -> app.cancelMate()
            app.taskPanel != null -> GuiCommands.panelButton(app, if (app.taskPanel?.optJSONArray("buttons")?.toString()?.contains("\"cancel\"") == true) "cancel" else "close")
            app.edit != null -> app.cancelEdit()
            app.sketch != null -> app.leaveSketch(true)
            else -> (ctx as? android.app.Activity)?.moveTaskToBack(true)
        }
    }
    var treeWidth by remember { mutableStateOf(240.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val ver = app.sceneVersion.intValue; val sel = app.selection.toList(); val sk = app.sketch; val manip = app.manipulator
    LaunchedEffect(ver, sel, sk, manip) { view?.sync() }

    // Every file command (Std_Open/Save/SaveAs/SaveCopy/MergeProjects, Part_Import/Export, macros, screenshots, PDF ...)
    // goes through the Storage Access Framework: the picked document is copied to/from the app cache for FreeCAD.
    var pending by remember { mutableStateOf<AppState.FileRequest?>(null) }
    fun cacheFile(uri: Uri, fallback: String) = File(ctx.cacheDir, displayName(ctx, uri) ?: fallback)
    fun writeBack(uri: Uri, f: File) { ctx.contentResolver.openOutputStream(uri, "wt")?.use { o -> f.inputStream().use { it.copyTo(o) } } }
    fun saveTo(uri: Uri) { val f = cacheFile(uri, "${app.docName}.FCStd")
        app.eng({ saveDocument(f.absolutePath) }) { writeBack(uri, f); app.docUri = uri.toString(); app.docName = f.nameWithoutExtension; app.message = "Saved ${f.name}"; app.markSaved() } }   // saveAs names the document after its file
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val r = pending ?: return@rememberLauncherForActivityResult; pending = null
        if (uri == null) { if (r.kind == "panelopen") GuiCommands.panelFile(app, null); return@rememberLauncherForActivityResult }
        val f = cacheFile(uri, "file"); ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
        when (r.kind) {
            "panelopen" -> GuiCommands.panelFile(app, f.absolutePath)
            "curvenet" -> { val keep = File(ctx.filesDir, "imports").apply { mkdirs() }.let { File(it, f.name) }; f.copyTo(keep, overwrite = true)
                app.createFeature("Import Curve Net") { call("part_import_curve_net", mapOf("path" to keep.absolutePath)) as String } }
            "texture", "viewimage" -> {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
                var sample = 1; while (maxOf(o.outWidth, o.outHeight) / sample > (if (r.kind == "texture") 1024 else 2048)) sample *= 2
                val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                if (bmp == null) app.message = "Not an image: ${f.name}"
                else if (r.kind == "texture") { r.names.forEach { app.textures[it] = bmp }; view?.sync() } else app.imageView = bmp }
            "open" -> { runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                app.open(f.absolutePath); app.docUri = uri.toString(); app.addRecent(uri.toString(), f.name) }
            "propfile" -> { val keep = File(ctx.filesDir, "imports").apply { mkdirs() }.let { File(it, f.name) }; f.copyTo(keep, overwrite = true)
                app.step("Edit ${r.names[1]}", { setProperty(r.names[0], r.names[1], keep.absolutePath) }) { app.edit?.let { app.reloadProps(it) } } }
            "merge" -> app.step("Merge ${f.name}", { call("merge_project", mapOf("path" to f.absolutePath)) })
            "import" -> app.step("Import ${f.name}", { call("import_file", mapOf("path" to f.absolutePath)) })
            "sheetimport" -> app.step("Import ${f.name}", { call("sheet_import", mapOf("name" to r.names.firstOrNull(), "path" to f.absolutePath)) })
            "macro" -> app.step("Macro ${f.name}", { call("run_macro", mapOf("path" to f.absolutePath)) })
            "tdimage" -> { val keep = File(ctx.filesDir, "images").apply { mkdirs() }.let { File(it, f.name) }; f.copyTo(keep, overwrite = true)
                app.step("Bitmap image", { call("td_simple", mapOf("page" to r.names.first(), "kind" to "Image", "props" to mapOf("ImageFile" to keep.absolutePath, "Width" to 100.0, "Height" to 100.0))) }) }
        }
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val r = pending ?: return@rememberLauncherForActivityResult; pending = null
        if (uri == null) { if (r.kind == "panelsave") GuiCommands.panelFile(app, null); return@rememberLauncherForActivityResult }
        val f = cacheFile(uri, r.suggested)
        when (r.kind) {
            "panelsave" -> { f.delete(); GuiCommands.panelFile(app, f.absolutePath) { if (f.exists()) { writeBack(uri, f); app.message = "Saved ${f.name}" } } }
            "save" -> saveTo(uri)
            "savecopy" -> app.eng({ call("save_copy", mapOf("path" to f.absolutePath)) }) { writeBack(uri, f); app.message = "Saved a copy" }
            "export" -> app.eng({ call("export_file", mapOf("names" to r.names, "path" to f.absolutePath)) }) { writeBack(uri, f); app.message = "Exported ${f.name}" }
            "robotexport_ExportKukaCompact", "robotexport_ExportKukaFull" -> app.eng({ call("robot_cmd", mapOf("kind" to r.kind.removePrefix("robotexport_"), "names" to r.names, "opts" to mapOf("path" to f.absolutePath))) }) {
                writeBack(uri, f); app.message = "Exported ${f.name}" }
            "sheetexport" -> app.eng({ call("sheet_export", mapOf("name" to r.names.first(), "path" to f.absolutePath)) }) { writeBack(uri, f); app.message = "Exported ${f.name}" }
            "pagesvg" -> app.eng({ call("td_export_svg", mapOf("page" to r.names.first(), "path" to f.absolutePath)) }) { writeBack(uri, f); app.message = "Exported ${f.name}" }
            "pagedxf" -> app.eng({ call("td_export_dxf", mapOf("page" to r.names.first(), "path" to f.absolutePath)) }) { writeBack(uri, f); app.message = "Exported ${f.name}" }
            "pagepdf" -> app.pageView?.let { pv -> val pdf = android.graphics.pdf.PdfDocument()
                val wmm = app.pageRender?.optDouble("width", 297.0) ?: 297.0; val hmm = app.pageRender?.optDouble("height", 210.0) ?: 210.0
                val pg = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder((wmm / 25.4 * 72).toInt(), (hmm / 25.4 * 72).toInt(), 1).create())
                pv.drawPage(pg.canvas, (72 / 25.4).toFloat()); pdf.finishPage(pg)
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { pdf.writeTo(it) }; pdf.close(); app.message = "Exported ${displayName(ctx, uri) ?: "PDF"}" }
            "graph" -> app.eng({ (call("dependency_graph") as org.json.JSONObject).getString("dot") }) { dot -> f.writeText(dot); writeBack(uri, f); app.message = "Exported ${f.name}" }
            "screenshot", "pdf" -> view?.screenshot { bmp ->
                if (r.kind == "screenshot") ctx.contentResolver.openOutputStream(uri, "wt")?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                else { val pdf = android.graphics.pdf.PdfDocument()
                    val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create())
                    page.canvas.drawBitmap(bmp, 0f, 0f, null); pdf.finishPage(page)
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { pdf.writeTo(it) }; pdf.close() }
                app.message = "Saved ${displayName(ctx, uri) ?: ""}" }
        }
    }
    LaunchedEffect(app.fileRequest) { app.fileRequest?.let { r -> app.fileRequest = null
        // Std_Save to the known location without asking
        if (r.kind == "save" && r.suggested.isEmpty() && app.docUri != null) { saveTo(Uri.parse(app.docUri)); return@let }
        pending = r
        if (r.create) createFile.launch(r.suggested.ifEmpty { "${app.docName}.FCStd" }) else pickFile.launch(arrayOf("*/*")) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // window size class (Material: compact < 600dp wide): phones, folded foldables, split screen
    val compactNow = maxWidth < 600.dp
    SideEffect { Screen.width = maxWidth; Screen.height = maxHeight }
    LaunchedEffect(compactNow) { Screen.compact = compactNow; if (compactNow) app.treeOpen = false }
    // phones: the list is a drawer over the view, so it gets out of the way of the dialog / sketch it just opened
    LaunchedEffect(app.edit, app.sketch) { if (Screen.compact && (app.edit != null || app.sketch != null)) app.treeOpen = false }
    val compact = Screen.compact
    // panels: floating at the top left on tablets, bottom sheets on phones (the model stays visible above)
    val panelAlign = if (compact) Alignment.BottomCenter else Alignment.TopStart
    val panelPad = if (compact) Modifier.padding(0.dp) else Modifier.padding(6.dp)
    // the feature list's collapsed tab: tapping it opens the list
    val treeTab: @Composable () -> Unit = {
        Column(Modifier.width(40.dp).fillMaxHeight().background(OS.Surface).clickable { app.treeOpen = true }, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp)); Icon(Icons.Default.ChevronRight, "Show features", tint = OS.Primary, modifier = Modifier.size(28.dp))
            Icon(Icons.Default.AccountTree, null, tint = OS.Steel, modifier = Modifier.padding(top = 8.dp).size(20.dp))
        }
    }
    val topBar: @Composable () -> Unit = { TopBar(app, onOpen = { app.fileRequest = AppState.FileRequest("open") }, onSave = { app.fileRequest = AppState.FileRequest("save", suggested = "${app.docName}.FCStd") }, onFit = { view?.fitAll() }) }
    val message: @Composable (Modifier) -> Unit = { mod -> inUiMessage?.let { m ->
        Text(m, color = Color.White, fontSize = 14.sp, modifier = mod.shadow(6.dp, RoundedCornerShape(20.dp)).background(Color(0xE6202428), RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
    } }
    val numpad: @Composable (Modifier) -> Unit = { mod -> app.numpad?.let { n -> key(n) { Numpad(n.title, n.initial, n.units, n.onAccept, { app.numpad = null }, mod) } } }   // fresh state per request
    // What the 3D view area holds: the view itself and what floats over it (`stage`: banners, chips, menus, dialogs, the
    // drawing / spreadsheet that replace the view), and the panels docked in it (`side`: tool panels, feature dialogs,
    // task panels, the constraint list). One area on phones and tablets; MobileCAD XR shows them as two surfaces.
    val viewArea: @Composable BoxScope.(Boolean, Boolean) -> Unit = { stage, side ->
        if (stage) AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            ViewportView(c, app).also { it.layoutParams = ViewGroup.LayoutParams(-1, -1); it.onContextMenu = { x, y -> menu = x to y }; view = it; app.viewport = it; it.sync() }
        })
        // Variables tab: the spreadsheet grid replaces the 3D view
        if (stage) if (app.inSheet) SheetView(app, Modifier.fillMaxSize())
        // Drawing tab: the TechDraw page replaces the 3D view
        if (stage) if (app.inDrawing) {
            LaunchedEffect(Unit) { app.refreshPage() }
            AndroidView(modifier = Modifier.fillMaxSize().clipToBounds(), factory = { c ->
                PageView(c, app).also { it.layoutParams = ViewGroup.LayoutParams(-1, -1); app.pageView = it } },
                update = { it.invalidate() })
            DisposableEffect(Unit) { onDispose { app.pageView = null } }
        }
        // empty element tabs: the first step as a button (empty-state call to action)
        if (stage) if (app.inDrawing && app.page == null && app.objects.none { it.type == "TechDraw::DrawPage" })
            EmptyState("No drawing yet", "New drawing page", Modifier.align(Alignment.Center)) { app.run("TechDraw_PageDefault") }
        if (stage) if (app.inAssembly && app.objects.none { it.type == "Assembly::AssemblyObject" })
            EmptyState("No assembly yet", "Create assembly", Modifier.align(Alignment.Center)) { app.run("Assembly_CreateAssembly") }
        if (side) app.openToolset?.let { ts ->
            ToolPanel(app.catalog, ts, { MobileCommands.available(it) }, { app.run(it.id) }, panelPad.align(panelAlign),
                extras = remember(ts, sk != null) { Workspaces.extras(app.catalog, app.workspace, ts, sketchMode = sk != null) })
        }
        if (side) app.edit?.let { e ->
            FeatureDialog(e.title, e.type, e.props, e.error, e.activeQuery,
                onQueryFocus = { q -> e.activeQuery = if (e.activeQuery == q) null else q },
                onSet = { p, v -> app.setProp(e, p, v) },
                onNumpad = { p -> app.numpad = NumpadRequest(p.name, p.expression?.let { "=$it" } ?: (p.value?.toString() ?: ""), DialogSpec.units(p.type, p.value)) { v -> app.numpad = null; app.setExpr(e, p, v.removePrefix("=")) } },
                onOk = { app.commitEdit() }, onCancel = { app.cancelEdit() }, modifier = panelPad.align(panelAlign),
                editors = DialogEditors(
                    number = { t, init, units, ok -> app.numpad = NumpadRequest(t, init, units) { v -> app.numpad = null; ok(v) } },
                    text = { t, init, multi, ok -> app.textPrompt = TextPrompt(t, init, multi, ok) },
                    file = { p -> app.fileRequest = AppState.FileRequest("propfile", listOf(e.obj, p.name)) },
                    choose = { t, opts, ok -> app.choose(t, opts, ok) }),
                onSetPart = { path, v -> app.setPart(e, path, v.removePrefix("=")) })
        }
        if (side) app.taskPanel?.let { p -> TaskPanelView(app, p, panelPad.align(panelAlign)) }
        if (stage) if (app.inDrawing) Text("Fit", Modifier.align(Alignment.TopEnd).padding(8.dp).background(OS.Surface.copy(alpha = .92f), RoundedCornerShape(6.dp))
            .clickable { app.pageView?.fit() }.padding(horizontal = 16.dp, vertical = 10.dp), color = OS.Primary, fontSize = 12.sp)
        if (stage) if (sk == null && app.openToolset == null && app.selection.isNotEmpty() && (app.xr || !((compact || Screen.height < 560.dp) && (app.edit != null || app.taskPanel != null)))) SelectionChips(app, Modifier.align(Alignment.BottomStart).padding(8.dp))
        if (stage) app.draft?.let { t -> DraftBanner(app, t, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) }
        if (stage) app.mate?.let { m -> MateBanner(app, m.jointType, m.picks.size, Modifier.align(Alignment.TopCenter).padding(top = 10.dp)) }
        if (stage) app.partPicker?.let { parts -> PartPicker(app, parts) }
        if (stage) app.textPrompt?.let { tp -> var txt by remember(tp) { mutableStateOf(tp.initial) }
            AppDialog(onDismissRequest = { app.textPrompt = null }, title = { Text(tp.title) },
                text = { OutlinedTextField(txt, { txt = it }, singleLine = !tp.multiline, minLines = if (tp.multiline) 4 else 1, maxLines = if (tp.multiline) 10 else 1) },
                confirmButton = { TextButton({ app.textPrompt = null; tp.onAccept(txt) }) { Text("OK") } },
                dismissButton = { TextButton({ app.textPrompt = null }) { Text("Cancel") } }) }
        if (stage) app.imageView?.let { bmp -> AppDialog(onDismissRequest = { app.imageView = null }, confirmButton = { TextButton({ app.imageView = null }) { Text("Close") } },
            title = { Text("Image ${bmp.width}×${bmp.height}") },
            text = { var sc by remember(bmp) { mutableStateOf(1f) }; var off by remember(bmp) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 480.dp).clipToBounds()
                    .pointerInput(bmp) { detectTransformGestures { _, pan, zoom, _ -> sc = (sc * zoom).coerceIn(1f, 12f); off += pan } }
                    .graphicsLayer { scaleX = sc; scaleY = sc; translationX = off.x; translationY = off.y }) }) }
        if (stage) app.info?.let { (t, body) -> AppDialog(onDismissRequest = { app.info = null }, title = { Text(t) },
            text = { Text(body, fontSize = 13.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ app.info = null }) { Text("Close") } }) }
        if (stage) if (sk == null && (app.selFilter != null || app.boxSelect != null)) FilterBanner(app, Modifier.align(Alignment.BottomEnd).padding(10.dp))
        if (stage) app.multiChoice?.let { mc -> val picked = remember(mc) { mutableStateListOf<String>().apply { addAll(mc.initial) } }
            AppDialog(onDismissRequest = { app.multiChoice = null }, title = { Text(mc.title) },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp)) { items(mc.options) { (id, l) ->
                    Row(Modifier.fillMaxWidth().clickable { if (id in picked) picked.remove(id) else picked.add(id) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in picked, { if (it) picked.add(id) else picked.remove(id) }); Text(l, color = OS.Navy) } } } },
                confirmButton = { TextButton({ mc.onDone(picked.toList()) }) { Text("OK") } },
                dismissButton = { TextButton({ app.multiChoice = null }) { Text("Cancel") } }) }
        if (stage) app.choice?.let { ch -> AppDialog(onDismissRequest = { app.choice = null }, confirmButton = {}, dismissButton = { TextButton({ app.choice = null }) { Text("Cancel") } },
            title = { Text(ch.title) }, text = { Column { ch.options.forEach { (id, l) ->
                Text(l, Modifier.fillMaxWidth().clickable { ch.onPick(id) }.padding(vertical = 12.dp), color = OS.Navy) } } }) }
        if (stage) sk?.let { s -> SketchStatus(s.status, s.fully, s.conflicting.isNotEmpty(),
            if (compact) Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = Screen.width - 140.dp) else Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            // the solver message opens the constraint list on the problems (desktop: the message links select them)
            s.consList = if (s.conflicting.isNotEmpty() || s.redundant.isNotEmpty() || s.malformed.isNotEmpty()) "problems" else "all" } }
        if (side) sk?.takeIf { it.consList != null }?.let { s -> ConstraintList(app, s, panelPad.align(panelAlign)) }
        if (stage) sk?.let { s -> s.drawStep.let { _ -> SketchInputs(s, Modifier.align(if (compact) Alignment.BottomStart else Alignment.BottomCenter).padding(10.dp)) } }
        if (stage) ToolExit(app, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        if (stage) sk?.let { SketchSelectionBar(it, Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 72.dp else 64.dp)) }
        if (stage) menu?.let { (x, y) -> Box(Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) { ContextMenu(app, view) { menu = null } } }
    }

    if (app.xr) {
        // MobileCAD XR: the same interface, laid out to be cut into surfaces around the user (XrLayout): the feature list
        // on the left, the view area ahead with the bars (shown under it, like a keyboard), the docked panels on the right; nothing is drawn where
        // the model in the room should show through. Its dialogs are drawn in the view area (LocalInlineDialogs).
        CompositionLocalProvider(LocalInlineDialogs provides true) { Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(XrLayout.TREE.dp).fillMaxHeight()) { if (app.treeOpen) FeatureTree(app, Modifier.fillMaxSize()) { app.treeOpen = false } else treeTab() }
            Column(Modifier.width(XrLayout.STAGE.dp).fillMaxHeight()) {
                // the bars first: their menus drop down into the view area, which is shown in one piece
                Column(Modifier.height(XrLayout.BARS.dp).fillMaxWidth()) { topBar(); if (sk != null) SketchStrip(app) else ToolStrip(app); ElementTabs(app) }
                Box(Modifier.weight(1f).fillMaxWidth()) { viewArea(true, false); message(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) }
            }
            Box(Modifier.width(XrLayout.SIDE.dp).fillMaxHeight()) { viewArea(false, true); numpad(Modifier.align(Alignment.BottomCenter).padding(6.dp)) }
        } }
        return@BoxWithConstraints
    }
    Column(Modifier.fillMaxSize().background(OS.Metal6).statusBarsPadding()) {
        topBar()
        Row(Modifier.weight(1f)) {
            if (compact) { /* the feature list is a drawer over the view (below) */ }
            else if (app.treeOpen) {
                FeatureTree(app, Modifier.width(treeWidth).fillMaxHeight()) { app.treeOpen = false }
                // drag handle: resize the feature list
                Box(Modifier.width(14.dp).fillMaxHeight().background(OS.Metal6)
                    .pointerInput(Unit) { detectHorizontalDragGestures { _, dx ->
                        treeWidth = (treeWidth + with(density) { dx.toDp() }).coerceIn(160.dp, 480.dp) } },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.width(4.dp).height(36.dp).background(OS.Aluminum, RoundedCornerShape(2.dp)))
                }
            } else treeTab()
            Column(Modifier.weight(1f)) {
                if (sk != null) SketchStrip(app) else ToolStrip(app)
                Box(Modifier.weight(1f).fillMaxWidth().background(OS.Surface)) {
                    viewArea(true, true)
                    // phones: the feature list slides over the view; tapping outside closes it
                    if (compact && app.treeOpen) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { app.treeOpen = false })
                        FeatureTree(app, Modifier.width(minOf(320.dp, Screen.width * 0.85f)).fillMaxHeight().shadow(12.dp)) { app.treeOpen = false }
                    }
                }
            }
        }
        ElementTabs(app)
    }
    // the numpad sits above everything (like a keyboard): it fits short screens, e.g. phones in landscape
    numpad(if (compact) Modifier.align(Alignment.BottomCenter).navigationBarsPadding() else Modifier.align(Alignment.CenterEnd).padding(16.dp).statusBarsPadding())
    message(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }
}

/** Dialogs drawn in the layout instead of in windows of their own (MobileCAD XR: its private display takes no dialog
 *  windows). */
val LocalInlineDialogs = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Material's AlertDialog; with [LocalInlineDialogs] the same dialog over a scrim filling the enclosing box. */
@Composable
fun AppDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
              title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
    if (!LocalInlineDialogs.current) { AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton, title = title, text = text); return }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismissRequest), contentAlignment = Alignment.Center) {
        Surface(modifier.widthIn(min = 280.dp, max = 560.dp).padding(24.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},   // taps inside stay inside
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp, shadowElevation = 8.dp) {
            Column(Modifier.padding(24.dp)) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() }; Spacer(Modifier.height(16.dp)) }
                text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { Box(Modifier.weight(1f, fill = false)) { it() } }; Spacer(Modifier.height(24.dp)) }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) { dismissButton?.invoke(); confirmButton() }
            }
        }
    }
}

/** MobileCAD XR's layout (dp): the parts the headset cuts the interface into, left to right: feature list, [BARS] (top
 *  bar, tool strip, element tabs) over the view area, docked panels; [HEIGHT] tall. */
object XrLayout { const val TREE = 320; const val STAGE = 800; const val SIDE = 360; const val HEIGHT = 800; const val BARS = 154 }   // (whole pixels at 1.5 px per dp)

/** Draft tool prompt (desktop: the Draft task panel with its coordinate fields, Finish, Close, Undo, Copy). */
@Composable
private fun DraftBanner(app: AppState, t: DraftTool, modifier: Modifier) {
    Row(modifier.shadow(4.dp, RoundedCornerShape(8.dp)).background(OS.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.padding(end = 8.dp)) {
            Text(t.label, color = OS.Navy, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(t.prompt, color = OS.Steel, fontSize = 12.sp)
        }
        // typed coordinates: "x, y, z" (global) or "@dx, dy, dz" (relative to the last point), like the Draft task panel
        TextButton({ app.textPrompt = TextPrompt("Point: x, y, z  or  @dx, dy, dz", "") { txt ->
            val rel = txt.trim().startsWith("@"); val v = txt.removePrefix("@").split(Regex("[,;\\s]+")).mapNotNull { it.toDoubleOrNull() }
            if (v.size < 2) app.message = "Enter two or three numbers" else {
                val wp = app.workPlane; val loc = doubleArrayOf(v[0], v[1], v.getOrElse(2) { 0.0 })
                val base = if (rel) t.pts.lastOrNull() else null
                app.draftPoint(if (base != null) DoubleArray(3) { base[it] + wp.u[it] * loc[0] + wp.v[it] * loc[1] + wp.w[it] * loc[2] } else wp.global(loc)) } } }) { Text("Type") }
        if (t.pts.isNotEmpty()) TextButton({ t.pts.removeAt(t.pts.size - 1); app.viewport?.refresh() }) { Text("Undo") }
        if (t.maxPts < 0 && t.id == "Wire" && t.pts.size >= 2) TextButton({ app.draftPoint(t.pts.first()); app.finishDraft() }) { Text("Close") }
        if (t.maxPts < 0 && t.pts.size >= t.minPts) TextButton({ app.finishDraft() }) { Text("Finish") }
        if (t.modifier) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(t.copy, { t.copy = it }); Text("Copy", fontSize = 13.sp) }
        IconButton({ app.cancelDraft() }, Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Cancel", tint = OS.Red) }
    }
}

private fun displayName(ctx: android.content.Context, uri: Uri): String? =
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null }

/** Active Part selection gate / armed box selection, with a clear button (desktop: Part_RemoveSelectionGate). */
@Composable
private fun FilterBanner(app: AppState, modifier: Modifier) {
    Row(modifier.background(OS.Navy, RoundedCornerShape(16.dp)).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(when (app.boxSelect) { "faces" -> "Drag a box to select faces"; "objects" -> "Drag a box to select objects"; "zoom" -> "Drag a box to zoom"
            else -> "Selecting ${app.selFilter?.lowercase()}s only" }, color = Color.White, fontSize = 13.sp)
        IconButton({ app.boxSelect = null; app.selFilter = null }, Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Clear filter", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Splash(status: String) {
    // the theme's own background (white / near-black), the MobileCAD logo, progress and the loading step
    Box(Modifier.fillMaxSize().background(OS.Surface).statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.app_logo), "MobileCAD", Modifier.size(112.dp))
            Spacer(Modifier.height(20.dp))
            Text("MobileCAD", color = OS.Navy, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(20.dp))
            if (!status.startsWith("The CAD engine failed")) CircularProgressIndicator(color = OS.Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(status, color = if (status.startsWith("The CAD engine failed")) OS.Red else OS.Steel, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

@Composable
private fun TopBar(app: AppState, onOpen: () -> Unit, onSave: () -> Unit, onFit: () -> Unit) {
    var docMenu by remember { mutableStateOf(false) }
    // a plain title bar that follows the theme (white / dark)
    Row(Modifier.fillMaxWidth().height(56.dp).background(OS.TopBar).drawBehind {
        drawLine(OS.Metal4, androidx.compose.ui.geometry.Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Offset(size.width, size.height - 1f), 1f) }
        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconBtn(Icons.Default.Menu, "Document", OS.Navy) { docMenu = true }
            MainMenuDropdown(app, docMenu) { docMenu = false }
        }
        // phones: the feature list opens from here (it is a drawer)
        if (Screen.compact) IconBtn(Icons.Default.AccountTree, "Features", OS.Navy) { app.treeOpen = !app.treeOpen }
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.app_logo), "MobileCAD",
            Modifier.padding(start = 4.dp).size(30.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(app.docName, color = OS.Navy, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${app.workspace.label} · based on FreeCAD ${app.catalog.source.removePrefix("FreeCAD ")}", color = OS.Steel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconBtn(Icons.Default.Undo, app.undoNames.firstOrNull()?.let { "Undo $it" } ?: "Undo", OS.Primary, app.undoNames.isNotEmpty()) { app.undo() }
        IconBtn(Icons.Default.Redo, "Redo", OS.Primary, app.redoNames.isNotEmpty()) { app.redo() }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = .35f), modifier = Modifier.size(26.dp))
    }
}

/** Toolset buttons + recently used tools. */
@Composable
private fun ToolStrip(app: AppState) {
    Row(Modifier.fillMaxWidth().height(56.dp).background(OS.ToolbarBg).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        app.workspace.toolsets.forEach { ts -> ToolsetButton(app, ts, app.openToolset == ts) { app.openToolset = if (app.openToolset == ts) null else ts } }
        if (app.recent.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(34.dp).background(OS.ToolbarDiv))
            app.recent.forEach { id -> app.catalog.cmd(id)?.let { c -> StripTool(app, c.icon, c.label) { app.run(id) } } }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OS.ToolbarDiv))
}

@Composable
private fun ToolsetButton(app: AppState, ts: ToolsetDef, open: Boolean, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 76.dp).fillMaxHeight().background(if (open) OS.ToolPanel else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // the toolset's own icon, else its first tool's (like a FreeCAD toolbar shows its first button)
        val img = app.catalog.icon(app.catalog.cmd(ts.icon)?.icon ?: ts.icon) ?: ts.groups.firstNotNullOfOrNull { (wb, tb) ->
            app.catalog.wb(wb)?.toolbars?.firstOrNull { it.name == tb }?.let { t -> app.catalog.flatten(t.items).firstNotNullOfOrNull { app.catalog.icon(app.catalog.cmd(it)?.icon) } } }
        if (img != null) Image(img, ts.label, Modifier.size(26.dp)) else Icon(Icons.Default.Build, null, tint = OS.Primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ts.label, fontSize = 10.sp, color = OS.Navy, maxLines = 1, softWrap = false); Icon(Icons.Default.ArrowDropDown, null, tint = OS.Steel, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StripTool(app: AppState, icon: String?, label: String, active: Boolean = false, fallback: ImageVector = Icons.Default.Build, onClick: () -> Unit) {
    Column(Modifier.width(64.dp).fillMaxHeight().background(if (active) OS.Cloud else Color.Transparent).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val img = app.catalog.icon(icon)
        if (img != null) Image(img, label, Modifier.size(26.dp)) else Icon(fallback, null, tint = OS.Primary)
        Text(label, Modifier.padding(horizontal = 2.dp), fontSize = 9.sp, lineHeight = 10.sp, color = OS.Navy, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Sketch-mode strip: sketch toolsets + fixed section (active tool, construction, ✓ / ✗). */
@Composable
private fun SketchStrip(app: AppState) {
    val sk = app.sketch ?: return
    Row(Modifier.fillMaxWidth().height(56.dp).background(OS.ToolbarBg), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Workspaces.sketch.forEach { ts -> ToolsetButton(app, ts, app.openToolset == ts) { app.openToolset = if (app.openToolset == ts) null else ts } }
            Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(34.dp).background(OS.ToolbarDiv))
            // Sketcher_ToggleConstruction: with curves selected it converts them, otherwise it switches the creation mode
            StripTool(app, "Sketcher_ToggleConstruction", if (sk.construction) "Construction: on" else "Construction: off", sk.construction) { sk.toggleConstruction() }
            StripTool(app, null, "Constraints", sk.consList != null, Icons.Default.List) { sk.consList = if (sk.consList == null) "all" else null }
            listOf("Sketcher_CreatePolyline" to SketchTool.POLYLINE, "Sketcher_CreateRectangle" to SketchTool.RECTANGLE, "Sketcher_CreateCircle" to SketchTool.CIRCLE,
                "Sketcher_Create3PointArc" to SketchTool.ARC3, "Sketcher_Dimension" to SketchTool.DIMENSION, "Sketcher_Trimming" to SketchTool.TRIM).forEach { (id, t) ->
                app.catalog.cmd(id)?.let { c -> StripTool(app, c.icon, t.label, sk.tool == t) { sk.selectTool(if (sk.tool == t) SketchTool.SELECT else t) } }
            }
            StripTool(app, null, "Select", sk.tool == SketchTool.SELECT, Icons.Default.NearMe) { sk.selectTool(SketchTool.SELECT) }
            StripTool(app, null, "Delete", fallback = Icons.Default.Delete) { sk.deleteSelected() }
            StripTool(app, null, "Finish", fallback = Icons.Default.Done) { sk.finishShape() }
        }
        Box(Modifier.width(1.dp).height(34.dp).background(OS.ToolbarDiv))
        IconBtn(Icons.Default.Check, "Leave sketch", OS.Green) { app.leaveSketch(true) }
        IconBtn(Icons.Default.Close, "Cancel sketch", OS.Red) { app.leaveSketch(false) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OS.ToolbarDiv))
}

/** Mate creation prompt (FreeCAD TaskAssemblyCreateJoint selection step). */
@Composable
private fun MateBanner(app: AppState, type: String, picked: Int, modifier: Modifier) {
    Row(modifier.shadow(6.dp, RoundedCornerShape(6.dp)).background(OS.Surface, RoundedCornerShape(6.dp)).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        val img = app.catalog.icon(app.catalog.cmd("Assembly_CreateJoint$type")?.icon)
        if (img != null) Image(img, null, Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$type joint", fontWeight = FontWeight.Medium, color = OS.Navy)
            Text("Tap a face, edge or vertex for connector ${picked + 1} of 2", color = OS.Steel, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        IconBtn(Icons.Default.Close, "Cancel", OS.Red) { app.cancelMate() }
    }
}

/** Assembly_InsertLink part list. */
@Composable
private fun PartPicker(app: AppState, parts: List<Pair<String, String>>) {
    AppDialog(onDismissRequest = { app.partPicker = null }, confirmButton = {}, dismissButton = { TextButton({ app.partPicker = null }) { Text("Cancel") } },
        title = { Text("Insert part") },
        text = {
            if (parts.isEmpty()) Text("No parts yet. Model a Body in the Part tab first.")
            else Column { parts.forEach { (n, l) ->
                Row(Modifier.fillMaxWidth().clickable { app.insertPart(n) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val img = app.catalog.icon(app.catalog.cmd("PartDesign_Body")?.icon); if (img != null) Image(img, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp)); Text(l, color = OS.Navy)
                } } }
        })
}

/** Actions for the sketch selection: construction on/off for the selected curves, delete. */
@Composable
private fun SketchSelectionBar(sk: SketchEditor, modifier: Modifier) {
    sk.tick.intValue
    val curves = sk.selectedCurves(); val any = sk.selected.isNotEmpty() || sk.selectedCons.isNotEmpty()
    if (!any) return
    Row(modifier.shadow(6.dp, RoundedCornerShape(24.dp)).background(OS.Surface, RoundedCornerShape(24.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val n = curves.size + sk.selected.count { it.pos != 0 || it.geo < 0 } + sk.selectedCons.size
        Text("$n selected", Modifier.padding(horizontal = 10.dp), color = OS.Steel, fontSize = 13.sp)
        if (curves.isNotEmpty()) {
            val on = sk.selectionIsConstruction()
            Row(Modifier.clickable { sk.toggleConstruction() }.background(if (on) OS.SelBg else Color.Transparent, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Switch(on, { sk.toggleConstruction() }, Modifier.scale(0.7f).height(20.dp))
                Spacer(Modifier.width(4.dp)); Text("Construction", color = OS.Navy, fontSize = 14.sp)
            }
        }
        Row(Modifier.clickable { sk.deleteSelected() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Delete, null, tint = OS.Red, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Delete", color = OS.Red, fontSize = 14.sp)
        }
    }
}

/** The active tool (sketch tool, Draft / TechDraw point picking, mate) with an ✕ that leaves it, like Esc on the desktop. */
@Composable
private fun ToolExit(app: AppState, modifier: Modifier) {
    val sk = app.sketch
    val (label, exit) = when {
        sk != null && sk.tool != SketchTool.SELECT -> sk.tool.label to { sk.selectTool(SketchTool.SELECT) }
        app.draft != null -> (app.draft?.label ?: "Tool") to { app.cancelDraft() }
        app.mate != null -> "Joint" to { app.cancelMate() }
        else -> return
    }
    Row(modifier.shadow(6.dp, RoundedCornerShape(24.dp)).background(OS.Primary, RoundedCornerShape(24.dp)).clickable { exit() }
        .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(28.dp).background(OS.Surface.copy(alpha = 0.22f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "Exit ${label}", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

/** Values that can be typed for the next point of the shape being drawn (FreeCAD 1.x on-view parameters): the point is
 *  placed from them and they become the shape's dimensions. */
@Composable
private fun SketchInputs(sk: SketchEditor, modifier: Modifier) {
    val ins = sk.inputs(); if (ins.isEmpty()) return
    Row(modifier.background(OS.ToolbarBg, RoundedCornerShape(6.dp)).border(1.dp, OS.Metal4, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Type:", color = OS.Steel, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        ins.forEach { n -> OutlinedButton({ sk.enterValue(n) }, Modifier.padding(horizontal = 3.dp).height(34.dp), shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) { Text(n, fontSize = 13.sp, color = OS.Primary) } }
    }
}

@Composable
private fun SketchStatus(text: String, fully: Boolean, bad: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(text + "  ›", modifier.background(if (bad) OS.ErrBg else if (fully) OS.OkBg else OS.ToolbarBg, RoundedCornerShape(4.dp)).clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp), color = if (bad) OS.Red else if (fully) OS.Green else OS.Primary, fontSize = 13.sp)
}

/** Sketcher's constraint list (TaskSketcherConstraints): every constraint with what it ties and its value; conflicting
 *  (red) and redundant (orange) ones flagged; tap selects it in the sketch, the row's buttons edit / switch it off /
 *  make it reference / rename / delete it. */
@Composable
private fun ConstraintList(app: AppState, sk: SketchEditor, modifier: Modifier) {
    sk.tick.intValue
    val filter = sk.consList ?: return
    val all = sk.cons
    val shown = if (filter == "problems") all.filter { sk.isConflicting(it) || sk.isRedundant(it) } else all
    val dims = setOf("Distance", "DistanceX", "DistanceY", "Radius", "Diameter", "Angle", "Weight", "SnellsLaw")
    Column(modifier.then(Screen.panelWidth(330.dp)).heightIn(max = Screen.panelHeight(520.dp)).shadow(8.dp, RoundedCornerShape(6.dp)).background(OS.Surface, RoundedCornerShape(6.dp))) {
        Row(Modifier.fillMaxWidth().height(44.dp).background(OS.Metal5, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Constraints (${all.size})", Modifier.weight(1f), fontWeight = FontWeight.Medium, color = OS.Navy, fontSize = 15.sp)
            IconBtn(Icons.Default.Close, "Close", OS.Steel) { sk.consList = null }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "All", "problems" to "Conflicts / redundant").forEach { (k, l) ->
                Text(l, Modifier.background(if (filter == k) OS.SelBg else Color.Transparent, RoundedCornerShape(14.dp)).border(1.dp, OS.Metal4, RoundedCornerShape(14.dp))
                    .clickable { sk.consList = k }.padding(horizontal = 12.dp, vertical = 5.dp), color = OS.Navy, fontSize = 13.sp) }
        }
        if (shown.isEmpty()) Text(if (filter == "problems") "No conflicting or redundant constraints" else "No constraints",
            Modifier.padding(14.dp), color = OS.Green, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown.size) { idx ->
                val k = shown[idx]
                val conf = sk.isConflicting(k); val red = sk.isRedundant(k); val sel = k.i in sk.selectedCons
                val col = when { conf -> OS.Red; red -> Color(0xFFE08A00); !k.active -> OS.Aluminum; else -> OS.Navy }
                var menu by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.fillMaxWidth().background(if (sel) OS.SelBg else Color.Transparent).clickable { sk.selectConstraint(k) }
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${k.i + 1}", Modifier.width(34.dp), color = OS.Steel, fontSize = 12.sp)
                        Column(Modifier.weight(1f)) {
                            val v = if (k.type in dims) "  " + (if (k.type == "Diameter") "⌀" else if (k.type == "Radius") "R" else "") + k.label else ""
                            Text((k.cname.ifEmpty { k.type.replace(Regex("([a-z])([A-Z])"), "$1 $2") }) + v + (if (!k.driving) "  (reference)" else "") +
                                (if (!k.active) "  (off)" else ""), color = col, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (conf) "Conflicting · " else if (red) "Redundant · " else "") + sk.describe(k), color = if (conf || red) col else OS.Steel,
                                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (k.type in dims) IconBtn(Icons.Default.Edit, "Edit value", OS.Primary) { sk.editDimension(k) }
                        IconBtn(Icons.Default.MoreVert, "More", OS.Steel) { menu = true }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(if (k.active) "Deactivate" else "Activate") }, { menu = false; sk.toggleActive(k) })
                        if (k.type in dims) DropdownMenuItem({ Text(if (k.driving) "Make reference (driven)" else "Make driving") }, { menu = false; sk.toggleDriving(k) })
                        DropdownMenuItem({ Text("Rename") }, { menu = false
                            app.textPrompt = TextPrompt("Name of constraint #${k.i + 1}", k.cname) { t -> sk.renameConstraint(k, t.trim()) } })
                        DropdownMenuItem({ Text("Delete", color = OS.Red) }, { menu = false; sk.deleteConstraint(k) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- feature tree
private fun typeIcon(app: AppState, o: FcObject): String? {
    val id = when {
        o.subtype?.startsWith("Joint:") == true -> "Assembly_CreateJoint" + o.subtype.removePrefix("Joint:")
        o.subtype == "Grounded" -> "Assembly_ToggleGrounded"
        o.type == "App::Link" -> o.linked?.let { l -> app.obj(l)?.let { return typeIcon(app, it) } } ?: "Std_LinkMake"
        else -> when (o.type) { "Sketcher::SketchObject" -> "Sketcher_Sketch"; "PartDesign::Body" -> "PartDesign_Body"; "App::Origin" -> "Std_CoordinateSystem"
            "App::Plane" -> "Std_Plane"; "App::Line" -> "Std_Axis"; "PartDesign::Plane" -> "PartDesign_Plane"; "PartDesign::Line" -> "PartDesign_Line"
            "PartDesign::Point" -> "PartDesign_Point"; "Assembly::AssemblyObject" -> "Assembly_CreateAssembly"; "Assembly::JointGroup" -> "Assembly_CreateJointFixed"
            "TechDraw::DrawPage" -> "TechDraw_PageDefault"; "TechDraw::DrawSVGTemplate" -> "TechDraw_PageTemplate"
            "TechDraw::DrawViewPart" -> "TechDraw_View"; "TechDraw::DrawProjGroup", "TechDraw::DrawProjGroupItem" -> "TechDraw_ProjectionGroup"
            "TechDraw::DrawViewSection" -> "TechDraw_SectionView"; "TechDraw::DrawViewDetail" -> "TechDraw_DetailView"
            "TechDraw::DrawViewDimension" -> "TechDraw_LengthDimension"; "TechDraw::DrawViewBalloon" -> "TechDraw_Balloon"
            "TechDraw::DrawViewAnnotation" -> "TechDraw_Annotation"; "TechDraw::DrawViewImage" -> "TechDraw_Image"
            "TechDraw::DrawHatch" -> "TechDraw_Hatch"; "TechDraw::DrawLeaderLine" -> "TechDraw_LeaderLine"
            "Spreadsheet::Sheet" -> "Spreadsheet_CreateSheet"; "Fem::FemAnalysis" -> "FEM_Analysis"
            "Fem::FemMeshObject", "Fem::FemMeshShapeObject" -> "FEM_MeshNetgenFromShape"; "Fem::FemResultObject" -> "FEM_ResultShow"
            "Fem::FemPostPipeline" -> "FEM_PostPipelineFromResult"; "Mesh::Feature" -> "Mesh_FromPartShape"; "Points::Feature" -> "Points_Import"
            "Part::Box" -> "Part_Box"; "Part::Cylinder" -> "Part_Cylinder"; "Part::Sphere" -> "Part_Sphere"; "Part::Cone" -> "Part_Cone"
            "Part::Torus" -> "Part_Torus"; "Part::Extrusion" -> "Part_Extrude"; "Part::Revolution" -> "Part_Revolve"
            "Part::Fuse", "Part::MultiFuse" -> "Part_Fuse"; "Part::Cut" -> "Part_Cut"; "Part::Common", "Part::MultiCommon" -> "Part_Common"
            "App::DocumentObjectGroup" -> "Std_Group"; "App::Part" -> "Std_Part"
            else -> o.type.replace("::", "_") }
    }
    return app.catalog.cmd(id)?.icon ?: app.catalog.cmd(id.replace("_Draw", "_"))?.icon ?: id
}

private val CONTAINERS = setOf("PartDesign::Body", "Assembly::AssemblyObject", "Assembly::JointGroup", "App::Part", "App::DocumentObjectGroup")

@Composable
private fun FeatureTree(app: AppState, modifier: Modifier, onCollapse: () -> Unit) {
    val objs = app.objects.toList(); val byName = objs.associateBy { it.name }
    // FreeCAD's tree: an object appears under every object claiming it (ViewProvider::claimChildren), roots are the rest
    val claimed = objs.flatMap { it.claims }.toSet()
    // tabs: the assembly lives in the Assembly tab; part objects in the Part tab
    // element tabs: Drawing lists the TechDraw pages and their views, the other tabs never show them
    val roots = objs.filter { it.name !in claimed && ((it.type == "Assembly::AssemblyObject") == app.inAssembly) &&
        (it.type.startsWith("TechDraw::") == app.inDrawing) }
    var rowMenu by remember { mutableStateOf<String?>(null) }
    val collapsed = app.collapsed.toSet(); val expanded = app.expanded.toSet()
    // FreeCAD opens group containers (Body, Part, groups); features with claimed children and the Origin start closed
    fun isOpen(o: FcObject) = o.name !in collapsed && (o.type in CONTAINERS || o.name in expanded)
    val rows = remember(objs, app.inAssembly, app.inDrawing, collapsed, expanded) {
        val out = ArrayList<TreeLine>()
        fun walk(o: FcObject, depth: Int, rolled: Boolean, path: Set<String>) {
            val all = o.claims.mapNotNull { byName[it] }.filter { it.name !in path }
            // a child also claimed by a sibling is shown under that sibling only (the Pad's sketch is not repeated in the Body)
            val kids = all.filter { k -> all.none { other -> other !== k && k.name in other.claims } }
            out.add(TreeLine(o, depth, rolled, kids.isNotEmpty(), isOpen(o)))
            if (kids.isEmpty() || !isOpen(o)) return
            val order = if (o.type == "PartDesign::Body") o.group else emptyList()
            val tipIdx = order.indexOf(o.tip)
            kids.forEach { k -> val gi = order.indexOf(k.name)
                walk(k, depth + 1, rolled || (tipIdx >= 0 && gi > tipIdx), path + o.name) }
        }
        roots.forEach { walk(it, 0, false, emptySet()) }; out
    }
    Column(modifier.background(OS.Surface)) {
        Row(Modifier.fillMaxWidth().height(44.dp).background(OS.Metal5).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (app.inAssembly) "Components & joints" else "Features", Modifier.weight(1f), fontWeight = FontWeight.Medium, color = OS.Navy)
            Text("${rows.size}", color = OS.Steel, fontSize = 12.sp)
            Box(Modifier.size(32.dp).clickable(onClick = onCollapse), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ChevronLeft, "Hide features", tint = OS.Primary, modifier = Modifier.size(22.dp)) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows.size) { i ->
                val line = rows[i]; val o = line.o
                Box(Modifier.alpha(if (line.rolled) 0.4f else 1f)) { TreeRow(app, o, line.depth, rowMenu == o.name, line.hasKids, line.open) { rowMenu = it } }
                // rollback bar after the body's Tip
                val parent = o.parents.firstNotNullOfOrNull { byName[it] }?.takeIf { it.type == "PartDesign::Body" }
                if (parent != null && parent.tip == o.name && parent.group.lastOrNull() != o.name) RollbackBar()
            }
        }
    }
}

/** Rollback bar = PartDesign Tip. */
@Composable
private fun RollbackBar() {
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp).height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(3.dp).background(OS.BlueMed))
        Text(" Tip", fontSize = 10.sp, color = OS.BlueMed)
    }
}

private class TreeLine(val o: FcObject, val depth: Int, val rolled: Boolean, val hasKids: Boolean, val open: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(app: AppState, o: FcObject, depth: Int, menuOpen: Boolean, hasKids: Boolean, open: Boolean, setMenu: (String?) -> Unit) {
    val selected = app.selection.any { it.obj == o.name }
    Box {
        Row(Modifier.fillMaxWidth().height(40.dp).background(if (selected) OS.Cloud else OS.Surface)
            .combinedClickable(onClick = { if (o.type == "TechDraw::DrawPage") { app.page = o.name; app.refreshPage() }
                else if (o.type == "Spreadsheet::Sheet" && app.inSheet) { app.sheet = o.name; app.refreshSheet() } else { app.select(SelRef(o.name), additive = false); app.refresh() } }, onDoubleClick = { app.openEditor(o.name) }, onLongClick = { setMenu(o.name) })
            .padding(start = (4 + depth * 18).dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // FreeCAD's expand arrow
            Box(Modifier.size(24.dp).then(if (hasKids) Modifier.clickable {
                if (open) { app.expanded.remove(o.name); if (o.name !in app.collapsed) app.collapsed.add(o.name) }
                else { app.collapsed.remove(o.name); if (o.name !in app.expanded) app.expanded.add(o.name) } } else Modifier), contentAlignment = Alignment.Center) {
                if (hasKids) Icon(if (open) Icons.Default.ExpandMore else Icons.Default.ChevronRight, if (open) "Collapse" else "Expand", tint = OS.Steel, modifier = Modifier.size(18.dp))
            }
            val img = app.catalog.icon(typeIcon(app, o))
            if (img != null) Image(img, null, Modifier.size(22.dp).alpha(if (o.visible) 1f else 0.4f)) else Icon(Icons.Default.Category, null, tint = OS.Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(o.label, Modifier.weight(1f), color = if (o.invalid) OS.Red else if (o.visible) OS.Navy else OS.Aluminum, fontSize = 14.sp, maxLines = 1)
            if (o.invalid) Icon(Icons.Default.Error, "Error", tint = OS.Red, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(menuOpen, { setMenu(null) }) {
            DropdownMenuItem({ Text("Edit ${o.label}") }, { setMenu(null); app.openEditor(o.name) })
            DropdownMenuItem({ Text("Rename") }, { setMenu(null)
                app.textPrompt = TextPrompt("Rename ${o.label}", o.label) { t -> if (t.isNotBlank())
                    app.step("Rename", { setProperty(o.name, "Label", t.trim()) }) } })
            if (o.parents.any { p -> app.obj(p)?.type == "PartDesign::Body" }) DropdownMenuItem({ Text("Suppress / unsuppress") }, { setMenu(null)
                app.select(SelRef(o.name), additive = false); app.run("Std_ToggleSuppress") })
            // a Part Fillet / Chamfer made on a Body: move it into the Body's history where it was created
            if ((o.type == "Part::Fillet" || o.type == "Part::Chamfer") && o.claims.any { c -> app.obj(c)?.type == "PartDesign::Body" })
                DropdownMenuItem({ Text("Move into Body") }, { setMenu(null)
                    app.step("Move ${o.label} into Body", { call("pd_absorb_dressup", mapOf("name" to o.name)) }) { app.selection.clear() } })
            DropdownMenuItem({ Text("Duplicate") }, { setMenu(null); app.select(SelRef(o.name), additive = false); app.run("Std_DuplicateSelection") })
            DropdownMenuItem({ Text(if (o.visible) "Hide ${o.label}" else "Show ${o.label}") }, { setMenu(null)
                app.eng({ call("set_visibility", mapOf("names" to listOf(o.name), "visible" to !o.visible)) }) { app.refresh() } })
            if (o.parents.any { p -> app.obj(p)?.type == "PartDesign::Body" }) DropdownMenuItem({ Text("Roll tip to here") }, { setMenu(null)
                app.eng({ begin("Set tip"); call("pd_set_tip", mapOf("name" to o.name)); commit() }) { app.refresh() } })
            DropdownMenuItem({ Text("Delete ${o.label}", color = OS.Red) }, { setMenu(null)
                app.eng({ begin("Delete"); call("delete_objects", mapOf("names" to listOf(o.name))); commit() }) { app.selection.clear(); app.refresh() } })
        }
    }
}

// ---------------------------------------------------------------------------------------------- context menu (selection-aware)
/** One entry of the 3D view's context menu: [group] changes where a divider goes, [danger] is drawn in red. */
class ContextEntry(val label: String, val group: Int, val danger: Boolean = false, val action: () -> Unit)

/** The 3D view's context menu for the current selection, in order: the touch screen's dropdown and MobileCAD XR's
 *  marking menu show the same entries. */
fun viewContextEntries(app: AppState, view: ViewportView?): List<ContextEntry> {
    val faces = app.selection.filter { it.sub.startsWith("Face") }; val edges = app.selection.filter { it.sub.startsWith("Edge") }
    val sketches = app.selection.filter { app.obj(it.obj)?.type == "Sketcher::SketchObject" }
    val out = ArrayList<ContextEntry>()
    fun item(label: String, id: String, group: Int) = out.add(ContextEntry(label, group) { app.run(id) })
    if (app.inAssembly) {
        item("Fixed joint", "Assembly_CreateJointFixed", 0); item("Revolute joint", "Assembly_CreateJointRevolute", 0)
        item("Slider joint", "Assembly_CreateJointSlider", 0); item("Insert part…", "Assembly_InsertLink", 0)
        if (app.selection.isNotEmpty()) item("Toggle grounded", "Assembly_ToggleGrounded", 0)
        item("Solve assembly", "Assembly_SolveAssembly", 0)
    }
    if (faces.size == 1 && !app.inAssembly) { item("Sketch", "PartDesign_NewSketch", 1); item("Extrude (Pad)", "PartDesign_Pad", 1); item("Plane", "PartDesign_Plane", 1) }
    if (sketches.isNotEmpty()) { item("Extrude (Pad)", "PartDesign_Pad", 1); item("Revolve", "PartDesign_Revolution", 1); item("Pocket", "PartDesign_Pocket", 1); item("Edit sketch", "Sketcher_EditSketch", 1) }
    if (edges.isNotEmpty()) { item("Fillet", "PartDesign_Fillet", 1); item("Chamfer", "PartDesign_Chamfer", 1) }
    if (app.selection.isNotEmpty()) {
        item("Hide / show", "Std_ToggleVisibility", 2)
        out.add(ContextEntry("Delete ${app.selection.map { it.obj }.distinct().size}", 2, danger = true) { app.run("Std_Delete") })
        out.add(ContextEntry("Clear selection", 2) { app.clearSelection(); app.refresh() })
    }
    out.add(ContextEntry("Zoom to fit", 3) { view?.fitAll() })
    out.add(ContextEntry("Isometric", 3) { view?.setView(-0.785f, 0.6155f) })
    out.add(ContextEntry("Top", 3) { view?.setView(-1.5708f, 1.5f) })
    out.add(ContextEntry("Front", 3) { view?.setView(-1.5708f, 0f) })
    out.add(ContextEntry("Right", 3) { view?.setView(0f, 0f) })
    return out
}

@Composable
private fun ContextMenu(app: AppState, view: ViewportView?, dismiss: () -> Unit) {
    val entries = viewContextEntries(app, view)
    DropdownMenu(true, dismiss) {
        entries.forEachIndexed { i, e ->
            if (i > 0 && entries[i - 1].group != e.group) HorizontalDivider()
            DropdownMenuItem({ Text(e.label, color = if (e.danger) OS.Red else Color.Unspecified) }, { dismiss(); e.action() })
        }
    }
}

// ---------------------------------------------------------------------------------------------- selection chips
/** Current selection as FreeCAD sub-element names (replaces a side info panel). */
@Composable
private fun SelectionChips(app: AppState, modifier: Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        app.selection.take(6).forEach { s ->
            Row(Modifier.padding(end = 6.dp).background(OS.Surface.copy(alpha = .94f), RoundedCornerShape(14.dp)).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${app.obj(s.obj)?.label ?: s.obj}${if (s.sub.isNotEmpty()) "." + s.sub else ""}", color = OS.Navy, fontSize = 12.sp)
                Box(Modifier.size(28.dp).clickable { app.selection.remove(s); app.refresh() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "Deselect", tint = OS.Steel, modifier = Modifier.size(14.dp)) }
            }
        }
        if (app.selection.size > 6) Text("+${app.selection.size - 6}", color = OS.Steel, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------------------------- bottom element tabs (workbenches)
@Composable
private fun ElementTabs(app: AppState) {
    val tabs = remember { Workspaces.main(app.catalog) }
    var more by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(40.dp).background(OS.TabInactive)) {
        tabs.forEach { w -> Tab(w.label, app.workspace.id == w.id) { app.switchWorkspace(w.id) } }
        Box {
            // the active "More" workbench names the tab (BIM ▾, FEM ▾ ...)
            val moreActive = Workspaces.more.any { it.first == app.workspace.id }
            Tab(if (moreActive) "${app.workspace.label} ▾" else "More ▾", moreActive) { more = true }
            DropdownMenu(more, { more = false }) {
                Workspaces.more.forEach { (id, label) ->
                    DropdownMenuItem({ Text(label) }, { more = false; app.switchWorkspace(id) })
                }
            }
        }
    }
}

@Composable
private fun Tab(label: String, active: Boolean, onClick: () -> Unit) {
    // tablets: 150dp tabs; phones: the tabs share the width
    Box(Modifier.width(minOf(150.dp, Screen.width / 5)).fillMaxHeight().background(if (active) OS.Surface else OS.TabInactive).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) OS.BlueMed else OS.TabDivider))
        Text(label, Modifier.align(Alignment.Center).padding(horizontal = 2.dp), color = if (active) OS.Navy else OS.Steel, fontSize = if (Screen.compact) 12.sp else 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(OS.TabDivider))
}


/** Empty tab: what is missing and a button for the first step. */
@Composable
fun EmptyState(text: String, action: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.background(OS.Surface.copy(alpha = .9f), RoundedCornerShape(8.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = OS.Steel, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = OS.Primary)) { Text(action) }
    }
}
