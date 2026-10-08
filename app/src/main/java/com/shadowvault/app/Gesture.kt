package com.shadowvault.app

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.io.File
import kotlin.math.sqrt

/**
 * Математика жеста: ресемплинг в N точек, нормализация (центр + масштаб),
 * сравнение по среднему расстоянию. Жест хранится зашифрованным.
 */
object GestureMath {

    const val SAMPLES = 64
    const val MATCH_THRESHOLD = 0.11f // среднее расстояние после нормализации
    const val MIN_PATH_LENGTH_PX = 350f
    const val MIN_BOX_PX = 60f

    private val rnd = SecureRandom()

    fun resample(points: List<FloatArray>, n: Int = SAMPLES): FloatArray {
        val out = FloatArray(2 * n)
        if (points.isEmpty()) return out

        val cum = DoubleArray(points.size) // кумулятивная длина
        for (i in 1 until points.size) {
            val dx = (points[i][0] - points[i - 1][0]).toDouble()
            val dy = (points[i][1] - points[i - 1][1]).toDouble()
            cum[i] = cum[i - 1] + sqrt(dx * dx + dy * dy)
        }
        val total = cum.last()
        if (total == 0.0) {
            for (i in 0 until n) { out[2 * i] = points[0][0]; out[2 * i + 1] = points[0][1] }
            return out
        }

        var seg = 0
        var acc = 0.0
        for (i in 0 until n) {
            val d = total * i / (n - 1)
            while (seg < points.size - 2 && acc + (cum[seg + 1] - cum[seg]) < d) {
                acc += cum[seg + 1] - cum[seg]; seg++
            }
            val segLen = cum[seg + 1] - cum[seg]
            val t = if (segLen <= 0.0) 0.0 else (d - acc) / segLen
            out[2 * i] = points[seg][0] + ((points[seg + 1][0] - points[seg][0]) * t).toFloat()
            out[2 * i + 1] = points[seg][1] + ((points[seg + 1][1] - points[seg][1]) * t).toFloat()
        }
        return out
    }

    /** Центр в начало координат, масштаб ~[-0.5..0.5] по большей стороне. */
    fun normalize(v: FloatArray): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in v.indices step 2) {
            if (v[i] < minX) minX = v[i]; if (v[i] > maxX) maxX = v[i]
            if (v[i + 1] < minY) minY = v[i + 1]; if (v[i + 1] > maxY) maxY = v[i + 1]
        }
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f
        val scale = maxOf(maxX - minX, maxY - minY).takeIf { it > 0f } ?: 1f
        val out = FloatArray(v.size)
        for (i in v.indices step 2) {
            out[i] = (v[i] - cx) / scale
            out[i + 1] = (v[i + 1] - cy) / scale
        }
        return out
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        for (i in a.indices step 2) {
            val dx = (a[i] - b[i]).toDouble(); val dy = (a[i + 1] - b[i + 1]).toDouble()
            sum += sqrt(dx * dx + dy * dy)
        }
        return (sum / (a.size / 2)).toFloat()
    }

    fun isMeaningful(points: List<FloatArray>): Boolean {
        if (points.size < 8) return false
        var len = 0f
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in points.indices) {
            if (points[i][0] < minX) minX = points[i][0]; if (points[i][0] > maxX) maxX = points[i][0]
            if (points[i][1] < minY) minY = points[i][1]; if (points[i][1] > maxY) maxY = points[i][1]
            if (i > 0) {
                val dx = points[i][0] - points[i - 1][0]; val dy = points[i][1] - points[i - 1][1]
                len += sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
        }
        return len >= MIN_PATH_LENGTH_PX && (maxX - minX) >= MIN_BOX_PX && (maxY - minY) >= MIN_BOX_PX
    }

    fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes); rnd.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}

object GestureStorage {
    private const val FILE = "gesture.bin"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun hasTemplate(ctx: Context) = file(ctx).exists()

    fun save(ctx: Context, template: FloatArray) {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { d ->
            d.writeInt(template.size)
            template.forEach { d.writeFloat(it) }
        }
        val blob = CryptoManager.encrypt(baos.toByteArray())
        file(ctx).writeBytes(blob)
        // Синхронизация файла на диск, чтобы не осталось в page cache-образах
        file(ctx).setReadable(false, false)
    }

    fun load(ctx: Context): FloatArray? {
        val f = file(ctx)
        if (!f.exists()) return null
        return try {
            val plain = CryptoManager.decrypt(f.readBytes())
            java.io.DataInputStream(plain.inputStream()).use { d ->
                val n = d.readInt()
                FloatArray(n) { d.readFloat() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
