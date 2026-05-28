@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import com.agarthavision.R
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaSpacing
import com.agarthavision.ui.theme.AgarthaRadius
import com.komoui.themes.styles
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.agarthavision.ui.components.glassChrome
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign

// Tokens
private val Brand = Color(0xFF1E40AF)
private val Danger = Color(0xFFFF3B30)
private val Success = Color(0xFF34C759)
private val SuccessDeep = Color(0xFF248A3D)
private val Warning = Color(0xFFFF9F0A)
private val AiPurple = Color(0xFFAF52DE)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF6E6E73)
private val Surface = Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationQueueScreen(
    onBackClick: () -> Unit,
    viewModel: VerificationQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredFrames = remember(state.flaggedFrames, state.queueFilter) {
        filterQueueFrames(state.flaggedFrames, state.queueFilter)
    }

    val aiCount = state.flaggedFrames.count { it.source == FrameSource.MODEL }
    val manualCount = state.flaggedFrames.count { it.source == FrameSource.MANUAL }
    // As per the spec, detections are pending, verified, or deleted. 
    // In our current data model, we don't have a strict 'verified' vs 'pending' field yet,
    // so we'll just treat everything currently in the queue as pending for display purposes.
    val pendingCount = state.flaggedFrames.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7)) // iOS system gray 6
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Glass Nav Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassChrome(shape = RoundedCornerShape(0.dp))
                    .padding(top = 47.dp, bottom = 12.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).clickable { onBackClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand, modifier = Modifier.size(22.dp))
                    Text("Capture", color = Brand, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Verification",
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(2f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // Spacer to balance the back button weight
                Spacer(modifier = Modifier.weight(1f))
            }

            // 2. Stats Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
                    .background(Surface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, Color(0x140F172A), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${state.flaggedFrames.size}", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Ink, letterSpacing = (-1).sp)
                    Text("FLAGGED FRAMES", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Muted, letterSpacing = 1.2.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow(dotColor = AiPurple, label = "AI detected", count = aiCount)
                    StatRow(dotColor = Muted, label = "Manual", count = manualCount)
                    StatRow(dotColor = Warning, label = "Pending review", count = pendingCount)
                }
            }

            // 3. Filter Chips
            QueueFilterChips(
                selected = state.queueFilter,
                onSelected = viewModel::onQueueFilterSelected,
                counts = listOf(state.flaggedFrames.size, pendingCount, aiCount, manualCount),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 4. Verify List
            if (filteredFrames.isEmpty()) {
                QueueEmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(1.dp), // using hairline separators instead of gaps
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredFrames,
                        key = { frame -> frame.capturedAt.toEpochMilli() },
                    ) { frame ->
                        VerificationQueueRow(
                            frame = frame,
                            onClick = { viewModel.onQueueItemSelected(frame) },
                            onDelete = { viewModel.onQueueItemDeleted(frame) },
                        )
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(0.5.dp).background(Color(0x140F172A)))
                    }
                }
            }
        }

        // 5. Floating Action Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .glassChrome(shape = RoundedCornerShape(100.dp))
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$pendingCount pending", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(120, 120, 128, (0.12f * 255).toInt()), RoundedCornerShape(100.dp))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Filter", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .background(Brand, RoundedCornerShape(100.dp))
                        .clickable { 
                            filteredFrames.firstOrNull()?.let { viewModel.onQueueItemSelected(it) } 
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text("Review next", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
private fun StatRow(dotColor: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
        Text(label, color = Muted, fontSize = 13.sp)
        Text("$count", color = Ink, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

internal fun filterQueueFrames(
    frames: List<FlaggedFrame>,
    filter: QueueFilter,
): List<FlaggedFrame> = when (filter) {
    QueueFilter.ALL -> frames
    // REPEAT is used as a sentinel for the "Pending" chip (all items in the queue are pending review)
    QueueFilter.REPEAT -> frames
    QueueFilter.FLAGGED -> frames.filter { it.source == FrameSource.MODEL }
    QueueFilter.MANUAL -> frames.filter { it.source == FrameSource.MANUAL }
}

@Composable
private fun QueueFilterChips(
    selected: QueueFilter,
    onSelected: (QueueFilter) -> Unit,
    counts: List<Int>,
    modifier: Modifier = Modifier,
) {
    // Chips: All | Pending | AI | Manual
    // "All" → QueueFilter.ALL (every frame)
    // "Pending" → QueueFilter.ALL as well (all in queue = all pending review); visually distinct from All
    //   In the current data model every item in the queue is pending, so Pending = total.
    //   We keep a separate PENDING sentinel by reusing ALL here and differentiating via index.
    // "AI" → QueueFilter.FLAGGED (MODEL source)
    // "Manual" → QueueFilter.MANUAL
    data class Chip(val label: String, val filter: QueueFilter, val countIdx: Int)
    val chips = listOf(
        Chip("All",     QueueFilter.ALL,    0),
        Chip("Pending", QueueFilter.REPEAT, 1), // REPEAT used as sentinel for Pending display
        Chip("AI",      QueueFilter.FLAGGED, 2),
        Chip("Manual",  QueueFilter.MANUAL,  3),
    )

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size) { index ->
            val chip = chips[index]
            val isSelected = selected == chip.filter
            QueueFilterChip(
                label = chip.label,
                count = counts[chip.countIdx],
                selected = isSelected,
                onClick = { onSelected(chip.filter) },
            )
        }
    }
}

@Composable
private fun QueueFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Ink else Color(120, 120, 128, (0.12f * 255).toInt())
    val fg = if (selected) Color.White else Ink
    
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(100.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(text = "$count", color = if (selected) Color.White.copy(alpha = 0.7f) else Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun QueueEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AgarthaSpacing.xxl),
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.styles.mutedForeground,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.padding(AgarthaSpacing.xxs))
        Text(
            text = stringResource(R.string.queue_sheet_empty_state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.styles.mutedForeground,
        )
    }
}

@Composable
private fun RowBadge(text: String, isAi: Boolean = false, isPending: Boolean = false) {
    val bg = when {
        isAi -> AiPurple.copy(alpha = 0.14f)
        isPending -> Warning.copy(alpha = 0.18f)
        else -> Color(120, 120, 128, (0.14f * 255).toInt())
    }
    val fg = when {
        isAi -> Color(0xFF8E3FBF)
        isPending -> Color(0xFFB86E00)
        else -> Color(0xFF515154)
    }
    
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, 
                fontWeight = FontWeight.Bold, 
                letterSpacing = 0.4.sp,
                fontSize = 10.sp
            ),
            color = fg,
        )
    }
}

