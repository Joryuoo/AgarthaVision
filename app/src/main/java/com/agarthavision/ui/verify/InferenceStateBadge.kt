package com.agarthavision.ui.verify

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.agarthavision.R
import com.agarthavision.domain.inference.InferenceState
import com.agarthavision.ui.theme.AgarthaColors

/**
 * How a queue row names its sample's [InferenceState] (14zcqntj6p0).
 *
 * Four labels, not "ready / in inference": the on-device path takes seconds per frame and frames
 * run one at a time, so calling ten waiting frames "In inference" when one is running would
 * misstate what the system is doing.
 *
 * The label is always shown; the tone only reinforces it, so nothing is carried by colour alone.
 */
internal enum class InferenceBadge(@StringRes val label: Int) {
    /** Waiting its turn. Neutral: nothing is happening to it yet. */
    QUEUED(R.string.badge_inference_queued),

    /** The one frame the queue is running. Gold, the brand's "attention, in progress" tone. */
    IN_INFERENCE(R.string.badge_inference_running),

    /** A model answered. The accent tint the queue always used for AI-assisted rows. */
    READY(R.string.badge_inference_ready),

    /** No model output, ever. The warning tint the queue always used for manual rows. */
    MANUAL(R.string.badge_manual),
    ;

    companion object {
        fun of(state: InferenceState): InferenceBadge = when (state) {
            InferenceState.QUEUED -> QUEUED
            InferenceState.IN_INFERENCE -> IN_INFERENCE
            InferenceState.READY -> READY
            InferenceState.MANUAL -> MANUAL
        }
    }
}

/** Container and text colour for [this] badge, from the theme's tokens (C11). */
internal fun InferenceBadge.tones(colors: AgarthaColors): Pair<Color, Color> = when (this) {
    InferenceBadge.QUEUED -> colors.surfaceMuted to colors.textSecondary
    InferenceBadge.IN_INFERENCE -> colors.goldTint to colors.goldText
    InferenceBadge.READY -> colors.accentTint to colors.accent
    InferenceBadge.MANUAL -> colors.warningTint to colors.warningText
}
