package com.agarthavision.ui.sessions

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agarthavision.ui.components.SearchableDropdown
import com.agarthavision.ui.components.SearchableDropdownActions
import com.agarthavision.ui.components.SearchableDropdownConfig
import com.agarthavision.ui.components.SearchableDropdownState
import com.agarthavision.ui.components.SearchableOption
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.SessionLinkState
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.ui.navigation.Screen
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SessionsScreen(
    onNavigate: (String) -> Unit = {},
    onNavigateToCapture: (String) -> Unit,
    onSessionSelected: (String) -> Unit,
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
                // App Bar
                val activeCount = state.sessions.count { it.session.endedAt == null }
                AppBar(activeCount = activeCount, totalCount = state.sessions.size)

                // Sessions List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sessions, key = { it.session.id }) { sessionData ->
                        SessionCard(
                            sessionData = sessionData,
                            isActive = sessionData.session.endedAt == null,
                            actions = SessionCardActions(
                                onClick = {
                                    if (sessionData.session.endedAt == null) {
                                        viewModel.onResumeSession(sessionData.session.id)
                                    } else {
                                        onSessionSelected(sessionData.session.id)
                                    }
                                },
                            )
                        )
                    }
                }

                // Sticky bottom CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .border(1.dp, colors.border) // Top hairline
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
            barangay = BarangayPickerState(
                selected = state.selectedBarangay,
                query = state.barangayQuery,
                results = state.barangayResults,
                actions = SearchableDropdownActions(
                    onQueryChange = viewModel::onBarangayQueryChanged,
                    onSelect = { option -> viewModel.onBarangaySelected(option.key) },
                    onClear = viewModel::onBarangayCleared,
                ),
            ),
            onDismiss = {
                // Leaving the sheet abandons the whole draft, so the picker resets too —
                // label and note are local `remember` state and reset with it.
                viewModel.onBarangayCleared()
                showCreateDialog = false
            },
            onSubmit = { label, note ->
                viewModel.onCreateSession(label, note)
                showCreateDialog = false
            }
        )
    }

    // KebabMenu is now hoisted into SessionCard
}

@Composable
private fun AppBar(activeCount: Int, totalCount: Int) {
    val colors = AgarthaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sessions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = (-0.02).em
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$totalCount sessions · $activeCount active",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
        }
    }
}

