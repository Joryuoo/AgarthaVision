package com.agarthavision.domain.usecase.reports

import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import javax.inject.Inject

/**
 * Computes per-session Low Power Field (LPF) density from medtech findings.
 *
 * Philippine medtechs use Direct Smear rather than Kato-Katz, so EPG is retired (86d4a6jxw) in
 * favour of LPF density.
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
        
        // The denominator is the total number of fields (samples) examined in the session,
        // including clean fields (zero eggs).
        val samples = sampleRepository.getSamplesForSession(sessionId, userId)
        val fieldCount = samples.size.coerceAtLeast(1)

        val confirmedCounts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val total = confirmedCounts.sumOf { it.count }

        val findings = findingDao.getFindingsForSession(sessionId, userId)
        
        // Density is per species: sum of eggs of that species divided by fields examined.
        // We also track the min/max egg count seen in any single field for the range.
        val lpfPerSpecies = findings.groupBy { it.species }.mapValues { (_, speciesFindings) ->
            val countsByField = speciesFindings.groupBy { it.sampleId }
                .mapValues { it.value.sumOf { f -> f.eggCount } }
            
            val totalEggs = countsByField.values.sum()
            val maxEggs = countsByField.values.maxOrNull() ?: 0
            // If any field in the session had zero eggs of this species, min is 0.
            val minEggs = if (countsByField.size < fieldCount) 0 else countsByField.values.minOrNull() ?: 0

            LpfDensity(
                mean = totalEggs.toFloat() / fieldCount,
                min = minEggs,
                max = maxEggs
            )
        }

        SessionEggCounts(
            counts = confirmedCounts,
            totalEggCount = total,
            lpfPerSpecies = lpfPerSpecies,
            fieldCount = fieldCount
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
