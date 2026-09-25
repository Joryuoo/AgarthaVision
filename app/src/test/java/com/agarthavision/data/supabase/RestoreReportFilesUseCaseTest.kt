package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.ReportWithSessionLabel
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric because the use case logs a missing object through `android.util.Log`, which
 * throws on a plain JVM test. Same reason as [SyncReportUseCaseTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestoreReportFilesUseCaseTest {

    @Test
    fun `downloads the file this device never had and repoints the row`() = runTest {
        // The bug in one line: the row synced from another device, so the path it carries
        // names a MediaStore id that only ever existed over there.
        val dao = FakeDao(listOf(entity(pdfPath = "content://media/external_primary/file/99")))
        val store = FakeStore(present = emptySet())
        val useCase = RestoreReportFilesUseCase(dao, StubRemote(stored = setOf(PDF_OBJECT)), store)

        val result = useCase(REPORT_ID)

        assertTrue(result.isSuccess)
        assertEquals(WRITTEN_PDF, result.getOrThrow().pdfFilePath)
        // Repointing is what makes this a one-time cost rather than a download per tap.
        assertEquals(WRITTEN_PDF, dao.rowOf(REPORT_ID)?.pdfFilePath)
    }

    @Test
    fun `leaves a file that is already here alone`() = runTest {
        val dao = FakeDao(listOf(entity(pdfPath = LOCAL_PDF)))
        val remote = StubRemote(stored = setOf(PDF_OBJECT))
        val useCase = RestoreReportFilesUseCase(dao, remote, FakeStore(present = setOf(LOCAL_PDF)))

        val result = useCase(REPORT_ID)

        assertEquals(LOCAL_PDF, result.getOrThrow().pdfFilePath)
        // Re-fetching a document the device already holds spends the medtech's data to
        // arrive exactly where it started.
        assertTrue(remote.downloadedPaths.isEmpty())
    }

    @Test
    fun `fails when the report predates the bucket and nothing is stored`() = runTest {
        val dao = FakeDao(listOf(entity(pdfPath = LOCAL_PDF)))
        val useCase = RestoreReportFilesUseCase(
            dao,
            StubRemote(stored = emptySet()),
            FakeStore(present = emptySet()),
        )

        val result = useCase(REPORT_ID)

        assertTrue(result.isFailure)
        // The row keeps its original path: a failed restore must not erase what was there.
        assertEquals(LOCAL_PDF, dao.rowOf(REPORT_ID)?.pdfFilePath)
    }

    @Test
    fun `fails when the report does not exist`() = runTest {
        val useCase = RestoreReportFilesUseCase(
            FakeDao(emptyList()),
            StubRemote(stored = emptySet()),
            FakeStore(present = emptySet()),
        )

        assertTrue(useCase("nope").isFailure)
    }

    @Test
    fun `reports a csv-only report without inventing a pdf`() = runTest {
        val dao = FakeDao(listOf(entity(pdfPath = null, csvPath = "content://media/file/7")))
        val useCase = RestoreReportFilesUseCase(
            dao,
            StubRemote(stored = setOf(CSV_OBJECT)),
            FakeStore(present = emptySet()),
        )

        val restored = useCase(REPORT_ID).getOrThrow()

        assertEquals(WRITTEN_CSV, restored.csvFilePath)
        assertNull(restored.pdfFilePath)
    }

    private companion object {
        const val REPORT_ID = "report-1"
        const val USER_ID = "user-1"
        const val LOCAL_PDF = "/documents/report.pdf"
        const val WRITTEN_PDF = "/documents/AgarthaVision/written.pdf"
        const val WRITTEN_CSV = "/documents/AgarthaVision/written.csv"
        const val PDF_OBJECT = "$USER_ID/$REPORT_ID.pdf"
        const val CSV_OBJECT = "$USER_ID/$REPORT_ID.csv"

        fun entity(pdfPath: String?, csvPath: String? = null): ReportEntity = ReportEntity(
            reportId = REPORT_ID,
            sessionId = "session-1",
            userId = USER_ID,
            reportType = "session",
            generatedAt = 1_000L,
            totalSamples = 1,
            totalEggsConfirmed = 1,
            positiveSpeciesJson = "[]",
            lpfPerSpeciesJson = "{}",
            csvFilePath = csvPath,
            pdfFilePath = pdfPath,
            supabaseStatus = ReportSyncStatus.SYNCED.value,
            createdAt = 1_000L,
        )
    }
}

private class StubRemote(private val stored: Set<String>) : ReportRemoteDataSource(
    supabaseProvider = mock(),
    gson = Gson(),
) {
    val downloadedPaths = mutableListOf<String>()

    override suspend fun downloadReportFile(objectPath: String): ByteArray {
        downloadedPaths += objectPath
        if (objectPath !in stored) error("object not found: $objectPath")
        return "stored-bytes".toByteArray()
    }
}

private class FakeStore(private val present: Set<String>) : ReportFileStore {
    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String =
        "/documents/AgarthaVision/written.csv"

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String =
        "/documents/AgarthaVision/written.pdf"

    override suspend fun readBytes(path: String): ByteArray? =
        if (path in present) "local-bytes".toByteArray() else null
}

private class FakeDao(seeded: List<ReportEntity>) : ReportDao {
    private val rows = seeded.associateBy { it.reportId }.toMutableMap()

    fun rowOf(reportId: String): ReportEntity? = rows[reportId]

    override suspend fun getReportById(reportId: String): ReportEntity? = rows[reportId]

    override suspend fun updateFilePaths(
        reportId: String,
        pdfFilePath: String?,
        csvFilePath: String?,
    ) {
        rows[reportId]?.let {
            rows[reportId] = it.copy(pdfFilePath = pdfFilePath, csvFilePath = csvFilePath)
        }
    }

    override suspend fun insertReport(report: ReportEntity) {
        rows[report.reportId] = report
    }

    override suspend fun deleteReport(reportId: String) {
        rows.remove(reportId)
    }

    override fun observeReportsForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>> = flowOf(emptyList())

    override fun observeReportCountForSession(sessionId: String, userId: String): Flow<Int> =
        flowOf(0)

    override fun observeAllReports(
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>> = flowOf(emptyList())

    override fun observeAllReportsCount(userId: String): Flow<Int> = flowOf(0)

    override fun observeFilteredReports(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportWithSessionLabel>> = flowOf(emptyList())

    override fun observeFilteredReportsCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> = flowOf(0)

    override suspend fun getReportsPendingSync(userId: String): List<ReportEntity> = emptyList()

    override suspend fun updateSupabaseStatus(reportId: String, status: String) = Unit

    override suspend fun claimReportsForSessions(sessionIds: List<String>, userId: String) = Unit

    override fun observePendingCount(userId: String): Flow<Int> = flowOf(0)

    override fun observeFailedCount(userId: String): Flow<Int> = flowOf(0)
}
