package app.mobilecad.freecad

import android.content.Context
import android.util.Log
import app.mobilecad.i18n.tr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.Executors

class FcObject(val name: String, val label: String, val type: String, val visible: Boolean, val invalid: Boolean,
               val parents: List<String>, val group: List<String>, val tip: String?, val hasShape: Boolean,
               val assembly: String? = null, val linked: String? = null, val subtype: String? = null, val error: String? = null,
               /** what FreeCAD's tree shows under this object (ViewProvider::claimChildren) */
               val claims: List<String> = emptyList(),
               /** App::SuppressibleExtension's Suppressed (Std_ToggleSuppress) */
               val suppressed: Boolean = false,
               /** Part::AttachExtension: has an attachment to edit (ViewProviderAttachExtension's "Edit Attachment") */
               val attachable: Boolean = false,
               /** the active body (ActiveView.getActiveObject("pdbody")): bold in the tree */
               val active: Boolean = false,
               /** touched (State "Touched", e.g. Mark to recompute): the tree's recompute marker */
               val touched: Boolean = false)

class FcProperty(val name: String, val type: String, val group: String, val doc: String, val readonly: Boolean,
                 val value: Any?, val expression: String?, val enums: List<String>,
                 /** editor for non-scalar properties (engine _edit_form): "parts" (vector / placement components), "color",
                  *  "text", "file", "list", "edges" (Part fillet / chamfer); extra holds that editor's data. */
                 val kind: String? = null, val extra: org.json.JSONObject? = null)

/**
 * One shape's display mesh, in native memory (Android caps an app's Java heap at a few hundred MB whatever the device's
 * RAM; a large assembly's meshes take gigabytes).  Shared by every object showing the same shape (an assembly's repeated
 * parts), each with its own placement ([FcPart]).
 * vtx: x y z nx ny nz per vertex; idx: triangle indices; faces: (FreeCAD face id, first index, index count) triples;
 * lines: edge points x y z in GL_LINES pairs; edges: (edge id, first point, point count); points: vertexes x y z id;
 * colors: r g b per vertex (or per point of a cloud) for result colourings, else null.
 */
class FcMesh(val key: String, val vtx: FloatBuffer, val idx: IntBuffer, val faces: IntArray, val lines: FloatBuffer,
             val edges: IntArray, val points: FloatBuffer, val colors: FloatBuffer?,
             /** the same sections as bytes, for OpenGL (its Java binding misplaces float views of a sliced native buffer) */
             val raw: Array<ByteBuffer> = emptyArray()) {
    /** bytes of section [i] (0 vtx, 1 idx, 2 lines, 3 points, 4 colors) for glBufferData / glVertexAttribPointer */
    fun bytesOf(i: Int): ByteBuffer? = raw.getOrNull(i)?.also { it.position(0) }
    val vertCount get() = vtx.capacity() / 6
    val idxCount get() = idx.capacity()
    val lineCount get() = lines.capacity() / 3
    val pointCount get() = points.capacity() / 4
    val hasTris get() = idx.capacity() > 0
    val hasEdges get() = lines.capacity() > 0
    /** bytes it takes (in native memory; the view holds a GPU copy of the same size) */
    val bytes: Long get() = 4L * (vtx.capacity() + idx.capacity() + lines.capacity() + points.capacity() + (colors?.capacity() ?: 0))
    /** face id -> its index ranges (first, count), edge id -> its point ranges */
    val faceRanges: Map<Int, List<IntArray>> by lazy { ranges(faces) }
    val edgeRanges: Map<Int, List<IntArray>> by lazy { ranges(edges) }
    private fun ranges(t: IntArray): Map<Int, List<IntArray>> { val out = LinkedHashMap<Int, MutableList<IntArray>>()
        var i = 0; while (i + 2 < t.size) { out.getOrPut(t[i]) { ArrayList(1) }.add(intArrayOf(t[i + 1], t[i + 2])); i += 3 }; return out }
    /** bounds in the mesh's own coordinates */
    val min = FloatArray(3) { Float.MAX_VALUE }; val max = FloatArray(3) { -Float.MAX_VALUE }
    init {
        fun add(b: FloatBuffer, stride: Int) { var i = 0; val n = b.capacity()
            while (i + 2 < n) { for (k in 0..2) { val v = b.get(i + k); if (v.isFinite()) { if (v < min[k]) min[k] = v; if (v > max[k]) max[k] = v } }; i += stride } }
        add(vtx, 6); add(lines, 3); add(points, 4)
    }
}

