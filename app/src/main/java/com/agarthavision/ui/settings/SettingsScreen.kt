package com.agarthavision.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import com.agarthavision.ui.theme.Spacing
import kotlinx.coroutines.flow.collectLatest

/** Actions the Settings screen's sections dispatch back to the [SettingsViewModel]. */
data class SettingsActions(
    val onSignInClick: () -> Unit,
    val onSignOutClick: () -> Unit,
    val onSyncNowClick: () -> Unit,
    val onToggleTheme: () -> Unit,
)

/**
 * Production Settings screen: Account, Data & Sync, Appearance, About.
 * Per the Settings scope (ADR-007 follow-ups) and ADR-008.
 */
@Composable
fun SettingsScreen(
    onSignInClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var signOutBlockedReason by remember { mutableStateOf<String?>(null) }

    val eventFlow = viewModel.eventFlow
    LaunchedEffect(eventFlow) {
        eventFlow.collectLatest { event ->
            when (event) {
                SettingsEvent.SignedOut -> Unit
                is SettingsEvent.SignOutBlocked -> signOutBlockedReason = event.reason
            }
        }
    }

    SettingsContent(
        state = state,
        actions = SettingsActions(
            onSignInClick = onSignInClick,
            onSignOutClick = { showSignOutDialog = true },
            onSyncNowClick = viewModel::onSyncNow,
            onToggleTheme = viewModel::onToggleTheme,
        ),
    )

    if (showSignOutDialog) {
        SignOutConfirmDialog(
            pendingCount = state.pendingSyncCounts.totalPending,
            onConfirm = {
                showSignOutDialog = false
                viewModel.onSignOut()
            },
            onDismiss = { showSignOutDialog = false },
        )
    }

    signOutBlockedReason?.let { reason ->
        SignOutBlockedDialog(reason = reason, onDismiss = { signOutBlockedReason = null })
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            // AgarthaNavGraph zeroes contentWindowInsets app-wide, so each screen applies
            // its own. Without this the title draws under the status bar.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                        .padding(top = Spacing.xl, bottom = Spacing.lg),
                )
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_account)) {
                    AccountCard(
                        identity = state.identity,
                        isOffline = state.isOffline,
                        onSignInClick = actions.onSignInClick,
                        onSignOutClick = actions.onSignOutClick,
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_sync)) {
                    SyncCard(
                        state = SyncCardState(
                            isSignedIn = state.isSignedIn,
                            isOffline = state.isOffline,
                            counts = state.pendingSyncCounts,
                            isSyncing = state.isSyncing,
                            canSyncNow = state.canSyncNow,
                        ),
                        onSyncNowClick = actions.onSyncNowClick,
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                    AppearanceCard(isDarkMode = state.isDarkMode, onToggleTheme = actions.onToggleTheme)
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    AboutCard()
                }
            }
        }
    }
}

@Composable
private fun SignOutConfirmDialog(
    pendingCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AgarthaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = { Text(stringResource(R.string.settings_sign_out_dialog_title)) },
        text = {
            Text(
                if (pendingCount > 0) {
                    stringResource(R.string.settings_sign_out_dialog_body_pending, pendingCount)
                } else {
                    stringResource(R.string.settings_sign_out_dialog_body_synced)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_sign_out_dialog_confirm), color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_sign_out_dialog_cancel))
            }
        },
    )
}

@Composable
private fun SignOutBlockedDialog(reason: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = { Text(stringResource(R.string.settings_sign_out)) },
        text = { Text(stringResource(R.string.settings_sign_out_blocked, reason)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sample_detail_yes))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsContent(
        state = SettingsUiState(
            isLoading = false,
            identity = LocalIdentity(userId = "user-1", email = "medtech@example.com"),
            isSignedIn = true,
            isOffline = false,
            isDarkMode = false,
            pendingSyncCounts = PendingSyncCounts(2, 5, 1, 0),
        ),
        actions = SettingsActions(
            onSignInClick = {},
            onSignOutClick = {},
            onSyncNowClick = {},
            onToggleTheme = {},
        ),
    )
}
