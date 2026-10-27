package app.mobilecad.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.Catalog
import app.mobilecad.freecad.FcCommand
import app.mobilecad.freecad.ToolsetDef
import app.mobilecad.freecad.GuiCommands

/** Tool panel: 5-column grid of a toolset's tools, sectioned by FreeCAD toolbar, searchable across every command. */
@Composable
fun ToolPanel(cat: Catalog, toolset: ToolsetDef, isAvailable: (String) -> Boolean, onPick: (FcCommand) -> Unit, modifier: Modifier = Modifier,
              extras: List<String> = emptyList()) {
    var query by remember(toolset) { mutableStateOf("") }
    val sections: List<Pair<String, List<FcCommand>>> = remember(toolset, query, extras) {
        if (query.isNotBlank()) cat.search(query).groupBy { it.workbench }.map { (wb, l) -> wb to l }
        else toolset.groups.mapNotNull { (wb, tbName) ->
            val t = cat.wb(wb)?.toolbars?.firstOrNull { it.name == tbName } ?: return@mapNotNull null
            prettify(wb, t.name) to cat.flatten(t.items).mapNotNull { cat.cmd(it) }
        } + listOfNotNull(extras.filter(isAvailable).mapNotNull { cat.cmd(it) }.takeIf { it.isNotEmpty() }?.let { "More tools" to it })
    }
    Column(modifier.then(Screen.panelWidth(412.dp)).heightIn(max = Screen.panelHeight(440.dp)).shadow(8.dp, RoundedCornerShape(6.dp)).background(OS.Surface, RoundedCornerShape(6.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp).border(1.dp, OS.Metal4, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = OS.Steel, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search all tools", color = OS.Aluminum, fontSize = 14.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = OS.Navy), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(8.dp))
        // "More tools" (commands of this workbench outside its toolbars) folds away so the toolset stays readable
        var moreOpen by remember(toolset) { mutableStateOf(false) }
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxWidth()) {
            sections.forEach { (title, cmds) ->
                val foldable = title == "More tools" && query.isBlank()
                item(span = { GridItemSpan(5) }) {
                    Row(Modifier.fillMaxWidth().then(if (foldable) Modifier.clickable { moreOpen = !moreOpen } else Modifier).padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (foldable) "$title (${cmds.size})" else title, Modifier.weight(1f), color = if (foldable) OS.Primary else OS.Steel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (foldable) Icon(if (moreOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OS.Primary, modifier = Modifier.size(18.dp))
                    }
                }
                if (!foldable || moreOpen) items(cmds, key = { title + it.id }) { c -> ToolTile(cat, c, isAvailable(c.id)) { onPick(c) } }
            }
        }
    }
}

@Composable
fun ToolTile(cat: Catalog, c: FcCommand, available: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(78.dp).height(80.dp).clickable(onClick = onClick).alpha(if (available) 1f else 0.38f).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // group commands without their own pixmap show their first member's icon, as FreeCAD's toolbar button does
        val img = cat.icon(c.icon) ?: (c.group.ifEmpty { GuiCommands.groups[c.id] ?: emptyList() }).firstNotNullOfOrNull { cat.icon(cat.cmd(it)?.icon) }
        if (img != null) {
            Image(img, c.label, Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(c.label, fontSize = 10.sp, lineHeight = 12.sp, color = OS.Navy, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // a command without a pixmap is a text button, as in FreeCAD's toolbars
            Text(c.label, fontSize = 11.sp, lineHeight = 13.sp, color = OS.Navy, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun prettify(wb: String, toolbar: String): String = toolbar.removePrefix("Part Design ").removePrefix("$wb ").let { if (it == "Sketcher") "Sketch" else it }
