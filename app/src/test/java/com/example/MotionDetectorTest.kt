package com.example

import com.example.motion.MotionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {

    @Test
    fun testNoMotionOnStaticFrames() {
        val detector = MotionDetector(sensitivity = 0.65f)
        val staticBytes = ByteArray(640 * 480) { 100.toByte() }

        // First frame initializes baseline
        val result1 = detector.analyzeLuminance(staticBytes, 640, 480)
        assertFalse(result1.hasMotion)

        // Second frame identical -> no motion
        val result2 = detector.analyzeLuminance(staticBytes, 640, 480)
        assertFalse(result2.hasMotion)
        assertEquals(0f, result2.motionScore, 0.001f)
    }

    @Test
    fun testMotionDetectionOnSignificantChange() {
        val detector = MotionDetector(sensitivity = 0.85f)
        val frame1 = ByteArray(640 * 480) { 40.toByte() }
        val frame2 = ByteArray(640 * 480) { 120.toByte() }
        val frame3 = ByteArray(640 * 480) { 200.toByte() }

        detector.analyzeLuminance(frame1, 640, 480)
        detector.analyzeLuminance(frame2, 640, 480)
        val result = detector.analyzeLuminance(frame3, 640, 480)

        assertTrue("Motion should be confirmed on consecutive changed frames", result.hasMotion)
        assertNotNull(result.activeZone)
    }

    @Test
    fun testSensitivityAdjustment() {
        val detector = MotionDetector(sensitivity = 0.35f)
        assertEquals(0.35f, detector.sensitivity, 0.01f)

        detector.sensitivity = 0.85f
        assertEquals(0.85f, detector.sensitivity, 0.01f)
    }
}
