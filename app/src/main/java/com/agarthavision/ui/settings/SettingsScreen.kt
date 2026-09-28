package com.agarthavision.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agarthavision.R
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.sync.SyncCompletion
import com.agarthavision.ui.theme.AgarthaTheme
import com.agarthavision.ui.theme.DialogShape
import kotlinx.coroutines.flow.collectLatest

/** Actions the Settings screen's sections dispatch back to the [SettingsViewModel]. */
data class SettingsActions(
    val onSignInClick: () -> Unit,
    val onSignOutClick: () -> Unit,
    val onSyncNowClick: () -> Unit,
    val onSelectTheme: (ThemeMode) -> Unit,
)

/**
 * Production Settings screen: Account, Data & Sync, Appearance, About, and Sign out.
 */
@Composable
fun SettingsScreen(
    onSignInClick: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var signOutBlockedReason by remember { mutableStateOf<String?>(null) }

    val eventFlow = viewModel.eventFlow
    LaunchedEffect(eventFlow) {
        eventFlow.collectLatest { event ->
            when (event) {
                SettingsEvent.SignedOut -> onSignedOut()
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
            onSelectTheme = viewModel::onSelectTheme,
        ),
    )

    if (showSignOutDialog) {
        SignOutConfirmDialog(
            unsyncedCount = state.pendingSyncCounts.totalUnsynced,
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
private fun SettingsScreenHeader(modifier: Modifier = Modifier) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (colors.isDark) Color.White else Color.Black,
            letterSpacing = (-0.5).sp,
            lineHeight = 30.sp,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = AgarthaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SettingsScreenHeader()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                AccountCard(
                    identity = state.identity,
                    isOffline = state.isOffline,
                    onSignInClick = actions.onSignInClick,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                Spacer(Modifier.height(10.dp))
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
                            initialFetchDone = state.initialFetchDone,
                            isFetching = state.isSyncing,
                            lastFetchIncomplete = state.lastFetchIncomplete,
                        ),
                        lastSyncMillis = state.lastSyncCompletion?.completedAtMillis,
                        onSyncNowClick = actions.onSyncNowClick,
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                    AppearanceCard(
                        themeMode = state.themeMode,
                        onSelectTheme = actions.onSelectTheme,
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    AboutCard()
                }
            }
            if (state.isSignedIn) {
                item {
                    Spacer(Modifier.height(14.dp))
                    SignOutSection(
                        unsyncedCount = state.pendingSyncCounts.totalUnsynced,
                        onSignOutClick = actions.onSignOutClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignOutConfirmDialog(
    unsyncedCount: Int,
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
                if (unsyncedCount > 0) {
                    stringResource(R.string.settings_sign_out_dialog_body_pending, unsyncedCount)
                } else {
                    stringResource(R.string.settings_sign_out_dialog_body_synced)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                val confirm = if (unsyncedCount > 0) {
                    R.string.settings_sign_out_dialog_confirm_discard
                } else {
                    R.string.settings_sign_out_dialog_confirm
                }
                Text(stringResource(confirm), color = colors.danger)
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
            identity = LocalIdentity(userId = "user-1", email = "medtech@agartha.ph"),
            isSignedIn = true,
            isOffline = false,
            themeMode = ThemeMode.LIGHT,
            isDarkMode = false,
            pendingSyncCounts = PendingSyncCounts(
                pendingPatients = 0,
                pendingSessions = 2,
                pendingSamples = 0,
                pendingReports = 0,
                failed = 1,
                failedSamples = 1,
            ),
            lastSyncCompletion = SyncCompletion(
                completedAtMillis = System.currentTimeMillis() - 3600000L,
                itemsSynced = 4,
            ),
        ),
        actions = SettingsActions(
            onSignInClick = {},
            onSignOutClick = {},
            onSyncNowClick = {},
            onSelectTheme = {},
        ),
    )
}

@Preview(showBackground = true, name = "Settings - signed out")
@Composable
private fun SettingsScreenSignedOutPreview() {
    SettingsContent(
        state = SettingsUiState(
            isLoading = false,
            identity = null,
            isSignedIn = false,
            isOffline = false,
            themeMode = ThemeMode.LIGHT,
            isDarkMode = false,
            pendingSyncCounts = PendingSyncCounts(0, 0, 0, 0, 0),
        ),
        actions = SettingsActions(
            onSignInClick = {},
            onSignOutClick = {},
            onSyncNowClick = {},
            onSelectTheme = {},
        ),
    )
}
