package app.mobilecad.freecad

import app.mobilecad.sketch.SketchTool
import org.json.JSONObject

/**
 * FreeCAD command id → mobile implementation.  Implementations call mobilecad (engine/python/mobilecad), which reproduces
 * what the desktop Gui command does; then the result opens in the feature dialog like FreeCAD's task panel.
 */
object MobileCommands {
    private val profile = listOf("Pad", "Pocket", "Revolution", "Groove", "Hole", "AdditiveLoft", "SubtractiveLoft",
        "AdditivePipe", "SubtractivePipe", "AdditiveHelix", "SubtractiveHelix")
    private val dressup = listOf("Fillet", "Chamfer", "Draft", "Thickness")
    private val transform = listOf("Mirrored", "LinearPattern", "PolarPattern")
    private val prims = listOf("Box", "Cylinder", "Sphere", "Cone", "Ellipsoid", "Torus", "Prism", "Wedge")
    private val partPrims = listOf("Box", "Cylinder", "Sphere", "Cone", "Torus", "Tube")

    val sketchTools = mapOf(
        "Sketcher_CreatePolyline" to SketchTool.POLYLINE, "Sketcher_CreateLine" to SketchTool.LINE,
        "Sketcher_CreateRectangle" to SketchTool.RECTANGLE, "Sketcher_CreateRectangle_Center" to SketchTool.CENTER_RECTANGLE,
        "Sketcher_CreateCircle" to SketchTool.CIRCLE, "Sketcher_Create3PointCircle" to SketchTool.CIRCLE3,
        "Sketcher_CreateArc" to SketchTool.ARC_CENTER, "Sketcher_Create3PointArc" to SketchTool.ARC3,
        "Sketcher_CreatePoint" to SketchTool.POINT, "Sketcher_CreateSlot" to SketchTool.SLOT,
        "Sketcher_CreateTriangle" to SketchTool.POLYGON3, "Sketcher_CreateSquare" to SketchTool.POLYGON4,
        "Sketcher_CreatePentagon" to SketchTool.POLYGON5, "Sketcher_CreateHexagon" to SketchTool.POLYGON6,
        "Sketcher_CreateHeptagon" to SketchTool.POLYGON7, "Sketcher_CreateOctagon" to SketchTool.POLYGON8,
        "Sketcher_CreateRegularPolygon" to SketchTool.POLYGON6,
        "Sketcher_Trimming" to SketchTool.TRIM, "Sketcher_Dimension" to SketchTool.DIMENSION,
        "Sketcher_CreatePolylineLegacy" to SketchTool.POLYLINE, "Sketcher_ProfilesHexagon1" to SketchTool.POLYGON6,
        "Sketcher_CreateEllipseByCenter" to SketchTool.ELLIPSE, "Sketcher_CreateEllipseBy3Points" to SketchTool.ELLIPSE3,
        "Sketcher_CreateArcOfEllipse" to SketchTool.ARC_ELLIPSE, "Sketcher_CreateArcOfHyperbola" to SketchTool.ARC_HYPERBOLA,
        "Sketcher_CreateArcOfParabola" to SketchTool.ARC_PARABOLA, "Sketcher_CreateBSpline" to SketchTool.BSPLINE,
        "Sketcher_CreatePeriodicBSpline" to SketchTool.BSPLINE_PERIODIC, "Sketcher_CreateBSplineByInterpolation" to SketchTool.BSPLINE_INTERP,
        "Sketcher_CreatePeriodicBSplineByInterpolation" to SketchTool.BSPLINE_INTERP_PERIODIC, "Sketcher_CreateOblong" to SketchTool.OBLONG,
        "Sketcher_CreateArcSlot" to SketchTool.ARC_SLOT, "Sketcher_CreateFillet" to SketchTool.FILLET, "Sketcher_CreateChamfer" to SketchTool.CHAMFER,
        "Sketcher_Extend" to SketchTool.EXTEND, "Sketcher_Split" to SketchTool.SPLIT, "Sketcher_JoinCurves" to SketchTool.JOIN,
        "Sketcher_Symmetry" to SketchTool.SYMMETRY, "Sketcher_Translate" to SketchTool.TRANSLATE, "Sketcher_Rotate" to SketchTool.ROTATE,
        "Sketcher_Scale" to SketchTool.SCALE, "Sketcher_RectangularArray" to SketchTool.ARRAY, "Sketcher_Projection" to SketchTool.PROJECTION,
        "Sketcher_Intersection" to SketchTool.INTERSECTION, "Sketcher_BSplineInsertKnot" to SketchTool.INSERT_KNOT,
        "Sketcher_BSplineIncreaseKnotMultiplicity" to SketchTool.KNOT_UP, "Sketcher_CreateText" to SketchTool.TEXT, "Sketcher_BSplineDecreaseKnotMultiplicity" to SketchTool.KNOT_DOWN,
    )

    /** Sketch-mode commands acting on the selection (SketchEditor.command) and visual helpers (SketchEditor.toggleHelper). */
    val sketchCommands = setOf("Sketcher_Offset", "Sketcher_BSplineConvertToNURBS", "Sketcher_BSplineIncreaseDegree", "Sketcher_BSplineDecreaseDegree",
        "Sketcher_RemoveAxesAlignment", "Sketcher_RestoreInternalAlignmentGeometry", "Sketcher_ToggleDrivingConstraint", "Sketcher_ToggleActiveConstraint",
        "Sketcher_SwitchVirtualSpace", "Sketcher_ChangeDimensionConstraint", "Sketcher_ValidateSketch", "Sketcher_DeleteAllGeometry",
        "Sketcher_DeleteAllConstraints", "Sketcher_SelectConstraints", "Sketcher_SelectElementsAssociatedWithConstraints",
        "Sketcher_SelectConflictingConstraints", "Sketcher_SelectRedundantConstraints", "Sketcher_SelectOrigin", "Sketcher_SelectHorizontalAxis",
        "Sketcher_SelectVerticalAxis", "Sketcher_StopOperation", "Sketcher_SelectMalformedConstraints", "Sketcher_SelectPartiallyRedundantConstraints",
        "Sketcher_SelectElementsWithDoFs", "Sketcher_ConstrainGroup", "Sketcher_ConstrainSnellsLaw", "Sketcher_CopyClipboard", "Sketcher_Cut", "Sketcher_Paste")
    val sketchHelpers = setOf("Sketcher_ArcOverlay", "Sketcher_BSplineDegree", "Sketcher_BSplinePolygon", "Sketcher_BSplineComb",
        "Sketcher_BSplineKnotMultiplicity", "Sketcher_BSplinePoleWeight", "Sketcher_Snap", "Sketcher_RenderingOrder", "Sketcher_ViewSection", "Sketcher_Grid")

