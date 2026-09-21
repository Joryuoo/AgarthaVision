@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Inbox
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.agarthavision.R
import com.agarthavision.domain.model.FrameSource
import com.agarthavision.domain.model.QueueSample
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val InterBaseStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "\"cv11\", \"ss01\", \"ss03\""
)
private val InterTabularStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "\"cv11\", \"ss01\", \"ss03\", \"tnum\""
)

/**
 * The sample's label: the time its shutter tap landed, to the second.
 *
 * Seconds are not decoration. A medtech works through a smear faster than a minute a field, so
 * `HH:mm` would give several rows the same name.
 */
private val CapturedAtFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun VerificationQueueScreen(
    onBackClick: () -> Unit,
    onSampleDetailClick: (String) -> Unit,
    onGoToRecords: (String) -> Unit,
    viewModel: VerificationQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            // App Bar. Swaps to a contextual bar while a selection is live, so the delete
            // affordance is only ever reachable with something selected.
            Row(
                // Matches ScreenTopBar (the verification sheet's header) so the back arrow and
                // title hold their position as you move queue -> sheet -> record.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isSelecting) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.queue_selection_clear),
                        tint = colors.textPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.onClearSelection() }
                            .padding(4.dp)
                            .size(24.dp),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        pluralStringResource(
                            R.plurals.queue_selection_count,
                            state.selectedIds.size,
                            state.selectedIds.size,
                        ),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        style = InterBaseStyle
                    )

                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.queue_selection_delete),
                        tint = colors.danger,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.onDeleteRequested() }
                            .padding(4.dp)
                            .size(24.dp),
                    )
                } else {
                    BackArrow(onBack = onBackClick)

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.verify_queue_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = (-0.44).sp,
                            style = InterBaseStyle
                        )
                        // One number, because there is one list. The old subtitle gave a total
                        // and an unverified count, which only differed while the queue held
                        // verified rows as well.
                        Text(
                            pluralStringResource(
                                R.plurals.verify_queue_subtitle,
                                state.samples.size,
                                state.samples.size,
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            style = InterTabularStyle
                        )
                    }
                }
            }

            // No filter chips. The queue is one flat list of work still to do, so there is
            // nothing to filter between - and a single chip that is always selected is a
            // control that cannot do anything.

            // Frame list, or why it is empty
            if (state.samples.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    QueueEmptyState(
                        variant = queueEmptyVariant(state.verifiedInSession),
                        onViewRecords = { state.sessionId?.let(onGoToRecords) },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 24px bottom for home indicator
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = state.samples,
                        // Keyed by primary key, never by capturedAt: two frames captured in the
                        // same millisecond once threw a duplicate-key exception.
                        key = { sample -> sample.sampleId }
                    ) { sample ->
                        FrameRow(
                            sample = sample,
                            isSelecting = state.isSelecting,
                            isSelected = sample.sampleId in state.selectedIds,
                            onClick = {
                                if (state.isSelecting) {
                                    viewModel.onToggleSelected(sample)
                                } else {
                                    viewModel.onQueueItemSelected(sample)
                                }
                            },
                            onLongClick = { viewModel.onToggleSelected(sample) },
                        )
                    }
                }
            }
        }
    }

    // System back leaves the selection before it leaves the screen.
    BackHandler(enabled = state.isSelecting) { viewModel.onClearSelection() }

    if (state.showDeleteConfirm) {
        DeleteSamplesConfirmDialog(
            // Zero by construction, not by coincidence: this queue holds unverified rows only,
            // so a selection made here cannot contain a verified sample. The dialog keeps its
            // verified branch for the Records screen's Samples tab, which is where a verified
            // sample is deleted and where the warning about the retained corpus belongs.
            verifiedCount = 0,
            unverifiedCount = state.selectedIds.size,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }

    val target = state.verificationTarget
    if (target != null) {
        // One screen for both sources. What makes a sample "AI" is simply that it has model
        // output, which the sheet reads off the frame itself.
        VerificationSheet(
            frame = target,
            onDismiss = viewModel::onVerificationDismissed,
            prior = state.priorTarget,
        )
    }
}

/**
 * Body shown when there is nothing left to verify.
 *
 * Two cases, not three. They look identical from the list's point of view — zero unverified rows
 * either way — but mean opposite things to the medtech, so each gets its own copy and only the
 * all-done case offers a way onward, into the session's records.
 */
@Composable
internal fun QueueEmptyState(
    variant: QueueEmptyVariant,
    onViewRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val padded = modifier.padding(horizontal = 32.dp)
    when (variant) {
        QueueEmptyVariant.NEVER_HAD -> EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.verify_queue_empty_title),
            body = stringResource(R.string.verify_queue_empty_body),
            modifier = padded,
        )
        QueueEmptyVariant.ALL_DONE -> EmptyState(
            icon = AgarthaIcons.Verified,
            title = stringResource(R.string.verify_queue_done_title),
            body = stringResource(R.string.verify_queue_done_body),
            modifier = padded,
        ) {
            Button(
                onClick = onViewRecords,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(R.string.verify_queue_view_records),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = InterBaseStyle,
                )
            }
        }
    }
}

@Composable
private fun FrameRow(
    sample: QueueSample,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val isAI = sample.source == FrameSource.MODEL

    // The row leads with the capture time, which is the sample's label on the Verification
    // Screen too - the same name in both places, so the row you tapped is the screen you land
    // on. It replaces a title that read "Awaiting review" on every row: true, and therefore
    // useless for telling two rows apart.
    val title = remember(sample.capturedAt) {
        sample.capturedAt.atZone(ZoneId.systemDefault()).format(CapturedAtFormat)
    }

    val elapsed = Duration.between(sample.capturedAt, Instant.now())
    val timeStr = when {
        elapsed.toHours() > 0 -> stringResource(R.string.queue_row_elapsed_hours, elapsed.toHours())
        elapsed.toMinutes() > 0 ->
            stringResource(R.string.queue_row_elapsed_minutes, elapsed.toMinutes())
        else -> stringResource(R.string.queue_row_elapsed_seconds, elapsed.seconds)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.accentTint else colors.surface)
            .border(
                1.dp,
                if (isSelected) colors.accent else colors.border,
                RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
            // Loaded from the path, not from bytes held in the row. The shape this replaced
            // re-read every full-resolution JPEG from disk on every emission - survivable while
            // the queue drained, an OOM risk once it grows. Coil caches by path, so a visible
            // row decodes once.
            AsyncImage(
                model = File(sample.imagePath),
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
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = InterTabularStyle
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // One badge, not two. Every row here is unverified, so the "Pending" pill said
                // nothing; what does still vary is whether the model ever saw this frame, and a
                // Manual row is the one the medtech has to annotate from scratch through Add Egg.
                Box(
                    modifier = Modifier
                        .background(if (isAI) colors.accentTint else colors.warningTint, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        stringResource(
                            if (isAI) R.string.badge_ai_suggested else R.string.badge_manual,
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAI) colors.accent else colors.warningText,
                        style = InterBaseStyle,
                    )
                }
                Text("·", fontSize = 12.sp, color = colors.textTertiary, style = InterBaseStyle)
                Text(timeStr, fontSize = 12.sp, color = colors.textSecondary, style = InterTabularStyle)
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (isSelecting) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected) colors.accent else colors.textTertiary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
