package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.agarthavision.R
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ArrowBackIosNew
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppTypography
import com.agarthavision.ui.theme.MonoSmallStyle

@Composable
fun ScreenTopBar(
    title: String,
    metaText: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(AgarthaIcons.ArrowBackIosNew, contentDescription = "Back", tint = colors.textPrimary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = AppTypography.headlineSmall,
                color = colors.textPrimary,
            )
            // MonoSmallStyle is the app-wide face for timestamps and counters (Inter + tnum);
            // the platform monospace face this used to force is what made the sheets look
            // different from every other screen.
            Text(
                metaText.uppercase(),
                color = colors.textSecondary,
                letterSpacing = 0.5.sp,
                style = MonoSmallStyle,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * Snapshot [SheetActionRow] renders from — bundled since this primary/secondary action pair
 * always travels together wherever it is used.
 */
data class SheetActionRowState(
    val primaryLabel: String,
    val secondaryLabel: String,
    val onPrimaryClick: () -> Unit,
    val onSecondaryClick: () -> Unit,
    val primaryLoading: Boolean = false,
    val primaryEnabled: Boolean = true
)

@Composable
fun SheetActionRow(state: SheetActionRowState) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Secondary Button — neutral grey chip. Discard is a confirmed action (it opens a
        // dialog), so it reads as secondary rather than as a red destructive control.
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(VerifyTestTags.SHEET_SECONDARY_ACTION)
                .background(colors.surfaceVariant, RoundedCornerShape(14.dp))
                .border(0.5.dp, colors.borderStrong, RoundedCornerShape(14.dp))
                .clickable { state.onSecondaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.secondaryLabel,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }

        // Primary Button — brand maroon, dimmed while the form is incomplete.
        Box(
            modifier = Modifier
                .weight(2f)
                .testTag(VerifyTestTags.SHEET_PRIMARY_ACTION)
                .background(
                    if (state.primaryEnabled) colors.accent else colors.accent.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp),
                )
                .clickable(enabled = state.primaryEnabled && !state.primaryLoading) { state.onPrimaryClick() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.primaryLoading) "Loading..." else state.primaryLabel,
                color = colors.onAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
        }
    }
}

/**
 * Width/height the frame preview should be laid out at. Falls back to square — the
 * capture pipeline emits 640x640 — when a frame carries no dimensions (older manual
 * captures), so the preview never collapses to zero height.
 */
internal fun FlaggedFrame.previewAspectRatio(): Float {
    val w = imageWidth ?: 0
    val h = imageHeight ?: 0
    return if (w > 0 && h > 0) w.toFloat() / h else 1f
}

/**
 * Small uppercase section label ("SPECIES", "REMARKS", the verification questions), in the
 * same face and weight the records screens use for their group titles.
 */
@Composable
internal fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textSecondary,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

/**
 * Snapshot a [NavPairRow] renders from — bundled like [SheetActionRowState], since the two
 * halves always travel together.
 */
internal data class NavPairState(
    val prevLabel: String,
    val nextLabel: String,
    val prevTag: String,
    val nextTag: String,
    val canGoPrev: Boolean,
    val canGoNext: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
)

/**
 * A full-width previous / next pair. Full-width on purpose: the medtech is working a
 * microscope with one hand, so small arrow buttons are a miss waiting to happen.
 */
@Composable
internal fun NavPairRow(state: NavPairState, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SmallToggle(
            label = state.prevLabel,
            selected = false,
            onClick = state.onPrev,
            modifier = Modifier
                .weight(1f)
                .testTag(state.prevTag),
            enabled = state.canGoPrev,
        )
        SmallToggle(
            label = state.nextLabel,
            selected = false,
            onClick = state.onNext,
            modifier = Modifier
                .weight(1f)
                .testTag(state.nextTag),
            enabled = state.canGoNext,
        )
    }
}

/**
 * Previous / next frame pair, laid out under the frame preview on both sheets.
 */
@Composable
internal fun FrameNavRow(
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavPairRow(
        state = NavPairState(
            prevLabel = stringResource(R.string.verify_prev_frame),
            nextLabel = stringResource(R.string.verify_next_frame),
            prevTag = VerifyTestTags.FRAME_PREV,
            nextTag = VerifyTestTags.FRAME_NEXT,
            canGoPrev = canGoPrev,
            canGoNext = canGoNext,
            onPrev = onPrev,
            onNext = onNext,
        ),
        modifier = modifier,
    )
}

/**
 * Compact pill button. When [enabled] is false it dims and stops accepting taps —
 * used to show the ends of the verification queue.
 */
@Composable
internal fun SmallToggle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val borderColor = when {
        !enabled -> AgarthaTheme.colors.border
        selected -> AgarthaTheme.colors.accent
        else -> AgarthaTheme.colors.borderStrong
    }
    val labelColor = when {
        !enabled -> AgarthaTheme.colors.textTertiary
        selected -> AgarthaTheme.colors.accent
        else -> AgarthaTheme.colors.textSecondary
    }

    Box(
        modifier = modifier
            .background(
                if (selected && enabled) AgarthaTheme.colors.accentTint else AgarthaTheme.colors.surface,
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