    /** FreeCAD constraint command → Sketcher.Constraint type applied to the current sketch selection. */
    val sketchConstraints = mapOf(
        "Sketcher_ConstrainCoincidentUnified" to "Coincident", "Sketcher_ConstrainCoincident" to "Coincident",
        "Sketcher_ConstrainPointOnObject" to "PointOnObject", "Sketcher_ConstrainHorVer" to "HorVer",
        "Sketcher_ConstrainHorizontal" to "Horizontal", "Sketcher_ConstrainVertical" to "Vertical",
        "Sketcher_ConstrainParallel" to "Parallel", "Sketcher_ConstrainPerpendicular" to "Perpendicular",
        "Sketcher_ConstrainTangent" to "Tangent", "Sketcher_ConstrainEqual" to "Equal", "Sketcher_ConstrainSymmetric" to "Symmetric",
        "Sketcher_ConstrainBlock" to "Block", "Sketcher_ConstrainLock" to "Lock",
        "Sketcher_ConstrainDistance" to "Distance", "Sketcher_ConstrainDistanceX" to "DistanceX", "Sketcher_ConstrainDistanceY" to "DistanceY",
        "Sketcher_ConstrainRadius" to "Radius", "Sketcher_ConstrainDiameter" to "Diameter", "Sketcher_ConstrainRadiam" to "Radiam",
        "Sketcher_ConstrainAngle" to "Angle",
    )

    /** Assembly selection helpers (Assembly/Gui/Commands.cpp) -> mobilecad.assembly.asm_select kind. */
    private val asmSelect = mapOf("Assembly_SelectConflictingConstraints" to "Conflicting", "Assembly_SelectRedundantConstraints" to "Redundant",
        "Assembly_SelectMalformedConstraints" to "Malformed", "Assembly_SelectComponentsWithDoFs" to "ComponentsWithDoFs",
        "Assembly_SelectJointsOfComponent" to "JointsOfComponent", "Assembly_LinkSelectLinked" to "LinkSelectLinked")

    /** Assembly_CreateJoint<X> → JointObject.JointTypes name. */
    val joints = mapOf("Fixed" to "Fixed", "Revolute" to "Revolute", "Cylindrical" to "Cylindrical", "Slider" to "Slider", "Ball" to "Ball",
        "Distance" to "Distance", "Parallel" to "Parallel", "Perpendicular" to "Perpendicular", "Angle" to "Angle",
        "RackPinion" to "RackPinion", "Screw" to "Screw", "Gears" to "Gears", "GearBelt" to "Gears", "Belt" to "Belt")

    /** Part / PartDesign commands that run one mobilecad op on the selection and open the result in the feature dialog
     *  (desktop: command + task panel).  id → (op, extra args). */
    private val selOps: Map<String, Pair<String, Map<String, Any>>> = mapOf(
        "Part_Extrude" to ("part_extrude" to emptyMap()), "Part_Revolve" to ("part_revolve" to emptyMap()),
        "Part_Loft" to ("part_loft" to emptyMap()), "Part_Sweep" to ("part_sweep" to emptyMap()), "Part_Scale" to ("part_scale" to emptyMap()),
        "Part_ProjectionOnSurface" to ("part_project_on_surface" to emptyMap()),
        "Part_BooleanFragments" to ("part_split" to mapOf("kind" to "BooleanFragments")), "Part_Slice" to ("part_split" to mapOf("kind" to "Slice")),
        "Part_SliceApart" to ("part_split" to mapOf("kind" to "SliceApart")), "Part_XOR" to ("part_split" to mapOf("kind" to "XOR")),
        "Part_JoinConnect" to ("part_join" to mapOf("mode" to "Connect")), "Part_JoinEmbed" to ("part_join" to mapOf("mode" to "Embed")),
        "Part_JoinCutout" to ("part_join" to mapOf("mode" to "Cutout")), "Part_CompoundFilter" to ("part_compound_filter" to emptyMap()),
        "Part_ExplodeCompound" to ("part_explode" to emptyMap()), "Part_ToleranceSet" to ("part_tolerance_set" to emptyMap()),
        "Part_MakeSolid" to ("part_make_solid" to emptyMap()), "Part_Defeaturing" to ("part_defeaturing" to emptyMap()),
        "Part_SimpleCopy" to ("part_copy" to mapOf("mode" to "Simple")), "Part_TransformedCopy" to ("part_copy" to mapOf("mode" to "Transformed")),
        "Part_ElementCopy" to ("part_copy" to mapOf("mode" to "Element")),
        "Part_LinkArrayLinear" to ("part_link_array" to mapOf("kind" to "Linear")), "Part_LinkArrayPolar" to ("part_link_array" to mapOf("kind" to "Polar")),
        "Part_LinkArrayCircular" to ("part_link_array" to mapOf("kind" to "Circular")), "Part_LinkArrayPath" to ("part_link_array" to mapOf("kind" to "Path")),
        "Part_LinkArrayPoint" to ("part_link_array" to mapOf("kind" to "Point")),
        "Part_ShapeFromMesh" to ("part_shape_from_mesh" to emptyMap()), "Part_PointsFromMesh" to ("part_points_from_shape" to emptyMap()),
        "PartDesign_Clone" to ("pd_clone" to emptyMap()), "PartDesign_CoordinateSystem" to ("pd_coordinate_system" to emptyMap()),
        "PartDesign_DuplicateSelection" to ("pd_duplicate" to emptyMap()), "PartDesign_Defeaturing" to ("pd_dressup" to mapOf("kind" to "Defeaturing")),
        "PartDesign_InvoluteGear" to ("pd_gear" to mapOf("kind" to "InvoluteGear")), "PartDesign_Sprocket" to ("pd_gear" to mapOf("kind" to "Sprocket")),
    )
    private val otherIds = setOf("Part_Mirror", "Part_Fillet", "Part_Chamfer", "Part_CrossSections", "Part_SimpleCylinder", "Part_ShapeInfo",
        "Part_CheckGeometry", "Part_Import", "Part_Export", "Std_Import", "Std_Export", "Part_NewDoc", "Std_New", "Part_SectionCut",
        "Part_VertexSelection", "Part_EdgeSelection", "Part_FaceSelection", "Part_RemoveSelectionGate", "Part_SelectFilter", "Part_BoxSelection",
        "Part_EditAttachment", "Part_Primitives", "Part_Builder", "PartDesign_MoveFeature", "PartDesign_MoveFeatureInTree", "PartDesign_Migrate")

