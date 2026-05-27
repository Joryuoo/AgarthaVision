package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.repository.SampleImageRepository
import java.io.File
import javax.inject.Inject

/**
 * Resolves the best available image source for a verified sample.
 */
class ResolveSampleImageSourceUseCase @Inject constructor(
    private val sampleImageRepository: SampleImageRepository,
) {
    /**
     * Prefers the local per-user JPEG, then falls back to a signed Storage URL.
     */
    suspend operator fun invoke(sample: Sample): SampleImageSource {
        val storagePath = sample.storagePath?.takeIf { it.isNotBlank() }
        return when {
            sample.filePath.isNotBlank() && File(sample.filePath).isFile -> SampleImageSource.Local(
                path = sample.filePath,
            )
            storagePath == null -> SampleImageSource.Unavailable(SampleImageUnavailableReason.NO_STORAGE_PATH)
            else -> createRemoteSource(storagePath)
        }
    }

    private suspend fun createRemoteSource(storagePath: String): SampleImageSource =
        runCatching {
            sampleImageRepository.createSignedImageUrl(storagePath)
        }.fold(
            onSuccess = { url ->
                SampleImageSource.RemoteSignedUrl(
                    url = url,
                    cacheKey = storagePath,
                )
            },
            onFailure = {
                SampleImageSource.Unavailable(SampleImageUnavailableReason.REMOTE_LOAD_FAILED)
            },
        )
}
