package app.mobilecad.viewport

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Z-up turntable camera. All access on the UI thread except matrix reads by the renderer (volatile copies). */
private const val HALF_PI = (Math.PI / 2).toFloat()

class Camera {
    @Volatile var yaw = -0.8f
    @Volatile var pitch = 0.5f
    @Volatile var dist = 320f
    @Volatile var tx = 0f; @Volatile var ty = 0f; @Volatile var tz = 20f
    @Volatile var w = 1; @Volatile var h = 1
    val fov = Math.toRadians(45.0).toFloat()
    /** Orthographic by default, as in FreeCAD (Std_OrthographicCamera); perspective is optional. */
    @Volatile var orthographic = true
    /** Rotation about the view axis (Std_ViewRotateLeft/Right, FreeCAD's standard view quaternions). */
    @Volatile var roll = 0f

    fun eye(): FloatArray = floatArrayOf(
        tx + dist * cos(pitch) * cos(yaw), ty + dist * cos(pitch) * sin(yaw), tz + dist * sin(pitch))

    fun view(): FloatArray {
        val e = eye(); val m = FloatArray(16); val u = basis().second
        Matrix.setLookAtM(m, 0, e[0], e[1], e[2], tx, ty, tz, u[0], u[1], u[2]); return m
    }

    fun proj(): FloatArray {
        val m = FloatArray(16)
        if (orthographic) {
            // same visible height at the target as the perspective view, so zoom/pan/pick scale stay consistent
            val hh = dist * tan(fov / 2); val hw = hh * w.toFloat() / h
            Matrix.orthoM(m, 0, -hw, hw, -hh, hh, -dist * 50f, dist * 50f)
        } else Matrix.perspectiveM(m, 0, Math.toDegrees(fov.toDouble()).toFloat(), w.toFloat() / h, max(1f, dist / 100f), dist * 20f)
        return m
    }

    fun viewProj(): FloatArray { val m = FloatArray(16); Matrix.multiplyMM(m, 0, proj(), 0, view(), 0); return m }

    fun orbit(dx: Float, dy: Float) {
        yaw -= dx * 0.006f
        pitch = min(HALF_PI, max(-HALF_PI, pitch + dy * 0.006f))
    }

    /** Two-finger slide: pan by centroid delta, zoom by pinch ratio. */
    fun slide(dx: Float, dy: Float, ratio: Float) {
        val (r, u) = basis()
        val k = 2f * dist * tan(fov / 2) / h
        tx += (-dx * r[0] + dy * u[0]) * k; ty += (-dx * r[1] + dy * u[1]) * k; tz += (-dx * r[2] + dy * u[2]) * k
        dist = min(5000f, max(20f, dist * ratio))
    }

    fun setView(yawV: Float, pitchV: Float) { yaw = yawV; pitch = pitchV; roll = 0f }

    /** FreeCAD Gui::Camera orientation quaternion (x, y, z, w): the camera looks down its local -Z with local +Y up. */
    fun setOrientation(q: FloatArray) {
        fun rot(v: FloatArray): FloatArray {   // v' = q v q*
            val (x, y, z, w) = q
            val ix = w * v[0] + y * v[2] - z * v[1]; val iy = w * v[1] + z * v[0] - x * v[2]
            val iz = w * v[2] + x * v[1] - y * v[0]; val iw = -x * v[0] - y * v[1] - z * v[2]
            return floatArrayOf(ix * w + iw * -x + iy * -z - iz * -y, iy * w + iw * -y + iz * -x - ix * -z, iz * w + iw * -z + ix * -y - iy * -x)
        }
        val e = rot(floatArrayOf(0f, 0f, 1f)); val up = rot(floatArrayOf(0f, 1f, 0f))
        pitch = Math.asin(e[2].coerceIn(-1f, 1f).toDouble()).toFloat()
        yaw = if (Math.hypot(e[0].toDouble(), e[1].toDouble()) > 1e-4) Math.atan2(e[1].toDouble(), e[0].toDouble()).toFloat() else -HALF_PI
        roll = 0f
        val (r0, u0) = basis()
        roll = Math.atan2(-(up[0] * r0[0] + up[1] * r0[1] + up[2] * r0[2]).toDouble(), (up[0] * u0[0] + up[1] * u0[1] + up[2] * u0[2]).toDouble()).toFloat()
    }

