package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.MyCoverage
import com.agarthavision.domain.model.PeriodWindows
import com.agarthavision.domain.repository.CoverageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Combines town-level coverage counts with the offline area directory into one [MyCoverage]
 * per subscription. The directory is loaded once per subscription (it doesn't change at
 * runtime) and combined with the live town-coverage flow.
 *
 * A null/blank `userId` is the caller's job to branch on (see `DashboardViewModel`'s
 * `userIdFlow.flatMapLatest` convention for `ObserveHomeKpisUseCase`/`ObserveFindingsUseCase`)
 * — this use case always expects a real id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMyCoverageUseCase @Inject constructor(
    private val coverageRepository: CoverageRepository,
    private val loadAreaDirectoryUseCase: LoadAreaDirectoryUseCase,
) {
    operator fun invoke(userId: String, period: HomePeriod, windows: PeriodWindows): Flow<Result<MyCoverage>> =
        flow { emit(loadAreaDirectoryUseCase()) }
            .flatMapLatest { directoryResult ->
                directoryResult.fold(
                    onSuccess = { directory ->
                        coverageRepository.observeTownCoverage(userId, windows.current).map { rows ->
                            Result.success(aggregateCoverage(period, rows, directory))
                        }
                    },
                    onFailure = { error ->
                        flow { emit(Result.failure<MyCoverage>(error)) }
                    },
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(Result.failure(error))
            }
}
