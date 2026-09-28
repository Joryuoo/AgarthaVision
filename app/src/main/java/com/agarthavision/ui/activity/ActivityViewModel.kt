package com.agarthavision.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.home.ObserveRecentActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

data class ActivityUiState(
    val items: List<ActivityItem> = emptyList(),
    val isLoading: Boolean = true,
    val canLoadMore: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val observeRecentActivityUseCase: ObserveRecentActivityUseCase,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val userIdFlow = observeLocalIdentityUseCase()
        .map { it?.userId }
        .distinctUntilChanged()

    private val limitFlow = MutableStateFlow(INITIAL_LIMIT)

    val uiState: StateFlow<ActivityUiState> = combine(
        userIdFlow,
        limitFlow,
    ) { userId, limit ->
        userId to limit
    }.flatMapLatest { (userId, limit) ->
        if (userId == null) {
            flowOf(emptyList<ActivityItem>())
        } else {
            val sinceMillis = clock.instant().minus(Duration.ofDays(DAYS_WINDOW)).toEpochMilli()
            observeRecentActivityUseCase(userId, limit, sinceMillis)
        }
    }.combine(limitFlow) { items, limit ->
        ActivityUiState(
            items = items,
            isLoading = false,
            canLoadMore = items.size >= limit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState(),
    )

    fun onLoadMore() {
        if (uiState.value.canLoadMore) {
            limitFlow.value += PAGE_INCREMENT
        }
    }

    private companion object {
        const val DAYS_WINDOW = 7L
        const val INITIAL_LIMIT = 50
        const val PAGE_INCREMENT = 20
    }
}
