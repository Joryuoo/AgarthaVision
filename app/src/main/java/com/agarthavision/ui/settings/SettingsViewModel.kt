package com.agarthavision.ui.settings

import com.agarthavision.core.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.sync.FetchOutcomeStore
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.auth.SignOutUseCase
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.domain.sync.LastSyncStore
import com.agarthavision.domain.sync.SyncCompletion
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
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val isDarkMode: Boolean = false,
    val pendingSyncCounts: PendingSyncCounts = PendingSyncCounts(0, 0, 0, 0, 0),
    val isSyncing: Boolean = false,
    val initialFetchDone: Boolean = true,
    val lastFetchIncomplete: Boolean = false,
    val lastSyncCompletion: SyncCompletion? = null,
    val lastSyncError: String? = null,
) {
    /** Sync-now is available only to a signed-in medtech with an online connection. */
    val canSyncNow: Boolean
        get() = isSignedIn && !isOffline && !isSyncing
}

private data class SyncTuple(
    val syncing: Boolean,
    val initialFetchDone: Boolean,
    val fetchIncomplete: Boolean,
    val lastSync: SyncCompletion?,
    val lastSyncError: String?,
)

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
    private val fetchRemoteDataUseCase: FetchRemoteDataUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val initialFetchStateStore: InitialFetchStateStore,
    private val fetchOutcomeStore: FetchOutcomeStore,
    private val lastSyncStore: LastSyncStore,
) : ViewModel() {

    private val events = MutableSharedFlow<SettingsEvent>()
    val eventFlow: SharedFlow<SettingsEvent> = events

    private val identityFlow = observeLocalIdentityUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val pendingSyncFlow = identityFlow.flatMapLatest { identity ->
        if (identity == null) {
            flowOf(PendingSyncCounts(0, 0, 0, 0, 0))
        } else {
            observePendingSyncCountsUseCase(identity.userId)
        }
    }

    private val isSyncingFlow = MutableStateFlow(false)

    // Emits true once the initial remote fetch has completed for the current user; true by
    // default when signed out (nothing to wait for). Folded into the inner combine to keep
    // the outer combine at the 5-arg limit.
    private val initialFetchDoneFlow = identityFlow.flatMapLatest { identity ->
        identity?.let { initialFetchStateStore.observeCompleted(it.userId) } ?: flowOf(true)
    }

    // Whether the last pull left an entity type unfetched. Read from a store rather than held
    // here because the pass that fails is usually the worker's, with this screen not in memory.
    private val lastFetchIncompleteFlow = identityFlow.flatMapLatest { identity ->
        identity?.let { fetchOutcomeStore.observeIncomplete(it.userId) } ?: flowOf(false)
    }

    private val lastSyncFlow = identityFlow.flatMapLatest { identity ->
        identity?.let { lastSyncStore.observe(it.userId) } ?: flowOf(null)
    }

    private val lastSyncErrorFlow = identityFlow.flatMapLatest { identity ->
        identity?.let { lastSyncStore.observeLastError(it.userId) } ?: flowOf(null)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        identityFlow,
        connectivityObserver.isOnline,
        observeThemeModeUseCase(),
        pendingSyncFlow,
        combine(
            isSyncingFlow,
            initialFetchDoneFlow,
            lastFetchIncompleteFlow,
            lastSyncFlow,
            lastSyncErrorFlow,
            ::SyncTuple,
        ),
    ) { identity, online, themeMode, pendingSync, tuple ->
        SettingsUiState(
            isLoading = false,
            identity = identity,
            isSignedIn = identity != null,
            isOffline = !online,
            themeMode = themeMode,
            isDarkMode = themeMode == ThemeMode.DARK,
            pendingSyncCounts = pendingSync,
            isSyncing = tuple.syncing,
            initialFetchDone = tuple.initialFetchDone,
            lastFetchIncomplete = tuple.fetchIncomplete,
            lastSyncCompletion = tuple.lastSync,
            lastSyncError = tuple.lastSyncError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /** Persists the chosen theme mode. */
    fun onSelectTheme(mode: ThemeMode) {
        viewModelScope.launch {
            setThemeModeUseCase(mode).onFailure { error ->
                Logger.e(TAG, "Failed to persist theme mode $mode", error)
            }
        }
    }

    /** Flips the persisted theme between light and dark. */
    fun onToggleTheme() {
        val target = if (uiState.value.isDarkMode) ThemeMode.LIGHT else ThemeMode.DARK
        onSelectTheme(target)
    }

    /** Runs a manual pending-sync pass (push + pull). */
    fun onSyncNow() {
        if (!uiState.value.canSyncNow) return
        viewModelScope.launch {
            isSyncingFlow.value = true
            syncPendingDataUseCase().onFailure { error ->
                Logger.e(TAG, "Manual sync (push) failed", error)
            }
            fetchRemoteDataUseCase().onFailure { error ->
                Logger.e(TAG, "Manual sync (fetch) failed", error)
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
