package com.agarthavision.ui.records

import app.cash.turbine.test
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.supabase.ReportRemoteDataSource
import com.agarthavision.data.supabase.RestoreReportFilesUseCase
import com.agarthavision.data.supabase.RestoredReportFiles
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.usecase.records.FakeAuthRepository
import com.agarthavision.domain.usecase.records.ObserveReportsUseCase
import com.agarthavision.util.MainDispatcherRule
import com.google.gson.Gson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

/**
 * [RecordsViewModel.restoreReportFiles] — the Records tab's counterpart to
 * [SessionDetailViewModelRestoreTest], needed because a patient report card is openable straight
 * from this tab on a device that never generated it (14zcqntj2uz follow-up).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelRestoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a successful restore emits started then restored with the new path`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val restoreUseCase: RestoreReportFilesUseCase = mock()
            whenever(restoreUseCase(REPORT_ID)).thenReturn(
                Result.success(RestoredReportFiles(pdfFilePath = WRITTEN_PDF)),
            )
            val vm = viewModelWith(restoreUseCase)

            vm.events.test {
                vm.restoreReportFiles(REPORT_ID)
                assertEquals(RecordsEvent.ReportRestoreStarted, awaitItem())
                assertEquals(RecordsEvent.ReportRestored(pdfPath = WRITTEN_PDF), awaitItem())
            }
        }

    @Test
    fun `a failed restore emits started then failed`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val restoreUseCase: RestoreReportFilesUseCase = mock()
            whenever(restoreUseCase(REPORT_ID)).thenReturn(
                Result.failure(IllegalStateException("no stored pdf")),
            )
            val vm = viewModelWith(restoreUseCase)

            vm.events.test {
                vm.restoreReportFiles(REPORT_ID)
                assertEquals(RecordsEvent.ReportRestoreStarted, awaitItem())
                assertEquals(RecordsEvent.ReportRestoreFailed, awaitItem())
            }
        }

    @Test
    fun `a second tap while the first restore is still in flight fetches nothing more`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            // Runs the real use case over a download that can be held open — same shape as
            // SessionDetailViewModelRestoreTest — so what is counted is the thing the guard
            // exists to prevent: a second fetch of the same object.
            val remote = GatedReportRemote()
            val reportDao: ReportDao = mock()
            reportDao.stub { onBlocking { getReportById(REPORT_ID) } doReturn entity() }
            val fileStore = object : ReportFileStore {
                override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String =
                    WRITTEN_PDF
                override suspend fun writePatientPdf(reportId: String, patientId: String, pdf: ByteArray): String =
                    WRITTEN_PDF
                override suspend fun readBytes(path: String): ByteArray? = null
            }
            val restoreUseCase = RestoreReportFilesUseCase(reportDao, remote, fileStore)
            val vm = viewModelWith(restoreUseCase)

            vm.events.test {
                vm.restoreReportFiles(REPORT_ID)
                assertEquals(RecordsEvent.ReportRestoreStarted, awaitItem())
                vm.restoreReportFiles(REPORT_ID)
                vm.restoreReportFiles(REPORT_ID)
                remote.gate.complete(Unit)

                assertEquals(RecordsEvent.ReportRestored(pdfPath = WRITTEN_PDF), awaitItem())
                expectNoEvents()
            }
            assertEquals(1, remote.downloads)
        }

    private fun viewModelWith(restoreUseCase: RestoreReportFilesUseCase): RecordsViewModel {
        val reportRepo = object : ReportRepository {
            override suspend fun insert(report: Report) = Unit
            override fun observeForSession(
                sessionId: String,
                userId: String,
                limit: Int,
                offset: Int,
            ): Flow<List<Report>> = flowOf(emptyList())
            override fun observeCountForSession(sessionId: String, userId: String): Flow<Int> = flowOf(0)
            override fun observeAll(userId: String, limit: Int, offset: Int): Flow<List<Report>> = flowOf(emptyList())
            override fun observeAllCount(userId: String): Flow<Int> = flowOf(0)
            override fun observeFiltered(
                userId: String,
                startMillis: Long?,
                endMillis: Long?,
                species: String?,
                query: String,
                limit: Int,
                offset: Int,
            ): Flow<List<Report>> = flowOf(emptyList())
            override fun observeFilteredCount(
                userId: String,
                startMillis: Long?,
                endMillis: Long?,
                species: String?,
                query: String,
            ): Flow<Int> = flowOf(0)
            override suspend fun getById(reportId: String): Report? = null
            override suspend fun getReportsPendingSync(userId: String): List<Report> = emptyList()
            override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) = Unit
        }
        val authRepo = FakeAuthRepository("user-1")
        val useCase = ObserveReportsUseCase(authRepo, reportRepo)
        val clock = Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC)
        return RecordsViewModel(useCase, restoreUseCase, clock)
    }

    private fun entity(): ReportEntity = ReportEntity(
        reportId = REPORT_ID,
        sessionId = null,
        patientId = "patient-1",
        userId = "user-1",
        reportType = "patient",
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

    private companion object {
        const val REPORT_ID = "report-1"
        const val WRITTEN_PDF = "/documents/AgarthaVision/written.pdf"
    }
}

/** A download that waits on [gate], counting every fetch it is asked for. */
private class GatedReportRemote : ReportRemoteDataSource(
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
