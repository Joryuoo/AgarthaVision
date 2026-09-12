package com.agarthavision.ui.sessions

import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.PsgcBarangay
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ClaimLocalDataUseCase
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.sessions.SearchBarangaysUseCase
import com.agarthavision.domain.usecase.sessions.SetSessionClaimExemptUseCase
import com.agarthavision.util.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
        whenever(it.observeVisibleSessionsPage(anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), any()))
            .thenReturn(sessionsFlow)
        whenever(it.observeVisibleSessionsCounts(anyOrNull(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(countsFlow)
        // Keep the old stub so any residual call doesn't NPE (defensive).
        whenever(it.observeSessionsWithStats(any(), any())).thenReturn(sessionsFlow)
    }
    private val sessionManager: SessionManager = mock()
    private val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase =
        mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(
                MutableStateFlow(LocalIdentity(userId = "user-1", email = "user@example.com")),
            )
        }
    private val setSessionClaimExemptUseCase: SetSessionClaimExemptUseCase = mock()
    private val claimLocalDataUseCase: ClaimLocalDataUseCase = mock()

    // The real use case over a mocked repository, so the minimum-query-length rule is
    // exercised here rather than stubbed away.
    private val psgcRepository: PsgcRepository = mock()
    private val searchBarangaysUseCase = SearchBarangaysUseCase(psgcRepository)

    private fun viewModel() = SessionsViewModel(
        sessionRepository = sessionRepository,
        sessionManager = sessionManager,
        observeLocalIdentityUseCase = observeLocalIdentityUseCase,
        setSessionClaimExemptUseCase = setSessionClaimExemptUseCase,
        claimLocalDataUseCase = claimLocalDataUseCase,
        searchBarangaysUseCase = searchBarangaysUseCase,
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
        whenever(sessionManager.startSession(any(), anyOrNull(), anyOrNull()))
            .thenReturn(makeSessionEntity("session-1"))
        selectBarangay(vm)

        vm.events.test {
            vm.onCreateSession("Smear 1", null)
            advanceUntilIdle()
            val event = awaitItem() as SessionsEvent.NavigateToCapture
            assertEquals("session-1", event.sessionId)
        }
    }

    @Test
    fun `onCreateSession passes the selected barangay through`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            whenever(sessionManager.startSession(any(), anyOrNull(), anyOrNull()))
                .thenReturn(makeSessionEntity("session-1"))
            selectBarangay(vm)

            vm.onCreateSession("Smear 1", null)
            advanceUntilIdle()

            verify(sessionManager).startSession(
                label = eq("Smear 1"),
                psgcBarangayCode = eq(LAHUG.code),
                notes = anyOrNull(),
            )
        }

    @Test
    fun `onCreateSession refuses a session with no barangay`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val vm = viewModel()
            vm.state.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading) {
                    snapshot = awaitItem()
                }
                vm.onCreateSession("Smear 1", null)
                val withError = awaitItem()
                // Asserted word for word, not with `contains`: this copy has to stay
                // identical to R.string.session_new_barangay_required, and a substring
                // match would let the two drift into two messages for one rule.
                assertEquals(
                    "Please select the patient's barangay to continue.",
                    withError.errorMessage,
                )
                cancelAndIgnoreRemainingEvents()
            }
            verify(sessionManager, never()).startSession(any(), anyOrNull(), anyOrNull())
        }

    @Test
    fun `barangay query debounces into one search`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val vm = viewModel()

            // A burst of keystrokes, as typing produces.
            vm.onBarangayQueryChanged("l")
            vm.onBarangayQueryChanged("la")
            vm.onBarangayQueryChanged("lah")
            vm.onBarangayQueryChanged("lahu")
            advanceUntilIdle()

            verify(psgcRepository).searchBarangays(query = eq("lahu"), limit = any())
        }

    @Test
    fun `selecting a barangay clears the query and results`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val vm = viewModel()
            selectBarangay(vm)

            vm.state.test {
                var snapshot = awaitItem()
                while (snapshot.isLoading || snapshot.selectedBarangay == null) {
                    snapshot = awaitItem()
                }
                assertEquals(LAHUG, snapshot.selectedBarangay)
                assertEquals("", snapshot.barangayQuery)
                assertTrue(snapshot.barangayResults.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearing the barangay drops the selection`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
            val vm = viewModel()
            selectBarangay(vm)

            vm.onBarangayCleared()
            advanceUntilIdle()

            vm.state.test {
                assertNull(awaitItem().selectedBarangay)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Drives the picker the way the sheet does: type, wait for results, tap one. */
    private suspend fun TestScope.selectBarangay(vm: SessionsViewModel) {
        whenever(psgcRepository.searchBarangays(any(), any())).thenReturn(listOf(LAHUG))
        vm.onBarangayQueryChanged("lahug")
        advanceUntilIdle()
        vm.onBarangaySelected(LAHUG.code)
    }

    @Test
    fun `onCreateSession rejects blank labels`() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val vm = viewModel()
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.isLoading) {
                snapshot = awaitItem()
            }
            vm.onCreateSession("   ", null)
            val withError = awaitItem()
            assertTrue(withError.errorMessage?.contains("Label") == true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        private val LAHUG = PsgcBarangay(
            code = "0730600051",
            name = "Lahug",
            cityMuniName = "City of Cebu",
            provinceName = null,
            regionName = "Region VII (Central Visayas)",
        )
    }

    private fun makeSessionStats(id: String): SessionWithStats =
        SessionWithStats(
            session = Session(
                id = id,
                userId = "user-1",
                deviceId = "device-1",
                startedAt = Instant.EPOCH.toEpochMilli(),
                endedAt = null,
                notes = null,
                label = "Smear 1",
            ),
            totalSamples = 0,
            verifiedSamples = 0,
            unverifiedSamples = 0,
            totalEpg = 0,
        )

    private fun makeSessionEntity(id: String): SessionEntity = SessionEntity(
        sessionId = id,
        userId = "user-1",
        deviceId = "device-1",
        startedAt = Instant.EPOCH.toEpochMilli(),
        endedAt = null,
        notes = null,
        label = "Smear 1",
    )
}
