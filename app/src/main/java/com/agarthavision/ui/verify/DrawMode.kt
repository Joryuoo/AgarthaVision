@file:Suppress("FunctionNaming")

package com.agarthavision.ui.verify

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.R
import com.agarthavision.domain.usecase.verify.boxedCountOf
import com.agarthavision.domain.usecase.verify.fieldTotalOf
import com.agarthavision.ui.theme.AgarthaTheme

/**
 * Drawing a box, on a screen that holds nothing else.
 *
 * **Why a mode rather than a better-placed button.** The sheet is one scrolling column with the
 * frame at its top, and the frame is full-width at the capture aspect — a 640 square in practice,
 * so on a phone it is most of the viewport on its own. Every affordance that starts a drawing
 * (the Q2 redraw, every row of the Add Species reveal list) therefore sits below the fold by
 * construction, not by accident. Tapping one used to scroll nothing: the medtech had to find the
 * image again, aim at an egg they could no longer see when they decided to aim at it, drag, and
 * then scroll back down to learn whether it worked from a button changing its own label. Three
 * costs, all of them landing on someone holding a phone over a microscope.
 *
 * This mode is about drawing only. Where the frame sits while the medtech reads and answers is a
 * separate question (14zcqnthuab), and this mode works under any answer to it, because it
 * replaces the sheet rather than rearranging it. What it guarantees whatever the sheet becomes:
 * the frame at full width, so the drag is as precise as the screen allows, and nothing moving
 * under the medtech's finger while they aim.
 *
 * Leaving the sheet costs the medtech nothing: [drawTargetCaption] names the egg being located.
 *
 * The result is visible where they already are, because the frame they return to now draws the
 * box — see [frameBoxes]. That is 86d4by5n4, and without it this mode would return the medtech
 * to a frame still showing nothing and read as a failure.
 */
@Composable
internal fun DrawModeScreen(
    state: VerificationUiState,
    actions: VerificationSheetActions,
) {
    val frame = state.frame ?: return
    val imageModel = rememberFrameImageModel(frame, state.imageSource)
    val colors = AgarthaTheme.colors

    // Backing out of drawing, not out of the sheet. Without this, the hardware back gesture
    // abandons the whole frame from inside a mode the medtech only meant to leave.
    BackHandler(onBack = actions.onCancelDraw)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
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
            // nobody on this device can see, so the mode says so and offers the way out instead.
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
                onBoxDrawn = actions.onBoxDrawn,
                onDrawCancelled = actions.onCancelDraw,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frame.previewAspectRatio())
                    .testTag(VerifyTestTags.FRAME_PREVIEW),
            )
        }
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
            stringResource(
                R.string.verify_locate_egg_row,
                species,
                findings.boxedCountOf(species) + target.slot + 1,
                findings.fieldTotalOf(species),
            )
        }
    }
}
