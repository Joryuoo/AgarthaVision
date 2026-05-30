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

    override fun observeForSession(sessionId: String, userId: String): Flow<List<Report>> =
        reportDao.observeReportsForSession(sessionId, userId).map { rows ->
            rows.map { it.toDomain(gson) }
        }

    override suspend fun getById(reportId: String): Report? =
        reportDao.getReportById(reportId)?.toDomain(gson)

    override suspend fun getReportsPendingSync(userId: String): List<Report> =
        reportDao.getReportsPendingSync(userId).map { it.toDomain(gson) }

    override suspend fun updateSupabaseStatus(reportId: String, status: ReportSyncStatus) {
        reportDao.updateSupabaseStatus(reportId, status.value)
    }
}
