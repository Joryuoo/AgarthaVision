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
    inferenceImageWidth: Float = 640f,
    inferenceImageHeight: Float = 640f,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = jpegBytes,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        if (showBoxes) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / inferenceImageWidth
                val scaleY = size.height / inferenceImageHeight
                predictions.forEachIndexed { index, box ->
                    val left = (box.x - box.width / 2f) * scaleX
                    val top = (box.y - box.height / 2f) * scaleY
                    val w = box.width * scaleX
                    val h = box.height * scaleY
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
