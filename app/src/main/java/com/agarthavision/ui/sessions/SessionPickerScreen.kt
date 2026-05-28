@file:Suppress("FunctionNaming", "LongMethod")

package com.agarthavision.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.ui.theme.AgarthaSpacing
import com.komoui.components.Badge as KomoBadge
import com.komoui.components.BadgeVariant
import com.komoui.components.Button as KomoButton
import com.komoui.components.ButtonSize
import com.komoui.components.ButtonVariant
import com.komoui.components.Input
import com.komoui.themes.styles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lists active sessions and the last 30 days of closed sessions, lets the
 * medtech create a new smear session, resume an active one, or end an active
 * one from a row kebab. Per ADR-005 and docs/03_MOBILE_APP_PLAN.md §1.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerScreen(
    onSessionSelected: (String) -> Unit,
    viewModel: SessionPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionPickerEvent.NavigateToCapture -> onSessionSelected(event.sessionId)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.styles.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.session_picker_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.styles.background,
                    titleContentColor = MaterialTheme.styles.foreground,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.styles.primary)
                }
                state.sessions.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                )
                else -> SessionList(
                    sessions = state.sessions,
                    onResume = viewModel::onResumeSession,
                    onEnd = viewModel::onEndSession,
                )
            }

            CreateSessionButton(
                isCreating = state.isCreating,
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AgarthaSpacing.screenEdge),
            )
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            isCreating = state.isCreating,
            onDismiss = { showCreateDialog = false },
            onCreate = { label, notes ->
                showCreateDialog = false
                viewModel.onCreateSession(label, notes)
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(AgarthaSpacing.screenEdge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.session_picker_empty_title),
            color = MaterialTheme.styles.foreground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(AgarthaSpacing.xs))
        Text(
            text = stringResource(R.string.session_picker_empty_body),
            color = MaterialTheme.styles.mutedForeground,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionEntity>,
    onResume: (String) -> Unit,
    onEnd: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = AgarthaSpacing.screenEdge,
            end = AgarthaSpacing.screenEdge,
            top = AgarthaSpacing.md,
            bottom = AgarthaSpacing.huge + AgarthaSpacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.sm),
    ) {
        items(sessions, key = { it.sessionId }) { session ->
            SessionRow(
                session = session,
                onClick = { onResume(session.sessionId) },
                onEndSession = { onEnd(session.sessionId) },
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionEntity,
    onClick: () -> Unit,
    onEndSession: () -> Unit,
) {
    val isActive = session.endedAt == null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.styles.secondary,
            contentColor = MaterialTheme.styles.secondaryForeground,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(AgarthaSpacing.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.label?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.session_picker_row_untitled),
                        color = MaterialTheme.styles.foreground,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(AgarthaSpacing.xxs))
                    Text(
                        text = session.startedAt.formatPickerDateTime(),
                        color = MaterialTheme.styles.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    session.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(modifier = Modifier.height(AgarthaSpacing.xxs))
                        Text(
                            text = notes.preview(),
                            color = MaterialTheme.styles.mutedForeground,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (isActive) {
                    KomoBadge(variant = BadgeVariant.Default) {
                        Text(stringResource(R.string.session_picker_row_active))
                    }
                    RowKebab(onEndSession = onEndSession)
                }
            }
        }
    }
}

@Composable
private fun RowKebab(onEndSession: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.session_picker_row_kebab_desc),
                tint = MaterialTheme.styles.foreground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_picker_row_end_session)) },
                onClick = {
                    expanded = false
                    onEndSession()
                },
            )
        }
    }
}

@Composable
private fun CreateSessionButton(
    isCreating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomoButton(
        onClick = onClick,
        size = ButtonSize.Lg,
        variant = ButtonVariant.Default,
        loading = isCreating,
        fullWidth = true,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.padding(end = AgarthaSpacing.xs))
        Text(stringResource(R.string.session_picker_create))
    }
}

@Composable
private fun CreateSessionDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (label: String, notes: String?) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val labelError = label.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            KomoButton(
                onClick = { onCreate(label, notes.takeIf { it.isNotBlank() }) },
                enabled = !labelError && !isCreating,
                loading = isCreating,
                size = ButtonSize.Default,
            ) {
                Text(stringResource(R.string.session_picker_dialog_create))
            }
        },
        dismissButton = {
            KomoButton(
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Default,
                enabled = !isCreating,
            ) {
                Text(stringResource(R.string.verify_cancel))
            }
        },
        title = { Text(stringResource(R.string.session_picker_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AgarthaSpacing.sm)) {
                Text(
                    text = stringResource(R.string.session_picker_dialog_label),
                    color = MaterialTheme.styles.foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
                Input(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = stringResource(R.string.session_picker_dialog_label_placeholder),
                    singleLine = true,
                    enabled = !isCreating,
                )
                Text(
                    text = stringResource(R.string.session_picker_dialog_notes),
                    color = MaterialTheme.styles.foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
                Input(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = stringResource(R.string.session_picker_dialog_notes_placeholder),
                    singleLine = false,
                    enabled = !isCreating,
                )
            }
        },
        containerColor = MaterialTheme.styles.popover,
        titleContentColor = MaterialTheme.styles.foreground,
        textContentColor = MaterialTheme.styles.foreground,
    )
}

private fun Long.formatPickerDateTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun String.preview(maxChars: Int = 60): String =
    if (length <= maxChars) this else take(maxChars).trimEnd() + "…"
