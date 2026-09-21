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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.ChevronRight
import com.agarthavision.ui.icons.Science
import com.agarthavision.ui.icons.Warning
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
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
                Text(frameCount.toString(),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onAccent,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Text(" frames", fontSize = 12.sp, color = colors.onAccent.copy(alpha = 0.85f))
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

@Composable
internal fun KpiGrid(kpis: KpiState, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Sessions", kpis.sessionsCount, trend = null,
                colors = KpiTileColors(
                    bgColor = colors.accent,
                    contentColor = colors.onAccent,
                    labelColor = colors.onAccent.copy(alpha = 0.8f),
                ),
                modifier = Modifier.weight(1f))
            KpiTile("Verified", kpis.samplesCount, trend = null,
                colors = KpiTileColors(
                    bgColor = AppColors.Gray700,
                    contentColor = AppColors.White,
                    labelColor = AppColors.White.copy(alpha = 0.8f),
                ),
                modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Patients", kpis.patientsCount, trend = null,
                colors = KpiTileColors(
                    bgColor = AppColors.Gray900,
                    contentColor = AppColors.White,
                    labelColor = AppColors.White.copy(alpha = 0.8f),
                    borderColor = colors.border,
                ),
                modifier = Modifier.weight(1f))
            KpiTile("To review", kpis.pendingCount, trend = null,
                colors = KpiTileColors(
                    bgColor = colors.gold,
                    contentColor = colors.onGold,
                    labelColor = colors.onGold.copy(alpha = 0.75f),
                ),
                modifier = Modifier.weight(1f))
        }
    }
}

/** Color set for [KpiTile] — bundled since bg/content/label/border always travel together. */
private data class KpiTileColors(
    val bgColor: Color,
    val contentColor: Color,
    val labelColor: Color,
    val borderColor: Color = Color.Transparent,
)

/** A [KpiTile]'s trend indicator — the direction only has meaning alongside its label text. */
private data class KpiTrend(val label: String, val isUp: Boolean)

@Composable
private fun KpiTile(
    label: String,
    value: String,
    trend: KpiTrend?,
    colors: KpiTileColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(colors.bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, colors.borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.labelColor)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.contentColor,
            letterSpacing = (-0.7).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            lineHeight = 30.sp
        )
        if (trend != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trend.isUp) {
                    Text("↑ ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgarthaTheme.colors.success)
                }
                Text(
                    trend.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (trend.isUp) AgarthaTheme.colors.success else colors.labelColor,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
            }
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
    speciesData: List<SpeciesData>,
    modifier: Modifier = Modifier
) {
    val theme = AgarthaTheme.colors
    // Categorical series colors — brand tokens only (labels carry the meaning)
    val colors = listOf(theme.accent, theme.gold, theme.success)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("Today's findings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
        Text(
            "${speciesData.size} species detected",
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

@Composable
internal fun VerifyAlertRow(
    pendingCount: Int,
    oldestAgo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.warningTint, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AgarthaIcons.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                "$pendingCount frames awaiting verification",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                "Oldest pending · $oldestAgo",
                fontSize = 11.sp,
                color = colors.textSecondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Icon(
            imageVector = AgarthaIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

