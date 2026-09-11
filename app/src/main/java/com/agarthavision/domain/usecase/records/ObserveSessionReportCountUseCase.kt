package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Observes the total number of persisted reports for one session belonging to the current user,
 * ignoring any page limit. Emits 0 when no user is authenticated. Lets the session detail screen
 * show "showing N of total" alongside the paged report list from [ObserveSessionReportsUseCase].
 */
class ObserveSessionReportCountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(sessionId: String): Flow<Int> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emitAll(flowOf(0))
            return@flow
        }
        emitAll(reportRepository.observeCountForSession(sessionId, userId))
    }
}
