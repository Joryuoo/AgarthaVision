package com.agarthavision.ui.sessions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.GenerateSessionLabelUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionsFlow = MutableStateFlow<List<SessionWithStats>>(emptyList())
    private val countsFlow = MutableStateFlow(SessionsCounts())
    private val sessionRepository: SessionRepository = mock<SessionRepository>().also {
        // VM now uses observeVisibleSessionsPage / observeVisibleSessionsCounts.
        whenever(
            it.observeVisibleSessionsPage(
                anyOrNull(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), any(),
            ),
        )
            .thenReturn(sessionsFlow)
        whenever(
            it.observeVisibleSessionsCounts(
                anyOrNull(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(),
            ),
        )
            .thenReturn(countsFlow)
        // Keep the old stub so any residual call doesn't NPE (defensive).
        whenever(it.observeSessionsWithStats(any(), any())).thenReturn(sessionsFlow)
        // Unstubbed, this suspend fun returns null through Mockito's default answer, which
        // NPEs when unboxed to Boolean and silently kills onCreateSession's coroutine.
        it.stub {
            onBlocking { isSessionLabelTaken(any(), any(), anyOrNull()) } doReturn false
        }
    }
    private val sessionManager: SessionManager = mock {
        on { state } doReturn MutableStateFlow<SessionState>(SessionState.Idle)
    }
    // Called from the VM's init. An unstubbed mock returns null for a non-null Result and
    // takes down every test here, so it is stubbed even though the label is not asserted.
    private val generateSessionLabelUseCase: GenerateSessionLabelUseCase = mock {
        onBlocking { invoke(any()) } doReturn Result.success("C.G.-0730600000-001")
    }
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                MutableStateFlow(LocalIdentity(userId = "user-1", email = "user@example.com")),
            )
        }

    /**
     * The screen is reached at `patients/{patientId}`, so the patient a new session belongs
     * to comes off the route rather than out of the sheet. Pass a null id to model a route
     * that carries none.
     */
    private fun viewModel(patientId: String? = PATIENT_ID) = SessionsViewModel(
        sessionRepository = sessionRepository,
        sessionManager = sessionManager,
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        generateSessionLabelUseCase = generateSessionLabelUseCase,
        savedStateHandle = SavedStateHandle(
            if (patientId == null) emptyMap() else mapOf("patientId" to patientId),
        ),
    )

    @Test
    fun `state mirrors active and recent sessions`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            assertFalse(snapshot.isLoading)

            sessionsFlow.value = listOf(makeSessionStats("session-1"))
            val withSession = awaitItem()
            assertEquals(1, withSession.sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCreateSession emits navigate event`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        whenever(sessionManager.startSession(any(), any()))
            .thenReturn(makeSessionEntity("session-1"))

        vm.events.test {
            vm.onCreateSession("Smear 1")
            advanceUntilIdle()
            val event = awaitItem() as SessionsEvent.NavigateToCapture
            assertEquals("session-1", event.sessionId)
        }
    }

    @Test
    fun `onCreateSession attributes the session to the patient from the route`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            whenever(sessionManager.startSession(any(), any()))
                .thenReturn(makeSessionEntity("session-1"))

            vm.onCreateSession("Smear 1")
            advanceUntilIdle()

            // sessions.patient_id is NOT NULL with a foreign key onto patients, so getting
            // this wrong is an insert failure rather than a mis-filed smear.
            verify(sessionManager).startSession(label = eq("Smear 1"), patientId = eq(PATIENT_ID))
        }

    @Test
    fun `onCreateSession refuses when the route carries no patient`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel(patientId = null)
            vm.state.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                // The *list* refuses first now: with no patient to scope the query to there
                // is nothing to ask Room for, so the screen resolves to an error rather than
                // an empty list that would read as "this patient has no smears".
                assertTrue(snapshot.errorMessage?.contains("patient") == true)

                // onCreateSession refuses for the same reason. It cannot be asserted with
                // another awaitItem(): the state is already carrying this error, and a
                // StateFlow does not re-emit an equal value.
                vm.onCreateSession("Smear 1")
                assertTrue(vm.state.value.errorMessage?.contains("patient") == true)
                cancelAndIgnoreRemainingEvents()
            }
            // Better to refuse than to write a session that fails its foreign key on insert.
            verify(sessionManager, never()).startSession(any(), any())
        }

    @Test
    fun `onCreateSession rejects blank labels`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            vm.onCreateSession("   ")
            val withError = awaitItem()
            assertTrue(withError.errorMessage?.contains("Label") == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        private const val PATIENT_ID = "patient-1"
    }

    private fun makeSessionStats(id: String): SessionWithStats =
        SessionWithStats(
            session = Session(
                id = id,
                userId = "user-1",
                deviceId = "device-1",
                startedAt = Instant.EPOCH.toEpochMilli(),
                patientId = "patient-1",
                label = "Smear 1",
            ),
            totalSamples = 0,
            verifiedSamples = 0,
            unverifiedSamples = 0,
            totalEggs = 0,
        )

    private fun makeSessionEntity(id: String): SessionEntity = SessionEntity(
        sessionId = id,
        userId = "user-1",
        patientId = PATIENT_ID,
        deviceId = "device-1",
        startedAt = Instant.EPOCH.toEpochMilli(),
        label = "Smear 1",
    )
}
