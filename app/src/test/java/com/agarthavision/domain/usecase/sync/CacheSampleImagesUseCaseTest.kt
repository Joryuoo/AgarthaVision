package com.agarthavision.domain.usecase.sync

import com.agarthavision.data.local.SampleImageStore
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.supabase.SampleRemoteDataSource
import com.agarthavision.domain.model.SampleStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Sample frames on the device, brought down by sync rather than on open (86d4by5n9).
 *
 * PB-15b made a synced sample openable *if there is signal at that moment*. Editing depends on
 * seeing the frame — with no image there is nothing to place a box against — so an uncached
 * sample is not one missing a picture, it is one that cannot be corrected. In a barangay with
 * no signal that is the whole feature failing, which is why the cache is filled while the radio
 * is already on and why [ImageCacheSummary.missing] is carried rather than swallowed.
 */
class CacheSampleImagesUseCaseTest {

    private val sampleDao: SampleDao = mock()
    private val sampleImageStore: SampleImageStore = mock()
    private val remote: SampleRemoteDataSource = mock()

    private val useCase = CacheSampleImagesUseCase(sampleDao, sampleImageStore, remote)

    private val userId = "user-1"

    private fun sample(id: String, verifiedAt: Long = 1_000L) = SampleEntity(
        sampleId = id,
        sessionId = "session-1",
        userId = userId,
        deviceId = "device-1",
        timestamp = verifiedAt,
        verifiedAt = verifiedAt,
        imagePath = "",
        storagePath = "$userId/$id.jpg",
        status = SampleStatus.SYNCED.value,
    )

    private fun pathOf(id: String) = "/files/users/$userId/samples/$id.jpg"

    @Test
    fun `a frame the device does not hold is downloaded and its row points at it`() = runTest {
        whenever(sampleDao.getCacheableSamples(userId)).thenReturn(listOf(sample("smp-1")))
        whenever(sampleImageStore.cachedPathOrNull(userId, "smp-1")).thenReturn(null)
        whenever(sampleImageStore.sizeOf(userId, "smp-1")).thenReturn(0L)
        whenever(remote.downloadSampleImage("$userId/smp-1.jpg")).thenReturn(byteArrayOf(1, 2, 3))
        whenever(sampleImageStore.persistJpeg(userId, "smp-1", byteArrayOf(1, 2, 3)))
            .thenReturn(pathOf("smp-1"))

        val summary = useCase(userId)

        assertEquals(1, summary.downloaded)
        assertTrue(summary.isComplete)
        // Downloading it is half the job. A row that does not know where its frame went is a
        // device that owns the image and cannot find it.
        verify(sampleDao).updateImagePath("smp-1", pathOf("smp-1"))
    }

    @Test
    fun `a frame already held is not fetched again`() = runTest {
        whenever(sampleDao.getCacheableSamples(userId)).thenReturn(listOf(sample("smp-1")))
        whenever(sampleImageStore.cachedPathOrNull(userId, "smp-1")).thenReturn(pathOf("smp-1"))
        whenever(sampleImageStore.sizeOf(userId, "smp-1")).thenReturn(120L)

        val summary = useCase(userId)

        assertEquals(0, summary.downloaded)
        assertTrue(summary.isComplete)
        verify(remote, never()).downloadSampleImage(any())
        // Still repaired: a pulled row arrives with image_path empty even when the file is
        // right there on disk.
        verify(sampleDao).updateImagePath("smp-1", pathOf("smp-1"))
    }

    @Test
    fun `an unreachable frame is counted, and does not cost the pass the rest`() = runTest {
        whenever(sampleDao.getCacheableSamples(userId))
            .thenReturn(listOf(sample("smp-1", 2_000L), sample("smp-2", 1_000L)))
        whenever(sampleImageStore.cachedPathOrNull(eq(userId), any())).thenReturn(null)
        whenever(sampleImageStore.sizeOf(eq(userId), any())).thenReturn(0L)
        whenever(remote.downloadSampleImage("$userId/smp-1.jpg"))
            .thenThrow(RuntimeException("object not found"))
        whenever(remote.downloadSampleImage("$userId/smp-2.jpg")).thenReturn(byteArrayOf(9))
        whenever(sampleImageStore.persistJpeg(eq(userId), eq("smp-2"), any()))
            .thenReturn(pathOf("smp-2"))

        val summary = useCase(userId)

        // One object gone must not cost every other frame the pass could still have brought
        // down, and the device must not then claim it is ready to work offline.
        assertEquals(1, summary.downloaded)
        assertEquals(1, summary.missing)
        assertEquals(false, summary.isComplete)
    }

    @Test
    fun `frames past the budget are evicted, oldest first`() = runTest {
        // Newest first is the order the DAO returns and the order the budget is spent in.
        whenever(sampleDao.getCacheableSamples(userId))
            .thenReturn(listOf(sample("new", 3_000L), sample("old", 1_000L)))
        whenever(sampleImageStore.cachedPathOrNull(userId, "new")).thenReturn(pathOf("new"))
        whenever(sampleImageStore.sizeOf(userId, "new"))
            .thenReturn(CacheSampleImagesUseCase.MAX_CACHE_BYTES)
        whenever(sampleImageStore.sizeOf(userId, "old")).thenReturn(1L)
        whenever(sampleImageStore.evict(userId, "old")).thenReturn(true)

        val summary = useCase(userId)

        assertEquals(1, summary.evicted)
        verify(sampleImageStore).evict(userId, "old")
        verify(sampleImageStore, never()).evict(userId, "new")
        // The row stops claiming a file that is gone; reopening falls back to Storage, which
        // is PB-15b's path and says so plainly when there is no signal.
        verify(sampleDao).updateImagePath("old", "")
        verify(remote, never()).downloadSampleImage(any())
    }

    @Test
    fun `an unpushed sample is never a candidate`() = runTest {
        // Enforced in SQL rather than here: `getCacheableSamples` requires a non-empty
        // storage_path, so a sample the server does not hold yet cannot reach the eviction
        // loop at all. Its local JPEG is the only copy in existence, and dropping it would be
        // deleting the clinical record (C8), not managing a cache.
        whenever(sampleDao.getCacheableSamples(userId)).thenReturn(emptyList())

        val summary = useCase(userId)

        assertEquals(ImageCacheSummary(), summary)
        verify(sampleImageStore, never()).evict(any(), any())
    }

    @Test
    fun `a first sync does not try to pull a year of history in one pass`() = runTest {
        val many = (1..CacheSampleImagesUseCase.MAX_IMAGES_PER_PASS + 5)
            .map { sample("smp-$it", verifiedAt = it.toLong()) }
        whenever(sampleDao.getCacheableSamples(userId)).thenReturn(many)
        whenever(sampleImageStore.cachedPathOrNull(eq(userId), any())).thenReturn(null)
        whenever(sampleImageStore.sizeOf(eq(userId), any())).thenReturn(0L)
        whenever(remote.downloadSampleImage(any())).thenReturn(byteArrayOf(1))
        whenever(sampleImageStore.persistJpeg(eq(userId), any(), any())).thenReturn(pathOf("x"))

        val summary = useCase(userId)

        // The ceiling defers rather than drops: the rest come down next pass, and until they
        // do the summary says so rather than reporting a completeness the device lacks.
        assertEquals(CacheSampleImagesUseCase.MAX_IMAGES_PER_PASS, summary.downloaded)
        assertEquals(5, summary.missing)
        assertEquals(false, summary.isComplete)
    }
}
