package app.mobilecad.viewport

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import app.mobilecad.freecad.FcMesh
import app.mobilecad.freecad.FcPart
import java.util.IdentityHashMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * One FreeCAD object in the view: its mesh (shared with every object showing the same shape, uploaded to the GPU once)
 * at its placement.  Triangles are grouped by face (FreeCAD Face<i>), edge segments by edge (Edge<i>), so a single
 * face/edge can be highlighted by drawing its range again.
 */
class RenderObj(val name: String, val part: FcPart, val isSketch: Boolean, val ghostOf: String? = null) {
    /** The object picks refer to: a failed feature's ghost is the shape of [ghostOf], so its faces/edges are that object's. */
    val ref get() = ghostOf ?: name
    val mesh: FcMesh get() = part.mesh
    val min get() = part.min
    val max get() = part.max
    val model: FloatArray = part.matrix ?: FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    /** Its size on screen in the frame being drawn (Renderer). */
    var px = Float.MAX_VALUE
    /** Bounding sphere (centre, radius) in world coordinates; radius < 0: unknown (always drawn). */
    val sphere: FloatArray = part.min.let { a -> val b = part.max
        if (a[0] > b[0]) floatArrayOf(0f, 0f, 0f, -1f)
        else floatArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2,
            0.5f * kotlin.math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]))) }
}

class Renderer(private val cam: Camera) : GLSurfaceView.Renderer {
    @Volatile var objs: List<RenderObj> = emptyList()
    @Volatile var selectedObjs: Set<String> = emptySet()
    @Volatile var selectedFaces: Set<Pair<String, Int>> = emptySet()
    @Volatile var selectedEdges: Set<Pair<String, Int>> = emptySet()
    @Volatile var hover: Pair<String, String>? = null
    @Volatile var dim = false
    @Volatile var showGrid = true
    /** Dark theme: dark background, light wires / sketches / grid (FreeCAD's dark style sheets do the same). */
    @Volatile var dark = false
    /** Section clip plane (a,b,c,d): fragments with a*x+b*y+c*z+d > 0 are discarded (Sketcher_ViewSection). */
    @Volatile var clip: FloatArray? = null
    /** Std_DrawStyle: "As Is", "Points", "Wireframe", "Hidden Line", "No Shading", "Shaded", "Flat Lines". */
    @Volatile var drawStyle = "As Is"
    /** Per-object display colour (Std_RandomColor / Std_SetAppearance) and transparency (Std_ToggleTransparency). */
    @Volatile var colors: Map<String, Int> = emptyMap()
    @Volatile var faceColors: Map<Pair<String, Int>, Int> = emptyMap()
    /** Std_TextureMapping: object -> image, box-projected on its faces (desktop: SoTexture2 + texture coordinates). */
    @Volatile var textures: Map<String, android.graphics.Bitmap> = emptyMap()
    /** View background: 0 plain white, 1 linear gradient (top -> bottom), 2 radial gradient (FreeCAD View preferences). */
    @Volatile var background = 0
    private val texIds = HashMap<android.graphics.Bitmap, Int>()
    private var uTex = 0; private var uUseTex = 0; private var uTexBox = 0
    @Volatile var transparent: Set<String> = emptySet()
    /** Std_AxisCross: long axes through the origin instead of the small origin triad. */
    @Volatile var axisCross = false
    /** Std_SelBoundingBox: bounding boxes of selected objects as (min, max). */
    @Volatile var boxes: List<Pair<FloatArray, FloatArray>> = emptyList()
    /** One-shot frame grab (Std_ViewScreenShot), delivered on the GL thread. */
    @Volatile var capture: ((android.graphics.Bitmap) -> Unit)? = null
    private var vw = 1; private var vh = 1
    private var uClip = 0          // sketch mode: dim the model like FreeCAD does
    private var aCol = 0; private var uVcol = 0
    private var prog = 0; private var aPos = 0; private var aNrm = 0; private var uMvp = 0; private var uColor = 0; private var uLit = 0
    private var uModel = 0
    /** Wide lines as far as the GPU draws them (some draw nothing for a width outside their range) */
    private var maxLine = 1f
    private fun lineWidth(w: Float) = GLES20.glLineWidth(w.coerceIn(1f, maxLine))
    private val ident = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    /** Asks for another frame (the meshes not yet uploaded are drawn then). */
    @Volatile var requestRender: () -> Unit = {}

