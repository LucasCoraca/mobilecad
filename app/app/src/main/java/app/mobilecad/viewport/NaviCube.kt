package app.mobilecad.viewport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * FreeCAD's NaviCube (src/Gui/Navigation/NaviCube.cpp) for touch: a labelled cube turning with the camera.
 *  - tap a face → that standard view; near an edge or corner → the view along that edge / corner (26 directions)
 *  - ▲▼◀▶ orbit by [step] degrees, ↶↷ roll 90° about the view axis
 *  - drag the cube → orbit; ☰ button → Orthographic / Perspective / Isometric / Fit all
 * Every move is animated like the desktop (NavigationAnimation).
 */
class NaviCube(ctx: Context, private val cam: Camera, private val host: Host) : View(ctx) {
    interface Host {
        /** Animate the camera to the eye direction [dir] (target→eye, unit); [keepRoll] keeps the current roll. */
        fun viewAlong(dir: FloatArray)
        fun orbitBy(dYaw: Float, dPitch: Float)
        fun rollBy(deg: Float)
        fun dragOrbit(dx: Float, dy: Float)
        fun menu(anchor: View)
        val dark: Boolean
    }

    private val dp = resources.displayMetrics.density
    /** arrow / icon scale (smaller than dp: the cube is a compact corner widget like FreeCAD's) */
    private val u get() = dp * 0.72f
    private val step = 15f
    private val cubeR get() = 24 * dp                 // half size of the cube on screen
    private val cx get() = width / 2f
    private val cy get() = 10 * dp + cubeR * 1.75f

