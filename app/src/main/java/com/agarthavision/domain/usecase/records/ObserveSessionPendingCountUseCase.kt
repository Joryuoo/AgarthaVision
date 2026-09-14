package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SampleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Observes the count of flagged (pending-verification) samples for a given session owned by
 * the current user. Emits 0 when no user is authenticated or the session has no pending frames.
 *
 * Used by [com.agarthavision.ui.records.SessionDetailViewModel] to decide whether to surface
 * the "Verify flagged frames" action in the Session Detail app bar.
 */
class ObserveSessionPendingCountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sampleRepository: SampleRepository,
) {
    operator fun invoke(sessionId: String): Flow<Int> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emitAll(flowOf(0))
            return@flow
        }
        emitAll(
            sampleRepository.observeFlaggedSamplesForSession(sessionId, userId)
                .map { it.size },
        )
    }
}
