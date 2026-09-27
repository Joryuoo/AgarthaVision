package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.repository.BoundaryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Loads a province's town-level boundary shapes, or `null` if none are bundled for it. */
class LoadTownBoundariesUseCase @Inject constructor(
    private val boundaryRepository: BoundaryRepository,
) {
    fun cachedOrNull(provinceKey: String): BoundarySet? = boundaryRepository.cachedTownsOfOrNull(provinceKey)

    suspend operator fun invoke(provinceKey: String): Result<BoundarySet?> =
        runCatching {
            boundaryRepository.townsOf(provinceKey)
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }
}
