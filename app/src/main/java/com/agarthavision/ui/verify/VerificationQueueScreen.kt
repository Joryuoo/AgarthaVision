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
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import java.time.Duration
import java.time.Instant

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
        filterQueueFrames(state.flaggedFrames, state.queueFilter)
    }

    // Counted through filterQueueFrames rather than a second copy of the predicates —
    // the duplication is how a chip's count and its list drift apart.
    val counts = remember(state.flaggedFrames) {
        QueueFilter.entries.associateWith { filter ->
            filterQueueFrames(state.flaggedFrames, filter).size
        }
    }

    val pendingCount = state.flaggedFrames.size
    val colors = AgarthaTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background), // Background outside the 480dp container
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .background(colors.background)
                .systemBarsPadding()
        ) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackArrow(onBack = onBackClick)

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Verify Queue",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.44).sp,
                        style = InterBaseStyle
                    )
                    Text(
                        "${state.flaggedFrames.size} items · $pendingCount pending",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        style = InterTabularStyle
                    )
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
                ).forEach { (filter, label) ->
                    val isSelected = state.queueFilter == filter
                    val count = counts[filter] ?: 0

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) colors.textPrimary else colors.surface)
                            .border(1.dp, if (isSelected) colors.textPrimary else colors.borderStrong, CircleShape)
                            .clickable { viewModel.onQueueFilterSelected(filter) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) colors.background else colors.textSecondary,
                            style = InterBaseStyle
                        )
                        Text(
                            "$count",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) {
                                colors.background.copy(alpha = 0.6f)
                            } else {
                                colors.textTertiary
                            },
                            style = InterTabularStyle
                        )
                    }
                }
            }

            // Frame List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 24px bottom for home indicator
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = filteredFrames,
                    key = { frame -> frame.sampleId }
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
        // One screen for both sources. What makes a sample "AI" is simply that it has model
        // output, which the sheet reads off the frame itself.
        VerificationSheet(
            frame = target,
            onDismiss = viewModel::onVerificationDismissed,
        )
    }
}

@Composable
private fun FrameRow(
    frame: FlaggedFrame,
    onClick: () -> Unit
) {
    val colors = AgarthaTheme.colors
    val isManual = frame.source == FrameSource.MANUAL
    val top = frame.predictions.firstOrNull()
    val isAI = !isManual

    val title = when {
        isManual -> "Manual capture"
        top != null -> top.classLabel
        else -> "Unknown class"
    }

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
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
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
                    drawRect(brush = AppColors.MicroscopeBrush)
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
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = InterBaseStyle
                )
                if (isAI) {
                    Box(
                        modifier = Modifier
                            .background(colors.accentTint, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                            style = InterTabularStyle,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(metaSource, fontSize = 12.sp, color = colors.textSecondary, style = InterBaseStyle)
                Text("·", fontSize = 12.sp, color = colors.textTertiary, style = InterBaseStyle)
                Text(timeStr, fontSize = 12.sp, color = colors.textSecondary, style = InterTabularStyle)
                Text("·", fontSize = 12.sp, color = colors.textTertiary, style = InterBaseStyle)

                Row(
                    modifier = Modifier
                        .background(colors.warningTint, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "Pending",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.warningText,
                        letterSpacing = 0.1.sp,
                        style = InterBaseStyle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(24.dp)
        )
    }
}
