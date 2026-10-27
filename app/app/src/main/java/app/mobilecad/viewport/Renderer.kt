package app.mobilecad.viewport

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import app.mobilecad.freecad.FcMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GPU copy of one FreeCAD object's tessellation. Vertices are grouped by face (FreeCAD Face<i>), segments by edge (Edge<i>),
 * so a single face/edge can be highlighted by drawing its range again.
 */
class RenderObj(val name: String, val mesh: FcMesh, val isSketch: Boolean, val ghostOf: String? = null) {
    /** The object picks refer to: a failed feature's ghost is the shape of [ghostOf], so its faces/edges are that object's. */
    val ref get() = ghostOf ?: name
    val tris: FloatBuffer = buf(mesh.tris); val edges: FloatBuffer = buf(mesh.edges)
    val colors: FloatBuffer? = if (mesh.colors.isNotEmpty()) buf(mesh.colors) else null
    val faceRanges = ranges(mesh.tris, 3)      // face id -> (first vertex, vertex count)
    val edgeRanges = ranges(mesh.edges, 2)     // edge id -> (first vertex, vertex count)
    val min = FloatArray(3) { Float.MAX_VALUE }; val max = FloatArray(3) { -Float.MAX_VALUE }
    init {
        for (arr in listOf(mesh.tris, mesh.edges)) { var i = 0
            while (i + 6 < arr.size) { for (k in 0..2) { val v = arr[i + k]; if (v.isFinite()) { min[k] = minOf(min[k], v); max[k] = maxOf(max[k], v) } }; i += 7 } }
        var j = 0; val vs = mesh.verts          // point clouds have only vertexes [x y z id]
        while (j + 3 < vs.size) { for (k in 0..2) { val v = vs[j + k]; if (v.isFinite()) { min[k] = minOf(min[k], v); max[k] = maxOf(max[k], v) } }; j += 4 }
    }
    private fun buf(a: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(maxOf(4, a.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
    /** Tri vertices: x y z nx ny nz id (stride 7, id per vertex). Edge vertices are stored as 2-point segments x1 y1 z1 x2 y2 z2 id. */
    private fun ranges(a: FloatArray, perPrim: Int): Map<Int, Pair<Int, Int>> {
        val out = LinkedHashMap<Int, Pair<Int, Int>>(); var i = 0; var v = 0
        if (perPrim == 3) while (i + 6 < a.size) { val id = a[i + 6].toInt(); val r = out[id]; out[id] = if (r == null) v to 1 else r.first to r.second + 1; i += 7; v++ }
        else while (i + 6 < a.size) { val id = a[i + 6].toInt(); val r = out[id]; out[id] = if (r == null) v to 1 else r.first to r.second + 1; i += 7; v++ }
        return out
    }
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
    private var edgeBuf: FloatBuffer? = null

    private val vs = """uniform mat4 uMvp; attribute vec3 aPos; attribute vec3 aNrm; attribute vec3 aCol; varying vec3 vN; varying vec3 vP; varying vec3 vC;
        void main(){ vN = aNrm; vP = aPos; vC = aCol; gl_PointSize = 7.0; gl_Position = uMvp * vec4(aPos,1.0); }"""
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
        uMvp = GLES20.glGetUniformLocation(prog, "uMvp"); uColor = GLES20.glGetUniformLocation(prog, "uColor"); uLit = GLES20.glGetUniformLocation(prog, "uLit"); uClip = GLES20.glGetUniformLocation(prog, "uClip")
        aCol = GLES20.glGetAttribLocation(prog, "aCol"); uVcol = GLES20.glGetUniformLocation(prog, "uVcol")
        uTex = GLES20.glGetUniformLocation(prog, "uTex"); uUseTex = GLES20.glGetUniformLocation(prog, "uUseTex"); uTexBox = GLES20.glGetUniformLocation(prog, "uTexBox")
        texIds.clear()
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h); cam.w = w; cam.h = h; vw = w; vh = h }
    private fun col(c: Int, a: Float = 1f) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f, a)

