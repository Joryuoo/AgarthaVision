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
import com.agarthavision.ui.records.AppColors
import com.agarthavision.ui.records.AgarthaTheme
import com.agarthavision.ui.records.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Species colors for the mix bar
private val SpeciesBlue  = AppColors.Blue
private val SpeciesCyan  = Color(0xFF0EA5E9)
private val SpeciesAmber = AppColors.Amber

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AgarthaTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.White)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl)
            ) {
                // 1. Status Bar Padding
                item {
                    Spacer(Modifier.statusBarsPadding().height(Spacing.md))
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
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Gray500,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Blue)
            .clickable(onClick = onResume)
            .padding(18.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = AppColors.White, size = 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE SESSION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.White.copy(alpha = 0.92f),
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                sessionId,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.White,
                letterSpacing = (-0.7).sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(elapsed,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.White,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Text("  ·  ", fontSize = 12.sp, color = AppColors.White.copy(alpha = 0.5f))
                Text(frameCount.toString(),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.White,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                Text(" frames", fontSize = 12.sp, color = AppColors.White.copy(alpha = 0.85f))
            }
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AppColors.White, CircleShape)
                .clickable(onClick = onResume),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = "Resume",
                tint = AppColors.Blue,
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Sessions",    kpis.sessionsCount, null, true,  Modifier.weight(1f))
            KpiTile("Samples",     kpis.samplesCount,  null, true,  Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            KpiTile("Verified",    kpis.verifiedRatio, null, true,  Modifier.weight(1f))
            KpiTile("EPG avg",     kpis.epgAvgStatus,  null, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    trend: String?,
    trendUp: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppColors.Gray500)
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Gray900,
            letterSpacing = (-0.7).sp,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            lineHeight = 30.sp
        )
        if (trend != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trendUp) {
                    Text("↑ ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Green)
                }
                Text(
                    trend,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (trendUp) AppColors.Green else AppColors.Gray500,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("7-day activity", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Gray900)
                Text(
                    "Eggs found per day",
                    fontSize = 11.sp,
                    color = AppColors.Gray500,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(AppColors.GreenTint, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    "↑ $delta",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green,
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
                    color = if (i == 6) AppColors.Gray700 else AppColors.Gray400,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = AppColors.Blue
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
        drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(sx, sy))
        drawCircle(lineColor,   radius = 2.5.dp.toPx(), center = Offset(sx, sy), style = Stroke(width = 1.5.dp.toPx()))

        val ex = xAt(values.lastIndex); val ey = yAt(values.last())
        drawCircle(lineColor,   radius = 4.dp.toPx(), center = Offset(ex, ey))
        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(ex, ey), style = Stroke(width = 2.dp.toPx()))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeciesMixCard(
    speciesData: List<SpeciesData>,
    modifier: Modifier = Modifier
) {
    // Map SpeciesData to color-weighted segments
    val colors = listOf(SpeciesBlue, SpeciesCyan, SpeciesAmber)
    val total = speciesData.sumOf { it.ratio.toDouble() }.toFloat().coerceAtLeast(1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text("Today's findings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Gray900)
        Text(
            "${speciesData.size} species detected",
            fontSize = 11.sp,
            color = AppColors.Gray500,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(14.dp))

        // Segmented bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AppColors.Gray100)
        ) {
            speciesData.forEachIndexed { i, seg ->
                val color = colors.getOrElse(i) { AppColors.Gray400 }
                Box(
                    modifier = Modifier
                        .weight(seg.ratio.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(color)
                )
                if (i < speciesData.lastIndex) {
                    Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(AppColors.White))
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
                val color = colors.getOrElse(i) { AppColors.Gray400 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        seg.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = AppColors.Gray700
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        seg.formattedPercentage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Gray900,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.White)
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AppColors.AmberTint, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = AppColors.Amber,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                "$pendingCount frames awaiting verification",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Gray900
            )
            Text(
                "Oldest pending · $oldestAgo",
                fontSize = 11.sp,
                color = AppColors.Gray500,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = AppColors.Gray300,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.Gray100, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AppColors.GreenTint, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = AppColors.Green,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                if (allSynced) "All samples synced" else "Sync pending",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Gray900
            )
            Text(
                "Last sync $lastSyncLabel · $samplesSynced samples",
                fontSize = 11.sp,
                color = AppColors.Gray500,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