/** An object's mesh at its placement ([matrix]: column major, world = matrix * mesh coordinates; null = identity). */
class FcPart(val mesh: FcMesh, val matrix: FloatArray?) {
    /** world bounds */
    val min = FloatArray(3) { Float.MAX_VALUE }; val max = FloatArray(3) { -Float.MAX_VALUE }
    init {
        val a = mesh.min; val b = mesh.max
        if ((0..2).all { a[it] <= b[it] }) { val p = FloatArray(3)
            for (c in 0 until 8) { world(if (c and 1 == 0) a[0] else b[0], if (c and 2 == 0) a[1] else b[1], if (c and 4 == 0) a[2] else b[2], p)
                for (k in 0..2) { min[k] = minOf(min[k], p[k]); max[k] = maxOf(max[k], p[k]) } } }
    }
    /** world coordinates of mesh point (x, y, z), into [out] */
    fun world(x: Float, y: Float, z: Float, out: FloatArray): FloatArray { val m = matrix
        if (m == null) { out[0] = x; out[1] = y; out[2] = z } else {
            out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]; out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]; out[2] = m[2] * x + m[6] * y + m[10] * z + m[14] }
        return out }
    /** the ray (ox oy oz dx dy dz, world) in mesh coordinates: distances along it are the same in both */
    fun local(r: FloatArray): FloatArray { val m = matrix ?: return r
        val inv = FloatArray(16); if (!android.opengl.Matrix.invertM(inv, 0, m, 0)) return r
        val o = floatArrayOf(r[0], r[1], r[2], 1f); val d = floatArrayOf(r[3], r[4], r[5], 0f); val lo = FloatArray(4); val ld = FloatArray(4)
        android.opengl.Matrix.multiplyMV(lo, 0, inv, 0, o, 0); android.opengl.Matrix.multiplyMV(ld, 0, inv, 0, d, 0)
        return floatArrayOf(lo[0], lo[1], lo[2], ld[0], ld[1], ld[2]) }
}

/** The document's undo / redo names (newest first) and its position in the history (bridge.undo_state: +1 per committed
 *  step, -1 per undo; unlike the list lengths it keeps counting when FreeCAD's 20-step limit drops old steps). */
data class History(val undo: List<String>, val redo: List<String>, val pos: Int)

class EngineException(msg: String) : Exception(msg)

/**
 * FreeCAD's App layer running in-process (embedded CPython + FreeCAD.so), driven through mobilecad.bridge.
 * Every call runs on one dedicated engine thread.
 */
