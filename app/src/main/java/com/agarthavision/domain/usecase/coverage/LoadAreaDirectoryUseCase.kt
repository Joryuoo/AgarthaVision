package com.agarthavision.domain.usecase.coverage

import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.repository.BoundaryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Loads the town/province directory (independent of loaded geometry) for coverage rollups. */
class LoadAreaDirectoryUseCase @Inject constructor(
    private val boundaryRepository: BoundaryRepository,
) {
    suspend operator fun invoke(): Result<AreaDirectory> =
        runCatching {
            boundaryRepository.directory()
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
        }
}
