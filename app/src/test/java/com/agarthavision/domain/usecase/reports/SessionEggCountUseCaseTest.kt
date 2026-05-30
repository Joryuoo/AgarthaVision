package com.agarthavision.domain.usecase.reports

import com.agarthavision.domain.model.EggCount
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionEggCountUseCaseTest {
    @Test
    fun `returns empty counts when no user session`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn(null)

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(0, result.totalEggCount)
        assertEquals(0, result.epg)
        assertEquals(emptyList<EggCount>(), result.counts)
    }

    @Test
    fun `computes total eggs and epg from confirmed counts`() = runTest {
        val authRepository: AuthRepository = mock()
        val detectionRepository: DetectionRepository = mock()
        whenever(authRepository.getCurrentUserId()).thenReturn("user-1")
        whenever(detectionRepository.getConfirmedEggCountsForSession("session-1", "user-1")).thenReturn(
            listOf(EggCount("Ascaris", 2), EggCount("Trichuris", 1)),
        )

        val useCase = SessionEggCountUseCase(authRepository, detectionRepository)
        val result = useCase("session-1")

        assertEquals(3, result.totalEggCount)
        assertEquals(72, result.epg)
        assertEquals(2, result.counts.size)
    }
}
