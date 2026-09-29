package com.agarthavision.ui.sessionlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.domain.model.windows
import com.agarthavision.domain.usecase.home.ObserveSessionListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface SessionListEvent {
    data class NavigateToDetail(val sessionId: String) : SessionListEvent
    data object NavigateToVerificationQueue : SessionListEvent
}

data class SessionListUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
    val filter: SessionListFilter = SessionListFilter.ALL,
    val period: HomePeriod? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clock: Clock,
    private val sessionManager: SessionManager,
    private val observeSessionListUseCase: ObserveSessionListUseCase,
) : ViewModel() {

    val filter: SessionListFilter = savedStateHandle.get<String>("filter")?.let { raw ->
        runCatching { SessionListFilter.valueOf(raw) }.getOrNull()
    } ?: SessionListFilter.ALL

    val period: HomePeriod? = savedStateHandle.get<String>("period")?.let { raw ->
        if (raw == "ALL") null else runCatching { HomePeriod.valueOf(raw) }.getOrNull()
    }

    private val limitFlow = MutableStateFlow(INITIAL_PAGE_SIZE)

    private val _events = Channel<SessionListEvent>(Channel.BUFFERED)
    val events: Flow<SessionListEvent> = _events.receiveAsFlow()

    private val window = period?.windows(clock.instant(), CLINICAL_ZONE)?.current

    val uiState: StateFlow<SessionListUiState> = limitFlow.flatMapLatest { limit ->
        observeSessionListUseCase(filter, window, limit)
    }.combine(limitFlow) { result, _ ->
        SessionListUiState(
            sessions = result.items,
            totalCount = result.totalCount,
            isLoading = false,
            canLoadMore = result.items.size < result.totalCount,
            filter = filter,
            period = period,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionListUiState(filter = filter, period = period),
    )

    fun onLoadMore() {
        if (uiState.value.canLoadMore) {
            limitFlow.value += PAGE_INCREMENT
        }
    }

    fun onSessionClick(sessionSummary: SessionSummary) {
        if (filter == SessionListFilter.TO_REVIEW) {
            viewModelScope.launch {
                runCatching {
                    sessionManager.resumeSession(sessionSummary.session.id)
                }.onSuccess {
                    _events.send(SessionListEvent.NavigateToVerificationQueue)
                }
            }
        } else {
            viewModelScope.launch {
                _events.send(SessionListEvent.NavigateToDetail(sessionSummary.session.id))
            }
        }
    }

    companion object {
        const val INITIAL_PAGE_SIZE = 20
        const val PAGE_INCREMENT = 20
    }
}