    /** GPU buffers of one mesh: vertices, triangle indices, edge points, colours (0 = none). */
    private class Gpu(val vbo: Int, val ibo: Int, val lbo: Int, val cbo: Int, val bytes: Long) { var pbo = 0 }
    private val gpu = IdentityHashMap<FcMesh, Gpu>()
    private var gpuObjs: List<RenderObj>? = null
    /** Meshes are uploaded up to this many bytes per frame, so a large model appears progressively instead of freezing the view. */
    private val uploadPerFrame = 48L shl 20
    private var uploaded = 0L

    private fun gpuOf(m: FcMesh): Gpu? {
        gpu[m]?.let { return it }
        if (uploaded >= uploadPerFrame) return null
        val ids = IntArray(5); GLES20.glGenBuffers(5, ids, 0)
        fun up(target: Int, id: Int, b: java.nio.Buffer, bytes: Int): Int { if (bytes == 0) return 0
            b.position(0); GLES20.glBindBuffer(target, id); GLES20.glBufferData(target, bytes, b, GLES20.GL_STATIC_DRAW); return id }
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) { }
        fun sec(i: Int) = m.bytesOf(i) ?: ByteBuffer.allocateDirect(0)
        val g = Gpu(up(GLES20.GL_ARRAY_BUFFER, ids[0], sec(0), m.vtx.capacity() * 4), up(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[1], sec(1), m.idx.capacity() * 4),
            up(GLES20.GL_ARRAY_BUFFER, ids[2], sec(2), m.lines.capacity() * 4), m.colors?.let { up(GLES20.GL_ARRAY_BUFFER, ids[3], sec(4), it.capacity() * 4) } ?: 0, m.bytes)
        g.pbo = up(GLES20.GL_ARRAY_BUFFER, ids[4], sec(3), m.points.capacity() * 4)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        if (GLES20.glGetError() == GLES20.GL_OUT_OF_MEMORY) { GLES20.glDeleteBuffers(5, ids, 0); outOfGpu = true; uploaded = uploadPerFrame; return null }
        for (k in 0..4) if (ids[k] != g.vbo && ids[k] != g.ibo && ids[k] != g.lbo && ids[k] != g.cbo && ids[k] != g.pbo) GLES20.glDeleteBuffers(1, ids, k)
        uploaded += m.bytes; gpu[m] = g
        return g
    }
    /** Buffer objects for the small per-frame geometry (grid, axes, boxes, background). Client-side arrays are not used at
     *  all: mixed with buffer objects, some GL implementations (the emulator's) draw from a stale client array. */
    private val scratch = IntArray(3)
    private fun attrib(loc: Int, size: Int, stride: Int, b: java.nio.Buffer, slot: Int) {
        if (scratch[slot] == 0) GLES20.glGenBuffers(1, scratch, slot)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, scratch[slot]); b.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, b.capacity() * (if (b is ByteBuffer) 1 else 4), b, GLES20.GL_STREAM_DRAW)
        GLES20.glVertexAttribPointer(loc, size, GLES20.GL_FLOAT, false, stride, 0); GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }
    /** Drops the GPU copies of meshes no longer shown. */
    private fun gpuPrune(os: List<RenderObj>) {
        if (os === gpuObjs) return; gpuObjs = os; outOfGpu = false
        val used = java.util.Collections.newSetFromMap(IdentityHashMap<FcMesh, Boolean>()).apply { os.forEach { add(it.mesh) } }
        val it = gpu.entries.iterator()
        while (it.hasNext()) { val (m, g) = it.next(); if (m !in used) { GLES20.glDeleteBuffers(5, intArrayOf(g.vbo, g.ibo, g.lbo, g.cbo, g.pbo), 0); it.remove() } }
    }
    /** The GPU's memory ran out: the rest of the model is not drawn. */
    @Volatile var outOfGpu = false

    /** View frustum planes (a b c d, inside: a x + b y + c z + d >= 0) of [vp]. */
    private fun frustum(vp: FloatArray): Array<FloatArray> = Array(6) { i -> val r = i / 2; val sg = if (i % 2 == 0) 1f else -1f
        FloatArray(4) { c -> vp[c * 4 + 3] + sg * vp[c * 4 + r] } }
    private fun visible(o: RenderObj, pl: Array<FloatArray>): Boolean {
        val a = o.min; val b = o.max; if (a[0] > b[0]) return true
        for (p in pl) if (p[0] * (if (p[0] >= 0) b[0] else a[0]) + p[1] * (if (p[1] >= 0) b[1] else a[1]) + p[2] * (if (p[2] >= 0) b[2] else a[2]) + p[3] < 0) return false
        return true
    }

    private val vs = """uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPos; attribute vec3 aNrm; attribute vec3 aCol; varying vec3 vN; varying vec3 vP; varying vec3 vC;
        void main(){ vN = (uModel * vec4(aNrm,0.0)).xyz; vP = (uModel * vec4(aPos,1.0)).xyz; vC = aCol; gl_PointSize = 7.0; gl_Position = uMvp * vec4(vP,1.0); }"""
    private val fs = """precision highp float; uniform vec4 uColor; uniform float uLit; uniform vec4 uClip; uniform float uVcol; uniform float uUseTex; uniform sampler2D uTex; uniform vec4 uTexBox; varying vec3 vN; varying vec3 vP; varying vec3 vC;
        void main(){ if (dot(uClip.xyz, vP) + uClip.w > 0.001) discard; vec3 n = normalize(vN); if (!gl_FrontFacing) n = -n;
          float l = 0.35 + 0.45*max(dot(n, normalize(vec3(0.4,-0.6,0.8))),0.0) + 0.2*max(dot(n, normalize(vec3(-0.5,0.4,0.3))),0.0);
          vec3 base = mix(uColor.rgb, vC, uVcol);
          if (uUseTex > 0.5) { vec3 a = abs(n); vec3 q = (vP - uTexBox.xyz) / uTexBox.w;
            vec2 uv = a.z >= a.x && a.z >= a.y ? q.xy : (a.x >= a.y ? q.yz : q.xz); base = texture2D(uTex, vec2(uv.x, 1.0 - uv.y)).rgb; }
          gl_FragColor = vec4(base * mix(1.0, l, uLit), uColor.a); }"""

    private fun sh(t: Int, src: String) = GLES20.glCreateShader(t).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1f, 1f, 1f, 1f); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        prog = GLES20.glCreateProgram().also { GLES20.glAttachShader(it, sh(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(it, sh(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(it) }
        aPos = GLES20.glGetAttribLocation(prog, "aPos"); aNrm = GLES20.glGetAttribLocation(prog, "aNrm")
        uMvp = GLES20.glGetUniformLocation(prog, "uMvp"); uModel = GLES20.glGetUniformLocation(prog, "uModel"); uColor = GLES20.glGetUniformLocation(prog, "uColor"); uLit = GLES20.glGetUniformLocation(prog, "uLit"); uClip = GLES20.glGetUniformLocation(prog, "uClip")
        aCol = GLES20.glGetAttribLocation(prog, "aCol"); uVcol = GLES20.glGetUniformLocation(prog, "uVcol")
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uUseTex = GLES20.glGetUniformLocation(prog, "uUseTex"); uTexBox = GLES20.glGetUniformLocation(prog, "uTexBox")
        val lr = FloatArray(2); GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, lr, 0); maxLine = maxOf(1f, lr[1])
        texIds.clear(); gpu.clear(); gpuObjs = null; scratch.fill(0)      // a new context: the old one's buffers are gone
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h); cam.w = w; cam.h = h; vw = w; vh = h }
    private val colBuf = FloatArray(4)
    private fun col(c: Int, a: Float = 1f) = colBuf.also { it[0] = Color.red(c) / 255f; it[1] = Color.green(c) / 255f; it[2] = Color.blue(c) / 255f; it[3] = a }

    // While the view moves (a gesture, an animation), parts smaller on screen than a few pixels and the edges of parts
    // smaller than a thumb are left out, so a model of thousands of parts still turns smoothly; once the view rests
    // for a moment, it is drawn again in full.
    private val lastVp = FloatArray(16); private var lastMove = 0L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val settle = Runnable { requestRender() }
    /** Radius on screen, in pixels, of [o]'s bounding sphere (infinite when unknown or around the eye). */
    private fun pixels(o: RenderObj, vp: FloatArray, sy: Float): Float {
        val c = o.sphere; if (c[3] < 0f) return Float.MAX_VALUE
        val w = vp[3] * c[0] + vp[7] * c[1] + vp[11] * c[2] + vp[15]
        return if (w <= 1e-6f) Float.MAX_VALUE else c[3] * sy / w * vh / 2
    }

    override fun onDrawFrame(gl: GL10?) {
        if (dark) GLES20.glClearColor(0.094f, 0.106f, 0.122f, 1f) else GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog); GLES20.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        if (background != 0) drawBackground()
        val vp = cam.viewProj()
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glUniform4f(uClip, 0f, 0f, 0f, -1f)
        if (showGrid) drawGrid()
        clip?.let { GLES20.glUniform4f(uClip, it[0], it[1], it[2], it[3]) }
        val os = objs; val selO = selectedObjs; val selF = selectedFaces; val selE = selectedEdges; val hov = hover
        val style = drawStyle; val cols = colors; val transp = transparent; val fcs = faceColors
        gpuPrune(os); uploaded = 0L
        // objects with a face / edge drawn again (selected, preselected, coloured): only those walk their faces
        val faceMarked = HashSet<String>().apply { selF.forEach { add(it.first) }; hov?.let { add(it.first) }; fcs.keys.forEach { add(it.first) } }
        val edgeMarked = HashSet<String>().apply { selE.forEach { add(it.first) }; hov?.let { add(it.first) } }
        val now = android.os.SystemClock.uptimeMillis()
        if (!vp.contentEquals(lastVp)) { vp.copyInto(lastVp); lastMove = now }
        val moving = now - lastMove < 200 && os.size > 200
        if (moving) { main.removeCallbacks(settle); main.postDelayed(settle, 220) }
        val sy = kotlin.math.sqrt(vp[1] * vp[1] + vp[5] * vp[5] + vp[9] * vp[9])
        val minFace = if (moving) 2f else 0.5f; val minEdge = if (moving) 40f else 2f
        val pl = frustum(vp)
        val shown = os.filter { visible(it, pl) && pixels(it, vp, sy).also { p -> it.px = p }.let { p -> p >= minFace || it.name in selO } }
        fun edgesOf(o: RenderObj) = o.isSketch || o.name in selO || o.ref in edgeMarked || o.px >= minEdge
        var pending = false
        val faces = style != "Wireframe" && style != "Points"
        val edgesOn = style != "Shaded" && style != "Points"
        val lit = if (style == "No Shading" || style == "Flat Lines" || style == "Hidden Line") 0f else 1f
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glEnableVertexAttribArray(aNrm)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        if (faces) for (o in shown.sortedBy { it.name in transp || it.ghostOf != null }) {      // opaque first, then transparent
            val m = o.mesh; if (!m.hasTris) continue
            val g = gpuOf(m); if (g == null) { pending = true; continue }
            GLES20.glUniformMatrix4fv(uModel, 1, false, o.model, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.vbo)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0); GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, 24, 12)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, g.ibo)
            val a = if (dim) 0.35f else if (o.ghostOf != null) 0.45f else if (o.name in transp) 0.5f else 1f
            GLES20.glDepthMask(a == 1f)
            // result colourings (FEM results / pipelines, inspection) lie on the part they were computed on: drawn a
            // little nearer than plain faces so they win instead of z-fighting, still behind edges
            GLES20.glPolygonOffset(if (g.cbo != 0) 1f else 2f, if (g.cbo != 0) 2f else 8f); GLES20.glUniform1f(uLit, lit)
            val base = if (o.ghostOf != null) 0xE0483A else if (style == "Hidden Line") (if (dark) 0x181B1F else 0xFFFFFF) else cols[o.name] ?: 0xCFD6DC
            GLES20.glUniform4fv(uColor, 1, col(if (o.name in selO) 0x9CC6EF else base, a), 0)
            val vc = g.cbo != 0 && o.name !in selO
            val tex = textures[o.name]?.takeIf { o.name !in selO && style != "Hidden Line" }
            if (tex != null) {
                val id = texIds.getOrPut(tex) { val t = IntArray(1); GLES20.glGenTextures(1, t, 0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, tex, 0); t[0] }
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id); GLES20.glUniform1i(uTex, 0)
                val size = maxOf(o.max[0] - o.min[0], o.max[1] - o.min[1], o.max[2] - o.min[2], 1e-6f)
                GLES20.glUniform4f(uTexBox, o.min[0], o.min[1], o.min[2], size); GLES20.glUniform1f(uUseTex, 1f)
            }
            if (vc) { GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.cbo); GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 12, 0); GLES20.glUniform1f(uVcol, 1f) }
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.idxCount, GLES20.GL_UNSIGNED_INT, 0)
            if (vc) { GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f) }
            if (tex != null) GLES20.glUniform1f(uUseTex, 0f)
            GLES20.glPolygonOffset(0f, 0f)
            if (o.ref in faceMarked || o.name in faceMarked) for ((id, rs) in m.faceRanges) {
                val c = when { (o.ref to id) in selF -> 0x418FDE; hov == o.ref to "Face$id" -> 0xC3DCF5
                    style != "Hidden Line" && o.name !in selO -> fcs[o.name to id] ?: continue; else -> continue }
                GLES20.glUniform4fv(uColor, 1, col(c, a), 0)
                for (r in rs) GLES20.glDrawElements(GLES20.GL_TRIANGLES, r[1], GLES20.GL_UNSIGNED_INT, r[0] * 4)
            }
            GLES20.glDepthMask(true)
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDisableVertexAttribArray(aNrm); GLES20.glVertexAttrib3f(aNrm, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL); GLES20.glUniform1f(uLit, 0f)
        for (o in shown) {
            val m = o.mesh; if (!m.hasEdges || (m.hasTris && !edgesOf(o))) continue
            val g = gpuOf(m); if (g == null) { pending = true; continue }
            GLES20.glUniformMatrix4fv(uModel, 1, false, o.model, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.lbo); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
            // edges of solids stay dark on the light-grey faces; wires on their own need contrast with the background
            val ec = when { o.ghostOf != null -> 0xB0281C; o.isSketch -> if (dark) 0x6FA8FF else 0x2A6FC9; dark && !m.hasTris -> 0xD0D7DE; else -> 0x2B3640 }
            if (edgesOn || o.isSketch) { lineWidth(if (o.isSketch) 2.5f else 1.5f); GLES20.glUniform4fv(uColor, 1, col(ec), 0); GLES20.glDrawArrays(GLES20.GL_LINES, 0, m.lineCount) }
            if (o.ref in edgeMarked) for ((id, rs) in m.edgeRanges) {
                val c = when { (o.ref to id) in selE -> 0x418FDE; hov == o.ref to "Edge$id" -> 0x66C7C9; else -> continue }
                lineWidth(4f); GLES20.glUniform4fv(uColor, 1, col(c), 0)
                for (r in rs) GLES20.glDrawArrays(GLES20.GL_LINES, r[0], r[1])
            }
            lineWidth(1f)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        for (o in shown) { val m = o.mesh
            if (!(style == "Points" || (!m.hasTris && !m.hasEdges)) || m.pointCount == 0) continue  // point clouds always
            GLES20.glUniformMatrix4fv(uModel, 1, false, o.model, 0)
            val g = gpuOf(m) ?: run { pending = true; null } ?: continue
            val vc = g.cbo != 0 && !m.hasTris && o.name !in selO
            if (vc) { GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.cbo); GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 12, 0); GLES20.glUniform1f(uVcol, 1f) }
            GLES20.glUniform4fv(uColor, 1, col(if (o.name in selO) 0x418FDE else cols[o.name] ?: if (dark) 0xD0D7DE else 0x2B3640), 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.pbo); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 16, 0); GLES20.glDrawArrays(GLES20.GL_POINTS, 0, m.pointCount)
            if (vc) { GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f) }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        }
        GLES20.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        if (pending) requestRender()
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUniform4f(uClip, 0f, 0f, 0f, -1f)
        for ((mn, mx) in boxes) drawBox(mn, mx)
        drawAxes()
        capture?.let { cb -> capture = null; cb(grab()) }
    }

    /** Full-screen gradient behind the scene (vertex colours, no depth). */
    private fun drawBackground() {
        val top = if (dark) floatArrayOf(0.20f, 0.25f, 0.32f) else floatArrayOf(0.60f, 0.70f, 0.82f)
        val bot = if (dark) floatArrayOf(0.07f, 0.08f, 0.10f) else floatArrayOf(0.96f, 0.97f, 0.99f)
        val pts = mutableListOf<Float>(); val cs = mutableListOf<Float>()
        fun v(x: Float, y: Float, c: FloatArray) { pts += listOf(x, y, 0f); cs += c.toList() }
        if (background == 1) { v(-1f, -1f, bot); v(1f, -1f, bot); v(1f, 1f, top); v(-1f, -1f, bot); v(1f, 1f, top); v(-1f, 1f, top) }
        else { val n = 48; for (i in 0 until n) { val a0 = (2 * Math.PI * i / n).toFloat(); val a1 = (2 * Math.PI * (i + 1) / n).toFloat()
            v(0f, 0f, bot); v(1.5f * kotlin.math.cos(a0), 1.5f * kotlin.math.sin(a0), top); v(1.5f * kotlin.math.cos(a1), 1.5f * kotlin.math.sin(a1), top) } }
        val pb = ByteBuffer.allocateDirect(pts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(pts.toFloatArray()); position(0) }
        val cb = ByteBuffer.allocateDirect(cs.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(cs.toFloatArray()); position(0) }
        val id = FloatArray(16); android.opengl.Matrix.setIdentityM(id, 0); GLES20.glUniformMatrix4fv(uMvp, 1, false, id, 0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glUniform1f(uLit, 0f); GLES20.glUniform1f(uVcol, 1f); GLES20.glUniform4f(uClip, 0f, 0f, 0f, -1f)
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 1f)
        GLES20.glEnableVertexAttribArray(aPos); GLES20.glEnableVertexAttribArray(aCol)
        attrib(aPos, 3, 12, pb, 0); attrib(aCol, 3, 12, cb, 1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, pts.size / 3)
        GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun lines(pts: FloatArray, color: Int, width: Float, mode: Int = GLES20.GL_LINES) {
        if (pts.isEmpty()) return
        val b = ByteBuffer.allocateDirect(pts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(pts); position(0) }
        lineWidth(width); GLES20.glUniform4fv(uColor, 1, col(color), 0)
        attrib(aPos, 3, 12, b, 0); GLES20.glDrawArrays(mode, 0, pts.size / 3)
        lineWidth(1f)
    }

    private fun drawBox(a: FloatArray, b: FloatArray) {
        val c = listOf(floatArrayOf(a[0], a[1], a[2]), floatArrayOf(b[0], a[1], a[2]), floatArrayOf(b[0], b[1], a[2]), floatArrayOf(a[0], b[1], a[2]),
            floatArrayOf(a[0], a[1], b[2]), floatArrayOf(b[0], a[1], b[2]), floatArrayOf(b[0], b[1], b[2]), floatArrayOf(a[0], b[1], b[2]))
        val idx = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)
        lines(FloatArray(idx.size * 3) { c[idx[it / 3]][it % 3] }, 0xE0403F, 1.5f)
    }

    private fun grab(): android.graphics.Bitmap {
        val buf = ByteBuffer.allocateDirect(vw * vh * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, vw, vh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val bmp = android.graphics.Bitmap.createBitmap(vw, vh, android.graphics.Bitmap.Config.ARGB_8888); bmp.copyPixelsFromBuffer(buf)
        val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
        return android.graphics.Bitmap.createBitmap(bmp, 0, 0, vw, vh, flip, false)
    }

    private val grid: FloatBuffer by lazy {
        val l = ArrayList<Float>(); val n = 100; val s = 10f
        for (i in -n..n) l.addAll(listOf(i * s, -n * s, 0f, i * s, n * s, 0f, -n * s, i * s, 0f, n * s, i * s, 0f))
        ByteBuffer.allocateDirect(l.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(l.toFloatArray()); position(0) }
    }
    /** Power of ten that follows the zoom (1 at the default view): grid cell and origin axes scale by it. */
    private fun zoomStep() = Math.pow(10.0, Math.round(Math.log10(cam.dist / 320.0)).toDouble()).toFloat()
    private fun drawGrid() {
        GLES20.glDisableVertexAttribArray(aNrm); GLES20.glVertexAttrib3f(aNrm, 0f, 0f, 1f); GLES20.glUniform1f(uLit, 0f)
        GLES20.glUniform4fv(uColor, 1, col(if (dark) 0x2A3138 else 0xE4E8EB), 0); grid.position(0)
        // the cell follows the zoom in powers of ten (10 mm at the default view, 1 m for a building) and the grid stays
        // under the view target, so it never ends inside the view
        val k = zoomStep(); val step = 100f * k
        val mm = FloatArray(16); android.opengl.Matrix.setIdentityM(mm, 0)
        android.opengl.Matrix.translateM(mm, 0, Math.round(cam.tx / step) * step, Math.round(cam.ty / step) * step, 0f)
        android.opengl.Matrix.scaleM(mm, 0, k, k, k)
        val mvp = FloatArray(16); android.opengl.Matrix.multiplyMM(mvp, 0, cam.viewProj(), 0, mm, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        // the grid never occludes geometry: draw it without writing depth
        GLES20.glDepthMask(false)
        attrib(aPos, 3, 12, grid, 0); GLES20.glDrawArrays(GLES20.GL_LINES, 0, grid.capacity() / 3)
        GLES20.glDepthMask(true)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, cam.viewProj(), 0)
    }
    private fun drawAxes() {
        val b = ByteBuffer.allocateDirect(18 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        lineWidth(2.5f)
        val l = if (axisCross) cam.dist * 2f else 30f * zoomStep(); val s0 = if (axisCross) -l else 0f
        for ((v, c) in listOf(floatArrayOf(1f, 0f, 0f) to 0xE0403F, floatArrayOf(0f, 1f, 0f) to 0x47AB72, floatArrayOf(0f, 0f, 1f) to 0x418FDE)) {
            b.position(0); b.put(floatArrayOf(v[0] * s0, v[1] * s0, v[2] * s0, v[0] * l, v[1] * l, v[2] * l)); b.position(0)
            GLES20.glUniform4fv(uColor, 1, col(c), 0); attrib(aPos, 3, 12, b, 0); GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }
        lineWidth(1f)
    }
}
