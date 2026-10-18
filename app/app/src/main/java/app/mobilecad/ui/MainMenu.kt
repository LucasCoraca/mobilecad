package app.mobilecad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.MobileCommands
import app.mobilecad.freecad.StdCommands

/** A menu entry: a command id, a submenu, or a separator ("-"). */
sealed class MenuNode { class Cmd(val id: String) : MenuNode(); class Sub(val title: String, val items: List<MenuNode>) : MenuNode(); object Sep : MenuNode() }

/** FreeCAD's menu bar (src/Gui/Workbench.cpp StdWorkbench::setupMenuBar), without the desktop-window entries. */
object MainMenu {
    private fun m(vararg ids: String): List<MenuNode> = ids.map { if (it == "-") MenuNode.Sep else MenuNode.Cmd(it) }
    private fun sub(t: String, vararg n: Any): MenuNode.Sub = MenuNode.Sub(t, n.flatMap { if (it is MenuNode) listOf(it) else m(it as String) })

    val bar: List<MenuNode.Sub> = listOf(
        sub("File", "Std_New", "Std_Open", "Std_RecentFiles", "-", "Std_Save", "Std_SaveAs", "Std_SaveCopy", "Std_SaveAll", "Std_Revert", "-",
            "Std_Import", "Std_Export", "Std_MergeProjects", "Std_ProjectInfo", "-", "Std_Print", "Std_PrintPreview", "Std_PrintPdf", "-", "Std_Quit"),
        sub("Edit", "Std_Undo", "Std_Redo", "-", "Std_Cut", "Std_Copy", "Std_Paste", "Std_DuplicateSelection", "Std_Delete", "-", "Std_Refresh", "Std_Recompute",
            "Std_BoxSelection", "Std_BoxElementSelection", "Std_SelectAll", "-", "Std_TransformManip", "Std_Transform", "Std_Placement", "Std_Alignment",
            "Std_SendToPythonConsole", "Std_Properties", "-", "Std_Edit", "-",
            sub("Create", "Std_Part", "Std_Group", "Std_VarSet", "Std_AnnotationLabel", "Std_LinkMake", "Std_LinkMakeRelative", "Std_LinkMakeGroup",
                "Std_LinkReplace", "Std_LinkUnlink", "Std_LinkImport", "Std_LinkImportAll", "-", "Std_TextDocument"),
            "-", "Std_DlgPreferences"),
        sub("View", "Std_OrthographicCamera", "Std_PerspectiveCamera", "Std_MainFullscreen", "Std_ViewFullscreen", "-",
            sub("Standard Views", "Std_ViewFitAll", "Std_ViewFitSelection", "Std_AlignToSelection",
                sub("Axonometric", "Std_ViewIsometric", "Std_ViewDimetric", "Std_ViewTrimetric"), "-",
                "Std_ViewHome", "Std_ViewFront", "Std_ViewTop", "Std_ViewRight", "Std_ViewRear", "Std_ViewBottom", "Std_ViewLeft", "-",
                "Std_ViewRotateLeft", "Std_ViewRotateRight", "-", "Std_StoreWorkingView", "Std_RecallWorkingView", "Std_ViewSaveCamera", "Std_ViewRestoreCamera"),
            "Std_FreezeViews", "Std_DrawStyle", "Std_SelBoundingBox", "-",
            sub("Zoom", "Std_ViewZoomIn", "Std_ViewZoomOut", "-", "Std_ViewBoxZoom"),
            "Std_AxisCross", "Std_ToggleClipPlane", "Std_TextureMapping", "-",
            sub("Visibility", "Std_ToggleVisibility", "Std_ShowSelection", "Std_HideSelection", "Std_SelectVisibleObjects", "-",
                "Std_ToggleObjects", "Std_ShowObjects", "Std_HideObjects", "-", "Std_ToggleSelectability"),
            "Std_RandomColor", "Std_ToggleTransparency", "Std_SetAppearance", "Std_SetMaterial", "-", "Std_Workbench", "-",
            sub("Link Navigation", "Std_LinkSelectLinked", "Std_LinkSelectLinkedFinal", "Std_LinkSelectAllLinks"),
            sub("Tree View Actions", "Std_TreeSelection", "Std_TreeSelectAllInstances", "Std_TreeCollapse", "Std_TreeExpand"),
            "Std_SelBack", "Std_SelForward", "Std_ToggleSuppress", "Std_ToggleFreeze", "Std_ToggleSkipRecompute"),
        sub("Tools", "Std_Measure", "Std_MassProperties", "Std_UnitsCalculator", "Std_ClarifySelection", "-", "Std_ViewLoadImage",
            "Std_ViewScreenShot", "Std_DemoMode", "-", "Std_DependencyGraph", "Std_ExportDependencyGraph", "-", "Std_ProjectUtil", "Std_CommandLine"),
        sub("Macro", "Std_DlgMacroExecute", "-", "Std_DlgMacroExecuteDirect"),
        // FreeCAD's documentation and community; not its bug tracker / developer handbook, which are for FreeCAD itself
        sub("Help", "Std_FreeCADUserHub", "Std_FreeCADForum", "Std_PythonHelp", "-",
            "Std_FreeCADWebsite", "Std_FreeCADDonation", "Std_OnlineHelp", "Std_OnlineHelpWebsite", "Std_About"),
    )

    /** Every command id the menu shows (for the reachability check). */
    fun ids(n: List<MenuNode> = bar): List<String> = n.flatMap { when (it) { is MenuNode.Cmd -> listOf(it.id); is MenuNode.Sub -> ids(it.items); else -> emptyList() } }
}

/** The ☰ menu: FreeCAD's menu bar as nested drop-down menus. */
@Composable
fun MainMenuDropdown(app: AppState, expanded: Boolean, onDismiss: () -> Unit) {
    var path by remember { mutableStateOf<List<MenuNode.Sub>>(emptyList()) }
    DropdownMenu(expanded, { path = emptyList(); onDismiss() }) {
        val items: List<MenuNode> = path.lastOrNull()?.items ?: MainMenu.bar
        if (path.isNotEmpty()) DropdownMenuItem({ Text("‹  ${path.last().title}", color = OS.Primary) }, { path = path.dropLast(1) })
        items.forEach { n ->
            when (n) {
                is MenuNode.Sep -> HorizontalDivider(Modifier.padding(vertical = 2.dp), color = OS.Metal4)
                is MenuNode.Sub -> DropdownMenuItem({ Text(n.title) }, { path = path + n }, trailingIcon = { Icon(Icons.Default.ChevronRight, null) })
                is MenuNode.Cmd -> if (n.id !in StdCommands.notApplicable) {
                    val c = app.catalog.cmd(n.id); val ok = MobileCommands.available(n.id)
                    DropdownMenuItem({ Text(c?.label?.replace("&", "") ?: n.id, fontSize = 14.sp) }, { path = emptyList(); onDismiss(); app.run(n.id) }, enabled = ok,
                        leadingIcon = { app.catalog.icon(c?.icon)?.let { androidx.compose.foundation.Image(it, null, Modifier.size(20.dp)) } ?: Spacer(Modifier.size(20.dp)) })
                }
            }
        }
    }
}
