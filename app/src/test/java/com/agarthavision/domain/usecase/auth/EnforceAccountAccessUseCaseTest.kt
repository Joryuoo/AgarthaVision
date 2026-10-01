package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.AccountAccess
import com.agarthavision.domain.model.SignedOutNotice
import com.agarthavision.domain.repository.AccountAccessRepository
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.SignedOutNoticeStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Pins when the phone wipes itself (14zcqntjph8): only on a refusal the server gave, never
 * offline, and in an order that keeps the wipe scoped and the reason readable.
 */
class EnforceAccountAccessUseCaseTest {

    private val authRepository: AuthRepository = mock {
        onBlocking { currentLocalUserId() } doReturn USER_ID
    }
    private val accountAccessRepository: AccountAccessRepository = mock()
    private val connectivityObserver: ConnectivityObserver = mock {
        on { currentlyOnline() } doReturn true
    }
    private val sessionManager: SessionManager = mock()
    private val wipe: WipeLocalAccountDataUseCase = mock {
        onBlocking { invoke(any()) } doReturn 0
    }
    private val noticeStore: SignedOutNoticeStore = mock()

    private val useCase = EnforceAccountAccessUseCase(
        authRepository = authRepository,
        accountAccessRepository = accountAccessRepository,
        connectivityObserver = connectivityObserver,
        sessionManager = sessionManager,
        wipeLocalAccountDataUseCase = wipe,
        signedOutNoticeStore = noticeStore,
    )

    @Test
    fun `a refusal wipes, records why, then signs out`() = runTest {
        whenever(accountAccessRepository.checkAccountAccess()).thenReturn(AccountAccess.REFUSED)
        whenever(wipe.invoke(USER_ID)).thenReturn(2)

        assertEquals(AccountAccess.REFUSED, useCase())

        // The wipe is scoped by the cached identity, and signOut is what clears it, so it has to
        // come last. The notice comes before it so a process death in between still explains.
        inOrder(sessionManager, wipe, noticeStore, authRepository) {
            verify(sessionManager).clearActive()
            verify(wipe).invoke(USER_ID)
            verify(noticeStore).record(SignedOutNotice(unsyncedKept = 2))
            verify(authRepository).signOut()
        }
    }

    @Test
    fun `an unknown answer changes nothing`() = runTest {
        // Offline at the server, a 5xx, a timeout: the medtech keeps working.
        whenever(accountAccessRepository.checkAccountAccess()).thenReturn(AccountAccess.UNKNOWN)

        useCase()

        verifyNothingRemoved()
    }

    @Test
    fun `an allowed account changes nothing`() = runTest {
        whenever(accountAccessRepository.checkAccountAccess()).thenReturn(AccountAccess.ALLOWED)

        useCase()

        verifyNothingRemoved()
    }

    @Test
    fun `offline, the server is not even asked`() = runTest {
        whenever(connectivityObserver.currentlyOnline()).thenReturn(false)

        assertEquals(AccountAccess.UNKNOWN, useCase())

        verify(accountAccessRepository, never()).checkAccountAccess()
        verifyNothingRemoved()
    }

    @Test
    fun `with nobody signed in there is nothing to check`() = runTest {
        whenever(authRepository.currentLocalUserId()).thenReturn(null)

        assertEquals(AccountAccess.UNKNOWN, useCase())

        verify(accountAccessRepository, never()).checkAccountAccess()
        verifyNothingRemoved()
    }

    private suspend fun verifyNothingRemoved() {
        verify(wipe, never()).invoke(any())
        verify(noticeStore, never()).record(any())
        verify(authRepository, never()).signOut()
    }

    private companion object {
        const val USER_ID = "medtech-1"
    }
}
