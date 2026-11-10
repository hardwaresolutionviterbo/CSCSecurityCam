package com.example.camera

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun CameraXPreview(
    isBackCamera: Boolean,
    isTorchOn: Boolean,
    zoomRatio: Float,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    motionDetector: com.example.motion.MotionDetector? = null,
    onMotionResult: ((com.example.motion.MotionResult) -> Unit)? = null,
    onFrameEncoded: ((ByteArray) -> Unit)? = null,
    onCameraReady: (Camera) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(isBackCamera, isStreaming) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            val cameraSelector = if (isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                previewView?.surfaceProvider?.let { preview.setSurfaceProvider(it) }

                if (isStreaming && onFrameEncoded != null) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()

                    imageAnalysis.setAnalyzer(
                        analysisExecutor,
                        CameraStreamAnalyzer(
                            onFrameEncoded = onFrameEncoded,
                            motionDetector = motionDetector,
                            onMotionResult = onMotionResult
                        )
                    )

                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera
                    onCameraReady(boundCamera)
                } else {
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    camera = boundCamera
                    onCameraReady(boundCamera)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isTorchOn, camera) {
        try {
            if (isBackCamera) {
                camera?.cameraControl?.enableTorch(isTorchOn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(zoomRatio, camera) {
        try {
            camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, 5f))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                analysisExecutor.shutdown()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                // Ignore during disposal
            }
        }
    }
}
