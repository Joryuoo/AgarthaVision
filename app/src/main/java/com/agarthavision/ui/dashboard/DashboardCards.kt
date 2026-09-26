package com.agarthavision.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.agarthavision.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.icons.Science
import com.agarthavision.ui.icons.Warning
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
internal fun ActiveSessionHero(
    sessionId: String,
    elapsed: String,
    frameCount: Int,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.accent)
            .clickable(onClick = onResume)
            .padding(18.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "RECENT SESSION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent.copy(alpha = 0.92f),
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                sessionId,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
                letterSpacing = (-0.7).sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(elapsed,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onAccent,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Text("  ·  ", fontSize = 12.sp, color = colors.onAccent.copy(alpha = 0.5f))
                Text(
                    pluralStringResource(R.plurals.dashboard_frame_count, frameCount, frameCount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccent,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }

        // Idle "breathing" scale so the recent-session affordance feels alive without the
        // old blinking dot, which read as a still-live recording indicator.
        val idleTransition = rememberInfiniteTransition(label = "recentSessionIdle")
        val iconScale by idleTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "recentSessionIdleScale"
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.surface, CircleShape)
                .clickable(onClick = onResume),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AgarthaIcons.Science,
                contentDescription = "Open session",
                tint = colors.accent,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )
        }
    }
}

enum class KpiKind {
    SESSIONS,
    POSITIVE_RATE,
    TO_REVIEW,
    AI_AGREEMENT,
}

data class KpiTileUi(
    val kind: KpiKind,
    val label: String,
    val value: String,
    val subtitle: String,
    val changeText: String = "",
    val sparkline: List<Double?> = emptyList(),
    val spokenDescription: String = "",
)

