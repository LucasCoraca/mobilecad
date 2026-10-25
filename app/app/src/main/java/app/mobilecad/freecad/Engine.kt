package app.mobilecad.freecad

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class FcObject(val name: String, val label: String, val type: String, val visible: Boolean, val invalid: Boolean,
               val parents: List<String>, val group: List<String>, val tip: String?, val hasShape: Boolean,
               val assembly: String? = null, val linked: String? = null, val subtype: String? = null, val error: String? = null,
               /** what FreeCAD's tree shows under this object (ViewProvider::claimChildren) */
               val claims: List<String> = emptyList())

class FcProperty(val name: String, val type: String, val group: String, val doc: String, val readonly: Boolean,
                 val value: Any?, val expression: String?, val enums: List<String>,
                 /** editor for non-scalar properties (engine _edit_form): "parts" (vector / placement components), "color",
                  *  "text", "file", "list", "edges" (Part fillet / chamfer); extra holds that editor's data. */
                 val kind: String? = null, val extra: org.json.JSONObject? = null)

/** Triangles: x y z nx ny nz face (stride 7). Edges: x1 y1 z1 x2 y2 z2 edge (stride 7). Indices are FreeCAD's Face<i>/Edge<i>. */
/** colors: optional r g b per tri vertex (or per point of a cloud) — Inspection deviation maps. */
class FcMesh(val tris: FloatArray, val edges: FloatArray, val verts: FloatArray = FloatArray(0), val colors: FloatArray = FloatArray(0))

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
    private external fun nativeTessellate(name: String, tol: Double): ByteArray
    private external fun nativeExec(src: String): String

    suspend fun start(progress: (String) -> Unit = {}) = withContext(dispatcher) {
        if (ready) return@withContext
        val root = ctx.filesDir
        val stamp = File(root, "runtime.version"); val want = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
        if (!stamp.exists() || stamp.readText() != want) {
            progress("Unpacking the CAD engine…")
            File(root, "python").deleteRecursively(); File(root, "freecad").deleteRecursively()
            File(root, "lib").deleteRecursively(); extract("python", File(root, "python")); extract("freecad-runtime", File(root, "freecad"))
            stamp.writeText(want)
        }
        progress("Starting the CAD engine…")
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
                if (o.has("claims")) l("claims") else emptyList())
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

    fun tessellate(name: String, tol: Double = 0.05): FcMesh {
        val b = ByteBuffer.wrap(nativeTessellate(name, tol)).order(ByteOrder.LITTLE_ENDIAN)
        if (b.remaining() < 8) return FcMesh(FloatArray(0), FloatArray(0))
        val nt = b.int; val t = FloatArray(nt); b.asFloatBuffer().get(t); b.position(b.position() + nt * 4)
        val ne = b.int; val e = FloatArray(ne); b.asFloatBuffer().get(e); b.position(b.position() + ne * 4)
        val v = if (b.remaining() >= 4) { val nv = b.int; FloatArray(nv).also { b.asFloatBuffer().get(it); b.position(b.position() + nv * 4) } } else FloatArray(0)
        val c = if (b.remaining() >= 4) { val nc = b.int; FloatArray(nc).also { b.asFloatBuffer().get(it) } } else FloatArray(0)
        return FcMesh(t, e, v, c)
    }

    companion object {
        @Volatile private var inst: Engine? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: Engine(ctx.applicationContext).also { inst = it } }
    }
}
