@file:Suppress("FunctionNaming", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs
import com.agarthavision.R
import com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX
import com.agarthavision.domain.inference.ImageBox
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
 * detection at once and gives each its own colour from a fixed palette, because a medtech
 * reading a crowded field has to tell which toggle belongs to which box. Two screens, two rules:
 * this one is focused on one detection at a time. Do not "fix" the inconsistency.
 *
 * While a box is being drawn the same rule holds, stated from the other side: the box under the
 * medtech's finger is the only coloured one, and every existing box recedes to gray.
 */
object VerificationBoxColors {
    /** The detection the question chain is currently about, or the box being drawn. */
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
 * the wrong specimen. It is also how drawing dims everything: the draft passes [NO_HIGHLIGHT].
 */
internal fun boxColorAt(index: Int, highlightedIndex: Int, active: Color, inactive: Color): Color =
    if (index == highlightedIndex) active else inactive

/** A highlight index no box can hold, so every existing box renders gray. */
internal const val NO_HIGHLIGHT = -1

/**
 * The frame, its detection boxes, and — when [isDrawing] — a box the medtech is drawing on it.
 *
 * Drawing is the primitive only: drag to define a rect, then accept or cancel. What the accepted
 * box *means* is the caller's business (PB-14b wires it to a Q2 redraw and to Add Egg), which is
 * why this emits geometry and nothing else.
 *
 * @param inferenceImageWidth the source image's width. Null falls back to
 *   [com.agarthavision.core.util.CAPTURE_FRAME_SIZE_PX], which is safe by construction rather
 *   than a guess: `toJpegBytes()` centre-crops and downscales every frame to a 640 square before
 *   it is ever posted, so 640 is the only value a non-null dimension holds in practice. The one
 *   exception is a device whose camera cannot supply a 640 stream and encodes at its own smaller
 *   native square instead; such a frame, pulled down from Supabase with null dimensions, renders
 *   its boxes slightly off. Fixing that means carrying the dimensions down in
 *   `SampleRemoteDataSource.toEntity()`, not papering over it here.
 * @param isDrawing true while the caller wants a new box drawn. Turning it off abandons any
 *   draft in progress.
 * @param onBoxDrawn fired when the medtech accepts a draft, carrying the box in **image
 *   coordinates, centre-based** — the same space `Prediction` uses, so the two are
 *   interchangeable downstream.
 * @param onDrawCancelled fired when the medtech backs out instead.
 */
@Composable
fun FrameWithBoxes(
    jpegBytes: ByteArray,
    predictions: List<Prediction>,
    highlightedIndex: Int,
    showBoxes: Boolean,
    inferenceImageWidth: Int?,
    inferenceImageHeight: Int?,
    modifier: Modifier = Modifier,
    isDrawing: Boolean = false,
    onBoxDrawn: (ImageBox) -> Unit = {},
    onDrawCancelled: () -> Unit = {},
) {
    val sourceW = (inferenceImageWidth ?: CAPTURE_FRAME_SIZE_PX).toFloat().takeIf { it > 0f }
    val sourceH = (inferenceImageHeight ?: CAPTURE_FRAME_SIZE_PX).toFloat().takeIf { it > 0f }
    // Capture tokens at composition time — DrawScope inside Canvas is not @Composable.
    val activeBoxColor = VerificationBoxColors.active
    val otherBoxColor = VerificationBoxColors.inactive

    // The drag, in canvas pixels. Held here rather than hoisted because it is transient
    // interaction state with no meaning outside the gesture: only the accepted box leaves.
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    // The size the drag was measured in, read back from layout rather than from the draw pass.
    // The frame can be re-laid-out between the drag and the accept tap - a keyboard opening, the
    // scroll container settling - and a box converted against a stale size lands somewhere the
    // medtech did not draw it.
    var frameSize by remember { mutableStateOf(Size.Zero) }

    // Leaving draw mode abandons whatever was half-drawn. Without this, re-entering would
    // resume someone else's drag on a different detection.
    LaunchedEffect(isDrawing) {
        if (!isDrawing) {
            dragStart = null
            dragEnd = null
        }
    }

    Box(
        modifier = modifier.onSizeChanged {
            frameSize = Size(it.width.toFloat(), it.height.toFloat())
        },
    ) {
        AsyncImage(
            model = jpegBytes,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if ((showBoxes || isDrawing) && sourceW != null && sourceH != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(VerifyTestTags.FRAME_CANVAS)
                    // Keyed on isDrawing so the gesture detector is attached only while drawing.
                    // A pointerInput that is always present would swallow taps meant for the
                    // scroll container underneath the frame.
                    .then(
                        if (isDrawing) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        dragStart = start
                                        dragEnd = start
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        dragEnd = change.position
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val transform = frameTransform(size.width, size.height, sourceW, sourceH)
                    ?: return@Canvas

                if (showBoxes) {
                    predictions.forEachIndexed { index, box ->
                        // Predictions are centre-based: box.x is the centre, not the left edge.
                        val left = transform.toCanvasX(box.x - box.width / 2f)
                        val top = transform.toCanvasY(box.y - box.height / 2f)
                        drawRect(
                            // While drawing, the draft is the only coloured box.
                            color = boxColorAt(
                                index = index,
                                highlightedIndex = if (isDrawing) NO_HIGHLIGHT else highlightedIndex,
                                active = activeBoxColor,
                                inactive = otherBoxColor,
                            ),
                            topLeft = Offset(left, top),
                            size = Size(box.width * transform.scale, box.height * transform.scale),
                            style = Stroke(width = 3f),
                        )
                    }
                }

                val start = dragStart
                val end = dragEnd
                if (start != null && end != null) {
                    drawRect(
                        color = activeBoxColor,
                        topLeft = Offset(minOf(start.x, end.x), minOf(start.y, end.y)),
                        size = Size(abs(end.x - start.x), abs(end.y - start.y)),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }

        if (isDrawing) {
            DrawControls(
                canAccept = dragStart != null && dragEnd != null && sourceW != null && sourceH != null,
                onAccept = accept@{
                    val start = dragStart ?: return@accept
                    val end = dragEnd ?: return@accept
                    val transform = frameTransform(
                        frameSize.width,
                        frameSize.height,
                        sourceW ?: return@accept,
                        sourceH ?: return@accept,
                    ) ?: return@accept
                    onBoxDrawn(transform.imageBoxBetween(start.x, start.y, end.x, end.y))
                },
                onCancel = {
                    dragStart = null
                    dragEnd = null
                    onDrawCancelled()
                },
            )
        }
    }
}

/** Accept and cancel, laid over the bottom of the frame while a box is being drawn. */
@Composable
private fun BoxScope.DrawControls(
    canAccept: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AgarthaTheme.colors

    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrawControlButton(
            label = stringResource(R.string.verify_draw_cancel),
            tag = VerifyTestTags.DRAW_CANCEL,
            enabled = true,
            background = colors.surface,
            foreground = colors.textPrimary,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        DrawControlButton(
            label = stringResource(R.string.verify_draw_accept),
            tag = VerifyTestTags.DRAW_ACCEPT,
            enabled = canAccept,
            background = colors.accent,
            foreground = colors.onAccent,
            onClick = onAccept,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DrawControlButton(
    label: String,
    tag: String,
    enabled: Boolean,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) background else background.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
