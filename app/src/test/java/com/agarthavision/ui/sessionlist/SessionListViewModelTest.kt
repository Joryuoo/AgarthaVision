package com.agarthavision.ui.sessionlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.HomePeriod
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionListFilter
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.usecase.home.ObserveSessionListUseCase
import com.agarthavision.domain.usecase.home.SessionListResult
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedInstant = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, CLINICAL_ZONE)
    private val sessionManager: SessionManager = mock()
    private val observeSessionListUseCase: ObserveSessionListUseCase = mock()

    private fun samplePatient(id: String) = Patient(
        id = id,
        lastname = "Rizal",
        firstname = "Jose",
        middleName = null,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1990, 1, 1),
        psgcBarangayCode = "0102801001",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun sampleSession(id: String) = Session(
        id = id,
        patientId = "patient-1",
        userId = "user-1",
        deviceId = "dev-1",
        startedAt = 0L,
        label = "Smear 1",
    )

    private fun sampleSummary(sessionId: String) = SessionSummary(
        session = sampleSession(sessionId),
        patient = samplePatient("patient-1"),
        totalFrames = 10,
        framesToReview = 2,
        isPositive = false,
        lastActivityAt = 1_000L,
    )

    @Test
    fun `parses valid filter and period from SavedStateHandle`() = runTest {
        val handle = SavedStateHandle(
            mapOf("filter" to "TO_REVIEW", "period" to "LAST_7_DAYS"),
        )
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )

        val vm = SessionListViewModel(
            savedStateHandle = handle,
            clock = clock,
            sessionManager = sessionManager,
            observeSessionListUseCase = observeSessionListUseCase,
        )

        assertEquals(SessionListFilter.TO_REVIEW, vm.filter)
        assertEquals(HomePeriod.LAST_7_DAYS, vm.period)
    }

    @Test
    fun `falls back to ALL filter and null period when missing or invalid`() = runTest {
        val handle = SavedStateHandle(
            mapOf("filter" to "INVALID_FILTER", "period" to "ALL"),
        )
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )

        val vm = SessionListViewModel(
            savedStateHandle = handle,
            clock = clock,
            sessionManager = sessionManager,
            observeSessionListUseCase = observeSessionListUseCase,
        )

        assertEquals(SessionListFilter.ALL, vm.filter)
        assertNull(vm.period)
    }

    @Test
    fun `onSessionClick with TO_REVIEW resumes session before navigating to verification queue`() = runTest {
        val handle = SavedStateHandle(mapOf("filter" to "TO_REVIEW"))
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )
        val vm = SessionListViewModel(
            savedStateHandle = handle,
            clock = clock,
            sessionManager = sessionManager,
            observeSessionListUseCase = observeSessionListUseCase,
        )

        val summary = sampleSummary("session-to-resume")

        vm.events.test {
            vm.onSessionClick(summary)
            advanceUntilIdle()

            verify(sessionManager).resumeSession("session-to-resume")
            assertEquals(SessionListEvent.NavigateToVerificationQueue, awaitItem())
        }
    }

    @Test
    fun `onSessionClick with other filter navigates to detail without resuming session`() = runTest {
        val handle = SavedStateHandle(mapOf("filter" to "ALL"))
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )
        val vm = SessionListViewModel(
            savedStateHandle = handle,
            clock = clock,
            sessionManager = sessionManager,
            observeSessionListUseCase = observeSessionListUseCase,
        )

        val summary = sampleSummary("session-detail-id")

        vm.events.test {
            vm.onSessionClick(summary)
            advanceUntilIdle()

            verify(sessionManager, never()).resumeSession(any())
            assertEquals(SessionListEvent.NavigateToDetail("session-detail-id"), awaitItem())
        }
    }

    @Test
    fun `onLoadMore increases page limit when canLoadMore is true`() = runTest {
        val handle = SavedStateHandle(mapOf("filter" to "ALL"))
        val firstPage = (1..20).map { sampleSummary("session-$it") }
        val secondPage = (1..30).map { sampleSummary("session-$it") }

        whenever(observeSessionListUseCase(eq(SessionListFilter.ALL), anyOrNull(), eq(20))).thenReturn(
            flowOf(SessionListResult(firstPage, 30)),
        )
        whenever(observeSessionListUseCase(eq(SessionListFilter.ALL), anyOrNull(), eq(40))).thenReturn(
            flowOf(SessionListResult(secondPage, 30)),
        )

        val vm = SessionListViewModel(
            savedStateHandle = handle,
            clock = clock,
            sessionManager = sessionManager,
            observeSessionListUseCase = observeSessionListUseCase,
        )

        vm.uiState.test {
            val initial = awaitItem()
            // Wait until first page is loaded
            val loaded = if (initial.isLoading) awaitItem() else initial
            assertEquals(20, loaded.sessions.size)
            assertEquals(true, loaded.canLoadMore)

            vm.onLoadMore()
            advanceUntilIdle()

            val moreLoaded = awaitItem()
            assertEquals(30, moreLoaded.sessions.size)
            assertEquals(false, moreLoaded.canLoadMore)
        }
    }
}