    @Volatile var catalogRef: Catalog? = null
    private fun groupMembers(id: String) = (catalogRef?.cmd(id)?.group?.takeIf { it.isNotEmpty() } ?: GuiCommands.groups[id])
        ?.flatMap { m -> catalogRef?.cmd(m)?.group?.takeIf { it.isNotEmpty() } ?: GuiCommands.groups[m] ?: listOf(m) } ?: emptyList()
    /** A FreeCAD group command (toolbar dropdown) with at least one implemented tool. */
    fun isGroup(id: String) = groupMembers(id).let { ms -> ms.isNotEmpty() && ms.any { it != id && (specific(it) || it in commandTypes || it in StdCommands.ids || it in DraftCommands.ids || it in TechDrawCommands.ids || it in GuiCommands.ids || it in MeshCommands.ids) } }

    // ---- Spreadsheet (src/Mod/Spreadsheet/Gui/Command.cpp): act on the selected cells of the Variables tab
    private val sheetAlign = mapOf("Spreadsheet_AlignLeft" to "left", "Spreadsheet_AlignCenter" to "center", "Spreadsheet_AlignRight" to "right",
        "Spreadsheet_AlignTop" to "top", "Spreadsheet_AlignVCenter" to "vcenter", "Spreadsheet_AlignBottom" to "bottom")
    private val sheetStyle = mapOf("Spreadsheet_StyleBold" to "bold", "Spreadsheet_StyleItalic" to "italic", "Spreadsheet_StyleUnderline" to "underline")
    val sheetIds = sheetAlign.keys + sheetStyle.keys + setOf("Spreadsheet_MergeCells", "Spreadsheet_SplitCell", "Spreadsheet_SetAlias", "Spreadsheet_Import", "Spreadsheet_Export")
    private fun sheetCmd(s: AppState, id: String): Boolean {
        val sh = s.sheet
        if (id == "Spreadsheet_Import") { s.fileRequest = AppState.FileRequest("sheetimport", listOfNotNull(sh)); return true }
        if (sh == null) { s.message = "Create or open a spreadsheet in the Variables tab"; return true }
        fun cmd(kind: String, v: Any? = null) = s.step(kind, { call("sheet_cmd", mapOf("name" to sh, "rng" to s.sheetSel, "kind" to kind, "value" to v)) })
        when (id) {
            in sheetAlign -> cmd("align", sheetAlign.getValue(id))
            in sheetStyle -> cmd("style", sheetStyle.getValue(id))
            "Spreadsheet_MergeCells" -> if (':' in s.sheetSel) cmd("merge") else s.message = "Select a range (long-press a cell, then tap the opposite corner)"
            "Spreadsheet_SplitCell" -> cmd("split")
            "Spreadsheet_SetAlias" -> s.textPrompt = TextPrompt("Alias for ${s.sheetSel.substringBefore(':')}", "") { a -> cmd("alias", a) }
            "Spreadsheet_Export" -> s.fileRequest = AppState.FileRequest("sheetexport", listOf(sh), "${s.obj(sh)?.label ?: "Spreadsheet"}.csv")
        }
        return true
    }

