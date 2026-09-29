package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FindingsResult
import com.agarthavision.domain.model.SpeciesFinding
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveFindingsUseCase @Inject constructor(
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(
        userId: String,
        window: TimeWindow,
        townCodes: Set<String>? = null,
    ): Flow<FindingsResult> {
        return detectionRepository.observeSessionFindingsBetween(
            userId,
            window.startMillis,
            window.endMillis,
        ).map { rows ->
            val filteredRows = if (townCodes != null) {
                rows.filter { it.townCode != null && it.townCode in townCodes }
            } else {
                rows
            }

            // Normalise species with EggSpecies.fromClassLabel
            // Distinct (sessionId, species) pairs per D1
            val distinctPairs = filteredRows.map { row ->
                val canonicalName = EggSpecies.fromClassLabel(row.rawSpecies)?.displayName
                    ?: row.rawSpecies.trim()
                row.sessionId to canonicalName
            }.distinct()

            val positiveSmearsCount = distinctPairs.map { it.first }.distinct().size

            val countsBySpecies = distinctPairs.groupingBy { it.second }.eachCount()
            val totalPairs = distinctPairs.size.coerceAtLeast(1)

            val speciesFindings = countsBySpecies.map { (speciesName, count) ->
                val ratio = count.toFloat() / totalPairs
                SpeciesFinding(
                    name = speciesName,
                    count = count,
                    ratio = ratio,
                    formattedPercentage = "${(ratio * 100).toInt()}%",
                )
            }.sortedWith(
                compareByDescending<SpeciesFinding> { it.count }
                    .thenBy { it.name },
            )

            FindingsResult(
                species = speciesFindings,
                positiveSmearsCount = positiveSmearsCount,
            )
        }
    }
}
