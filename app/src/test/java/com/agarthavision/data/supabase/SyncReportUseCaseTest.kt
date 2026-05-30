package com.agarthavision.data.supabase

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.domain.model.ReportSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class SyncReportUseCaseTest {
    @Test
    fun `marks report SYNCED when remote upsert succeeds`() = runTest {
        val report = entity("report-1")
        val dao = FakeReportDao(seeded = listOf(report))
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote)

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
        val useCase = SyncReportUseCase(dao, remote)

        val result = useCase("report-2")

        assertTrue(result.isFailure)
        assertEquals(ReportSyncStatus.SYNC_FAILED.value, dao.statusOf("report-2"))
    }

    @Test
    fun `returns failure when report is not found`() = runTest {
        val dao = FakeReportDao(seeded = emptyList())
        val remote = StubRemoteDataSource(shouldThrow = false)
        val useCase = SyncReportUseCase(dao, remote)

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
        epgPerSpeciesJson = "{}",
        csvFilePath = "/downloads/report.csv",
        supabaseStatus = status,
        createdAt = 1_000L,
    )

private class FakeReportDao(seeded: List<ReportEntity>) : ReportDao {
    private val rows = seeded.associateBy { it.reportId }.toMutableMap()

    fun statusOf(reportId: String): String? = rows[reportId]?.supabaseStatus

    override suspend fun insertReport(report: ReportEntity) {
        rows[report.reportId] = report
    }

    override fun observeReportsForSession(sessionId: String, userId: String): Flow<List<ReportEntity>> =
        flowOf(rows.values.filter { it.sessionId == sessionId && it.userId == userId })

    override suspend fun getReportById(reportId: String): ReportEntity? = rows[reportId]

    override suspend fun getReportsPendingSync(userId: String): List<ReportEntity> =
        rows.values.filter {
            it.userId == userId && it.supabaseStatus in setOf("pending", "sync_failed")
        }

    override suspend fun updateSupabaseStatus(reportId: String, status: String) {
        rows[reportId]?.let { rows[reportId] = it.copy(supabaseStatus = status) }
    }
}

private class StubRemoteDataSource(
    private val shouldThrow: Boolean,
) : ReportRemoteDataSource(
    supabase = mock(),
    gson = com.google.gson.Gson(),
) {
    var upsertCallCount = 0
        private set

    override suspend fun upsertReport(report: ReportEntity) {
        upsertCallCount++
        if (shouldThrow) {
            throw IllegalStateException("simulated upstream failure")
        }
    }
}
