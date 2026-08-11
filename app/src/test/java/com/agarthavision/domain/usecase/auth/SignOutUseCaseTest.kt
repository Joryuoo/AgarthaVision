package com.agarthavision.domain.usecase.auth

import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SignOutUseCaseTest {
    private val authRepository: AuthRepository = mock()
    private val sessionManager: SessionManager = mock()

    private val useCase = SignOutUseCase(
        authRepository = authRepository,
        sessionManager = sessionManager,
    )

    @Test
    fun `signs out when no capture session is active`() = runTest {
        whenever(sessionManager.state).thenReturn(MutableStateFlow(SessionState.Idle))

        val result = useCase()

        assertTrue(result.isSuccess)
        verify(authRepository).signOut()
    }

    @Test
    fun `fails without signing out while a capture session is active`() = runTest {
        val active = SessionState.Active(
            session = mock<SessionEntity>(),
            startedAt = java.time.Instant.now(),
        )
        whenever(sessionManager.state).thenReturn(MutableStateFlow(active))

        val result = useCase()

        assertTrue(result.isFailure)
        verify(authRepository, never()).signOut()
    }
}
