package app.mobilecad.freecad

/**
 * Element tabs ⇐ FreeCAD workbenches.
 * A toolset is a strip button; its tool panel shows the listed FreeCAD toolbars as groups.
 */
class ToolsetDef(val label: String, val icon: String, val groups: List<Pair<String, String>>)   // (workbench, toolbar name)
class WorkspaceDef(val id: String, val label: String, val toolsets: List<ToolsetDef>, val sketchToolsets: List<ToolsetDef> = emptyList())

object Workspaces {
    private fun tb(wb: String, vararg names: String) = names.map { wb to it }

    val sketch = listOf(
        ToolsetDef("Draw", "Sketcher_CreatePolyline", tb("Sketcher", "Geometries")),
        ToolsetDef("Constrain", "Sketcher_Dimension", tb("Sketcher", "Constraints")),
        ToolsetDef("Modify", "Sketcher_Trimming", tb("Sketcher", "Sketcher Tools", "B-Spline Tools")),
        ToolsetDef("View", "Sketcher_ArcOverlay", tb("Sketcher", "Visual Helpers", "Sketcher")),
    )

    val partStudio = WorkspaceDef("partstudio", "Part", listOf(
        ToolsetDef("Sketch", "Sketcher_NewSketch", tb("Sketcher", "Sketcher")),
        ToolsetDef("Features", "PartDesign_Pad", tb("PartDesign", "Part Design Modeling Features", "Part Design Dress-Up Features", "Part Design Transformation Features", "Part Design Helper Features")),
        ToolsetDef("Part", "Part_Box", tb("Part", "Solids", "Part Tools", "Boolean Tools")),
        ToolsetDef("Surface", "Surface_Filling", tb("Surface", "Surface")),
        ToolsetDef("Draft", "Draft_Line", tb("Draft", "Draft Creation", "Draft Annotation", "Draft Modification", "Draft Utility")),
    ), sketch)

    fun forWorkbench(c: Catalog, wb: String, label: String) = WorkspaceDef(wb, label,
        (c.wb(wb)?.toolbars ?: emptyList()).map { t ->
            ToolsetDef(short(wb, t.name), c.flatten(t.items).firstOrNull() ?: "", listOf(wb to t.name))
        }.ifEmpty {          // no toolbars: one toolset whose "More tools" lists every command of the workbench
            listOf(ToolsetDef("Tools", c.wb(wb)?.other?.firstOrNull() ?: "", listOf(wb to "__all__")))
        })

    /** Strip label for a FreeCAD toolbar: workbench prefixes and long qualifiers dropped ("Mechanical Boundary Conditions
     *  and Loads" → "Mechanical", "Part Design Dress-Up Features" → "Dress-Up"); the panel still shows the full name. */
    fun short(wb: String, name: String): String {
        var n = name
        for (p in listOf("$wb ", "TechDraw ", "Mesh ", "Part Design ", "Draft ")) if (n.startsWith(p) && n.length > p.length) n = n.removePrefix(p)
        for (x in listOf(" Boundary Conditions and Loads", " Boundary Conditions", " Analysis Features", " Features", " Tools", " tools"))
            if (n.endsWith(x) && n.length > x.length) n = n.removeSuffix(x)
        return when (n) { "Frequently-used Part WB" -> "Part"; else -> n }
    }

    /** Tools of a toolset's workbench(es) that no toolset of [ws] shows (FreeCAD menu-only commands, toolbars this
     *  workspace leaves out): listed under "More tools" in the first toolset of that workbench, so every tool is reachable. */
    fun extras(c: Catalog, ws: WorkspaceDef, ts: ToolsetDef, sketchMode: Boolean = false): List<String> {
        val sets = if (sketchMode) ws.sketchToolsets else ws.toolsets
        fun shown(t: ToolsetDef) = t.groups.flatMap { (wb, tb) -> c.wb(wb)?.toolbars?.firstOrNull { it.name == tb }?.let { c.flatten(it.items) } ?: emptyList() }
        val inWs = sets.flatMap { shown(it) }.toSet()
        val owned = ts.groups.map { it.first }.distinct().filter { wb -> sets.first { t -> t.groups.any { it.first == wb } } === ts }
        return owned.flatMap { wb -> c.wb(wb)?.let { w -> c.flatten(w.toolbars.flatMap { it.items } + w.other) } ?: emptyList() }
            .filter { it !in inWs && (sketchMode || !MobileCommands.sketchOnly(it)) }.distinct()
    }

    /** Bottom-tab elements; everything else lives under "More". */
    fun main(c: Catalog) = listOf(
        partStudio,
        forWorkbench(c, "Assembly", "Assembly"),
        forWorkbench(c, "TechDraw", "Drawing"),
        forWorkbench(c, "Spreadsheet", "Variables"),
    )

    val more = listOf("BIM" to "BIM", "Fem" to "FEM", "CAM" to "CAM", "Mesh" to "Mesh", "MeshPart" to "Mesh Part", "Points" to "Points",
        "ReverseEngineering" to "Reverse Engineering", "Robot" to "Robot", "OpenSCAD" to "OpenSCAD", "Inspection" to "Inspection",
        "Material" to "Material", "Draft" to "Draft", "Part" to "Part", "PartDesign" to "Part Design", "Sketcher" to "Sketcher", "Surface" to "Surface")
}
