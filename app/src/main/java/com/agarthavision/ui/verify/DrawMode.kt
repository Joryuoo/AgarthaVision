@file:Suppress("FunctionNaming", "LongParameterList")

package com.agarthavision.ui.verify

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.inference.ImageBox
import com.agarthavision.domain.usecase.verify.boxedCountOf
import com.agarthavision.domain.usecase.verify.fieldTotalOf
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Drawing a box, on a screen of its own.
 *
 * **Why a screen rather than a better-placed button.** The sheet is one scrolling column with the
 * frame at its top, and the frame is full-width at the capture aspect — a 640 square in practice,
 * so on a phone it is most of the viewport on its own. Every affordance that starts a drawing
 * (the Q2 redraw, every row of the Add Species reveal list) therefore sits below the fold by
 * construction, not by accident. Tapping one used to scroll nothing: the medtech had to find the
 * image again, aim at an egg they could no longer see when they decided to aim at it, drag, and
 * then scroll back down to learn whether it worked from a button changing its own label. Three
 * costs, all of them landing on someone holding a phone over a microscope.
 *
 * This is about drawing only. Where the frame sits while the medtech reads and answers is a
 * separate question (14zcqnthuab), and this screen works under any answer to it, because it
 * covers the sheet rather than rearranging it. What it guarantees whatever the sheet becomes:
 * the frame at full width, so the drag is as precise as the screen allows, and nothing moving
 * under the medtech's finger while they aim.
 *
 * **It takes every touch.** It is laid over a sheet that stays composed underneath, and a layer
 * with no pointer handling of its own lets a tap on its empty space fall through to whatever is
 * below it: the sheet's buttons, or the host screen behind the sheet. Keeping the sheet composed
 * is what lets Cancel return the medtech to the exact scroll they left; [onSaved] is where the
 * caller scrolls back to the top, so the box just saved is on screen when they land.
 *
 * **Cancel and Save box sit below the frame, not over it.** Laid over the bottom of the image they
 * covered the pixels being drawn on, and an egg near the lower edge could not be boxed without
 * dragging under a button.
 *
 * Leaving the sheet costs the medtech nothing: [drawTargetCaption] names the egg being located.
 *
 * The result is visible where they land, because the frame draws the box — see [frameBoxes].
 * That is 86d4by5n4, and without it this screen would return the medtech to a frame still
 * showing nothing and read as a failure.
 */
@Composable
internal fun DrawModeScreen(
    state: VerificationUiState,
    actions: VerificationSheetActions,
    onSaved: (ImageBox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = state.frame ?: return
    val imageModel = rememberFrameImageModel(frame, state.imageSource)
    val colors = AgarthaTheme.colors
    // Keyed on the target, so a draft never survives into a draw for a different egg.
    var draft by remember(state.drawTarget) { mutableStateOf<ImageBox?>(null) }

    // Backing out of drawing, not out of the sheet. Without this, the hardware back gesture
    // abandons the whole frame from inside a screen the medtech only meant to leave.
    BackHandler(onBack = actions.onCancelDraw)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // Claims every pointer that lands here, drawn on or not. Being a hit target is what
            // stops the sheet and the host screen beneath from receiving it.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .testTag(VerifyTestTags.DRAW_MODE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.drawTargetCaption(),
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .testTag(VerifyTestTags.DRAW_MODE_TARGET),
        )
        Text(
            text = stringResource(R.string.verify_draw_hint),
            color = colors.textTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )

        if (imageModel == null) {
            // Nothing to aim at. Drawing onto a blank canvas would post geometry against an image
            // nobody on this device can see, so the screen says so and offers the way out instead.
            FrameUnavailable(
                reason = state.imageSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.previewAspectRatio())
                    .padding(horizontal = 12.dp)
                    .testTag(VerifyTestTags.FRAME_UNAVAILABLE),
            )
        } else {
            FrameWithBoxes(
                imageModel = imageModel,
                // Every existing box, dimmed — the draft under the medtech's finger is the
                // only coloured one. Passing them at all is what lets the medtech see they
                // are not drawing over a box that is already there.
                boxes = state.findings.frameBoxes(active = null),
                showBoxes = state.showBoundingBoxes,
                inferenceImageWidth = frame.imageWidth,
                inferenceImageHeight = frame.imageHeight,
                isDrawing = true,
                onDraftChanged = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.previewAspectRatio())
                    .testTag(VerifyTestTags.FRAME_PREVIEW),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawControlButton(
                label = stringResource(R.string.verify_draw_cancel),
                tag = VerifyTestTags.DRAW_CANCEL,
                enabled = true,
                background = colors.surface,
                foreground = colors.textPrimary,
                onClick = actions.onCancelDraw,
                modifier = Modifier.weight(1f),
            )
            DrawControlButton(
                label = stringResource(R.string.verify_draw_accept),
                tag = VerifyTestTags.DRAW_ACCEPT,
                enabled = draft != null,
                background = colors.accent,
                foreground = colors.onAccent,
                onClick = { draft?.let(onSaved) },
                modifier = Modifier.weight(1f),
            )
        }
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

/**
 * What the medtech is drawing for, in the words the screen they came from used.
 *
 * An added egg is named the way the reveal list names it — species, then which egg of the field's
 * total — because those are the numbers someone at a microscope is holding in their head. The
 * ordinal counts from the boxes the model already supplied, so "egg 10 of 23" means the tenth egg
 * of that species in this field rather than the first one the medtech happens to be locating.
 */
@Composable
private fun VerificationUiState.drawTargetCaption(): String {
    val target = drawTarget
    val finding = target?.let { findings.getOrNull(it.findingIndex) }
    return when {
        target == null || finding == null -> ""
        target.slot == null -> stringResource(
            R.string.verify_draw_target_box,
            target.findingIndex + 1,
            frame?.predictions?.size ?: 0,
        )
        else -> {
            val species = finding.answers.speciesLabel.orEmpty()
            val stage = finding.answers.stage
            val otherStageText = finding.answers.otherStageText
            stringResource(
                R.string.verify_locate_egg_row,
                species,
                findings.boxedCountOf(species, stage, otherStageText) + target.slot + 1,
                findings.fieldTotalOf(species, stage, otherStageText),
            )
        }
    }
}
