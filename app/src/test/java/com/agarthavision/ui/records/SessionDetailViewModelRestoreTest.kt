package com.agarthavision.ui.records

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.agarthavision.core.session.SessionManager
import com.agarthavision.core.session.SessionState
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.RestoreReportFilesUseCase
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.usecase.records.GetSessionSamplesUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionPendingCountUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportCountUseCase
import com.agarthavision.domain.usecase.records.ObserveSessionReportsUseCase
import com.agarthavision.util.MainDispatcherRule
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * The in-flight guard on [SessionDetailViewModel.restoreReportFiles].
 *
 * Runs the real [RestoreReportFilesUseCase] over a download that can be held open, so what is
 * counted is the thing the guard exists to prevent: a second fetch of the same object, and a
 * second copy written beside the first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelRestoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a second tap while the file is still downloading fetches nothing more`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val remote = GatedRemote()
            val vm = viewModelWith(remote, rows = listOf(entity()))

            vm.events.test {
                vm.restoreReportFiles(REPORT_ID)
                assertEquals(SessionDetailEvent.ReportRestoreStarted, awaitItem())
                // The first download is parked on the gate, as a slow field connection would be.
                vm.restoreReportFiles(REPORT_ID)
                vm.restoreReportFiles(REPORT_ID)
                remote.gate.complete(Unit)

                assertEquals(restoredEvent(), awaitItem())
                expectNoEvents()
            }
            assertEquals(1, remote.downloads)
        }

    @Test
    fun `a failed restore releases the report so the next tap can retry`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val remote = GatedRemote().apply { gate.complete(Unit) }
            // The first tap lands before the row has been pulled down, so the restore fails
            // without reaching Storage; the second finds it. The guard is released the same
            // way whatever the failure — this one just doesn't log through android.util.Log.
            val vm = viewModelWith(remote, rows = listOf(null, entity()))

            vm.events.test {
                vm.restoreReportFiles(REPORT_ID)
                assertEquals(SessionDetailEvent.ReportRestoreStarted, awaitItem())
                assertEquals(SessionDetailEvent.ReportRestoreFailed, awaitItem())

                vm.restoreReportFiles(REPORT_ID)
                assertEquals(SessionDetailEvent.ReportRestoreStarted, awaitItem())
                assertEquals(restoredEvent(), awaitItem())
            }
            assertEquals(1, remote.downloads)
        }

    private fun restoredEvent() = SessionDetailEvent.ReportRestored(pdfPath = WRITTEN_PDF, csvPath = null)

    /** [rows] are what successive `getReportById` calls return. */
    private fun viewModelWith(remote: GatedRemote, rows: List<ReportEntity?>): SessionDetailViewModel {
        val reportDao: ReportDao = mock()
        reportDao.stub { onBlocking { getReportById(REPORT_ID) } doReturnConsecutively rows }
        // The row names a MediaStore id this device no longer holds.
        val fileStore = object : ReportFileStore {
            override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String =
                error("a pdf-only report never writes a csv")

            override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String =
                WRITTEN_PDF

            override suspend fun readBytes(path: String): ByteArray? = null
        }
        val sessionManager: SessionManager = mock()
        whenever(sessionManager.state).thenReturn(MutableStateFlow(SessionState.Idle))

        val getSessionSamples: GetSessionSamplesUseCase = mock()
        whenever(getSessionSamples(any())).thenReturn(emptyFlow())
        val observeReports: ObserveSessionReportsUseCase = mock()
        whenever(observeReports(any(), any(), any())).thenReturn(emptyFlow())
        val observeReportCount: ObserveSessionReportCountUseCase = mock()
        whenever(observeReportCount(any())).thenReturn(emptyFlow())
        val observePendingCount: ObserveSessionPendingCountUseCase = mock()
        whenever(observePendingCount(any())).thenReturn(emptyFlow())

        return SessionDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to SESSION_ID)),
            getSessionSamplesUseCase = getSessionSamples,
            observeSessionReportsUseCase = observeReports,
            observeSessionReportCountUseCase = observeReportCount,
            observeSessionPendingCountUseCase = observePendingCount,
            sessionEggCountUseCase = mock(),
            generateSessionReportUseCase = mock(),
            restoreReportFilesUseCase = RestoreReportFilesUseCase(reportDao, remote, fileStore),
            sessionManager = sessionManager,
        )
    }

    private companion object {
        const val REPORT_ID = "report-1"
        const val SESSION_ID = "session-1"
        const val WRITTEN_PDF = "/documents/AgarthaVision/written.pdf"

        fun entity(): ReportEntity = ReportEntity(
            reportId = REPORT_ID,
            sessionId = SESSION_ID,
            userId = "user-1",
            reportType = "session",
            generatedAt = 1_000L,
            totalSamples = 1,
            totalEggsConfirmed = 1,
            positiveSpeciesJson = "[]",
            lpfPerSpeciesJson = "{}",
            csvFilePath = null,
            pdfFilePath = "content://media/external_primary/file/99",
            supabaseStatus = ReportSyncStatus.SYNCED.value,
            createdAt = 1_000L,
        )
    }
}

/** A download that waits on [gate], counting every fetch it is asked for. */
private class GatedRemote : ReportRemoteDataSource(
    supabaseProvider = mock(),
    gson = Gson(),
) {
    val gate = CompletableDeferred<Unit>()
    var downloads = 0
        private set

    override suspend fun downloadReportFile(objectPath: String): ByteArray {
        downloads++
        gate.await()
        return "stored-bytes".toByteArray()
    }
}
