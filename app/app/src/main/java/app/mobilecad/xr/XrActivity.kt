package app.mobilecad.xr

import android.app.Presentation
import app.mobilecad.i18n.Language
import app.mobilecad.i18n.tr
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.mobilecad.freecad.AppState
import app.mobilecad.freecad.SelRef
import app.mobilecad.ui.OS
import app.mobilecad.ui.Shell
import app.mobilecad.ui.XrLayout
import kotlinx.coroutines.launch
import org.qtproject.qt.android.QtHostAccess
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * MobileCAD XR: the whole MobileCAD app, in a headset. MobileCAD's interface (every workbench, feature dialogs, the
 * number pad, the feature tree) runs on a see-through virtual display in its XR layout, cut into surfaces around the
 * user (tree left, bars ahead, docked panels right) that you use with the controller ray as a touch screen. The model stands in the room and is MobileCAD's 3D view: the controller ray goes through that
 * view's own touch handling (picking, every sketch tool, Draft points, drag arrows), and what the view draws (the sketch
 * being edited, preselection, Draft feedback, annotations) is drawn on the model in 3D. Your hands move, turn, scale it.
 *
 * Without an OpenXR runtime (phone, emulator) the same scene is shown as a touch preview, so everything can be tested
 * without a headset.
 */
class XrActivity : ComponentActivity() {
    private lateinit var app: AppState
    private var panelTexture: SurfaceTexture? = null
    @Volatile private var panelFrame = false
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var touchDown = 0L
    private var lastDoc = ""