@Composable
internal fun KpiGrid(
    tiles: List<KpiTileUi>,
    isLoading: Boolean,
    onTileClick: (KpiKind) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val colorForKind = { kind: KpiKind ->
        when (kind) {
            KpiKind.SESSIONS -> KpiTileColors(
                bgColor = colors.accent,
                contentColor = colors.onAccent,
                labelColor = colors.onAccent.copy(alpha = 0.8f),
                subtitleColor = colors.onAccent.copy(alpha = 0.85f),
            )
            KpiKind.POSITIVE_RATE -> KpiTileColors(
                bgColor = colors.surface,
                contentColor = colors.textPrimary,
                labelColor = colors.textSecondary,
                subtitleColor = colors.textSecondary,
                borderColor = colors.border,
            )
            KpiKind.TO_REVIEW -> KpiTileColors(
                bgColor = colors.gold,
                contentColor = colors.onGold,
                labelColor = colors.onGold.copy(alpha = 0.8f),
                subtitleColor = colors.onGold.copy(alpha = 0.85f),
            )
            KpiKind.AI_AGREEMENT -> KpiTileColors(
                bgColor = colors.surfaceVariant,
                contentColor = colors.textPrimary,
                labelColor = colors.textSecondary,
                subtitleColor = colors.textSecondary,
                borderColor = colors.border,
            )
        }
    }

    val displayTiles = if (tiles.size >= 4) {
        tiles.take(4)
    } else {
        listOf(
            KpiTileUi(
                KpiKind.SESSIONS,
                "Sessions",
                "0",
                "0 patients",
                "Sessions, 0, 0 patients. Opens sessions.",
            ),
            KpiTileUi(
                KpiKind.POSITIVE_RATE,
                "Positive rate",
                "—",
                "No smears examined yet",
                "Positive rate, no smears examined yet. Opens examined smears.",
            ),
            KpiTileUi(
                KpiKind.TO_REVIEW,
                "To review",
                "0",
                "0 verified today",
                "To review, 0 frames. Opens frames to review.",
            ),
            KpiTileUi(
                KpiKind.AI_AGREEMENT,
                "AI agreement",
                "—",
                "No AI results reviewed yet",
                "AI agreement, no AI results reviewed yet. Opens AI agreement details.",
            ),
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val tile0 = displayTiles[0]
            KpiTile(
                tile = tile0,
                colors = colorForKind(tile0.kind),
                isLoading = isLoading,
                onClick = { onTileClick(tile0.kind) },
                modifier = Modifier.weight(1f),
            )
            val tile1 = displayTiles[1]
            KpiTile(
                tile = tile1,
                colors = colorForKind(tile1.kind),
                isLoading = isLoading,
                onClick = { onTileClick(tile1.kind) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val tile2 = displayTiles[2]
            KpiTile(
                tile = tile2,
                colors = colorForKind(tile2.kind),
                isLoading = isLoading,
                onClick = { onTileClick(tile2.kind) },
                modifier = Modifier.weight(1f),
            )
            val tile3 = displayTiles[3]
            KpiTile(
                tile = tile3,
                colors = colorForKind(tile3.kind),
                isLoading = isLoading,
                onClick = { onTileClick(tile3.kind) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Color set for [KpiTile] — bundled since bg/content/label/border always travel together. */
private data class KpiTileColors(
    val bgColor: Color,
    val contentColor: Color,
    val labelColor: Color,
    val subtitleColor: Color,
    val borderColor: Color = Color.Transparent,
)

@Composable
private fun KpiTile(
    tile: KpiTileUi,
    colors: KpiTileColors,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .testTag("kpiTile_${tile.label}")
                .fillMaxWidth()
                .clip(shape)
                .background(if (isLoading) Color.Transparent else colors.bgColor, shape)
                .border(1.dp, if (isLoading) Color.Transparent else colors.borderColor, shape)
                .clickable(
                    enabled = !isLoading,
                    onClickLabel = "Open ${tile.label.lowercase()}",
                    onClick = onClick,
                )
                .padding(14.dp)
                .then(
                    if (isLoading) {
                        Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {}
                    } else {
                        Modifier.semantics(mergeDescendants = true) {
                            role = Role.Button
                            if (tile.spokenDescription.isNotEmpty()) {
                                contentDescription = tile.spokenDescription
                            }
                        }
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tile.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.labelColor)
                if (tile.changeText.isNotEmpty()) {
                    Text(
                        tile.changeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.labelColor,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tile.value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.contentColor,
                    letterSpacing = (-0.7).sp,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    lineHeight = 30.sp,
                )
                if (tile.sparkline.isNotEmpty()) {
                    Sparkline(
                        points = tile.sparkline,
                        lineColor = colors.contentColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .width(48.dp)
                            .height(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tile.subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = colors.subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isLoading) {
            SkeletonBox(
                modifier = Modifier.matchParentSize(),
                shape = shape,
            )
        }
    }
}

/*
 * SparklineCard and Sparkline are gone (PB-23).
 *
 * The card showed a seven-day egg-count trend with a delta beside it, and that delta was the
 * string literal "+38%" - it had never reflected any data. Deleted rather than re-pointed: a
 * seven-day trend of egg counts across different patients is not a meaningful aggregate, and
 * inventing one repeats the mistake PB-16 removed.
 */

@Composable
internal fun SpeciesMixCard(
    title: String,
    positiveSmearsCount: Int,
    speciesData: List<SpeciesData>,
    modifier: Modifier = Modifier,
) {
    val theme = AgarthaTheme.colors
    // Categorical series colors — brand tokens only (labels carry the meaning)
    val colors = listOf(theme.accent, theme.gold, theme.success)

    val speciesCount = speciesData.size
    val speciesPart = if (speciesCount == 1) "1 species" else "$speciesCount species"
    val smearPart = if (positiveSmearsCount == 1) {
        "1 positive smear"
    } else {
        "$positiveSmearsCount positive smears"
    }
    val summaryText = "$speciesPart · $smearPart"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
        Text(
            summaryText,
            fontSize = 11.sp,
            color = theme.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(14.dp))

        // Segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(theme.surfaceMuted)
        ) {
            speciesData.forEachIndexed { i, seg ->
                val color = colors.getOrElse(i) { theme.textTertiary }
                Box(
                    modifier = Modifier
                        .weight(seg.ratio.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(color)
                )
                if (i < speciesData.lastIndex) {
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(theme.surface))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp)
        ) {
            speciesData.forEachIndexed { i, seg ->
                val color = colors.getOrElse(i) { theme.textTertiary }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        seg.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = theme.textSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        seg.formattedPercentage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}

