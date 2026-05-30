package com.agarthavision.domain.usecase.reports

import com.agarthavision.core.util.EpgCalculator
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import javax.inject.Inject

/**
 * Computes per-session egg counts and EPG from confirmed detections.
 */
class SessionEggCountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val detectionRepository: DetectionRepository,
) {
    /**
     * Returns per-species counts, total egg count, and EPG for a session.
     */
    suspend operator fun invoke(sessionId: String): SessionEggCounts {
        val userId = authRepository.getCurrentUserId() ?: return SessionEggCounts.empty()
        val counts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val total = counts.sumOf { it.count }
        val epg = EpgCalculator.epg(total)
        return SessionEggCounts(
            counts = counts,
            totalEggCount = total,
            epg = epg,
        )
    }
}

/**
 * Session-level egg count payload.
 */
data class SessionEggCounts(
    val counts: List<EggCount>,
    val totalEggCount: Int,
    val epg: Int,
) {
    companion object {
        /**
         * Empty default when no user session is available.
         */
        fun empty(): SessionEggCounts = SessionEggCounts(emptyList(), 0, 0)
    }
}
