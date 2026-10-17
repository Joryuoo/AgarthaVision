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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ArrowBackIosNew
import com.agarthavision.ui.icons.ChevronLeft
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppTypography

/**
 * Back arrow, a title, and an optional line of meta beneath it.
 *
 * [metaText] is blank on the Verification Screen: the sample's label is the whole title there,
 * and the frame counter it used to carry now sits beneath the frame as the Current Sample
 * indicator, next to the control that changes it. A blank meta renders nothing rather than an
 * empty line that pushes the title off centre.
 */
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
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = if (metaText.isNotBlank()) 4.dp else 8.dp,
            ),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = AppTypography.headlineSmall,
                color = colors.textPrimary,
            )
            if (metaText.isNotBlank()) {
                Text(
                    metaText,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }
        actions()
    }
}

@Composable
internal fun ModeChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val colors = AgarthaTheme.colors
    val fgColor = contentColor ?: colors.accent
    Row(
        modifier = modifier
            .background(containerColor ?: colors.accentTint2, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fgColor,
        )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Secondary Button — neutral grey chip. Discard is a confirmed action (it opens a
        // dialog), so it reads as secondary rather than as a red destructive control.
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(VerifyTestTags.SHEET_SECONDARY_ACTION)
                .background(colors.surface, RoundedCornerShape(12.dp))
                .border(1.dp, colors.borderStrong, RoundedCornerShape(12.dp))
                .clickable { state.onSecondaryClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.secondaryLabel,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Primary Button — brand maroon, dimmed while the form is incomplete.
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag(VerifyTestTags.SHEET_PRIMARY_ACTION)
                .background(
                    if (state.primaryEnabled) colors.brandFill else colors.brandFill.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp),
                )
                .clickable(enabled = state.primaryEnabled && !state.primaryLoading) { state.onPrimaryClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.primaryLoading) "Loading..." else state.primaryLabel,
                color = colors.onBrandFill,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
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
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = AgarthaTheme.colors.textSecondary,
        letterSpacing = 0.8.sp,
        modifier = modifier,
    )
}

/**
 * Where you are in a cycle, and the two controls that move you through it.
 *
 * Used twice on the Verification Screen, for the two things a medtech steps through: the samples
 * in the queue, and the detections within one sample. The indicator sits left and the pair of
 * icon buttons right, per the screen's section spec.
 *
 * **Iconised, but not small.** The full-width labelled pair this replaced was full-width on
 * purpose — a medtech works a microscope one-handed and a small arrow is a miss waiting to
 * happen. Icons win the vertical space back for the frame, so the targets are held at the 48dp
 * minimum instead: smaller to look at, the same size to hit. Do not shrink them to fit the row.
 *
 * Each side goes dead at its end of the range rather than wrapping, so the control cannot read
 * as a way to leave the cycle.
 */
// A prev/next pair needs its label, description, tag and enablement on both sides; bundling
// them into a holder would add a type that exists only to satisfy the threshold.
@Suppress("LongParameterList")
@Composable
internal fun CycleRow(
    indicator: String,
    prevDescription: String,
    nextDescription: String,
    prevTag: String,
    nextTag: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    centered: Boolean = false,
) {
    if (centered) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CycleButton(
                icon = AgarthaIcons.ChevronLeft,
                contentDescription = prevDescription,
                enabled = canGoPrev,
                onClick = onPrev,
                tag = prevTag,
            )
            Text(
                text = indicator,
                color = AgarthaTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CycleButton(
                icon = AgarthaIcons.ChevronRight,
                contentDescription = nextDescription,
                enabled = canGoNext,
                onClick = onNext,
                tag = nextTag,
            )
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = indicator,
                    color = AgarthaTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = AgarthaTheme.colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CycleButton(
                    icon = AgarthaIcons.ChevronLeft,
                    contentDescription = prevDescription,
                    enabled = canGoPrev,
                    onClick = onPrev,
                    tag = prevTag,
                )
                CycleButton(
                    icon = AgarthaIcons.ChevronRight,
                    contentDescription = nextDescription,
                    enabled = canGoNext,
                    onClick = onNext,
                    tag = nextTag,
                )
            }
        }
    }
}

/** One side of a [CycleRow]. Circular outline button matching the Figma redesign. */
@Composable
private fun CycleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(
                1.dp,
                if (enabled) colors.borderStrong else colors.border,
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.textPrimary else colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// SmallToggle is gone with the labelled previous/next pair it rendered. Its one job was a
// full-width pill that dimmed at the ends of a cycle; CycleRow's icon buttons carry that
// behaviour, and there is no second caller to keep it alive for.
