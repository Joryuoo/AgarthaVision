package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Report
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Observes the persisted reports for one session belonging to the current user.
 * Emits an empty list when no user is authenticated.
 */
class ObserveSessionReportsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(sessionId: String): Flow<List<Report>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emitAll(flowOf(emptyList()))
            return@flow
        }
        emitAll(reportRepository.observeForSession(sessionId, userId))
    }
}
