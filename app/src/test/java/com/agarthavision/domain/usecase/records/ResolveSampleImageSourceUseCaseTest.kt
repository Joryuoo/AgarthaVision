package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.repository.SampleImageRepository
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveSampleImageSourceUseCaseTest {
    @Test
    fun `uses local file when it exists`() = runTest {
        val image = File.createTempFile("sample-1", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val repository = FakeSampleImageRepository()
        val useCase = ResolveSampleImageSourceUseCase(repository)

        val source = useCase(sample(filePath = image.absolutePath, storagePath = "user-1/sample-1.jpg"))

        assertEquals(SampleImageSource.Local(image.absolutePath), source)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `falls back to signed url when local file is missing and storage path exists`() = runTest {
        val repository = FakeSampleImageRepository(signedUrl = "https://example.test/signed")
        val useCase = ResolveSampleImageSourceUseCase(repository)

        val source = useCase(sample(filePath = "/missing/sample-1.jpg", storagePath = "user-1/sample-1.jpg"))

        assertEquals(
            SampleImageSource.RemoteSignedUrl(
                url = "https://example.test/signed",
                cacheKey = "user-1/sample-1.jpg",
            ),
            source,
        )
        assertEquals(1, repository.calls)
        assertEquals("user-1/sample-1.jpg", repository.lastStoragePath)
    }

    @Test
    fun `returns unavailable when no local file or storage path exists`() = runTest {
        val repository = FakeSampleImageRepository()
        val useCase = ResolveSampleImageSourceUseCase(repository)

        val source = useCase(sample(filePath = "/missing/sample-1.jpg", storagePath = null))

        assertTrue(source is SampleImageSource.Unavailable)
        assertEquals(0, repository.calls)
    }

    private class FakeSampleImageRepository(
        private val signedUrl: String = "https://example.test/image.jpg",
    ) : SampleImageRepository {
        var calls = 0
        var lastStoragePath: String? = null

        override suspend fun createSignedImageUrl(storagePath: String): String {
            calls += 1
            lastStoragePath = storagePath
            return signedUrl
        }
    }
}

private fun sample(
    filePath: String,
    storagePath: String?,
): Sample =
    Sample(
        id = "sample-1",
        userId = "user-1",
        timestamp = 1_000L,
        verifiedAt = 2_000L,
        deviceId = "device-1",
        sessionId = "session-1",
        filePath = filePath,
        storagePath = storagePath,
        latitude = 10.0,
        longitude = 20.0,
        accuracyMeters = 5f,
        status = SampleStatus.SYNCED,
    )
