package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class SessionListResult(
    val items: List<SessionSummary>,
    val totalCount: Int,
)

class ObserveSessionListUseCase @Inject constructor(
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase,
    private val sessionRepository: SessionRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        filter: SessionListFilter,
        window: TimeWindow? = null,
        limit: Int = 20,
    ): Flow<SessionListResult> =
        observeLocalIdentityUseCase()
            .map { it?.userId }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(SessionListResult(emptyList(), 0))
                } else {
                    combine(
                        sessionRepository.observeSessionSummaries(userId, filter, window, limit),
                        sessionRepository.observeSessionSummaryCount(userId, filter, window),
                    ) { items, totalCount ->
                        SessionListResult(items, totalCount)
                    }
                }
            }
}
