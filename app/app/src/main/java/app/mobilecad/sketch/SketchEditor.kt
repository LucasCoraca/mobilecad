package app.mobilecad.sketch

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.NumpadRequest
import app.mobilecad.freecad.TextPrompt
import app.mobilecad.ui.ANGLE_UNITS
import app.mobilecad.ui.LENGTH_UNITS
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import app.mobilecad.ui.OS

/** Sketch tools; each produces the same geometry/constraints as the FreeCAD tool of the same name. */
enum class SketchTool(val label: String, val points: Int) {
    SELECT("Select", 0), POLYLINE("Polyline", -1), LINE("Line", 2), RECTANGLE("Rectangle", 2), CENTER_RECTANGLE("Centered rectangle", 2),
    CIRCLE("Circle", 2), CIRCLE3("3-point circle", 3), ARC_CENTER("Arc", 3), ARC3("3-point arc", 3), POINT("Point", 1), SLOT("Slot", 3),
    POLYGON3("Triangle", 2), POLYGON4("Square", 2), POLYGON5("Pentagon", 2), POLYGON6("Hexagon", 2), POLYGON7("Heptagon", 2), POLYGON8("Octagon", 2),
    ELLIPSE("Ellipse", 3), ELLIPSE3("Ellipse by 3 points", 3), ARC_ELLIPSE("Arc of ellipse", 4), ARC_HYPERBOLA("Arc of hyperbola", 4),
    ARC_PARABOLA("Arc of parabola", 4), BSPLINE("B-spline", -1), BSPLINE_PERIODIC("Periodic B-spline", -1), BSPLINE_INTERP("B-spline by knots", -1),
    BSPLINE_INTERP_PERIODIC("Periodic B-spline by knots", -1), OBLONG("Rounded rectangle", 2), ARC_SLOT("Arc slot", 4),
    // action tools (points == 0: they react to taps on existing geometry instead of placing points)
    TRIM("Trim", 0), DIMENSION("Dimension", 0), FILLET("Fillet", 0), CHAMFER("Chamfer", 0), EXTEND("Extend", 0), SPLIT("Split", 0), JOIN("Join curves", 0),
    SYMMETRY("Symmetry", 0), TRANSLATE("Move", 0), COPY("Copy", 0), ROTATE("Rotate", 0), SCALE("Scale", 0), ARRAY("Rectangular array", 0),
    PROJECTION("External projection", 0), INTERSECTION("External intersection", 0),
    TEXT("Text", 2), INSERT_KNOT("Insert knot", 0), KNOT_UP("Increase knot multiplicity", 0), KNOT_DOWN("Decrease knot multiplicity", 0), PASTE("Paste", 0)
}

data class P2(val x: Double, val y: Double) { fun d(o: P2) = hypot(x - o.x, y - o.y) }

/** Sketch element reference: geo id + FreeCAD PointPos (0 edge, 1 start, 2 end, 3 center). Axes: -1 H_Axis, -2 V_Axis. */
data class SkSel(val geo: Int, val pos: Int)

/** B-spline data for FreeCAD's B-spline information overlays. */
class BsInfo(val degree: Int, val poles: List<P2>, val weights: List<Double>, val knots: List<Pair<P2, Int>>, val comb: List<Pair<P2, P2>>)

class SkGeo(val i: Int, val type: String, val construction: Boolean, val a: P2?, val b: P2?, val c: P2?, val r: Double, val a0: Double, val a1: Double, val poly: List<P2>,
            val bs: BsInfo? = null, val external: Boolean = false) {
    val isLine get() = type == "LineSegment"; val isCircle get() = type == "Circle"; val isArc get() = type == "ArcOfCircle"; val isPoint get() = type == "Point"
    fun polyline(): List<P2> = when {
        isLine -> listOf(a!!, b!!)
        isCircle -> (0..72).map { P2(c!!.x + r * cos(it * Math.PI / 36), c.y + r * sin(it * Math.PI / 36)) }
        isArc -> (0..48).map { val t = a0 + (a1 - a0) * it / 48; P2(c!!.x + r * cos(t), c.y + r * sin(t)) }
        else -> poly
    }
    fun points(): List<Pair<Int, P2>> = buildList {
        if (isPoint) { add(1 to a!!); return@buildList }
        a?.let { add(1 to it) }; b?.let { add(2 to it) }; c?.let { add(3 to it) }
    }
}

/** A part edge lying on the sketch plane (engine sketch_model_refs): snapped to like sketch geometry; the engine projects it
 *  as external geometry when a constraint uses it ("Obj:EdgeN"). */
class ModelCurve(val ref: String, val kind: String, val pts: List<P2>, val c: P2?, val r: Double)

class SkCons(val i: Int, val type: String, val first: Int, val firstPos: Int, val second: Int, val secondPos: Int, val third: Int, val value: Double, val driving: Boolean, val label: String,
             /** FreeCAD's label offset (Constraint.LabelDistance / LabelPosition), changed while the label is dragged */
             var ld: Double = 10.0, var lp: Double = 0.0, val active: Boolean = true, val cname: String = "", val second2: Int = -2000)

/** Where a dimension is drawn: its label point and the lines (pairs of points) of extension / dimension lines. */
class DimLayout(val label: P2, val lines: List<Pair<P2, P2>>)

class SketchEditor(private val app: AppState, val name: String) {
    var tool by mutableStateOf(SketchTool.POLYLINE)
    var construction by mutableStateOf(false)
    var dof by mutableStateOf(-1); var fully by mutableStateOf(false)
    var status by mutableStateOf("")
    /** A constraint picked with nothing selected (DrawSketchHandlerGenConstraint): taps select its elements and it is
     *  applied as soon as they form a valid selection; it stays active for the next one until another tool is chosen. */
    var armed by mutableStateOf<String?>(null)
    var geos: List<SkGeo> = emptyList(); var cons: List<SkCons> = emptyList()
    /** The part's vertices / edges on the sketch plane (sketch on a face, snap to its edges and corners). */
    var modelPts: List<Pair<String, P2>> = emptyList(); var modelCurves: List<ModelCurve> = emptyList()
    var conflicting: Set<Int> = emptySet(); var redundant: Set<Int> = emptySet()
    var malformed: Set<Int> = emptySet(); var partiallyRedundant: Set<Int> = emptySet()
    /** sketch → global placement (3×4, row major) */
    var m = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    val selected = mutableListOf<SkSel>()
    /** Bumped on every change (selection included) so the Compose selection bar recomposes. */
    val tick = androidx.compose.runtime.mutableIntStateOf(0)
    /** Selected sketch curves (not points / external geometry) and whether they are all construction geometry. */
    fun selectedCurves() = selected.filter { it.geo >= 0 && it.pos == 0 }.map { it.geo }.distinct()
    fun selectionIsConstruction() = selectedCurves().let { g -> g.isNotEmpty() && g.all { i -> geos.firstOrNull { it.i == i }?.construction == true } }
    private val pending = mutableListOf<Pair<P2, List<JSONArray>>>()     // placed points + their auto-constraints
    var preview: Snap? = null
    private var chainStart: P2? = null
    private var segments = 0
    var onChanged: () -> Unit = {}
    var viewNormalRequest: (() -> Unit)? = null

    class Snap(val p: P2, val auto: List<JSONArray>, val kind: String, val guide: P2? = null, val guide2: P2? = null)

    // ------------------------------------------------------------------ engine sync
    fun loadModelRefs() = app.eng({ call("sketch_model_refs", mapOf("name" to name)) as JSONObject }) { j ->
        val ps = j.getJSONArray("points")
        modelPts = (0 until ps.length()).map { i -> ps.getJSONArray(i).let { it.getString(0) to P2(it.getDouble(2), it.getDouble(3)) } }
        val cs = j.getJSONArray("curves")
        modelCurves = (0 until cs.length()).map { i -> cs.getJSONObject(i).let { o -> val a = o.getJSONArray("pts")
            ModelCurve(o.getString("ref"), o.getString("kind"), (0 until a.length()).map { k -> a.getJSONArray(k).let { P2(it.getDouble(0), it.getDouble(1)) } },
                o.optJSONArray("c")?.let { P2(it.getDouble(0), it.getDouble(1)) }, o.optDouble("r", 0.0)) } }
    }
    fun reload(keepView: Boolean = false) = app.eng({ call("sketch_state", mapOf("name" to name)) as JSONObject }) { load(it); if (!keepView) viewNormal() }

    private fun load(s: JSONObject) {
        val pl = s.getJSONArray("placement"); m = DoubleArray(12) { pl.getDouble(it) }
        val ga = s.getJSONArray("geometry")
        geos = List(ga.length()) { k ->
            val g = ga.getJSONObject(k)
            fun p(key: String) = g.optJSONArray(key)?.let { P2(it.getDouble(0), it.getDouble(1)) }
            SkGeo(g.getInt("i"), g.getString("type"), g.getBoolean("construction"), p("a") ?: p("p"), p("b"), p("c"), g.optDouble("r", 0.0),
                g.optDouble("a0", 0.0), g.optDouble("a1", 0.0), g.optJSONArray("poly")?.let { a -> List(a.length()) { P2(a.getJSONArray(it).getDouble(0), a.getJSONArray(it).getDouble(1)) } } ?: emptyList(),
                g.optJSONObject("bspline")?.let { b ->
                    fun pts(k: String) = b.getJSONArray(k).let { a -> List(a.length()) { P2(a.getJSONArray(it).getDouble(0), a.getJSONArray(it).getDouble(1)) } }
                    val kn = b.getJSONArray("knots"); val cb = b.getJSONArray("comb"); val w = b.getJSONArray("weights")
                    BsInfo(b.getInt("degree"), pts("poles"), List(w.length()) { w.getDouble(it) },
                        List(kn.length()) { kn.getJSONArray(it).let { x -> P2(x.getDouble(0), x.getDouble(1)) to x.getInt(2) } },
                        List(cb.length()) { cb.getJSONArray(it).let { x -> P2(x.getDouble(0), x.getDouble(1)) to P2(x.getDouble(2), x.getDouble(3)) } })
                }, g.optBoolean("external"))
        }
        val ca = s.getJSONArray("constraints")
        cons = List(ca.length()) { k -> val c = ca.getJSONObject(k)
            SkCons(c.getInt("i"), c.getString("type"), c.getInt("first"), c.getInt("firstPos"), c.getInt("second"), c.getInt("secondPos"), c.getInt("third"),
                c.getDouble("value"), c.getBoolean("driving"), c.optString("label"), c.optDouble("ld", 10.0), c.optDouble("lp", 0.0),
                c.optBoolean("active", true), c.optString("name")) }
        dof = s.getInt("dof"); fully = s.getBoolean("fully")
        fun set(k: String) = s.getJSONArray(k).let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
        conflicting = set("conflicting"); redundant = set("redundant"); malformed = set("malformed")
        partiallyRedundant = if (s.has("partially_redundant")) set("partially_redundant") else emptySet()
        status = when {
            conflicting.isNotEmpty() -> "Conflicting constraints: ${conflicting.joinToString { "#$it" }}"
            redundant.isNotEmpty() -> "Redundant constraints: ${redundant.joinToString { "#$it" }}"
            geos.none { !it.external } -> if (geos.isEmpty()) "Empty sketch" else "Only external geometry"
            fully -> "Fully constrained"
            else -> "$dof degrees of freedom"
        }.let { st -> armed?.let { "${armedHint(it)}  ($st)" } ?: st }
        app.sceneVersion.intValue++; onChanged()
    }

