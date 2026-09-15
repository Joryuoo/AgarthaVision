package com.agarthavision.ui.image

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil.ComponentRegistry
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.agarthavision.domain.repository.SampleImageRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Disk-cache key correctness tests for [SampleImageFetcher].
 *
 * Robolectric is required because Coil's [Options] constructor takes an Android [Context].
 * Using a real [Options] (rather than a mock) is essential: a mocked Options.copy() returns
 * null by default, which would make the diskCacheKey assertion vacuous.
 *
 * Critical invariant: when fetching a remote image, [SampleImageFetcher] MUST pass
 * diskCacheKey = storagePath (the stable Storage path) to the delegate fetcher's Options,
 * NOT the signed URL (which contains a churning time-limited token). If the signed URL were
 * used as the disk key, every cache miss would trigger a re-download even though the image
 * content has not changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SampleImageFetcherDiskKeyTest {

    private val imageRepository: SampleImageRepository = mock()
    private val mockFetchResult: FetchResult = mock()
    private val mockDelegateFetcher: Fetcher = mock()
    private val imageLoader: ImageLoader = mock()
    private val components: ComponentRegistry = mock()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    init {
        whenever(imageLoader.components).thenReturn(components)
    }

    // ── remote branch ────────────────────────────────────────────────────────

    /**
     * The crux of the caching contract: the Options passed to the delegate fetcher
     * must carry diskCacheKey = storagePath, not the signed URL.
     */
    @Test
    fun `remote fetch delegates with diskCacheKey equal to storagePath not the signed URL`() = runTest {
        val storagePath = "users/uid-1/samples/sample-abc.jpg"
        val signedUrl = "https://storage.example.test/signed?token=CHURNING_TOKEN_CHANGES_EVERY_CALL"
        val realOptions = Options(context = context) // diskCacheKey starts null

        whenever(imageRepository.createSignedImageUrl(storagePath)).thenReturn(signedUrl)
        whenever(components.newFetcher(any<android.net.Uri>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = "/nonexistent/path.jpg", storagePath = storagePath)
        SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()

        val optionsCaptor = argumentCaptor<Options>()
        verify(components).newFetcher(any<android.net.Uri>(), optionsCaptor.capture(), any(), any())
        assertEquals(
            "diskCacheKey must be the stable storagePath, not the churning signed URL",
            storagePath,
            optionsCaptor.firstValue.diskCacheKey,
        )
    }

    /**
     * Complement: the signed URL itself must still be the data argument (so Coil fetches
     * the right remote resource), even though the disk key is the stable path.
     */
    @Test
    fun `remote fetch delegates with the signed URL as the data argument`() = runTest {
        val storagePath = "users/uid-1/samples/sample-abc.jpg"
        val signedUrl = "https://storage.example.test/signed?token=ABC123"
        val realOptions = Options(context = context)

        whenever(imageRepository.createSignedImageUrl(storagePath)).thenReturn(signedUrl)
        whenever(components.newFetcher(any<android.net.Uri>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = null, storagePath = storagePath)
        SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()

        val dataCaptor = argumentCaptor<android.net.Uri>()
        verify(components).newFetcher(dataCaptor.capture(), any(), any(), any())
        assertEquals(
            "data arg must be the signed URL (as Uri) so Coil actually fetches it",
            signedUrl,
            dataCaptor.firstValue.toString(),
        )
    }

    /**
     * storagePath starting blank/null means the signed URL is never requested and the
     * disk key is never set — fetch returns null cleanly.
     */
    @Test
    fun `both paths null — repository never called and no delegate invoked`() = runTest {
        val realOptions = Options(context = context)

        val ref = SampleImageRef(localPath = null, storagePath = null)
        val result = SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()

        assertNull(result)
        verify(imageRepository, never()).createSignedImageUrl(any())
        verify(components, never()).newFetcher(any<android.net.Uri>(), any(), any(), any())
    }

    // ── local branch ─────────────────────────────────────────────────────────

    /**
     * Local files are delegated with the original Options untouched — no diskCacheKey
     * override is applied (the Keyer already returned the local path as memory key).
     */
    @Test
    fun `local file fetch passes original options without altering diskCacheKey`() = runTest {
        val realOptions = Options(context = context) // diskCacheKey null
        val file = File.createTempFile("fetcher-disk-key-local", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        whenever(components.newFetcher(any<File>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = file.absolutePath, storagePath = "users/uid-1/sample.jpg")
        SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()

        val optionsCaptor = argumentCaptor<Options>()
        verify(components).newFetcher(any<File>(), optionsCaptor.capture(), any(), any())
        assertNull(
            "local branch must not set diskCacheKey on options",
            optionsCaptor.firstValue.diskCacheKey,
        )
    }

    /**
     * Repository must never be called when a real local file is present.
     */
    @Test
    fun `local file present — repository createSignedImageUrl never called`() = runTest {
        val realOptions = Options(context = context)
        val file = File.createTempFile("fetcher-disk-key-no-sign", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        whenever(components.newFetcher(any<File>(), any(), any(), any()))
            .thenReturn(mockDelegateFetcher to 0)
        whenever(mockDelegateFetcher.fetch()).thenReturn(mockFetchResult)

        val ref = SampleImageRef(localPath = file.absolutePath, storagePath = "users/uid-1/sample.jpg")
        SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()

        verify(imageRepository, never()).createSignedImageUrl(any())
    }

    // ── offline / error propagation ───────────────────────────────────────────

    /**
     * If signing throws (e.g. no network), the exception must propagate so Coil can render
     * an error placeholder — it must not be swallowed or replaced with a bogus URL.
     */
    @Test(expected = RuntimeException::class)
    fun `signing failure propagates as exception — not swallowed`() = runTest {
        val realOptions = Options(context = context)
        whenever(imageRepository.createSignedImageUrl(any()))
            .thenThrow(RuntimeException("offline"))

        val ref = SampleImageRef(localPath = "/nonexistent/path.jpg", storagePath = "users/uid-1/sample.jpg")
        SampleImageFetcher(ref, realOptions, imageLoader, imageRepository).fetch()
    }
}
