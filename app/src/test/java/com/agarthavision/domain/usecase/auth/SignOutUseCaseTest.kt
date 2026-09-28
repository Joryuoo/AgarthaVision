package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SignOutUseCaseTest {
    private val authRepository: AuthRepository = mock()
    private val sessionManager: SessionManager = mock()
    private val discardUnsyncedDataUseCase: DiscardUnsyncedDataUseCase = mock()

    private val useCase = SignOutUseCase(
        authRepository = authRepository,
        sessionManager = sessionManager,
        discardUnsyncedDataUseCase = discardUnsyncedDataUseCase,
    )

    @Test
    fun `signs out and detaches from the session`() = runTest {
        // This used to fail while a session was active, on the reasoning that the medtech
        // should end the smear first. Sessions do not end any more (86d4ab4vm), so that guard
        // was a permanent block on signing out. Detaching replaces it: the session stays open
        // and unfinished, and the medtech can come back to it.
        val result = useCase()

        assertTrue(result.isSuccess)
        verify(sessionManager).clearActive()
        verify(authRepository).signOut()
    }

    @Test
    fun `discards unsynced work before clearing the identity`() = runTest {
        // The dialog promises the medtech that unsynced rows will never reach the central
        // database. Discarding after signOut would break that promise silently: the discard is
        // scoped by the cached identity, and signOut is what removes it.
        whenever(authRepository.currentLocalUserId()).thenReturn(USER_ID)

        useCase()

        inOrder(discardUnsyncedDataUseCase, authRepository) {
            verify(discardUnsyncedDataUseCase).invoke(eq(USER_ID))
            verify(authRepository).signOut()
        }
    }

    @Test
    fun `discards nothing when no identity is cached`() = runTest {
        // Nothing has been attributed to anyone, so there is no owner to scope a discard by.
        whenever(authRepository.currentLocalUserId()).thenReturn(null)

        val result = useCase()

        assertTrue(result.isSuccess)
        verify(discardUnsyncedDataUseCase, never()).invoke(org.mockito.kotlin.any())
        verify(authRepository).signOut()
    }

    @Test
    fun `detaches before clearing the identity`() = runTest {
        // Order matters. Clearing the identity first would leave the session pointer aimed at
        // a smear the next launch may no longer be able to read.
        useCase()

        inOrder(sessionManager, authRepository) {
            verify(sessionManager).clearActive()
            verify(authRepository).signOut()
        }
    }

    private companion object {
        const val USER_ID = "medtech-1"
    }
}
