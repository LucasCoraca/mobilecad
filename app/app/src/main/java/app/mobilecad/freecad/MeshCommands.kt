package app.mobilecad.freecad

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mesh, MeshPart, Points and Reverse Engineering (C++ Gui commands in src/Mod/{Mesh,MeshPart,Points,ReverseEngineering}/Gui
 * /Command.cpp).  Their dialogs become choices / number entry; polygon tools draw on the view plane and cut along the view.
 */
object MeshCommands {
    private val modify = mapOf("Mesh_HarmonizeNormals" to "HarmonizeNormals", "Mesh_FlipNormals" to "FlipNormals")
    val ids: Set<String> = setOf("Mesh_Union", "Mesh_Difference", "Mesh_Intersection", "Mesh_Import", "Mesh_Export", "Mesh_FromGeometry",
        "Mesh_FromPartShape", "MeshPart_Mesher", "Mesh_VertexCurvature", "Mesh_CurvatureInfo", "Mesh_PolySegm", "Mesh_AddFacet", "Mesh_PolyCut",
        "Mesh_PolyTrim", "Mesh_PolySplit", "Mesh_TrimByPlane", "MeshPart_TrimByPlane", "Mesh_SectionByPlane", "MeshPart_SectionByPlane",
        "Mesh_CrossSections", "MeshPart_CrossSections", "Mesh_Evaluation", "Mesh_EvaluateFacet", "Mesh_EvaluateSolid", "Mesh_BoundingBox",
        "Mesh_RemoveComponents", "Mesh_RemoveCompByHand", "Mesh_Smoothing", "Mesh_Decimating", "Mesh_HarmonizeNormals", "Mesh_FlipNormals",
        "Mesh_BuildRegularSolid", "Mesh_FillupHoles", "Mesh_FillInteractiveHole", "Mesh_Segmentation", "Mesh_SegmentationBestFit",
        "Mesh_Merge", "Mesh_SplitComponents", "Mesh_Scale", "Mesh_RemeshGmsh", "MeshPart_CurveOnMesh", "Surface_CurveOnMesh",
        "Points_Import", "Points_Export", "Points_Convert", "Points_PolyCut", "Points_Merge", "Points_Structure",
        "Reen_ApproxCurve", "Reen_ApproxSurface", "Reen_ApproxPlane", "Reen_ApproxCylinder", "Reen_ApproxSphere", "Reen_ApproxPolynomial",
        "Reen_Segmentation", "Reen_SegmentationManual", "Reen_SegmentationFromComponents", "Reen_MeshBoundary")

    private fun names(s: AppState) = s.selection.map { it.obj }.distinct()
    private fun meshes(s: AppState) = names(s).filter { s.obj(it)?.type?.startsWith("Mesh::") == true }
    private fun need(s: AppState, list: List<String>, what: String, f: (List<String>) -> Unit) = if (list.isEmpty()) s.message = "Select $what first" else f(list)
    private fun viewDir(s: AppState) = s.viewport?.cam?.let { c -> val e = c.eye(); listOf((c.tx - e[0]).toDouble(), (c.ty - e[1]).toDouble(), (c.tz - e[2]).toDouble()) } ?: listOf(0.0, 0.0, -1.0)
    private fun polygon(s: AppState, label: String, onDone: (List<DoubleArray>) -> Unit) =
        s.startDraft(DraftTool("MeshPoly", label, listOf("Polygon corner (Finish to close)"), 3, -1, onDone = onDone).apply { viewPlane = true })
    private fun pick(s: AppState, label: String, prompts: List<String>, n: Int, onDone: (List<DoubleArray>) -> Unit) =
        s.startDraft(DraftTool("MeshPick", label, prompts, n, n, onDone = onDone))
    private fun step(s: AppState, label: String, op: String, args: Map<String, Any?>) = s.step(label, { call(op, args) })
    private fun plane(s: AppState, label: String, f: (List<Double>, List<Double>) -> Unit) =
        s.choose("$label: plane", listOf("XY" to "XY plane (normal Z)", "XZ" to "XZ plane (normal Y)", "YZ" to "YZ plane (normal X)")) { p ->
            s.askNumber("Plane offset", 0.0) { off -> when (p) { "XY" -> f(listOf(0.0, 0.0, off), listOf(0.0, 0.0, 1.0)); "XZ" -> f(listOf(0.0, off, 0.0), listOf(0.0, 1.0, 0.0)); else -> f(listOf(off, 0.0, 0.0), listOf(1.0, 0.0, 0.0)) } } }
    private fun info(s: AppState, title: String, j: JSONObject) { s.info = title to j.keys().asSequence().joinToString("\n") { k -> "$k: ${j.get(k)}" } }

