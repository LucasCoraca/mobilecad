package app.mobilecad.freecad

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.IdentityHashMap

/**
 * The 3D view of a large model as it was last drawn, kept per file (by its contents), so opening that file again shows
 * the model within seconds while FreeCAD still reads it: reading a large STEP assembly takes minutes, most of it in
 * OCCT's STEP reader.  Only the display is cached: the document itself is always read from the file, and its own
 * meshes replace these once it is ready.
 *
 * Layout (little endian): "MCV2", mesh count, part count; per mesh: key, bounds (6 floats), faces (int count + ints), edges (same), then
 * the five sections of FcMesh.raw (int byte count + bytes, padded to 4); per part: name, mesh index, 16 floats of its
 * matrix (NaN first: none).  Strings are an int byte count + UTF-8, padded to 4.  Read memory-mapped: nothing is copied
 * into the Java heap, and pages are read as the GPU upload reaches them.
 */
object ViewCache {
    private const val MAGIC = 0x3256434D          // "MCV2"
    /** Models with fewer parts open fast enough without it. */
    const val MIN_PARTS = 200
    private const val KEEP = 4                    // files cached

    private fun dir(ctx: Context) = File(ctx.filesDir, "viewcache").apply { mkdirs() }

    /** The file's identity: its size and the hashes of its start, middle and end (a large file is not read whole). */
    fun fingerprint(f: File): String? = runCatching {
        val n = f.length(); val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(1 shl 20)
        RandomAccessFile(f, "r").use { r -> for (at in longArrayOf(0, maxOf(0, n / 2 - buf.size / 2), maxOf(0, n - buf.size))) {
            r.seek(at); val k = r.read(buf); if (k > 0) md.update(buf, 0, k) } }
        n.toString(16) + "-" + md.digest().take(12).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun has(ctx: Context, fp: String) = File(dir(ctx), "$fp.mcv").isFile

    /** Writes the parts shown ([parts]: object name -> mesh at its place) for the file [fp]; keeps the newest few files. */
    fun write(ctx: Context, fp: String, parts: Map<String, FcPart>) {
        val tmp = File(dir(ctx), "$fp.tmp"); val out = File(dir(ctx), "$fp.mcv")
        try {
            val index = IdentityHashMap<FcMesh, Int>(); val meshes = ArrayList<FcMesh>()
            for (p in parts.values) if (p.mesh !in index) { index[p.mesh] = meshes.size; meshes.add(p.mesh) }
            RandomAccessFile(tmp, "rw").use { r -> r.setLength(0); val ch = r.channel
                val h = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
                fun flush() { h.flip(); while (h.hasRemaining()) ch.write(h); h.clear() }
                fun room(n: Int) { if (h.remaining() < n) flush() }
                fun int(v: Int) { room(4); h.putInt(v) }
                fun str(s: String) { val b = s.toByteArray(); int(b.size); for (x in b) { room(1); h.put(x) }; repeat((-b.size).mod(4)) { room(1); h.put(0) } }
                fun ints(a: IntArray) { int(a.size); for (v in a) int(v) }
                int(MAGIC); int(meshes.size); int(parts.size)
                for (m in meshes) {
                    str(m.key); for (v in m.min + m.max) { room(4); h.putFloat(v) }; ints(m.faces); ints(m.edges)
                    for (i in 0..4) { val b = m.raw.getOrNull(i)?.duplicate()?.apply { position(0) }; val n = b?.remaining() ?: 0
                        int(n); flush(); if (b != null) while (b.hasRemaining()) ch.write(b); repeat((-n).mod(4)) { room(1); h.put(0) } }
                }
                for ((name, p) in parts) { str(name); int(index[p.mesh]!!); val mt = p.matrix
                    for (i in 0 until 16) { room(4); h.putFloat(if (mt == null) (if (i == 0) Float.NaN else 0f) else mt[i]) } }
                flush()
            }
            if (!tmp.renameTo(out)) tmp.delete()
            dir(ctx).listFiles { f -> f.name.endsWith(".mcv") }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        } catch (e: Throwable) { tmp.delete(); out.delete(); Log.w("ViewCache", "not written", e) }
    }

    /** The parts saved for the file [fp], or null. */
    fun read(ctx: Context, fp: String): Map<String, FcPart>? {
        val f = File(dir(ctx), "$fp.mcv"); if (!f.isFile) return null
        return try {
            val b = RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }.order(ByteOrder.LITTLE_ENDIAN)
            if (b.int != MAGIC) { Log.w("ViewCache", "bad magic"); return null }
            val nm = b.int; val np = b.int
            fun str(): String { val n = b.int; val a = ByteArray(n); b.get(a); b.position(b.position() + (-n).mod(4)); return String(a) }
            fun ints() = IntArray(b.int).also { a -> b.asIntBuffer().get(a); b.position(b.position() + 4 * a.size) }
            fun sec(): ByteBuffer { val n = b.int; val s = b.slice().order(ByteOrder.LITTLE_ENDIAN); s.limit(n); b.position(b.position() + n + (-n).mod(4)); return s }
            val meshes = List(nm) {
                val key = str(); val bounds = FloatArray(6) { b.float }; val faces = ints(); val edges = ints()
                val raw = Array(5) { sec() }
                FcMesh(key, raw[0].asFloatBuffer(), raw[1].asIntBuffer(), faces, raw[2].asFloatBuffer(), edges, raw[3].asFloatBuffer(),
                    raw[4].takeIf { it.limit() > 0 }?.asFloatBuffer(), raw, bounds)
            }
            val parts = LinkedHashMap<String, FcPart>(np * 2)
            repeat(np) { val name = str(); val mi = b.int; val mt = FloatArray(16) { b.float }
                parts[name] = FcPart(meshes[mi], mt.takeUnless { it[0].isNaN() }) }
            f.setLastModified(System.currentTimeMillis())
            parts
        } catch (e: Throwable) { Log.w("ViewCache", "unreadable", e); f.delete(); null }
    }
}