    // FreeCAD labels; normals are the face's outward direction = the eye direction of its view
    private val faces = listOf(
        Face("TOP", floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
        Face("BOTTOM", floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, -1f, 0f)),
        Face("FRONT", floatArrayOf(0f, -1f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f)),
        Face("REAR", floatArrayOf(0f, 1f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 0f, 1f)),
        Face("RIGHT", floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f)),
        Face("LEFT", floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f)))
    /** A cube face: outward normal, and in-face axes a (label right) / b (label up). */
    private class Face(val label: String, val n: FloatArray, val a: FloatArray, val b: FloatArray)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG); private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private var hot: FloatArray? = null          // direction under the finger (highlighted)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((112 * dp).toInt(), (108 * dp).toInt())

    // world vector -> cube screen offset (x right, y down) and depth (toward the viewer > 0)
    private fun scr(v: FloatArray): FloatArray {
        val (r, u) = cam.basis(); val f = floatArrayOf(-u[1] * r[2] + u[2] * r[1], -u[2] * r[0] + u[0] * r[2], -u[0] * r[1] + u[1] * r[0])  // r × u = toward viewer
        return floatArrayOf(dot(v, r) * cubeR, -dot(v, u) * cubeR, dot(v, f))
    }
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun pt(n: FloatArray, a: FloatArray, b: FloatArray, s: Float, t: Float) = FloatArray(3) { n[it] + a[it] * s + b[it] * t }

    override fun onDraw(c: Canvas) {
        val dark = host.dark
        val faceCol = if (dark) 0xFF3A424B.toInt() else 0xFFF2F4F6.toInt(); val edgeCol = if (dark) 0xFF8A96A2.toInt() else 0xFF7A8794.toInt()
        val textCol = if (dark) 0xFFE3E9EF.toInt() else 0xFF1B2733.toInt(); val hotCol = 0xFF418FDE.toInt()
        val bevel = if (dark) 0xFF2E353C.toInt() else 0xFFDDE3E8.toInt()
        // axis triad from the cube's left-front-bottom corner, drawn first so the cube hides what is behind it (FreeCAD draws X red, Y green, Z blue)
        val o = scr(floatArrayOf(-1f, -1f, -1f))
        for ((v, col) in listOf(floatArrayOf(2.4f, -1f, -1f) to 0xFFE0403F.toInt(), floatArrayOf(-1f, 1.4f, -1f) to 0xFF47AB72.toInt(), floatArrayOf(-1f, -1f, 1.4f) to 0xFF418FDE.toInt())) {
            val p = scr(v); stroke.color = col; stroke.strokeWidth = 1.5f * dp
            c.drawLine(cx + o[0], cy + o[1], cx + p[0] * 0.9f + o[0] * 0.1f, cy + p[1] * 0.9f + o[1] * 0.1f, stroke)
        }
        // faces facing the viewer, far ones first
        val vis = faces.map { it to scr(it.n)[2] }.filter { it.second > 0.02f }.sortedBy { it.second }
        for ((fc, _) in vis) {
            fun quad(s0: Float, s1: Float, t0: Float, t1: Float) = Path().apply {
                listOf(s0 to t0, s1 to t0, s1 to t1, s0 to t1).forEachIndexed { i, (s, t) -> val p = scr(pt(fc.n, fc.a, fc.b, s, t))
                    if (i == 0) moveTo(cx + p[0], cy + p[1]) else lineTo(cx + p[0], cy + p[1]) }; close() }
            fill.color = bevel; c.drawPath(quad(-1f, 1f, -1f, 1f), fill)
            fill.color = faceCol; c.drawPath(quad(-INNER, INNER, -INNER, INNER), fill)
            // highlight the region the finger is on (face centre, edge strip or corner)
            hot?.let { h -> regionOf(fc, h)?.let { (s0, s1, t0, t1) -> fill.color = hotCol and 0x99FFFFFF.toInt(); c.drawPath(quad(s0, s1, t0, t1), fill) } }
            stroke.color = edgeCol; stroke.strokeWidth = 1.2f * dp; c.drawPath(quad(-1f, 1f, -1f, 1f), stroke)
            stroke.strokeWidth = 0.6f * dp; c.drawPath(quad(-INNER, INNER, -INNER, INNER), stroke)
            // the label, mapped onto the face like a texture
            val o = scr(pt(fc.n, fc.a, fc.b, 0f, 0f)); val ax = scr(pt(fc.n, fc.a, fc.b, 1f, 0f)); val bx = scr(pt(fc.n, fc.a, fc.b, 0f, 1f))
            val m = Matrix(); m.setPolyToPoly(floatArrayOf(0f, 0f, 100f, 0f, 0f, -100f), 0,
                floatArrayOf(cx + o[0], cy + o[1], cx + ax[0], cy + ax[1], cx + bx[0], cy + bx[1]), 0, 3)
            c.save(); c.concat(m); text.color = textCol; text.textSize = 32f
            text.textScaleX = 1f; text.textScaleX = minOf(1f, 108f / text.measureText(fc.label))      // fits the inner square
            c.drawText(fc.label, 0f, 10f, text); c.restore()
        }
        // orbit arrows, roll arrows, menu
        fill.color = if (dark) 0xFF8AB4F8.toInt() else 0xFF2A6FC9.toInt()
        for (b in buttons()) {
            val p = Path()
            when (b.kind) {
                "up" -> { p.moveTo(b.x, b.y - 7 * u); p.lineTo(b.x - 9 * u, b.y + 5 * u); p.lineTo(b.x + 9 * u, b.y + 5 * u) }
                "down" -> { p.moveTo(b.x, b.y + 7 * u); p.lineTo(b.x - 9 * u, b.y - 5 * u); p.lineTo(b.x + 9 * u, b.y - 5 * u) }
                "left" -> { p.moveTo(b.x - 7 * u, b.y); p.lineTo(b.x + 5 * u, b.y - 9 * u); p.lineTo(b.x + 5 * u, b.y + 9 * u) }
                "right" -> { p.moveTo(b.x + 7 * u, b.y); p.lineTo(b.x - 5 * u, b.y - 9 * u); p.lineTo(b.x - 5 * u, b.y + 9 * u) }
            }
            if (b.kind in setOf("up", "down", "left", "right")) { p.close(); c.drawPath(p, fill) }
            if (b.kind == "rollL" || b.kind == "rollR") {
                stroke.color = fill.color; stroke.strokeWidth = 2.2f * u; val r = 8 * u
                val rect = android.graphics.RectF(b.x - r, b.y - r, b.x + r, b.y + r)
                c.drawArc(rect, if (b.kind == "rollL") 200f else -20f, if (b.kind == "rollL") 140f else -140f, false, stroke)
                // arrow head at the end of the arc
                val endA = Math.toRadians(if (b.kind == "rollL") 340.0 else 200.0)
                val ex = b.x + r * Math.cos(endA).toFloat(); val ey = b.y + r * Math.sin(endA).toFloat()
                val hp = Path(); hp.moveTo(ex, ey + 5 * u); hp.lineTo(ex - 5 * u, ey - 2 * u); hp.lineTo(ex + 5 * u, ey - 2 * u); hp.close(); c.drawPath(hp, fill)
            }
            if (b.kind == "menu") {
                stroke.color = fill.color; stroke.strokeWidth = 1.2f * u; c.drawCircle(b.x, b.y, 11 * u, stroke)
                stroke.strokeWidth = 2f * u; for (k in -1..1) c.drawLine(b.x - 6 * u, b.y + k * 4 * u, b.x + 6 * u, b.y + k * 4 * u, stroke)
            }
        }
    }

    private class Btn(val kind: String, val x: Float, val y: Float)
    private fun buttons(): List<Btn> {
        val g = cubeR * 1.75f
        return listOf(Btn("up", cx, cy - g), Btn("down", cx, cy + g), Btn("left", cx - g, cy), Btn("right", cx + g, cy),
            Btn("rollL", cx - g * 0.8f, cy - g * 0.8f), Btn("rollR", cx + g * 0.8f, cy - g * 0.8f), Btn("menu", cx + g * 0.8f, cy + g * 0.8f))
    }

    /** The view direction under a screen point on the cube: a face centre, an edge strip or a corner. */
    private fun pick(x: Float, y: Float): FloatArray? {
        val vis = faces.map { it to scr(it.n)[2] }.filter { it.second > 0.02f }.sortedByDescending { it.second }
        for ((fc, _) in vis) {
            val o = scr(fc.n); val a = scr(fc.a); val b = scr(fc.b)
            // solve (x,y) - (c + o) = s*a + t*b
            val px = x - cx - o[0]; val py = y - cy - o[1]
            val det = a[0] * b[1] - a[1] * b[0]; if (abs(det) < 1e-6) continue
            val s = (px * b[1] - py * b[0]) / det; val t = (a[0] * py - a[1] * px) / det
            if (abs(s) > 1.02f || abs(t) > 1.02f) continue
            val d = fc.n.copyOf()
            if (s > INNER) for (k in 0..2) d[k] += fc.a[k]; if (s < -INNER) for (k in 0..2) d[k] -= fc.a[k]
            if (t > INNER) for (k in 0..2) d[k] += fc.b[k]; if (t < -INNER) for (k in 0..2) d[k] -= fc.b[k]
            val l = sqrt(dot(d, d)); return FloatArray(3) { d[it] / l }
        }
        return null
    }

    /** Region of face [fc] belonging to direction [d] (for the highlight), as s0, s1, t0, t1 — or null. */
    private fun regionOf(fc: Face, d: FloatArray): List<Float>? {
        if (dot(d, fc.n) <= 0.01f) return null
        val sa = dot(d, fc.a); val sb = dot(d, fc.b)
        fun span(v: Float) = when { v > 0.01f -> listOf(INNER, 1f); v < -0.01f -> listOf(-1f, -INNER); else -> listOf(-INNER, INNER) }
        val s = span(sa); val t = span(sb); return listOf(s[0], s[1], t[0], t[1])
    }

    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f; private var dragging = false
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val onCube = hypot(e.x - cx, e.y - cy) < cubeR * 1.5f
                val btn = buttons().firstOrNull { hypot(e.x - it.x, e.y - it.y) < 13 * dp }
                if (!onCube && btn == null) return false          // let the viewport have it
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; dragging = false
                hot = if (onCube) pick(e.x, e.y) else null; invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(e.x - downX, e.y - downY) > 8 * dp && hypot(downX - cx, downY - cy) < cubeR * 1.5f) { dragging = true; hot = null }
                if (dragging) { host.dragOrbit(e.x - lastX, e.y - lastY); invalidate() }
                lastX = e.x; lastY = e.y; return true
            }
            MotionEvent.ACTION_UP -> {
                hot = null
                if (!dragging) {
                    val btn = buttons().firstOrNull { hypot(e.x - it.x, e.y - it.y) < 13 * dp }
                    when (btn?.kind) {
                        "up" -> host.orbitBy(0f, step); "down" -> host.orbitBy(0f, -step)
                        "left" -> host.orbitBy(-step, 0f); "right" -> host.orbitBy(step, 0f)
                        "rollL" -> host.rollBy(90f); "rollR" -> host.rollBy(-90f)
                        "menu" -> host.menu(this)
                        else -> pick(e.x, e.y)?.let { host.viewAlong(it) }
                    }
                }
                dragging = false; invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { hot = null; dragging = false; invalidate(); return true }
        }
        return true
    }

    companion object { private const val INNER = 0.62f }
}