    // ---- FEM C++ commands (src/Mod/Fem/Gui/Command.cpp) -> mobilecad.fem
    private val femConstraints = listOf("Bearing", "Contact", "Displacement", "Fixed", "FluidBoundary", "Force", "Gear", "Heatflux", "InitialTemperature",
        "PlaneRotation", "Pressure", "Pulley", "RigidBody", "Spring", "Temperature", "Transform")
    private val femPost = listOf("PostApplyChanges", "PostBranchFilter", "PostFilterCalculator", "PostFilterClipRegion", "PostFilterClipScalar",
        "PostFilterContours", "PostFilterCutFunction", "PostFilterDataAlongLine", "PostFilterDataAtPoint", "PostFilterLinearizedStresses",
        "PostFilterWarp", "PostPipelineFromResult", "PostCreateFunctions")
    val femIds = femConstraints.map { "FEM_Constraint$it" }.toSet() + femPost.map { "FEM_$it" } +
        setOf("FEM_CreateNodesSet", "FEM_DefineNodesSet", "FEM_CreateElementsSet", "FEM_DefineElementsSet", "FEM_FemAddPart", "FEM_ResultShow")
    private fun femCmd(s: AppState, id: String): Boolean {
        val n = id.removePrefix("FEM_"); val sel = s.selJson()
        val label = s.catalog.cmd(id)?.label?.replace("&", "") ?: n
        when {
            // like the desktop: the condition is created at once (preselected faces / edges become its references) and its
            // editor opens with References waiting for geometry taps
            n.startsWith("Constraint") -> s.createFeature(label) { call("fem_constraint", mapOf("kind" to n.removePrefix("Constraint"), "sel" to sel)) as String }
            // FEM_ResultShow: the field colouring the result mesh and the deformation scale (desktop: TaskPanel of the result)
            n == "ResultShow" -> { val res = s.selection.firstOrNull()?.obj ?: s.objects.lastOrNull { it.type.startsWith("Fem::FemResultObject") || it.type == "Fem::FemPostPipeline" }?.name
                val post = res?.let { s.obj(it)?.type?.startsWith("Fem::FemPost") } == true
                if (res == null) s.message = "No FEM result — run the solver first" else if (post)
                // pipeline / filter (CalculiX solver results, clip, cut ...): the field colouring it
                s.eng({ call("fem_post_show", mapOf("name" to res)) as org.json.JSONObject }) { info -> val f = info.getJSONArray("fields")
                    s.choose("Show field", List(f.length()) { f.getString(it) }.map { it to it }) { field ->
                        s.eng({ call("fem_post_show", mapOf("name" to res, "field" to field)) as org.json.JSONObject }) { r ->
                            s.message = "$field: %.4g … %.4g".format(r.optDouble("min"), r.optDouble("max")); s.refresh() } } }
                else
                s.eng({ call("fem_result_show", mapOf("name" to res)) as org.json.JSONObject }) { info -> val f = info.getJSONArray("fields")
                    s.choose("Show result", List(f.length()) { f.getString(it) }.map { it to it }) { field ->
                        s.askNumber("Deformation scale (0 = undeformed)", 0.0, emptyList()) { sc ->
                            s.eng({ call("fem_result_show", mapOf("name" to res, "field" to field, "scale" to sc)) as org.json.JSONObject }) { r ->
                                s.message = "$field: %.4g … %.4g".format(r.optDouble("min"), r.optDouble("max")); s.refresh() } } } } }
            n == "FemAddPart" -> s.createFeature(label) { call("fem_add_part", mapOf("sel" to sel)) as String }
            n.endsWith("NodesSet") || n.endsWith("ElementsSet") -> s.startDraft(DraftTool("FemSet", label, listOf("Lasso corner (Finish to close)"), 3, -1) { p ->
                val dir = s.viewport?.cam?.let { c -> val e = c.eye(); listOf((c.tx - e[0]).toDouble(), (c.ty - e[1]).toDouble(), (c.tz - e[2]).toDouble()) }
                s.step(label, { call("fem_nodes_set", mapOf("polygon" to p.map { it.toList() }, "view_dir" to dir, "elements" to n.contains("Elements"))) }) }.apply { viewPlane = true })
            n == "PostCreateFunctions" -> s.choose("Implicit function", listOf("Plane", "Sphere", "Cylinder", "Box").map { it to it }) { k ->
                s.createFeature(label) { call("fem_post", mapOf("kind" to k, "sel" to sel)) as String } }
            n == "PostApplyChanges" -> s.step(label, { call("fem_post", mapOf("kind" to "ApplyChanges", "sel" to sel)) })
            else -> s.createFeature(label) { call("fem_post", mapOf("kind" to n.removePrefix("Post"), "sel" to sel)) as String }
        }
        return true
    }

    // ---- CAM commands implemented in C++ (src/Mod/CAM/Gui/Command.cpp) -> mobilecad.cam; Part_Boolean (the Boolean dialog)
    val camIds = setOf("CAM_Area", "CAM_Area_Workplane", "CAM_Compound", "CAM_Shape", "Part_Boolean",
        "Inspection_VisualInspection", "Inspection_InspectElement")
    private fun camCmd(s: AppState, id: String): Boolean {
        val sel = s.selJson(); val label = s.catalog.cmd(id)?.label?.replace("&", "") ?: id
        when (id) {
            "Part_Boolean" -> s.choose("Boolean operation", listOf("Part_Common" to "Intersection", "Part_Cut" to "Cut", "Part_Fuse" to "Union", "Part_Section" to "Section")) { c -> s.run(c) }
            "CAM_Area_Workplane" -> s.step(label, { call("cam_area_workplane", mapOf("sel" to sel)) })
            // TaskVisualInspection: actual geometries, nominal geometries, search radius, thickness
            "Inspection_VisualInspection" -> {
                val geo = s.objects.filter { it.hasShape }.map { it.name to it.label }
                val picked = s.selection.map { it.obj }.toSet()
                s.chooseMany("Actual (measured) geometry", geo, picked.take(1).toSet()) { act ->
                    s.chooseMany("Nominal (reference) geometry", geo.filter { it.first !in act }, picked.drop(1).toSet()) { nom ->
                        s.askNumber("Search distance", 0.05) { r -> s.askNumber("Thickness", 0.0) { t ->
                            s.step(label, { call("inspection_create", mapOf("actual" to act, "nominals" to nom, "radius" to r, "thickness" to t)) }) } } } }
            }
            // Inspection_InspectElement: tap the inspection result for the distance there
            "Inspection_InspectElement" -> s.startDraft(DraftTool("Inspect", label, listOf("Tap a point on the inspected geometry"), 1, 1) { p ->
                val n = s.selection.firstOrNull()?.obj ?: s.objects.lastOrNull { it.type == "Inspection::Feature" }?.name
                if (n == null) s.message = "No inspection result — run Visual Inspection first" else
                s.eng({ call("inspection_value", mapOf("name" to n, "point" to p[0].toList())) as JSONObject? }) { r ->
                    s.info = "Inspection" to (r?.let { j -> if (j.isNull("distance")) "No distance (outside the search radius)" else "Distance: %.4f mm".format(j.getDouble("distance")) } ?: "No data") } })
            else -> s.createFeature(label) { call(id.lowercase(), mapOf("sel" to sel)) as String }
        }
        return true
    }

