package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CscOrange
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR reale generato con ZXing: quello disegnato prima era un pattern decorativo e nessun lettore
 * riusciva a ricavarne l'indirizzo della telecamera. Include il quiet zone, quindi è scansionabile
 * da un altro telefono con CSCSecurityCam o con una fotocamera qualsiasi.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    codeColor: Color = Color(0xFF0F172A),
    cornerColor: Color = CscOrange
) {
    val matrix = remember(data) {
        runCatching {
            QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(2.dp, cornerColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (matrix == null) {
            Text(text = "QR non disponibile", color = codeColor, fontSize = 11.sp)
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val n = matrix.width
                val cellSize = size.width / n
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = codeColor,
                                topLeft = Offset(x * cellSize, y * cellSize),
                                size = Size(cellSize + 0.5f, cellSize + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val QR_SIZE = 512
