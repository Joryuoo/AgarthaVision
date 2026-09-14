package com.agarthavision.domain.usecase.reports

import com.agarthavision.core.util.EpgCalculator
import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.InfectivityLevel
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
     * Returns per-species counts, total egg count, EPG, and the WHO infectivity tier for a
     * session.
     */
    suspend operator fun invoke(sessionId: String): SessionEggCounts {
        val userId = authRepository.getCurrentUserId() ?: return SessionEggCounts.empty()
        val counts = detectionRepository.getConfirmedEggCountsForSession(sessionId, userId)
        val total = counts.sumOf { it.count }
        val epg = EpgCalculator.epg(total)

        // Same normalization the report path uses (alias -> EggSpecies), but keyed by the
        // recognized EggSpecies itself rather than its display string, since the WHO tier
        // table is keyed on the enum. Unrecognized/"Other" labels have no WHO table entry and
        // are excluded here rather than silently folded into a tier they don't have.
        val epgPerSpecies: Map<EggSpecies, Int> = counts
            .mapNotNull { count -> count.canonicalEggSpecies()?.let { it to count.count } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, speciesCounts) -> EpgCalculator.epg(speciesCounts.sum()) }

        val infectivityLevel = InfectivityLevelCalculator.sessionLevel(epgPerSpecies)
        val topSpecies = infectivityLevel?.let { level ->
            epgPerSpecies.entries
                .filter { (species, speciesEpg) ->
                    InfectivityLevelCalculator.classify(species, speciesEpg) == level
                }
                .maxByOrNull { it.value }
                ?.key
        }

        return SessionEggCounts(
            counts = counts,
            totalEggCount = total,
            epg = epg,
            epgPerSpecies = epgPerSpecies,
            infectivityLevel = infectivityLevel,
            topSpecies = topSpecies,
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
    val epgPerSpecies: Map<EggSpecies, Int> = emptyMap(),
    val infectivityLevel: InfectivityLevel? = null,
    val topSpecies: EggSpecies? = null,
) {
    companion object {
        /**
         * Empty default when no user session is available.
         */
        fun empty(): SessionEggCounts = SessionEggCounts(emptyList(), 0, 0)
    }
}