    // ---- Robot (src/Mod/Robot/Gui/Command*.cpp) -> mobilecad.robot
    val robotIds = setOf("Robot_Create", "Robot_CreateTrajectory", "Robot_InsertWaypoint", "Robot_InsertWaypointPreselect", "Robot_SetDefaultOrientation",
        "Robot_SetDefaultValues", "Robot_Edge2Trac", "Robot_TrajectoryDressUp", "Robot_TrajectoryCompound", "Robot_SetHomePos", "Robot_RestoreHomePos",
        "Robot_AddToolShape", "Robot_ExportKukaCompact", "Robot_ExportKukaFull", "Robot_Simulate")
    private fun robotCmd(s: AppState, id: String): Boolean {
        val n = id.removePrefix("Robot_"); val names = s.selection.map { it.obj }.distinct()
        val label = s.catalog.cmd(id)?.label?.replace("&", "") ?: n
        fun rc(opts: Map<String, Any?> = emptyMap(), pts: List<List<Double>>? = null) = s.step(label, { call("robot_cmd", mapOf("kind" to n, "names" to names, "pts" to pts, "opts" to opts)) })
        when (n) {
            "Create" -> s.createFeature(label) { call("robot_create") as String }
            "CreateTrajectory" -> s.createFeature(label) { call("robot_cmd", mapOf("kind" to n)) as String }
            "InsertWaypointPreselect" -> s.startDraft(DraftTool("RobotWp", label, listOf("Waypoint position"), 1, 1) { p -> rc(pts = p.map { it.toList() }) })
            "SetDefaultOrientation" -> s.askNumber("Yaw", 0.0, listOf("°")) { y -> s.askNumber("Pitch", 0.0, listOf("°")) { pi -> s.askNumber("Roll", 0.0, listOf("°")) { ro ->
                s.eng({ call("robot_cmd", mapOf("kind" to n, "opts" to mapOf("ypr" to listOf(y, pi, ro)))) }) { s.message = "Default orientation set" } } } }
            "SetDefaultValues" -> s.choose("Waypoint type", listOf("LIN" to "Linear (LIN)", "PTP" to "Point to point (PTP)", "CIRC" to "Circular (CIRC)", "WAIT" to "Wait")) { t ->
                s.askNumber("Speed", 1000.0, listOf("mm/s")) { v -> s.askNumber("Acceleration", 100.0, listOf("mm/s²")) { a ->
                    s.eng({ call("robot_cmd", mapOf("kind" to n, "opts" to mapOf("type" to t, "velocity" to v, "acceleration" to a))) }) { s.message = "Waypoint defaults set" } } } }
            "Edge2Trac" -> if (s.selection.none { it.sub.startsWith("Edge") }) s.message = "Select edges" else
                s.createFeature(label) { call("robot_cmd", mapOf("kind" to n, "opts" to mapOf("sel" to s.selJson()))) as String }
            "TrajectoryDressUp", "TrajectoryCompound" -> s.createFeature(label) { call("robot_cmd", mapOf("kind" to n, "names" to names)) as String }
            "ExportKukaCompact", "ExportKukaFull" -> s.fileRequest = AppState.FileRequest("robotexport_$n", names, "${s.docName}.src")
            "Simulate" -> {                       // TaskRobotSimulate: play the trajectory, robot following by inverse kinematics
                s.eng({ call("robot_simulate", mapOf("names" to names, "t" to 0.0)) as org.json.JSONObject }) { j ->
                    val dur = j.getDouble("duration"); val steps = 60
                    fun frame(k: Int) { if (k > steps) { s.message = "Simulation finished (${"%.1f".format(dur)} s)"; return }
                        s.eng({ call("robot_simulate", mapOf("names" to names, "t" to dur * k / steps)) }) { s.refresh { s.viewport?.postDelayed({ frame(k + 1) }, 40) } } }
                    frame(0) } }
            else -> rc()
        }
        return true
    }

    /** Sketcher tools that only act inside an open sketch (kept out of the Part panels). */
    fun sketchOnly(id: String) = id in sketchTools || id in sketchConstraints || id in sketchCommands || id in sketchHelpers ||
        id in setOf("Sketcher_LeaveSketch", "Sketcher_CancelSketch", "Sketcher_ToggleConstruction", "Sketcher_ViewSketch", "Sketcher_CarbonCopy")

    /** Set once at startup: FreeCAD command → object type for the generic implementation. */
    var commandTypes: Map<String, String> = emptyMap()

    fun available(id: String): Boolean = specific(id) || id in commandTypes || id in StdCommands.ids || id in DraftCommands.ids || id in TechDrawCommands.ids || id in GuiCommands.ids || id in MeshCommands.ids || id in sheetIds || id in femIds || id in robotIds || id in camIds || isGroup(id)

