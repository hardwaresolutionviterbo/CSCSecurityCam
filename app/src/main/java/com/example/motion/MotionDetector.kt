package com.example.motion

import android.graphics.RectF
import kotlin.math.abs

data class MotionResult(
    val hasMotion: Boolean,
    val motionScore: Float, // 0.0 to 1.0
    val activeZone: RectF? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class MotionDetector(
    var sensitivity: Float = 0.65f, // 0.0 (least sensitive) to 1.0 (most sensitive)
    private val pixelThreshold: Int = 28, // minimum luminance change per pixel
    private val minActivePixelsFraction: Float = 0.035f // fraction of pixels that must change
) {
    private var previousGrid: ByteArray? = null
    private val gridWidth = 64
    private val gridHeight = 48
    private var consecutiveMotionFrames = 0
    private var lastMotionTriggerTime = 0L
    private val triggerCooldownMs = 3000L // 3 seconds cooldown between new alarm events

    /**
     * Analyzes Y (luminance) plane bytes downsampled to 64x48 grid.
     */
    fun analyzeLuminance(yBytes: ByteArray, width: Int, height: Int): MotionResult {
        val currentGrid = ByteArray(gridWidth * gridHeight)
        val xStep = width / gridWidth
        val yStep = height / gridHeight

        for (gy in 0 until gridHeight) {
            val srcY = gy * yStep
            for (gx in 0 until gridWidth) {
                val srcX = gx * xStep
                val srcIndex = srcY * width + srcX
                if (srcIndex < yBytes.size) {
                    currentGrid[gy * gridWidth + gx] = yBytes[srcIndex]
                }
            }
        }

        val prev = previousGrid
        previousGrid = currentGrid
        if (prev == null) {
            return MotionResult(hasMotion = false, motionScore = 0f)
        }

        var changedPixels = 0
        var minX = gridWidth
        var maxX = 0
        var minY = gridHeight
        var maxY = 0

        // Adjusted threshold based on sensitivity
        // High sensitivity (0.9) -> low delta threshold (15)
        // Low sensitivity (0.3) -> high delta threshold (45)
        val dynamicThreshold = (50 - (sensitivity * 35)).toInt().coerceIn(10, 60)
        val requiredFraction = (minActivePixelsFraction * (1.2f - (sensitivity * 0.5f))).coerceAtLeast(0.015f)

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {
                val idx = gy * gridWidth + gx
                val diff = abs((currentGrid[idx].toInt() and 0xFF) - (prev[idx].toInt() and 0xFF))
                if (diff > dynamicThreshold) {
                    changedPixels++
                    if (gx < minX) minX = gx
                    if (gx > maxX) maxX = gx
                    if (gy < minY) minY = gy
                    if (gy > maxY) maxY = gy
                }
            }
        }

        val totalPixels = gridWidth * gridHeight
        val changeFraction = changedPixels.toFloat() / totalPixels
        val motionDetected = changeFraction > requiredFraction

        val now = System.currentTimeMillis()
        var boundingBox: RectF? = null

        if (motionDetected) {
            consecutiveMotionFrames++
            boundingBox = RectF(
                minX.toFloat() / gridWidth,
                minY.toFloat() / gridHeight,
                (maxX + 1).toFloat() / gridWidth,
                (maxY + 1).toFloat() / gridHeight
            )
        } else {
            consecutiveMotionFrames = (consecutiveMotionFrames - 1).coerceAtLeast(0)
        }

        // Require at least 2 consecutive frames to trigger to avoid flickering / sensor noise
        val isConfirmedAlarm = consecutiveMotionFrames >= 2 && (now - lastMotionTriggerTime > triggerCooldownMs)
        if (isConfirmedAlarm) {
            lastMotionTriggerTime = now
        }

        return MotionResult(
            hasMotion = isConfirmedAlarm,
            motionScore = changeFraction.coerceIn(0f, 1f),
            activeZone = if (motionDetected) boundingBox else null,
            timestamp = now
        )
    }

    fun reset() {
        previousGrid = null
        consecutiveMotionFrames = 0
    }
}
