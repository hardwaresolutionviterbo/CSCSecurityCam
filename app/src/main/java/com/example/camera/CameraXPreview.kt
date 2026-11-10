package com.example.camera

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.CameraStreamingService

/**
 * Anteprima della schermata Camera.
 *
 * Con lo streaming attivo la sessione CameraX appartiene a [CameraStreamingService]: qui viene
 * mostrata solo la superficie del PreviewView sulla sessione del servizio, così la cattura
 * sopravvive all'uscita dalla schermata e non ci sono due sessioni sulla stessa fotocamera.
 * Con lo streaming fermo la sessione di sola Preview è della schermata, come prima.
 */
@Composable
fun CameraXPreview(
    isBackCamera: Boolean,
    isTorchOn: Boolean,
    zoomRatio: Float,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewCamera by remember { mutableStateOf<Camera?>(null) }
    var screenPreview by remember { mutableStateOf<Preview?>(null) }

    if (isStreaming) {
        DisposableEffect(previewView) {
            val provider = previewView?.surfaceProvider
            CameraStreamingService.attachPreviewSurface(provider)
            onDispose { CameraStreamingService.attachPreviewSurface(null) }
        }
    } else {
        DisposableEffect(isBackCamera, previewView) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    previewView?.surfaceProvider?.let { preview.setSurfaceProvider(it) }
                    val selector = if (isBackCamera) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    provider.unbindAll()
                    previewCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    screenPreview = preview
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                previewCamera = null
                // Sgancia solo la Preview della schermata: un unbindAll spegnerebbe la sessione
                // del servizio quando lo streaming parte mentre questa schermata è aperta.
                val useCase = screenPreview
                screenPreview = null
                try {
                    if (useCase != null) {
                        ProcessCameraProvider.getInstance(context).get().unbind(useCase)
                    }
                } catch (e: Exception) {
                    // Ignore during disposal
                }
            }
        }
    }

    LaunchedEffect(isTorchOn, zoomRatio, isBackCamera, isStreaming, previewCamera) {
        if (isStreaming) {
            // La fotocamera è del servizio: i controlli vanno applicati alla sua sessione.
            CameraStreamingService.applyLens(isBackCamera)
            CameraStreamingService.applyTorch(isTorchOn)
            CameraStreamingService.applyZoom(zoomRatio)
        } else {
            try {
                if (isBackCamera) {
                    previewCamera?.cameraControl?.enableTorch(isTorchOn)
                }
                previewCamera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, 5f))
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
}
