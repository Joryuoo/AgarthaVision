package com.agarthavision.domain.usecase.sessions

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.SessionSyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SetSessionClaimExemptUseCaseTest {

    private val sessionDao: SessionDao = mock()
    private val useCase = SetSessionClaimExemptUseCase(sessionDao)

    private fun session(userId: String?, status: SessionSyncStatus) = SessionEntity(
        sessionId = "s1",
        userId = userId,
        deviceId = "device-1",
        startedAt = 0L,
        endedAt = null,
        notes = null,
        label = "Smear",
        supabaseStatus = status.value,
        claimExempt = false,
    )

    @Test
    fun `opting out an unowned session sets the exempt flag`() = runTest {
        whenever(sessionDao.getSessionById("s1")).thenReturn(session(userId = null, status = SessionSyncStatus.PENDING))

        val result = useCase("s1", exempt = true)

        assertTrue(result.isSuccess)
        verify(sessionDao).setClaimExempt(eq("s1"), eq(true))
    }

    @Test
    fun `unlinking a still-pending claimed session reverts it to unowned and exempt`() = runTest {
        whenever(sessionDao.getSessionById("s1"))
            .thenReturn(session(userId = "user-1", status = SessionSyncStatus.PENDING))

        val result = useCase("s1", exempt = true)

        assertTrue(result.isSuccess)
        verify(sessionDao).updateSession(
            org.mockito.kotlin.check { updated ->
                assert(updated.userId == null)
                assert(updated.claimExempt)
            },
        )
    }

    @Test
    fun `unlinking an already-synced session fails because ownership is permanent`() = runTest {
        whenever(sessionDao.getSessionById("s1"))
            .thenReturn(session(userId = "user-1", status = SessionSyncStatus.SYNCED))

        val result = useCase("s1", exempt = true)

        assertTrue(result.isFailure)
    }
}
