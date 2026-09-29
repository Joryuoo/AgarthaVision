package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.model.RecordsTotals
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionsCounts
import com.agarthavision.domain.model.SessionWithStats
import com.agarthavision.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed implementation of [SessionRepository].
 */
@Suppress("TooManyFunctions")
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override fun observeAllSessions(userId: String?): Flow<List<Session>> =
        sessionDao.observeAllSessions(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSessionById(sessionId: String): Session? =
        sessionDao.getSessionById(sessionId)?.toDomain()

    override fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>> =
        sessionDao.observeSessionsWithStats(userId, sinceMillis).map { list ->
            list.map { item ->
                SessionWithStats(
                    session = item.session.toDomain(),
                    totalSamples = item.totalSamples,
                    verifiedSamples = item.verifiedSamples,
                    unverifiedSamples = item.unverifiedSamples,
                    totalEggs = item.totalEggs
                )
            }
        }

    override suspend fun updateSessionLabel(sessionId: String, label: String) {
        sessionDao.updateSessionLabel(sessionId, label)
    }

    override suspend fun isSessionLabelTaken(
        patientId: String,
        label: String,
        excludingSessionId: String?,
    ): Boolean = sessionDao.countLabelCollisions(
        patientId = patientId,
        label = label,
        excludingSessionId = excludingSessionId ?: "",
    ) > 0

    override suspend fun getSessionLabelsForPatient(patientId: String): List<String> =
        sessionDao.getLabelsForPatient(patientId)

    override suspend fun getSessionsForPatient(patientId: String, userId: String): List<Session> =
        sessionDao.getSessionsForPatient(patientId, userId).map { it.toDomain() }

    /**
     * A signed-out caller sees nothing, not everything.
     *
     * This used to fall through to an unfiltered `SELECT * FROM sessions`. On a personal
     * device that reads as "the sessions on this phone"; on a shared one it is another
     * medtech's smears. Since login is mandatory on first run there is no legitimate
     * signed-out reader left, so the null case is empty rather than unscoped.
     */
    override fun observeVisibleSessions(userId: String?): Flow<List<Session>> =
        if (userId == null) {
            flowOf(emptyList())
        } else {
            sessionDao.observeOwnedOrUnowned(userId).map { list -> list.map { it.toDomain() } }
        }

    override fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionWithStats>> =
        sessionDao.observeSessionRecordsPage(userId, startMillis, endMillis, query, species, limit)
            .map { list ->
                list.map { row ->
                    SessionWithStats(
                        session = row.session.toDomain(),
                        totalSamples = row.totalSamples,
                        verifiedSamples = 0,
                        unverifiedSamples = 0,
                        totalEggs = row.totalEggs,
                    )
                }
            }

    override fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotals> =
        sessionDao.observeSessionRecordsTotals(userId, startMillis, endMillis, query, species)
            .map { row ->
                RecordsTotals(
                    sessionCount = row.sessionCount,
                    totalSamples = row.totalSamples,
                    totalEggs = row.totalEggs,
                )
            }

    /**
     * A signed-out caller gets an empty page, for the reason on [observeVisibleSessions].
     *
     * This is the path that actually leaked. `LOCAL_SESSIONS_FILTER` carried the patient
     * scope and the date range but no owner guard at all, so signing out turned the Session
     * List into every smear recorded under that patient by anyone who had used the device.
     * `SessionDao.observeAllSessions` had the guard right — `user_id = :userId OR user_id IS
     * NULL`, with its KDoc saying "never another medtech's data left on a shared phone" —
     * and the paginated path written later simply did not carry it over.
     */
    override fun observeVisibleSessionsPage(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>> = if (userId == null) {
        flowOf(emptyList())
    } else {
        sessionDao.observeSessionsPage(
            userId, patientId, activeSessionId, sinceMillis, startMillis, endMillis, query, limit,
        )
            .map { list ->
                list.map { item ->
                    SessionWithStats(
                        session = item.session.toDomain(),
                        totalSamples = item.totalSamples,
                        verifiedSamples = item.verifiedSamples,
                        unverifiedSamples = item.unverifiedSamples,
                        totalEggs = item.totalEggs,
                    )
                }
            }
    }

    /** Empty counts for a signed-out caller, matching [observeVisibleSessionsPage]. */
    override fun observeVisibleSessionsCounts(
        userId: String?,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCounts> = if (userId == null) {
        flowOf(SessionsCounts())
    } else {
        sessionDao.observeSessionsCounts(
            userId, patientId, activeSessionId, sinceMillis, startMillis, endMillis, query,
        )
            .map { row ->
                SessionsCounts(totalCount = row.totalCount, unverifiedCount = row.unverifiedCount)
            }
    }

    override fun observeSessionSummaries(
        userId: String,
        filter: com.agarthavision.domain.model.SessionListFilter,
        window: com.agarthavision.domain.model.TimeWindow?,
        limit: Int,
    ): Flow<List<com.agarthavision.domain.model.SessionSummary>> =
        sessionDao.observeSessionSummaries(
            userId = userId,
            filter = filter.sql,
            fromMillis = window?.startMillis,
            toMillis = window?.endMillis,
            limit = limit,
        ).map { rows ->
            rows.map { row ->
                com.agarthavision.domain.model.SessionSummary(
                    session = row.session.toDomain(),
                    patient = row.patient.toDomain(),
                    totalFrames = row.totalSamples,
                    framesToReview = row.unverifiedSamples,
                    isPositive = row.isPositive,
                    lastActivityAt = row.lastActivityAt,
                )
            }
        }

    override fun observeSessionSummaryCount(
        userId: String,
        filter: com.agarthavision.domain.model.SessionListFilter,
        window: com.agarthavision.domain.model.TimeWindow?,
    ): Flow<Int> =
        sessionDao.observeSessionSummaryCount(
            userId = userId,
            filter = filter.sql,
            fromMillis = window?.startMillis,
            toMillis = window?.endMillis,
        )

    override fun observeEmptySessionCount(
        userId: String,
        excludeSessionId: String?,
    ): Flow<Int> =
        sessionDao.observeEmptySessionCount(userId, excludeSessionId)

    override fun observeSessionOutcomesBetween(
        userId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<com.agarthavision.domain.model.SessionOutcome>> =
        sessionDao.observeSessionOutcomesBetween(userId, fromMillis, toMillis).map { rows ->
            rows.map {
                com.agarthavision.domain.model.SessionOutcome(
                    sessionId = it.sessionId,
                    patientId = it.patientId,
                    startedAt = it.startedAt,
                    examined = it.examined,
                    positive = it.positive,
                )
            }
        }

    override fun observeStartedActivity(
        userId: String,
        limit: Int,
    ): Flow<List<ActivityItem.SessionStarted>> =
        sessionDao.observeStartedActivity(userId, limit).map { rows ->
            rows.map {
                ActivityItem.SessionStarted(
                    sessionId = it.sessionId,
                    sessionLabel = it.sessionLabel,
                    occurredAt = it.occurredAt,
                )
            }
        }
}
