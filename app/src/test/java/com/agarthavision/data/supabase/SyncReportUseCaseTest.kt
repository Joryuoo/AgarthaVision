package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.kotlin.mock

/**
 * Robolectric because the use case now logs a push failure, and `android.util.Log`
 * throws in a plain JVM test. Same reason and same shape as FetchRemoteDataUseCaseTest,
 * which has logged its pull failures all along.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncReportUseCaseTest {
    @Test
    fun `marks report SYNCED when remote upsert succeeds`() = runTest {
        val report = entity("report-1")
        val dao = FakeReportDao(seeded = listOf(report))
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        val result = useCase("report-1")

        assertTrue(result.isSuccess)
        assertEquals(ReportSyncStatus.SYNCED.value, dao.statusOf("report-1"))
        assertEquals(1, remote.upsertCallCount)
    }

    @Test
    fun `marks report SYNC_FAILED when remote upsert throws`() = runTest {
        val report = entity("report-2")
        val dao = FakeReportDao(seeded = listOf(report))
        val remote = StubRemoteDataSource(shouldThrow = true)
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        val result = useCase("report-2")

        assertTrue(result.isFailure)
        assertEquals(ReportSyncStatus.SYNC_FAILED.value, dao.statusOf("report-2"))
    }

    @Test
    fun `uploads both report files to the owner-scoped object paths`() = runTest {
        val dao = FakeReportDao(seeded = listOf(entity("report-3")))
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        useCase("report-3")

        // The leading uid is not decoration: the bucket's RLS matches on it, so a path
        // built any other way is refused.
        assertEquals(
            listOf("user-1/report-3.pdf", "user-1/report-3.csv"),
            remote.uploadedPaths,
        )
    }

    @Test
    fun `still syncs the row when the local files are gone`() = runTest {
        val dao = FakeReportDao(seeded = listOf(entity("report-4")))
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore(present = emptySet()))

        val result = useCase("report-4")

        // Losing the bytes must not cost the metadata too: parking the row in sync_failed
        // forever over a file the medtech cleared would lose the report entirely.
        assertTrue(result.isSuccess)
        assertEquals(ReportSyncStatus.SYNCED.value, dao.statusOf("report-4"))
        assertTrue(remote.uploadedPaths.isEmpty())
        assertEquals(1, remote.upsertCallCount)
    }

    @Test
    fun `a second sync of the same report succeeds against a row the server already holds`() = runTest {
        val dao = FakeReportDao(seeded = listOf(entity("report-5")))
        val remote = InsertIfAbsentRemoteDataSource()
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        val first = useCase("report-5")
        val second = useCase("report-5")

        // The case that never ran before 14zcqnthrx9: a retry used to conflict on the
        // primary key and park the report in sync_failed for good.
        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(ReportSyncStatus.SYNCED.value, dao.statusOf("report-5"))
        assertEquals(listOf("report-5"), remote.serverRowIds)
    }

    @Test
    fun `a report parked in sync_failed recovers on the next pass`() = runTest {
        // The row reached the server, but the report was marked failed locally: the state
        // the old insert left behind after any retry.
        val dao = FakeReportDao(
            seeded = listOf(entity("report-6", status = ReportSyncStatus.SYNC_FAILED.value)),
        )
        val remote = InsertIfAbsentRemoteDataSource(serverRowIds = listOf("report-6"))
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        val pending = dao.getReportsPendingSync("user-1").map { it.reportId }
        val result = useCase("report-6")

        assertEquals(listOf("report-6"), pending)
        assertTrue(result.isSuccess)
        assertEquals(ReportSyncStatus.SYNCED.value, dao.statusOf("report-6"))
        assertTrue(dao.getReportsPendingSync("user-1").isEmpty())
        assertEquals(listOf("report-6"), remote.serverRowIds)
    }

    @Test
    fun `returns failure when report is not found`() = runTest {
        val dao = FakeReportDao(seeded = emptyList())
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote, FakeReportFileStore())

        val result = useCase("missing")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertFalse(remote.upsertCallCount > 0)
    }
}

private fun entity(id: String, status: String = ReportSyncStatus.PENDING.value): ReportEntity =
    ReportEntity(
        reportId = id,
        sessionId = "session-1",
        userId = "user-1",
        reportType = "session",
        generatedAt = 1_000L,
        totalSamples = 3,
        totalEggsConfirmed = 5,
        positiveSpeciesJson = "[]",
        lpfPerSpeciesJson = "{}",
        csvFilePath = "/downloads/report.csv",
        pdfFilePath = "/downloads/report.pdf",
        supabaseStatus = status,
        createdAt = 1_000L,
    )

private class FakeReportDao(seeded: List<ReportEntity>) : ReportDao {
    private val rows = seeded.associateBy { it.reportId }.toMutableMap()

    fun statusOf(reportId: String): String? = rows[reportId]?.supabaseStatus

    override suspend fun deleteReport(reportId: String) {
        rows.remove(reportId)
    }

    override suspend fun insertReport(report: ReportEntity) {
        rows[report.reportId] = report
    }

    override fun observeReportsForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>> =
        flowOf(
            rows.values
                .filter { it.sessionId == sessionId && it.userId == userId }
                .drop(offset)
                .take(limit),
        )

    override fun observeReportCountForSession(sessionId: String, userId: String): Flow<Int> =
        flowOf(rows.values.count { it.sessionId == sessionId && it.userId == userId })

    override fun observeAllReports(
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ReportEntity>> =
        flowOf(
            rows.values
                .filter { it.userId == userId }
                .sortedByDescending { it.generatedAt }
                .drop(offset)
                .take(limit),
        )

    override fun observeAllReportsCount(userId: String): Flow<Int> =
        flowOf(rows.values.count { it.userId == userId })

    override fun observeFilteredReports(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<com.agarthavision.data.local.dao.ReportWithSessionLabel>> =
        flowOf(
            rows.values
                .filter { it.userId == userId }
                .drop(offset)
                .take(limit)
                .map { com.agarthavision.data.local.dao.ReportWithSessionLabel(it) },
        )

    override fun observeFilteredReportsCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> =
        flowOf(rows.values.count { it.userId == userId })

    override suspend fun getReportById(reportId: String): ReportEntity? = rows[reportId]

    override suspend fun getReportsPendingSync(userId: String): List<ReportEntity> =
        rows.values.filter {
            it.userId == userId && it.supabaseStatus in setOf("pending", "sync_failed")
        }

    override suspend fun updateSupabaseStatus(reportId: String, status: String) {
        rows[reportId]?.let { rows[reportId] = it.copy(supabaseStatus = status) }
    }

    override suspend fun updateFilePaths(
        reportId: String,
        pdfFilePath: String?,
        csvFilePath: String?,
    ) {
        rows[reportId]?.let {
            rows[reportId] = it.copy(pdfFilePath = pdfFilePath, csvFilePath = csvFilePath)
        }
    }

    override suspend fun claimReportsForSessions(sessionIds: List<String>, userId: String) {
        rows.replaceAll { _, row ->
            if (row.sessionId in sessionIds && row.userId == null) {
                row.copy(userId = userId, supabaseStatus = ReportSyncStatus.PENDING.value)
            } else {
                row
            }
        }
    }

    override fun observePendingCount(userId: String): Flow<Int> = flowOf(0)
    override fun observeFailedCount(userId: String): Flow<Int> = flowOf(0)
}

private class StubRemoteDataSource(
    private val shouldThrow: Boolean,
) : ReportRemoteDataSource(
    supabase = mock(),
    gson = com.google.gson.Gson(),
) {
    var upsertCallCount = 0
        private set

    val uploadedPaths = mutableListOf<String>()

    override suspend fun upsertReport(report: ReportEntity) {
        upsertCallCount++
        if (shouldThrow) {
            error("simulated upstream failure")
        }
    }

    override suspend fun uploadReportFile(objectPath: String, bytes: ByteArray) {
        uploadedPaths += objectPath
    }
}

/**
 * Models `public.reports` under `ON CONFLICT (id) DO NOTHING`: a new id adds a row, and an id
 * the server already holds is a successful no-op rather than a primary-key conflict.
 */
private class InsertIfAbsentRemoteDataSource(
    serverRowIds: List<String> = emptyList(),
) : ReportRemoteDataSource(
    supabase = mock(),
    gson = com.google.gson.Gson(),
) {
    private val rows = serverRowIds.toMutableList()

    val serverRowIds: List<String> get() = rows.toList()

    override suspend fun upsertReport(report: ReportEntity) {
        if (report.reportId !in rows) rows += report.reportId
    }

    override suspend fun uploadReportFile(objectPath: String, bytes: ByteArray) = Unit
}

/** Holds bytes for the two paths [entity] uses, and nothing else. */
private class FakeReportFileStore(
    private val present: Set<String> = setOf("/downloads/report.pdf", "/downloads/report.csv"),
) : ReportFileStore {
    override suspend fun writeCsv(reportId: String, sessionId: String, csv: String): String =
        "/downloads/report.csv"

    override suspend fun writePdf(reportId: String, sessionId: String, pdf: ByteArray): String =
        "/downloads/report.pdf"

    override suspend fun readBytes(path: String): ByteArray? =
        if (path in present) "bytes-for-$path".toByteArray() else null
}
