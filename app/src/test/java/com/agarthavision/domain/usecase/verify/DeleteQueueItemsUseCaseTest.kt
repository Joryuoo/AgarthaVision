package com.agarthavision.domain.usecase.verify

import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.domain.model.SampleStatus
import com.agarthavision.domain.usecase.capture.DeleteFlaggedSampleUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteQueueItemsUseCaseTest {

    private val sampleDao: SampleDao = mock()
    private val deleteFlaggedSampleUseCase: DeleteFlaggedSampleUseCase = mock()
    private val syncPendingDataUseCase: SyncPendingDataUseCase = mock()

    private val useCase = DeleteQueueItemsUseCase(
        sampleDao = sampleDao,
        deleteFlaggedSampleUseCase = deleteFlaggedSampleUseCase,
        syncPendingDataUseCase = syncPendingDataUseCase,
    )

    private fun sample(
        id: String,
        status: SampleStatus,
        deletedAt: Long? = null,
    ) = SampleEntity(
        sampleId = id,
        sessionId = "session-1",
        userId = "user-1",
        deviceId = "device-1",
        timestamp = 1_000L,
        imagePath = "/tmp/$id.jpg",
        gpsLatitude = null,
        gpsLongitude = null,
        gpsAccuracy = null,
        status = status.value,
        deletedAt = deletedAt,
    )

    @Test
    fun `an unverified frame is hard deleted`() = runTest {
        // Nothing has been asserted about it and its JPEG is the only artefact. C8's local
        // exception permits discarding a flagged frame before submission.
        whenever(sampleDao.getSampleByIdIncludingDeleted("a"))
            .thenReturn(sample("a", SampleStatus.FLAGGED))

        val summary = useCase(setOf("a")).getOrThrow()

        verify(deleteFlaggedSampleUseCase).invoke("a")
        verify(sampleDao, never()).tombstoneSample(any(), any(), any())
        assertEquals(1, summary.hardDeleted)
        assertEquals(0, summary.tombstoned)
    }

    @Test
    fun `a verified sample is tombstoned and never hard deleted`() = runTest {
        // The C8 regression. detections doubles as the retraining corpus, so a verified
        // sample, its detections and its Storage object are never destroyed - the medtech gets
        // the duplicate out of their way, and the corpus keeps what it needs.
        whenever(sampleDao.getSampleByIdIncludingDeleted("b"))
            .thenReturn(sample("b", SampleStatus.SYNCED))

        val summary = useCase(setOf("b")).getOrThrow()

        verify(sampleDao).tombstoneSample(
            sampleId = eq("b"),
            deletedAt = any(),
            // Back to VERIFIED so the tombstone itself re-enters the sync queue. Without
            // this the delete would never leave the device.
            status = eq(SampleStatus.VERIFIED.value),
        )
        verify(deleteFlaggedSampleUseCase, never()).invoke(any())
        verify(sampleDao, never()).deleteSample(any())
        assertEquals(0, summary.hardDeleted)
        assertEquals(1, summary.tombstoned)
    }

    @Test
    fun `a mixed batch takes both branches and syncs once`() = runTest {
        whenever(sampleDao.getSampleByIdIncludingDeleted("a"))
            .thenReturn(sample("a", SampleStatus.FLAGGED))
        whenever(sampleDao.getSampleByIdIncludingDeleted("b"))
            .thenReturn(sample("b", SampleStatus.VERIFIED))

        val summary = useCase(setOf("a", "b")).getOrThrow()

        assertEquals(1, summary.hardDeleted)
        assertEquals(1, summary.tombstoned)
        assertEquals(2, summary.total)
        // Once for the batch, not once per item: per item it is a push per tap on a slow link.
        verify(syncPendingDataUseCase, org.mockito.kotlin.times(1)).invoke()
    }

    @Test
    fun `deleting an already tombstoned sample is a no-op`() = runTest {
        // A concurrent delete must not fail the batch or re-stamp the timestamp.
        whenever(sampleDao.getSampleByIdIncludingDeleted("c"))
            .thenReturn(sample("c", SampleStatus.SYNCED, deletedAt = 5_000L))

        val summary = useCase(setOf("c")).getOrThrow()

        verify(sampleDao, never()).tombstoneSample(any(), any(), any())
        assertEquals(0, summary.total)
    }

    @Test
    fun `a sample that has vanished is skipped rather than failing the batch`() = runTest {
        whenever(sampleDao.getSampleByIdIncludingDeleted("gone")).thenReturn(null)

        val summary = useCase(setOf("gone")).getOrThrow()

        assertEquals(0, summary.total)
    }

    @Test
    fun `a hard-delete-only batch does not trigger a sync`() = runTest {
        // Nothing reached Supabase in the first place, so there is nothing to push.
        whenever(sampleDao.getSampleByIdIncludingDeleted("a"))
            .thenReturn(sample("a", SampleStatus.FLAGGED))

        useCase(setOf("a"))

        verify(syncPendingDataUseCase, never()).invoke()
    }
}
