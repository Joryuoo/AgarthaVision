package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Session summary shown on the Records screen.
 */
data class SessionRecordItem(
    val session: Session,
    val sampleCount: Int,
    val speciesLabels: List<String>,
    val totalEpg: Int = 0,
)

/**
 * Sample row with detections attached for session detail and sample detail views.
 */
data class SampleRecordItem(
    val sample: Sample,
    val detections: List<Detection>,
) {
    val primaryDetection: Detection?
        get() = detections
            .filterNot { it.verdict == DetectionVerdict.FALSE_POSITIVE }
            .maxByOrNull { it.confidence }
            ?: detections.maxByOrNull { it.confidence }
}

/**
 * Query parameters for the Records screen paginated session load.
 */
data class RecordsQuery(
    val species: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val limit: Int,
)

/**
 * Provides current-user session summaries for the Records screen.
 * Filtering and aggregation are pushed to SQL; species labels are bulk-fetched
 * in a single follow-up query to avoid per-session N+1 reads.
 */
class GetRecordsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val detectionRepository: DetectionRepository,
) {
    operator fun invoke(query: RecordsQuery): Flow<List<SessionRecordItem>> = flow {
        val userId = authRepository.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }

        val zone = ZoneId.systemDefault()
        val startMillis = query.startDate
            ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val endMillis = query.endDate
            ?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()
        val speciesNeedle = query.species?.canonicalClass

        emitAll(
            sessionRepository.observeSessionRecordsPage(
                userId = userId,
                startMillis = startMillis,
                endMillis = endMillis,
                query = query.searchQuery,
                species = speciesNeedle,
                limit = query.limit,
            ).map { rows ->
                val labels = detectionRepository.getSpeciesLabelsForSessions(
                    rows.map { it.session.id }
                )
                rows.map { row ->
                    SessionRecordItem(
                        session = row.session,
                        sampleCount = row.totalSamples,
                        speciesLabels = labels[row.session.id].orEmpty(),
                        totalEpg = row.totalEpg,
                    )
                }
            },
        )
    }
}
