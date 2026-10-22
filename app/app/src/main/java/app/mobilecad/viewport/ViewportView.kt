package app.mobilecad.viewport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.Manipulator
import app.mobilecad.freecad.SelRef
import app.mobilecad.sketch.P2
import app.mobilecad.sketch.SketchEditor
import app.mobilecad.sketch.SketchTool
import app.mobilecad.sketch.SkSel
import kotlin.math.abs
import kotlin.math.hypot
import app.mobilecad.sketch.SkCons
import app.mobilecad.ui.OS

/**
 * GL viewport + touch navigation over FreeCAD's document:
 *  model: 1 finger orbit · 2 fingers pan+pinch · tap select Face/Edge · double tap clear · 2-finger tap context menu ·
 *         long press precision pointer (60dp above finger) · 2-finger long press box select
 *  sketch: 1 finger draws (pointer 50dp above finger while dragging) · 2nd finger within 300ms cancels and pans · drag geometry
 */
private const val HALF_PI = (Math.PI / 2).toFloat()

class ViewportView(ctx: Context, private val app: AppState) : FrameLayout(ctx) {
    val cam = Camera()
    /** The camera picking and drawing input go through: [cam], or in MobileCAD XR a controller ray's virtual camera. */
    private var icam = cam
    private val renderer = Renderer(cam)
    private val gl = GLSurfaceView(ctx).apply { setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 24, 0); setRenderer(renderer); renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY }
    private val overlay = Overlay(ctx)
    var onContextMenu: (Float, Float) -> Unit = { _, _ -> }

    private val dp = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
    private val handler = Handler(Looper.getMainLooper())
    private enum class Mode { NONE, ORBIT, SLIDE, PRECISION, BOX, SKETCH_PLACE, SKETCH_DRAG, COMPONENT_DRAG, MANIPULATOR, LABEL_DRAG }
    private var mode = Mode.NONE
    private var downX = 0f; private var downY = 0f; private var downT = 0L; private var lastX = 0f; private var lastY = 0f
    private var lastCx = 0f; private var lastCy = 0f; private var lastD = 1f; private var lastA = 0f; private var slideMoved = false; private var slideStartT = 0L
    private var offX = 0f; private var offY = -60 * dp; private var lastTapT = -10_000L; private var suppressTap = false
    private var p0x = 0f; private var p0y = 0f; private var p1x = 0f; private var p1y = 0f
    private var dragSel: SkSel? = null; private var lastDragT = 0L
    /** A sketch dimension label being dragged (or held: hold = edit its value, like a desktop double click). */
    private var dragLab: SkCons? = null; private var labMoved = false
    private val labelHold = Runnable { val k = dragLab; val sk = sketch
        if (mode == Mode.LABEL_DRAG && !labMoved && k != null && sk != null) { mode = Mode.NONE; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); sk.editDimension(k) } }
    private var fitted = false
    private var lastScope = ""
    private var dragComp: String? = null; private val dragAcc = FloatArray(3)

    /** FreeCAD's NaviCube, top right. */
    private val navi = NaviCube(ctx, cam, object : NaviCube.Host {
        override fun viewAlong(dir: FloatArray) {
            val pitch = Math.asin(dir[2].coerceIn(-1f, 1f).toDouble()).toFloat()
            // straight up / down: keep the nearest of the four upright orientations (front view's is yaw -90°)
            val yaw = if (abs(dir[2]) > 0.999f) (Math.round((cam.yaw + HALF_PI) / HALF_PI) * HALF_PI - HALF_PI)
                      else Math.atan2(dir[1].toDouble(), dir[0].toDouble()).toFloat()
            animateTo(yaw, pitch, 0f)
        }
        override fun orbitBy(dYaw: Float, dPitch: Float) {
            val r = Math.toRadians(1.0).toFloat()
            animateTo(cam.yaw + dYaw * r, (cam.pitch + dPitch * r).coerceIn(-HALF_PI, HALF_PI), cam.roll)
        }
        override fun rollBy(deg: Float) = animateTo(cam.yaw, cam.pitch, cam.roll + Math.toRadians(deg.toDouble()).toFloat())
        override fun dragOrbit(dx: Float, dy: Float) { cam.orbit(dx * 1.5f, dy * 1.5f); refresh() }
        override fun menu(anchor: View) {
            val pm = android.widget.PopupMenu(context, anchor)
            pm.menu.add(0, 1, 0, "Orthographic").apply { isCheckable = true; isChecked = cam.orthographic }
            pm.menu.add(0, 2, 1, "Perspective").apply { isCheckable = true; isChecked = !cam.orthographic }
            pm.menu.add(0, 3, 2, "Isometric"); pm.menu.add(0, 4, 3, "Fit all")
            pm.setOnMenuItemClickListener { when (it.itemId) { 1 -> cam.orthographic = true; 2 -> cam.orthographic = false
                3 -> stdView("Isometric"); 4 -> fitAll() }; refresh(); true }
            pm.show()
        }
        override val dark get() = OS.dark
    })

    init {
        addView(gl, LayoutParams(-1, -1)); addView(overlay, LayoutParams(-1, -1))
        addView(navi, LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END))
        gl.isClickable = false; overlay.isClickable = false
    }

    // ------------------------------------------------------------------ animated camera moves (FreeCAD NavigationAnimation)
    private var anim: Runnable? = null
    fun animateTo(yaw: Float, pitch: Float, roll: Float, ms: Long = 280) {
        anim?.let { handler.removeCallbacks(it) }
        val y0 = cam.yaw; val p0 = cam.pitch; val r0 = cam.roll; val t0 = SystemClock.uptimeMillis()
        // shortest way round
        fun near(a: Float, from: Float): Float { var d = (a - from) % (2 * Math.PI.toFloat()); if (d > Math.PI) d -= 2 * Math.PI.toFloat(); if (d < -Math.PI) d += 2 * Math.PI.toFloat(); return from + d }
        val y1 = near(yaw, y0); val r1 = near(roll, r0)
        val r = object : Runnable { override fun run() {
            val k = ((SystemClock.uptimeMillis() - t0).toFloat() / ms).coerceIn(0f, 1f); val e = k * k * (3 - 2 * k)
            cam.yaw = y0 + (y1 - y0) * e; cam.pitch = p0 + (pitch - p0) * e; cam.roll = r0 + (r1 - r0) * e; refresh()
            if (k < 1f) handler.postDelayed(this, 16) else anim = null } }
        anim = r; handler.post(r)
    }

    private val sketch: SketchEditor? get() = app.sketch

    /** Rebuild GPU objects from the engine's tessellation + current selection. */
    fun sync() {
        val objs = app.meshes.map { (n, m) -> RenderObj(n, m, app.obj(n)?.type == "Sketcher::SketchObject", app.ghosts[n]) }
        renderer.objs = objs
        renderer.selectedObjs = app.selection.filter { it.sub.isEmpty() }.map { it.obj }.toSet()
        renderer.selectedFaces = app.selection.filter { it.sub.startsWith("Face") }.map { it.obj to it.sub.removePrefix("Face").toInt() }.toSet()
        renderer.selectedEdges = app.selection.filter { it.sub.startsWith("Edge") }.map { it.obj to it.sub.removePrefix("Edge").toInt() }.toSet()
        renderer.dim = sketch != null
        renderer.showGrid = app.showGrid
        renderer.clip = sketch?.takeIf { it.viewSection }?.let { ed -> val n = ed.normal; val o = ed.toWorld(P2(0.0, 0.0))
            floatArrayOf(n[0].toFloat(), n[1].toFloat(), n[2].toFloat(), -(n[0] * o[0] + n[1] * o[1] + n[2] * o[2]).toFloat()) } ?: app.clipPlane
        renderer.drawStyle = app.drawStyle; renderer.colors = app.colors.toMap(); renderer.faceColors = app.faceColors.toMap(); renderer.textures = app.textures.toMap(); renderer.background = app.background; renderer.dark = OS.dark; renderer.transparent = app.transparent.toSet()
        renderer.axisCross = app.axisCross
        renderer.boxes = if (!app.selBoundingBox) emptyList() else objs.filter { o -> app.selection.any { it.obj == o.name } }.map { it.min to it.max }
        sketch?.let { ed -> ed.onChanged = { overlayChanged(); ed.tick.intValue++ }
            if (ed.viewNormalRequest == null) { ed.viewNormalRequest = { cam.lookAlong(ed.normal, ed.toWorld(P2(0.0, 0.0)))
                // Sketcher_ViewSketch frames the sketch (its geometry, else a 100 mm area around the origin)
                val pts = ed.geos.flatMap { g -> g.polyline() + g.points().map { it.second } }.map { ed.toWorld(it) }
                if (pts.isNotEmpty()) cam.fit(FloatArray(3) { k -> pts.minOf { it[k] }.toFloat() }, FloatArray(3) { k -> pts.maxOf { it[k] }.toFloat() })
                refresh() }; if (ed.pendingNormal) ed.viewNormal() } }
        // fit the first time geometry appears, and whenever the tab (Part / Assembly) changes the scope
        if (app.workspace.id != lastScope) { lastScope = app.workspace.id; fitted = false }
        if (!fitted && objs.any { it.mesh.tris.isNotEmpty() }) { fitAll(); fitted = true }
        refresh()
    }

    fun refresh() { gl.requestRender(); overlay.invalidate(); navi.invalidate(); if (xr) app.xrChanged?.invoke() }
    private fun overlayChanged() { overlay.invalidate(); if (xr) app.xrChanged?.invoke() }
    /** A newly opened document is framed once its geometry arrives. */
    fun refitOnNextSync() { fitted = false }

    fun fitAll() {
        val os = renderer.objs; if (os.isEmpty()) return
        val mn = FloatArray(3) { Float.MAX_VALUE }; val mx = FloatArray(3) { -Float.MAX_VALUE }
        os.filter { o -> (0..2).all { o.min[it] <= o.max[it] } }.forEach { o -> for (k in 0..2) { mn[k] = minOf(mn[k], o.min[k]); mx[k] = maxOf(mx[k], o.max[k]) } }
        if ((0..2).any { mn[it] > mx[it] }) return
        cam.fit(mn, mx); refresh()
    }

    fun setView(yaw: Float, pitch: Float) { cam.setView(yaw, pitch); refresh() }

    // ------------------------------------------------------------------ Std_View* commands
    /** FreeCAD Gui::Camera standard orientations (Std_ViewTop ... Std_ViewTrimetric), then fit like the desktop does. */
    fun stdView(name: String) {
        val r = (Math.sqrt(2.0) / 2).toFloat()
        val q = when (name) {
            "Top" -> floatArrayOf(0f, 0f, 0f, 1f); "Bottom" -> floatArrayOf(1f, 0f, 0f, 0f)
            "Front" -> floatArrayOf(r, 0f, 0f, r); "Rear" -> floatArrayOf(0f, r, r, 0f)
            "Right" -> floatArrayOf(.5f, .5f, .5f, .5f); "Left" -> floatArrayOf(-.5f, .5f, .5f, -.5f)
            "Dimetric" -> floatArrayOf(0.567952f, 0.103751f, 0.146726f, 0.803205f)
            "Trimetric" -> floatArrayOf(0.446015f, 0.119509f, 0.229575f, 0.856787f)
            else -> floatArrayOf(0.424708f, 0.17592f, 0.339851f, 0.820473f)        // Isometric
        }
        // like the NaviCube: turn (animated) and keep the zoom
        val y = cam.yaw; val p = cam.pitch; val rl = cam.roll
        cam.setOrientation(q); val ty = cam.yaw; val tp = cam.pitch; val tr = cam.roll
        cam.yaw = y; cam.pitch = p; cam.roll = rl; animateTo(ty, tp, tr)
    }
    /** Std_ViewRotateLeft / Right: 90° about the view axis. */
    fun rotateView(deg: Float) = animateTo(cam.yaw, cam.pitch, cam.roll + Math.toRadians(deg.toDouble()).toFloat())
    /** Std_ViewZoomIn / Out. */
    fun zoom(factor: Float) { cam.dist = (cam.dist * factor).coerceIn(20f, 5000f); refresh() }
    /** Std_ViewFitSelection. */
    fun fitSelection() {
        val names = app.selection.map { it.obj }.toSet(); val os = renderer.objs.filter { it.name in names }
        if (os.isEmpty()) { fitAll(); return }
        val mn = FloatArray(3) { Float.MAX_VALUE }; val mx = FloatArray(3) { -Float.MAX_VALUE }
        os.forEach { o -> for (k in 0..2) { mn[k] = minOf(mn[k], o.min[k]); mx[k] = maxOf(mx[k], o.max[k]) } }
        cam.fit(mn, mx); refresh()
    }
    /** Std_ViewScreenShot: grab the next frame. */
    fun screenshot(cb: (android.graphics.Bitmap) -> Unit) { renderer.capture = { b -> post { cb(b) } }; gl.requestRender() }
    /** Std_StoreWorkingView / Std_ViewSaveCamera and their recall counterparts. */
    fun cameraState() = floatArrayOf(cam.yaw, cam.pitch, cam.roll, cam.dist, cam.tx, cam.ty, cam.tz, if (cam.orthographic) 1f else 0f)
    fun restoreCamera(c: FloatArray) { cam.yaw = c[0]; cam.pitch = c[1]; cam.roll = c[2]; cam.dist = c[3]; cam.tx = c[4]; cam.ty = c[5]; cam.tz = c[6]; cam.orthographic = c[7] == 1f; refresh() }
    /** Std_DemoMode (View Turntable): spin about Z until toggled off or the user touches the view. */
    var turntable = false
        set(v) { field = v; if (v) handler.post(spin) }
    private val spin: Runnable = object : Runnable { override fun run() { if (!turntable) return; cam.yaw += 0.01f; refresh(); handler.postDelayed(this, 16) } }

    // ------------------------------------------------------------------ touch
    private val longPress = Runnable { if (mode == Mode.NONE && sketch == null && !xr) { mode = Mode.PRECISION; offX = 0f; offY = -60 * dp; precision(lastX, lastY) } }
    private val longPress2 = Runnable { if (mode == Mode.SLIDE && !slideMoved && sketch == null) { mode = Mode.BOX; overlay.pointer = null; box() } }
    private fun readPts(e: MotionEvent) { p0x = e.getX(0); p0y = e.getY(0); if (e.pointerCount > 1) { p1x = e.getX(1); p1y = e.getY(1) } }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis(); val sk = sketch
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                turntable = false
                mode = Mode.NONE; slideMoved = false; suppressTap = false
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; downT = now
                if (sk != null) {
                    val p = planePt(e.x, e.y)
                    // a dimension label under the finger, in any tool: drag moves it, hold edits it
                    dragLab = if (p != null && !sk.hasPending()) sk.pickLabel(p, tolMm(12f)) else null
                    dragSel = if (dragLab == null && sk.tool == SketchTool.SELECT && p != null) sk.pickElement(p, tolMm(12f)) else null
                    mode = if (dragLab != null) Mode.LABEL_DRAG else if (dragSel != null) Mode.SKETCH_DRAG else Mode.SKETCH_PLACE
                    if (dragLab != null) { labMoved = false; handler.postDelayed(labelHold, ViewConfiguration.getLongPressTimeout().toLong()) }
                    // pressing while a shape is in progress previews it (rubber band, live dimension, inference) at once
                    if (mode == Mode.SKETCH_PLACE && sk.tool.points != 0 && sk.hasPending() && p != null) sk.hover(sk.snap(p, tolMm(18f)))
                } else if (manipHit(e.x, e.y)) {
                    mode = Mode.MANIPULATOR; manipStartValue = app.manipulator!!.value; app.manipStart(app.manipulator!!)
                } else {
                    dragComp = if (app.inAssembly && app.mate == null) pickFace(e.x, e.y)?.obj else null
                    handler.removeCallbacks(longPress); handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                handler.removeCallbacks(longPress); readPts(e)
                if (e.pointerCount == 2) {
                    if (mode == Mode.PRECISION) { offX = p1x - p0x; offY = p1y - p0y; precision(p0x, p0y) }
                    else {
                        // sketch: a second finger within 300ms cancels the point being placed (BTSketchCreatorTool.onPointerDown)
                        if (sk != null) { sk.hover(null); overlay.pointer = null }
                        mode = Mode.SLIDE; slideMoved = false; slideStartT = now
                        lastCx = (p0x + p1x) / 2; lastCy = (p0y + p1y) / 2; lastD = hypot(p1x - p0x, p1y - p0y).coerceAtLeast(1f)
                        lastA = kotlin.math.atan2(p1y - p0y, p1x - p0x)
                        handler.removeCallbacks(longPress2); handler.postDelayed(longPress2, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                readPts(e)
                when (mode) {
                    Mode.NONE -> if (e.pointerCount == 1 && hypot(e.x - downX, e.y - downY) > slop) {
                        handler.removeCallbacks(longPress)
                        mode = if (app.boxSelect != null) Mode.BOX else if (dragComp != null) Mode.COMPONENT_DRAG else Mode.ORBIT; lastX = e.x; lastY = e.y }
                    Mode.MANIPULATOR -> app.manipulator?.let { m ->
                        // project the finger travel onto the arrow's screen direction (mm per pixel along the arrow)
                        val a = icam.project(m.base[0], m.base[1], m.base[2]); val b = icam.project(m.base[0] + m.dir[0], m.base[1] + m.dir[1], m.base[2] + m.dir[2])
                        if (a != null && b != null) {
                            val vx = b[0] - a[0]; val vy = b[1] - a[1]; val l2 = vx * vx + vy * vy
                            if (l2 > 1e-6f) {
                                val mm = ((e.x - downX) * vx + (e.y - downY) * vy) / l2
                                app.manipDrag(m, maxOf(0.01, manipStartValue + mm)); overlayChanged()
                            }
                        }
                    }
                    Mode.COMPONENT_DRAG -> {
                        val (r, u) = icam.basis(); val k = icam.unitsPerPixel(); val dx = (e.x - lastX) * k; val dy = -(e.y - lastY) * k
                        for (i in 0..2) dragAcc[i] += r[i] * dx + u[i] * dy
                        lastX = e.x; lastY = e.y
                        if (now - lastDragT > 120) { lastDragT = now; flushDrag() }
                    }
                    Mode.ORBIT -> { icam.orbit(e.x - lastX, e.y - lastY); lastX = e.x; lastY = e.y; refresh() }
                    Mode.SLIDE -> if (e.pointerCount >= 2) {
                        val cx = (p0x + p1x) / 2; val cy = (p0y + p1y) / 2; val d = hypot(p1x - p0x, p1y - p0y).coerceAtLeast(1f)
                        // two-finger twist rotates the view about the screen axis (FreeCAD touchpad navigation)
                        val a = kotlin.math.atan2(p1y - p0y, p1x - p0x)
                        var da = a - lastA; if (da > Math.PI) da -= (2 * Math.PI).toFloat(); if (da < -Math.PI) da += (2 * Math.PI).toFloat()
                        if (!slideMoved && (hypot(cx - lastCx, cy - lastCy) > slop || abs(d - lastD) > slop || abs(da) > 0.06f)) { slideMoved = true; handler.removeCallbacks(longPress2) }
                        if (slideMoved) { icam.slide(cx - lastCx, cy - lastCy, lastD / d); icam.roll += da; lastCx = cx; lastCy = cy; lastD = d; lastA = a; refresh() }
                    }
                    Mode.PRECISION -> { if (e.pointerCount >= 2) { offX = p1x - p0x; offY = p1y - p0y }; precision(p0x, p0y) }
                    Mode.BOX -> if (e.pointerCount >= 2) box()
                        else if (app.boxSelect != null) { overlay.box = RectF(minOf(downX, e.x), minOf(downY, e.y), maxOf(downX, e.x), maxOf(downY, e.y)); overlay.invalidate() }
                    Mode.SKETCH_PLACE -> if (sk != null && e.pointerCount == 1 && hypot(e.x - downX, e.y - downY) > slop) {
                        val px = e.x; val py = e.y - fingerOffset                // sketch_drag_pointer_offset = 50dp
                        overlay.pointer = floatArrayOf(e.x, e.y, px, py)
                        planePt(px, py)?.let { sk.hover(sk.snap(it, tolMm(18f))) }
                    }
                    Mode.LABEL_DRAG -> if (sk != null) {
                        if (!labMoved && hypot(e.x - downX, e.y - downY) > slop) { labMoved = true; handler.removeCallbacks(labelHold) }
                        if (labMoved) planePt(e.x, e.y)?.let { p -> dragLab?.let { sk.dragLabel(it, p, false) } } }
                    Mode.SKETCH_DRAG -> if (sk != null && now - lastDragT > 60) { lastDragT = now
                        planePt(e.x, e.y)?.let { p -> dragSel?.let { sk.drag(it, p) } } }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                handler.removeCallbacks(longPress2)
                if (mode == Mode.SLIDE) {
                    if (!slideMoved && now - slideStartT < 400 && sk == null) { onContextMenu((p0x + p1x) / 2, (p0y + p1y) / 2); mode = Mode.NONE; suppressTap = true }
                    else if (sk != null) { mode = Mode.NONE; suppressTap = true }
                    else { val k = if (e.actionIndex == 0) 1 else 0; lastX = e.getX(k); lastY = e.getY(k); mode = Mode.ORBIT }
                } else if (mode == Mode.BOX) { commitBox(); suppressTap = true }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress); handler.removeCallbacks(longPress2)
                val quick = now - downT < ViewConfiguration.getTapTimeout() + 250
                when (mode) {
                    Mode.NONE -> if (!suppressTap && quick && app.draft != null) {
                        if (now - lastTapT < ViewConfiguration.getDoubleTapTimeout()) { app.draft?.let { t -> if (t.pts.isNotEmpty()) t.pts.removeAt(t.pts.size - 1) }; app.finishDraft(); lastTapT = -10_000L }
                        else { draftSnap(e.x, e.y)?.let { app.draftPoint(it) }; lastTapT = now }
                    } else if (!suppressTap && quick) {
                        if (now - lastTapT < ViewConfiguration.getDoubleTapTimeout()) { app.clearSelection(); lastTapT = -10_000L; app.refresh() }
                        else { tapSelect(e.x, e.y); lastTapT = now }
                    }
                    Mode.PRECISION -> if (app.draft != null) { draftSnap(e.x + offX, e.y + offY)?.let { app.draftPoint(it) }; overlay.pointer = null }
                        else pick(e.x + offX, e.y + offY, 1f)?.let { app.select(it); sync() }
                    Mode.BOX -> commitBox()
                    Mode.SKETCH_PLACE -> if (sk != null && !suppressTap) {
                        val dragged = overlay.pointer != null
                        if (!dragged && quick && now - lastTapT < ViewConfiguration.getDoubleTapTimeout()) { sk.finishShape(); lastTapT = -10_000L }  // double tap finishes B-splines / ends chains
                        else {
                            val (px, py) = if (dragged) e.x to e.y - fingerOffset else e.x to e.y
                            if (sk.tool == SketchTool.PROJECTION || sk.tool == SketchTool.INTERSECTION) pick(px, py, 8 * dp)?.let { r -> sk.externalPick(r.obj, r.sub) }
                            else planePt(px, py)?.let { p -> if (sk.tool.points == 0) sk.tap(p, tolMm(12f)) else sk.place(sk.snap(p, tolMm(18f))) }
                            lastTapT = now
                        }
                    }
                    Mode.MANIPULATOR -> app.manipulator?.let { app.manipEnd(it) }
                    Mode.LABEL_DRAG -> if (sk != null) { handler.removeCallbacks(labelHold); val p = planePt(e.x, e.y)
                        if (labMoved && p != null) dragLab?.let { sk.dragLabel(it, p, true) }
                        else if (!labMoved && p != null && sk.tool == SketchTool.SELECT) sk.tap(p, tolMm(12f)) }
                    Mode.SKETCH_DRAG -> if (sk != null) { val p = planePt(e.x, e.y)
                        if (hypot(e.x - downX, e.y - downY) < slop && p != null) sk.tap(p, tolMm(12f)) else p?.let { q -> dragSel?.let { sk.drag(it, q) } } }
                    else -> {}
                }
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPress); handler.removeCallbacks(longPress2); endGesture() }
        }
        return true
    }

    private var manipStartValue = 0.0
    private fun manipTip(c: Camera = icam): FloatArray? = app.manipulator?.let { m -> val v = m.value.toFloat()
        c.project(m.base[0] + m.dir[0] * v, m.base[1] + m.dir[1] * v, m.base[2] + m.dir[2] * v) }
    private fun manipHit(x: Float, y: Float) = manipTip()?.let { t -> hypot(x - t[0], y - t[1]) < 28 * dp } ?: false

    private fun flushDrag() {
        val c = dragComp ?: return
        if (dragAcc.any { it != 0f }) { app.dragComponent(c, dragAcc.copyOf()); dragAcc.fill(0f) }
    }

    private fun endGesture() { handler.removeCallbacks(labelHold); dragLab = null; if (mode == Mode.COMPONENT_DRAG) flushDrag(); dragComp = null; mode = Mode.NONE; overlay.pointer = null; overlay.box = null; dragSel = null; renderer.hover = null; sketch?.hover(null); refresh() }

    private fun tolMm(px: Float) = (px * dp * icam.unitsPerPixel()).toDouble()
    private fun planePt(x: Float, y: Float): P2? = sketch?.fromRay(icam.ray(x, y))

    // ------------------------------------------------------------------ picking (FreeCAD sub-elements)
    private fun tapSelect(x: Float, y: Float) { lastTap = x to y; val r = pick(x, y, 8 * dp); if (r != null) { app.select(r); sync() } }
    private var lastTap: Pair<Float, Float>? = null

    /** Std_ClarifySelection: every vertex, edge and face under the last tap, nearest first. */
    fun pickAll(): List<SelRef> {
        val (x, y) = lastTap ?: return emptyList()
        val out = ArrayList<Pair<Float, SelRef>>()
        for (o in renderer.objs) {
            val v = o.mesh.verts; var i = 0
            while (i + 3 < v.size) { icam.project(v[i], v[i + 1], v[i + 2])?.let { p -> if (hypot(x - p[0], y - p[1]) < 14 * dp) out.add(0f to SelRef(o.ref, "Vertex${v[i + 3].toInt()}", floatArrayOf(v[i], v[i + 1], v[i + 2]))) }; i += 4 }
            val a = o.mesh.edges; val seenE = HashSet<Int>(); i = 0
            while (i + 6 < a.size) { val id = a[i + 6].toInt()
                if (id !in seenE) { val s1 = icam.project(a[i], a[i + 1], a[i + 2]); val s2 = icam.project(a[i + 3], a[i + 4], a[i + 5])
                    if (s1 != null && s2 != null && segDist(x, y, s1, s2) < 12 * dp) { seenE.add(id); out.add(1f to SelRef(o.ref, "Edge$id", floatArrayOf(a[i], a[i + 1], a[i + 2]))) } }
                i += 7 }
            val r = icam.ray(x, y); val t = o.mesh.tris; val seenF = HashSet<Int>(); i = 0
            while (i + 20 < t.size) { val id = t[i + 6].toInt()
                if (id !in seenF) { val d = tri(r, t, i); if (d >= 0f) { seenF.add(id); out.add(2f + d to SelRef(o.ref, "Face$id", floatArrayOf(r[0] + r[3] * d, r[1] + r[4] * d, r[2] + r[5] * d))) } }
                i += 21 }
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    /** Vertexes win within 12dp, then edges within [radius], then faces by ray cast — restricted by
     *  the Part selection filter (Part_VertexSelection / EdgeSelection / FaceSelection). */
    fun pick(x: Float, y: Float, radius: Float): SelRef? {
        val filter = app.selFilter
        // as FreeCAD's picking: what lies behind the face under the pointer is hidden (a box's back edges project onto its
        // front faces); depths along the ray, with a margin of a few pick radii for elements on that face's own outline
        val face = pickFace(x, y); val ray = icam.ray(x, y)
        fun depth(px: Float, py: Float, pz: Float) = (px - ray[0]) * ray[3] + (py - ray[1]) * ray[4] + (pz - ray[2]) * ray[5]
        val faceT = face?.point?.let { depth(it[0], it[1], it[2]) }
        val margin = faceT?.let { 4 * 12 * dp * icam.unitsPerPixel() + it * 0.002f } ?: 0f
        fun hidden(px: Float, py: Float, pz: Float) = faceT != null && depth(px, py, pz) > faceT + margin
        var bestV: SelRef? = null; var bv = 12 * dp
        if (filter == null || filter == "Vertex") for (o in renderer.objs) { val a = o.mesh.verts; var i = 0
            while (i + 3 < a.size) { val p = icam.project(a[i], a[i + 1], a[i + 2])
                if (p != null && !hidden(a[i], a[i + 1], a[i + 2])) { val d = hypot(x - p[0], y - p[1]); if (d < bv) { bv = d; bestV = SelRef(o.ref, "Vertex${a[i + 3].toInt()}", floatArrayOf(a[i], a[i + 1], a[i + 2])) } }
                i += 4 } }
        if (bestV != null || filter == "Vertex") return bestV
        var bestE: SelRef? = null; var bd = maxOf(radius, 10 * dp)
        for (o in renderer.objs) { val a = o.mesh.edges; var i = 0
            while (i + 6 < a.size) {
                val s1 = icam.project(a[i], a[i + 1], a[i + 2]); val s2 = icam.project(a[i + 3], a[i + 4], a[i + 5])
                if (s1 != null && s2 != null) { val d = segDist(x, y, s1, s2); val t = segT(x, y, s1, s2)
                    if (d < bd && !hidden(a[i] + (a[i + 3] - a[i]) * t, a[i + 1] + (a[i + 4] - a[i + 1]) * t, a[i + 2] + (a[i + 5] - a[i + 2]) * t)) { bd = d
                    bestE = SelRef(o.ref, "Edge${a[i + 6].toInt()}", floatArrayOf(a[i] + (a[i + 3] - a[i]) * t, a[i + 1] + (a[i + 4] - a[i + 1]) * t, a[i + 2] + (a[i + 5] - a[i + 2]) * t)) } }
                i += 7 }
        }
        if (filter == "Edge") return bestE
        if (filter == "Face") return face
        if (bestE != null && (face == null || bd < 8 * dp)) return bestE
        return face ?: bestE
    }

    private fun segT(x: Float, y: Float, a: FloatArray, b: FloatArray): Float {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l2 = dx * dx + dy * dy
        return if (l2 < 1e-6f) 0f else (((x - a[0]) * dx + (y - a[1]) * dy) / l2).coerceIn(0f, 1f)
    }

    private fun segDist(x: Float, y: Float, a: FloatArray, b: FloatArray): Float {
        val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l2 = dx * dx + dy * dy
        val t = if (l2 < 1e-6f) 0f else (((x - a[0]) * dx + (y - a[1]) * dy) / l2).coerceIn(0f, 1f)
        return hypot(x - (a[0] + t * dx), y - (a[1] + t * dy))
    }

    private fun pickFace(x: Float, y: Float): SelRef? {
        val r = icam.ray(x, y); var best = Float.MAX_VALUE; var hit: SelRef? = null
        for (o in renderer.objs) { val t = o.mesh.tris; var i = 0
            while (i + 20 < t.size) {
                val d = tri(r, t, i); if (d in 0f..best) { best = d; hit = SelRef(o.ref, "Face${t[i + 6].toInt()}", floatArrayOf(r[0] + r[3] * d, r[1] + r[4] * d, r[2] + r[5] * d)) }
                i += 21 } }
        return hit
    }

    private fun tri(r: FloatArray, t: FloatArray, i: Int): Float {
        val e1x = t[i + 7] - t[i]; val e1y = t[i + 8] - t[i + 1]; val e1z = t[i + 9] - t[i + 2]
        val e2x = t[i + 14] - t[i]; val e2y = t[i + 15] - t[i + 1]; val e2z = t[i + 16] - t[i + 2]
        val hx = r[4] * e2z - r[5] * e2y; val hy = r[5] * e2x - r[3] * e2z; val hz = r[3] * e2y - r[4] * e2x
        val a = e1x * hx + e1y * hy + e1z * hz; if (abs(a) < 1e-9f) return -1f
        val f = 1f / a; val sx = r[0] - t[i]; val sy = r[1] - t[i + 1]; val sz = r[2] - t[i + 2]
        val u = f * (sx * hx + sy * hy + sz * hz); if (u < 0f || u > 1f) return -1f
        val qx = sy * e1z - sz * e1y; val qy = sz * e1x - sx * e1z; val qz = sx * e1y - sy * e1x
        val v = f * (r[3] * qx + r[4] * qy + r[5] * qz); if (v < 0f || u + v > 1f) return -1f
        return f * (e2x * qx + e2y * qy + e2z * qz)
    }

    private fun precision(fx: Float, fy: Float) {
        val px = fx + offX; val py = fy + offY
        overlay.pointer = floatArrayOf(fx, fy, px, py)
        app.draft?.let { t -> t.hover = draftSnap(px, py); refresh(); return }
        renderer.hover = pick(px, py, 1f)?.let { it.obj to it.sub }; refresh()
    }

    // ------------------------------------------------------------------ Draft Snapper (draftguitools/gui_snapper.py)
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /** Ray / working-plane intersection. */
    private fun onPlane(x: Float, y: Float): DoubleArray? {
        val r = icam.ray(x, y); val wp = app.workPlane
        val o = doubleArrayOf(r[0].toDouble(), r[1].toDouble(), r[2].toDouble()); val d = doubleArrayOf(r[3].toDouble(), r[4].toDouble(), r[5].toDouble())
        val den = dot(d, wp.w); if (kotlin.math.abs(den) < 1e-9) return null
        val t = dot(DoubleArray(3) { wp.pos[it] - o[it] }, wp.w) / den
        return DoubleArray(3) { o[it] + d[it] * t }
    }

    /** The point the desktop Snapper would return at screen (x, y): object snaps first, then the working plane with grid,
     *  ortho and angle constraints relative to the last point. Sets the tool's snap marker. */
    fun draftSnap(x: Float, y: Float): DoubleArray? {
        val t = app.draft ?: return null
        if (t.viewPlane) {                       // polygon on screen: plane through the target, normal to the view
            val r = icam.ray(x, y); val e = icam.eye()
            val n = doubleArrayOf((icam.tx - e[0]).toDouble(), (icam.ty - e[1]).toDouble(), (icam.tz - e[2]).toDouble())
            val o = doubleArrayOf(r[0].toDouble(), r[1].toDouble(), r[2].toDouble()); val d = doubleArrayOf(r[3].toDouble(), r[4].toDouble(), r[5].toDouble())
            val den = dot(d, n); if (kotlin.math.abs(den) < 1e-12) return null
            val tt = dot(doubleArrayOf(icam.tx - o[0], icam.ty - o[1], icam.tz - o[2]), n) / den
            return DoubleArray(3) { o[it] + d[it] * tt }.also { t.hover = it; t.snapKind = null }
        }
        val on = app.draftSnaps; val lock = "Lock" in on
        val last = t.pts.lastOrNull()
        val rad = 18 * dp
        if (lock) {
            // endpoint / midpoint / centre targets
            val st = app.snapTargets; var best: DoubleArray? = null; var bd = rad; var kind = ""
            var i = 0
            while (i + 3 < st.size) {
                val k = st[i + 3].toInt(); val name = when (k) { 0 -> "Endpoint"; 1 -> "Midpoint"; else -> "Center" }
                if (name in on) icam.project(st[i], st[i + 1], st[i + 2])?.let { p -> val dd = hypot(x - p[0], y - p[1])
                    if (dd < bd) { bd = dd; best = doubleArrayOf(st[i].toDouble(), st[i + 1].toDouble(), st[i + 2].toDouble()); kind = name } }
                i += 4
            }
            // intersections of nearby edge segments (screen space), then perpendicular foot / nearest point on an edge
            if (best == null && "Intersection" in on) intersection(x, y, rad)?.let { best = it; kind = "Intersection" }
            if (best == null && ("Near" in on || "Perpendicular" in on)) {
                var bestD = rad
                for (o in renderer.objs) { val a = o.mesh.edges; var j = 0
                    while (j + 6 < a.size) {
                        val s1 = icam.project(a[j], a[j + 1], a[j + 2]); val s2 = icam.project(a[j + 3], a[j + 4], a[j + 5])
                        if (s1 != null && s2 != null) { val dd = segDist(x, y, s1, s2)
                            if (dd < bestD) { bestD = dd
                                val p1 = doubleArrayOf(a[j].toDouble(), a[j + 1].toDouble(), a[j + 2].toDouble()); val p2 = doubleArrayOf(a[j + 3].toDouble(), a[j + 4].toDouble(), a[j + 5].toDouble())
                                val dir = DoubleArray(3) { p2[it] - p1[it] }; val l2 = dot(dir, dir).coerceAtLeast(1e-12)
                                if ("Perpendicular" in on && last != null) {
                                    val tt = dot(DoubleArray(3) { last[it] - p1[it] }, dir) / l2
                                    if (tt in 0.0..1.0) { best = DoubleArray(3) { p1[it] + dir[it] * tt }; kind = "Perpendicular"; continue }
                                }
                                if ("Near" in on) { val tt = segT(x, y, s1, s2).toDouble(); best = DoubleArray(3) { p1[it] + dir[it] * tt }; kind = "Near" }
                            } }
                        j += 7 } }
            }
            best?.let { b -> t.snapKind = kind; t.hover = b
                return if ("WorkingPlane" in on) app.workPlane.let { wp -> val l = wp.local(b); wp.global(doubleArrayOf(l[0], l[1], 0.0)) } else b }
        }
        val p = onPlane(x, y) ?: return null
        val wp = app.workPlane; var l = wp.local(p); t.snapKind = null
        if (lock && last != null) {
            val ll = wp.local(last)
            if ("Ortho" in on) { if (kotlin.math.abs(l[0] - ll[0]) > kotlin.math.abs(l[1] - ll[1])) l[1] = ll[1] else l[0] = ll[0]; t.snapKind = "Ortho" }
            else if ("Angle" in on) {   // Draft_Snap_Angle: 15° steps around the last point
                val dx = l[0] - ll[0]; val dy = l[1] - ll[1]; val r = Math.hypot(dx, dy); val a = Math.round(Math.atan2(dy, dx) / (Math.PI / 12)) * (Math.PI / 12)
                l = doubleArrayOf(ll[0] + r * Math.cos(a), ll[1] + r * Math.sin(a), l[2]); t.snapKind = "Angle"
            }
        }
        if (lock && "Grid" in on && t.snapKind == null) { val g = app.gridSpacing; l = doubleArrayOf(Math.round(l[0] / g) * g, Math.round(l[1] / g) * g, l[2]); t.snapKind = "Grid" }
        val out = wp.global(doubleArrayOf(l[0], l[1], 0.0)); t.hover = out; return out
    }

    private fun intersection(x: Float, y: Float, rad: Float): DoubleArray? {
        val segs = ArrayList<FloatArray>()      // screen segments near the finger with their 3D ends
        for (o in renderer.objs) { val a = o.mesh.edges; var j = 0
            while (j + 6 < a.size) { val s1 = icam.project(a[j], a[j + 1], a[j + 2]); val s2 = icam.project(a[j + 3], a[j + 4], a[j + 5])
                if (s1 != null && s2 != null && segDist(x, y, s1, s2) < rad) segs.add(floatArrayOf(s1[0], s1[1], s2[0], s2[1], a[j], a[j + 1], a[j + 2], a[j + 3], a[j + 4], a[j + 5], a[j + 6]))
                j += 7 } }
        var best: DoubleArray? = null; var bd = rad
        for (i in segs.indices) for (k in i + 1 until segs.size) {
            val a = segs[i]; val b = segs[k]; if (a[10] == b[10]) continue
            val d1x = a[2] - a[0]; val d1y = a[3] - a[1]; val d2x = b[2] - b[0]; val d2y = b[3] - b[1]
            val den = d1x * d2y - d1y * d2x; if (kotlin.math.abs(den) < 1e-6f) continue
            val ta = ((b[0] - a[0]) * d2y - (b[1] - a[1]) * d2x) / den; val tb = ((b[0] - a[0]) * d1y - (b[1] - a[1]) * d1x) / den
            if (ta !in 0f..1f || tb !in 0f..1f) continue
            val sx = a[0] + d1x * ta; val sy = a[1] + d1y * ta; val dd = hypot(x - sx, y - sy)
            if (dd < bd) { bd = dd; best = DoubleArray(3) { (a[4 + it] + (a[7 + it] - a[4 + it]) * ta).toDouble() } }
        }
        return best
    }

    private fun box() { overlay.box = RectF(minOf(p0x, p1x), minOf(p0y, p1y), maxOf(p0x, p1x), maxOf(p0y, p1y)); overlay.invalidate() }
    private fun commitBox() {
        val r = overlay.box ?: return
        val armed = app.boxSelect; app.boxSelect = null
        if (armed == "zoom") {
            // Std_ViewBoxZoom: centre on the box and zoom so it fills the view
            val (rr, uu) = icam.basis(); val k = icam.unitsPerPixel()
            val dx = r.centerX() - width / 2f; val dy = r.centerY() - height / 2f
            for (i in 0..2) { val d = (rr[i] * dx - uu[i] * dy) * k; if (i == 0) icam.tx += d else if (i == 1) icam.ty += d else icam.tz += d }
            icam.dist = (icam.dist * maxOf(r.width() / width, r.height() / height)).coerceIn(20f, 5000f)
            overlay.box = null; mode = Mode.NONE; refresh(); return
        }
        if (armed == "faces") {
            // Part_BoxSelection (PartGui::BoxSelection, TopAbs_FACE): faces whose tessellation lies inside the box
            for (o in renderer.objs) { val t = o.mesh.tris; val inside = HashMap<Int, Boolean>(); var i = 0
                while (i + 6 < t.size) { val f = t[i + 6].toInt(); val q = icam.project(t[i], t[i + 1], t[i + 2])
                    inside[f] = (inside[f] ?: true) && q != null && r.contains(q[0], q[1]); i += 7 }
                inside.filterValues { it }.keys.sorted().forEach { f -> val ref = SelRef(o.name, "Face$f"); if (ref !in app.selection) app.selection.add(ref) } }
            overlay.box = null; mode = Mode.NONE; sync(); return
        }
        renderer.objs.filter { o -> val c = icam.project((o.min[0] + o.max[0]) / 2, (o.min[1] + o.max[1]) / 2, (o.min[2] + o.max[2]) / 2); c != null && r.contains(c[0], c[1]) }
            .forEach { o -> if (app.selection.none { it.obj == o.name && it.sub.isEmpty() }) app.selection.add(SelRef(o.name)) }
        overlay.box = null; mode = Mode.NONE; sync()
    }

    // ------------------------------------------------------------------ pointer hover (mouse; MobileCAD XR's controller rays)
    /** A pointer over the view without pressing, as on desktop: preselection, the sketch tool's preview point and the Draft
     *  snapper follow it. */
    fun hoverAt(x: Float, y: Float) {
        val sk = sketch; val t = app.draft
        when {
            sk != null -> { if (sk.tool.points != 0) planePt(x, y)?.let { sk.hover(sk.snap(it, tolMm(18f))) } }
            t != null -> draftSnap(x, y)
            else -> { val r = pick(x, y, 8 * dp); renderer.hover = r?.let { it.obj to it.sub }; hoverPoint = r?.point }
        }
        refresh()
    }
    fun hoverEnd() { renderer.hover = null; hoverPoint = null; if (mode == Mode.NONE) sketch?.hover(null); app.draft?.hover = null; refresh() }
    private var hoverPoint: FloatArray? = null

    override fun onHoverEvent(e: MotionEvent): Boolean {
        if (xr) return false
        when (e.actionMasked) { MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> hoverAt(e.x, e.y); MotionEvent.ACTION_HOVER_EXIT -> hoverEnd() }
        return true
    }

    // ------------------------------------------------------------------ MobileCAD XR: the model in the room is the view
    /** MobileCAD XR shows the model in 3D around the user: this view then draws nothing, and controller rays come in through
     *  [xrPointer], running the same touch handling as a finger (picking, every sketch tool, Draft points, drag arrows). */
    var xr = false
        set(v) { field = v; val vis = if (v) View.GONE else View.VISIBLE; gl.visibility = vis; overlay.visibility = vis; navi.visibility = vis }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (app.xr && !xr) xr = true }
    private val fingerOffset get() = if (xr) 0f else 50 * dp
    /** The virtual camera's image: 20 dp per degree (12 dp, the pick radius, is 0.6°: about what it is on a phone held at
     *  arm's length), the same angles at any distance. */
    private val xrPx get() = (900 * dp).toInt()
    private var xrPressed: Camera? = null; private var xrDownT = 0L

    /** A camera at [o] looking along [d] (model coordinates, mm), its target [depth] mm away. */
    private fun rayCam(o: FloatArray, d: FloatArray, depth: Float) = Camera().apply {
        orthographic = false; w = xrPx; h = xrPx; dist = depth.coerceIn(1f, 1e7f)
        tx = o[0] + d[0] * dist; ty = o[1] + d[1] * dist; tz = o[2] + d[2] * dist
        pitch = Math.asin((-d[2]).toDouble().coerceIn(-1.0, 1.0)).toFloat(); yaw = Math.atan2(-d[1].toDouble(), -d[0].toDouble()).toFloat()
    }

    /** A controller ray (origin [o], unit direction [d], model mm; [depth]: distance to what it points at). action: 0 press,
     *  1 drag, 2 release, 3 hover, 4 leave, 5 cancel (the touch is called off: a second hand joined in). A press is a touch at the centre of a camera looking down the ray; while
     *  pressed, the ray is followed on that camera's image, so drags keep their meaning (slop, rubber bands, arrows). */
    fun xrPointer(action: Int, o: FloatArray, d: FloatArray, depth: Float) {
        val c = xrPx / 2f
        fun touch(a: Int, x: Float, y: Float, cm: Camera) {
            val now = SystemClock.uptimeMillis(); if (a == MotionEvent.ACTION_DOWN) xrDownT = now
            val e = MotionEvent.obtain(xrDownT, now, a, x, y, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            icam = cm; try { onTouchEvent(e) } finally { icam = cam; e.recycle() }
        }
        when (action) {
            0 -> { hoverEnd(); val cm = rayCam(o, d, depth); xrPressed = cm; touch(MotionEvent.ACTION_DOWN, c, c, cm)
                xrArrowOff = if (mode == Mode.MANIPULATOR) app.manipulator?.let { m -> rayOnArrow(m, o, d)?.minus(manipStartValue) } ?: Double.NaN else Double.NaN }
            1 -> if (mode == Mode.MANIPULATOR && !xrArrowOff.isNaN()) {
                // a drag arrow held by the ray: its tip goes where the ray passes the arrow's line, as if held in the hand
                val m = app.manipulator ?: return; val s = rayOnArrow(m, o, d) ?: return
                app.manipDrag(m, maxOf(0.01, s - xrArrowOff)); overlayChanged()
            } else xrPointer(-1, o, d, depth)
            -1, 2 -> { val cm = xrPressed ?: return
                val q = cm.project(o[0] + d[0] * cm.dist, o[1] + d[1] * cm.dist, o[2] + d[2] * cm.dist) ?: floatArrayOf(c, c)
                touch(if (action == -1) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_UP, q[0], q[1], cm)
                if (action == 2) xrPressed = null }
            3 -> if (xrPressed == null) { icam = rayCam(o, d, depth); try { hoverAt(c, c) } finally { icam = cam } }
            4 -> if (xrPressed == null) hoverEnd()
            5 -> { val cm = xrPressed ?: return; touch(MotionEvent.ACTION_CANCEL, c, c, cm); xrPressed = null }
        }
    }
    private var xrArrowOff = Double.NaN
    /** Where the ray (model mm) passes closest to the arrow's line, as the arrow's value there (along `dir` from `base`);
     *  null when the ray runs along the line. */
    private fun rayOnArrow(m: Manipulator, o: FloatArray, d: FloatArray): Double? {
        val u = m.dir; val w = FloatArray(3) { m.base[it] - o[it] }
        fun dot(a: FloatArray, b: FloatArray) = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).toDouble()
        val a = dot(u, u); val b = dot(u, d); val c = dot(d, d); val den = a * c - b * b
        if (den < 1e-6 * a * c) return null
        return (b * dot(d, w) - c * dot(u, w)) / den
    }
    /** The context menu (a two-finger tap on a touch screen), centred on the view. */
    fun xrContextMenu() = onContextMenu(width / 2f, height / 2f)

    /** What the view draws over the model, as 3D primitives in model coordinates (mm): lines (x1 y1 z1 x2 y2 z2 r g b),
     *  dots (x y z r g b sizeDp) and text labels. The sketch being edited is separate: [xrDrawSketch]. */
    class XrOverlay(val lines: FloatArray, val dots: FloatArray, val labels: List<Pair<FloatArray, String>>, val hover: Pair<String, String>?, val dim: Boolean)
    fun xrOverlay(): XrOverlay {
        val ln = ArrayList<Float>(); val dt = ArrayList<Float>(); val lb = ArrayList<Pair<FloatArray, String>>()
        fun rgb(c: Int) = floatArrayOf(((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f)
        fun line(a: FloatArray, b: FloatArray, c: Int) { ln.addAll(a.take(3)); ln.addAll(b.take(3)); ln.addAll(rgb(c).toList()) }
        fun dot(p: FloatArray, c: Int, size: Float) { dt.addAll(p.take(3)); dt.addAll(rgb(c).toList()); dt.add(size) }
        fun f(d: DoubleArray) = floatArrayOf(d[0].toFloat(), d[1].toFloat(), d[2].toFloat())
        val ring = 0x1651b0; val fill = 0x90cef1; val ann = if (OS.dark) 0xC8D0D8 else 0x2B3640
        for ((name, an) in app.annotations) { val (seg, texts) = an; var i = 0
            while (i + 5 < seg.size) { line(floatArrayOf(seg[i], seg[i + 1], seg[i + 2]), floatArrayOf(seg[i + 3], seg[i + 4], seg[i + 5]), ann); i += 6 }
            for ((p, txt) in texts) lb.add(p to txt) }
        app.draft?.let { t ->
            val all = t.pts.toList() + listOfNotNull(t.hover)
            for (k in 1 until all.size) line(f(all[k - 1]), f(all[k]), ring)
            for (q in t.pts.toList()) dot(f(q), fill, 10f)
            t.hover?.let { h -> dot(f(h), ring, 14f); t.snapKind?.let { k -> lb.add(f(h) to k) } }
        }
        for (r in app.selection) if (r.sub.startsWith("Vertex")) r.point?.let { dot(it, 0x418FDE, 12f) }
        val hv = renderer.hover
        if (hv != null && hv.second.startsWith("Vertex")) hoverPoint?.let { dot(it, 0x90cef1, 12f) }
        app.manipulator?.let { m -> val v = m.value.toFloat()
            val tip = floatArrayOf(m.base[0] + m.dir[0] * v, m.base[1] + m.dir[1] * v, m.base[2] + m.dir[2] * v)
            line(m.base, tip, ring); dot(tip, fill, 26f); lb.add(tip to "%.2f mm".format(m.value)) }
        return XrOverlay(ln.toFloatArray(), dt.toFloatArray(), lb, hv, sketch != null)
    }

    /** The sketch's extent on its plane (sketch mm: x0 y0 x1 y1): geometry, the shape being drawn, the origin. */
    fun xrSketchBounds(): DoubleArray? {
        val sk = sketch ?: return null
        // what is drawn, not the hover preview (pointing at the plane at a low angle puts it far away); an empty sketch: its origin
        val pts = (sk.geos.filter { !it.external }.flatMap { g -> g.polyline() + g.points().map { it.second } } + sk.pendingPoints()).ifEmpty { listOf(P2(0.0, 0.0)) }
        return doubleArrayOf(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    /** Draws the sketch being edited with its own editor (geometry, the shape in progress, snap feedback) into [bmp]: a view
     *  straight onto the sketch plane covering sketch coordinates [x0, y0] .. + [size]. [dpPx] is the size of a dp in bitmap
     *  pixels. Its text labels (constraint glyphs, dimensions, sizes while drawing, snap hints) go to [labels] sized for
     *  [labelDp], with their points in model coordinates, to be shown facing the user. Returns the bitmap's corners in model
     *  coordinates (top left, top right, bottom right, bottom left), or null when no sketch is open. */
    fun xrDrawSketch(bmp: android.graphics.Bitmap, x0: Double, y0: Double, size: Double, dpPx: Float,
                     labels: MutableList<Pair<DoubleArray, app.mobilecad.sketch.SketchEditor.Label>>, labelDp: Float): FloatArray? {
        val sk = sketch ?: return null
        val m = sk.m; val n = bmp.width
        val c = Canvas(bmp); c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val ls = ArrayList<app.mobilecad.sketch.SketchEditor.Label>()
        sk.draw(c, dpPx, { w -> val dx = w[0] - m[3]; val dy = w[1] - m[7]; val dz = w[2] - m[11]
            val u = dx * m[0] + dy * m[4] + dz * m[8]; val v = dx * m[1] + dy * m[5] + dz * m[9]
            floatArrayOf(((u - x0) / size * n).toFloat(), ((y0 + size - v) / size * n).toFloat()) }, ls, labelDp)
        ls.forEach { labels.add(sk.toWorld(it.at) to it) }
        return listOf(P2(x0, y0 + size), P2(x0 + size, y0 + size), P2(x0 + size, y0), P2(x0, y0)).flatMap { p -> sk.toWorld(p).map { it.toFloat() } }.toFloatArray()
    }

    // ------------------------------------------------------------------ overlay
    private inner class Overlay(c: Context) : View(c) {
        // own density: the outer `dp` is not initialised yet when this view is constructed
        private val dp = c.resources.displayMetrics.density
        var pointer: FloatArray? = null; var box: RectF? = null
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp; color = Color.parseColor("#1651b0") }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5590cef1") }
        private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#1651b0") }
        private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#90cef1") }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEFFFFFF") }
        private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B2733"); textSize = 13 * dp }
        private val annLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B3640"); strokeWidth = 1.5f * dp }
        private val annText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B3640"); textSize = 14 * dp }
        override fun onDraw(c: Canvas) {
            val dk = OS.dark
            labelBg.color = if (dk) 0xEE22272D.toInt() else 0xEEFFFFFF.toInt(); labelText.color = if (dk) 0xFFE3E9EF.toInt() else 0xFF1B2733.toInt()
            annLine.color = if (dk) 0xFFC8D0D8.toInt() else 0xFF2B3640.toInt(); annText.color = annLine.color
            sketch?.draw(c, dp, { w -> cam.project(w[0].toFloat(), w[1].toFloat(), w[2].toFloat()) })
            pointer?.let { p ->
                c.drawLine(p[0], p[1], p[2], p[3], ring); c.drawCircle(p[2], p[3], 12 * dp, fill); c.drawCircle(p[2], p[3], 12 * dp, ring)
                c.drawLine(p[2] - 18 * dp, p[3], p[2] + 18 * dp, p[3], ring); c.drawLine(p[2], p[3] - 18 * dp, p[2], p[3] + 18 * dp, ring)
            }
            box?.let { c.drawRect(it, fill); c.drawRect(it, ring) }
            // Draft annotations (texts, dimensions, labels: view-provider drawings on desktop)
            for ((name, an) in app.annotations) { val (seg, texts) = an; var i = 0
                while (i + 5 < seg.size) { val a = cam.project(seg[i], seg[i + 1], seg[i + 2]); val b = cam.project(seg[i + 3], seg[i + 4], seg[i + 5])
                    if (a != null && b != null) c.drawLine(a[0], a[1], b[0], b[1], annLine); i += 6 }
                // model-sized text (FontSize / units per pixel); too small to read -> not drawn, capped so it never floods the view
                val px = (app.annotationSize[name] ?: 3.5f) / cam.unitsPerPixel()
                if (px >= 7 * dp) { annText.textSize = minOf(px, 28 * dp)
                    for ((p, txt) in texts) cam.project(p[0], p[1], p[2])?.let { q -> txt.lines().forEachIndexed { k, l -> c.drawText(l, q[0], q[1] - 4 * dp + k * annText.textSize * 1.2f, annText) } } }
            }
            // Draft tool: picked points, rubber band to the hover point, snap marker
            app.draft?.let { t ->
                val all = t.pts.toList() + listOfNotNull(t.hover)
                val sp = all.mapNotNull { w -> cam.project(w[0].toFloat(), w[1].toFloat(), w[2].toFloat()) }
                for (k in 1 until sp.size) c.drawLine(sp[k - 1][0], sp[k - 1][1], sp[k][0], sp[k][1], ring)
                for (q in sp.take(t.pts.size)) c.drawCircle(q[0], q[1], 5 * dp, arrowFill)
                t.hover?.let { w -> cam.project(w[0].toFloat(), w[1].toFloat(), w[2].toFloat())?.let { q ->
                    c.drawCircle(q[0], q[1], 8 * dp, ring)
                    t.snapKind?.let { k -> c.drawText(k, q[0] + 12 * dp, q[1] - 10 * dp, labelText) } } }
            }
            // selected / hovered vertexes (the GL renderer highlights faces and edges)
            for (r in app.selection) if (r.sub.startsWith("Vertex")) r.point?.let { w -> cam.project(w[0], w[1], w[2])?.let { q ->
                c.drawCircle(q[0], q[1], 6 * dp, arrowFill); c.drawCircle(q[0], q[1], 6 * dp, ring) } }
            // manipulator arrow + value
            app.manipulator?.let { m ->
                val a = cam.project(m.base[0], m.base[1], m.base[2]); val t = manipTip(cam)
                if (a != null && t != null) {
                    arrow.strokeWidth = 3 * dp; c.drawLine(a[0], a[1], t[0], t[1], arrow)
                    c.drawCircle(t[0], t[1], 13 * dp, arrowFill); c.drawCircle(t[0], t[1], 13 * dp, arrow)
                    val label = "%.2f mm".format(m.value); val w = label.length * 7 * dp
                    c.drawRoundRect(t[0] + 18 * dp, t[1] - 13 * dp, t[0] + 26 * dp + w, t[1] + 11 * dp, 4 * dp, 4 * dp, labelBg)
                    c.drawText(label, t[0] + 22 * dp, t[1] + 4 * dp, labelText)
                }
            }
        }
    }
}