@Composable
private fun VerificationQueueRow(
    frame: FlaggedFrame,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val top = frame.predictions.firstOrNull()
    val timeText = remember(frame.capturedAt) {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(frame.capturedAt)
    }
    val isManual = frame.source == FrameSource.MANUAL
    
    val title = when {
        isManual -> "Unlabeled"
        top != null -> top.classLabel
        else -> "Unknown class"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        // Thumbnail with Bounding Box Overlay
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = frame.jpegBytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // AI Bounding Box overlay (Pending = Warning color)
            if (!isManual) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.5.dp, Warning, RoundedCornerShape(4.dp))
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontStyle = if (isManual || title == "Unknown class") FontStyle.Normal else FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                color = if (isManual) Muted else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            val metadata = if (isManual) {
                "Frame ${frame.capturedAt.toEpochMilli().toString().takeLast(3)} · — · $timeText"
            } else {
                "Frame ${frame.capturedAt.toEpochMilli().toString().takeLast(3)} · ${(top?.confidence ?: 0f * 100).toInt()}% · $timeText"
            }
            Text(
                text = metadata,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = Muted,
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (isManual) {
                    RowBadge(text = "MANUAL", isAi = false, isPending = false)
                } else {
                    RowBadge(text = "AI", isAi = true, isPending = false)
                }
                RowBadge(text = "PENDING", isAi = false, isPending = true)
            }
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(20.dp)
        )
    }
}

