package app.mobilecad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.GuiCommands
import app.mobilecad.freecad.NumpadRequest
import app.mobilecad.freecad.TextPrompt
import org.json.JSONArray
import org.json.JSONObject

/**
 * A FreeCAD task panel / dialog of a Python command (BIM, CAM, FEM, OpenSCAD ...), rendered from the widget tree the
 * engine's Qt emulation built (mobilecad.taskpanel): group boxes become sections, labelled spin boxes / quantity fields
 * open the numpad, combos drop down, lists select by tap.  Every edit goes back as the widget's own signal, so the
 * panel's Python logic runs exactly as on the desktop; OK / Cancel / Apply are the task dialog's standard buttons.
 */
@Composable
fun TaskPanelView(app: AppState, panel: JSONObject, modifier: Modifier = Modifier) {
    var collapsed by remember(panel.optInt("id")) { mutableStateOf(false) }
    // selection made while the panel is open goes to its Gui.Selection observers (reference pickers)
    val selKey = app.selection.toList()
    LaunchedEffect(selKey) { if (selKey.isNotEmpty()) GuiCommands.panelSelection(app) }
    val ev: (Int, String, Any?) -> Unit = { id, kind, v -> GuiCommands.panelEvent(app, id, kind, v) }
    // QFileDialog: the system file picker / "save as" answers it
    panel.optJSONObject("file")?.let { f ->
        LaunchedEffect(panel.optInt("id")) {
            app.fileRequest = AppState.FileRequest(if (f.optString("mode") == "save") "panelsave" else "panelopen", suggested = f.optString("name").ifEmpty { "file" }) }
        return
    }
    Column(modifier.then(Screen.panelWidth(330.dp)).shadow(8.dp, RoundedCornerShape(6.dp)).background(OS.Surface, RoundedCornerShape(6.dp))) {
        Row(Modifier.fillMaxWidth().height(44.dp).background(OS.Metal5, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).clickable { collapsed = !collapsed }.padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = OS.Steel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(panel.optString("title"), Modifier.weight(1f), fontWeight = FontWeight.Medium, color = OS.Navy, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!collapsed) {
            Column(Modifier.heightIn(max = Screen.panelHeight(460.dp)).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                val forms = panel.optJSONArray("forms") ?: JSONArray()
                for (i in 0 until forms.length()) forms.getJSONObject(i).let { f ->
                    if (forms.length() > 1 && f.optString("title").isNotEmpty()) SectionTitle(f.optString("title"))
                    Nodes(app, f.optJSONArray("kids"), ev)
                }
            }
            val buttons = panel.optJSONArray("buttons") ?: JSONArray()
            Row(Modifier.fillMaxWidth().background(OS.Metal6).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.End) {
                for (i in 0 until buttons.length()) { val b = buttons.getString(i)
                    TextButton({ GuiCommands.panelButton(app, b) }) {
                        Text(panel.optJSONObject("labels")?.optString(b)?.takeIf { it.isNotEmpty() } ?: when (b) { "ok" -> "OK"; "cancel" -> "Cancel"; "apply" -> "Apply"; "close" -> "Close"; "yes" -> "Yes"; "no" -> "No"; "reset" -> "Reset"; else -> b.replaceFirstChar { it.uppercase() } },
                            color = if (b in setOf("cancel", "no")) OS.Red else OS.Primary, fontWeight = FontWeight.Medium) } }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), color = OS.Navy, fontWeight = FontWeight.Medium, fontSize = 14.sp)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Nodes(app: AppState, kids: JSONArray?, ev: (Int, String, Any?) -> Unit) {
    if (kids == null) return
    var i = 0
    while (i < kids.length()) {
        val n = kids.getJSONObject(i)
        if (n.optString("t") == "button") {            // a run of push buttons / actions: one wrapping row of chips
            val run = mutableListOf<JSONObject>()
            while (i < kids.length() && kids.getJSONObject(i).optString("t") == "button") { run += kids.getJSONObject(i); i++ }
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                run.forEach { b -> val on = b.optBoolean("checkable") && b.optBoolean("value"); val en = b.optBoolean("enabled", true)
                    Text(b.optString("text"), Modifier.padding(vertical = 3.dp).border(1.dp, if (on) OS.Primary else OS.Metal4, RoundedCornerShape(16.dp))
                        .background(if (on) OS.SelBg else OS.Surface, RoundedCornerShape(16.dp))
                        .clickable(enabled = en) { ev(b.optInt("id"), "set", if (b.optBoolean("checkable")) !b.optBoolean("value") else null) }
                        .padding(horizontal = 12.dp, vertical = 7.dp), color = if (en) OS.Primary else OS.Aluminum, fontSize = 13.sp, maxLines = 1) }
            }
            continue
        }
        Node(app, n, ev); i++
    }
}

private fun num(v: Double, dec: Int = 6): String = if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString() else "%.${dec}f".format(v).trimEnd('0').trimEnd('.')

@Composable
private fun Field(label: String, enabled: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label, Modifier.weight(1f).padding(end = 6.dp), color = if (enabled) OS.Steel else OS.Aluminum, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        content()
    }
}

@Composable
private fun ValueBox(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.widthIn(min = 120.dp, max = 190.dp).height(36.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(text, color = if (enabled) OS.Navy else OS.Aluminum, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Node(app: AppState, n: JSONObject, ev: (Int, String, Any?) -> Unit) {
    val id = n.optInt("id"); val en = n.optBoolean("enabled", true); val label = n.optString("label")
    when (n.optString("t")) {
        "label" -> Text(n.optString("text"), Modifier.fillMaxWidth().padding(vertical = 4.dp), color = OS.Steel, fontSize = 13.sp)
        "group" -> {
            val title = n.optString("title")
            if (title.isNotEmpty() || n.optBoolean("checkable")) Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.optBoolean("checkable")) Checkbox(n.optBoolean("value"), { ev(id, "set", it) }, enabled = en)
                SectionTitle(title)
            }
            if (!n.optBoolean("checkable") || n.optBoolean("value"))
                Column(Modifier.padding(start = if (title.isNotEmpty()) 6.dp else 0.dp)) { Nodes(app, n.optJSONArray("kids"), ev) }
        }
        "tabs" -> {
            val tabs = n.optJSONArray("tabs") ?: JSONArray(); val cur = n.optInt("value")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                for (i in 0 until tabs.length()) { val sel = i == cur
                    Text(tabs.getJSONObject(i).optString("title").ifEmpty { "Tab ${i + 1}" }, Modifier.padding(end = 4.dp).background(if (sel) OS.Primary else OS.Metal5, RoundedCornerShape(14.dp))
                        .clickable { ev(id, "tab", i) }.padding(horizontal = 12.dp, vertical = 6.dp), color = if (sel) Color.White else OS.Navy, fontSize = 13.sp) }
            }
            if (cur in 0 until tabs.length()) Nodes(app, tabs.getJSONObject(cur).optJSONArray("kids"), ev)
        }
        "qty", "float", "int" -> {
            val v = n.optDouble("value", 0.0); val unit = n.optString("unit").ifEmpty { n.optString("suffix") }
            val t = n.optString("t")
            Field(label, en) { ValueBox(if (t == "int") v.toLong().toString() + (if (unit.isNotEmpty()) " $unit" else "") else num(v, n.optInt("dec", 6)) + (if (unit.isNotEmpty()) " $unit" else ""), en) {
                app.numpad = NumpadRequest(label.ifEmpty { n.optString("name") }, num(v), if (t == "qty" && unit.isNotEmpty()) listOf(unit) else emptyList()) { s ->
                    app.numpad = null
                    val txt = s.trim()
                    when (t) { "qty" -> ev(id, "set", if (txt.any { it.isLetter() }) txt else "$txt $unit".trim())
                        "int" -> txt.toDoubleOrNull()?.let { ev(id, "set", it.toLong()) }
                        else -> txt.toDoubleOrNull()?.let { ev(id, "set", it) } }
                } } }
        }
        "line", "text" -> Field(label, en) { ValueBox(n.optString("value").ifEmpty { " " }, en && !n.optBoolean("ro")) {
            app.textPrompt = TextPrompt(label.ifEmpty { n.optString("name") }, n.optString("value")) { s -> ev(id, "set", s) } } }
        "check" -> Row(Modifier.fillMaxWidth().clickable(enabled = en) { ev(id, "set", !n.optBoolean("value")) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(n.optBoolean("value"), { ev(id, "set", it) }, enabled = en); Text(n.optString("text"), color = OS.Navy, fontSize = 14.sp) }
        "radio" -> Row(Modifier.fillMaxWidth().clickable(enabled = en) { ev(id, "set", true) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(n.optBoolean("value"), { ev(id, "set", true) }, enabled = en); Text(n.optString("text"), color = OS.Navy, fontSize = 14.sp) }
        "button" -> OutlinedButton({ ev(id, "set", if (n.optBoolean("checkable")) !n.optBoolean("value") else null) }, enabled = en,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(4.dp),
            colors = if (n.optBoolean("checkable") && n.optBoolean("value")) ButtonDefaults.outlinedButtonColors(containerColor = OS.SelBg) else ButtonDefaults.outlinedButtonColors()) {
            Text(n.optString("text"), color = OS.Primary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        "combo" -> {
            val items = n.optJSONArray("items") ?: JSONArray(); val cur = n.optInt("value", -1); var open by remember { mutableStateOf(false) }
            Field(label, en) { Box {
                Row(Modifier.widthIn(min = 120.dp, max = 190.dp).height(36.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).clickable(enabled = en) { open = true }.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (cur in 0 until items.length()) items.getString(cur) else "", Modifier.weight(1f, fill = false), color = OS.Navy, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, null, tint = OS.Steel) }
                DropdownMenu(open, { open = false }) { for (i in 0 until items.length()) DropdownMenuItem({ Text(items.getString(i)) }, { open = false; ev(id, "set", i) }) }
            } }
        }
        "list" -> {
            val items = n.optJSONArray("items") ?: JSONArray()
            if (label.isNotEmpty()) Text(label, Modifier.padding(top = 6.dp), color = OS.Steel, fontSize = 13.sp)
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).verticalScroll(rememberScrollState())) {
                if (items.length() == 0) Text("(empty)", Modifier.padding(8.dp), color = OS.Aluminum, fontSize = 13.sp)
                for (i in 0 until items.length()) { val it = items.getJSONObject(i); val sel = it.optBoolean("sel")
                    Row(Modifier.fillMaxWidth().background(if (sel) OS.SelBg else Color.Transparent).clickable(enabled = en) { ev(id, "set", i) }.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (!it.isNull("check")) Checkbox(it.optInt("check") != 0, { on -> ev(id, "check", listOf(i, on)) }, enabled = en)
                        Text(it.optString("text"), Modifier.padding(vertical = 8.dp), color = OS.Navy, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } }
            }
        }
        // QTreeView / QTableView over a QStandardItemModel: tap a row to select it, check boxes, tap an editable cell to edit
        "grid" -> {
            val cols = n.optJSONArray("cols") ?: JSONArray(); val rows = n.optJSONArray("rows") ?: JSONArray()
            if (label.isNotEmpty()) Text(label, Modifier.padding(top = 6.dp), color = OS.Steel, fontSize = 13.sp)
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).horizontalScroll(rememberScrollState())) {
                if (cols.length() > 0 && (0 until cols.length()).any { cols.optString(it).isNotEmpty() })
                    Row { for (c in 0 until cols.length()) Text(cols.optString(c), Modifier.width(110.dp).background(OS.Metal5).padding(4.dp), color = OS.Steel, fontSize = 12.sp, maxLines = 1) }
                if (rows.length() == 0) Text("(empty)", Modifier.padding(8.dp), color = OS.Aluminum, fontSize = 13.sp)
                var top = -1
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (r in 0 until rows.length()) { val row = rows.getJSONObject(r); val isTop = row.optBoolean("top", true); if (isTop) top++
                        val ri = top; val cells = row.optJSONArray("cells") ?: JSONArray()
                        Row(Modifier.background(if (row.optBoolean("sel")) OS.SelBg else Color.Transparent).clickable(enabled = en && isTop) { ev(id, "set", ri) },
                            verticalAlignment = Alignment.CenterVertically) {
                            for (c in 0 until cells.length()) { val cl = cells.getJSONObject(c)
                                Row(Modifier.width(110.dp).heightIn(min = 36.dp).border(0.5.dp, OS.Metal4).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (cl.has("check")) Checkbox(cl.optInt("check") != 0, { on -> ev(id, "check", listOf(ri, on, c)) }, enabled = en && isTop, modifier = Modifier.size(32.dp))
                                    Text(cl.optString("text").ifEmpty { " " }, Modifier.weight(1f).then(if (cl.optBoolean("edit") && isTop && en) Modifier.clickable {
                                        app.textPrompt = TextPrompt("${cols.optString(c).ifEmpty { "Value" }} (row ${ri + 1})", cl.optString("text")) { s -> ev(id, "cell", listOf(ri, c, s)) } } else Modifier),
                                        color = OS.Navy, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } }
                        } }
                }
            }
        }
        "table" -> {
            val cols = n.optJSONArray("cols") ?: JSONArray(); val cells = n.optJSONArray("cells") ?: JSONArray()
            if (label.isNotEmpty()) Text(label, Modifier.padding(top = 6.dp), color = OS.Steel, fontSize = 13.sp)
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).border(1.dp, OS.Metal4)) {
                Row { for (c in 0 until cols.length()) Text(cols.optString(c), Modifier.width(110.dp).background(OS.Metal5).padding(4.dp), color = OS.Steel, fontSize = 12.sp, maxLines = 1) }
                for (r in 0 until cells.length()) Row { val row = cells.getJSONArray(r)
                    for (c in 0 until row.length()) Text(row.optString(c).ifEmpty { " " }, Modifier.width(110.dp).border(0.5.dp, OS.Metal4).clickable(enabled = en) {
                        app.textPrompt = TextPrompt("${cols.optString(c)} (row ${r + 1})", row.optString(c)) { s -> ev(id, "cell", listOf(r, c, s)) } }.padding(4.dp),
                        color = OS.Navy, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        else -> {}
    }
}
