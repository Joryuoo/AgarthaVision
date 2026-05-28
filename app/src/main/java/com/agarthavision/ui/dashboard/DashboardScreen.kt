package com.agarthavision.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.ui.components.AgarthaBottomBar
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaVisionTheme

// Design Tokens
private val Brand = Color(0xFF1E40AF)
private val BrandSecondary = Color(0xFF3B82F6)
private val BrandLight = Color(0xFF4F8EE8)
private val SuccessDeep = Color(0xFF248A3D)
private val Warning = Color(0xFFFF9F0A)
private val Danger = Color(0xFFFF3B30)
private val AiAccent = Color(0xFFAF52DE)
private val Violet2 = Color(0xFF5856D6)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF6E6E73)
private val Bg = Color(0xFFF2F2F7)
private val Surface = Color(0xFFFFFFFF)
private val Hairline = Color(0x1E3C3C43)
private val HairlineSoft = Color(0x143C3C43)

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = { AgarthaBottomBar(activeRoute = Screen.Dashboard.route, onNavigate = onNavigate) },
        containerColor = Bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            TodayHeader(uiState.userName, uiState.dateString)
            Spacer(modifier = Modifier.height(14.dp))
            if (uiState.activeSession != null) {
                ResumeActiveSessionCard(uiState.activeSession!!)
            }
            KpiRow(uiState.kpis)
            EpgThisWeekCard(uiState.epgSparklineData)
            TopSpeciesCard(uiState.topSpecies)
            PendingReviewActionCard(uiState.pendingReviewCount)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TodayHeader(userName: String, dateString: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, end = 22.dp, bottom = 14.dp, start = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(4.dp, CircleShape, spotColor = Brand)
                .background(Brush.linearGradient(listOf(Brand, BrandSecondary)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("MS", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Greeting Stack
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "GOOD EVENING", 
                color = Muted, 
                fontSize = 10.sp, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = 1.1.sp, 
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                userName, 
                color = Ink, 
                fontSize = 22.sp, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = (-0.6).sp, 
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                dateString, 
                color = Muted, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Notification Bell
        Box(
            modifier = Modifier
                .size(38.dp)
                .border(0.5.dp, Hairline, CircleShape)
                .background(Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Ink, modifier = Modifier.size(18.dp))
            
            // Badge dot
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .background(Danger, CircleShape)
                    .border(1.5.dp, Surface, CircleShape)
            )
        }
    }
}

@Composable
fun ResumeActiveSessionCard(state: ActiveSessionState) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp)
            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = Brand)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Brand, BrandSecondary, BrandLight),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
    ) {
        // Radial highlight
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33FFFFFF), Color.Transparent)
                    )
                )
        )
        
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ACTIVE SESSION", 
                    color = Color.White.copy(alpha = 0.85f), 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 1.2.sp, 
                    fontFamily = FontFamily.Monospace
                )
                
                // Live indicator
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size((6 * pulseScale).dp)
                            .shadow(8.dp, CircleShape, ambientColor = Color.White)
                            .background(Color.White, CircleShape)
                    )
                    Text("RECORDING", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
            
            Text(
                state.label, 
                color = Color.White, 
                fontSize = 36.sp, 
                fontWeight = FontWeight.ExtraBold, 
                letterSpacing = (-1.4).sp, 
                lineHeight = 36.sp, 
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                state.startedAtAgo, 
                color = Color.White.copy(alpha = 0.85f), 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Medium, 
                modifier = Modifier.padding(bottom = 14.dp)
            )
            
            // Stats Row
            Row(
                modifier = Modifier.padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StatCol(state.totalFrames, "FRAMES")
                StatCol(state.verifiedFrames, "VERIFIED")
                StatCol(state.totalEpg, "EPG")
                StatCol(state.pendingFrames, "PENDING")
            }
            
            // Resume btn
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Brand, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(7.dp))
                Text("Resume capture", color = Brand, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            }
        }
    }
}

@Composable
fun StatCol(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun KpiRow(state: KpiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KpiTile(Modifier.weight(1f), state.sessionsCount, "SESSIONS", "", SuccessDeep)
        KpiTile(Modifier.weight(1f), state.samplesCount, "SAMPLES", "", SuccessDeep)
        KpiTile(Modifier.weight(1f), state.verifiedRatio, "VERIFIED", "", SuccessDeep)
        KpiTile(Modifier.weight(1f), state.epgAvgStatus, "EPG AVG", "", Warning)
    }
}

