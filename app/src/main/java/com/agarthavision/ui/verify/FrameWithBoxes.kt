@file:Suppress("FunctionNaming")

package com.agarthavision.ui.verify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.agarthavision.data.remote.dto.PredictionDto

@Composable
fun FrameWithBoxes(
    jpegBytes: ByteArray,
    predictions: List<PredictionDto>,
    highlightedIndex: Int,
    showBoxes: Boolean,
    inferenceImageWidth: Int?,
    inferenceImageHeight: Int?,
    modifier: Modifier = Modifier,
) {
    val sourceW = inferenceImageWidth?.toFloat()?.takeIf { it > 0f }
    val sourceH = inferenceImageHeight?.toFloat()?.takeIf { it > 0f }
    Box(modifier = modifier) {
        AsyncImage(
            model = jpegBytes,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (showBoxes && sourceW != null && sourceH != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Match ContentScale.Fit: scale uniformly to fit the longer edge,
                // letterboxing the shorter edge. Compute offsets so boxes land on
                // the actual image pixels, not the letterbox bands.
                val scale = minOf(size.width / sourceW, size.height / sourceH)
                val drawnW = sourceW * scale
                val drawnH = sourceH * scale
                val offsetX = (size.width - drawnW) / 2f
                val offsetY = (size.height - drawnH) / 2f
                predictions.forEachIndexed { index, box ->
                    val left = offsetX + (box.x - box.width / 2f) * scale
                    val top = offsetY + (box.y - box.height / 2f) * scale
                    val w = box.width * scale
                    val h = box.height * scale
                    val color = if (index == highlightedIndex) {
                        Color(0xFFFF5A4A)  // AlertCoral — active detection
                    } else {
                        Color(0xFF1F5BFF)  // ClinicalBlue — other detections
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}