    fun run(s: AppState, id: String): Boolean {
        val label = s.catalog.cmd(id)?.label?.replace("&", "") ?: id
        when (id) {
            "Mesh_Union", "Mesh_Difference", "Mesh_Intersection" -> meshes(s).takeIf { it.size >= 2 }?.let { m -> step(s, label, "mesh_bool", mapOf("kind" to id.removePrefix("Mesh_"), "names" to m)) }
                ?: run { s.message = "Select two meshes" }
            "Mesh_Import", "Points_Import" -> s.fileRequest = AppState.FileRequest("import")
            // MeshGui::RemeshGmsh: 2D algorithm, max / min element size, surface-detection angle -> Gmsh
            "Mesh_RemeshGmsh" -> meshes(s).firstOrNull()?.let { m ->
                s.choose("Meshing algorithm", listOf("2" to "Automatic", "1" to "MeshAdapt", "5" to "Delaunay", "6" to "Frontal", "7" to "BAMG",
                    "8" to "Frontal Quad", "9" to "Packing of Parallelograms", "11" to "Quasi-structured Quad")) { alg ->
                    s.askNumber("Maximum element size (0 = no limit)", 0.0) { mx -> s.askNumber("Minimum element size", 0.0) { mn ->
                        s.askNumber("Angle for surface detection (°)", 40.0, listOf("°")) { ang ->
                            step(s, label, "mesh_remesh_gmsh", mapOf("name" to m, "algorithm" to alg.toInt(), "max_size" to mx, "min_size" to mn, "angle" to ang)) } } } }
            } ?: run { s.message = "Select a mesh" }
            "Mesh_Export", "Points_Export" -> need(s, names(s), "a mesh or point cloud") { n -> s.fileRequest = AppState.FileRequest("export", n, "${s.obj(n.first())?.label ?: "mesh"}.${if (id.startsWith("Points")) "asc" else "stl"}") }
            "Mesh_FromGeometry" -> need(s, names(s), "shapes") { n -> s.askNumber("Surface deviation", 0.1) { dev -> step(s, label, "mesh_from_shape", mapOf("names" to n, "mode" to "Geometry", "linear" to dev)) } }
            "Mesh_FromPartShape", "MeshPart_Mesher" -> need(s, names(s), "shapes") { n -> s.choose("Mesher (Tessellation dialog)", listOf("Standard" to "Standard (deflection)", "Mefisto" to "Mefisto (max edge length)")) { m ->
                if (m == "Standard") s.askNumber("Surface deviation", 0.1) { dev -> s.askNumber("Angular deviation", 28.65, listOf("°")) { ang ->
                    step(s, label, "mesh_from_shape", mapOf("names" to n, "mode" to "Standard", "linear" to dev, "angular" to ang)) } }
                else s.askNumber("Maximum edge length", 1.0) { l -> step(s, label, "mesh_from_shape", mapOf("names" to n, "mode" to "Mefisto", "max_length" to l)) } } }
            "Mesh_VertexCurvature" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "mesh_curvature", mapOf("names" to m)) }
            "Mesh_CurvatureInfo", "Mesh_EvaluateFacet" -> s.selection.firstOrNull { it.sub.startsWith("Face") && s.obj(it.obj)?.type?.startsWith("Mesh::") == true }?.let { r ->
                s.eng({ call("mesh_facet_info", mapOf("name" to r.obj, "facet" to r.sub.removePrefix("Face").toInt() - 1)) as JSONObject }) { j -> info(s, label, j) } }
                ?: run { s.message = "Tap a facet of a mesh first" }
            "Mesh_Evaluation", "Mesh_EvaluateSolid", "Mesh_BoundingBox" -> need(s, meshes(s), "a mesh") { m ->
                s.eng({ call("mesh_evaluate", mapOf("name" to m.first())) as JSONObject }) { j ->
                    if (id == "Mesh_Evaluation") s.choose("Evaluate & repair: ${j.optInt("facets")} facets, solid ${j.optBoolean("solid")}, non-manifolds ${j.optBoolean("nonManifolds")}, " +
                        "flipped ${j.optInt("nonUniformOrientation")}, self-intersections ${j.optBoolean("selfIntersections")}",
                        listOf("all" to "Repair all", "DuplicatedPoints" to "Remove duplicated points", "Degenerations" to "Fix degenerations", "NonManifolds" to "Remove non-manifolds",
                            "Orientation" to "Fix orientation", "SelfIntersections" to "Fix self-intersections", "Folds" to "Remove folds", "info" to "Details")) { k ->
                        if (k == "info") info(s, label, j) else step(s, "Repair mesh", "mesh_modify", mapOf("kind" to "Repair", "names" to m, "opts" to if (k == "all") emptyMap() else mapOf("steps" to listOf(k)))) }
                    else info(s, label, j) } }
            "Mesh_RemoveComponents" -> need(s, meshes(s), "a mesh") { m -> s.askNumber("Remove components with fewer facets than", 10.0, emptyList()) { n -> step(s, label, "mesh_modify", mapOf("kind" to "RemoveComponents", "names" to m, "opts" to mapOf("count" to n.toInt()))) } }
            "Mesh_RemoveCompByHand" -> s.selection.filter { it.sub.startsWith("Face") }.takeIf { it.isNotEmpty() }?.let { rs ->
                step(s, label, "mesh_remove_by_facets", mapOf("name" to rs.first().obj, "facets" to rs.map { it.sub.removePrefix("Face").toInt() - 1 })) }
                ?: run { s.message = "Tap facets of the components to remove" }
            "Mesh_Smoothing" -> need(s, meshes(s), "a mesh") { m -> s.choose("Smoothing", listOf("Taubin" to "Taubin", "Laplace" to "Laplace", "MedianFilter" to "Median filter")) { meth ->
                s.askNumber("Iterations", 4.0, emptyList()) { it2 -> step(s, label, "mesh_modify", mapOf("kind" to "Smoothing", "names" to m, "opts" to mapOf("method" to meth, "iterations" to it2.toInt()))) } } }
            "Mesh_Decimating" -> need(s, meshes(s), "a mesh") { m -> s.askNumber("Reduction (0..1)", 0.9, emptyList()) { red -> s.askNumber("Tolerance", 0.1) { tol ->
                step(s, label, "mesh_modify", mapOf("kind" to "Decimating", "names" to m, "opts" to mapOf("reduction" to red, "tolerance" to tol))) } } }
            "Mesh_HarmonizeNormals", "Mesh_FlipNormals" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "mesh_modify", mapOf("kind" to modify.getValue(id), "names" to m)) }
            "Mesh_FillupHoles" -> need(s, meshes(s), "a mesh") { m -> s.askNumber("Fill holes with up to … edges", 3.0, emptyList()) { n -> step(s, label, "mesh_modify", mapOf("kind" to "FillupHoles", "names" to m, "opts" to mapOf("length" to n.toInt()))) } }
            "Mesh_FillInteractiveHole" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "mesh_fill_hole", mapOf("name" to m.first(), "near" to null)) }
            "Mesh_Scale" -> need(s, meshes(s), "a mesh") { m -> s.askNumber("Scale factor", 1.0, emptyList()) { k -> step(s, label, "mesh_modify", mapOf("kind" to "Scale", "names" to m, "opts" to mapOf("factor" to k))) } }
            "Mesh_BuildRegularSolid" -> s.choose("Regular solid", listOf("Cube", "Cylinder", "Cone", "Sphere", "Ellipsoid", "Torus").map { it to it }) { k -> step(s, label, "mesh_regular_solid", mapOf("kind" to k, "params" to emptyMap<String, Any>())) }
            "Mesh_Merge" -> meshes(s).takeIf { it.size >= 2 }?.let { m -> step(s, label, "mesh_merge", mapOf("names" to m)) } ?: run { s.message = "Select two or more meshes" }
            "Mesh_SplitComponents" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "mesh_split_components", mapOf("names" to m)) }
            "Mesh_Segmentation", "Reen_Segmentation" -> need(s, meshes(s), "a mesh") { m -> s.askNumber("Curvature tolerance", 0.1, emptyList()) { t -> s.askNumber("Minimum facets per segment", 20.0, emptyList()) { mn ->
                step(s, label, "mesh_segmentation", mapOf("name" to m.first(), "kind" to "Curvature", "opts" to mapOf("tol" to t, "min" to mn.toInt()))) } } }
            "Mesh_SegmentationBestFit" -> need(s, meshes(s), "a mesh") { m -> s.choose("Fit", listOf("Plane" to "Planes", "Cylinder" to "Cylinders", "Sphere" to "Spheres")) { k ->
                s.askNumber("Max deviation", 0.1) { dev -> step(s, label, "mesh_segmentation", mapOf("name" to m.first(), "kind" to k, "opts" to mapOf("dev" to dev, "min" to 20))) } } }
            "Mesh_PolyCut", "Mesh_PolyTrim", "Mesh_PolySegm", "Mesh_PolySplit", "Reen_SegmentationManual" -> need(s, meshes(s), "a mesh") { m ->
                val kind = when (id) { "Mesh_PolyCut" -> "Cut"; "Mesh_PolyTrim" -> "Trim"; "Mesh_PolySplit" -> "Split"; else -> "Segm" }
                polygon(s, label) { p -> s.choose("Keep", listOf("inner" to if (kind == "Cut") "Remove inside" else "Inside", "outer" to if (kind == "Cut") "Remove outside" else "Outside")) { io ->
                    step(s, label, "mesh_poly", mapOf("kind" to kind, "names" to m, "polygon" to p.map { it.toList() }, "view_dir" to viewDir(s), "inner" to (io == "inner"))) } } }
            "Mesh_AddFacet" -> need(s, meshes(s), "a mesh") { m -> pick(s, label, listOf("First corner", "Second corner", "Third corner"), 3) { p ->
                step(s, label, "mesh_add_facet", mapOf("name" to m.first(), "pts" to p.map { it.toList() })) } }
            "Mesh_TrimByPlane", "MeshPart_TrimByPlane", "Mesh_SectionByPlane", "MeshPart_SectionByPlane" -> need(s, meshes(s), "a mesh") { m ->
                plane(s, label) { b, n -> step(s, label, "mesh_plane_op", mapOf("kind" to if (id.contains("Trim")) "Trim" else "Section", "names" to m, "base" to b, "normal" to n)) } }
            "Mesh_CrossSections", "MeshPart_CrossSections" -> need(s, meshes(s), "a mesh") { m -> s.choose("Section axis", listOf("Z", "Y", "X").map { it to it }) { ax ->
                s.askNumber("Number of sections", 10.0, emptyList()) { n -> step(s, label, "mesh_cross_sections", mapOf("names" to m, "axis" to ax, "count" to n.toInt())) } } }
            "MeshPart_CurveOnMesh", "Surface_CurveOnMesh" -> need(s, meshes(s), "a mesh") { m -> s.startDraft(DraftTool("MeshCurve", label, listOf("Point on the mesh (Finish to end)"), 2, -1) { p ->
                step(s, label, "mesh_curve_on_mesh", mapOf("name" to m.first(), "pts" to p.map { it.toList() })) }.apply { s.draftSnaps.let { if ("Near" !in it) it.add("Near") } }) }
            "Points_Convert" -> need(s, names(s), "a shape or mesh") { n -> s.askNumber("Point distance (tolerance)", 0.1) { t -> step(s, label, "points_op", mapOf("kind" to "Convert", "names" to n, "opts" to mapOf("tol" to t))) } }
            "Points_Merge" -> need(s, names(s), "point clouds") { n -> step(s, label, "points_op", mapOf("kind" to "Merge", "names" to n)) }
            "Points_Structure" -> need(s, names(s), "a point cloud") { n -> s.askNumber("Grid width (points)", 1.0, emptyList()) { w -> step(s, label, "points_op", mapOf("kind" to "Structure", "names" to n, "opts" to mapOf("width" to w.toInt()))) } }
            "Points_PolyCut" -> need(s, names(s), "a point cloud") { n -> polygon(s, label) { p -> step(s, label, "points_poly_cut", mapOf("names" to n, "polygon" to p.map { it.toList() }, "view_dir" to viewDir(s), "inner" to true)) } }
            "Reen_ApproxCurve", "Reen_ApproxSurface", "Reen_ApproxPlane", "Reen_ApproxCylinder", "Reen_ApproxSphere" -> need(s, names(s), "points, a mesh or a shape") { n ->
                step(s, label, "reen_fit", mapOf("kind" to id.removePrefix("Reen_Approx"), "names" to n)) }
            "Reen_ApproxPolynomial" -> need(s, names(s), "points or a mesh") { n -> s.eng({ call("reen_fit", mapOf("kind" to "Polynomial", "names" to n)) as JSONObject }) { j -> info(s, label, j) } }
            "Reen_SegmentationFromComponents" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "reen_segmentation_from_components", mapOf("names" to m)) }
            "Reen_MeshBoundary" -> need(s, meshes(s), "a mesh") { m -> step(s, label, "reen_mesh_boundary", mapOf("names" to m)) }
            else -> return false
        }
        return true
    }
}