@Composable
fun KpiTile(modifier: Modifier, num: String, lbl: String, delta: String, deltaColor: Color) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(Surface, RoundedCornerShape(14.dp))
            .border(0.5.dp, HairlineSoft, RoundedCornerShape(14.dp))
            .padding(top = 11.dp, start = 8.dp, end = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(num, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, lineHeight = 21.sp)
        Spacer(modifier = Modifier.height(5.dp))
        Text(lbl, color = Muted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(3.dp))
        Text(delta, color = deltaColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EpgThisWeekCard(sparklineData: List<Float>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 12.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(Surface, RoundedCornerShape(18.dp))
            .border(0.5.dp, HairlineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EPG this week", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Text("7-DAY · LIVE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, fontFamily = FontFamily.Monospace)
        }
        
        // Sparkline Canvas
        Canvas(modifier = Modifier.fillMaxWidth().height(70.dp)) {
            val width = size.width
            val height = size.height
            
            // Grid lines
            val lineColor = Color(0x123C3C43)
            drawLine(lineColor, Offset(0f, height * 0.25f), Offset(width, height * 0.25f), 0.5.dp.toPx())
            drawLine(lineColor, Offset(0f, height * 0.5f), Offset(width, height * 0.5f), 0.5.dp.toPx())
            drawLine(lineColor, Offset(0f, height * 0.75f), Offset(width, height * 0.75f), 0.5.dp.toPx())
            
            val ptsX = listOf(23f, 69f, 114f, 160f, 206f, 251f, 297f).map { it / 320f * width }
            
            // Map the dynamic sparkline data (0.0 to 1.0) into Y coordinates (44f to 12f)
            val minY = 12f / 70f * height
            val maxY = 44f / 70f * height
            val ptsY = if (sparklineData.size == 7) {
                sparklineData.map { value ->
                    // value is 0..1 (where 1 is highest point -> lower Y)
                    maxY - (value * (maxY - minY))
                }
            } else {
                listOf(44f, 32f, 40f, 24f, 30f, 20f, 12f).map { it / 70f * height }
            }
            
            val path = Path().apply {
                moveTo(ptsX[0], ptsY[0])
                for (i in 1..6) {
                    lineTo(ptsX[i], ptsY[i])
                }
            }
            
            val areaPath = Path().apply {
                addPath(path)
                lineTo(ptsX[6], height)
                lineTo(ptsX[0], height)
                close()
            }
            
            // Fill
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Brand.copy(alpha = 0.28f), Brand.copy(alpha = 0f))
                )
            )
            
            // Line
            drawPath(
                path = path,
                color = Brand,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            
            // Points
            for (i in 0..5) {
                drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(ptsX[i], ptsY[i]))
                drawCircle(color = Brand, radius = 2.5.dp.toPx(), center = Offset(ptsX[i], ptsY[i]), style = Stroke(width = 1.6.dp.toPx()))
            }
            // Today point
            drawCircle(color = Brand, radius = 5.5.dp.toPx(), center = Offset(ptsX[6], ptsY[6]))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(ptsX[6], ptsY[6]))
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val days = listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Today")
            days.forEachIndexed { i, day ->
                val isToday = i == 6
                Text(
                    text = day,
                    color = if (isToday) Brand else Muted,
                    fontSize = 9.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TopSpeciesCard(topSpecies: List<SpeciesData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 12.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(Surface, RoundedCornerShape(18.dp))
            .border(0.5.dp, HairlineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Top species this week", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Text("478 DETECTIONS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, fontFamily = FontFamily.Monospace)
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (topSpecies.isEmpty()) {
                Text("No data available", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                topSpecies.forEachIndexed { index, species ->
                    val gradient = when (index) {
                        0 -> listOf(Brand, BrandSecondary)
                        1 -> listOf(Violet2, Color(0xFF7B79DE))
                        else -> listOf(AiAccent, Color(0xFFC57BE6))
                    }
                    SpeciesBarRow(species.name, species.ratio, species.formattedPercentage, gradient, true)
                }
            }
        }
    }
}

@Composable
fun SpeciesBarRow(name: String, ratio: Float, value: String, gradient: List<Color>, isItalic: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name
        Text(
            text = name,
            color = Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            modifier = Modifier.width(100.dp),
            letterSpacing = (-0.1).sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        
        // Track
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color(0x1F787880), RoundedCornerShape(100.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(gradient), RoundedCornerShape(100.dp))
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Value
        Text(
            text = value,
            color = Ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PendingReviewActionCard(pendingCount: Int) {
    if (pendingCount <= 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Warning.copy(alpha = 0.08f))
            .background(Surface, RoundedCornerShape(18.dp))
            .border(0.5.dp, Warning.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon tile
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Warning.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = Warning, modifier = Modifier.size(22.dp))
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Body
        Column(modifier = Modifier.weight(1f)) {
            Text("$pendingCount frames awaiting review", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text("From recent active sessions", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Button
        Box(
            modifier = Modifier
                .background(Brand, RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text("Review", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    AgarthaVisionTheme {
        DashboardScreen()
    }
}
