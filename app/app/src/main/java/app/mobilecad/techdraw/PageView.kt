package app.mobilecad.techdraw

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.caverock.androidsvg.SVG
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.SelRef
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import app.mobilecad.ui.OS

/**
 * The Drawing tab: a TechDraw page as the desktop's QGraphicsScene shows it (template, views, dimensions, balloons,
 * leaders, annotations, symbols, hatches), drawn from mobilecad.techdraw.td_render in page millimetres (Y up).
 * Touch: one finger pans (or drags the selected view / dimension / balloon), pinch zooms, tap selects a vertex, edge,
 * dimension or view (TechDraw sub-element names Vertex<i> / Edge<i>), double tap clears.  While a TechDraw tool is
 * collecting points (AppState.draft) taps place page points instead.
 */
class PageView(ctx: Context, private val app: AppState) : View(ctx) {
    private val dp = resources.displayMetrics.density
    private var k = 2f                     // screen px per page mm
    /** TechDraw_ToggleFrame / TechDraw_ToggleGrid. */
    var showFrames = false
    var showGrid = false
    private var ox = 0f; private var oy = 0f
    private var fitted: String? = null
    private var tplPicture: Picture? = null; private var tplKey: String? = null
    private val symbols = HashMap<String, Picture?>()
    private val images = HashMap<String, Bitmap?>()

