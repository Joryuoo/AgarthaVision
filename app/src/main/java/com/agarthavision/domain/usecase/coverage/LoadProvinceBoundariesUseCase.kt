package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.repository.BoundaryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Loads the 85 province-level boundary shapes for the offline map. */
class LoadProvinceBoundariesUseCase @Inject constructor(
    private val boundaryRepository: BoundaryRepository,
) {
    fun cachedOrNull(): BoundarySet? = boundaryRepository.cachedProvincesOrNull()

    suspend operator fun invoke(): Result<BoundarySet> =
        runCatching {
            boundaryRepository.provinces()
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }
}
