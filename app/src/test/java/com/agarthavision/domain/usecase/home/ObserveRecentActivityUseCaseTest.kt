package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.sync.LastSyncStore
import com.agarthavision.domain.sync.SyncCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveRecentActivityUseCaseTest {

    private val sampleRepository: SampleRepository = mock()
    private val sessionRepository: SessionRepository = mock()
    private val patientRepository: PatientRepository = mock()
    private val lastSyncStore: LastSyncStore = mock()

    private val useCase = ObserveRecentActivityUseCase(
        sampleRepository = sampleRepository,
        sessionRepository = sessionRepository,
        patientRepository = patientRepository,
        lastSyncStore = lastSyncStore,
    )

    private fun stubSources(
        captured: List<ActivityItem.FramesCaptured> = emptyList(),
        verified: List<ActivityItem.FramesVerified> = emptyList(),
        started: List<ActivityItem.SessionStarted> = emptyList(),
        added: List<ActivityItem.PatientAdded> = emptyList(),
        lastSync: SyncCompletion? = null,
    ) {
        whenever(sampleRepository.observeCaptureActivity(any(), any())).thenReturn(flowOf(captured))
        whenever(sampleRepository.observeVerifyActivity(any(), any())).thenReturn(flowOf(verified))
        whenever(sessionRepository.observeStartedActivity(any(), any())).thenReturn(flowOf(started))
        whenever(patientRepository.observeAddedActivity(any(), any())).thenReturn(flowOf(added))
        whenever(lastSyncStore.observe(any())).thenReturn(flowOf(lastSync))
    }

    @Test
    fun `interleaves items from all sources sorted by occurredAt descending`() = runTest {
        stubSources(
            captured = listOf(
                ActivityItem.FramesCaptured("s-1", "Smear 1", 2, occurredAt = 1_000L),
            ),
            verified = listOf(
                ActivityItem.FramesVerified("s-1", "Smear 1", 1, occurredAt = 5_000L),
            ),
            started = listOf(
                ActivityItem.SessionStarted("s-2", "Smear 2", occurredAt = 3_000L),
            ),
            added = listOf(
                ActivityItem.PatientAdded("p-1", "R.*, J*.", occurredAt = 4_000L),
            ),
            lastSync = SyncCompletion(completedAtMillis = 2_000L, itemsSynced = 7),
        )

        val result = useCase("user-a", limit = 10).first()

        val order = result.map { it.occurredAt }
        assertEquals(listOf(5_000L, 4_000L, 3_000L, 2_000L, 1_000L), order)
    }

    @Test
    fun `truncates the merged list to limit`() = runTest {
        stubSources(
            captured = listOf(
                ActivityItem.FramesCaptured("s-1", "Smear 1", 1, occurredAt = 1_000L),
                ActivityItem.FramesCaptured("s-1", "Smear 1", 1, occurredAt = 2_000L),
            ),
            verified = listOf(
                ActivityItem.FramesVerified("s-1", "Smear 1", 1, occurredAt = 3_000L),
            ),
            started = listOf(
                ActivityItem.SessionStarted("s-2", "Smear 2", occurredAt = 4_000L),
            ),
        )

        val result = useCase("user-a", limit = 2).first()

        assertEquals(2, result.size)
        assertEquals(listOf(4_000L, 3_000L), result.map { it.occurredAt })
    }

    @Test
    fun `omits SyncFinished when itemsSynced is 0`() = runTest {
        stubSources(lastSync = SyncCompletion(completedAtMillis = 9_000L, itemsSynced = 0))

        val result = useCase("user-a", limit = 10).first()

        assertEquals(emptyList<ActivityItem>(), result)
    }

    @Test
    fun `omits SyncFinished when lastSyncStore emits null`() = runTest {
        stubSources(lastSync = null)

        val result = useCase("user-a", limit = 10).first()

        assertEquals(emptyList<ActivityItem>(), result)
    }

    @Test
    fun `a stale sync completion still surfaces at its sorted position`() = runTest {
        // No time-window filter is applied to SyncFinished: an old completion sorts by its own
        // occurredAt and only drops off once newer items push it past `limit`.
        stubSources(
            captured = listOf(
                ActivityItem.FramesCaptured("s-1", "Smear 1", 1, occurredAt = 100_000L),
            ),
            lastSync = SyncCompletion(completedAtMillis = 1L, itemsSynced = 3),
        )

        val result = useCase("user-a", limit = 10).first()

        assertEquals(2, result.size)
        assertEquals(1L, (result.last() as ActivityItem.SyncFinished).occurredAt)
    }

    @Test
    fun `empty everywhere yields an empty list`() = runTest {
        stubSources()

        val result = useCase("user-a", limit = 10).first()

        assertEquals(emptyList<ActivityItem>(), result)
    }

    @Test
    fun `filters out items older than sinceMillis when sinceMillis is provided`() = runTest {
        stubSources(
            captured = listOf(
                ActivityItem.FramesCaptured("s-1", "Smear 1", 1, occurredAt = 10_000L),
                ActivityItem.FramesCaptured("s-1", "Smear 1", 1, occurredAt = 4_000L),
            ),
            started = listOf(
                ActivityItem.SessionStarted("s-2", "Smear 2", occurredAt = 2_000L),
            ),
        )

        val result = useCase("user-a", limit = 10, sinceMillis = 5_000L).first()

        assertEquals(1, result.size)
        assertEquals(10_000L, result.first().occurredAt)
    }
}
