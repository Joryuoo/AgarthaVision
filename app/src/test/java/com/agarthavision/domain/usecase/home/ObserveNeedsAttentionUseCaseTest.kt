package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.PendingSyncCounts
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.settings.ObservePendingSyncCountsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ObserveNeedsAttentionUseCaseTest {

    private val observePendingSyncCountsUseCase: ObservePendingSyncCountsUseCase = mock()
    private val sampleRepository: SampleRepository = mock()
    private val sessionRepository: SessionRepository = mock()

    private val useCase = ObserveNeedsAttentionUseCase(
        observePendingSyncCountsUseCase = observePendingSyncCountsUseCase,
        sampleRepository = sampleRepository,
        sessionRepository = sessionRepository,
    )

    private val userId = "user-123"
    private val activeSessionId = "session-active"

    @Test
    fun `when all items are zero, isEmpty is true`() = runTest {
        whenever(observePendingSyncCountsUseCase(userId)).thenReturn(
            flowOf(PendingSyncCounts(0, 0, 0, 0, 0)),
        )
        whenever(sampleRepository.observeFlaggedCount(userId)).thenReturn(flowOf(0))
        whenever(sessionRepository.observeEmptySessionCount(userId, activeSessionId)).thenReturn(flowOf(0))

        val result = useCase(userId, activeSessionId).first()

        assertEquals(0, result.unsyncedItems)
        assertEquals(0, result.framesToReview)
        assertEquals(0, result.emptySessions)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `combines unsynced items from pending sync counts`() = runTest {
        // totalPending (1+2+3+0=6) + failed (2) = totalUnsynced = 8
        whenever(observePendingSyncCountsUseCase(userId)).thenReturn(
            flowOf(PendingSyncCounts(1, 2, 3, 0, 2)),
        )
        whenever(sampleRepository.observeFlaggedCount(userId)).thenReturn(flowOf(5))
        whenever(sessionRepository.observeEmptySessionCount(userId, activeSessionId)).thenReturn(flowOf(3))

        val result = useCase(userId, activeSessionId).first()

        assertEquals(8, result.unsyncedItems)
        assertEquals(5, result.framesToReview)
        assertEquals(3, result.emptySessions)
        assertFalse(result.isEmpty)
    }
}