    /** Each sketch edit is one undoable FreeCAD transaction, named like the desktop command. */
    private fun exec(op: String, args: Map<String, Any?>) = app.eng({
        begin(LABELS[op] ?: op.removePrefix("sketch_").replace('_', ' '))
        try { val r = call(op, mapOf("name" to name) + args) as JSONObject; commit(); r to undoState() } catch (e: Throwable) { abort(); throw e }
    }) { (r, h) -> load(r); app.setHistory(h) }

    private companion object {
        val LABELS = mapOf("sketch_polyline" to "Add sketch line", "sketch_rectangle" to "Add sketch box", "sketch_center_rectangle" to "Add centered sketch box",
            "sketch_circle" to "Add sketch circle", "sketch_circle3" to "Add sketch circle", "sketch_arc_center" to "Add sketch arc", "sketch_arc3" to "Add sketch arc",
            "sketch_point" to "Add sketch point", "sketch_slot" to "Add slot", "sketch_polygon" to "Add hexagon", "sketch_constrain" to "Add constraint",
            "sketch_set_datum" to "Modify sketch constraints", "sketch_set_datum_expression" to "Modify sketch constraints", "sketch_delete" to "Delete",
            "sketch_toggle_construction" to "Toggle draft from/to draft", "sketch_move_point" to "Drag point", "sketch_trim" to "Trim edge",
            "sketch_fillet" to "Create fillet", "sketch_external" to "Add external geometry", "sketch_toggle_driving" to "Toggle constraint to driving/reference")
    }

    // ------------------------------------------------------------------ plane math
    fun toWorld(p: P2) = doubleArrayOf(m[0] * p.x + m[1] * p.y + m[3], m[4] * p.x + m[5] * p.y + m[7], m[8] * p.x + m[9] * p.y + m[11])
    val normal get() = doubleArrayOf(m[2], m[6], m[10])
    /** world ray → sketch point (null if parallel). */
    fun fromRay(o: FloatArray): P2? {
        val n = normal; val org = doubleArrayOf(m[3], m[7], m[11])
        val dn = o[3] * n[0] + o[4] * n[1] + o[5] * n[2]; if (abs(dn) < 1e-9) return null
        val t = ((org[0] - o[0]) * n[0] + (org[1] - o[1]) * n[1] + (org[2] - o[2]) * n[2]) / dn
        val w = doubleArrayOf(o[0] + o[3] * t - org[0], o[1] + o[4] * t - org[1], o[2] + o[5] * t - org[2])
        return P2(w[0] * m[0] + w[1] * m[4] + w[2] * m[8], w[0] * m[1] + w[1] * m[5] + w[2] * m[9])
    }
    /** Sketcher_ViewSketch; queued until the viewport attaches its handler. */
    var pendingNormal = false
    fun viewNormal() { val r = viewNormalRequest; if (r != null) { pendingNormal = false; r() } else pendingNormal = true }

    // ------------------------------------------------------------------ inference (FreeCAD autoconstraints, touch tolerance)
    fun snap(p: P2, tolMm: Double): Snap {
        if (!snapToGrid) return Snap(p, emptyList(), "free")      // Sketcher_Snap off: no snapping / inference
        val local = pending.size     // auto constraints refer to the point index within the new shape; resolved at commit
        // 1. points: end points, centres, the origin -> Coincident (FreeCAD preselected vertex)
        var best: Triple<SkSel, P2, String>? = null; var bd = tolMm
        for (g in geos) for ((pos, q) in g.points()) { val d = q.d(p); if (d < bd) { bd = d; best = Triple(SkSel(g.i, pos), q, if (pos == 3) "center" else "point") } }
        if (P2(0.0, 0.0).d(p) < bd) { bd = P2(0.0, 0.0).d(p); best = Triple(SkSel(-1, 1), P2(0.0, 0.0), "origin") }
        best?.let { (s, q, k) -> return Snap(q, listOf(JSONArray(listOf("Coincident", local, s.geo, s.pos))), k) }
        // 1b. the part's corners and circle centres on the sketch plane (projected on use)
        var mb: Triple<String, P2, Int>? = null; var md = tolMm
        for ((ref, q) in modelPts) { val d = q.d(p); if (d < md) { md = d; mb = Triple(ref, q, 1) } }
        for (m in modelCurves) if (m.kind == "circle" && m.c != null) { val d = m.c.d(p); if (d < md) { md = d; mb = Triple(m.ref, m.c, 3) } }
        mb?.let { (ref, q, pos) -> return Snap(q, listOf(JSONArray(listOf("Coincident", local, ref, pos))), if (pos == 3) "model center" else "model point") }
        // 2. midpoints (SnapManager::snapToLineMiddle / snapToArcMiddle): a line's middle -> Symmetric about its ends
        //    (symmetric constraint); an arc's middle -> point on the arc, as FreeCAD does
        for (g in geos) {
            if (g.isLine) { val m = P2((g.a!!.x + g.b!!.x) / 2, (g.a.y + g.b.y) / 2)
                if (m.d(p) < tolMm) return Snap(m, listOf(JSONArray(listOf("Symmetric", local, g.i))), "midpoint") }
            if (g.isArc) { val t = (g.a0 + g.a1) / 2; val m = P2(g.c!!.x + g.r * cos(t), g.c.y + g.r * sin(t))
                if (m.d(p) < tolMm) return Snap(m, listOf(JSONArray(listOf("PointOnObject", local, g.i))), "midpoint") }
        }
        for (m in modelCurves) if (m.kind == "line" && m.pts.size >= 2) { val a = m.pts.first(); val b = m.pts.last(); val mid = P2((a.x + b.x) / 2, (a.y + b.y) / 2)
            if (mid.d(p) < tolMm) return Snap(mid, listOf(JSONArray(listOf("Symmetric", local, m.ref))), "model midpoint") }
        // 3. point on curve / axis
        for (g in geos) if (!g.isPoint) {
            val d = dist(g, p); if (d < tolMm * 0.6) return Snap(project(g, p), listOf(JSONArray(listOf("PointOnObject", local, g.i))), "on curve")
        }
        for (m in modelCurves) {
            var bestQ: P2? = null; var bdq = tolMm * 0.6
            for ((a, b) in m.pts.zipWithNext()) { val q = closest(p, a, b); val d = q.d(p); if (d < bdq) { bdq = d; bestQ = q } }
            bestQ?.let { q -> return Snap(q, listOf(JSONArray(listOf("PointOnObject", local, m.ref))), "on model edge") }
        }
        if (abs(p.y) < tolMm * 0.6) return Snap(P2(p.x, 0.0), listOf(JSONArray(listOf("PointOnObject", local, -1))), "on axis")
        if (abs(p.x) < tolMm * 0.6) return Snap(P2(0.0, p.y), listOf(JSONArray(listOf("PointOnObject", local, -2))), "on axis")
        // 4. horizontal / vertical with the previous point (DrawSketchHandler::seekAlignmentAutoConstraint)
        val last = pending.lastOrNull()?.first
        if (last != null) {
            if (abs(p.y - last.y) < tolMm * 0.5) return Snap(P2(p.x, last.y), listOf(JSONArray(listOf("Horizontal", local))), "horizontal", last)
            if (abs(p.x - last.x) < tolMm * 0.5) return Snap(P2(last.x, p.y), listOf(JSONArray(listOf("Vertical", local))), "vertical", last)
        }
        // 5. alignment with other points (inference lines): the position lines up, no constraint is added
        var ax: P2? = null; var ay: P2? = null; var bx = tolMm * 0.5; var by = tolMm * 0.5
        for (g in geos) for ((_, q) in g.points()) {
            if (q == last) continue
            abs(p.x - q.x).let { if (it < bx) { bx = it; ax = q } }; abs(p.y - q.y).let { if (it < by) { by = it; ay = q } }
        }
        if (ax != null || ay != null) return Snap(P2(ax?.x ?: p.x, ay?.y ?: p.y), emptyList(), "aligned", ax ?: ay, if (ax != null && ay != null) ay else null)
        return Snap(p, emptyList(), "free")
    }

