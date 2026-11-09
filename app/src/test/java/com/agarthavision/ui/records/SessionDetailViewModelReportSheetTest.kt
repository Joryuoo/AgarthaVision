package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.supabase.RestoreReportFilesUseCase
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.domain.model.Sample
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.model.Sex
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.usecase.records.GenerateSessionReportUseCase
import com.agarthavision.domain.usecase.records.GetSessionSamplesUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionPendingCountUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportCountUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportsUseCase
import com.agarthavision.domain.usecase.records.PreviewSessionReportUseCase
import com.agarthavision.domain.usecase.records.SampleRecordItem
import com.agarthavision.domain.usecase.records.SessionSamples
import com.agarthavision.domain.usecase.records.SessionSamplesResult
import com.agarthavision.domain.usecase.reports.SessionEggCountUseCase
import com.agarthavision.domain.usecase.reports.SessionEggCounts
import com.agarthavision.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.mockito.kotlin.doSuspendableAnswer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelReportSheetTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    companion object {
        private const val SESSION_ID = "session-100"
        private const val PATIENT_ID = "patient-200"
        private const val PDF_PATH = "/documents/report_session_100.pdf"
    }

    @Test
    fun `onOpenGenerateReport populates reportSheet state when session is present`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Dela Cruz", "Juan", "M")
            val (vm, _) = createViewModel(patient = patient, sampleCount = 3)

            vm.state.test {
                advanceUntilIdle()
                assertNull(expectMostRecentItem().reportSheet)

                vm.onOpenGenerateReport()
                advanceUntilIdle()

                val sheet = expectMostRecentItem().reportSheet
                assertNotNull(sheet)
                assertEquals(ReportSheetStep.CONFIGURATION, sheet?.step)
                assertEquals(3, sheet?.verifiedSampleCount)
                assertEquals(patient.displayName, sheet?.patientDisplayName)
                assertEquals(patient.maskedDisplayName, sheet?.patientMaskedName)
                assertEquals(ReportConfig(), sheet?.config)
                assertFalse(sheet?.isLoadingPreview ?: true)
                assertFalse(sheet?.isGenerating ?: true)
                assertFalse(sheet?.isWarningAcknowledged ?: true)
            }
        }

    @Test
    fun `onOpenGenerateReport with null patient leaves display names empty`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (vm, _) = createViewModel(patient = null, sampleCount = 5)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                val sheet = expectMostRecentItem().reportSheet
                assertNotNull(sheet)
                assertEquals("", sheet?.patientDisplayName)
                assertEquals("", sheet?.patientMaskedName)
                assertEquals(5, sheet?.verifiedSampleCount)
                assertFalse(sheet?.isPatientCodename ?: true)
            }
        }

    @Test
    fun `onOpenGenerateReport with codenamed patient sets isPatientCodename true`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val codenamePatient = makePatient(PATIENT_ID, "M24-001", "")
            assertTrue(codenamePatient.isCodename)
            val (vm, _) = createViewModel(patient = codenamePatient, sampleCount = 3)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                val sheet = expectMostRecentItem().reportSheet
                assertNotNull(sheet)
                assertTrue(sheet?.isPatientCodename ?: false)
            }
        }

    @Test
    fun `onUpdateReportConfig ignores CENSOR when isPatientCodename is true`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val codenamePatient = makePatient(PATIENT_ID, "M24-001", "")
            val (vm, _) = createViewModel(patient = codenamePatient, sampleCount = 3)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                val sheet = expectMostRecentItem().reportSheet
                assertNotNull(sheet)
                assertTrue(sheet?.isPatientCodename ?: false)
                assertEquals(PatientNameOption.SHOW, sheet?.config?.patientNameOption)

                val censorConfig = ReportConfig(
                    includeSampleImages = true,
                    patientNameOption = PatientNameOption.CENSOR,
                )
                vm.onUpdateReportConfig(censorConfig)
                advanceUntilIdle()

                val updatedSheet = expectMostRecentItem().reportSheet
                assertEquals(PatientNameOption.SHOW, updatedSheet?.config?.patientNameOption)
                assertTrue(updatedSheet?.config?.includeSampleImages ?: false)
            }
        }

    @Test
    fun `onOpenGenerateReport does nothing when session is null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val (vm, _) = createViewModel(patient = null, sessionResult = SessionSamplesResult.NotFound)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                assertNull(expectMostRecentItem().reportSheet)
            }
        }

    @Test
    fun `onDismissReportSheet resets sheet state to null`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val (vm, _) = createViewModel(patient = patient, sampleCount = 4)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()
                assertNotNull(expectMostRecentItem().reportSheet)

                vm.onDismissReportSheet()
                advanceUntilIdle()
                assertNull(expectMostRecentItem().reportSheet)
            }
        }

    @Test
    fun `onUpdateReportConfig updates config and clears preview bytes`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val fakePdfBytes = byteArrayOf(10, 20, 30)
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)
            whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(fakePdfBytes))

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()
                vm.onPreviewSessionReport()
                advanceUntilIdle()

                val sheetWithPreview = expectMostRecentItem().reportSheet
                assertNotNull(sheetWithPreview?.previewPdfBytes)

                val newConfig = ReportConfig(
                    includeSampleImages = true,
                    patientNameOption = PatientNameOption.CENSOR,
                )
                vm.onUpdateReportConfig(newConfig)
                advanceUntilIdle()

                val updatedSheet = expectMostRecentItem().reportSheet
                assertEquals(newConfig, updatedSheet?.config)
                assertNull(updatedSheet?.previewPdfBytes)
            }
        }

    @Test
    fun `onAcknowledgeReportWarning updates warning acknowledgment flag`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val (vm, _) = createViewModel(patient = patient, sampleCount = 2)

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                val initialSheet = expectMostRecentItem().reportSheet
                assertFalse(initialSheet?.isWarningAcknowledged ?: true)

                vm.onAcknowledgeReportWarning(true)
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().reportSheet?.isWarningAcknowledged ?: false)

                vm.onAcknowledgeReportWarning(false)
                advanceUntilIdle()
                assertFalse(expectMostRecentItem().reportSheet?.isWarningAcknowledged ?: true)
            }
        }

    @Test
    fun `onPreviewSessionReport transitions to PREVIEW with pdf bytes on success`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val fakePdfBytes = byteArrayOf(1, 2, 3, 4, 5)
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)
            whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(fakePdfBytes))

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                vm.onPreviewSessionReport()
                advanceUntilIdle()

                val previewSheet = expectMostRecentItem().reportSheet
                assertEquals(ReportSheetStep.PREVIEW, previewSheet?.step)
                assertTrue(fakePdfBytes.contentEquals(previewSheet?.previewPdfBytes))
                assertFalse(previewSheet?.isLoadingPreview ?: true)
                assertNull(previewSheet?.error)
            }
        }

    @Test
    fun `onPreviewSessionReport sets error and stays in config on failure`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)
            whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.failure(IllegalStateException("Preview generation failed")))

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                vm.onPreviewSessionReport()
                advanceUntilIdle()

                val sheet = expectMostRecentItem().reportSheet
                assertEquals(ReportSheetStep.CONFIGURATION, sheet?.step)
                assertNull(sheet?.previewPdfBytes)
                assertFalse(sheet?.isLoadingPreview ?: true)
                assertEquals("Preview generation failed", sheet?.error)
            }
        }

    @Test
    fun `onBackToReportConfig transitions step back to CONFIGURATION`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val fakePdf = byteArrayOf(9, 8, 7)
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)
            whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(fakePdf))

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()
                vm.onPreviewSessionReport()
                advanceUntilIdle()
                assertEquals(ReportSheetStep.PREVIEW, expectMostRecentItem().reportSheet?.step)

                vm.onBackToReportConfig()
                advanceUntilIdle()
                assertEquals(ReportSheetStep.CONFIGURATION, expectMostRecentItem().reportSheet?.step)
            }
        }

    @Test
    fun `onFinalizeSessionReport invokes use case closes sheet and emits event on success`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val fakePdf = byteArrayOf(1, 2, 3)
            val expectedReport = makeReport(SESSION_ID, PDF_PATH)
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)

            whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(fakePdf))
            whenever(harness.generateUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(expectedReport))

            vm.events.test {
                vm.state.test {
                    advanceUntilIdle()
                    vm.onOpenGenerateReport()
                    advanceUntilIdle()
                    vm.onPreviewSessionReport()
                    advanceUntilIdle()

                    vm.onFinalizeSessionReport()
                    advanceUntilIdle()

                    val finalState = expectMostRecentItem()
                    assertNull(finalState.reportSheet)
                    assertFalse(finalState.isGenerating)
                    assertNull(finalState.generationError)
                }

                assertEquals(SessionDetailEvent.ReportGenerated(pdfPath = PDF_PATH), awaitItem())
            }

            verify(harness.generateUseCase).invoke(eq(SESSION_ID), any())
        }

    @Test
    fun `onFinalizeSessionReport failure sets error and preserves sheet`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)

            whenever(harness.generateUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.failure(RuntimeException("Disk full error")))

            vm.state.test {
                advanceUntilIdle()
                vm.onOpenGenerateReport()
                advanceUntilIdle()

                vm.onFinalizeSessionReport()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.reportSheet)
                assertFalse(state.reportSheet?.isGenerating ?: true)
                assertEquals("Disk full error", state.reportSheet?.error)
                assertEquals("Disk full error", state.generationError)
            }
        }

    @Test
    fun `generateReport delegates to onFinalizeSessionReport`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val patient = makePatient(PATIENT_ID, "Santos", "Maria")
            val expectedReport = makeReport(SESSION_ID, PDF_PATH)
            val (vm, harness) = createViewModel(patient = patient, sampleCount = 10)

            whenever(harness.generateUseCase.invoke(eq(SESSION_ID), any()))
                .thenReturn(Result.success(expectedReport))

            vm.events.test {
                vm.state.test {
                    advanceUntilIdle()
                    vm.onOpenGenerateReport()
                    advanceUntilIdle()

                    vm.generateReport()
                    advanceUntilIdle()

                    val finalState = expectMostRecentItem()
                    assertNull(finalState.reportSheet)
                }
                assertEquals(SessionDetailEvent.ReportGenerated(pdfPath = PDF_PATH), awaitItem())
            }
        }

    @Test
    fun `stale session preview cannot restore patient name after exclusion or reopening`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            for (reopen in listOf(false, true)) {
                val pending = CompletableDeferred<Result<ByteArray>>()
                val (vm, harness) = createViewModel(makePatient(PATIENT_ID, "Santos", "Maria"))
                whenever(harness.previewUseCase.invoke(eq(SESSION_ID), any()))
                    .doSuspendableAnswer { pending.await() }
                vm.state.test {
                    advanceUntilIdle()
                    vm.onOpenGenerateReport()
                    advanceUntilIdle()
                    vm.onPreviewSessionReport()
                    advanceUntilIdle()
                    if (reopen) {
                        vm.onDismissReportSheet()
                        vm.onOpenGenerateReport()
                        advanceUntilIdle()
                    }
                    vm.onUpdateReportConfig(ReportConfig(patientNameOption = PatientNameOption.EXCLUDE))
                    pending.complete(Result.success(byteArrayOf(1, 2, 3)))
                    advanceUntilIdle()
                    val sheet = expectMostRecentItem().reportSheet!!
                    assertEquals(PatientNameOption.EXCLUDE, sheet.config.patientNameOption)
                    assertEquals(ReportSheetStep.CONFIGURATION, sheet.step)
                    assertNull(sheet.previewPdfBytes)
                    assertFalse(sheet.isLoadingPreview)
                }
            }
        }

    // -------------------------------------------------------------------------
    // Test helpers & fixtures
    // -------------------------------------------------------------------------

    @Suppress("LongParameterList")
    private class MocksHarness(
        val getSessionSamplesUseCase: GetSessionSamplesUseCase = mock(),
        val observeReportsUseCase: ObserveSessionReportsUseCase = mock(),
        val observeReportCountUseCase: ObserveSessionReportCountUseCase = mock(),
        val observePendingCountUseCase: ObserveSessionPendingCountUseCase = mock(),
        val sessionEggCountUseCase: SessionEggCountUseCase = mock(),
        val generateUseCase: GenerateSessionReportUseCase = mock(),
        val previewUseCase: PreviewSessionReportUseCase = mock(),
        val patientRepository: PatientRepository = mock(),
        val restoreReportFilesUseCase: RestoreReportFilesUseCase = mock(),
        val sessionManager: SessionManager = mock(),
    )

    private suspend fun createViewModel(
        patient: Patient?,
        sampleCount: Int = 1,
        sessionResult: SessionSamplesResult? = null,
    ): Pair<SessionDetailViewModel, MocksHarness> {
        val harness = MocksHarness()

        val resolvedSessionResult = sessionResult ?: run {
            val session = Session(
                id = SESSION_ID,
                userId = "user-1",
                patientId = PATIENT_ID,
                deviceId = "device-1",
                startedAt = 10_000L,
                label = "Session 100",
                supabaseStatus = SessionSyncStatus.SYNCED,
            )
            val samples = (1..sampleCount).map { i ->
                SampleRecordItem(
                    sample = Sample(
                        id = "sample-$i",
                        userId = "user-1",
                        deviceId = "device-1",
                        sessionId = SESSION_ID,
                        filePath = "/images/sample-$i.jpg",
                        isManual = false,
                    ),
                    detections = emptyList(),
                )
            }
            SessionSamplesResult.Visible(
                SessionSamples(
                    session = session,
                    samples = samples,
                ),
            )
        }

        whenever(harness.getSessionSamplesUseCase.invoke(SESSION_ID))
            .thenReturn(flowOf(resolvedSessionResult))
        whenever(harness.observeReportsUseCase.invoke(eq(SESSION_ID), any(), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(harness.observeReportCountUseCase.invoke(SESSION_ID))
            .thenReturn(flowOf(0))
        whenever(harness.observePendingCountUseCase.invoke(SESSION_ID))
            .thenReturn(flowOf(0))
        whenever(harness.sessionEggCountUseCase.invoke(any()))
            .thenReturn(Result.success(SessionEggCounts.empty()))
        whenever(harness.sessionManager.state)
            .thenReturn(MutableStateFlow(SessionState.Idle))
        whenever(harness.patientRepository.getPatientById(PATIENT_ID))
            .thenReturn(patient)

        val vm = SessionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to SESSION_ID)),
            getSessionSamplesUseCase = harness.getSessionSamplesUseCase,
            observeSessionReportsUseCase = harness.observeReportsUseCase,
            observeSessionReportCountUseCase = harness.observeReportCountUseCase,
            observeSessionPendingCountUseCase = harness.observePendingCountUseCase,
            sessionEggCountUseCase = harness.sessionEggCountUseCase,
            generateSessionReportUseCase = harness.generateUseCase,
            previewSessionReportUseCase = harness.previewUseCase,
            patientRepository = harness.patientRepository,
            restoreReportFilesUseCase = harness.restoreReportFilesUseCase,
            sessionManager = harness.sessionManager,
        )

        return vm to harness
    }

    private fun makePatient(
        id: String,
        lastname: String,
        firstname: String,
        middleName: String? = null,
    ): Patient = Patient(
        id = id,
        lastname = lastname,
        firstname = firstname,
        middleName = middleName,
        sex = Sex.MALE,
        birthdate = LocalDate.of(1995, 5, 20),
        psgcBarangayCode = "123456789",
        createdBy = "user-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun makeReport(sessionId: String, pdfPath: String): Report = Report(
        id = "report-1",
        sessionId = sessionId,
        userId = "user-1",
        reportType = ReportType.SESSION,
        generatedAt = Instant.now(),
        totalSamples = 10,
        totalEggsConfirmed = 5,
        positiveSpecies = emptyList(),
        lpfPerSpecies = emptyMap(),
        csvFilePath = null,
        pdfFilePath = pdfPath,
        supabaseStatus = ReportSyncStatus.SYNCED,
    )
}
