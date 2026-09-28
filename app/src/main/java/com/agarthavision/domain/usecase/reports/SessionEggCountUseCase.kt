package com.agarthavision.domain.usecase.reports

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import javax.inject.Inject

/**
 * The per-species LPF range for a session, for the screen.
 *
 * Philippine medtechs use Direct Smear rather than Kato-Katz, so there is no eggs-per-gram to
 * report (PB-16). The aggregation itself lives in [aggregateLpfPerSpecies], which the report
 * path calls too — the two used to compute it separately, from copied code.
 */
class SessionEggCountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val detectionRepository: DetectionRepository,
    private val sampleRepository: SampleRepository,
    private val findingDao: SampleSpeciesFindingDao,
) {
    /**
     * Returns per-species counts, total egg count, and LPF density for a session.
     */
    suspend operator fun invoke(sessionId: String): Result<SessionEggCounts> = runCatching {
        val userId = authRepository.currentLocalUserId()

        // Every field the medtech recorded, clean ones included. A field with none of a species
        // contributes a zero to that species' range, so leaving them out of the denominator
        // would raise every minimum off the floor it belongs on.
        //
        // No `coerceAtLeast(1)`. It was there to keep a mean from dividing by zero; with the
        // mean gone there is nothing to divide, and a session with no fields correctly produces
        // no ranges rather than a denominator invented to avoid an exception.
        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        val fieldCount = samples.size

        val confirmedCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val total = confirmedCounts.sumOf { it.count }

        val findings = findingDao.getFindingsForSession(sessionId, userId)

        SessionEggCounts(
            counts = confirmedCounts,
            totalEggCount = total,
            lpfPerSpecies = aggregateLpfPerSpecies(findings, fieldCount),
            fieldCount = fieldCount,
        )
    }
}

/**
 * Session-level egg count payload.
 */
data class SessionEggCounts(
    val counts: List<EggCount>,
    val totalEggCount: Int,
    val lpfPerSpecies: Map<String, LpfDensity> = emptyMap(),
    val fieldCount: Int = 0,
) {
    companion object {
        /**
         * Empty default when no user session is available.
         */
        fun empty(): SessionEggCounts = SessionEggCounts(emptyList(), 0)
    }
}
