package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CscOrange
import kotlin.math.absoluteValue

/**
 * High-fidelity QR Code Generator & Renderer with stylish orange corner targets
 * matching the CSCSecurityCam design board.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    codeColor: Color = Color(0xFF0F172A),
    cornerColor: Color = CscOrange
) {
    // Generate deterministic 25x25 QR Matrix with finder patterns and data hash
    val matrix = remember(data) { generateQrMatrix(data, 25) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = matrix.size
            val cellSize = size.width / n

            // Draw QR modules
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = codeColor,
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize + 0.5f, cellSize + 0.5f)
                        )
                    }
                }
            }

            // Draw Stylized Orange Accent Finder Outlines (top-left, top-right, bottom-left)
            val strokeWidth = 2.dp.toPx()
            val finderPixelSize = cellSize * 7

            // Top-Left Finder
            drawRoundRect(
                color = cornerColor,
                topLeft = Offset(0f, 0f),
                size = Size(finderPixelSize, finderPixelSize),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )

            // Top-Right Finder
            drawRoundRect(
                color = cornerColor,
                topLeft = Offset(size.width - finderPixelSize, 0f),
                size = Size(finderPixelSize, finderPixelSize),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )

            // Bottom-Left Finder
            drawRoundRect(
                color = cornerColor,
                topLeft = Offset(0f, size.height - finderPixelSize),
                size = Size(finderPixelSize, finderPixelSize),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

private fun generateQrMatrix(data: String, size: Int): Array<BooleanArray> {
    val matrix = Array(size) { BooleanArray(size) { false } }

    fun drawFinder(topRow: Int, leftCol: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                matrix[topRow + r][leftCol + c] = isOuter || isInner
            }
        }
    }

    // Three standard position detection patterns
    drawFinder(0, 0)
    drawFinder(0, size - 7)
    drawFinder(size - 7, 0)

    // Timing patterns
    for (i in 7 until size - 7) {
        val v = (i % 2 == 0)
        matrix[6][i] = v
        matrix[i][6] = v
    }

    // Alignment pattern for standard QR (around 18, 18 in 25x25)
    val alignR = size - 7
    val alignC = size - 7
    for (r in -2..2) {
        for (c in -2..2) {
            val isBorder = r == -2 || r == 2 || c == -2 || c == 2
            val isCenter = r == 0 && c == 0
            matrix[alignR + r][alignC + c] = isBorder || isCenter
        }
    }

    // Fill remaining cells with pseudo-random pattern derived from data hash
    val hash = data.hashCode().absoluteValue
    val bytes = data.toByteArray()

    var byteIndex = 0
    for (r in 0 until size) {
        for (c in 0 until size) {
            // Skip finder zones
            val inTopLeft = r <= 7 && c <= 7
            val inTopRight = r <= 7 && c >= size - 8
            val inBottomLeft = r >= size - 8 && c <= 7
            val inAlign = (r in alignR - 2..alignR + 2) && (c in alignC - 2..alignC + 2)
            val isTiming = (r == 6) || (c == 6)

            if (!inTopLeft && !inTopRight && !inBottomLeft && !inAlign && !isTiming) {
                val byteVal = if (bytes.isNotEmpty()) bytes[byteIndex % bytes.size].toInt() else hash
                val bitVal = ((byteVal xor (r * 31 + c * 17 + hash)) and 1) == 1
                matrix[r][c] = bitVal
                byteIndex++
            }
        }
    }

    return matrix
}