    private external fun nativeAttach()
    private external fun nativeSetLayout(w: Int, h: Int, pxPerDp: Float, rects: FloatArray)
    private external fun nativeStartXr()
    private external fun nativeStop()
    private external fun nativeBeginScene(title: String)
    private external fun nativeAddMesh(name: String, tris: FloatArray, edges: FloatArray, r: Float, g: Float, b: Float, alpha: Float)
    private external fun nativeSetView(hoverObj: String, hoverSub: String, fade: Float, lines: FloatArray, dots: FloatArray, labelPos: FloatArray, labelText: Array<String>)
    private external fun nativeSetSketch(pixels: java.nio.ByteBuffer?, w: Int, h: Int, corners: FloatArray)
    private external fun nativeSetSketchLabels(pixels: java.nio.ByteBuffer?, w: Int, h: Int, labels: FloatArray)
    private external fun nativeModelScale(): Float
    private external fun nativeSetPopups(rects: FloatArray)
    private external fun nativeSetModel(name: String, glb: ByteArray)
    private external fun nativeEndScene(place: Boolean)
    private external fun nativeSetSelection(objs: Array<String>, subs: Array<String>)
    private external fun nativePreviewInit()
    private external fun nativePreviewTouch(action: Int, x: Float, y: Float)
    private external fun nativePreviewDraw(w: Int, h: Int)
    private external fun nativePreviewLook(yaw: Float, pitch: Float, fov: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        QtHostAccess.attach(this)                       // FreeCAD's QtCore needs a host activity (see MobileCAD)
        Language.init(this)                             // the interface language, as in MobileCAD
        app = AppState(this)
        app.xr = true; app.xrChanged = { viewChanged() }
        app.start()
        app.whenReady { openFirstExample() }
        setContentView(TextView(this).apply {
            setBackgroundColor(Color.rgb(24, 27, 31)); setTextColor(Color.WHITE); textSize = 20f; gravity = android.view.Gravity.CENTER
            text = tr("MobileCAD XR\nPut on your headset")
        })
        nativeAttach()
        // the hand and Quest 3 controller models (WebXR input profiles, MIT: assets/models)
        for (m in listOf("hand-left", "hand-right", "touch-plus-left", "touch-plus-right"))
            runCatching { nativeSetModel(m, assets.open("models/$m.glb").use { it.readBytes() }) }
        layoutPanel()
        nativeStartXr()
        syncScene()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("preview_look")?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 3 }?.let { nativePreviewLook(it[0], it[1], it[2]) }
    }
    override fun onStop() { super.onStop(); app.autosave() }
    /** Back from another activity (a file picker): draw the interface afresh, so the headset gets its image (and where
     *  it can be clicked) again even when nothing in it changes. */
    override fun onStart() {
        super.onStart()
        panelWindows().forEach { w -> w.invalidate(); w.requestLayout() }
        if (!popupsPosted) { popupsPosted = true; window.decorView.post { popupsPosted = false; sendPopups() } }
    }
    override fun onDestroy() { nativeStop(); presentation?.dismiss(); display?.release(); super.onDestroy() }

    /** FreeCAD's example projects come with the engine (share/examples); the first one opens so there is something to
     *  look at. A copy, so saving it does not write into the engine's files. */
    private fun openFirstExample() {
        val dir = File(filesDir, "examples").apply { mkdirs() }
        for (n in listOf("PartDesignExample.FCStd")) {
            val f = File(dir, n); val src = File(filesDir, "freecad/share/examples/$n")
            if (!f.exists() && src.exists()) runCatching { src.copyTo(f) }
        }
        if (app.objects.isEmpty()) File(dir, "PartDesignExample.FCStd").takeIf { it.exists() }?.let { app.open(it.absolutePath) }
    }

    // ---------------------------------------------------------------- the 3D model follows the document
    private fun syncScene() = lifecycleScope.launch {
        // a document change is followed by the refresh that loads its shapes: the model is placed / sized on that one
        var newDocAt = -1
        snapshotFlow { Triple(app.sceneVersion.intValue, app.docName, app.selection.toList()) }.collect { (ver, doc, sel) ->
            if (doc != lastDoc) { newDocAt = ver; lastDoc = doc }
            val meshes = app.meshes
            nativeBeginScene(doc)
            for ((name, m) in meshes) {
                if (m.tris.isEmpty() && m.edges.isEmpty()) continue
                // as MobileCAD's view draws them: a failed feature's last good shape red and see-through, transparent objects
                val ghost = app.ghosts.containsKey(name)
                val c = if (ghost) 0xE0403F else app.colors[name] ?: 0xCFD6DC
                val alpha = if (ghost) 0.45f else if (name in app.transparent) 0.5f else 1f
                nativeAddMesh(name, m.tris, m.edges, ((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, alpha)
            }
            val place = newDocAt >= 0 && ver > newDocAt
            nativeEndScene(place)
            if (place) newDocAt = -1
            nativeSetSelection(sel.map { it.obj }.toTypedArray(), sel.map { it.sub }.toTypedArray())
            viewChanged()
        }
    }

    // ---------------------------------------------------------------- MobileCAD's 3D view, in the room
    /** Controller rays for the view (render thread): press / drag / release keep their order, consecutive drags and hovers
     *  collapse to the newest, so the UI thread never falls behind the headset's frame rate. */
    private val rays = ArrayDeque<FloatArray>()
    private var raysPosted = false
    @Suppress("unused") fun onXrViewport(action: Int, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, depth: Float) {
        val ev = floatArrayOf(action.toFloat(), ox, oy, oz, dx, dy, dz, depth)
        synchronized(rays) {
            val last = rays.lastOrNull()
            if (last != null && last[0] == ev[0] && (action == 1 || action == 3)) rays[rays.size - 1] = ev else rays.addLast(ev)
            if (raysPosted) return; raysPosted = true
        }
        runOnUiThread {
            val evs = synchronized(rays) { raysPosted = false; rays.toList().also { rays.clear() } }
            val v = app.viewport ?: return@runOnUiThread
            for (e in evs) {
                val a = e[0].toInt()
                // with a menu or dialog open, a press on the model is a touch outside it (it closes, as on a touch screen)
                if (a == 0) modal()?.let { w -> outsideTouch(w); swallow = true }
                if (swallow) { if (a == 2 || a == 5) swallow = false; if (a <= 2 || a == 5) continue }
                v.xrPointer(a, floatArrayOf(e[1], e[2], e[3]), floatArrayOf(e[4], e[5], e[6]), e[7])
            }
        }
    }
    private var swallow = false
    @Suppress("unused") fun onXrContextMenu() { runOnUiThread { app.viewport?.xrContextMenu() } }

    private var viewPosted = false
    private fun viewChanged() { if (viewPosted) return; viewPosted = true; window.decorView.postOnAnimation { viewPosted = false; sendView() } }

    private var skBmp: android.graphics.Bitmap? = null
    private var skBuf: java.nio.ByteBuffer? = null
    private var skRegion: DoubleArray? = null; private var skName = ""; private var skScale = 0f; private var skShown = false
    /** Sends what the view draws: its overlay as 3D primitives, and the sketch being edited drawn by its own editor onto a
     *  texture on the sketch plane, covering a stable region around the sketch at a resolution fit for its size in the room. */
    private fun sendView() {
        val v = app.viewport ?: return
        val o = v.xrOverlay()
        nativeSetView(o.hover?.first ?: "", o.hover?.second ?: "", if (o.dim) 0.35f else 1f, o.lines, o.dots,
            o.labels.flatMap { it.first.toList() }.toFloatArray(), o.labels.map { it.second }.toTypedArray())
        val sk = app.sketch; val b = v.xrSketchBounds()
        if (sk == null || b == null) { if (skShown) { nativeSetSketch(null, 0, 0, FloatArray(12)); nativeSetSketchLabels(null, 0, 0, FloatArray(0)); skShown = false; skRegion = null }; return }
        val n = 1536; val scale = nativeModelScale().takeIf { it > 0f } ?: 0.001f        // m per mm
        // A region of the sketch plane around the sketch, at least 30 cm across in the room. A dp is 1 mm in the room (a
        // phone's dp is ~0.03 degrees of view; legible in a headset takes ~3x that), at least a pixel: up to 1.5 m across
        // that holds; bigger sketches get coarser (up to 2 m, beyond which the region follows where you work). It stays while what is drawn fits and it is not much too big, so it does not jump.
        val minSize = 0.3 / scale; val maxSize = 2.0 / scale
        val ext = maxOf(b[2] - b[0], b[3] - b[1]); val want = (ext * 1.4).coerceIn(minSize, maxSize)
        val focus = sk.preview?.p ?: sk.pendingPoints().lastOrNull()
        val cx = if (ext * 1.4 > maxSize && focus != null) focus.x else (b[0] + b[2]) / 2
        val cy = if (ext * 1.4 > maxSize && focus != null) focus.y else (b[1] + b[3]) / 2
        var r = skRegion
        val fits = r != null && minOf(b[2], cx + want / 2) <= r[0] + r[2] && maxOf(b[0], cx - want / 2) >= r[0] &&
            minOf(b[3], cy + want / 2) <= r[1] + r[2] && maxOf(b[1], cy - want / 2) >= r[1]
        if (r == null || !fits || r[2] > want * 2 || sk.name != skName || kotlin.math.abs(scale / skScale - 1f) > 0.3f) {
            r = doubleArrayOf(cx - want / 2, cy - want / 2, want); skRegion = r; skName = sk.name; skScale = scale
        }
        val bmp = skBmp ?: android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888).also { skBmp = it }
        val metresPerPx = r[2] * scale / n
        val dpPx = maxOf(1f, (0.001 / metresPerPx).toFloat())                         // a dp is ~1 mm in the room
        val labels = ArrayList<Pair<DoubleArray, app.mobilecad.sketch.SketchEditor.Label>>()
        val corners = v.xrDrawSketch(bmp, r[0], r[1], r[2], dpPx, labels, LABEL_DP) ?: return
        val buf = skBuf ?: java.nio.ByteBuffer.allocateDirect(n * n * 4).also { skBuf = it }
        buf.rewind(); bmp.copyPixelsToBuffer(buf)
        nativeSetSketch(buf, n, n, corners); skShown = true
        sendLabels(labels)
    }

    private var lbBmp: android.graphics.Bitmap? = null
    private var lbBuf: java.nio.ByteBuffer? = null
    /** The sketch's text labels, each painted by the sketch editor into a cell of one texture (rows, tallest first), with
     *  its point (model mm), its box around the point (dp) and its cell (0..1): the headset shows them facing the user. */
    private fun sendLabels(labels: List<Pair<DoubleArray, app.mobilecad.sketch.SketchEditor.Label>>) {
        val n = 1024; val bmp = lbBmp ?: android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888).also { lbBmp = it }
        val c = android.graphics.Canvas(bmp); c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val out = ArrayList<Float>(); var x = 0f; var y = 0f; var rowH = 0f; val pad = 2f
        for ((at, l) in labels.sortedByDescending { it.second.b - it.second.t }) {
            val w = l.r - l.l + 2 * pad; val h = l.b - l.t + 2 * pad
            if (x + w > n) { x = 0f; y += rowH; rowH = 0f }
            if (y + h > n || w > n) break                                             // full: the rest is not shown
            l.paint(c, x + pad - l.l, y + pad - l.t)
            out.addAll(listOf(at[0].toFloat(), at[1].toFloat(), at[2].toFloat(),
                (l.l + l.r) / 2 / LABEL_DP, (l.t + l.b) / 2 / LABEL_DP, w / LABEL_DP, h / LABEL_DP, x / n, y / n, (x + w) / n, (y + h) / n))
            x += w; rowH = maxOf(rowH, h)
        }
        val buf = lbBuf ?: java.nio.ByteBuffer.allocateDirect(n * n * 4).also { lbBuf = it }
        buf.rewind(); bmp.copyPixelsToBuffer(buf)
        nativeSetSketchLabels(if (out.isEmpty()) null else buf, n, n, out.toFloatArray())
    }

    // ---------------------------------------------------------------- callbacks from the XR core (render thread)
    @Suppress("unused") fun onXrResult(ok: Boolean, message: String) {
        Log.i("mcxr", "OpenXR: ${if (ok) "running on $message" else "unavailable ($message)"}")
        if (!ok) runOnUiThread { showPreview(message) }
    }
    @Suppress("unused") fun onXrExit() { runOnUiThread { finish() } }
    /** A trigger click on the model: the face / edge goes into the document's selection, as a tap in MobileCAD's view. */
    @Suppress("unused") fun onXrPick(obj: String, sub: String, x: Float, y: Float, z: Float) {
        runOnUiThread { if (obj.isEmpty()) app.clearSelection() else app.select(SelRef(obj, sub, floatArrayOf(x, y, z))); app.viewport?.sync() }
    }

    // ---------------------------------------------------------------- the UI panel: MobileCAD on a virtual display
    /** MobileCAD lays its interface out for XR (XrLayout): the virtual display's size and the parts the headset shows as
     *  separate surfaces - the window with the whole interface and the keypad (number pad / keyboard). Rendered at 2.25 px
     *  per dp (360 dpi) and shown mipmapped: denser than the headset's pixels, so text stays crisp. */
    private fun layoutPanel() {
        val k = PX_PER_DP; val w = XrLayout.WIDTH * k; val h = XrLayout.HEIGHT * k
        nativeSetLayout(((XrLayout.WIDTH + XrLayout.PAD_W) * k).toInt(), h.toInt(), k,
            floatArrayOf(0f, 0f, w, h,  w, 0f, XrLayout.PAD_W * k, XrLayout.PAD_H * k))
    }

    /** Called on the GL thread with the panel's texture: MobileCAD's UI is rendered into it through a virtual display. */
    @Suppress("unused") fun createPanelSurface(tex: Int, w: Int, h: Int) {
        val st = SurfaceTexture(tex).apply { setDefaultBufferSize(w, h); setOnFrameAvailableListener { panelFrame = true } }
        panelTexture = st
        val surface = Surface(st)
        runOnUiThread {
            presentation?.dismiss(); display?.release()
            val dm = getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay("MobileCAD XR panel", w, h, (160 * PX_PER_DP).toInt(), surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            display = vd
            presentation = Presentation(this, vd.display).apply {
                // see-through where MobileCAD draws nothing: the model in the room shows between the surfaces
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                window?.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                val view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(this@XrActivity); setViewTreeSavedStateRegistryOwner(this@XrActivity)
                    setViewTreeViewModelStoreOwner(this@XrActivity)
                    setContent {
                        val dark = app.theme == "dark" || (app.theme == "system" && isSystemInDarkTheme())
                        OS.dark = dark
                        val scheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8AB4F8),
                            surface = androidx.compose.ui.graphics.Color(0xFF1E2227), surfaceContainer = androidx.compose.ui.graphics.Color(0xFF242930),
                            background = androidx.compose.ui.graphics.Color(0xFF181B1F))
                            else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF2A6FC9))
                        // file pickers / back handling inside MobileCAD's shell resolve to this activity
                        CompositionLocalProvider(LocalActivityResultRegistryOwner provides this@XrActivity,
                                                 LocalOnBackPressedDispatcherOwner provides this@XrActivity) {
                            MaterialTheme(colorScheme = scheme) { Shell(app) }
                        }
                    }
                }
                window?.decorView?.let { d -> d.setViewTreeLifecycleOwner(this@XrActivity); d.setViewTreeSavedStateRegistryOwner(this@XrActivity); d.setViewTreeViewModelStoreOwner(this@XrActivity) }
                setContentView(view, ViewGroup.LayoutParams(w, h))
                show()
            }
            watchTextFields()
        }
    }

    // ---------------------------------------------------------------- the keyboard (see XrKeyboard)
    private val keyboard = XrKeyboard()
    private var keyboardWindow: android.widget.PopupWindow? = null
    private var watching = false
    /** While a text field on the panel has focus, the keyboard is shown on the keypad's surface (a window that never takes the
     *  focus, so the field keeps it). */
    private fun watchTextFields() {
        if (watching) return; watching = true
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                val kb = keyboardWindow?.contentView?.rootView
                val ed = panelWindows().filter { it !== kb }.firstNotNullOfOrNull { w -> w.findFocus()?.takeIf { it.onCheckIsTextEditor() } }
                keyboard.attach(ed)
                if (ed != null && keyboardWindow?.isShowing != true) showKeyboard() else if (ed == null) keyboardWindow?.dismiss()
            }
        }
    }
    /** The window recomposer Compose installed on the interface's window (a view tag on its root or a parent). */
    private fun findCompositionContext(v: View): androidx.compose.runtime.CompositionContext? {
        v.compositionContext?.let { return it }
        if (v is ViewGroup) for (i in 0 until v.childCount) findCompositionContext(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun showKeyboard() {
        val decor = presentation?.window?.decorView ?: return
        val k = PX_PER_DP; val kw = 460; val kh = 220
        val win = keyboardWindow ?: android.widget.PopupWindow(ComposeView(decor.context).apply {
            // composed by the interface's recomposer (a popup window's root has no lifecycle owner of its own)
            setViewTreeLifecycleOwner(this@XrActivity); setViewTreeSavedStateRegistryOwner(this@XrActivity); setViewTreeViewModelStoreOwner(this@XrActivity)
            findCompositionContext(decor)?.let { setParentCompositionContext(it) }
            setContent { MaterialTheme { keyboard.Content(androidx.compose.ui.Modifier.fillMaxSize()) } }
        }, (kw * k).toInt(), (kh * k).toInt(), false).apply { isTouchable = true; isOutsideTouchable = false; inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED }
        keyboardWindow = win
        // on the keypad's surface, where the number pad shows too
        win.showAtLocation(decor, android.view.Gravity.NO_GRAVITY, ((XrLayout.WIDTH + (XrLayout.PAD_W - kw) / 2f) * k).toInt(), (10 * k).toInt())
    }

    /** Called on the GL thread every frame: latch the newest UI image into the panel texture. */
    @Suppress("unused") fun updatePanelTexture(): Boolean {
        if (!panelFrame) {
            // no new image: still look at the windows twice a second (one closed while nothing redrew stays cut out otherwise)
            val now = SystemClock.uptimeMillis()
            if (now - popupsChecked > 500 && !popupsPosted) { popupsChecked = now; popupsPosted = true; runOnUiThread { popupsPosted = false; sendPopups() } }
            return false
        }
        panelFrame = false; try { panelTexture?.updateTexImage() } catch (e: Exception) { Log.w("mcxr", "panel", e) }
        if (!popupsPosted) { popupsPosted = true; runOnUiThread { popupsPosted = false; sendPopups() } }
        return true
    }

    /** The controller ray on the panel, as touches (trigger) / mouse hover and wheel (pointing, thumbstick) on the UI. */
    @Suppress("unused") fun onXrPanelPointer(action: Int, x: Float, y: Float) {
        runOnUiThread {
            if (presentation?.window?.decorView == null) return@runOnUiThread
            val now = SystemClock.uptimeMillis()
            fun at(w: View): Pair<Float, Float> { val o = IntArray(2); w.getLocationOnScreen(o); return Pair(x - o[0], y - o[1]) }
            fun touch(a: Int) {
                if (a == MotionEvent.ACTION_DOWN) { touchDown = now; touchWindow = windowFor(x, y, true) }
                val w = touchWindow ?: return
                val (lx, ly) = at(w)
                val ev = MotionEvent.obtain(touchDown, now, a, lx, ly, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                w.dispatchTouchEvent(ev); ev.recycle()
                if (a == MotionEvent.ACTION_UP) touchWindow = null
            }
            fun mouse(a: Int, vscroll: Float = 0f) {
                val w = (if (a == MotionEvent.ACTION_HOVER_EXIT) hoverWindow else windowFor(x, y, false)) ?: return
                if (a == MotionEvent.ACTION_HOVER_MOVE && hoverWindow != null && hoverWindow != w) {
                    hoverWindow?.let { old -> val (ox, oy) = at(old); send(old, MotionEvent.ACTION_HOVER_EXIT, ox, oy, 0f, now) }
                }
                if (a != MotionEvent.ACTION_SCROLL) hoverWindow = if (a == MotionEvent.ACTION_HOVER_EXIT) null else w
                val (lx, ly) = at(w)
                send(w, a, lx, ly, vscroll, now)
            }
            when (action) {
                0 -> touch(MotionEvent.ACTION_DOWN)
                1 -> touch(MotionEvent.ACTION_MOVE)
                2 -> touch(MotionEvent.ACTION_UP)
                3 -> mouse(MotionEvent.ACTION_HOVER_MOVE)
                4 -> mouse(MotionEvent.ACTION_HOVER_EXIT)
                5 -> mouse(MotionEvent.ACTION_SCROLL, y)
            }
        }
    }

    @Volatile private var popupsPosted = false
    private var popupsChecked = 0L
    private var lastPopups = FloatArray(0)
    /** Where the menus, popups and dialogs over MobileCAD's window are: the headset shows each as a surface of its own. */
    private fun sendPopups() {
        val main = presentation?.window?.decorView ?: return
        val r = panelWindows().filter { it !== main }.flatMap { w -> val o = IntArray(2); w.getLocationOnScreen(o)
            listOf(o[0].toFloat(), o[1].toFloat(), w.width.toFloat(), w.height.toFloat()) }.toFloatArray()
        if (!r.contentEquals(lastPopups)) { lastPopups = r; nativeSetPopups(r) }
    }

    private var touchWindow: View? = null
    private var hoverWindow: View? = null

    private fun send(w: View, a: Int, x: Float, y: Float, vscroll: Float, now: Long) {
        val p = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val c = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; if (vscroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll) }
        val ev = MotionEvent.obtain(now, now, a, 1, arrayOf(p), arrayOf(c), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        w.dispatchGenericMotionEvent(ev); ev.recycle()
    }

    /** The windows on the panel's display, bottom to top: the Presentation plus the menus, popups and dialogs opened from
     *  it, which are windows of their own (a dispatch to the Presentation never reaches them). Same lookup as Espresso. */
    private fun panelWindows(): List<View> {
        val main = presentation?.window?.decorView ?: return emptyList()
        val id = main.display?.displayId ?: return listOf(main)
        return try {
            val c = Class.forName("android.view.WindowManagerGlobal")
            val f = c.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val all = (f.get(c.getMethod("getInstance").invoke(null)) as List<View>).toList()
            // only windows that are shown: one hidden or on its way out (a menu closed while a file picker was in front)
            // would otherwise take the touches meant for the interface
            all.filter { it.isAttachedToWindow && it.visibility == View.VISIBLE && it.windowVisibility == View.VISIBLE &&
                         it.width > 0 && it.height > 0 && it.display?.displayId == id }.ifEmpty { listOf(main) }
        } catch (e: Exception) { Log.w("mcxr", "window list", e); listOf(main) }
    }

    /** The window a pointer at (x, y) goes to: the topmost one under it, unless a focusable (modal) window lies above -
     *  then that one, which closes itself on a touch outside, as a menu or dialog does on a touch screen. */
    private fun windowFor(x: Float, y: Float, touch: Boolean): View? {
        val ws = panelWindows()
        for (w in ws.asReversed()) {
            val o = IntArray(2); w.getLocationOnScreen(o)
            if (x >= o[0] && y >= o[1] && x < o[0] + w.width && y < o[1] + w.height) return w
            val lp = w.layoutParams as? WindowManager.LayoutParams
            if (touch && w !== presentation?.window?.decorView && lp != null && lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) return w
        }
        return ws.firstOrNull()
    }

    /** The open menu / popup / dialog (a focusable window above MobileCAD's), if any. */
    private fun modal(): View? = panelWindows().lastOrNull { w -> w !== presentation?.window?.decorView &&
        (w.layoutParams as? WindowManager.LayoutParams)?.let { it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0 } == true }
    private fun outsideTouch(w: View) {
        val now = SystemClock.uptimeMillis()
        for (a in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val ev = MotionEvent.obtain(now, now, a, -10000f, -10000f, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            w.dispatchTouchEvent(ev); ev.recycle()
        }
    }

    // ---------------------------------------------------------------- no headset: touch preview of the XR scene
    private fun showPreview(reason: String) {
        val view = GLSurfaceView(this)
        view.setEGLContextClientVersion(3)
        view.setRenderer(object : GLSurfaceView.Renderer {
            var w = 1; var h = 1
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                nativePreviewInit()
                // debug: --es preview_look "yaw,pitch,fov" (radians) points the preview's eye (screenshots without two fingers)
                intent.getStringExtra("preview_look")?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 3 }?.let { nativePreviewLook(it[0], it[1], it[2]) }
            }
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { w = width; h = height }
            override fun onDrawFrame(gl: GL10?) { nativePreviewDraw(w, h) }
        })
        // one finger: the controller with its trigger pressed; two fingers: look around
        view.setOnTouchListener { _, e ->
            val cx = (0 until e.pointerCount).map { e.getX(it) }.average().toFloat(); val cy = (0 until e.pointerCount).map { e.getY(it) }.average().toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> nativePreviewTouch(0, e.x, e.y)
                MotionEvent.ACTION_POINTER_DOWN -> nativePreviewTouch(4, cx, cy)
                MotionEvent.ACTION_MOVE -> if (e.pointerCount >= 2) nativePreviewTouch(3, cx, cy) else nativePreviewTouch(1, e.x, e.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> nativePreviewTouch(2, e.x, e.y)
            }
            true
        }
        val note = TextView(this).apply {
            setTextColor(Color.rgb(200, 208, 216)); textSize = 13f; setPadding(28, 18, 28, 18); setBackgroundColor(Color.argb(140, 0, 0, 0))
            text = tr("No headset (%1): preview of the XR scene. One finger is the controller (touch the UI to use it, the model or the space around it to pick, sketch and place points); two fingers look around.", reason)
        }
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(note, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP))
        })
    }

    companion object {
        const val PX_PER_DP = 2.25f
        const val LABEL_DP = 3f                // sketch labels: texture pixels per dp
        init { System.loadLibrary("mcxr") }
    }
}

