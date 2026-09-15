package com.agarthavision.ui.image

import coil.ComponentRegistry
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.agarthavision.domain.repository.SampleImageRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SampleImageFetcher].
 *
 * The critical cache-correctness behavior is whether [SampleImageRepository.createSignedImageUrl]
 * is called (and with the right path). The newFetcher delegation path is exercised by verifying
 * the fetcher returned by [SampleImageFetcher.Factory] is correct and the signing gate is
 * respected.
 *
 * For tests that do NOT reach newFetcher (cases c, d), the assertions are hermetic.
 * For tests that DO reach newFetcher (cases a, b), we stub ComponentRegistry.newFetcher so the
 * test stays self-contained (mock-maker-inline is enabled in this project).
 *
 * Robolectric is required because production code calls url.toUri() (android.net.Uri.parse),
 * which is an Android API unavailable in a plain JVM test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SampleImageFetcherTest {

    private val imageRepository: SampleImageRepository = mock()
    private val options: Options = mock()
    private val mockFetchResult: FetchResult = mock()
    private val mockDelegateFetcher: Fetcher = mock()
    private val imageLoader: ImageLoader = mock()
    private val components: ComponentRegistry = mock()

    init {
        whenever(imageLoader.components).thenReturn(components)
    }

    // (a) local file exists -> createSignedImageUrl NOT called
    @Test
    fun `local file exists delegates to file fetcher without calling repository`() = runTest {
        val file = File.createTempFile("fetcher-test-a", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        whenever(components.newFetcher(any<File>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = file.absolutePath, storagePath = "user/sample.jpg")
        val fetcher = SampleImageFetcher(ref, options, imageLoader, imageRepository)
        fetcher.fetch()

        verify(imageRepository, never()).createSignedImageUrl(any())
    }

    // (b) blank/absent local + valid storagePath -> createSignedImageUrl(path) called exactly once
    @Test
    fun `missing local file calls createSignedImageUrl with storagePath exactly once`() = runTest {
        val storagePath = "user/sample.jpg"
        val signedUrl = "https://example.test/signed"
        whenever(imageRepository.createSignedImageUrl(storagePath)).thenReturn(signedUrl)
        whenever(components.newFetcher(any<android.net.Uri>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = "/missing/sample.jpg", storagePath = storagePath)
        val fetcher = SampleImageFetcher(ref, options, imageLoader, imageRepository)
        fetcher.fetch()

        // Exactly once — not zero, not twice
        verify(imageRepository, times(1)).createSignedImageUrl(storagePath)
    }

    // (c) both blank -> fetch returns null, repository never called
    @Test
    fun `both localPath and storagePath null returns null without calling repository`() = runTest {
        val ref = SampleImageRef(localPath = null, storagePath = null)
        val fetcher = SampleImageFetcher(ref, options, imageLoader, imageRepository)

        val result = fetcher.fetch()

        assertNull(result)
        verify(imageRepository, never()).createSignedImageUrl(any())
    }

    @Test
    fun `both localPath and storagePath blank returns null without calling repository`() = runTest {
        val ref = SampleImageRef(localPath = "", storagePath = "")
        val fetcher = SampleImageFetcher(ref, options, imageLoader, imageRepository)

        val result = fetcher.fetch()

        assertNull(result)
        verify(imageRepository, never()).createSignedImageUrl(any())
    }

    // (d) createSignedImageUrl throws -> fetch propagates (does not swallow)
    @Test(expected = RuntimeException::class)
    fun `createSignedImageUrl throws propagates exception`() = runTest {
        val storagePath = "user/sample.jpg"
        whenever(imageRepository.createSignedImageUrl(storagePath))
            .thenThrow(RuntimeException("offline"))

        val ref = SampleImageRef(localPath = "/missing/sample.jpg", storagePath = storagePath)
        val fetcher = SampleImageFetcher(ref, options, imageLoader, imageRepository)
        fetcher.fetch()
    }

    // Factory creates SampleImageFetcher for SampleImageRef data
    @Test
    fun `Factory creates SampleImageFetcher for SampleImageRef`() {
        val ref = SampleImageRef(localPath = null, storagePath = "user/sample.jpg")
        val lazyRepository = dagger.Lazy { imageRepository }
        val factory = SampleImageFetcher.Factory(lazyRepository)

        val fetcher = factory.create(ref, options, imageLoader)

        assert(fetcher is SampleImageFetcher)
    }
}
