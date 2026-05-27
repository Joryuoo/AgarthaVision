package com.agarthavision.domain.usecase.records

/**
 * Image source resolved for the sample detail view.
 */
sealed interface SampleImageSource {
    /**
     * A verified JPEG available in local app storage.
     */
    data class Local(val path: String) : SampleImageSource

    /**
     * A short-lived remote URL with a stable cache key for Coil disk caching.
     */
    data class RemoteSignedUrl(val url: String, val cacheKey: String) : SampleImageSource

    /**
     * No usable local or remote image source is available.
     */
    data class Unavailable(val reason: SampleImageUnavailableReason) : SampleImageSource
}

/**
 * Reason a sample image cannot be displayed.
 */
enum class SampleImageUnavailableReason {
    /**
     * The selected sample does not exist or is not owned by the current user.
     */
    SAMPLE_NOT_FOUND,

    /**
     * The local file is absent and the sample has no remote Storage path.
     */
    NO_STORAGE_PATH,

    /**
     * A remote Storage path exists, but signing or loading failed.
     */
    REMOTE_LOAD_FAILED,
}