/** Callbacks [SessionCard] (and its hoisted [KebabMenu]) dispatch back to the caller. */
private data class SessionCardActions(
    val onClick: () -> Unit,
)

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
    val meta = if (session.notes.isNullOrBlank()) {
        "$date · $time"
    } else {
        "$date · $time · ${session.notes}"
    }

    val bgColor = if (isActive) colors.accentTint2 else colors.surface
    val borderColor = if (isActive) colors.accentTint else colors.border
    // Per ADR-007: unowned or opted-out sessions show a neutral "Not linked" badge
    // regardless of active/ended state — local-only is a neutral state, not a warning.
    val linkState = session.linkState
    val showNotLinkedBadge = linkState == SessionLinkState.UNOWNED || linkState == SessionLinkState.NOT_LINKED

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
                color = colors.accent.copy(alpha = 0.7f)
            )
            if (showNotLinkedBadge) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(colors.surfaceMuted, CircleShape)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.session_not_linked),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isActive) {
                // Frames still to review, repeats excluded — the same count that blocks
                // ending the session, so this row and that dialog always agree.
                val unverified = sessionData.unverifiedSamples
                Row(
                    modifier = Modifier
                        .background(
                            if (unverified > 0) colors.accent else colors.surfaceMuted,
                            CircleShape,
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (unverified > 0) LiveDot()
                    Text(
                        // English has no `zero` plural, so 0 needs its own string.
                        text = if (unverified == 0) {
                            stringResource(R.string.session_all_verified)
                        } else {
                            pluralStringResource(
                                R.plurals.session_unverified_count,
                                unverified,
                                unverified,
                            )
                        },
                        color = if (unverified > 0) colors.onAccent else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

            } else {
                val eggs = sessionData.totalEpg
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


/** The barangay picker's slice of [SessionsState], hoisted into [NewSessionSheet]. */
private data class BarangayPickerState(
    val selected: PsgcBarangay?,
    val query: String,
    val results: List<PsgcBarangay>,
    val actions: SearchableDropdownActions,
)

private fun PsgcBarangay.toOption(): SearchableOption =
    SearchableOption(key = code, title = name, subtitle = parentPath)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    barangay: BarangayPickerState,
    onDismiss: () -> Unit,
    onSubmit: (label: String, note: String) -> Unit
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
        var note by remember { mutableStateOf("") }
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
                        isError = showError && label.isBlank()
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))
                SearchableDropdown(
                    state = SearchableDropdownState(
                        selected = barangay.selected?.toOption(),
                        query = barangay.query,
                        options = barangay.results.map { it.toOption() },
                    ),
                    config = SearchableDropdownConfig(
                        label = stringResource(R.string.session_new_barangay_label),
                        placeholder = stringResource(R.string.session_new_barangay_placeholder),
                        hint = stringResource(
                            R.string.session_new_barangay_hint,
                            SearchBarangaysUseCase.MIN_QUERY_LENGTH,
                        ),
                        noMatches = stringResource(R.string.session_new_barangay_no_matches),
                        clearLabel = stringResource(R.string.session_new_barangay_clear),
                        minQueryLength = SearchBarangaysUseCase.MIN_QUERY_LENGTH,
                        badge = stringResource(R.string.session_new_required_badge),
                        isError = showError && barangay.selected == null,
                    ),
                    actions = SearchableDropdownActions(
                        onQueryChange = {
                            showError = false
                            barangay.actions.onQueryChange(it)
                        },
                        onSelect = {
                            showError = false
                            barangay.actions.onSelect(it)
                        },
                        onClear = barangay.actions.onClear,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                SheetInput(
                    value = note,
                    onValueChange = { note = it; showError = false },
                    config = SheetInputConfig(
                        label = "Note",
                        placeholder = "Patient ID, clinical context, sample details...",
                        isError = false, // Note is never in error since it's optional
                        isTextArea = true,
                        isRequired = false
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                val missingLabel = showError && label.isBlank()
                val missingBarangay = showError && barangay.selected == null
                if (missingLabel || missingBarangay) {
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
                            if (label.isBlank()) {
                                stringResource(R.string.session_new_label_required)
                            } else {
                                stringResource(R.string.session_new_barangay_required)
                            },
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
                        if (label.isBlank() || barangay.selected == null) {
                            showError = true
                        } else {
                            onSubmit(label, note)
                        }
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

/** Static config for [SheetInput], separate from its stateful (value, onValueChange) pair. */
private data class SheetInputConfig(
    val label: String,
    val placeholder: String,
    val isError: Boolean,
    val isTextArea: Boolean = false,
    val isRequired: Boolean = true
)

@Composable
private fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    config: SheetInputConfig
) {
    val colors = AgarthaTheme.colors
    val (label, placeholder, isError) = config
    val isTextArea = config.isTextArea
    val isRequired = config.isRequired
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            if (isRequired) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "REQUIRED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.em,
                    color = colors.dangerText,
                    modifier = Modifier
                        .background(colors.dangerTint, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "OPTIONAL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.em,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .background(colors.surfaceMuted, RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        val borderColor = if (isError) colors.danger else if (isFocused) colors.accent else colors.borderStrong
        val bgColor = if (isError) colors.dangerTint.copy(alpha = 0.5f) else colors.surface

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = colors.textPrimary),
            singleLine = !isTextArea,
            minLines = if (isTextArea) 3 else 1,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isTextArea) Modifier.heightIn(min = 88.dp) else Modifier)
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    contentAlignment = if (isTextArea) Alignment.TopStart else Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = colors.textTertiary, lineHeight = 21.75.sp)
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
