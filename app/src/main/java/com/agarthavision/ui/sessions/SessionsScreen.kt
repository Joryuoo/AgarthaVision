package com.agarthavision.ui.sessions

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.SvgIcon
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.navigation.Screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.ui.text.style.TextOverflow
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.ScreenHeader
import com.agarthavision.ui.components.SheetInput
import com.agarthavision.ui.components.SheetInputConfig
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    onNavigateToCapture: (String) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = AgarthaTheme.colors

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionsEvent.NavigateToCapture -> onNavigateToCapture(event.sessionId)
                is SessionsEvent.ShareExport -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, event.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .align(Alignment.TopCenter)
            ) {
                // App Bar. Counts come from the repository query, not from the loaded page:
                // the list is paginated, so summing what is in `state.sessions` would report
                // only what had been scrolled into view. Sessions do not end any more, so the
                // count is of frames awaiting review rather than of open sessions - the
                // latter would have counted every session and said nothing.
                AppBar(
                    unverifiedCount = state.unverifiedCount,
                    totalCount = state.totalCount,
                    onBack = onBack,
                )

                // Search + date filter row
                SearchInput(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = stringResource(R.string.sessions_search_placeholder),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                DateRangeFilterBar(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    onRangeSelected = viewModel::onDateRangeSelected,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )

                // Sessions List with load-more pagination
                val listState = rememberLazyListState()
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisible >= listState.layoutInfo.totalItemsCount - 1 && state.canLoadMore
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) viewModel.onLoadMore()
                }

                // Sessions List
                when {
                    state.isLoading -> Spacer(Modifier.weight(1f))
                    state.sessions.isEmpty() -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Inbox,
                            title = stringResource(R.string.sessions_empty_title),
                            body = stringResource(R.string.sessions_empty_body),
                        )
                    }
                    else ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.sessions, key = { it.session.id }) { sessionData ->
                            SessionCard(
                                sessionData = sessionData,
                                isActive = sessionData.session.id == state.activeSessionId,
                                actions = SessionCardActions(
                                    // Every row opens Capture. There is no second
                                    // destination to branch to: a session does not end, so
                                    // the medtech is always going back to the smear to
                                    // capture or correct a frame. Session Detail is reached
                                    // from Records, which is where reading a finished
                                    // session belongs.
                                    onClick = { viewModel.onResumeSession(sessionData.session.id) },
                                )
                            )
                        }
                        if (state.canLoadMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = AgarthaTheme.colors.accent,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Sticky bottom CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(49.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent
                        )
                    ) {
                        SvgIcon(
                            "M12 5v14M5 12h14",
                            strokeWidth = 2.2f,
                            color = colors.onAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

    if (showCreateDialog) {
        NewSessionSheet(
            // Leaving the sheet abandons the draft; the label is local `remember` state and
            // resets with it. There is nothing left in the ViewModel to reset — the barangay
            // moved to the patient and the note is gone.
            onDismiss = { showCreateDialog = false },
            onSubmit = { label ->
                viewModel.onCreateSession(label)
                showCreateDialog = false
            }
        )
    }
}

/**
 * This screen is a drill-down now, so it carries a back arrow.
 *
 * It used to be a root tab, where the bottom bar was the way out. It is registered at
 * `patients/{patientId}`, which is not in `bottomBarRoutes`, so without this the only way
 * back is the system gesture — and a screen reachable only by gesture reads as a dead end.
 *
 * [ScreenHeader] is deliberately not given a leading slot: its doc scopes it to the root
 * tabs, and four screens share it. The arrow sits beside it instead, matching
 * `SessionDetailScreen`'s top bar.
 */
@Composable
private fun AppBar(unverifiedCount: Int, totalCount: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackArrow(onBack = onBack, modifier = Modifier.padding(start = Spacing.xs))
        ScreenHeader(
            title = stringResource(R.string.sessions_title),
            purpose = stringResource(R.string.sessions_subtitle_purpose),
            status = pluralStringResource(
                R.plurals.sessions_subtitle,
                totalCount,
                totalCount,
                unverifiedCount,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Callbacks [SessionCard] (and its hoisted [KebabMenu]) dispatch back to the caller. */
private data class SessionCardActions(
    val onClick: () -> Unit,
)

internal enum class SessionQueueBadge { NO_ITEMS, ALL_VERIFIED, PENDING }

internal fun sessionQueueBadge(totalSamples: Int, unverified: Int): SessionQueueBadge = when {
    totalSamples == 0 -> SessionQueueBadge.NO_ITEMS
    unverified == 0 -> SessionQueueBadge.ALL_VERIFIED
    else -> SessionQueueBadge.PENDING
}

@Composable
private fun SessionCard(
    sessionData: SessionWithStats,
    isActive: Boolean,
    actions: SessionCardActions
) {
    val colors = AgarthaTheme.colors
    val session = sessionData.session
    val date = formatDate(session.startedAt)
    val time = formatTime(session.startedAt)
    // Date and time only. The note that used to tail this line was an ad-hoc patient
    // identifier; the patient is a record of its own now and the column is gone.
    val meta = "$date · $time"

    val (bgColor, borderColor) = if (isActive) {
        colors.accentTint2 to colors.accentTint
    } else {
        colors.surface to colors.border
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { actions.onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.label ?: "Session ${session.id.take(8)}",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                letterSpacing = (-0.015).em
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = meta,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isActive) {
                // Frames still to review, repeats excluded — the same count that blocks
                // ending the session, so this row and that dialog always agree.
                val unverified = sessionData.unverifiedSamples
                val queueBadge = sessionQueueBadge(sessionData.totalSamples, unverified)
                val hasPending = unverified > 0
                val (badgeBg, badgeTextColor) = if (hasPending) {
                    colors.accent to colors.onAccent
                } else {
                    colors.surfaceMuted to colors.textSecondary
                }
                Row(
                    modifier = Modifier
                        .background(badgeBg, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (hasPending) LiveDot()
                    Text(
                        text = when (queueBadge) {
                            SessionQueueBadge.NO_ITEMS ->
                                stringResource(R.string.session_no_items_yet)
                            SessionQueueBadge.ALL_VERIFIED ->
                                stringResource(R.string.session_all_verified)
                            SessionQueueBadge.PENDING ->
                                pluralStringResource(
                                    R.plurals.session_unverified_count,
                                    unverified,
                                    unverified,
                                )
                        },
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

            } else {
                val eggs = sessionData.totalEggs
                val badgeBg = if (eggs > 0) colors.successTint else colors.surfaceMuted
                val badgeColor = if (eggs > 0) colors.successText else colors.textSecondary
                Box(
                    modifier = Modifier
                        .background(badgeBg, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text("$eggs eggs", color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun LiveDot() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(AgarthaTheme.colors.onAccent.copy(alpha = alpha), CircleShape)
    )
}

/**
 * The New Session sheet: a label, and nothing else.
 *
 * It used to collect a barangay and a note as well. The barangay moved to the patient — it is
 * the unit surveillance aggregates on and what the admin site's geospatial mapping tracks, and
 * it does not change from one smear to the next. The note was an ad-hoc patient identifier
 * that the patient record now carries properly. The patient itself comes from the route this
 * screen is reached at, not from the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    onDismiss: () -> Unit,
    onSubmit: (label: String) -> Unit
) {
    val colors = AgarthaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.borderStrong, RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        var label by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "New session",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.015).em
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.session_new_sheet_subtitle),
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.surfaceMuted, CircleShape)
                ) {
                    SvgIcon(
                        "M18 6L6 18M6 6l12 12",
                        color = colors.textSecondary,
                        strokeWidth = 2f,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                SheetInput(
                    value = label,
                    onValueChange = { label = it; showError = false },
                    config = SheetInputConfig(
                        label = "Label",
                        placeholder = "e.g. 325",
                        isError = showError && label.isBlank(),
                        maxLength = SESSION_LABEL_MAX_LENGTH
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (showError && label.isBlank()) {
                    val bannerDanger = colors.danger
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.dangerTint, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.danger.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SvgIcon(
                            "M12 8v4M12 16h.01",
                            drawExtras = {
                                drawCircle(
                                    bannerDanger,
                                    radius = 9f,
                                    center = Offset(12f, 12f),
                                    style = Stroke(width = 1.8f)
                                )
                            },
                            color = colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            stringResource(R.string.session_new_label_required),
                            fontSize = 12.sp,
                            color = colors.dangerText,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    val bannerAccent = colors.accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.accentTint2, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SvgIcon(
                            "M12 8v4M12 16h.01",
                            drawExtras = {
                                drawCircle(
                                    bannerAccent,
                                    radius = 9f,
                                    center = Offset(12f, 12f),
                                    style = Stroke(width = 1.8f)
                                )
                            },
                            color = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "A session label is required by lab protocol. You can edit it later from Session Detail.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceMuted,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        if (label.isBlank()) showError = true else onSubmit(label)
                    },
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    Text("Start session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    SvgIcon(
                        "M5 12h14M13 5l7 7-7 7",
                        color = colors.onAccent,
                        strokeWidth = 2.2f,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
