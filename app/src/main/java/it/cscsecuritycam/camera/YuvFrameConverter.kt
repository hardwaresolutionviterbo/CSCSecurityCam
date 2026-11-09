package it.cscsecuritycam.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object YuvFrameConverter {
    fun toJpeg(image: ImageProxy, quality: Int = 65): ByteArray? {
        val width = image.width
        val height = image.height
        if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) return null

        val nv21 = ByteArray(width * height * 3 / 2)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()

        var outputOffset = 0
        for (row in 0 until height) {
            val rowStart = yBase + row * yPlane.rowStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = vBase + row * vPlane.rowStride + col * vPlane.pixelStride
                val uIndex = uBase + row * uPlane.rowStride + col * uPlane.pixelStride
                nv21[outputOffset++] = vBuffer.get(vIndex)
                nv21[outputOffset++] = uBuffer.get(uIndex)
            }
        }

        val output = ByteArrayOutputStream()
        val success = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), quality, output)
        if (!success) return null

        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return output.toByteArray()

        val source = BitmapFactoryCompat.decode(output.toByteArray()) ?: return output.toByteArray()
        return try {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            val rotatedOutput = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, rotatedOutput)
            if (rotated !== source) rotated.recycle()
            rotatedOutput.toByteArray()
        } finally {
            source.recycle()
        }
    }

    private object BitmapFactoryCompat {
        fun decode(bytes: ByteArray): Bitmap? = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
