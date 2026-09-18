@file:Suppress("FunctionNaming", "LongParameterList")

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
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * The two colours a detection box can be drawn in on the Verification Screen.
 *
 * **The current detection is coloured; every other box is gray.** The rule this replaced gave
 * the others `accent`, and two colours that both read as "meaningful" is the problem: an accent
 * box competes for attention with the one box the medtech is actually answering questions about.
 * Gray recedes. It says these exist, they are not your subject right now.
 *
 * **The Sample Data Screen uses a different rule, and that is deliberate** — it shows every
 * detection at once and gives each its own colour from a fixed palette, because there a medtech
 * reading a crowded field has to tell which toggle belongs to which box. Two screens, two rules:
 * this one is focused on one detection at a time. Do not "fix" the inconsistency.
 *
 * Exposed rather than inlined so that drawing a new box (PB-14) reads from the same place — the
 * box being drawn is the only coloured one, which is the same rule stated from the other side.
 */
object VerificationBoxColors {
    /** The detection the question chain is currently about. */
    val active: Color
        @Composable get() = AgarthaTheme.colors.danger

    /** Every other detection: present, not the subject. */
    val inactive: Color
        @Composable get() = AgarthaTheme.colors.textTertiary
}

/**
 * Picks a box's colour from its position in the prediction list.
 *
 * Pure, and separated out so the rule can be tested without a renderer. Note what happens when
 * [highlightedIndex] is outside the list: **no box is coloured**, which is correct rather than a
 * gap. Once a medtech adds an egg through Add Egg, the answer list is longer than the prediction
 * list and the current index runs past the end — at that point the subject of the questions has
 * no drawn box at all, and colouring an arbitrary prediction instead would point the medtech at
 * the wrong specimen.
 */
internal fun boxColorAt(index: Int, highlightedIndex: Int, active: Color, inactive: Color): Color =
    if (index == highlightedIndex) active else inactive

@Composable
fun FrameWithBoxes(
    jpegBytes: ByteArray,
    predictions: List<Prediction>,
    highlightedIndex: Int,
    showBoxes: Boolean,
    inferenceImageWidth: Int?,
    inferenceImageHeight: Int?,
    modifier: Modifier = Modifier,
) {
    val sourceW = inferenceImageWidth?.toFloat()?.takeIf { it > 0f }
    val sourceH = inferenceImageHeight?.toFloat()?.takeIf { it > 0f }
    // Capture tokens at composition time — DrawScope inside Canvas is not @Composable.
    val activeBoxColor = VerificationBoxColors.active
    val otherBoxColor = VerificationBoxColors.inactive
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
                    // Predictions are centre-based: box.x is the centre, not the left edge.
                    val left = offsetX + (box.x - box.width / 2f) * scale
                    val top = offsetY + (box.y - box.height / 2f) * scale
                    val w = box.width * scale
                    val h = box.height * scale
                    drawRect(
                        color = boxColorAt(index, highlightedIndex, activeBoxColor, otherBoxColor),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}
