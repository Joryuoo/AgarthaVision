package com.agarthavision.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.agarthavision.core.connectivity.ConnectivityObserver
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.core.sync.InitialFetchStateStore
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.domain.model.AgreementBreakdown
import com.agarthavision.domain.model.CLINICAL_ZONE
import com.agarthavision.domain.model.FindingsResult
import com.agarthavision.domain.model.HomeKpis
import com.agarthavision.domain.model.KpiMetric
import com.agarthavision.domain.model.LocalIdentity
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Ratio
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSummary
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.model.ThemeMode
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.usecase.auth.ObserveLocalIdentityUseCase
import com.agarthavision.domain.usecase.home.NeedsAttention
import com.agarthavision.domain.usecase.home.ObserveFindingsUseCase
import com.agarthavision.domain.usecase.home.ObserveHomeKpisUseCase
import com.agarthavision.domain.usecase.home.ObserveNeedsAttentionUseCase
import com.agarthavision.domain.usecase.home.ObserveRecentActivityUseCase
import com.agarthavision.domain.usecase.home.ObserveSessionListUseCase
import com.agarthavision.domain.usecase.home.SessionListResult
import com.agarthavision.domain.usecase.settings.ObserveThemeModeUseCase
import com.agarthavision.domain.usecase.settings.SetThemeModeUseCase
import com.agarthavision.domain.usecase.sync.FetchRemoteDataUseCase
import com.agarthavision.domain.usecase.sync.SyncPendingDataUseCase
import com.agarthavision.ui.theme.AgarthaVisionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RecentSessionsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun sampleSession(id: String, label: String = "Smear 1") = Session(
        id = id,
        patientId = "patient-1",
        userId = "user-1",
        deviceId = "dev-1",
        startedAt = 0L,
        label = label,
    )

    private fun sampleSummary(sessionId: String, label: String = "Smear 1") = SessionSummary(
        session = sampleSession(sessionId, label),
        patient = samplePatient("patient-1"),
        totalFrames = 10,
        framesToReview = 2,
        isPositive = false,
        lastActivityAt = 1_000L,
    )

    /**
     * Stubs `observePatientCount` to always return 0, regardless of the bucket-sort filter
     * (`sort`/`todayStartMillis`/`sevenDaysAgoMillis`) a caller passes — this test doesn't
     * exercise Patients sorting, only that the Dashboard KPI wiring doesn't crash.
     */
    private fun stubPatientCount(patientRepository: PatientRepository) {
        whenever(
            patientRepository.observePatientCount(
                any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any(), anyOrNull(), anyOrNull(),
            ),
        ).thenReturn(flowOf(0))
    }

    private fun createViewModel(): DashboardViewModel {
        val clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), CLINICAL_ZONE)
        val observeLocalIdentityUseCase: ObserveLocalIdentityUseCase = mock<ObserveLocalIdentityUseCase>().also {
            whenever(it.invoke()).thenReturn(flowOf(LocalIdentity(userId = "user-1", email = "user@example.com")))
        }
        val connectivityObserver: ConnectivityObserver = mock<ConnectivityObserver>().also {
            whenever(it.isOnline).thenReturn(MutableStateFlow(true))
        }
        val syncPendingDataUseCase: SyncPendingDataUseCase = mock()
        val fetchRemoteDataUseCase: FetchRemoteDataUseCase = mock()
        val initialFetchStateStore: InitialFetchStateStore = mock<InitialFetchStateStore>().also {
            whenever(it.observeCompleted(any())).thenReturn(MutableStateFlow(true))
        }
        val sessionRepository: SessionRepository = mock<SessionRepository>().also {
            whenever(it.observeAllSessions(any())).thenReturn(flowOf(emptyList()))
        }
        val sampleRepository: SampleRepository = mock<SampleRepository>().also {
            whenever(it.observeAllSamples(any())).thenReturn(flowOf(emptyList()))
            whenever(it.observeSamplesForSession(any(), any())).thenReturn(flowOf(emptyList()))
            whenever(it.observePendingCount(any())).thenReturn(flowOf(0))
            kotlinx.coroutines.runBlocking {
                whenever(it.getSamplesPendingSyncIncludingDeleted(any())).thenReturn(emptyList())
            }
        }
        val patientRepository: PatientRepository = mock<PatientRepository>().also(::stubPatientCount)
        val observeThemeModeUseCase: ObserveThemeModeUseCase = mock<ObserveThemeModeUseCase>().also {
            whenever(it.invoke()).thenReturn(MutableStateFlow(ThemeMode.LIGHT))
        }
        val setThemeModeUseCase: SetThemeModeUseCase = mock()
        val observeNeedsAttentionUseCase: ObserveNeedsAttentionUseCase = mock<ObserveNeedsAttentionUseCase>().also {
            whenever(it.invoke(any(), anyOrNull())).thenReturn(flowOf(NeedsAttention(0, 0, 0)))
        }
        val defaultKpis = HomeKpis(
            sessions = KpiMetric(0, 0, emptyList()),
            patientsInSessions = 0,
            positiveRate = KpiMetric(Ratio(0, 0), Ratio(0, 0), emptyList()),
            toReview = KpiMetric(0, 0, emptyList()),
            verifiedInPeriod = 0,
            aiAgreement = KpiMetric(Ratio(0, 0), Ratio(0, 0), emptyList()),
            aiBreakdown = AgreementBreakdown(0, 0, 0, 0),
        )
        val observeHomeKpisUseCase: ObserveHomeKpisUseCase = mock<ObserveHomeKpisUseCase>().also {
            whenever(it.invoke(any(), any())).thenReturn(flowOf(defaultKpis))
        }
        val observeFindingsUseCase: ObserveFindingsUseCase = mock<ObserveFindingsUseCase>().also {
            whenever(it.invoke(any(), any(), anyOrNull())).thenReturn(
                flowOf(FindingsResult(emptyList(), 0)),
            )
        }
        val observeRecentActivityUseCase: ObserveRecentActivityUseCase =
            mock<ObserveRecentActivityUseCase>().also {
                whenever(it.invoke(any(), any(), anyOrNull())).thenReturn(flowOf(emptyList()))
            }

        return DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            clock = clock,
            observeLocalIdentityUseCase = observeLocalIdentityUseCase,
            connectivityObserver = connectivityObserver,
            syncPendingDataUseCase = syncPendingDataUseCase,
            fetchRemoteDataUseCase = fetchRemoteDataUseCase,
            initialFetchStateStore = initialFetchStateStore,
            sessionManager = sessionManager,
            sessionRepository = sessionRepository,
            sampleRepository = sampleRepository,
            patientRepository = patientRepository,
            observeThemeModeUseCase = observeThemeModeUseCase,
            setThemeModeUseCase = setThemeModeUseCase,
            observeNeedsAttentionUseCase = observeNeedsAttentionUseCase,
            observeHomeKpisUseCase = observeHomeKpisUseCase,
            observeFindingsUseCase = observeFindingsUseCase,
            observeSessionListUseCase = observeSessionListUseCase,
            observeRecentActivityUseCase = observeRecentActivityUseCase,
        )
    }

    @Test
    fun `empty state appears when there are no sessions`() {
        whenever(sessionManager.state).thenReturn(MutableStateFlow(SessionState.Idle))
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(emptyList(), 0)),
        )

        val vm = createViewModel()
        composeRule.setContent {
            AgarthaVisionTheme {
                DashboardScreen(viewModel = vm)
            }
        }

        composeRule.onNodeWithText("No sessions yet").assertIsDisplayed()
        composeRule.onNodeWithText("Start a session from a patient's page").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Patients").assertIsDisplayed()
        composeRule.onNodeWithTag("recentSessionCard_s1").assertDoesNotExist()
    }

    @Test
    fun `row is absent when only active continue session exists`() {
        val activeEntity = SessionEntity(
            sessionId = "s-active",
            userId = "user-1",
            patientId = "p-1",
            deviceId = "d-1",
            startedAt = 1000L,
            label = "Active Smear",
        )
        whenever(sessionManager.state).thenReturn(
            MutableStateFlow(SessionState.Active(activeEntity, Instant.ofEpochMilli(1000L))),
        )
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(SessionListResult(listOf(sampleSummary("s-active", "Active Smear")), 1)),
        )

        val vm = createViewModel()
        composeRule.setContent {
            AgarthaVisionTheme {
                DashboardScreen(viewModel = vm)
            }
        }

        composeRule.onNodeWithText("CONTINUE").assertIsDisplayed()
        composeRule.onNodeWithText("Active Smear").assertIsDisplayed()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.onNodeWithTag("recentSessionCard_s-active").assertDoesNotExist()
    }

    @Test
    fun `cards are displayed when multiple sessions exist`() {
        whenever(sessionManager.state).thenReturn(MutableStateFlow(SessionState.Idle))
        whenever(observeSessionListUseCase(any(), anyOrNull(), any())).thenReturn(
            flowOf(
                SessionListResult(
                    listOf(
                        sampleSummary("s-1", "Smear 1"),
                        sampleSummary("s-2", "Smear 2"),
                    ),
                    2,
                ),
            ),
        )

        val vm = createViewModel()
        composeRule.setContent {
            AgarthaVisionTheme {
                DashboardScreen(viewModel = vm)
            }
        }

        composeRule.onNodeWithTag("recentSessionCard_s-1").assertIsDisplayed()
        composeRule.onNodeWithTag("recentSessionCard_s-2").assertIsDisplayed()
        composeRule.onNodeWithText("Smear 1").assertIsDisplayed()
        composeRule.onNodeWithText("Smear 2").assertIsDisplayed()
    }
}