    private val paper = Paint().apply { color = Color.WHITE }
    private val shadow = Paint().apply { color = Color.parseColor("#33000000") }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.SANS_SERIF }
    private val sel = Color.parseColor("#1E8CE6"); private val pre = Color.parseColor("#66C7C9")

    private fun sx(x: Double) = (ox + x * k).toFloat()
    private fun sy(y: Double, h: Double) = (oy + (h - y) * k).toFloat()
    private fun H() = app.pageRender?.optDouble("height", 210.0) ?: 210.0
    private fun W() = app.pageRender?.optDouble("width", 297.0) ?: 297.0
    /** Screen -> page mm (Y up). */
    fun toPage(x: Float, y: Float) = doubleArrayOf(((x - ox) / k).toDouble(), H() - ((y - oy) / k).toDouble())

    fun fit() {
        if (width == 0) return
        k = min(width / W().toFloat(), height / H().toFloat()) * 0.92f
        ox = (width - W().toFloat() * k) / 2; oy = (height - H().toFloat() * k) / 2; invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { fit() }

    // ------------------------------------------------------------------ drawing
    private var printing = false

    /** TechDraw_ExportPagePDF / PrintAll: the page at [scale] px per mm, origin top-left, without selection highlights. */
    fun drawPage(c: Canvas, scale: Float) {
        val sk = k; val sox = ox; val soy = oy; val sf = fitted
        k = scale; ox = 0f; oy = 0f; printing = true; fitted = app.page
        try { onDraw(c) } finally { k = sk; ox = sox; oy = soy; printing = false; fitted = sf }
    }

    override fun onDraw(c: Canvas) {
        if (!printing) { c.clipRect(0, 0, width, height); c.drawColor(Color.parseColor(if (OS.dark) "#121417" else "#E4E8EB")) }
        val r = app.pageRender
        if (r == null) {
            text.textSize = 16 * dp; text.color = Color.parseColor("#5B6770"); text.textAlign = Paint.Align.CENTER
            if (app.page != null) c.drawText("Loading page…", width / 2f, height / 2f, text)     // empty state: Shell's EmptyState
            text.textAlign = Paint.Align.LEFT; text.color = Color.BLACK; return
        }
        if (fitted != app.page) { fitted = app.page; fit() }
        val h = H(); val w = W()
        if (!printing) c.drawRect(sx(0.0) + 4 * dp, sy(h, h) + 4 * dp, sx(w) + 4 * dp, sy(0.0, h) + 4 * dp, shadow)
        c.drawRect(sx(0.0), sy(h, h), sx(w), sy(0.0, h), paper)
        // template (AndroidSVG; the template's viewBox is in mm)
        val tsvg = r.optString("template")
        if (tsvg.isNotEmpty()) {
            if (tplKey != tsvg) { tplKey = tsvg; tplPicture = runCatching { SVG.getFromString(tsvg).apply { documentWidth = w.toFloat() * 4; documentHeight = h.toFloat() * 4 }.renderToPicture() }.getOrNull() }
            tplPicture?.let { p -> c.save(); c.translate(sx(0.0), sy(h, h)); c.scale(k / 4, k / 4); c.drawPicture(p); c.restore() }
        }
        if (showGrid) { line.color = Color.parseColor("#E8ECEF"); line.strokeWidth = 1f; line.pathEffect = null
            var g = 0.0; while (g <= w) { c.drawLine(sx(g), sy(0.0, h), sx(g), sy(h, h), line); g += 10.0 }
            g = 0.0; while (g <= h) { c.drawLine(sx(0.0), sy(g, h), sx(w), sy(g, h), line); g += 10.0 } }
        val selRefs = if (printing) emptyList() else app.selection.toList()
        // hatches under the geometry: the referenced faces of their view, filled with the pattern's lines
        val viewsByName = (r.optJSONArray("views") ?: JSONArray()).let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("name") to a.getJSONObject(it) } }
        r.optJSONArray("hatches")?.let { hs -> for (i in 0 until hs.length()) { val hh = hs.getJSONObject(i); val v = viewsByName[hh.optString("view")] ?: continue
            val faces = v.optJSONArray("faces") ?: continue; val idx = hh.optJSONArray("faces") ?: continue
            val bx = v.getDouble("x"); val by = v.getDouble("y"); val step = 3.0 * hh.optDouble("scale", 1.0).coerceIn(0.1, 20.0)
            for (j in 0 until idx.length()) { val fi = idx.getInt(j); if (fi >= faces.length()) continue
                val path = facePath(faces.getJSONArray(fi), bx, by, h); c.save(); c.clipPath(path)
                line.color = Color.parseColor("#6E7A84"); line.strokeWidth = max(1f, 0.18f * k); line.pathEffect = null
                val bb = RectF(); path.computeBounds(bb, true); var t = bb.left - bb.height()
                while (t < bb.right) { c.drawLine(t, bb.bottom, t + bb.height(), bb.top, line); t += (step * k).toFloat() }
                if (hh.optString("pattern") == "Diamond") { t = bb.left - bb.height(); while (t < bb.right + bb.height()) { c.drawLine(t, bb.top, t + bb.height(), bb.bottom, line); t += (step * k).toFloat() } }
                c.restore() } } }
        val views = r.optJSONArray("views") ?: JSONArray()
        val order = (0 until views.length()).map { views.getJSONObject(it) }.sortedBy { it.optDouble("z", 0.0) }
        for (v in order) drawView(c, v, h, selRefs)
        // Draft-style point picking for TechDraw tools
        app.draft?.let { t -> line.color = sel; line.strokeWidth = 2 * dp; line.pathEffect = null
            val pts = t.pts.toList() + listOfNotNull(t.hover)
            for (i in 1 until pts.size) c.drawLine(sx(pts[i - 1][0]), sy(pts[i - 1][1], h), sx(pts[i][0]), sy(pts[i][1], h), line)
            fill.color = sel; for (p in pts) c.drawCircle(sx(p[0]), sy(p[1], h), 5 * dp, fill) }
    }

    private fun drawView(c: Canvas, v: JSONObject, h: Double, selRefs: List<SelRef>) {
        val name = v.getString("name"); val bx = v.getDouble("x"); val by = v.getDouble("y")
        val viewSel = selRefs.any { it.obj == name && it.sub.isEmpty() }
        fun X(x: Double) = sx(bx + x)
        fun Y(y: Double) = sy(by + y, h)
        v.optJSONArray("faces")?.let { fs -> selRefs.filter { it.obj == name && it.sub.startsWith("Face") }.forEach { r ->
            val fi = r.sub.removePrefix("Face").toInt(); if (fi < fs.length()) { fill.color = Color.parseColor("#4D1E8CE6"); c.drawPath(facePath(fs.getJSONArray(fi), bx, by, h), fill) } } }
        v.optJSONArray("edges")?.let { es ->
            val selE = selRefs.filter { it.obj == name && it.sub.startsWith("Edge") }.map { it.sub.removePrefix("Edge").toInt() }.toSet()
            for (i in 0 until es.length()) { val e = es.getJSONArray(i); val idx = e.getInt(0); val visible = e.getInt(1) == 1; val pl = e.getJSONArray(2)
                line.color = when { idx in selE -> sel; viewSel -> Color.parseColor("#2A6FB0"); else -> Color.BLACK }
                line.strokeWidth = max(1.2f, (if (visible) 0.35f else 0.18f) * k) * (if (idx in selE) 2f else 1f)
                line.pathEffect = if (visible) null else DashPathEffect(floatArrayOf(2f * k, 1f * k), 0f)
                val path = Path(); for (q in 0 until pl.length()) { val p = pl.getJSONArray(q); if (q == 0) path.moveTo(X(p.getDouble(0)), Y(p.getDouble(1))) else path.lineTo(X(p.getDouble(0)), Y(p.getDouble(1))) }
                c.drawPath(path, line) }
            line.pathEffect = null
            v.optJSONArray("verts")?.let { vs -> val selV = selRefs.filter { it.obj == name && it.sub.startsWith("Vertex") }.map { it.sub.removePrefix("Vertex").toInt() }.toSet()
                for (i in 0 until vs.length()) { val q = vs.getJSONArray(i); val idx = q.getInt(0)
                    if (idx in selV || viewSel) { fill.color = if (idx in selV) sel else pre; c.drawCircle(X(q.getDouble(1)), Y(q.getDouble(2)), if (idx in selV) 5 * dp else 3 * dp, fill) } } }
            v.optString("caption").takeIf { it.isNotEmpty() }?.let { cap -> text.textSize = 5f * k; text.textAlign = Paint.Align.CENTER; text.color = Color.BLACK
                c.drawText(cap, X(0.0), Y(0.0) + 30 * k, text); text.textAlign = Paint.Align.LEFT }
        }
        v.optJSONObject("dim")?.let { d -> val col = if (viewSel) sel else Color.parseColor("#1D2D3C")
            line.color = col; line.strokeWidth = max(1f, 0.25f * k); fill.color = col
            val ls = d.getJSONArray("lines")
            for (i in 0 until ls.length()) { val pl = ls.getJSONArray(i); val path = Path()
                for (q in 0 until pl.length()) { val p = pl.getJSONArray(q); if (q == 0) path.moveTo(X(p.getDouble(0)), Y(p.getDouble(1))) else path.lineTo(X(p.getDouble(0)), Y(p.getDouble(1))) }
                c.drawPath(path, line) }
            val ar = d.getJSONArray("arrows")
            for (i in 0 until ar.length()) { val a = ar.getJSONArray(i); arrow(c, X(a.getDouble(0)), Y(a.getDouble(1)), a.getDouble(2), -a.getDouble(3)) }
            text.textSize = 3.5f * k; text.color = col; text.textAlign = Paint.Align.CENTER
            c.drawText(d.optString("text"), X(d.getDouble("tx")), Y(d.getDouble("ty")) - 0.8f * k, text); text.textAlign = Paint.Align.LEFT; text.color = Color.BLACK }
        v.optJSONObject("balloon")?.let { b -> val col = if (viewSel) sel else Color.BLACK
            line.color = col; line.strokeWidth = max(1f, 0.25f * k)
            val cx = X(b.getDouble("bx")); val cy = Y(b.getDouble("by")); val r = 4f * k
            c.drawLine(X(b.getDouble("ox")), Y(b.getDouble("oy")), cx, cy, line)
            fill.color = Color.WHITE; c.drawCircle(cx, cy, r, fill); c.drawCircle(cx, cy, r, line)
            fill.color = col; c.drawCircle(X(b.getDouble("ox")), Y(b.getDouble("oy")), 0.8f * k, fill)
            text.textSize = 4f * k; text.textAlign = Paint.Align.CENTER; text.color = col; c.drawText(b.optString("text"), cx, cy + 1.4f * k, text); text.textAlign = Paint.Align.LEFT; text.color = Color.BLACK }
        v.optJSONArray("leader")?.let { wp -> line.color = if (viewSel) sel else Color.BLACK; line.strokeWidth = max(1f, 0.25f * k)
            for (i in 1 until wp.length()) { val a = wp.getJSONArray(i - 1); val b = wp.getJSONArray(i); c.drawLine(X(a.getDouble(0)), Y(a.getDouble(1)), X(b.getDouble(0)), Y(b.getDouble(1)), line) }
            if (wp.length() > 1) { val a = wp.getJSONArray(1); val b = wp.getJSONArray(0); fill.color = line.color
                arrow(c, X(b.getDouble(0)), Y(b.getDouble(1)), b.getDouble(0) - a.getDouble(0), -(b.getDouble(1) - a.getDouble(1))) } }
        v.optString("text").takeIf { it.isNotEmpty() && !v.has("dim") }?.let { t -> text.textSize = (v.optDouble("size", 5.0) * k).toFloat(); text.color = if (viewSel) sel else Color.BLACK
            t.lines().forEachIndexed { i, l -> c.drawText(l, X(0.0), Y(0.0) + i * text.textSize * 1.25f, text) }; text.color = Color.BLACK }
        v.optString("svg").takeIf { it.isNotEmpty() }?.let { s -> val pic = symbols.getOrPut(s) { runCatching { SVG.getFromString(s).renderToPicture() }.getOrNull() }
            pic?.let { p -> val sc = v.optDouble("scale", 1.0).toFloat(); c.save(); c.translate(X(0.0) - p.width / 2f * k * sc, Y(0.0) - p.height / 2f * k * sc); c.scale(k * sc, k * sc); c.drawPicture(p); c.restore() } }
        v.optString("image").takeIf { it.isNotEmpty() }?.let { f -> images.getOrPut(f) { runCatching { BitmapFactory.decodeFile(f) }.getOrNull() }?.let { bmp ->
            val iw = v.optDouble("w", 50.0); val ih = v.optDouble("h", 50.0)
            c.drawBitmap(bmp, null, RectF(X(-iw / 2), Y(ih / 2), X(iw / 2), Y(-ih / 2)), null) } }
        if ((viewSel || showFrames) && v.has("edges")) bbox(v)?.let { b -> line.color = if (viewSel) sel else Color.parseColor("#9AA5AE"); line.strokeWidth = 1 * dp; line.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 4 * dp), 0f)
            c.drawRect(X(b[0]) - 4 * dp, Y(b[3]) - 4 * dp, X(b[2]) + 4 * dp, Y(b[1]) + 4 * dp, line); line.pathEffect = null }
    }

    private fun facePath(pl: JSONArray, bx: Double, by: Double, h: Double): Path {
        val path = Path()
        for (q in 0 until pl.length()) { val p = pl.getJSONArray(q); val X = sx(bx + p.getDouble(0)); val Y = sy(by + p.getDouble(1), h); if (q == 0) path.moveTo(X, Y) else path.lineTo(X, Y) }
        path.close(); return path
    }

    private fun arrow(c: Canvas, x: Float, y: Float, dx: Double, dy: Double) {
        val l = hypot(dx, dy).takeIf { it > 1e-9 } ?: return
        val ux = (dx / l).toFloat(); val uy = (dy / l).toFloat(); val L = 3.5f * k; val W = 1.2f * k
        val p = Path(); p.moveTo(x, y); p.lineTo(x - ux * L - uy * W, y - uy * L + ux * W); p.lineTo(x - ux * L + uy * W, y - uy * L - ux * W); p.close(); c.drawPath(p, fill)
    }

    private fun bbox(v: JSONObject): DoubleArray? {
        val es = v.optJSONArray("edges") ?: return null; if (es.length() == 0) return null
        var a = doubleArrayOf(1e9, 1e9, -1e9, -1e9)
        for (i in 0 until es.length()) { val pl = es.getJSONArray(i).getJSONArray(2)
            for (q in 0 until pl.length()) { val p = pl.getJSONArray(q); a = doubleArrayOf(min(a[0], p.getDouble(0)), min(a[1], p.getDouble(1)), max(a[2], p.getDouble(0)), max(a[3], p.getDouble(1))) } }
        return a
    }

    // ------------------------------------------------------------------ picking
    /** Nearest vertex (12dp), edge (10dp), dimension / balloon / annotation anchor (16dp) or view frame at (x, y). */
    fun pick(x: Float, y: Float): SelRef? {
        val r = app.pageRender ?: return null; val h = H(); val views = r.optJSONArray("views") ?: return null
        var best: SelRef? = null; var bd = 12 * dp
        for (i in 0 until views.length()) { val v = views.getJSONObject(i); val name = v.getString("name"); val bx = v.getDouble("x"); val by = v.getDouble("y")
            v.optJSONArray("verts")?.let { vs -> for (j in 0 until vs.length()) { val q = vs.getJSONArray(j)
                val d = hypot(x - sx(bx + q.getDouble(1)), y - sy(by + q.getDouble(2), h)); if (d < bd) { bd = d; best = SelRef(name, "Vertex${q.getInt(0)}") } } } }
        if (best != null) return best
        bd = 10 * dp
        for (i in 0 until views.length()) { val v = views.getJSONObject(i); val name = v.getString("name"); val bx = v.getDouble("x"); val by = v.getDouble("y")
            v.optJSONArray("edges")?.let { es -> for (j in 0 until es.length()) { val e = es.getJSONArray(j); val pl = e.getJSONArray(2)
                for (q in 1 until pl.length()) { val a = pl.getJSONArray(q - 1); val b = pl.getJSONArray(q)
                    val d = seg(x, y, sx(bx + a.getDouble(0)), sy(by + a.getDouble(1), h), sx(bx + b.getDouble(0)), sy(by + b.getDouble(1), h))
                    if (d < bd) { bd = d; best = SelRef(name, "Edge${e.getInt(0)}") } } } } }
        if (best != null) return best
        bd = 18 * dp
        for (i in 0 until views.length()) { val v = views.getJSONObject(i); val name = v.getString("name"); val bx = v.getDouble("x"); val by = v.getDouble("y")
            val anchor = v.optJSONObject("dim")?.let { doubleArrayOf(it.getDouble("tx"), it.getDouble("ty")) }
                ?: v.optJSONObject("balloon")?.let { doubleArrayOf(it.getDouble("bx"), it.getDouble("by")) } ?: doubleArrayOf(0.0, 0.0)
            val d = hypot(x - sx(bx + anchor[0]), y - sy(by + anchor[1], h)); if (d < bd) { bd = d; best = SelRef(name) } }
        if (best != null) return best
        // faces: tap inside a closed region (TechDraw Face<i>), smallest containing face wins
        var bestArea = Float.MAX_VALUE
        for (i in 0 until views.length()) { val v = views.getJSONObject(i); val fs = v.optJSONArray("faces") ?: continue
            val bx = v.getDouble("x"); val by = v.getDouble("y")
            for (j in 0 until fs.length()) { val path = facePath(fs.getJSONArray(j), bx, by, h); val rg = Region(); val bb = RectF(); path.computeBounds(bb, true)
                rg.setPath(path, Region(bb.left.toInt() - 1, bb.top.toInt() - 1, bb.right.toInt() + 1, bb.bottom.toInt() + 1))
                if (rg.contains(x.toInt(), y.toInt()) && bb.width() * bb.height() < bestArea) { bestArea = bb.width() * bb.height(); best = SelRef(v.getString("name"), "Face$j") } } }
        if (best != null) return best
        for (i in 0 until views.length()) { val v = views.getJSONObject(i); val b = bbox(v) ?: continue; val bx = v.getDouble("x"); val by = v.getDouble("y")
            if (x in sx(bx + b[0])..sx(bx + b[2]) && y in sy(by + b[3], h)..sy(by + b[1], h)) return SelRef(v.getString("name")) }
        return null
    }

    private fun seg(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-6f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(x - (ax + t * dx), y - (ay + t * dy))
    }

    // ------------------------------------------------------------------ touch
    private var lastX = 0f; private var lastY = 0f; private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var pinchD = 0f; private var moved = false; private var lastTap = 0L
    private var dragItem: String? = null; private var dragDx = 0.0; private var dragDy = 0.0
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; downX = e.x; downY = e.y; downT = SystemClock.uptimeMillis(); moved = false
                // drag starts on an already selected whole item (view / dimension / balloon / annotation)
                dragItem = app.selection.firstOrNull { it.sub.isEmpty() }?.obj?.takeIf { n -> pick(e.x, e.y)?.obj == n && app.draft == null }; dragDx = 0.0; dragDy = 0.0 }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) { pinchD = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); dragItem = null }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) moved = true
                if (e.pointerCount >= 2) {
                    val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)); val cx = (e.getX(0) + e.getX(1)) / 2; val cy = (e.getY(0) + e.getY(1)) / 2
                    if (pinchD > 0) { val f = d / pinchD; ox = cx - (cx - ox) * f; oy = cy - (cy - oy) * f; k *= f }
                    pinchD = d; invalidate()
                } else if (moved) {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    if (dragItem != null) { dragDx += dx / k; dragDy -= dy / k; app.pageDragPreview(dragItem!!, dragDx, dragDy) }
                    else if (app.draft != null) app.draft?.hover = toPage(e.x, e.y).let { doubleArrayOf(it[0], it[1], 0.0) }
                    else { ox += dx; oy += dy }
                    invalidate()
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val now = SystemClock.uptimeMillis()
                dragItem?.let { n -> if (moved) app.movePageItem(n, dragDx, dragDy) }
                if (!moved && now - downT < 400) {
                    val p = toPage(e.x, e.y)
                    if (app.draft != null) app.draftPoint(doubleArrayOf(p[0], p[1], 0.0))
                    else if (now - lastTap < ViewConfiguration.getDoubleTapTimeout()) { app.clearSelection(); lastTap = 0; invalidate() }
                    else { lastTap = now; pick(e.x, e.y)?.let { app.select(it) }; invalidate() }
                }
                dragItem = null
            }
        }
        return true
    }
}
