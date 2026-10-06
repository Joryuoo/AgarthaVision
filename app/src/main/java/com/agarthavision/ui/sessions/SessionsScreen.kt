package com.agarthavision.ui.sessions

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agarthavision.ui.components.BackArrow
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.ui.components.DateRangeFilterBar
import com.agarthavision.ui.components.SearchInput
import com.agarthavision.ui.navigation.Screen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import com.agarthavision.ui.components.EmptyState
import com.agarthavision.ui.components.SheetInput
import com.agarthavision.ui.components.SheetInputConfig
import com.agarthavision.ui.components.SkeletonBox
import com.agarthavision.ui.components.recordedByText
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.AppColors
import com.agarthavision.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
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
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val generatedSnackbarLabel = stringResource(R.string.patient_report_generated_snackbar)
    val openActionLabel = stringResource(R.string.reports_open_pdf)
    var renamingSessionData by remember { mutableStateOf<SessionWithStats?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            handleSessionsEvent(
                event = event,
                context = context,
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState,
                generatedSnackbarLabel = generatedSnackbarLabel,
                openActionLabel = openActionLabel,
                onNavigateToCapture = onNavigateToCapture,
                onNavigate = onNavigate,
                setShowCreateDialog = { showCreateDialog = it },
            )
        }
    }

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
                    onBack = onBack,
                    onGenerateReportClick = viewModel::onOpenGenerateReport,
                )

                // Patient Identity Preview Header / Card
                state.patient?.let { patient ->
                    PatientPreviewCard(
                        patient = patient,
                        barangayAddress = state.barangayAddress,
                        counts = PatientPreviewCounts(
                            totalCount = state.totalCount,
                            unverifiedCount = state.unverifiedCount,
                            isLoading = state.isLoading,
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }

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
                SessionsScreenList(
                    state = state,
                    listState = listState,
                    callbacks = SessionsListCallbacks(
                        onNavigate = onNavigate,
                        onResumeSession = viewModel::onResumeSession,
                        onOpenVerificationQueue = viewModel::onOpenVerificationQueue,
                        onRenameSession = { sessionData ->
                            viewModel.onDismissError()
                            renamingSessionData = sessionData
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )

                // Sticky bottom CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // This screen is a drill-down with no tab bar under it, so nothing
                        // else reserves the system navigation bar's space. Without this the
                        // button's lower half renders behind the system buttons and they take
                        // the taps. Inset here rather than on the root Column so the list
                        // above still scrolls the full height of the screen.
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.onDismissError()
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(49.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandFill,
                            contentColor = colors.onBrandFill,
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            // The button's own "New session" label says it; repeating it
                            // makes TalkBack read the control twice.
                            contentDescription = null,
                            tint = colors.onBrandFill,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.session_picker_create),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

    if (showCreateDialog) {
        NewSessionSheet(
            // Leaving the sheet abandons the draft; the label is local `remember` state and
            // resets with it. There is nothing left in the ViewModel to reset — the barangay
            // moved to the patient and the note is gone.
            state = state,
            onClearError = viewModel::onDismissError,
            onDismiss = {
                viewModel.onDismissError()
                showCreateDialog = false
            },
            onSubmit = { label ->
                viewModel.onCreateSession(label)
            }
        )
    }

    state.reportSheet?.let { sheet ->
        PatientReportSheet(
            sheet = sheet,
            patientName = state.patient?.displayName,
            onDismiss = viewModel::onDismissReportSheet,
            onDateRangeSelected = viewModel::onReportDateRangeSelected,
            onSelectAll = viewModel::onSelectAllReportSessions,
            onToggleSession = viewModel::onToggleReportSession,
            onGenerate = viewModel::onGeneratePatientReport,
        )
    }
    renamingSessionData?.let { target ->
        val currentLabel = state.sessions.firstOrNull { it.session.id == target.session.id }?.session?.label
        LaunchedEffect(currentLabel) {
            if (currentLabel != null && currentLabel != target.session.label) {
                viewModel.onDismissError()
                renamingSessionData = null
            }
        }
        RenameSessionSheet(
            initialLabel = target.session.label.orEmpty(),
            state = state,
            onClearError = viewModel::onDismissError,
            onDismiss = {
                viewModel.onDismissError()
                renamingSessionData = null
            },
            onSubmit = { newLabel ->
                viewModel.onRenameSession(target.session.id, newLabel)
            },
        )
    }
}

private data class SessionsListCallbacks(
    val onNavigate: (String) -> Unit,
    val onResumeSession: (String) -> Unit,
    val onOpenVerificationQueue: (String) -> Unit,
    val onRenameSession: (SessionWithStats) -> Unit,
)

@Composable
private fun SessionsScreenList(
    state: SessionsState,
    listState: LazyListState,
    callbacks: SessionsListCallbacks,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Column(
            modifier = modifier
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                SessionCardSkeleton()
            }
        }
        state.sessions.isEmpty() -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.sessions_empty_title),
                body = stringResource(R.string.sessions_empty_body),
                illustrationLight = R.drawable.ill_empty_sessions_light,
                illustrationDark = R.drawable.ill_empty_sessions_dark,
            )
        }
        else -> LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.sessions, key = { it.session.id }) { sessionData ->
                val sessionId = sessionData.session.id
                val isColleagueSession = sessionId in state.colleagueAuthors
                SessionCard(
                    sessionData = sessionData,
                    isActive = sessionId == state.activeSessionId,
                    colleagueAuthor = state.colleagueAuthors[sessionId],
                    isColleagueSession = isColleagueSession,
                    actions = SessionCardActions(
                        onClick = sessionRowClick(
                            isColleagueSession = isColleagueSession,
                            openDetail = { callbacks.onNavigate(Screen.SessionDetail.createRoute(sessionId)) },
                            resume = { callbacks.onResumeSession(sessionId) },
                        ),
                        onVerifyClick = {
                            callbacks.onOpenVerificationQueue(sessionData.session.id)
                        },
                        onViewReportClick = {
                            val route = Screen.SessionDetail.createRoute(sessionData.session.id)
                            callbacks.onNavigate(route)
                        },
                        onRenameClick = {
                            callbacks.onRenameSession(sessionData)
                        },
                    ),
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
}

/**
 * This screen is a drill-down now, so it carries a back arrow.
 *
 * It used to be a root tab, where the bottom bar was the way out. It is registered at
 * `patients/{patientId}`, which is not in `bottomBarRoutes`, so without this the only way
 * back is the system gesture — and a screen reachable only by gesture reads as a dead end.
 *
 * The [Row] owns `.statusBarsPadding()` so the back arrow and the title text share the
 * same inset origin and align correctly. The old implementation wrapped [ScreenHeader] (a
 * component that applies its own `.statusBarsPadding()` internally) inside a plain [Row]
 * alongside [BackArrow], which caused the title to sit lower than the arrow by the height
 * of the status bar.
 *
 * Pattern matches `SessionDetailScreen.SessionDetailAppBar`.
 */
@Composable
private fun AppBar(onBack: () -> Unit, onGenerateReportClick: () -> Unit) {
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
                text = stringResource(R.string.sessions_title),
                style = MaterialTheme.typography.headlineSmall,
                color = if (colors.isDark) Color.White else Color.Black,
            )
            Text(
                text = stringResource(R.string.sessions_subtitle_purpose),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onGenerateReportClick) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = stringResource(R.string.patient_report_generate_action),
                tint = colors.textPrimary,
            )
        }
    }
}

