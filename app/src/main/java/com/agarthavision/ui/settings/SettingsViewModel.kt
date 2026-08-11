package com.agarthavision.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.auth.SignOutUseCase
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot Settings screen events. */
sealed interface SettingsEvent {
    data object SignedOut : SettingsEvent
    data class SignOutBlocked(val reason: String) : SettingsEvent
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val identity: LocalIdentity? = null,
    val isSignedIn: Boolean = false,
    val isOffline: Boolean = false,
    val isDarkMode: Boolean = false,
    val pendingSyncCounts: PendingSyncCounts = PendingSyncCounts(0, 0, 0, 0),
    val isSyncing: Boolean = false,
) {
    /** Sync-now is available only to a signed-in medtech with an online connection. */
    val canSyncNow: Boolean
        get() = isSignedIn && !isOffline && !isSyncing
}

/**
 * Backs the production Settings screen: account (identity, sign-in/sign-out), Data &
 * Sync (pending/failed counts, manual sync), and Appearance (theme toggle). Per the
 * Settings scope (ADR-007 follow-ups) and ADR-008.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val connectivityObserver: ConnectivityObserver,
    private val observePendingSyncCountsUseCase: ObservePendingSyncCountsUseCase,
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val syncPendingDataUseCase: SyncPendingDataUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {

    private val events = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = events

    private val identityFlow = observeLocalIdentityUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val pendingSyncFlow = identityFlow.flatMapLatest { identity ->
        if (identity == null) {
            flowOf(PendingSyncCounts(0, 0, 0, 0))
        } else {
            observePendingSyncCountsUseCase(identity.userId)
        }
    }

    private val isSyncingFlow = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        identityFlow,
        connectivityObserver.isOnline,
        observeThemeModeUseCase(),
        pendingSyncFlow,
        isSyncingFlow,
    ) { identity, online, themeMode, pendingSync, syncing ->
        SettingsUiState(
            isLoading = false,
            identity = identity,
            isSignedIn = identity != null,
            isOffline = !online,
            isDarkMode = themeMode == ThemeMode.DARK,
            pendingSyncCounts = pendingSync,
            isSyncing = syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /** Flips the persisted theme between light and dark. */
    fun onToggleTheme() {
        val target = if (uiState.value.isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            setThemeModeUseCase(target).onFailure { error ->
                Log.e(TAG, "Failed to persist theme mode $target", error)
            }
        }
    }

    /** Runs a manual pending-sync pass. */
    fun onSyncNow() {
        if (!uiState.value.canSyncNow) return
        viewModelScope.launch {
            isSyncingFlow.value = true
            syncPendingDataUseCase().onFailure { error ->
                Log.e(TAG, "Manual sync failed", error)
            }
            isSyncingFlow.value = false
        }
    }

    /**
     * Signs out per ADR-008. Blocked with [SettingsEvent.SignOutBlocked] while a capture
     * session is active; the medtech must end the session first.
     */
    fun onSignOut() {
        viewModelScope.launch {
            signOutUseCase()
                .onSuccess { events.emit(SettingsEvent.SignedOut) }
                .onFailure { error ->
                    events.emit(
                        SettingsEvent.SignOutBlocked(
                            error.message ?: "End the active session before signing out.",
                        ),
                    )
                }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