class Engine private constructor(private val ctx: Context) {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "freecad-engine") }
    val dispatcher: CoroutineDispatcher = thread.asCoroutineDispatcher()
    @Volatile var ready = false; private set
    @Volatile var startupError: String? = null; private set

    private external fun nativeInit(pyHome: String, fcHome: String, userDir: String): String
    private external fun nativeCall(req: String): String
    private external fun nativeTessellate(name: String, tol: Double): ByteBuffer?
    private external fun nativeExec(src: String): String

    suspend fun start(progress: (String) -> Unit = {}) = withContext(dispatcher) {
        if (ready) return@withContext
        val root = ctx.filesDir
        val stamp = File(root, "runtime.version"); val want = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (!stamp.exists() || stamp.readText() != want) {
            progress(tr("Unpacking the CAD engine…"))
            File(root, "python").deleteRecursively(); File(root, "freecad").deleteRecursively()
            File(root, "lib").deleteRecursively(); extract("python", File(root, "python")); extract("freecad-runtime", File(root, "freecad"))
            stamp.writeText(want)
        }
        progress(tr("Starting the CAD engine…"))
        val user = File(root, "user").apply { mkdirs() }; File(user, "tmp").mkdirs()
        // programs shipped as native libraries (Gmsh: libgmsh.so) run from here; mobilecad.bridge points FreeCAD at them
        runCatching { android.system.Os.setenv("FC_NATIVE_LIB_DIR", ctx.applicationInfo.nativeLibraryDir, true) }
        System.loadLibrary("fcbridge")
        val err = nativeInit(File(root, "python").absolutePath, File(root, "freecad").absolutePath, user.absolutePath)
        if (err.isNotEmpty()) { startupError = err; Log.e("Engine", err); throw EngineException(err) }
        ready = true
    }

    private fun extract(asset: String, dest: File) {
        val names = ctx.assets.list(asset) ?: return
        if (names.isEmpty()) {   // a file
            dest.parentFile?.mkdirs()
            // gradle renames *.gz -> *.gz- so aapt keeps them; undo here (same convention as CPython's testbed)
            val out = if (dest.name.endsWith("-")) File(dest.parentFile, dest.name.dropLast(1)) else dest
            ctx.assets.open(asset).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        for (n in names) extract("$asset/$n", File(dest, n))
    }

    // ------------------------------------------------------------------ calls (must run on [dispatcher])
    fun call(op: String, args: Map<String, Any?> = emptyMap()): Any? {
        val req = JSONObject(); req.put("op", op); for ((k, v) in args) req.put(k, toJson(v))
        val res = JSONObject(nativeCall(req.toString()))
        if (!res.optBoolean("ok")) throw EngineException(res.optString("error") + "\n" + res.optString("trace"))
        return if (res.isNull("result")) null else res.get("result")
    }

    private fun toJson(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        is Array<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        else -> v
    }

    fun newDocument(name: String = "Unnamed") = call("new_document", mapOf("name" to name)) as String
    fun openDocument(path: String) = call("open_document", mapOf("path" to path)) as String
    fun saveDocument(path: String? = null) = call("save_document", mapOf("path" to path))
    fun recompute(): List<String> = (call("recompute") as JSONArray).let { a -> List(a.length()) { a.getString(it) } }
    fun begin(label: String) = call("begin", mapOf("label" to label))
    fun commit() = call("commit")
    fun abort() = call("abort")
    fun undo() = call("undo")
    fun redo() = call("redo")
    fun undoState(): History = (call("undo_state") as JSONObject).let { o ->
        fun l(k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } }
        History(l("undo"), l("redo"), o.optInt("pos"))
    }
    fun run(script: String, args: Map<String, Any?> = emptyMap()) = call("run", mapOf("script" to script, "args" to args))

    fun objects(): List<FcObject> {
        val a = call("objects") as JSONArray
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            fun l(k: String) = o.getJSONArray(k).let { x -> List(x.length()) { x.getString(it) } }
            FcObject(o.getString("name"), o.getString("label"), o.getString("type"), o.getBoolean("visible"), o.getBoolean("invalid"),
                l("parents"), l("group"), if (o.isNull("tip")) null else o.getString("tip"), o.getBoolean("hasShape"),
                if (o.isNull("assembly")) null else o.getString("assembly"), if (o.isNull("linked")) null else o.getString("linked"),
                if (o.isNull("subtype")) null else o.getString("subtype"), if (o.isNull("error")) null else o.getString("error"),
                if (o.has("claims")) l("claims") else emptyList(), o.optBoolean("suppressed"), o.optBoolean("attachable"), o.optBoolean("active"),
                o.optJSONArray("state")?.let { st -> (0 until st.length()).any { st.optString(it) == "Touched" } } == true)
        }
    }

    fun properties(name: String): List<FcProperty> {
        val a = call("properties", mapOf("name" to name)) as JSONArray
        return List(a.length()) { i ->
            val o = a.getJSONObject(i); val e = o.optJSONArray("enum")
            FcProperty(o.getString("name"), o.getString("type"), o.optString("group"), o.optString("doc"), o.optBoolean("readonly"),
                if (o.isNull("value")) null else o.get("value"), if (o.isNull("expression")) null else o.getString("expression"),
                if (e == null) emptyList() else List(e.length()) { e.getString(it) }, o.optString("kind").ifEmpty { null }, o)
        }
    }

    fun setProperty(name: String, prop: String, value: Any?) = call("set_property", mapOf("name" to name, "prop" to prop, "value" to value))
    fun setExpression(name: String, prop: String, expr: String?) = call("set_property", mapOf("name" to name, "prop" to prop, "expression" to (expr ?: "")))

    /** The object's mesh and placement.  A shape the engine already sent (same geometry as another object, or as in the
     *  last refresh) comes back as its key and is taken from [cache] or [old] (the last refresh's); the meshes used are
     *  put in [cache]. */
    fun tessellate(name: String, cache: MutableMap<String, FcMesh>, old: Map<String, FcMesh> = emptyMap(), tol: Double = 0.05): FcPart {
        for (attempt in 0..1) {
            val buf = nativeTessellate(name, tol) ?: throw OutOfMemoryError("tessellate $name")
            val addr = nativeAddress(buf); var owned = true      // freed here unless a mesh took it
            try {
                val b = buf.order(ByteOrder.LITTLE_ENDIAN)
                if (b.remaining() < 8 || b.int != 0x324D434D) return FcPart(empty, null)   // "MCM2"
                val ref = b.int and 1 != 0; val kl = b.int
                val kb = ByteArray(kl); b.get(kb); b.position(b.position() + (-kl).mod(4)); val key = String(kb)
                val m = FloatArray(16); b.asFloatBuffer().get(m); b.position(b.position() + 64)
                val mat = m.takeUnless { (0 until 16).all { i -> it[i] == if (i % 5 == 0) 1f else 0f } }
                if (ref) {
                    (cache[key] ?: old[key]?.also { cache[key] = it })?.let { return FcPart(it, mat) }
                    call("tess_drop", mapOf("keys" to listOf(key))); continue }      // the app no longer has it: send it again
                fun section(): ByteBuffer { val n = b.int; val s = b.slice().order(ByteOrder.LITTLE_ENDIAN); s.limit(n * 4); b.position(b.position() + n * 4); return s }
                val rv = section(); val ri = section(); val rf = section(); val rl = section(); val re = section(); val rp = section(); val rc = section()
                val faces = rf.asIntBuffer().let { IntArray(it.capacity()).also { a -> it.get(a) } }
                val edges = re.asIntBuffer().let { IntArray(it.capacity()).also { a -> it.get(a) } }
                val mesh = FcMesh(key, rv.asFloatBuffer(), ri.asIntBuffer(), faces, rl.asFloatBuffer(), edges, rp.asFloatBuffer(),
                    rc.asFloatBuffer().takeIf { it.capacity() > 0 }, arrayOf(rv, ri, rl, rp, rc))
                freeWhenUnused(mesh, addr); owned = false
                if (key.isNotEmpty()) cache[key] = mesh
                return FcPart(mesh, mat)
            } finally { if (owned) nativeFree(addr) }
        }
        return FcPart(empty, null)
    }

    companion object {
        @JvmStatic private external fun nativeFree(addr: Long)
        @JvmStatic private external fun nativeAddress(buf: ByteBuffer): Long
        private val emptyF = ByteBuffer.allocateDirect(0).asFloatBuffer(); private val emptyI = ByteBuffer.allocateDirect(0).asIntBuffer()
        val empty = FcMesh("", emptyF, emptyI, IntArray(0), emptyF, IntArray(0), emptyF, null)
        // native mesh memory is freed once its FcMesh can no longer be reached (the view, XR and the cache drop it)
        private val gone = java.lang.ref.ReferenceQueue<FcMesh>()
        private val live = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Ref, Boolean>())
        private class Ref(m: FcMesh, val addr: Long) : java.lang.ref.PhantomReference<FcMesh>(m, gone)
        private fun freeWhenUnused(m: FcMesh, addr: Long) { live.add(Ref(m, addr)); reaper }
        private val reaper by lazy { Thread({ while (true) { val r = gone.remove() as Ref; live.remove(r); nativeFree(r.addr) } }, "mesh-free").apply { isDaemon = true; start() } }
        @Volatile private var inst: Engine? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: Engine(ctx.applicationContext).also { inst = it } }
    }
}
