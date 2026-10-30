package app.mobilecad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.MobileCommands
import app.mobilecad.freecad.ToolsetDef
import app.mobilecad.freecad.Workspaces

/** MobileCAD XR's context panel: what fills the window's panel area while no dialog, task panel or tool menu is open,
 *  following what you are doing - what is selected and the tools that apply to it (the context menu's), or your recent
 *  tools with nothing selected; in a sketch, the sketch's tools. Under that, every tool of the workspace, so any tool is
 *  one click away (desktop's toolbars). */
@Composable
fun XrContextPanel(app: AppState, modifier: Modifier = Modifier) {
    val sk = app.sketch
    val sel = app.selection.toList()
    val objs = sel.map { it.obj }.distinct().mapNotNull { app.obj(it) }
    val faces = sel.filter { it.sub.startsWith("Face") }; val edges = sel.filter { it.sub.startsWith("Edge") }
    val sketches = objs.filter { it.type == "Sketcher::SketchObject" }
    // what you are doing, and the tools for it (as the context menu)
    val title: String; val hint: String; val actions = mutableListOf<String>()
    when {
        sk != null -> { title = "Editing ${app.obj(sk.name)?.label ?: sk.name}"; hint = "Draw in the room with the trigger; the sketch's tools are below" }
        sel.isEmpty() -> {
            title = "Nothing selected"; hint = "Point at a face, an edge or a feature and pull the trigger, or start with a tool"
            actions += app.recent
        }
        else -> {
            title = if (objs.size == 1) objs[0].label + sel.filter { it.obj == objs[0].name && it.sub.isNotEmpty() }.let { s ->
                if (s.isEmpty()) "" else ": " + s.take(4).joinToString(", ") { it.sub } + if (s.size > 4) " +${s.size - 4}" else "" }
                else "${objs.size} objects selected"
            hint = "Tools for the selection"
            if (app.inAssembly) actions += listOf("Assembly_CreateJointFixed", "Assembly_CreateJointRevolute", "Assembly_CreateJointSlider", "Assembly_ToggleGrounded")
            if (faces.size == 1 && !app.inAssembly) actions += listOf("PartDesign_NewSketch", "PartDesign_Pad", "PartDesign_Pocket", "PartDesign_Plane")
            if (sketches.isNotEmpty()) actions += listOf("PartDesign_Pad", "PartDesign_Pocket", "PartDesign_Revolution", "PartDesign_Groove", "Sketcher_EditSketch")
            if (edges.isNotEmpty()) actions += listOf("PartDesign_Fillet", "PartDesign_Chamfer")
            if (faces.isNotEmpty() && edges.isEmpty() && !app.inAssembly) actions += listOf("PartDesign_Thickness", "PartDesign_Draft")
            actions += listOf("Std_ToggleVisibility", "Std_Delete")
        }
    }
    val tools = remember(app.workspace, sk != null) {
        if (sk != null) ToolsetDef("Sketch", "", Workspaces.sketch.flatMap { it.groups }.distinct())
        else ToolsetDef("Tools", "", app.workspace.toolsets.flatMap { it.groups }.distinct())
    }
    Column(modifier) {
        Column(Modifier.fillMaxWidth().background(OS.Metal6, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = OS.Navy, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                objs.singleOrNull()?.takeIf { sk == null }?.let { o -> PanelButton("Edit") { app.openEditor(o.name) } }
                if (sel.isNotEmpty() && sk == null) PanelButton("Clear") { app.clearSelection(); app.refresh() }
            }
            Text(hint, color = OS.Steel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val cmds = actions.distinct().filter { MobileCommands.available(it) }.mapNotNull { app.catalog.cmd(it) }
            if (cmds.isNotEmpty()) {
                if (sel.isEmpty()) Text("Recent", Modifier.padding(top = 6.dp), color = OS.Steel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp)) { items(cmds, key = { it.id }) { c -> ToolTile(app.catalog, c, true) { app.run(c.id) } } }
            }
        }
        Spacer(Modifier.height(6.dp))
        ToolPanel(app.catalog, tools, { MobileCommands.available(it) }, { app.run(it.id) }, Modifier.weight(1f).fillMaxWidth(), columns = 7)
    }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit) =
    Text(label, Modifier.padding(start = 6.dp).background(OS.Surface, RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        color = OS.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
