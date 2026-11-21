package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.repository.SampleRepository
import kotlinx.coroutines.flow.first

/** The report needs acknowledgment of the current number of fields it will exclude. */
class UnverifiedReportFieldsException(val count: Int) : IllegalStateException()

internal suspend fun SampleRepository.countUnverifiedFieldsForReport(sessionId: String, userId: String): Int =
    observeFlaggedSamplesForSession(sessionId, userId).first().size

internal fun requireUnverifiedFieldsAcknowledged(unverifiedCount: Int, acknowledgedCount: Int?) {
    if (unverifiedCount > 0 && acknowledgedCount != unverifiedCount) {
        throw UnverifiedReportFieldsException(unverifiedCount)
    }
}
