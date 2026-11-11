package com.example.remote

import android.content.Context
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame

/**
 * Capturer WebRTC alimentato dai fotogrammi che CameraX produce già per lo streaming LAN.
 *
 * Non apre una seconda sessione sulla fotocamera (impossibile: la possiede il servizio), quindi
 * la telecamera può trasmettere in LAN e via WebRTC contemporaneamente.
 */
class CameraFrameCapturer : VideoCapturer {

    private var observer: CapturerObserver? = null
    private var started = false

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        applicationContext: Context,
        capturerObserver: CapturerObserver
    ) {
        observer = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, fps: Int) {
        started = true
        observer?.onCapturerStarted(true)
    }

    override fun stopCapture() {
        if (started) {
            started = false
            observer?.onCapturerStopped()
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, fps: Int) = Unit

    override fun isScreencast(): Boolean = false

    override fun dispose() {
        started = false
        observer = null
    }

    /** `nv21` è il fotogramma CameraX; la rotazione è quella dichiarata da ImageProxy. */
    fun push(nv21: ByteArray, width: Int, height: Int, rotationDegrees: Int) {
        val capturerObserver = observer ?: return
        if (!started || width <= 0 || height <= 0) return
        val expected = width * height * 3 / 2
        if (nv21.size < expected) return

        val i420 = JavaI420Buffer.allocate(width, height)
        try {
            fillI420(nv21, width, height, i420)
            val frame = VideoFrame(i420, rotationDegrees, System.nanoTime())
            try {
                capturerObserver.onFrameCaptured(frame)
            } finally {
                frame.release()
            }
        } finally {
            i420.release()
        }
    }

    private fun fillI420(nv21: ByteArray, width: Int, height: Int, buffer: JavaI420Buffer) {
        val yPlane = buffer.dataY
        for (row in 0 until height) {
            yPlane.position(row * buffer.strideY)
            yPlane.put(nv21, row * width, width)
        }

        val uPlane = buffer.dataU
        val vPlane = buffer.dataV
        val uvHeight = height / 2
        val uvWidth = width / 2
        var offset = width * height
        for (row in 0 until uvHeight) {
            uPlane.position(row * buffer.strideU)
            vPlane.position(row * buffer.strideV)
            for (col in 0 until uvWidth) {
                // NV21 è V,U intercalati; I420 vuole i due piani separati.
                vPlane.put(nv21[offset])
                uPlane.put(nv21[offset + 1])
                offset += 2
            }
        }
    }
}
