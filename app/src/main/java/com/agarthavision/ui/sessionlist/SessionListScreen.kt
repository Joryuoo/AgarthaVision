package com.agarthavision.ui.sessionlist

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.ui.components.BackArrow
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.dashboard.relativeTimeText
import com.agarthavision.ui.icons.AgarthaIcons
import com.agarthavision.ui.icons.Science
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.sessions.sessionQueueBadge
import com.agarthavision.ui.sessions.SessionQueueBadge
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.Spacing

fun sessionListTitle(filter: SessionListFilter): String = when (filter) {
    SessionListFilter.TO_REVIEW -> "Frames to review"
    SessionListFilter.NO_FRAMES -> "Sessions with no frames"
    SessionListFilter.EXAMINED -> "Examined smears"
    SessionListFilter.POSITIVE -> "Positive smears"
    SessionListFilter.ALL -> "Recent sessions"
}

fun sessionListSubtitle(filter: SessionListFilter, period: HomePeriod?): String {
    val periodText = when (period) {
        HomePeriod.TODAY -> "today"
        HomePeriod.LAST_7_DAYS -> "the past 7 days"
        HomePeriod.LAST_30_DAYS -> "the past 30 days"
        null -> null
    }
    return when {
        periodText != null -> "Testing sessions from $periodText"
        filter == SessionListFilter.TO_REVIEW -> "Unverified frames awaiting review"
        filter == SessionListFilter.NO_FRAMES -> "Testing sessions without captured frames"
        else -> "Testing sessions from the past 7 days"
    }
}

@Composable
private fun SessionListAppBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(start = Spacing.xs, end = Spacing.sm, top = 14.dp, bottom = 12.dp),
    ) {
        BackArrow(onBack = onBack)
        Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SessionListScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionListEvent.NavigateToDetail -> {
                    onNavigate(Screen.SessionDetail.createRoute(event.sessionId))
                }
                is SessionListEvent.NavigateToVerificationQueue -> {
                    onNavigate(Screen.VerificationQueue.route)
                }
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.sessions.size - 2
        }
    }

    LaunchedEffect(shouldLoadMore, state.canLoadMore) {
        if (shouldLoadMore && state.canLoadMore) {
            viewModel.onLoadMore()
        }
    }

    val colors = AgarthaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SessionListAppBar(
            title = sessionListTitle(state.filter),
            subtitle = sessionListSubtitle(state.filter, state.period),
            onBack = onBack,
        )

        if (state.isLoading && state.sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (state.sessions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = AgarthaIcons.Science,
                    title = "No sessions found",
                    body = "There are no sessions matching this filter.",
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.sessions, key = { it.session.id }) { item ->
                    SessionSummaryRowItem(
                        sessionSummary = item,
                        filter = state.filter,
                        onClick = { viewModel.onSessionClick(item) },
                    )
                }

                if (state.canLoadMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryRowItem(
    sessionSummary: SessionSummary,
    filter: SessionListFilter,
    onClick: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    val session = sessionSummary.session
    val label = session.label ?: "Session ${session.id.take(8)}"
    val queueBadge = sessionQueueBadge(sessionSummary.totalFrames, sessionSummary.framesToReview)
    val now = remember { System.currentTimeMillis() }
    val relativeTime = relativeTimeText(sessionSummary.lastActivityAt, now)

    val badgeText = when (queueBadge) {
        SessionQueueBadge.NO_ITEMS -> "No frames"
        SessionQueueBadge.ALL_VERIFIED -> "Verified"
        SessionQueueBadge.PENDING -> "To review"
    }

    val (badgeBg, badgeColor) = when (queueBadge) {
        SessionQueueBadge.NO_ITEMS -> colors.surfaceMuted to colors.textSecondary
        SessionQueueBadge.ALL_VERIFIED -> colors.surfaceMuted to colors.textSecondary
        SessionQueueBadge.PENDING -> colors.accent to colors.onAccent
    }

    val destHint = if (filter == SessionListFilter.TO_REVIEW) {
        "Opens verification queue."
    } else {
        "Opens session details."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label, ${sessionSummary.patient.maskedDisplayName}, $badgeText. $destHint"
            }
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sessionSummary.patient.maskedDisplayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            val frameCountText = pluralStringResource(
                R.plurals.dashboard_frame_count,
                sessionSummary.totalFrames,
                sessionSummary.totalFrames,
            )
            Text(
                text = "$frameCountText · $relativeTime",
                fontSize = 11.sp,
                color = colors.textSecondary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val showPositive = sessionSummary.isPositive &&
                (filter == SessionListFilter.EXAMINED || filter == SessionListFilter.POSITIVE)
            if (showPositive) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Positive",
                        color = colors.onAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
