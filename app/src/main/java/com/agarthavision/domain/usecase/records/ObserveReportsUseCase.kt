package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.ReportRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Filter and pagination query for the cross-patient reports browser.
 */
data class ReportsQuery(
    val species: EggSpecies? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val limit: Int = 20,
    val offset: Int = 0,
)

/**
 * Result returned by [ObserveReportsUseCase].
 */
data class ReportsResult(
    val items: List<Report> = emptyList(),
    val totalCount: Int = 0,
)

/**
 * Observes reports across all sessions and patients belonging to the current user.
 * Supports species filtering, date range filtering, free-text search, and pagination.
 */
class ObserveReportsUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val reportRepository: ReportRepository,
) {
    operator fun invoke(query: ReportsQuery): Flow<ReportsResult> = flow {
        val userId = authRepository.currentLocalUserId()
        if (userId == null) {
            emitAll(flowOf(ReportsResult()))
            return@flow
        }

        val zone = ZoneId.systemDefault()
        val startMillis = query.startDate
            ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val endMillis = query.endDate
            ?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)?.toEpochMilli()
        val speciesNeedle = query.species?.canonicalClass

        val escapedQuery = query.searchQuery
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        val reportsFlow = reportRepository.observeFiltered(
            userId = userId,
            startMillis = startMillis,
            endMillis = endMillis,
            species = speciesNeedle,
            query = escapedQuery,
            limit = query.limit,
            offset = query.offset,
        )

        val countFlow = reportRepository.observeFilteredCount(
            userId = userId,
            startMillis = startMillis,
            endMillis = endMillis,
            species = speciesNeedle,
            query = escapedQuery,
        )

        emitAll(
            combine(reportsFlow, countFlow) { items, count ->
                ReportsResult(items = items, totalCount = count)
            }
        )
    }
}
