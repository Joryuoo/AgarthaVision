package com.agarthavision.domain.usecase.auth

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimLocalDataUseCaseTest {

    private val sessionDao: SessionDao = mock()
    private val sampleDao: SampleDao = mock()
    private val reportDao: ReportDao = mock()

    private val useCase = ClaimLocalDataUseCase(sessionDao, sampleDao, reportDao)

    private fun unownedSession(id: String) = SessionEntity(
        sessionId = id,
        userId = null,
        deviceId = "device-1",
        startedAt = 0L,
        endedAt = null,
        notes = null,
        label = "Smear $id",
        supabaseStatus = "pending",
        claimExempt = false,
    )

    @Test
    fun `login-path claim assigns all claimable sessions and cascades to samples and reports`() =
        runTest {
            whenever(sessionDao.getClaimableSessions())
                .thenReturn(listOf(unownedSession("s1"), unownedSession("s2")))

            val result = useCase("user-1")

            assertEquals(2, result.getOrNull())
            verify(sessionDao).claimUnownedSessions("user-1")
            verify(sampleDao).claimSamplesForSessions(eq(listOf("s1", "s2")), eq("user-1"))
            verify(reportDao).claimReportsForSessions(eq(listOf("s1", "s2")), eq("user-1"))
        }

    @Test
    fun `explicit-ids claim only touches the given sessions`() =
        runTest {
            val result = useCase("user-1", sessionIds = listOf("s1"))

            assertEquals(1, result.getOrNull())
            verify(sessionDao).claimSession("s1", "user-1")
            verify(sessionDao, never()).claimUnownedSessions(org.mockito.kotlin.any())
            verify(sampleDao).claimSamplesForSessions(eq(listOf("s1")), eq("user-1"))
        }

    @Test
    fun `claim is a no-op when there is nothing to claim`() =
        runTest {
            whenever(sessionDao.getClaimableSessions()).thenReturn(emptyList())

            val result = useCase("user-1")

            assertEquals(0, result.getOrNull())
            verify(sessionDao, never()).claimUnownedSessions(org.mockito.kotlin.any())
            verify(sampleDao, never()).claimSamplesForSessions(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }
}
