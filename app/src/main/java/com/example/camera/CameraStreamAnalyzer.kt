package com.example.camera

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.motion.MotionDetector
import com.example.motion.MotionResult
import java.io.ByteArrayOutputStream

class CameraStreamAnalyzer(
    private val onFrameEncoded: (ByteArray) -> Unit,
    private val motionDetector: MotionDetector? = null,
    private val onMotionResult: ((MotionResult) -> Unit)? = null,
    private val jpegQuality: Int = 75
) : ImageAnalysis.Analyzer {

    private var lastAnalysisTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Target ~30 fps (at least 32ms between frames)
        if (currentTimestamp - lastAnalysisTimestamp < 32) {
            image.close()
            return
        }
        lastAnalysisTimestamp = currentTimestamp

        try {
            val yPlane = image.planes[0]
            val yBuffer = yPlane.buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            yBuffer.rewind()

            // Run Motion Detection if enabled
            if (motionDetector != null && onMotionResult != null) {
                val motion = motionDetector.analyzeLuminance(yBytes, image.width, image.height)
                onMotionResult.invoke(motion)
            }

            val nv21 = yuv420ToNv21(image)
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                image.width,
                image.height,
                null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, image.width, image.height),
                jpegQuality,
                out
            )
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty()) {
                onFrameEncoded(bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        var pos = 0

        if (yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            val row = ByteArray(yRowStride)
            for (i in 0 until height) {
                yBuffer.position(i * yRowStride)
                yBuffer.get(row, 0, yRowStride)
                System.arraycopy(row, 0, nv21, pos, width)
                pos += width
            }
        }

        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride

                if (vIndex < vBuffer.capacity() && uIndex < uBuffer.capacity()) {
                    val v = vBuffer.get(vIndex)
                    val u = uBuffer.get(uIndex)

                    nv21[pos++] = v
                    nv21[pos++] = u
                }
            }
        }

        return nv21
    }
}