/**
 * Patient identity preview card shown below the app bar.
 *
 * Establishes immediate clinical context (full name, age, sex, barangay address) so the
 * medtech can verify they are reading smears for the correct patient without navigating
 * back. Persists while scrolling the session list below.
 */
/** Counts + loading state for [PatientPreviewCard] — bundled since they always travel together. */
private data class PatientPreviewCounts(
    val totalCount: Int,
    val unverifiedCount: Int,
    val isLoading: Boolean,
)

@Composable
private fun PatientPreviewCard(
    patient: Patient,
    barangayAddress: String?,
    counts: PatientPreviewCounts,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    val context = LocalContext.current
    var isRevealed by rememberSaveable { mutableStateOf(false) }
    val age = patient.ageYears(Instant.now())
    val ageText = context.resources.getQuantityString(R.plurals.patient_preview_age, age, age)
    val sexLabel = when (patient.sex) {
        Sex.MALE -> stringResource(R.string.patients_sex_male)
        Sex.FEMALE -> stringResource(R.string.patients_sex_female)
        null -> stringResource(R.string.patients_sex_unknown)
    }
    val ageSex = stringResource(R.string.patient_preview_age_sex, ageText, sexLabel)
    val barangay = barangayAddress ?: patient.psgcBarangayCode

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.brandFill)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isRevealed) patient.displayName else patient.maskedDisplayName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onBrandFill,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!patient.isCodename) {
                val icon = if (isRevealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                val descRes = if (isRevealed) {
                    R.string.patients_mask_name_desc
                } else {
                    R.string.patients_reveal_name_desc
                }
                IconButton(
                    onClick = { isRevealed = !isRevealed },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(descRes),
                        tint = colors.onBrandFill,
                    )
                }
            }
        }
        Text(
            text = ageSex,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = colors.onBrandFill.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = barangay,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            color = colors.onBrandFill.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (counts.isLoading) {
            SkeletonBox(Modifier.width(140.dp).height(12.dp))
        } else {
            Text(
                text = pluralStringResource(
                    R.plurals.sessions_subtitle,
                    counts.totalCount,
                    counts.totalCount,
                    counts.unverifiedCount,
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.onBrandFill.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    state: SessionsState,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (label: String) -> Unit
) {
    val colors = AgarthaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHigh,
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
        // Keyed on the suggestion so a sheet opened after the previous smear was created
        // starts on the new number rather than the one already used. It is only a seed: the
        // field is editable from the first keystroke, and nothing re-applies it.
        var label by remember(state.suggestedLabel) { mutableStateOf(state.suggestedLabel) }
        var showEmptyError by remember { mutableStateOf(false) }

        val activeErrorMessage = when {
            showEmptyError && label.isBlank() -> stringResource(R.string.session_new_label_required)
            !state.errorMessage.isNullOrBlank() -> state.errorMessage
            else -> null
        }
        val isError = activeErrorMessage != null

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
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.session_new_sheet_close),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Body
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                SheetInput(
                    value = label,
                    onValueChange = {
                        label = it
                        showEmptyError = false
                        if (state.errorMessage != null) onClearError()
                    },
                    config = SheetInputConfig(
                        label = stringResource(R.string.session_label_field_label),
                        placeholder = stringResource(R.string.session_label_placeholder),
                        isError = isError,
                        maxLength = SESSION_LABEL_MAX_LENGTH
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (activeErrorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.dangerTint, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.danger.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            // Was a bare "!" stroke with the surrounding ring hand-drawn
                            // through `drawExtras`; the Material glyph already carries it.
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeErrorMessage,
                            fontSize = 12.sp,
                            color = colors.dangerText,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.accentTint2, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            stringResource(R.string.session_label_helper),
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
                    enabled = !state.isCreating,
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceMuted,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text(
                        stringResource(R.string.session_picker_dialog_cancel),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = {
                        if (label.isBlank()) showEmptyError = true else onSubmit(label)
                    },
                    enabled = !state.isCreating,
                    modifier = Modifier.weight(1f).height(49.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandFill,
                        contentColor = colors.onBrandFill,
                    ),
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            color = colors.onBrandFill,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(R.string.session_picker_dialog_start),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = colors.onBrandFill,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