    override fun onDrawFrame(gl: GL10?) {
        if (dark) GLES20.glClearColor(0.094f, 0.106f, 0.122f, 1f) else GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        if (background != 0) drawBackground()
        GLES20.glUniformMatrix4fv(uMvp, 1, false, cam.viewProj(), 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glUniform4f(uClip, 0f, 0f, 0f, -1f)
        if (showGrid) drawGrid()
        clip?.let { GLES20.glUniform4f(uClip, it[0], it[1], it[2], it[3]) }
        val os = objs; val selO = selectedObjs; val selF = selectedFaces; val selE = selectedEdges; val hov = hover
        val style = drawStyle; val cols = colors; val transp = transparent; val fcs = faceColors
        val faces = style != "Wireframe" && style != "Points"
        val edgesOn = style != "Shaded" && style != "Points"
        val lit = if (style == "No Shading" || style == "Flat Lines" || style == "Hidden Line") 0f else 1f
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glEnableVertexAttribArray(aNrm)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        if (faces) for (o in os.sortedBy { it.name in transp || it.ghostOf != null }) {      // opaque first, then transparent
            if (o.mesh.tris.isEmpty()) continue
            bindTris(o)
            val a = if (dim) 0.35f else if (o.ghostOf != null) 0.45f else if (o.name in transp) 0.5f else 1f
            GLES20.glDepthMask(a == 1f)
            // result colourings (FEM results / pipelines, inspection) lie on the part they were computed on: drawn a
            // little nearer than plain faces so they win instead of z-fighting, still behind edges
            GLES20.glPolygonOffset(if (o.colors != null) 1f else 2f, if (o.colors != null) 2f else 8f); GLES20.glUniform1f(uLit, lit)
            val base = if (o.ghostOf != null) 0xE0483A else if (style == "Hidden Line") (if (dark) 0x181B1F else 0xFFFFFF) else cols[o.name] ?: 0xCFD6DC
            GLES20.glUniform4fv(uColor, 1, col(if (o.name in selO) 0x9CC6EF else base, a), 0)
            val vc = o.colors
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
            if (vc != null && o.name !in selO) { vc.position(0); GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 12, vc); GLES20.glUniform1f(uVcol, 1f) }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, o.mesh.tris.size / 7)
            if (vc != null) { GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f) }
            if (tex != null) GLES20.glUniform1f(uUseTex, 0f)
            GLES20.glPolygonOffset(0f, 0f)
            for ((id, r) in o.faceRanges) {
                val c = when { (o.ref to id) in selF -> 0x418FDE; hov == o.ref to "Face$id" -> 0xC3DCF5
                    style != "Hidden Line" && o.name !in selO -> fcs[o.name to id] ?: continue; else -> continue }
                GLES20.glUniform4fv(uColor, 1, col(c, a), 0); GLES20.glDrawArrays(GLES20.GL_TRIANGLES, r.first, r.second)
            }
            GLES20.glDepthMask(true)
        }
        GLES20.glDisableVertexAttribArray(aNrm); GLES20.glVertexAttrib3f(aNrm, 0f, 0f, 1f)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL); GLES20.glUniform1f(uLit, 0f)
        for (o in os) {
            if (o.mesh.edges.isEmpty()) continue
            // edges of solids stay dark on the light-grey faces; wires on their own need contrast with the background
            val ec = when { o.ghostOf != null -> 0xB0281C; o.isSketch -> if (dark) 0x6FA8FF else 0x2A6FC9; dark && o.mesh.tris.isEmpty() -> 0xD0D7DE; else -> 0x2B3640 }
            if (edgesOn || o.isSketch) drawSegments(o, null, ec, if (o.isSketch) 2.5f else 1.5f)
            for ((id, _) in o.edgeRanges) {
                val c = when { (o.ref to id) in selE -> 0x418FDE; hov == o.ref to "Edge$id" -> 0x66C7C9; else -> continue }
                drawSegments(o, id, c, 4f)
            }
        }
        for (o in os) if (style == "Points" || (o.mesh.tris.isEmpty() && o.mesh.edges.isEmpty())) {  // point clouds always
            val vc = o.colors?.takeIf { o.mesh.tris.isEmpty() && o.name !in selO }
            if (vc != null) { vc.position(0); GLES20.glEnableVertexAttribArray(aCol); GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 12, vc); GLES20.glUniform1f(uVcol, 1f) }
            drawPoints(o.mesh.verts, if (o.name in selO) 0x418FDE else cols[o.name] ?: if (dark) 0xD0D7DE else 0x2B3640)
            if (vc != null) { GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f) }
        }
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
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, pb); GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, 12, cb)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, pts.size / 3)
        GLES20.glDisableVertexAttribArray(aCol); GLES20.glUniform1f(uVcol, 0f); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun lines(pts: FloatArray, color: Int, width: Float, mode: Int = GLES20.GL_LINES) {
        if (pts.isEmpty()) return
        val b = ByteBuffer.allocateDirect(pts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(pts); position(0) }
        GLES20.glLineWidth(width); GLES20.glUniform4fv(uColor, 1, col(color), 0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glDrawArrays(mode, 0, pts.size / 3)
        GLES20.glLineWidth(1f)
    }

    /** Vertex records are [x y z id]. */
    private fun drawPoints(v: FloatArray, color: Int) =
        lines(FloatArray(v.size / 4 * 3) { v[it / 3 * 4 + it % 3] }, color, 1f, GLES20.GL_POINTS)

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

    private fun bindTris(o: RenderObj) {
        o.tris.position(0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 28, o.tris)
        o.tris.position(3); GLES20.glVertexAttribPointer(aNrm, 3, GLES20.GL_FLOAT, false, 28, o.tris)
    }

    /** Expands [x1 y1 z1 x2 y2 z2 id] records into a temporary GL_LINES buffer (only for the requested edge, or all). */
    private fun drawSegments(o: RenderObj, edge: Int?, color: Int, width: Float) {
        val a = o.mesh.edges; val out = ArrayList<Float>(); var i = 0
        while (i + 6 < a.size) { if (edge == null || a[i + 6].toInt() == edge) { for (k in 0..5) out.add(a[i + k]) }; i += 7 }
        if (out.isEmpty()) return
        val b = ByteBuffer.allocateDirect(out.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(out.toFloatArray()); position(0) }
        GLES20.glLineWidth(width); GLES20.glUniform4fv(uColor, 1, col(color), 0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glDrawArrays(GLES20.GL_LINES, 0, out.size / 3)
        GLES20.glLineWidth(1f)
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
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, grid); GLES20.glDrawArrays(GLES20.GL_LINES, 0, grid.capacity() / 3)
        GLES20.glDepthMask(true)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, cam.viewProj(), 0)
    }
    private fun drawAxes() {
        val b = ByteBuffer.allocateDirect(18 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        GLES20.glLineWidth(2.5f)
        val l = if (axisCross) cam.dist * 2f else 30f * zoomStep(); val s0 = if (axisCross) -l else 0f
        for ((v, c) in listOf(floatArrayOf(1f, 0f, 0f) to 0xE0403F, floatArrayOf(0f, 1f, 0f) to 0x47AB72, floatArrayOf(0f, 0f, 1f) to 0x418FDE)) {
            b.position(0); b.put(floatArrayOf(v[0] * s0, v[1] * s0, v[2] * s0, v[0] * l, v[1] * l, v[2] * l)); b.position(0)
            GLES20.glUniform4fv(uColor, 1, col(c), 0); GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, b); GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }
        GLES20.glLineWidth(1f)
    }
}