    private fun specific(id: String): Boolean {
        val wb = id.substringBefore('_'); val n = id.substringAfter('_')
        return when {
            id in sketchTools || id in sketchConstraints || id in sketchCommands || id in sketchHelpers -> true
            id in setOf("Sketcher_MergeSketches", "Sketcher_MirrorSketch", "Sketcher_ReorientSketch", "Sketcher_MapSketch", "Sketcher_CarbonCopy") -> true
            id.startsWith("Assembly_CreateJoint") && id.removePrefix("Assembly_CreateJoint") in joints -> true
            id in setOf("Assembly_CreateAssembly", "Assembly_Insert", "Assembly_InsertLink", "Assembly_SolveAssembly", "Assembly_ToggleGrounded") -> true
            id in asmSelect -> true
            id in setOf("PartDesign_Body", "PartDesign_NewSketch", "Sketcher_NewSketch", "Sketcher_EditSketch", "Sketcher_LeaveSketch",
                "Sketcher_CancelSketch", "Sketcher_ToggleConstruction", "Sketcher_ViewSketch", "PartDesign_MoveTip",
                "PartDesign_Plane", "PartDesign_Line", "PartDesign_Point", "Part_Fuse", "Part_Common", "Part_Cut",
                "Std_Delete", "Std_ToggleVisibility", "Std_Undo", "Std_Redo", "Std_ViewFit") -> true
            wb == "PartDesign" && (n in profile || n in dressup || n in transform) -> true
            wb == "PartDesign" && prims.any { n == "Additive$it" || n == "Subtractive$it" } -> true
            wb == "Part" && n in partPrims -> true
            id in selOps || id in otherIds -> true
            else -> false
        }
    }

    private fun sketchesSel(s: AppState) = s.selection.map { it.obj }.distinct().filter { s.obj(it)?.type == "Sketcher::SketchObject" }

