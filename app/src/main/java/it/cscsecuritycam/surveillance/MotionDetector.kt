package it.cscsecuritycam.surveillance

import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * Lightweight frame-to-frame luminance motion detector.
 * It reports coarse scene changes; it is not a person/object classifier.
 */
class MotionDetector(
    private val cellColumns: Int = 16,
    private val cellRows: Int = 12,
    private val cellThreshold: Int = 22,
    private val changedCellsRequired: Int = 14,
    private val cooldownMillis: Long = 4_000L
) {
    private var previous: IntArray? = null
    private var lastEventAt = 0L

    @Synchronized
    fun detect(image: ImageProxy): Boolean {
        val plane = image.planes.firstOrNull() ?: return false
        val buffer = plane.buffer.duplicate()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val values = IntArray(cellColumns * cellRows)
        for (cy in 0 until cellRows) {
            for (cx in 0 until cellColumns) {
                val x = ((cx + 0.5f) * width / cellColumns).toInt().coerceIn(0, width - 1)
                val y = ((cy + 0.5f) * height / cellRows).toInt().coerceIn(0, height - 1)
                val index = y * rowStride + x * pixelStride
                values[cy * cellColumns + cx] = if (index in 0 until buffer.limit()) buffer.get(index).toInt() and 0xff else 0
            }
        }
        val old = previous
        previous = values
        if (old == null) return false

        var changed = 0
        for (i in values.indices) {
            if (abs(values[i] - old[i]) >= cellThreshold) changed++
        }
        val now = System.currentTimeMillis()
        if (changed >= changedCellsRequired && now - lastEventAt >= cooldownMillis) {
            lastEventAt = now
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        previous = null
        lastEventAt = 0L
    }
}
