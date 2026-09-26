package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class NeedsAttention(
    val unsyncedItems: Int,
    val framesToReview: Int,
    val emptySessions: Int,
) {
    val isEmpty: Boolean
        get() = unsyncedItems == 0 && framesToReview == 0 && emptySessions == 0
}

class ObserveNeedsAttentionUseCase @Inject constructor(
    private val observePendingSyncCountsUseCase: ObservePendingSyncCountsUseCase,
    private val sampleRepository: SampleRepository,
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(userId: String, activeSessionId: String?): Flow<NeedsAttention> =
        combine(
            observePendingSyncCountsUseCase(userId),
            sampleRepository.observeFlaggedCount(userId),
            sessionRepository.observeEmptySessionCount(userId, activeSessionId),
        ) { syncCounts, flaggedCount, emptyCount ->
            NeedsAttention(
                unsyncedItems = syncCounts.totalUnsynced,
                framesToReview = flaggedCount,
                emptySessions = emptyCount,
            )
        }
}
