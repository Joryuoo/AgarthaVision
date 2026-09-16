package com.agarthavision.ui.image

import androidx.core.net.toUri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.agarthavision.domain.repository.SampleImageRepository
import dagger.Lazy
import java.io.File

class SampleImageFetcher(
    private val ref: SampleImageRef,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val imageRepository: SampleImageRepository,
) : Fetcher {

    // Three returns (local hit, no-source guard, remote hit) read more clearly as guard
    // clauses than folded into one exit.
    @Suppress("ReturnCount")
    override suspend fun fetch(): FetchResult? {
        // 1. Local file wins
        val local = ref.localPath?.takeIf { it.isNotBlank() && File(it).isFile }
        if (local != null) {
            return imageLoader.components.newFetcher(File(local), options, imageLoader)
                ?.first
                ?.fetch()
        }

        // 2. Remote: sign lazily, delegate with STABLE disk key = storagePath
        val path = ref.storagePath?.takeIf { it.isNotBlank() } ?: return null
        val url = imageRepository.createSignedImageUrl(path) // suspend; if it throws (offline) let it propagate
        val stableOptions = options.copy(diskCacheKey = path)
        return imageLoader.components.newFetcher(url.toUri(), stableOptions, imageLoader)
            ?.first
            ?.fetch()
    }

    // Holds a Lazy so the Supabase-backed repository is constructed only when a SampleImageRef
    // is actually fetched (create() is called by Coil only for SampleImageRef data), not when
    // the app-wide ImageLoader is first built for any image (e.g. local-file verify screens).
    class Factory(private val imageRepository: Lazy<SampleImageRepository>) : Fetcher.Factory<SampleImageRef> {
        override fun create(data: SampleImageRef, options: Options, imageLoader: ImageLoader): Fetcher =
            SampleImageFetcher(data, options, imageLoader, imageRepository.get())
    }
}
