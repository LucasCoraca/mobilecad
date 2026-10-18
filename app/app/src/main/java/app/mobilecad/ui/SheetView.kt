package app.mobilecad.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.TextPrompt
import org.json.JSONObject

private fun colName(n: Int): String { var i = n; var s = ""; while (true) { s = ('A' + i % 26) + s; i = i / 26 - 1; if (i < 0) return s } }

/** Spreadsheet grid (SheetTableView): tap selects, long-press starts a range, the formula bar edits the cell. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetView(app: AppState, modifier: Modifier = Modifier) {
    LaunchedEffect(app.workspace.id) { app.refreshSheet() }
    val d = app.sheetData
    Column(modifier.background(OS.Surface)) {
        if (d == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (app.sheet == null) EmptyState("No spreadsheet yet", "Create spreadsheet") { app.run("Spreadsheet_CreateSheet") }
                else Text("Loading…", color = OS.Steel) }
            return@Column
        }
        val cells = d.getJSONObject("cells")
        var anchor by remember { mutableStateOf<String?>(null) }
        val selFirst = app.sheetSel.substringBefore(':')
        val cur = cells.optJSONObject(selFirst)
        // formula bar
        Row(Modifier.fillMaxWidth().height(44.dp).background(OS.Metal5).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(app.sheetSel + (cur?.optString("alias")?.takeIf { it.isNotEmpty() }?.let { "  ($it)" } ?: ""), Modifier.width(150.dp), color = OS.Navy, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(cur?.optString("content") ?: "", Modifier.weight(1f), color = OS.Navy, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton({ val sh = app.sheet ?: return@TextButton
                app.textPrompt = TextPrompt("Cell $selFirst  (text, number with unit, or =expression)", cur?.optString("content")?.removePrefix("'") ?: "") { t ->
                    app.step("Edit cell", { call("sheet_set", mapOf("name" to sh, "cell" to selFirst, "text" to t)) }) } }) { Text("Edit") }
        }
        val rows = d.optInt("rows", 40); val cols = d.optInt("cols", 12)
        val widths = d.optJSONObject("widths") ?: JSONObject()
        fun w(c: Int) = (widths.optInt(colName(c), 100) * 0.8f).dp
        val hs = rememberScrollState()
        val inRange: (Int, Int) -> Boolean = { c, r -> val p = app.sheetSel.split(":")
            fun rc(x: String) = Regex("([A-Z]+)(\\d+)").find(x)!!.destructured.let { (cc, rr) -> cc.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1 to rr.toInt() - 1 }
            val (c0, r0) = rc(p[0]); val (c1, r1) = rc(p.getOrElse(1) { p[0] })
            c in minOf(c0, c1)..maxOf(c0, c1) && r in minOf(r0, r1)..maxOf(r0, r1) }
        Row(Modifier.horizontalScroll(hs)) {
            Spacer(Modifier.width(44.dp))
            for (c in 0 until cols) Text(colName(c), Modifier.width(w(c)).height(28.dp).background(OS.Metal5).border(0.5.dp, OS.Metal4).padding(top = 6.dp),
                textAlign = TextAlign.Center, color = OS.Steel, fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { r ->
                Row(Modifier.horizontalScroll(hs)) {
                    Text("${r + 1}", Modifier.width(44.dp).height(32.dp).background(OS.Metal5).border(0.5.dp, OS.Metal4).padding(top = 8.dp),
                        textAlign = TextAlign.Center, color = OS.Steel, fontSize = 12.sp)
                    for (c in 0 until cols) {
                        val name = colName(c) + (r + 1); val cell = cells.optJSONObject(name); val sel = inRange(c, r)
                        val style = cell?.optJSONArray("style")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        val align = cell?.optJSONArray("align")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        val bg = cell?.optJSONArray("bg")?.takeIf { it.length() >= 3 }?.let { Color(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) }
                        val fg = cell?.optJSONArray("fg")?.takeIf { it.length() >= 3 }?.let { Color(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) }
                        Box(Modifier.width(w(c)).height(32.dp).background(if (sel) OS.SelBg else bg ?: OS.Surface)
                            .border(if (sel) 1.5.dp else 0.5.dp, if (sel) OS.Primary else OS.Metal4)
                            .combinedClickable(onClick = { app.sheetSel = anchor?.let { "$it:$name" } ?: name; anchor = null },
                                onLongClick = { anchor = name; app.sheetSel = name; app.message = "Range from $name — tap the opposite corner" },
                                onDoubleClick = { app.sheetSel = name; val sh = app.sheet ?: return@combinedClickable
                                    app.textPrompt = TextPrompt("Cell $name", cell?.optString("content")?.removePrefix("'") ?: "") { t ->
                                        app.step("Edit cell", { call("sheet_set", mapOf("name" to sh, "cell" to name, "text" to t)) }) } })
                            .padding(horizontal = 4.dp),
                            contentAlignment = when { "center" in align -> Alignment.Center; "right" in align -> Alignment.CenterEnd; else -> Alignment.CenterStart }) {
                            Text(cell?.optString("value") ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = fg ?: OS.Navy,
                                fontWeight = if ("bold" in style) FontWeight.Bold else null, fontStyle = if ("italic" in style) FontStyle.Italic else null,
                                textDecoration = if ("underline" in style) TextDecoration.Underline else null)
                        }
                    }
                }
            }
        }
    }
}
