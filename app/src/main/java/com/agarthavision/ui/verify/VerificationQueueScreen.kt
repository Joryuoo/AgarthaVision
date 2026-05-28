@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.verify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.ui.capture.SvgIcon
import java.time.Duration
import java.time.Instant

// Design Tokens
private val White = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFF7F8FA)
private val Gray100 = Color(0xFFEEF0F4)
private val Gray200 = Color(0xFFE2E5EB)
private val Gray300 = Color(0xFFCBD0DA)
private val Gray400 = Color(0xFF9CA3AF)
private val Gray500 = Color(0xFF6B7280)
private val Gray700 = Color(0xFF374151)
private val Gray900 = Color(0xFF0F172A)
private val Blue = Color(0xFF1E3FD9)
private val Amber = Color(0xFFD97706)
private val AmberTint = Color(0xFFFEF3C7)
private val AmberText = Color(0xFF92400E)
private val Green = Color(0xFF16A34A)
private val GreenTint = Color(0xFFDCFCE7)
private val GreenText = Color(0xFF166534)
private val Red = Color(0xFFDC2626)
private val RedTint = Color(0xFFFEE2E2)
private val RedText = Color(0xFFDC2626)

private val InterBaseStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "\"cv11\", \"ss01\", \"ss03\""
)
private val InterTabularStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "\"cv11\", \"ss01\", \"ss03\", \"tnum\""
)

@Composable
fun VerificationQueueScreen(
    onBackClick: () -> Unit,
    onSampleDetailClick: (String) -> Unit,
    viewModel: VerificationQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val filteredFrames = remember(state.flaggedFrames, state.queueFilter) {
        state.flaggedFrames.filter { frame ->
            val type = if (frame.source == FrameSource.MODEL) "ai" else "manual"
            // Note: Repeat is a mock condition since FlaggedFrame doesn't have is_repeat yet, 
            // but we'll include the filter for the UI structure.
            
            when (state.queueFilter) {
                QueueFilter.ALL -> true
                QueueFilter.FLAGGED -> type == "ai"
                QueueFilter.MANUAL -> type == "manual"
                QueueFilter.REPEAT -> false // No repeats mapped yet
            }
        }
    }

    val counts = remember(state.flaggedFrames) {
        mapOf(
            QueueFilter.ALL to state.flaggedFrames.size,
            QueueFilter.FLAGGED to state.flaggedFrames.count { it.source == FrameSource.MODEL },
            QueueFilter.MANUAL to state.flaggedFrames.count { it.source == FrameSource.MANUAL },
            QueueFilter.REPEAT to 0,
        )
    }

    val pendingCount = state.flaggedFrames.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Background outside the 480dp container
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .background(White)
                .systemBarsPadding()
        ) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    SvgIcon("M 15 18 L 9 12 L 15 6", color = Gray900, strokeWidth = 1.8f, modifier = Modifier.size(24.dp))
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Verify Queue", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900, letterSpacing = (-0.44).sp, style = InterBaseStyle)
                    Text("${state.flaggedFrames.size} items · $pendingCount pending", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray500, style = InterTabularStyle)
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .padding(bottom = 8.dp), // extra for shadow/scroll spacing
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    QueueFilter.ALL to "All",
                    QueueFilter.FLAGGED to "AI",
                    QueueFilter.MANUAL to "Manual",
                    QueueFilter.REPEAT to "Repeat"
                ).forEach { (filter, label) ->
                    val isSelected = state.queueFilter == filter
                    val count = counts[filter] ?: 0
                    
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Gray900 else White)
                            .border(1.dp, if (isSelected) Gray900 else Gray200, CircleShape)
                            .clickable { viewModel.onQueueFilterSelected(filter) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White else Gray700, style = InterBaseStyle)
                        Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isSelected) White.copy(alpha = 0.5f) else Gray400, style = InterTabularStyle)
                    }
                }
            }

            // Frame List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp), // 24px bottom for home indicator
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredFrames,
                    key = { frame -> frame.capturedAt.toEpochMilli() }
                ) { frame ->
                    FrameRow(
                        frame = frame,
                        onClick = { viewModel.onQueueItemSelected(frame) }
                    )
                }
            }
        }
    }

    val target = state.verificationTarget
    if (target != null) {
        if (target.source == FrameSource.MANUAL) {
            ManualSheet(
                frame = target,
                onDismiss = viewModel::onVerificationDismissed,
            )
        } else {
            VerificationSheet(
                frame = target,
                onDismiss = viewModel::onVerificationDismissed,
            )
        }
    }
}

@Composable
private fun FrameRow(
    frame: FlaggedFrame,
    onClick: () -> Unit
) {
    val isManual = frame.source == FrameSource.MANUAL
    val top = frame.predictions.firstOrNull()
    val isAI = !isManual
    
    val title = when {
        isManual -> "Manual capture"
        top != null -> top.classLabel
        else -> "Unknown class"
    }

    val confidence = if (isAI) "${(top?.confidence ?: 0f * 100).toInt()}%" else ""
    val isItalic = isAI && title != "Unknown class"

    val duration = Duration.between(frame.capturedAt, Instant.now())
    val timeStr = when {
        duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
        else -> "${duration.seconds}s ago"
    }
    
    val metaSource = if (isAI) "AI" else "Manual"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .border(1.dp, Gray100, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF0e1424), Color(0xFF060912)),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )
                    drawCircle(
                        color = Color(80, 60, 40, (0.6f * 255).toInt()),
                        radius = size.width * 0.5f,
                        center = Offset(size.width * 0.35f, size.height * 0.40f)
                    )
                    drawCircle(
                        color = Color(90, 70, 50, (0.4f * 255).toInt()),
                        radius = size.width * 0.5f,
                        center = Offset(size.width * 0.70f, size.height * 0.65f)
                    )
                }
        ) {
            AsyncImage(
                model = frame.jpegBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                    color = Gray900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = InterBaseStyle
                )
                if (isAI) {
                    Box(modifier = Modifier.background(Gray100, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(confidence, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray700, style = InterTabularStyle)
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(metaSource, fontSize = 12.sp, color = Gray500, style = InterBaseStyle)
                Text("·", fontSize = 12.sp, color = Gray300, style = InterBaseStyle)
                Text(timeStr, fontSize = 12.sp, color = Gray500, style = InterTabularStyle)
                Text("·", fontSize = 12.sp, color = Gray300, style = InterBaseStyle)
                
                Row(
                    modifier = Modifier.background(AmberTint, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Pending", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AmberText, letterSpacing = 0.1.sp, style = InterBaseStyle)
                }
            }
        }
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gray300, modifier = Modifier.size(24.dp))
    }
}