    private fun dist(g: SkGeo, p: P2) = when {
        g.isCircle -> abs(p.d(g.c!!) - g.r)
        else -> g.polyline().zipWithNext().minOfOrNull { (a, b) -> seg(p, a, b) } ?: Double.MAX_VALUE
    }
    private fun closest(p: P2, a: P2, b: P2): P2 {
        val dx = b.x - a.x; val dy = b.y - a.y; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-12) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0.0, 1.0)
        return P2(a.x + t * dx, a.y + t * dy)
    }
    private fun seg(p: P2, a: P2, b: P2): Double {
        val dx = b.x - a.x; val dy = b.y - a.y; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-12) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0.0, 1.0)
        return p.d(P2(a.x + t * dx, a.y + t * dy))
    }
    private fun project(g: SkGeo, p: P2): P2 = when {
        g.isCircle || g.isArc -> { val a = atan2(p.y - g.c!!.y, p.x - g.c.x); P2(g.c.x + g.r * cos(a), g.c.y + g.r * sin(a)) }
        g.isLine -> { val a = g.a!!; val b = g.b!!; val dx = b.x - a.x; val dy = b.y - a.y; val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-12) 0.0 else ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2; P2(a.x + t * dx, a.y + t * dy) }
        else -> p
    }

    // ------------------------------------------------------------------ tools
    fun selectTool(t: SketchTool) {
        finishChain(); actionPicks.clear(); armed = null
        // selection-based tools keep the current selection (Symmetry, Move, Rotate ... act on it)
        if (t !in setOf(SketchTool.SYMMETRY, SketchTool.TRANSLATE, SketchTool.COPY, SketchTool.ROTATE, SketchTool.SCALE, SketchTool.ARRAY)) { selected.clear(); selectedCons.clear() }
        tool = t; status = hint(); onChanged()
    }
    fun hover(s: Snap?) { preview = s; if (s != null) lastHover = s.p; onChanged() }

    /** Finger lifted at a snapped point. */
    fun place(s: Snap) {
        preview = null
        // a second tap on the point just placed would make a zero-length curve (OCC "Both points are equal")
        if (pending.lastOrNull()?.first?.let { it.d(s.p) < 1e-9 } == true) { onChanged(); return }
        pending.add(s.p to s.auto)
        val pts = pending.map { it.first }
        fun auto(map: (Int) -> Pair<Int, Int>?) = JSONArray().also { arr ->
            pending.forEachIndexed { k, (_, autos) -> val (geo, pos) = map(k) ?: return@forEachIndexed
                autos.forEach { a -> val t = a.getString(0)
                    when (t) { "Coincident" -> arr.put(JSONArray(listOf(t, geo, pos, a.get(2), a.getInt(3))))
                        "PointOnObject", "Symmetric" -> arr.put(JSONArray(listOf(t, geo, pos, a.get(2))))
                        "Horizontal", "Vertical" -> if (tool == SketchTool.POLYLINE || tool == SketchTool.LINE) arr.put(JSONArray(listOf(t, k - 1))) } } }
            typedDims.forEach { arr.put(it) } }
        fun xy(p: P2) = listOf(p.x, p.y)
        val c = construction
        when (tool) {
            SketchTool.POLYLINE -> if (pts.size >= 2) {
                val a = pts[pts.size - 2]; val b = pts.last()
                val start = chainStart ?: a
                val closes = segments > 0 && b.d(start) < 1e-9
                val autos = JSONArray().also { arr ->
                    s.auto.forEach { x -> when (x.getString(0)) {
                        "Coincident" -> arr.put(JSONArray(listOf("Coincident", 0, 2, x.get(2), x.getInt(3))))
                        "PointOnObject", "Symmetric" -> arr.put(JSONArray(listOf(x.getString(0), 0, 2, x.get(2))))
                        "Horizontal", "Vertical" -> arr.put(JSONArray(listOf(x.getString(0), 0))) } }
                    if (segments == 0) pending[0].second.forEach { x -> when (x.getString(0)) {
                        "Coincident" -> arr.put(JSONArray(listOf("Coincident", 0, 1, x.get(2), x.getInt(3))))
                        "PointOnObject", "Symmetric" -> arr.put(JSONArray(listOf(x.getString(0), 0, 1, x.get(2)))) } }
                    typedDims.forEach { arr.put(it) } }
                exec("sketch_polyline", mapOf("points" to listOf(xy(a), xy(b)), "construction" to c, "auto" to autos, "chain" to (segments > 0)))
                if (segments == 0) chainStart = a
                segments++
                pending.clear()
                if (closes) { chainStart = null; segments = 0 } else pending.add(b to emptyList())
            }
            SketchTool.LINE -> if (pts.size == 2) { exec("sketch_polyline", mapOf("points" to pts.map(::xy), "construction" to c,
                "auto" to auto { k -> 0 to (k + 1) })); pending.clear() }
            SketchTool.RECTANGLE -> if (pts.size == 2) { exec("sketch_rectangle", mapOf("c1" to xy(pts[0]), "c3" to xy(pts[1]), "construction" to c,
                "auto" to auto { k -> if (k == 0) 0 to 1 else 1 to 2 })); pending.clear() }
            SketchTool.CENTER_RECTANGLE -> if (pts.size == 2) { exec("sketch_center_rectangle", mapOf("center" to xy(pts[0]), "corner" to xy(pts[1]), "construction" to c,
                "auto" to auto { k -> if (k == 0) 4 to 1 else 1 to 2 })); pending.clear() }
            SketchTool.CIRCLE -> if (pts.size == 2) { exec("sketch_circle", mapOf("center" to xy(pts[0]), "radius" to pts[0].d(pts[1]), "construction" to c,
                "auto" to auto { k -> if (k == 0) 0 to 3 else null })); pending.clear() }
            SketchTool.CIRCLE3 -> if (pts.size == 3) { exec("sketch_circle3", mapOf("p1" to xy(pts[0]), "p2" to xy(pts[1]), "p3" to xy(pts[2]), "construction" to c)); pending.clear() }
            SketchTool.ARC_CENTER -> if (pts.size == 3) { exec("sketch_arc_center", mapOf("center" to xy(pts[0]), "start" to xy(pts[1]), "end" to xy(pts[2]), "construction" to c,
                "auto" to auto { k -> when (k) { 0 -> 0 to 3; 1 -> 0 to 1; else -> 0 to 2 } })); pending.clear() }
            SketchTool.ARC3 -> if (pts.size == 3) { exec("sketch_arc3", mapOf("start" to xy(pts[0]), "end" to xy(pts[1]), "onarc" to xy(pts[2]), "construction" to c)); pending.clear() }
            SketchTool.POINT -> { exec("sketch_point", mapOf("p" to xy(pts[0]), "auto" to auto { 0 to 1 })); pending.clear() }
            SketchTool.SLOT -> if (pts.size == 3) { exec("sketch_slot", mapOf("c1" to xy(pts[0]), "c2" to xy(pts[1]), "radius" to maxOf(0.1, seg(pts[2], pts[0], pts[1])), "construction" to c)); pending.clear() }
            SketchTool.POLYGON3, SketchTool.POLYGON4, SketchTool.POLYGON5, SketchTool.POLYGON6, SketchTool.POLYGON7, SketchTool.POLYGON8 -> if (pts.size == 2) {
                exec("sketch_polygon", mapOf("center" to xy(pts[0]), "corner" to xy(pts[1]), "sides" to tool.name.removePrefix("POLYGON").toInt(), "construction" to c)); pending.clear() }
            SketchTool.ELLIPSE -> if (pts.size == 3) { exec("sketch_ellipse", mapOf("center" to xy(pts[0]), "major" to xy(pts[1]), "minor_pt" to xy(pts[2]), "construction" to c)); pending.clear() }
            SketchTool.ELLIPSE3 -> if (pts.size == 3) { exec("sketch_ellipse3", mapOf("p1" to xy(pts[0]), "p2" to xy(pts[1]), "p3" to xy(pts[2]), "construction" to c)); pending.clear() }
            SketchTool.ARC_ELLIPSE -> if (pts.size == 4) { exec("sketch_arc_of_ellipse", mapOf("center" to xy(pts[0]), "major" to xy(pts[1]), "start" to xy(pts[2]), "end" to xy(pts[3]), "construction" to c)); pending.clear() }
            SketchTool.ARC_HYPERBOLA -> if (pts.size == 4) { exec("sketch_arc_of_hyperbola", mapOf("center" to xy(pts[0]), "major" to xy(pts[1]), "minor_pt" to xy(pts[2]), "end" to xy(pts[3]), "construction" to c)); pending.clear() }
            SketchTool.ARC_PARABOLA -> if (pts.size == 4) { exec("sketch_arc_of_parabola", mapOf("focus" to xy(pts[0]), "vertex" to xy(pts[1]), "start" to xy(pts[2]), "end" to xy(pts[3]), "construction" to c)); pending.clear() }
            SketchTool.OBLONG -> if (pts.size == 2) { exec("sketch_oblong", mapOf("c1" to xy(pts[0]), "c3" to xy(pts[1]), "construction" to c)); pending.clear() }
            SketchTool.ARC_SLOT -> if (pts.size == 4) { exec("sketch_arc_slot", mapOf("center" to xy(pts[0]), "start" to xy(pts[1]), "end" to xy(pts[2]),
                "width" to 2 * kotlin.math.abs(pts[3].d(pts[0]) - pts[1].d(pts[0])), "construction" to c)); pending.clear() }
            SketchTool.TEXT -> if (pts.size == 2) { val a = xy(pts[0]); val b = xy(pts[1]); pending.clear()
                app.textPrompt = TextPrompt("Sketch text", "Text") { t -> exec("sketch_text", mapOf("start" to a, "end" to b, "text" to t, "construction" to c)) } }
            SketchTool.BSPLINE, SketchTool.BSPLINE_PERIODIC, SketchTool.BSPLINE_INTERP, SketchTool.BSPLINE_INTERP_PERIODIC -> {}   // finished by double tap
            else -> pending.clear()
        }
        if (pending.isEmpty() || tool == SketchTool.POLYLINE) typedDims.clear()
        if (pending.isNotEmpty()) status = hint()
        drawStep++
        onChanged()
    }

    /** What the current tool wants next (FreeCAD's tool hints). */
    private fun hint(): String {
        val n = pending.size
        return when (tool) {
            SketchTool.SELECT -> "Tap geometry to select, drag points or curves to move"
            SketchTool.POLYLINE -> if (n == 0) "Polyline: tap the start point" else "Tap the next point · double tap to end"
            SketchTool.LINE -> if (n == 0) "Line: tap the start point" else "Tap the end point or type Length"
            SketchTool.RECTANGLE -> if (n == 0) "Rectangle: tap the first corner" else "Tap the opposite corner or type Width / Height"
            SketchTool.CENTER_RECTANGLE -> if (n == 0) "Centered rectangle: tap the center" else "Tap a corner or type Width / Height"
            SketchTool.CIRCLE -> if (n == 0) "Circle: tap the center" else "Tap a point on the circle or type Diameter"
            SketchTool.ARC_CENTER -> listOf("Arc: tap the center", "Tap the start point or type Radius", "Tap the end point").getOrElse(n) { "" }
            SketchTool.DIMENSION -> "Dimension: tap a line, circle, or two points"
            SketchTool.TRIM -> "Trim: tap the part of a curve to remove"
            SketchTool.PROJECTION -> "External projection: tap edges or vertices of the model"
            SketchTool.INTERSECTION -> "External intersection: tap faces or edges crossing the sketch plane"
            else -> if (tool.points != 0) "${tool.label}: tap point ${n + 1}" else tool.label
        } + if (construction && tool.points != 0) " (construction)" else ""
    }

    // ------------------------------------------------------------------ values typed while drawing (FreeCAD 1.x on-view parameters)
    /** Dimensions entered for the shape being drawn; they become its constraints when it is created. */
    private val typedDims = mutableListOf<JSONArray>()
    var drawStep by mutableStateOf(0)
    /** The values the user can type for the next point of the current tool. */
    fun inputs(): List<String> = when (tool) {
        SketchTool.LINE, SketchTool.POLYLINE -> if (pending.isNotEmpty()) listOf("Length", "Angle") else emptyList()
        SketchTool.RECTANGLE, SketchTool.CENTER_RECTANGLE -> if (pending.size == 1) listOf("Width", "Height") else emptyList()
        SketchTool.CIRCLE -> if (pending.size == 1) listOf("Diameter") else emptyList()
        SketchTool.ARC_CENTER -> if (pending.size == 1) listOf("Radius") else emptyList()
        SketchTool.POLYGON3, SketchTool.POLYGON4, SketchTool.POLYGON5, SketchTool.POLYGON6, SketchTool.POLYGON7, SketchTool.POLYGON8 ->
            if (pending.size == 1) listOf("Radius") else emptyList()
        else -> emptyList()
    }
    /** Where the finger last was (sets the direction / quadrant of a typed value). */
    private var lastHover: P2? = null
    fun enterValue(what: String) {
        val L = LENGTH_UNITS; val first = pending.firstOrNull()?.first ?: return; val last = pending.last().first
        val dir = lastHover?.let { atan2(it.y - last.y, it.x - last.x) } ?: 0.0
        fun put(p: P2, vararg dims: JSONArray) { typedDims.addAll(dims); place(Snap(p, emptyList(), "typed")) }
        when (what) {
            "Length", "Angle" -> {
                fun line(len: Double, ang: Double, angTyped: Boolean) = put(P2(last.x + len * cos(ang), last.y + len * sin(ang)),
                    *listOfNotNull(JSONArray(listOf("Distance", 0, len)), if (angTyped) JSONArray(listOf("Angle", 0, ang)) else null).toTypedArray())
                if (what == "Length") ask("Length", "%.2f mm".format(lastHover?.d(last) ?: 10.0), L) { len -> if (len > 0) line(len, dir, false) }
                else ask("Angle", "%.1f °".format(Math.toDegrees(dir)), ANGLE_UNITS) { a -> ask("Length", "%.2f mm".format(lastHover?.d(last) ?: 10.0), L) { len ->
                    if (len > 0) line(len, Math.toRadians(a), true) } }
            }
            "Width", "Height" -> {
                val h0 = lastHover; val sx = if (h0 != null && h0.x < first.x) -1.0 else 1.0; val sy = if (h0 != null && h0.y < first.y) -1.0 else 1.0
                ask("Width", "%.2f mm".format(h0?.let { abs(it.x - first.x) } ?: 10.0), L) { w -> ask("Height", "%.2f mm".format(h0?.let { abs(it.y - first.y) } ?: 10.0), L) { h ->
                    if (w <= 0 || h <= 0) return@ask
                    val k = if (tool == SketchTool.CENTER_RECTANGLE) 0.5 else 1.0
                    val dx = sx * w * k; val dy = sy * h * k
                    // sketch_rectangle: edge 0 runs along x when the diagonal goes up-right / down-left, else along y
                    val horiz = if (dx * dy > 0) 0 else 1
                    put(P2(first.x + dx, first.y + dy), JSONArray(listOf("Distance", horiz, w)), JSONArray(listOf("Distance", 1 - horiz, h))) } }
            }
            "Diameter" -> ask("Diameter", "%.2f mm".format(2 * (lastHover?.d(first) ?: 5.0)), L) { d -> if (d > 0)
                put(P2(first.x + d / 2 * cos(dir), first.y + d / 2 * sin(dir)), JSONArray(listOf("Diameter", 0, d))) }
            "Radius" -> ask("Radius", "%.2f mm".format(lastHover?.d(first) ?: 5.0), L) { r -> if (r > 0)
                put(P2(first.x + r * cos(dir), first.y + r * sin(dir)), *(if (tool == SketchTool.ARC_CENTER) arrayOf(JSONArray(listOf("Radius", 0, r))) else emptyArray())) }
        }
    }

    /** Double tap / Stop: multi-point tools (B-splines) commit; polylines end. */
    fun finishShape() {
        val pts = pending.map { listOf(it.first.x, it.first.y) }
        when (tool) {
            SketchTool.BSPLINE, SketchTool.BSPLINE_PERIODIC, SketchTool.BSPLINE_INTERP, SketchTool.BSPLINE_INTERP_PERIODIC -> if (pts.size >= (if (tool.name.endsWith("PERIODIC")) 3 else 2))
                exec("sketch_bspline", mapOf("poles" to pts, "periodic" to tool.name.endsWith("PERIODIC"), "interpolate" to tool.name.startsWith("BSPLINE_INTERP"), "construction" to construction))
            else -> {}
        }
        finishChain()
    }

    fun pendingPoints(): List<P2> = pending.map { it.first }
    fun finishChain() { pending.clear(); preview = null; chainStart = null; segments = 0; typedDims.clear(); lastHover = null; drawStep++; onChanged() }
    fun hasPending() = pending.isNotEmpty()

    // ------------------------------------------------------------------ selection / editing
    fun pickElement(p: P2, tolMm: Double): SkSel? {
        var best: SkSel? = null; var bd = tolMm
        for (g in geos) for ((pos, q) in g.points()) { val d = q.d(p); if (d < bd) { bd = d; best = SkSel(g.i, pos) } }
        if (best != null) return best
        if (P2(0.0, 0.0).d(p) < tolMm) return SkSel(-1, 1)
        bd = tolMm * 0.8
        for (g in geos) if (!g.isPoint) { val d = dist(g, p); if (d < bd) { bd = d; best = SkSel(g.i, 0) } }
        if (best == null && abs(p.y) < bd) best = SkSel(-1, 0)
        if (best == null && abs(p.x) < bd) best = SkSel(-2, 0)
        return best
    }

    private val actionPicks = mutableListOf<Pair<P2, SkSel?>>()
    private fun geoSel() = selected.filter { it.pos == 0 && it.geo >= 0 }.map { it.geo }.distinct()
    private fun num(v: String) = Regex("-?[0-9.]+").find(v.replace(',', '.'))?.value?.toDoubleOrNull()
    private fun ask(title: String, initial: String, units: List<String>, f: (Double) -> Unit) {
        app.numpad = NumpadRequest(title, initial, units) { v -> app.numpad = null; num(v)?.let(f) }
    }

    fun tap(p: P2, tolMm: Double) {
        val e = pickElement(p, tolMm)
        if (tool.points == 0 && tool !in setOf(SketchTool.SELECT, SketchTool.TRIM, SketchTool.DIMENSION)) { action(p, e); return }
        if (tool == SketchTool.SELECT && e == null) { consAt(p, tolMm)?.let { c -> toggleCons(c); onChanged(); return } }
        when (tool) {
            SketchTool.TRIM -> if (e != null && e.geo >= 0) exec("sketch_trim", mapOf("geo" to e.geo, "x" to p.x, "y" to p.y))
            SketchTool.DIMENSION -> { if (e == null) { selected.clear(); onChanged(); return }
                toggle(e); dimensionFromSelection(auto = true) }
            else -> { if (e == null) selected.clear() else toggle(e)
                armed?.let { t -> if (e != null) applyArmed(t) else status = armedHint(t) }
                onChanged() }
        }
    }
    private fun armedHint(t: String) = when (t) {
        "Coincident" -> "Coincident: tap two points, or a point and a curve"; "PointOnObject" -> "Point on object: tap a point and a curve"
        "Horizontal", "Vertical", "HorVer" -> "$t: tap a line or two points"; "Parallel", "Perpendicular" -> "$t: tap two lines"
        "Equal" -> "Equal: tap two lines, circles or arcs"; "Tangent" -> "Tangent: tap two curves, or two end points"
        "Symmetric" -> "Symmetric: tap two points, then the symmetry line or point"; "Block" -> "Block: tap the curves to fix"
        "Lock" -> "Lock: tap a point"; "Angle" -> "Angle: tap two lines"; "Radius", "Diameter", "Radiam" -> "$t: tap a circle or arc"
        else -> "$t: tap a line, or two points"
    } + " · Select to finish"
    /** Applies the armed constraint once the tapped elements are a complete selection for it (keeps waiting otherwise). */
    private fun applyArmed(t: String) {
        val n = selected.size
        // a single line is a complete selection for Angle only from the menu (its angle to the horizontal axis)
        if (!(t == "Angle" && n == 1)) { constrain(t, fromArmed = true); if (selected.isEmpty()) { status = armedHint(t); return } }
        // no constraint takes more than three elements: start over, as the desktop does with an invalid sequence
        if (n >= 3) { selected.clear(); status = "Not a valid selection for $t · " + armedHint(t) } else status = armedHint(t)
    }
    private fun toggle(e: SkSel) { if (e in selected) selected.remove(e) else selected.add(e) }

    /** Step-based tools mirroring FreeCAD's Sketcher tools; each finishes with one SketchObject call (one undo step). */
    private fun action(p: P2, e: SkSel?) {
        val g = geoSel()
        val L = LENGTH_UNITS; val A = ANGLE_UNITS
        when (tool) {
            SketchTool.FILLET, SketchTool.CHAMFER -> if (e != null && e.pos in 1..2 && e.geo >= 0)
                ask(if (tool == SketchTool.FILLET) "Fillet radius" else "Chamfer size", "2 mm", L) { r ->
                    exec("sketch_fillet_vertex", mapOf("geo" to e.geo, "pos" to e.pos, "radius" to r, "chamfer" to (tool == SketchTool.CHAMFER))) }
                else status = "Tap a corner vertex"
            SketchTool.EXTEND -> if (e != null && e.geo >= 0) {
                val gg = geos.firstOrNull { it.i == e.geo } ?: return
                val end = if (gg.a != null && gg.b != null && p.d(gg.a) < p.d(gg.b)) 1 else 2
                ask("Extend by", "10 mm", L) { v -> exec("sketch_extend", mapOf("geo" to e.geo, "increment" to v, "end_point" to end)) }
            } else status = "Tap the edge near the end to extend"
            SketchTool.SPLIT -> if (e != null && e.geo >= 0) exec("sketch_split", mapOf("geo" to e.geo, "x" to p.x, "y" to p.y)) else status = "Tap an edge where to split it"
            SketchTool.JOIN -> if (e != null && e.pos in 1..2) {
                actionPicks.add(p to e)
                if (actionPicks.size == 2) { val a = actionPicks[0].second!!; val b = actionPicks[1].second!!; actionPicks.clear()
                    exec("sketch_join", mapOf("geo1" to a.geo, "pos1" to a.pos, "geo2" to b.geo, "pos2" to b.pos)) } else status = "Tap the second curve's end point"
            } else status = "Tap an end point of the first curve"
            SketchTool.SYMMETRY -> if (g.isEmpty()) status = "Select geometry first (Select tool), then Symmetry"
                else if (e != null) { exec("sketch_symmetry", mapOf("geometry" to g, "ref_geo" to e.geo, "ref_pos" to e.pos)); selected.clear() }
                else status = "Tap the symmetry line or point"
            SketchTool.TRANSLATE, SketchTool.COPY, SketchTool.ARRAY -> if (g.isEmpty()) status = "Select geometry first (Select tool)" else {
                actionPicks.add(p to e)
                if (actionPicks.size == 2) {
                    val d = P2(actionPicks[1].first.x - actionPicks[0].first.x, actionPicks[1].first.y - actionPicks[0].first.y); actionPicks.clear()
                    if (tool == SketchTool.ARRAY) ask("Rows", "2", emptyList()) { r -> ask("Columns", "2", emptyList()) { c ->
                        exec("sketch_array", mapOf("geometry" to g, "dx" to d.x, "dy" to d.y, "rows" to r.toInt(), "cols" to c.toInt())) } }
                    else exec("sketch_translate", mapOf("geometry" to g, "dx" to d.x, "dy" to d.y, "copy" to (tool == SketchTool.COPY)))
                    selected.clear()
                } else status = "Tap the destination point"
            }
            SketchTool.INSERT_KNOT -> if (e != null && e.geo >= 0 && geos.firstOrNull { it.i == e.geo }?.bs != null)
                exec("sketch_insert_knot", mapOf("geo" to e.geo, "x" to p.x, "y" to p.y)) else status = "Tap a B-spline where to insert a knot"
            SketchTool.KNOT_UP, SketchTool.KNOT_DOWN -> if (e != null && e.geo >= 0)
                exec("sketch_knot_multiplicity", mapOf("point_geo" to e.geo, "delta" to (if (tool == SketchTool.KNOT_UP) 1 else -1))) else status = "Tap a knot point"
            SketchTool.PASTE -> { exec("sketch_clipboard", mapOf("action" to "paste", "dx" to p.x, "dy" to p.y)); selectTool(SketchTool.SELECT) }
            SketchTool.ROTATE -> if (g.isEmpty()) status = "Select geometry first (Select tool)" else
                ask("Rotation angle", "90 °", A) { a -> exec("sketch_rotate", mapOf("geometry" to g, "cx" to p.x, "cy" to p.y, "angle_deg" to a)); selected.clear() }
            SketchTool.SCALE -> if (g.isEmpty()) status = "Select geometry first (Select tool)" else
                ask("Scale factor", "2", emptyList()) { f -> exec("sketch_scale", mapOf("geometry" to g, "cx" to p.x, "cy" to p.y, "factor" to f)); selected.clear() }
            else -> {}
        }
        onChanged()
    }

    /** Commands acting on the current selection without taps (Sketcher_Offset, B-spline tools, toggles, validation, select helpers). */
    fun command(id: String) {
        val g = geoSel(); val c = selectedCons.toList()
        fun needG(f: () -> Unit) { if (g.isEmpty()) status = "Select geometry first" else f() }
        fun needC(f: () -> Unit) { if (c.isEmpty()) status = "Select a constraint first (tap its label)" else f() }
        when (id) {
            "Sketcher_Offset" -> needG { ask("Offset distance", "2 mm", LENGTH_UNITS) { d -> exec("sketch_offset", mapOf("geometry" to g, "distance" to d, "construction" to construction)) } }
            "Sketcher_BSplineConvertToNURBS" -> needG { exec("sketch_bspline_op", mapOf("op" to "ConvertToNURBS", "geo" to g[0])) }
            "Sketcher_BSplineIncreaseDegree" -> needG { exec("sketch_bspline_op", mapOf("op" to "IncreaseDegree", "geo" to g[0])) }
            "Sketcher_BSplineDecreaseDegree" -> needG { exec("sketch_bspline_op", mapOf("op" to "DecreaseDegree", "geo" to g[0])) }
            "Sketcher_RemoveAxesAlignment" -> needG { exec("sketch_remove_axes_alignment", mapOf("geometry" to g)) }
            "Sketcher_RestoreInternalAlignmentGeometry" -> needG { g.forEach { exec("sketch_toggle", mapOf("what" to "internal", "index" to it)) } }
            "Sketcher_ToggleDrivingConstraint" -> needC { c.forEach { exec("sketch_toggle", mapOf("what" to "driving", "index" to it)) } }
            "Sketcher_ToggleActiveConstraint" -> needC { c.forEach { exec("sketch_toggle", mapOf("what" to "active", "index" to it)) } }
            "Sketcher_SwitchVirtualSpace" -> needC { c.forEach { exec("sketch_toggle", mapOf("what" to "virtual", "index" to it)) } }
            "Sketcher_ChangeDimensionConstraint" -> needC { cons.firstOrNull { it.i == c[0] }?.let { editDimension(it) } }
            "Sketcher_ValidateSketch" -> exec("sketch_validate", emptyMap())
            "Sketcher_DeleteAllGeometry" -> exec("sketch_delete_all", mapOf("what" to "geometry"))
            "Sketcher_DeleteAllConstraints" -> exec("sketch_delete_all", mapOf("what" to "constraints"))
            "Sketcher_SelectConstraints" -> { selectedCons.clear(); cons.filter { k -> k.first in g || k.second in g }.forEach { selectedCons.add(it.i) } }
            "Sketcher_SelectElementsAssociatedWithConstraints" -> c.forEach { i -> cons.firstOrNull { it.i == i }?.let { k -> listOf(k.first, k.second).filter { it >= 0 }.forEach { selected.add(SkSel(it, 0)) } } }
            "Sketcher_SelectConflictingConstraints" -> { selectedCons.clear(); selectedCons.addAll(conflicting.map { it - 1 }) }
            "Sketcher_SelectRedundantConstraints" -> { selectedCons.clear(); selectedCons.addAll(redundant) }
            "Sketcher_SelectMalformedConstraints" -> { selectedCons.clear(); selectedCons.addAll(malformed) }
            "Sketcher_SelectPartiallyRedundantConstraints" -> { selectedCons.clear(); selectedCons.addAll(partiallyRedundant) }
            "Sketcher_SelectElementsWithDoFs" -> app.eng({ call("sketch_dof_elements", mapOf("name" to name)) as JSONArray }) { a ->
                selected.clear(); for (i in 0 until a.length()) a.getJSONArray(i).let { selected.add(SkSel(it.getInt(0), it.getInt(1))) }; onChanged() }
            "Sketcher_ConstrainGroup" -> needG { exec("sketch_group", mapOf("geometry" to g)); selected.clear() }
            "Sketcher_ConstrainSnellsLaw" -> { val pts = selected.filter { it.pos != 0 }; val edge = selected.firstOrNull { it.pos == 0 && it.geo >= 0 }
                if (pts.size == 2 && edge != null) { exec("sketch_snells_law", mapOf("geo1" to pts[0].geo, "pos1" to pts[0].pos, "geo2" to pts[1].geo, "pos2" to pts[1].pos,
                    "boundary" to edge.geo, "ratio" to 1.0)); selected.clear() } else status = "Snell's law: select two ray end points and the boundary curve" }
            "Sketcher_SelectOrigin" -> selected.add(SkSel(-1, 1))
            "Sketcher_SelectHorizontalAxis" -> selected.add(SkSel(-1, 0))
            "Sketcher_SelectVerticalAxis" -> selected.add(SkSel(-2, 0))
            "Sketcher_StopOperation" -> { finishShape(); selectTool(SketchTool.SELECT) }
            "Sketcher_CopyClipboard" -> needG { exec("sketch_clipboard", mapOf("action" to "copy", "geometry" to g)); status = "Copied ${g.size} element(s)" }
            "Sketcher_Cut" -> needG { exec("sketch_clipboard", mapOf("action" to "cut", "geometry" to g)); selected.clear() }
            "Sketcher_Paste" -> { selectTool(SketchTool.PASTE); status = "Tap where to paste" }
        }
        onChanged()
    }

    val selectedCons = mutableSetOf<Int>()

    // Sketcher visual helpers (Sketcher_ArcOverlay, Sketcher_BSpline*, Sketcher_Grid, Sketcher_Snap, Sketcher_RenderingOrder, Sketcher_ViewSection)
    var showArcOverlay = false; var showBsDegree = false; var showBsPolygon = false; var showBsComb = false
    var showBsKnotMult = false; var showBsWeight = false; var snapToGrid = true; var constructionOnTop = false
    var viewSection by mutableStateOf(false)
    fun toggleHelper(id: String) {
        when (id) {
            "Sketcher_ArcOverlay" -> showArcOverlay = !showArcOverlay
            "Sketcher_BSplineDegree" -> showBsDegree = !showBsDegree
            "Sketcher_BSplinePolygon" -> showBsPolygon = !showBsPolygon
            "Sketcher_BSplineComb" -> showBsComb = !showBsComb
            "Sketcher_BSplineKnotMultiplicity" -> showBsKnotMult = !showBsKnotMult
            "Sketcher_BSplinePoleWeight" -> showBsWeight = !showBsWeight
            "Sketcher_Snap" -> snapToGrid = !snapToGrid
            "Sketcher_RenderingOrder" -> constructionOnTop = !constructionOnTop
            "Sketcher_ViewSection" -> viewSection = !viewSection
        }
        onChanged()
    }
    private fun consAnchor(k: SkCons): P2? {
        val g = geos.firstOrNull { it.i == k.first } ?: return null
        return when { g.isLine -> P2((g.a!!.x + g.b!!.x) / 2, (g.a.y + g.b.y) / 2); g.c != null -> P2(g.c.x + g.r * 0.7, g.c.y + g.r * 0.7); else -> g.a }
    }
    private fun consAt(p: P2, tol: Double) = cons.mapNotNull { k -> consAnchor(k)?.let { k to it.d(p) } }.filter { it.second < tol * 1.6 }.minByOrNull { it.second }?.first?.i
    private fun toggleCons(i: Int) { if (i in selectedCons) selectedCons.remove(i) else selectedCons.add(i) }

    /** Sketcher_Projection / Sketcher_Intersection: a tap on the (dimmed) 3D model adds external geometry. */
    fun externalPick(obj: String, sub: String) {
        if (sub.isEmpty()) { status = "Tap an edge or vertex of the model"; return }
        exec("sketch_external", mapOf("obj" to obj, "sub" to sub, "intersection" to (tool == SketchTool.INTERSECTION)))
    }

    private var dragLast: P2? = null
    /** Finger down on [p] to drag an element: a curve then follows the finger from where it was grabbed. */
    fun dragStart(p: P2) { dragLast = p }
    fun drag(sel: SkSel, p: P2) {
        if (sel.geo < 0) return
        // ViewProviderSketch STATUS_SKETCH_DragCurve: a curve moves by the finger's offset (moveGeometry relative);
        // a point goes to the finger
        if (sel.pos == 0) { val q = dragLast ?: p; dragLast = p
            if (q.d(p) > 0) exec("sketch_move_point", mapOf("geo" to sel.geo, "pos" to 0, "x" to p.x - q.x, "y" to p.y - q.y, "relative" to true)) }
        else exec("sketch_move_point", mapOf("geo" to sel.geo, "pos" to sel.pos, "x" to p.x, "y" to p.y))
    }

    fun deleteSelected() {
        val g = selected.filter { it.pos == 0 && (it.geo >= 0 || it.geo <= -3) }.map { it.geo }.distinct()
        val pts = selected.filter { it.pos != 0 && (it.geo >= 0 || it.geo <= -3) }.map { listOf(it.geo, it.pos) }
        selected.clear(); if (g.isNotEmpty() || pts.isNotEmpty()) exec("sketch_delete", mapOf("geometry" to g, "points" to pts))
    }
    fun toggleConstruction() {
        val g = selected.map { it.geo }.filter { it >= 0 }.distinct()
        if (g.isEmpty()) { construction = !construction; status = hint(); onChanged() } else { selected.clear(); exec("sketch_toggle_construction", mapOf("geometry" to g)) }
    }

    private fun geo(i: Int) = geos.firstOrNull { it.i == i }
    private fun pt(s: SkSel): P2? = if (s.geo == -1 && s.pos == 1) P2(0.0, 0.0) else geo(s.geo)?.points()?.firstOrNull { it.first == s.pos }?.second

    /** Applies a FreeCAD constraint to the current selection, with FreeCAD's argument conventions. */
    fun constrain(type: String, fromArmed: Boolean = false) {
        // nothing selected: the constraint waits for its elements (desktop: the command starts its selection mode)
        if (!fromArmed && selected.isEmpty()) { selectTool(SketchTool.SELECT); armed = type; status = armedHint(type); onChanged(); return }
        val pts = selected.filter { it.pos != 0 }; val edges = selected.filter { it.pos == 0 }
        fun add(t: String, args: List<Int>, value: Any? = null, edit: Pair<String, List<String>>? = null) {
            exec("sketch_constrain", mapOf("ctype" to t, "args" to args, "value" to value)); selected.clear()
            if (edit != null) app.eng({ 0 }) { askValue(cons.size, edit.first, edit.second) }
        }
        when (type) {
            "Coincident" -> when { pts.size == 2 -> add("Coincident", listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos))
                pts.size == 1 && edges.size == 1 -> add("PointOnObject", listOf(pts[0].geo, pts[0].pos, edges[0].geo))
                else -> status = "Coincident: select two points, or a point and a curve" }
            "PointOnObject" -> if (pts.size == 1 && edges.size == 1) add(type, listOf(pts[0].geo, pts[0].pos, edges[0].geo)) else status = "Select a point and a curve"
            "Horizontal", "Vertical", "HorVer" -> {
                val t = if (type == "HorVer") { val g = edges.firstOrNull()?.let { geo(it.geo) }
                    if (g?.isLine == true && abs(g.b!!.y - g.a!!.y) > abs(g.b.x - g.a.x)) "Vertical" else "Horizontal" } else type
                when { edges.size == 1 && pts.isEmpty() -> add(t, listOf(edges[0].geo))
                    pts.size == 2 -> add(t, listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos))
                    else -> status = "$t: select a line or two points" } }
            "Parallel", "Perpendicular", "Equal" -> if (edges.size == 2) add(type, listOf(edges[0].geo, edges[1].geo)) else status = "$type: select two curves"
            "Tangent" -> when { edges.size == 2 -> add(type, listOf(edges[0].geo, edges[1].geo))
                pts.size == 2 -> add(type, listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos)) else -> status = "Tangent: select two curves" }
            "Symmetric" -> when { pts.size == 2 && edges.size == 1 -> add(type, listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos, edges[0].geo))
                pts.size == 3 -> add(type, listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos, pts[2].geo, pts[2].pos))
                else -> status = "Symmetric: two points + a line or a point" }
            "Block" -> edges.forEach { add("Block", listOf(it.geo)) }
            "Lock" -> pts.firstOrNull()?.let { s -> val p = pt(s) ?: return
                exec("sketch_constrain", mapOf("ctype" to "DistanceX", "args" to listOf(-1, 1, s.geo, s.pos), "value" to "${p.x} mm"))
                exec("sketch_constrain", mapOf("ctype" to "DistanceY", "args" to listOf(-1, 1, s.geo, s.pos), "value" to "${p.y} mm")); selected.clear() }
                ?: run { status = "Lock: select a point" }
            else -> dimensionFromSelection(auto = false, forced = type)
        }
        onChanged()
    }

    /** Sketcher_Dimension (unified): picks distance / radius / diameter / angle from the selection, then opens the numpad. */
    private fun dimensionFromSelection(auto: Boolean, forced: String? = null) {
        val pts = selected.filter { it.pos != 0 }; val edges = selected.filter { it.pos == 0 && it.geo >= 0 }
        fun add(t: String, args: List<Int>, v: Double, units: List<String>) {
            val txt = if (units === ANGLE_UNITS) "%.4f deg".format(Math.toDegrees(v)) else "%.4f mm".format(v)
            exec("sketch_constrain", mapOf("ctype" to t, "args" to args, "value" to txt)); selected.clear()
            app.eng({ 0 }) { askValue(cons.size, t, units) }
        }
        val g0 = edges.firstOrNull()?.let { geo(it.geo) }
        when {
            (forced == null || forced == "Distance") && edges.size == 1 && pts.isEmpty() && g0?.isLine == true -> add("Distance", listOf(g0.i), g0.a!!.d(g0.b!!), LENGTH_UNITS)
            forced in setOf(null, "Radius", "Radiam") && edges.size == 1 && g0?.isArc == true -> add("Radius", listOf(g0.i), g0.r, LENGTH_UNITS)
            forced in setOf(null, "Diameter", "Radiam") && edges.size == 1 && g0?.isCircle == true -> add("Diameter", listOf(g0.i), 2 * g0.r, LENGTH_UNITS)
            forced == "Radius" && g0?.isCircle == true -> add("Radius", listOf(g0.i), g0.r, LENGTH_UNITS)
            forced == "Diameter" && g0?.isArc == true -> add("Diameter", listOf(g0.i), 2 * g0.r, LENGTH_UNITS)
            forced in setOf("DistanceX", "DistanceY") && edges.size == 1 && g0?.isLine == true ->
                add(forced!!, listOf(g0.i), if (forced == "DistanceX") g0.b!!.x - g0.a!!.x else g0.b!!.y - g0.a!!.y, LENGTH_UNITS)
            forced in setOf("DistanceX", "DistanceY") && pts.size == 2 -> { val a = pt(pts[0])!!; val b = pt(pts[1])!!
                add(forced!!, listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos), if (forced == "DistanceX") b.x - a.x else b.y - a.y, LENGTH_UNITS) }
            forced in setOf(null, "Distance") && pts.size == 2 -> add("Distance", listOf(pts[0].geo, pts[0].pos, pts[1].geo, pts[1].pos), pt(pts[0])!!.d(pt(pts[1])!!), LENGTH_UNITS)
            forced in setOf(null, "Distance") && pts.size == 1 && edges.size == 1 && g0?.isLine == true ->
                add("Distance", listOf(pts[0].geo, pts[0].pos, g0.i), seg(pt(pts[0])!!, g0.a!!, g0.b!!), LENGTH_UNITS)
            forced in setOf(null, "Angle") && edges.size == 2 -> { val a = geo(edges[0].geo); val b = geo(edges[1].geo)
                if (a?.isLine == true && b?.isLine == true) {
                    val ang = atan2(b.b!!.y - b.a!!.y, b.b.x - b.a.x) - atan2(a.b!!.y - a.a!!.y, a.b.x - a.a.x)
                    add("Angle", listOf(a.i, b.i), ((ang % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI), ANGLE_UNITS)
                } }
            forced == "Angle" && edges.size == 1 && g0?.isLine == true -> add("Angle", listOf(g0.i), atan2(g0.b!!.y - g0.a!!.y, g0.b.x - g0.a.x), ANGLE_UNITS)
            !auto -> status = "${forced ?: "Dimension"}: invalid selection"
        }
        onChanged()
    }

    private fun askValue(index: Int, title: String, units: List<String>) {
        val c = cons.getOrNull(index - 1) ?: cons.lastOrNull() ?: return
        app.numpad = NumpadRequest(title, c.label.ifEmpty { "%.3f".format(c.value) }, units) { v ->
            app.numpad = null
            if (Regex("""^-?[\d.]+\s*[a-zA-Z°µ]*$""").matches(v.trim())) exec("sketch_set_datum", mapOf("index" to c.i, "value" to v.replace("°", "deg")))
            else exec("sketch_set_datum_expression", mapOf("index" to c.i, "expression" to v))
        }
    }

    // ------------------------------------------------------------------ dimension labels (SoDatumLabel placement)
    private val radial = setOf("Radius", "Diameter")
    /** Linear dimensions: extension lines from the measured points to a dimension line LabelDistance away, the label in
     *  its middle shifted by LabelPosition; radius / diameter: label on the ray at angle LabelPosition. */
    fun dimLayout(k: SkCons): DimLayout? {
        val g = geo(k.first) ?: return null
        if (k.type in radial && g.c != null) {
            val a = if (abs(k.lp) > 2 * Math.PI + 0.01) Math.PI / 4 else k.lp
            val d = P2(cos(a), sin(a)); val dist = maxOf(g.r * 0.4, g.r + k.ld - 10.0)
            val lab = P2(g.c.x + d.x * dist, g.c.y + d.y * dist)
            val from = if (k.type == "Diameter") P2(g.c.x - d.x * g.r, g.c.y - d.y * g.r) else g.c
            return DimLayout(lab, listOf(from to lab))
        }
        if (k.type !in setOf("Distance", "DistanceX", "DistanceY")) return null
        val (p1, p2) = when {
            k.second == -2000 && g.isLine -> g.a!! to g.b!!
            k.firstPos > 0 && k.secondPos > 0 -> (pt(SkSel(k.first, k.firstPos)) ?: return null) to (pt(SkSel(k.second, k.secondPos)) ?: return null)
            k.firstPos > 0 && geo(k.second)?.isLine == true -> { val q = pt(SkSel(k.first, k.firstPos)) ?: return null; val l = geo(k.second)!!
                q to closestOnLine(q, l.a!!, l.b!!) }
            k.firstPos > 0 && k.second == -2000 -> (pt(SkSel(k.first, k.firstPos)) ?: return null) to P2(0.0, 0.0)
            else -> return null
        }
        val dir = when (k.type) { "DistanceX" -> P2(if (p2.x >= p1.x) 1.0 else -1.0, 0.0); "DistanceY" -> P2(0.0, if (p2.y >= p1.y) 1.0 else -1.0)
            else -> { val l = p1.d(p2).coerceAtLeast(1e-9); P2((p2.x - p1.x) / l, (p2.y - p1.y) / l) } }
        val n = P2(-dir.y, dir.x)
        val mid = P2((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
        val base = P2(mid.x + n.x * k.ld, mid.y + n.y * k.ld)
        fun onBase(p: P2): P2 { val t = (p.x - base.x) * dir.x + (p.y - base.y) * dir.y; return P2(base.x + dir.x * t, base.y + dir.y * t) }
        val e1 = onBase(p1); val e2 = onBase(p2)
        val lab = P2((e1.x + e2.x) / 2 + dir.x * k.lp, (e1.y + e2.y) / 2 + dir.y * k.lp)
        return DimLayout(lab, listOf(p1 to e1, p2 to e2, e1 to e2))
    }
    private fun closestOnLine(p: P2, a: P2, b: P2): P2 { val dx = b.x - a.x; val dy = b.y - a.y; val l2 = (dx * dx + dy * dy).coerceAtLeast(1e-12)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2; return P2(a.x + dx * t, a.y + dy * t) }
    private fun labelPoint(k: SkCons): P2? = dimLayout(k)?.label ?: geo(k.first)?.let { g ->
        when { g.isLine -> P2((g.a!!.x + g.b!!.x) / 2, (g.a.y + g.b.y) / 2); g.c != null -> P2(g.c.x + g.r * 0.7, g.c.y + g.r * 0.7); else -> g.a } }

    /** The dimension whose label is under [p] (drag to move it, hold to edit its value). */
    fun pickLabel(p: P2, tolMm: Double): SkCons? = cons.filter { it.type in setOf("Distance", "DistanceX", "DistanceY", "Radius", "Diameter", "Angle") }
        .mapNotNull { k -> labelPoint(k)?.let { k to it.d(p) } }.filter { it.second < tolMm * 1.4 }.minByOrNull { it.second }?.first

    /** Moves the label to [p] (live); [commit] stores it in the sketch (one undo step, like desktop "Drag constraint"). */
    fun dragLabel(k: SkCons, p: P2, commit: Boolean) {
        val g = geo(k.first)
        if (k.type in radial && g?.c != null) {
            k.lp = atan2(p.y - g.c.y, p.x - g.c.x); k.ld = p.d(g.c) - g.r + 10.0
        } else if (k.type != "Angle") {
            // solve LabelDistance / LabelPosition from the finger: back out of the layout with zero offsets
            val ld0 = k.ld; val lp0 = k.lp; k.ld = 0.0; k.lp = 0.0
            val l0 = dimLayout(k); k.ld = ld0; k.lp = lp0
            if (l0 != null) { val (e1, e2) = l0.lines.last(); val len = e1.d(e2).coerceAtLeast(1e-9)
                val dir = P2((e2.x - e1.x) / len, (e2.y - e1.y) / len); val n = P2(-dir.y, dir.x)
                val dx = p.x - l0.label.x; val dy = p.y - l0.label.y
                k.ld = dx * n.x + dy * n.y; k.lp = dx * dir.x + dy * dir.y }
        }
        onChanged()
        if (commit) exec("sketch_set_label", mapOf("index" to k.i, "distance" to k.ld, "position" to k.lp))
    }

    // ------------------------------------------------------------------ TaskSketcherConstraints (the constraint list)
    /** The list panel: null = closed, else its filter ("all" or "problems"). */
    var consList by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun isConflicting(k: SkCons) = (k.i + 1) in conflicting
    fun isRedundant(k: SkCons) = (k.i + 1) in redundant || (k.i + 1) in partiallyRedundant || (k.i + 1) in malformed
    /** What a constraint ties, for the list ("Line 3 ↔ Circle 5", "Line 1 start"). */
    fun describe(k: SkCons): String {
        fun g(i: Int) = when { i == -1 -> "H axis"; i == -2 -> "V axis"; i <= -3 -> "External ${-i - 2}"; else -> geo(i)?.let { x ->
            when { x.isLine -> "Line"; x.isCircle -> "Circle"; x.isArc -> "Arc"; x.isPoint -> "Point"; else -> "Curve" } + " ${i + 1}" } ?: "#${i + 1}" }
        fun pos(p: Int) = when (p) { 1 -> " start"; 2 -> " end"; 3 -> " center"; else -> "" }
        val parts = listOfNotNull(k.first.takeIf { it != -2000 }?.let { g(it) + pos(k.firstPos) }, k.second.takeIf { it != -2000 }?.let { g(it) + pos(k.secondPos) },
            k.third.takeIf { it != -2000 }?.let { g(it) })
        return parts.joinToString(" ↔ ")
    }
    fun selectConstraint(k: SkCons) { selectedCons.clear(); selectedCons.add(k.i); selected.clear(); onChanged() }
    fun deleteConstraint(k: SkCons) { selectedCons.clear(); exec("sketch_delete", mapOf("constraints" to listOf(k.i))) }
    fun toggleActive(k: SkCons) = exec("sketch_toggle", mapOf("what" to "active", "index" to k.i))
    fun toggleDriving(k: SkCons) = exec("sketch_toggle_driving", mapOf("index" to k.i))
    fun renameConstraint(k: SkCons, n: String) = exec("sketch_rename_constraint", mapOf("index" to k.i, "label" to n))

    fun editDimension(c: SkCons) {
        val units = if (c.type == "Angle") ANGLE_UNITS else LENGTH_UNITS
        app.numpad = NumpadRequest(c.type, c.label, units) { v -> app.numpad = null
            exec("sketch_set_datum", mapOf("index" to c.i, "value" to v.replace("°", "deg"))) }
    }

    // ------------------------------------------------------------------ overlay drawing (free = blue, defined = black)
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** A text label the editor draws at a sketch point (constraint glyphs, dimension values, sizes while drawing, snap
     *  hints): its box around the point in pixels (left top right bottom) and how to paint it with the point at (x, y). */
    class Label(val at: P2, val l: Float, val t: Float, val r: Float, val b: Float, val paint: (Canvas, Float, Float) -> Unit)

    private fun pillLabel(at: P2, text: String, dp: Float, fg: Int, bg: Int, padX: Float, top: Float, bottom: Float, dy: Float = 0f): Label {
        val tp = Paint(pText).apply { textSize = 12 * dp; color = fg; textAlign = Paint.Align.CENTER }
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val w = tp.measureText(text) / 2 + padX
        return Label(at, -w, dy - top, w, dy + bottom) { c, x, y ->
            c.drawRoundRect(x - w, y + dy - top, x + w, y + dy + bottom, 4 * dp, 4 * dp, fp); c.drawText(text, x, y + dy, tp) }
    }

    private fun textLabel(at: P2, text: String, dp: Float, size: Float, color: Int, dx: Float, dy: Float, left: Boolean = false): Label {
        val tp = Paint(pText).apply { textSize = size * dp; this.color = color; textAlign = if (left) Paint.Align.LEFT else Paint.Align.CENTER }
        val tw = tp.measureText(text); val l = if (left) dx else dx - tw / 2
        return Label(at, l, dy - size * dp, l + tw, dy + 3 * dp) { c, x, y -> c.drawText(text, x + dx, y + dy, tp) }
    }

    /** Draws the sketch with [project] (world -> canvas pixels; null = not visible). With [labels], the text labels are
     *  collected there (sized for [labelDp]) instead of drawn: MobileCAD XR shows them facing the user. */
    fun draw(c: Canvas, dp: Float, project: (DoubleArray) -> FloatArray?, labels: MutableList<Label>? = null, labelDp: Float = dp) {
        fun scr(p: P2) = project(toWorld(p))
        val ldp = if (labels != null) labelDp else dp
        fun emit(l: Label) { if (labels != null) labels.add(l) else scr(l.at)?.let { s -> l.paint(c, s[0], s[1]) } }
        val dk = OS.dark
        val defined = if (dk) 0xFFE8ECEF.toInt() else 0xFF1A1A1A.toInt(); val free = if (dk) 0xFF6FA8FF.toInt() else 0xFF2A6FC9.toInt(); val cons = 0xFF66C7C9.toInt(); val sel = 0xFF418FDE.toInt()
        // axes
        pLine.strokeWidth = 1 * dp; pLine.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
        pLine.color = 0x66E0403F; axis(c, scr(P2(-1e7, 0.0)), scr(P2(1e7, 0.0)))
        pLine.color = 0x6647AB72; axis(c, scr(P2(0.0, -1e7)), scr(P2(0.0, 1e7)))
        for (g in geos) {
            val isSel = selected.any { it.geo == g.i && it.pos == 0 }
            pLine.pathEffect = if (g.construction) DashPathEffect(floatArrayOf(8 * dp, 5 * dp), 0f) else null
            pLine.strokeWidth = (if (isSel) 4f else 2f) * dp
            pLine.color = when { isSel -> sel; g.external -> 0xFFCC3399.toInt(); g.construction -> cons; fully -> defined; else -> free }
            if (g.isPoint) scr(g.a!!)?.let { c.drawCircle(it[0], it[1], 4 * dp, pFill.apply { color = pLine.color }) } else poly(c, g.polyline(), ::scr)
            for ((pos, q) in g.points()) scr(q)?.let {
                val s = selected.any { x -> x.geo == g.i && x.pos == pos }
                pFill.color = if (s) sel else if (fully) defined else free
                c.drawCircle(it[0], it[1], (if (s) 6f else 3f) * dp, pFill)
            }
        }
        pLine.pathEffect = null
        drawHelpers(c, dp, ::scr, ldp, ::emit)
        // constraint glyphs and dimension labels
        for (k in this.cons) {
            val lay = dimLayout(k)
            val anchor = labelPoint(k) ?: continue
            val s = scr(anchor) ?: continue
            lay?.let { l -> pLine.pathEffect = null; pLine.strokeWidth = 1 * dp
                pLine.color = if (k.i in selectedCons) 0xFF418FDE.toInt() else if (dk) 0x99C8D0D8.toInt() else 0x99606A73.toInt()
                l.lines.forEach { (a, b) -> val sa = scr(a); val sb = scr(b); if (sa != null && sb != null) c.drawLine(sa[0], sa[1], sb[0], sb[1], pLine) } }
            val bad = (k.i + 1) in conflicting || (k.i + 1) in redundant      // the solver reports 1-based #n
            val text = when (k.type) { "Horizontal" -> "H"; "Vertical" -> "V"; "Parallel" -> "∥"; "Perpendicular" -> "⊥"; "Tangent" -> "T"; "Equal" -> "="
                "Symmetric" -> "⇋"; "Block" -> "⊠"; "Distance", "DistanceX", "DistanceY", "Radius", "Diameter", "Angle" -> (if (k.type == "Diameter") "⌀" else if (k.type == "Radius") "R" else "") + k.label
                else -> continue }
            val fg = if (k.i in selectedCons) 0xFF418FDE.toInt() else if (bad) 0xFFE0403F.toInt() else if (k.driving) (if (dk) 0xFFE3E9EF.toInt() else 0xFF1B2733.toInt()) else 0xFF9AA5AF.toInt()
            emit(pillLabel(anchor, text, ldp, fg, if (dk) 0xE622272D.toInt() else 0xE6FFFFFF.toInt(), 4 * ldp, 14 * ldp, 4 * ldp))
        }
        // in-progress shape + snap feedback
        val pp = pendingPoints(); val pv = preview
        if (pp.isNotEmpty() || pv != null) {
            pLine.color = 0xFF418FDE.toInt(); pLine.strokeWidth = 2 * dp; pLine.pathEffect = DashPathEffect(floatArrayOf(5 * dp, 4 * dp), 0f)
            val all = pp + listOfNotNull(pv?.p)
            when (tool) {
                SketchTool.RECTANGLE -> if (all.size == 2) poly(c, listOf(all[0], P2(all[1].x, all[0].y), all[1], P2(all[0].x, all[1].y), all[0]), ::scr)
                SketchTool.CENTER_RECTANGLE -> if (all.size == 2) { val o = all[0]; val q = all[1]; val m = P2(2 * o.x - q.x, 2 * o.y - q.y)
                    poly(c, listOf(m, P2(q.x, m.y), q, P2(m.x, q.y), m), ::scr) }
                SketchTool.CIRCLE -> if (all.size == 2) { val r = all[0].d(all[1]); poly(c, (0..72).map { P2(all[0].x + r * cos(it * Math.PI / 36), all[0].y + r * sin(it * Math.PI / 36)) }, ::scr) }
                else -> if (all.size >= 2) poly(c, all, ::scr)
            }
            pLine.pathEffect = null
            pFill.color = 0xFF418FDE.toInt(); pp.forEach { p -> scr(p)?.let { q -> c.drawCircle(q[0], q[1], 5 * dp, pFill) } }
            liveDims(all, ldp, ::emit)
            pv?.let { s -> scr(s.p)?.let { q ->
                // inference guide lines (FreeCAD alignment): from the reference point to the cursor
                pLine.color = 0xFF47AB72.toInt(); pLine.strokeWidth = 1 * dp; pLine.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
                listOfNotNull(s.guide, s.guide2).forEach { g -> scr(g)?.let { r -> c.drawLine(r[0], r[1], q[0], q[1], pLine) } }
                pLine.pathEffect = null
                pFill.color = when (s.kind.removePrefix("model ")) { "point", "origin", "center" -> 0xFFE0403F.toInt(); "midpoint" -> 0xFFB04ADB.toInt(); "on curve", "on axis", "on model edge" -> 0xFFFFC20E.toInt()
                    "horizontal", "vertical", "aligned" -> 0xFF47AB72.toInt(); else -> 0xFF2A6FC9.toInt() }
                c.drawCircle(q[0], q[1], 6 * dp, pFill)
                val tag = when (s.kind) { "point" -> "coincident"; "origin" -> "origin"; "center" -> "center"; "midpoint" -> "midpoint"; "on curve" -> "on curve"
                    "on axis" -> "on axis"; "horizontal" -> "horizontal"; "vertical" -> "vertical"; "aligned" -> "aligned"
                    "model point" -> "part vertex"; "model center" -> "part center"; "model midpoint" -> "part midpoint"; "on model edge" -> "on part edge"; else -> null }
                tag?.let { t -> emit(textLabel(s.p, t, ldp, 11f, pFill.color, 12 * ldp, -12 * ldp, left = true)) }
            } }
        }
    }

    /** The shape's size while it is being drawn (FreeCAD's on-view parameters). */
    private fun liveDims(all: List<P2>, dp: Float, emit: (Label) -> Unit) {
        if (all.size < 2) return
        val a = all[all.size - 2]; val b = all.last()
        fun mm(v: Double) = "%.2f mm".format(v)
        fun label(p: P2, text: String, dy: Float = 0f) = emit(pillLabel(p, text, dp, 0xFFFFFFFF.toInt(), 0xF22A6FC9.toInt(), 5 * dp, 15 * dp, 5 * dp, dy))
        fun mid(p: P2, q: P2) = P2((p.x + q.x) / 2, (p.y + q.y) / 2)
        when (tool) {
            SketchTool.LINE, SketchTool.POLYLINE -> {
                val ang = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
                label(mid(a, b), mm(a.d(b)), -10 * dp); label(mid(a, b), "%.1f°".format(ang), 14 * dp)
            }
            SketchTool.RECTANGLE -> { val p0 = all[0]
                label(P2((p0.x + b.x) / 2, b.y), mm(abs(b.x - p0.x)), 18 * dp); label(P2(b.x, (p0.y + b.y) / 2), mm(abs(b.y - p0.y))) }
            SketchTool.CENTER_RECTANGLE -> { val p0 = all[0]
                label(P2(p0.x, b.y), mm(2 * abs(b.x - p0.x)), 18 * dp); label(P2(b.x, p0.y), mm(2 * abs(b.y - p0.y))) }
            SketchTool.CIRCLE -> label(mid(all[0], b), "⌀ " + mm(2 * all[0].d(b)))
            SketchTool.ARC_CENTER -> if (all.size == 2) label(mid(all[0], b), "R " + mm(all[0].d(b)))
                else { val s0 = all[1]; val sweep = Math.toDegrees(atan2(b.y - all[0].y, b.x - all[0].x) - atan2(s0.y - all[0].y, s0.x - all[0].x)).let { ((it % 360) + 360) % 360 }
                    label(b, "%.1f°".format(sweep), -16 * dp) }
            SketchTool.POLYGON3, SketchTool.POLYGON4, SketchTool.POLYGON5, SketchTool.POLYGON6, SketchTool.POLYGON7, SketchTool.POLYGON8 ->
                label(mid(all[0], b), "R " + mm(all[0].d(b)))
            SketchTool.SLOT -> label(mid(all[0], all[1]), mm(all[0].d(all[1])), -10 * dp)
            else -> label(mid(a, b), mm(a.d(b)), -10 * dp)
        }
    }

    private fun drawHelpers(c: Canvas, dp: Float, scr: (P2) -> FloatArray?, ldp: Float, emit: (Label) -> Unit) {
        pLine.strokeWidth = 1 * dp; val tc = if (OS.dark) 0xFFA3AEB8.toInt() else 0xFF606A73.toInt()
        for (g in geos) {
            if (showArcOverlay && g.isArc) { pLine.color = 0x55418FDE; pLine.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f)
                poly(c, (0..72).map { P2(g.c!!.x + g.r * cos(it * Math.PI / 36), g.c.y + g.r * sin(it * Math.PI / 36)) }, scr); pLine.pathEffect = null }
            val b = g.bs ?: continue
            if (showBsPolygon) { pLine.color = 0x88606A73.toInt(); poly(c, b.poles, scr) }
            if (showBsComb) { pLine.color = 0x88E0403F.toInt(); b.comb.forEach { (p0, p1) -> poly(c, listOf(p0, p1), scr) }
                poly(c, b.comb.map { it.second }, scr) }
            if (showBsDegree) b.poles.firstOrNull()?.let { emit(textLabel(it, "degree ${b.degree}", ldp, 11f, tc, 0f, -10 * ldp)) }
            if (showBsWeight) b.poles.zip(b.weights).forEach { (p, w) -> emit(textLabel(p, "%.2f".format(w), ldp, 11f, tc, 0f, 14 * ldp)) }
            if (showBsKnotMult) b.knots.forEach { (p, m) -> emit(textLabel(p, "($m)", ldp, 11f, tc, 0f, -8 * ldp)) }
        }
    }

    /** A sketch axis: the line through two far points, cut to the canvas (a dashed line kilometres long is slow to draw). */
    private fun axis(c: Canvas, s0: FloatArray?, s1: FloatArray?) {
        if (s0 == null || s1 == null) return
        val dx = s1[0] - s0[0]; val dy = s1[1] - s0[1]; var t0 = 0f; var t1 = 1f
        val w = c.width.toFloat(); val h = c.height.toFloat()
        for ((p, q) in listOf(-dx to s0[0], dx to w - s0[0], -dy to s0[1], dy to h - s0[1])) {   // Liang-Barsky
            if (p == 0f) { if (q < 0) return; continue }
            val r = q / p; if (p < 0) t0 = maxOf(t0, r) else t1 = minOf(t1, r)
        }
        if (t0 < t1) c.drawLine(s0[0] + t0 * dx, s0[1] + t0 * dy, s0[0] + t1 * dx, s0[1] + t1 * dy, pLine)
    }
    private fun poly(c: Canvas, pts: List<P2>, scr: (P2) -> FloatArray?) {
        val path = Path(); var started = false
        for (p in pts) { val s = scr(p); if (s == null) { started = false; continue }; if (!started) { path.moveTo(s[0], s[1]); started = true } else path.lineTo(s[0], s[1]) }
        c.drawPath(path, pLine)
    }
}