    /** Std_ViewFit: frame the bounding box. */
    fun fit(min: FloatArray, max: FloatArray) {
        if (min[0] > max[0]) return
        tx = (min[0] + max[0]) / 2; ty = (min[1] + max[1]) / 2; tz = (min[2] + max[2]) / 2
        val r = Math.sqrt(((max[0] - min[0]) * (max[0] - min[0]) + (max[1] - min[1]) * (max[1] - min[1]) + (max[2] - min[2]) * (max[2] - min[2])).toDouble()).toFloat() / 2
        dist = maxOf(20f, r / tan(fov / 2) * 1.15f * maxOf(1f, h.toFloat() / w))
    }

    /** View normal to a plane (Sketcher_ViewSketch); eye placed along [n] from [o]. */
    fun lookAlong(n: DoubleArray, o: DoubleArray) {
        tx = o[0].toFloat(); ty = o[1].toFloat(); tz = o[2].toFloat()
        yaw = Math.atan2(n[1], n[0]).toFloat().let { if (Math.abs(n[0]) < 1e-9 && Math.abs(n[1]) < 1e-9) -1.5707964f else it }
        pitch = Math.asin(n[2].coerceIn(-1.0, 1.0)).toFloat(); roll = 0f
    }

    /** Screen-right and screen-up unit vectors in world space (well defined at straight top/bottom views; roll applied). */
    fun basis(): Pair<FloatArray, FloatArray> {
        val f = floatArrayOf(-cos(pitch) * cos(yaw), -cos(pitch) * sin(yaw), -sin(pitch))
        val r = floatArrayOf(-sin(yaw), cos(yaw), 0f)
        val u = floatArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
        if (roll == 0f) return r to u
        val c = cos(roll); val sn = sin(roll)
        return FloatArray(3) { c * r[it] + sn * u[it] } to FloatArray(3) { c * u[it] - sn * r[it] }
    }

    /** World units per screen pixel at the target distance. */
    fun unitsPerPixel() = 2f * dist * tan(fov / 2) / h

    fun project(x: Float, y: Float, z: Float): FloatArray? {
        val vp = viewProj(); val o = FloatArray(4)
        Matrix.multiplyMV(o, 0, vp, 0, floatArrayOf(x, y, z, 1f), 0)
        if (o[3] <= 0f) return null
        return floatArrayOf((o[0] / o[3] * 0.5f + 0.5f) * w, (1f - (o[1] / o[3] * 0.5f + 0.5f)) * h)
    }

    /** Returns [origin(3), dir(3)] of the world-space ray through screen px. */
    fun ray(px: Float, py: Float): FloatArray {
        val inv = FloatArray(16); Matrix.invertM(inv, 0, viewProj(), 0)
        val nx = px / w * 2f - 1f; val ny = 1f - py / h * 2f
        fun un(z: Float): FloatArray {
            val o = FloatArray(4); Matrix.multiplyMV(o, 0, inv, 0, floatArrayOf(nx, ny, z, 1f), 0)
            return floatArrayOf(o[0] / o[3], o[1] / o[3], o[2] / o[3])
        }
        val a = un(-1f); val b = un(1f)
        val d = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val l = Math.sqrt((d[0] * d[0] + d[1] * d[1] + d[2] * d[2]).toDouble()).toFloat()
        return floatArrayOf(a[0], a[1], a[2], d[0] / l, d[1] / l, d[2] / l)
    }
}
