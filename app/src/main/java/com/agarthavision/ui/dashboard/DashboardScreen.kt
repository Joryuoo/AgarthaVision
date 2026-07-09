package com.agarthavision.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AgarthaTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            // 1. App Header (with theme toggle)
            item {
                com.agarthavision.ui.components.AppHeader(
                    isDarkMode = state.isDarkMode,
                    onToggleTheme = viewModel::onToggleTheme
                )
            }

            // 1b. Account / sync banner (ADR-007 offline access)
            item {
                Spacer(Modifier.height(Spacing.sm))
                AccountSyncBanner(
                    state = state,
                    onSignIn = { onNavigate(Screen.Login.route) },
                    onSyncNow = viewModel::onSyncNow,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }

            // 2. Active Session Hero (only when active)
            state.activeSession?.let { session ->
                item {
                    ActiveSessionHero(
                        sessionId    = session.label,
                        elapsed      = session.startedAtAgo,
                        frameCount   = session.totalFrames.toIntOrNull() ?: 0,
                        onResume     = { onNavigate(Screen.Capture.route) },
                        modifier     = Modifier.padding(horizontal = Spacing.xl)
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            // 3. Today's Activity KPI Grid
            item {
                SectionLabel(
                    "Today's activity",
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
            item {
                Spacer(Modifier.height(Spacing.sm))
                KpiGrid(
                    kpis = state.kpis,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }

            // 4. Sparkline card
            item {
                Spacer(Modifier.height(Spacing.lg))
                SparklineCard(
                    values = if (state.epgSparklineData.size == 7) state.epgSparklineData
                             else List(7) { 0f },
                    delta  = "+38%",
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }

            // 5. Species mix card
            if (state.topSpecies.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    SpeciesMixCard(
                        speciesData = state.topSpecies,
                        modifier    = Modifier.padding(horizontal = Spacing.xl)
                    )
                }
            }

            // 6. Verify alert row
            if (state.pendingReviewCount > 0) {
                item {
                    Spacer(Modifier.height(Spacing.lg))
                    VerifyAlertRow(
                        pendingCount = state.pendingReviewCount,
                        oldestAgo    = state.oldestPendingAgo,
                        onClick      = { onNavigate(Screen.VerificationQueue.route) },
                        modifier     = Modifier.padding(horizontal = Spacing.xl)
                    )
                }
            }

            // 7. Sync status row
            item {
                Spacer(Modifier.height(Spacing.md))
                SyncStatusRow(
                    allSynced      = state.allSynced,
                    lastSyncLabel  = state.lastSyncLabel,
                    samplesSynced  = state.syncedSamplesCount,
                    modifier       = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

/**
 * Account / sync banner (ADR-007). Signed-out shows a "Sign in" CTA; signed-in shows
 * pending-upload state with a "Sync now" action (disabled while offline or syncing).
 */
@Composable
private fun AccountSyncBanner(
    state: DashboardUiState,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val hasPending = state.pendingUploadCount > 0
    // Neutral surface when signed-out; amber when items await upload; quiet green when clear.
    val (bg, border) = when {
        !state.isSignedIn -> colors.surface to colors.border
        hasPending -> colors.warningTint to colors.warning
        else -> colors.successTint to colors.success
    }
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .border(1.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        val statusText = when {
            !state.isSignedIn && hasPending ->
                stringResource(R.string.dashboard_pending_upload, state.pendingUploadCount)
            !state.isSignedIn -> stringResource(R.string.dashboard_signed_out)
            state.isSyncing -> stringResource(R.string.dashboard_syncing)
            hasPending -> stringResource(R.string.dashboard_pending_upload, state.pendingUploadCount)
            else -> stringResource(R.string.dashboard_all_synced)
        }
        Text(
            text = statusText,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!state.isSignedIn) {
            BannerAction(text = stringResource(R.string.dashboard_sign_in), enabled = true, onClick = onSignIn)
        } else {
            BannerAction(
                text = if (state.isSyncing) stringResource(R.string.dashboard_syncing)
                       else stringResource(R.string.dashboard_sync_now),
                enabled = state.canSyncNow,
                onClick = onSyncNow,
            )
        }
    }
}

@Composable
private fun BannerAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AgarthaTheme.colors
    Text(
        text = text,
        color = if (enabled) colors.accent else colors.textTertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = AgarthaTheme.colors.textSecondary,
        letterSpacing = 1.1.sp,
        modifier = modifier
    )
}

@Composable
private fun ActiveSessionHero(
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = colors.onAccent, size = 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE SESSION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onAccent.copy(alpha = 0.92f),
                    letterSpacing = 1.2.sp
                )
            }
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

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.surface, CircleShape)
                .clickable(onClick = onResume),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = "Resume",
                tint = colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.4f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

@Composable
private fun KpiGrid(kpis: KpiState, modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Sessions", kpis.sessionsCount, null, true,
                bgColor = colors.accent,
                contentColor = colors.onAccent,
                labelColor = colors.onAccent.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f))
            KpiTile("Samples", kpis.samplesCount, null, true,
                bgColor = AppColors.Gray700,
                contentColor = AppColors.White,
                labelColor = AppColors.White.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Verified", kpis.verifiedRatio, null, true,
                bgColor = AppColors.Gray900,
                contentColor = AppColors.White,
                labelColor = AppColors.White.copy(alpha = 0.8f),
                borderColor = colors.border,
                modifier = Modifier.weight(1f))
            KpiTile("EPG avg", kpis.epgAvgStatus, null, false,
                bgColor = colors.gold, contentColor = colors.onGold, labelColor = colors.onGold.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    trend: String?,
    trendUp: Boolean,
    bgColor: Color,
    contentColor: Color,
    labelColor: Color,
    borderColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = labelColor)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            letterSpacing = (-0.7).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            lineHeight = 30.sp
        )
        if (trend != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trendUp) {
                    Text("↑ ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AgarthaTheme.colors.success)
                }
                Text(
                    trend,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (trendUp) AgarthaTheme.colors.success else labelColor,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun SparklineCard(
    values: List<Float>,
    delta: String,
    modifier: Modifier = Modifier
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("7-day activity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text(
                    "Eggs found per day",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(colors.successTint, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    "↑ $delta",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.successText,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Sparkline(values = values, modifier = Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Today").forEachIndexed { i, lbl ->
                Text(
                    lbl.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (i == 6) colors.textPrimary else colors.textTertiary,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = AgarthaTheme.colors.accent
    val knockoutColor = AgarthaTheme.colors.surface
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxV = values.maxOrNull() ?: 1f
        val minV = values.minOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(1f)
        val pad = 6.dp.toPx()

        fun xAt(i: Int) = i.toFloat() * w / (values.size - 1)
        fun yAt(v: Float) = pad + (h - 2 * pad) * (1f - (v - minV) / range)

        val linePath = Path().apply {
            values.forEachIndexed { i, v ->
                val x = xAt(i); val y = yAt(v)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(w, h); lineTo(0f, h); close()
        }

        drawPath(
            path  = areaPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.18f), Color.Transparent))
        )
        drawPath(
            path  = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val sx = xAt(0); val sy = yAt(values.first())
        drawCircle(knockoutColor, radius = 2.5.dp.toPx(), center = Offset(sx, sy))
        drawCircle(
            lineColor,
            radius = 2.5.dp.toPx(),
            center = Offset(sx, sy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val ex = xAt(values.lastIndex); val ey = yAt(values.last())
        drawCircle(lineColor,     radius = 4.dp.toPx(), center = Offset(ex, ey))
        drawCircle(knockoutColor, radius = 4.dp.toPx(), center = Offset(ex, ey), style = Stroke(width = 2.dp.toPx()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesMixCard(
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
private fun VerifyAlertRow(
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
                painter = painterResource(R.drawable.ic_warning),
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
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SyncStatusRow(
    allSynced: Boolean,
    lastSyncLabel: String,
    samplesSynced: Int,
    modifier: Modifier = Modifier
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colors.successTint, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                if (allSynced) "All samples synced" else "Sync pending",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                "Last sync $lastSyncLabel · $samplesSynced samples",
                fontSize = 11.sp,
                color = colors.textSecondary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
