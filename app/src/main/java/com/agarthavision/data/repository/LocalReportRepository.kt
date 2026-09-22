package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.repository.ReportRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [ReportRepository].
 */
class LocalReportRepository @Inject constructor(
    private val reportDao: ReportDao,
    private val gson: Gson,
) : ReportRepository {

    override suspend fun insert(report: Report) {
        reportDao.insertReport(report.toEntity(gson))
    }

    override fun observeForSession(
        sessionId: String,
        userId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<Report>> =
        reportDao.observeReportsForSession(sessionId, userId, limit, offset).map { rows ->
            rows.map { it.toDomain(gson) }
        }

    override fun observeCountForSession(sessionId: String, userId: String): Flow<Int> =
        reportDao.observeReportCountForSession(sessionId, userId)

    override fun observeAll(userId: String, limit: Int, offset: Int): Flow<List<Report>> =
        reportDao.observeAllReports(userId, limit, offset).map { rows ->
            rows.map { it.toDomain(gson) }
        }

    override fun observeAllCount(userId: String): Flow<Int> =
        reportDao.observeAllReportsCount(userId)

    override fun observeFiltered(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<Report>> =
        reportDao.observeFilteredReports(userId, startMillis, endMillis, species, query, limit, offset).map { rows ->
            rows.map { it.toDomain(gson) }
        }

    override fun observeFilteredCount(
        userId: String,
        startMillis: Long?,
        endMillis: Long?,
        species: String?,
        query: String,
    ): Flow<Int> =
        reportDao.observeFilteredReportsCount(userId, startMillis, endMillis, species, query)

    override suspend fun getById(reportId: String): Report? =
        reportDao.getReportById(reportId)?.toDomain(gson)

    override suspend fun getReportsPendingSync(userId: String): List<Report> =
        reportDao.getReportsPendingSync(userId).map { it.toDomain(gson) }

    override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) {
        reportDao.updateSupabaseStatus(reportId, status.value)
    }
}