    fun run(s: AppState, id: String): Boolean {
        if (!available(id)) return false
        if (!specific(id) && id in StdCommands.ids) return StdCommands.run(s, id)
        if (!specific(id) && id in DraftCommands.ids) return DraftCommands.run(s, id)
        if (!specific(id) && id in TechDrawCommands.ids) return TechDrawCommands.run(s, id)
        if (!specific(id) && id in MeshCommands.ids) return MeshCommands.run(s, id)
        if (!specific(id) && id in sheetIds) return sheetCmd(s, id)
        if (!specific(id) && id in femIds) return femCmd(s, id)
        if (!specific(id) && id in robotIds) return robotCmd(s, id)
        if (!specific(id) && id in camIds) return camCmd(s, id)
        if (!specific(id) && id in GuiCommands.ids) { GuiCommands.run(s, id); return true }
        if (!specific(id) && isGroup(id)) {            // FreeCAD dropdown (GroupCommand): pick one of its tools
            val members = groupMembers(id).filter { available(it) }
            s.choose(s.catalog.cmd(id)?.label?.replace("&", "") ?: id, members.map { it to (s.catalog.cmd(it)?.label?.replace("&", "") ?: it) }) { c -> s.run(c) }
            return true
        }
        if (!specific(id)) {
            // generic FreeCAD command: create its object type from the selection, then edit it in the feature dialog
            val type = commandTypes.getValue(id); val label = s.catalog.cmd(id)?.label ?: type.substringAfter("::")
            s.createFeature(label) { call("create_object", mapOf("type" to type, "label" to type.substringAfter("::"), "sel" to s.selJson())) as String }
            return true
        }
        val wb = id.substringBefore('_'); val n = id.substringAfter('_')
        val sel = s.selJson()
        val sk = s.sketch
        when {
            sk != null && id in sketchTools -> sk.selectTool(sketchTools.getValue(id))
            sk != null && id in sketchConstraints -> sk.constrain(sketchConstraints.getValue(id))
            sk != null && id in sketchCommands -> sk.command(id)
            sk != null && id == "Sketcher_Grid" -> s.showGrid = !s.showGrid
            sk != null && id in sketchHelpers -> sk.toggleHelper(id)
            sk != null && id == "Sketcher_CarbonCopy" -> s.choose("Carbon copy from", s.objects.filter { it.type == "Sketcher::SketchObject" && it.name != sk.name }.map { it.name to it.label }) { n ->
                s.eng({ begin("Carbon copy"); val r = call("sketch_carbon_copy", mapOf("name" to sk.name, "other" to n)); commit(); r }) { sk.reload(keepView = true) } }
            id == "Sketcher_MergeSketches" -> sketchesSel(s).takeIf { it.size >= 2 }?.let { n -> s.step("Merge sketches", { call("sketch_merge", mapOf("names" to n)) }) }
                ?: run { s.message = "Select at least two sketches" }
            id == "Sketcher_MirrorSketch" -> sketchesSel(s).takeIf { it.isNotEmpty() }?.let { n -> s.step("Mirror sketch", { call("sketch_mirror", mapOf("names" to n)) }) }
                ?: run { s.message = "Select sketches first" }
            id == "Sketcher_ReorientSketch" -> sketchesSel(s).firstOrNull()?.let { n -> s.choose("Reorient sketch to", listOf("XY_Plane" to "Top (XY)", "XZ_Plane" to "Front (XZ)", "YZ_Plane" to "Right (YZ)")) { pl ->
                s.step("Reorient sketch", { call("sketch_reorient", mapOf("name" to n, "plane" to pl)) }) } } ?: run { s.message = "Select a sketch first" }
            id == "Sketcher_MapSketch" -> {
                val sk2 = sketchesSel(s).firstOrNull(); val face = s.selection.firstOrNull { it.sub.startsWith("Face") }
                if (sk2 == null || face == null) s.message = "Select a sketch and a planar face"
                else s.step("Attach sketch", { run("o = doc.getObject(args['s']); o.AttachmentSupport = (doc.getObject(args['o']), [args['f']]); o.MapMode = 'FlatFace'",
                    mapOf("s" to sk2, "o" to face.obj, "f" to face.sub)) })
            }
            id == "Sketcher_ToggleConstruction" -> if (sk != null) sk.toggleConstruction() else return false
            id == "Sketcher_LeaveSketch" -> s.leaveSketch(true)
            id == "Sketcher_CancelSketch" -> s.leaveSketch(false)
            id == "Sketcher_ViewSketch" -> sk?.viewNormal()
            id in sketchTools || id in sketchConstraints -> s.message = "Open a sketch first"
            id.startsWith("Assembly_CreateJoint") -> s.startMate(joints.getValue(id.removePrefix("Assembly_CreateJoint")))
            id in asmSelect -> s.eng({ call("asm_select", mapOf("kind" to asmSelect.getValue(id), "sel" to sel)) as org.json.JSONArray }) { a ->
                if (a.length() == 0) s.message = "Nothing to select" else { s.selection.clear(); for (i in 0 until a.length()) s.selection.add(SelRef(a.getString(i))); s.recordSelection(); s.viewport?.sync() } }
            id == "Assembly_CreateAssembly" -> s.eng({ begin("Create assembly"); call("asm_create"); commit() }) { s.refresh() }
            id == "Assembly_Insert" || id == "Assembly_InsertLink" -> s.openPartPicker()
            id == "Assembly_SolveAssembly" -> s.eng({ call("asm_solve") }) { r -> if (r != 0) s.message = "Assembly solver failed ($r)"; s.refresh() }
            id == "Assembly_ToggleGrounded" -> s.selection.firstOrNull()?.let { r -> s.eng({ begin("Toggle grounded"); call("asm_ground", mapOf("link" to r.obj)); commit() }) { s.refresh() } }
                ?: run { s.message = "Select a component first" }
            id == "PartDesign_Body" -> s.eng({ begin("New body"); call("pd_body"); commit() }) { s.refresh() }
            // no face / plane selected: choose the origin plane (desktop: plane selection task)
            id == "PartDesign_NewSketch" || id == "Sketcher_NewSketch" -> if (sel.isEmpty())
                s.choose("Sketch plane", listOf("XY_Plane" to "Top (XY)", "XZ_Plane" to "Front (XZ)", "YZ_Plane" to "Right (YZ)")) { pl ->
                    s.createFeature("New sketch") { call("pd_new_sketch", mapOf("sel" to sel, "plane" to pl)) as String } }
                else s.createFeature("New sketch") { call("pd_new_sketch", mapOf("sel" to sel)) as String }
            id == "Sketcher_EditSketch" -> s.selection.firstOrNull { s.obj(it.obj)?.type == "Sketcher::SketchObject" }?.let { s.editSketch(it.obj) }
                ?: run { s.message = "Select a sketch first" }
            wb == "PartDesign" && n in profile -> s.createFeature(n) { call("pd_profile_feature", mapOf("kind" to n, "sel" to sel)) as String }
            wb == "PartDesign" && n in dressup -> s.createFeature(n) { call("pd_dressup", mapOf("kind" to n, "sel" to sel)) as String }
            wb == "PartDesign" && n in transform -> s.createFeature(n) { call("pd_transform", mapOf("kind" to n, "sel" to sel)) as String }
            wb == "PartDesign" && (n.startsWith("Additive") || n.startsWith("Subtractive")) ->
                s.createFeature(n) { call("pd_primitive", mapOf("kind" to n, "sel" to sel)) as String }
            id in setOf("PartDesign_Plane", "PartDesign_Line", "PartDesign_Point") ->
                s.createFeature("Datum $n") { call("pd_datum", mapOf("kind" to n, "sel" to sel)) as String }
            id == "PartDesign_MoveTip" -> s.selection.firstOrNull()?.let { r -> s.eng({ begin("Set tip"); call("pd_set_tip", mapOf("name" to r.obj)); commit() }) { s.refresh() } }
            wb == "Part" && n in partPrims -> s.createFeature(n) { call("part_primitive", mapOf("kind" to n)) as String }
            id in setOf("Part_Fuse", "Part_Common", "Part_Cut") ->
                s.createFeature(n) { call("part_boolean", mapOf("kind" to n, "sel" to sel)) as String }
            id == "Std_Delete" -> { val names = s.selection.map { it.obj }.distinct(); s.selection.clear()
                s.eng({ begin("Delete"); call("delete_objects", mapOf("names" to names)); commit() }) { s.refresh() } }
            id == "Std_ToggleVisibility" -> { val objs = s.selection.map { it.obj }.distinct().mapNotNull { s.obj(it) }
                s.eng({ call("set_visibility", mapOf("names" to objs.map { it.name }, "visible" to !(objs.firstOrNull()?.visible ?: true))) }) { s.refresh() } }
            id in selOps -> selOps.getValue(id).let { (op, extra) ->
                s.createFeature(s.catalog.cmd(id)?.label ?: n) { call(op, extra + mapOf("sel" to sel)) as String } }
            // Part_Primitives (DlgPrimitives): every parametric primitive type the dialog offers
            id == "Part_Primitives" -> s.choose("Primitive", listOf("Plane", "Box", "Cylinder", "Cone", "Sphere", "Ellipsoid", "Torus", "Prism", "Wedge",
                "Helix", "Spiral", "Circle", "Ellipse", "Vertex", "Line", "RegularPolygon").map { it to it }) { k ->
                s.createFeature(k) { call("part_primitive", mapOf("kind" to k)) as String } }
            id == "Part_Builder" -> s.choose("Shape builder", listOf("Edge" to "Edge from two vertexes", "Wire" to "Wire from edges",
                "FaceVertexes" to "Face from vertexes", "Face" to "Face from edges", "Shell" to "Shell from faces", "Solid" to "Solid from shell")) { m ->
                s.step("Shape builder", { call("part_builder", mapOf("mode" to m, "sel" to sel)) }) }
            id == "Part_Mirror" -> s.choose("Mirror plane", listOf("XY" to "XY plane", "XZ" to "XZ plane", "YZ" to "YZ plane")) { pl ->
                s.createFeature("Mirror") { call("part_mirror", mapOf("sel" to sel, "plane" to pl)) as String } }
            id == "Part_Fillet" || id == "Part_Chamfer" -> {
                fun partOne() = s.askNumber("${if (n == "Fillet") "Radius" else "Size"} (selected edges, or all)", 1.0) { r ->
                    s.createFeature(n) { call("part_fillet", mapOf("kind" to n, "sel" to sel, "radius" to r)) as String } }
                // On a PartDesign Body the Part tool makes a separate object on top of the whole Body, outside its
                // history; the Body's own feature keeps the order
                val onBody = s.selection.map { it.obj }.distinct().mapNotNull { s.obj(it) }.any { o ->
                    o.type == "PartDesign::Body" || o.parents.any { p -> s.obj(p)?.type == "PartDesign::Body" } }
                if (onBody) s.run("PartDesign_$n") else partOne()
            }
            id == "Part_CrossSections" -> s.choose("Section planes", listOf("Z" to "XY planes (along Z)", "Y" to "XZ planes (along Y)", "X" to "YZ planes (along X)")) { ax ->
                s.askNumber("Number of sections", 1.0, emptyList()) { c ->
                    s.step("Cross-sections", { call("part_cross_sections", mapOf("sel" to sel, "axis" to ax, "count" to c.toInt().coerceAtLeast(1))) }) } }
            id == "Part_SectionCut" -> s.choose("Cut along", listOf("X" to "X", "Y" to "Y", "Z" to "Z")) { ax ->
                s.step("Section cut", { call("part_section_cut", mapOf("axis" to ax)) }) { s.objects.firstOrNull { it.name == "SectionCut$ax" }?.let { s.openEditor("SectionCutBox$ax") } } }
            id == "Part_SimpleCylinder" -> s.askNumber("Cylinder radius", 2.0) { r -> s.askNumber("Cylinder length", 10.0) { l ->
                s.step("Create cylinder", { call("part_simple_cylinder", mapOf("radius" to r, "length" to l)) }) } }
            id == "Part_ShapeInfo" || id == "Part_CheckGeometry" -> s.selection.firstOrNull()?.let { r ->
                s.eng({ call("shape_info", mapOf("name" to r.obj)) as org.json.JSONObject }) { j ->
                    val bb = j.getJSONArray("bbox"); fun f(v: Double) = "%.4g".format(v)
                    s.info = (s.obj(r.obj)?.label ?: r.obj) to buildString {
                        appendLine("Check: ${j.optString("check")}  (valid: ${j.optBoolean("valid")})")
                        appendLine("Type: ${j.optString("type")}")
                        appendLine("Solids ${j.optInt("solids")} · Shells ${j.optInt("shells")} · Faces ${j.optInt("faces")} · Wires ${j.optInt("wires")} · Edges ${j.optInt("edges")} · Vertexes ${j.optInt("vertexes")}")
                        appendLine("Volume ${f(j.optDouble("volume"))} mm³ · Area ${f(j.optDouble("area"))} mm² · Length ${f(j.optDouble("length"))} mm")
                        appendLine("Bounds (${f(bb.getDouble(0))}, ${f(bb.getDouble(1))}, ${f(bb.getDouble(2))}) – (${f(bb.getDouble(3))}, ${f(bb.getDouble(4))}, ${f(bb.getDouble(5))})")
                        j.optJSONArray("center")?.let { c -> append("Center of mass (${f(c.getDouble(0))}, ${f(c.getDouble(1))}, ${f(c.getDouble(2))})") }
                    } } } ?: run { s.message = "Select a shape first" }
            id == "Part_Import" || id == "Std_Import" -> s.fileRequest = AppState.FileRequest("import")
            id == "Part_Export" || id == "Std_Export" -> s.fileRequest = AppState.FileRequest("export", s.selection.map { it.obj }.distinct(),
                "${s.selection.firstOrNull()?.let { s.obj(it.obj)?.label } ?: s.docName}.step")
            id == "Part_NewDoc" || id == "Std_New" -> { s.closeTools(); s.eng({ newDocument("Unnamed") }) { s.docName = it; s.docUri = null; s.selection.clear(); s.refresh { s.markSaved() } } }
            id == "Part_VertexSelection" -> s.selFilter = "Vertex"
            id == "Part_EdgeSelection" -> s.selFilter = "Edge"
            id == "Part_FaceSelection" -> s.selFilter = "Face"
            id == "Part_RemoveSelectionGate" -> { s.selFilter = null; s.boxSelect = null }
            id == "Part_SelectFilter" -> s.choose("Selection filter", listOf("Vertex" to "Vertexes", "Edge" to "Edges", "Face" to "Faces", "" to "No filter")) { f -> s.selFilter = f.ifEmpty { null } }
            id == "Part_BoxSelection" -> s.boxSelect = "faces"
            id == "Part_EditAttachment" -> s.selection.firstOrNull()?.let { s.openEditor(it.obj) } ?: run { s.message = "Select an object first" }
            id == "PartDesign_MoveFeature" -> { val names = s.selection.map { it.obj }.distinct()
                if (names.isEmpty()) s.message = "Select features first"
                else s.eng({ call("pd_bodies") as org.json.JSONArray }) { a -> s.choose("Move to body", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { b ->
                    s.step("Move feature", { call("pd_move_feature", mapOf("names" to names, "target" to b)) }) } } }
            id == "PartDesign_MoveFeatureInTree" -> { val names = s.selection.map { it.obj }.distinct()
                if (names.isEmpty()) s.message = "Select features first"
                else s.eng({ call("pd_features", mapOf("name" to names.first())) as org.json.JSONArray }) { a -> s.choose("Move after", List(a.length()) { i -> a.getJSONArray(i).let { it.getString(0) to it.getString(1) } }) { t ->
                    s.step("Move feature in tree", { call("pd_move_in_tree", mapOf("names" to names, "after" to t)) }) } } }
            id == "PartDesign_Migrate" -> s.eng({ begin("Migrate"); val r = call("pd_migrate"); commit(); r }) { r -> s.message = r.toString(); s.refresh() }
            id == "Std_Undo" -> s.undo()
            id == "Std_Redo" -> s.redo()
            else -> return false
        }
        return true
    }
}
