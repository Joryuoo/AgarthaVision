package com.agarthavision.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Embedded
import com.agarthavision.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for capture sessions.
 *
 * Room binds one DAO interface per entity — this codebase follows that convention
 * throughout, so splitting [SessionDao] across multiple interfaces would be
 * inconsistent with every other `@Dao` in the project for no functional benefit.
 */
@Suppress("TooManyFunctions")
@Dao
interface SessionDao {
    /**
     * Writes a session, inserting or updating in place.
     *
     * **`@Upsert`, not `@Insert(REPLACE)`**, for the reason spelled out on
     * [SampleDao.upsertSample]: a REPLACE conflict deletes the existing row before re-inserting
     * it, and `reports.session_id` is `onDelete = CASCADE`. Re-inserting a session the device
     * already holds silently deleted its reports — the row, not the CSV or PDF on disk, so the
     * files stayed behind orphaned and unreachable while every list that reads them went empty.
     *
     * Note what does *not* save this. `samples.session_id` is `NO_ACTION`, which refuses a
     * delete that would orphan samples — but REPLACE re-inserts the parent under the same id
     * inside the same statement, so the constraint is satisfied by the time it is checked and
     * the statement succeeds. The samples survive; the reports are already gone. A NO_ACTION
     * key is not a guard against this.
     *
     * `SampleDaoUpsertCascadeTest` pins it.
     */
    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    /**
     * Observes sessions visible to the caller: their own rows plus unowned rows recorded while
     * signed out. A null owner (signed out) sees only the unowned rows - never another
     * medtech's data left on a shared phone.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE (user_id = :userId OR user_id IS NULL)
        ORDER BY started_at DESC
        """,
    )
    fun observeAllSessions(userId: String?): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET label = :label WHERE session_id = :sessionId")
    suspend fun updateSessionLabel(sessionId: String, label: String)

    /**
     * Every label already minted for one patient, for the sequence in the next one.
     *
     * Deliberately not `MAX(...)` in SQL: the sequence is the tail of a text label the medtech
     * can edit, so `MAX` over the whole string would order lexically and pick the label that
     * sorts last rather than the highest number. Parsing happens in
     * [com.agarthavision.domain.session.SessionLabelGenerator], where it is testable.
     *
     * Rows with no label are excluded rather than returned as nulls — an unlabelled session
     * holds no sequence.
     */
    @Query("SELECT label FROM sessions WHERE patient_id = :patientId AND label IS NOT NULL")
    suspend fun getLabelsForPatient(patientId: String): List<String>

    /**
     * Observes sessions visible to a signed-out or offline medtech: those owned by
     * [userId] plus any not-yet-claimed local sessions (`user_id IS NULL`). Newest first.
     * Per ADR-007.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id = :userId OR user_id IS NULL
        ORDER BY started_at DESC
        """
    )
    fun observeOwnedOrUnowned(userId: String): Flow<List<SessionEntity>>

    // `observeAllLocal` is gone. It was `SELECT * FROM sessions` with no owner guard, for a
    // never-logged-in device — a state mandatory first-run login (86d4be3ke) removed. On a
    // shared phone it returned another medtech's smears. Signed out now reads as empty, in
    // SessionRepositoryImpl; see `observeAllSessions` above for the guard that was right.

    /**
     * Sessions still awaiting cloud upload, oldest first so the sync pass pushes them in
     * creation order.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE user_id = :userId
          AND supabase_status IN ('pending', 'sync_failed')
        ORDER BY started_at ASC
        """
    )
    suspend fun getSessionsPendingSync(userId: String): List<SessionEntity>

    /**
     * Hard-deletes a session and, by cascade, its reports (`ReportEntity` declares
     * `onDelete = CASCADE`).
     *
     * **Samples are deliberately untouched.** `SampleEntity` declares its `session_id` foreign
     * key as `NO_ACTION` (added in Room 16), so this refuses outright rather than cascading into
     * the verified samples and detections that C8 protects — the caller has to deal with the
     * samples first, and `DiscardUnsyncedDataUseCase` already does. Sign-out tombstones those
     * separately rather than deleting them.
     */
    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /** Sessions still attached to a patient, used to keep a delete off a NO_ACTION foreign key. */
    @Query("SELECT COUNT(*) FROM sessions WHERE patient_id = :patientId")
    suspend fun countSessionsForPatient(patientId: String): Int

    /**
     * Counts how many sessions for [patientId] already carry [label], excluding
     * [excludingSessionId] so an in-place rename does not flag itself.
     *
     * Used by [com.agarthavision.data.repository.SessionRepositoryImpl.isSessionLabelTaken]
     * to enforce per-patient label uniqueness before writing. Pass an empty string for
     * [excludingSessionId] when checking a new session (no id to exclude yet).
     */
    @Query(
        "SELECT COUNT(*) FROM sessions " +
        "WHERE patient_id = :patientId AND label = :label AND session_id != :excludingSessionId"
    )
    suspend fun countLabelCollisions(
        patientId: String,
        label: String,
        excludingSessionId: String,
    ): Int

    /** Updates the Room-only cloud sync status for a session. Per ADR-007. */
    @Query("UPDATE sessions SET supabase_status = :status WHERE session_id = :sessionId")
    suspend fun updateSupabaseStatus(sessionId: String, status: String)

    /**
     * Live count of sessions still awaiting cloud upload (`pending` only, not
     * `sync_failed`). Drives the Settings Data & Sync section.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE user_id = :userId AND supabase_status = 'pending'
        """,
    )
    fun observePendingCount(userId: String): Flow<Int>

    /**
     * Live count of sessions whose last sync attempt failed. Drives the Settings
     * Data & Sync section.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE user_id = :userId AND supabase_status = 'sync_failed'
        """,
    )
    fun observeFailedCount(userId: String): Flow<Int>

    /**
     * Observes sessions with their associated sample, verification, and egg counts.
     */
    @Query(
        """
        SELECT s.*,
               COUNT(DISTINCT smp.sample_id) AS totalSamples,
               SUM(CASE WHEN smp.verified_at > 0 THEN 1 ELSE 0 END) AS verifiedSamples,
               SUM(
                 CASE WHEN smp.status = 'flagged' THEN 1 ELSE 0 END
               ) AS unverifiedSamples,
               COUNT(d.detection_id) AS totalEggs
        FROM sessions s
        LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.deleted_at is null
        LEFT JOIN detections d ON smp.sample_id = d.sample_id AND d.verdict = 'confirmed'
        WHERE s.user_id = :userId
          AND s.started_at >= :sinceMillis
        GROUP BY s.session_id
        ORDER BY s.started_at DESC
        """
    )
    fun observeSessionsWithStats(userId: String, sinceMillis: Long): Flow<List<SessionWithStats>>

    /**
     * Observes a paginated, filtered window of sessions for the Records screen.
     * Non-flagged sample counts and non-false-positive detection totals are
     * pre-aggregated so the UI avoids per-session N+1 queries. The verdict filter
     * (`d.verdict != 'false_positive'`) matches [DetectionDao.getConfirmedEggCountsForSession]
     * so Records cards and Session Detail counts are always consistent.
     * Species-label lookup is done separately via [DetectionDao.getSpeciesLabelsForSessions].
     *
     * Shares [RECORDS_FILTER] with [observeSessionRecordsTotals] so filtering logic
     * can never diverge between the page and the stat-row totals.
     *
     * The parameter list maps one-to-one onto named SQL bind parameters, so it cannot
     * be collapsed into a holder type without losing Room's query binding.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT s.*, " +
        "  COUNT(DISTINCT CASE WHEN smp.status != 'flagged' THEN smp.sample_id END) AS totalSamples, " +
        "  COUNT(d.detection_id) AS totalEggs " +
        "FROM sessions s " +
        "LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.status != 'flagged' " +
        "     AND smp.deleted_at is null " +
        "LEFT JOIN detections d ON smp.sample_id = d.sample_id " +
        "     AND d.verdict != 'false_positive'" +
        RECORDS_FILTER +
        " GROUP BY s.session_id ORDER BY s.started_at DESC LIMIT :limit"
    )
    fun observeSessionRecordsPage(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
        limit: Int,
    ): Flow<List<SessionRecordStatsRow>>

    /**
     * Whole-filtered-set totals for the Records stat row. Applies the same [RECORDS_FILTER]
     * as [observeSessionRecordsPage] but no LIMIT, aggregated from a per-session subquery so
     * join fan-out cannot inflate the sums.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT COUNT(*) AS sessionCount, " +
        "COALESCE(SUM(perSession.samples), 0) AS totalSamples, " +
        "COALESCE(SUM(perSession.eggs), 0) AS totalEggs " +
        "FROM (SELECT s.session_id, " +
        "  COUNT(DISTINCT CASE WHEN smp.status != 'flagged' THEN smp.sample_id END) AS samples, " +
        "  COUNT(d.detection_id) AS eggs " +
        "  FROM sessions s " +
        "  LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.status != 'flagged' " +
        "       AND smp.deleted_at is null " +
        "  LEFT JOIN detections d ON smp.sample_id = d.sample_id" +
        "    AND d.verdict != 'false_positive' " +
        RECORDS_FILTER +
        "  GROUP BY s.session_id) AS perSession"
    )
    fun observeSessionRecordsTotals(
        userId: String?,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        species: String?,
    ): Flow<RecordsTotalsRow>

    // -------------------------------------------------------------------------
    // Sessions screen: paginated, filtered, with active-session priority
    // -------------------------------------------------------------------------

    /**
     * Observes a paginated, filtered window of sessions for the Sessions screen.
     * Aggregate columns mirror [observeSessionsWithStats] so [SessionCard] can display
     * the same metrics. The active session is always included; every other session
     * appears only within the recent window or the explicit date range.
     *
     * Shares [SESSIONS_FILTER] with [observeSessionsCounts] so count and list can
     * never disagree on the filtered universe. Per ADR-007.
     *
     * The parameter list maps one-to-one onto named SQL bind parameters, so it cannot
     * be collapsed into a holder type without losing Room's query binding.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT s.*, " +
        "  COUNT(DISTINCT smp.sample_id) AS totalSamples, " +
        "  SUM(CASE WHEN smp.verified_at > 0 THEN 1 ELSE 0 END) AS verifiedSamples, " +
        "  SUM(CASE WHEN smp.status = 'flagged' THEN 1 ELSE 0 END) AS unverifiedSamples, " +
        "  COUNT(d.detection_id) AS totalEggs " +
        "FROM sessions s " +
        "LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.deleted_at is null " +
        "LEFT JOIN detections d ON smp.sample_id = d.sample_id AND d.verdict = 'confirmed'" +
        SESSIONS_FILTER +
        " GROUP BY s.session_id ORDER BY s.started_at DESC LIMIT :limit"
    )
    fun observeSessionsPage(
        userId: String,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
        limit: Int,
    ): Flow<List<SessionWithStats>>

    /**
     * Live count of total sessions and unreviewed frames matching [SESSIONS_FILTER].
     * Shares the predicate with [observeSessionsPage] so the header counts
     * and the list can never disagree. Per ADR-007.
     *
     * The second column counts frames still awaiting review — a number the medtech can act
     * on. It used to count open sessions, which stopped distinguishing anything when
     * sessions stopped ending (86d4ab4vm).
     *
     * It is computed here rather than summed from the loaded page because the list is
     * paginated: a locally-summed header would report only what had been scrolled into
     * view and shrink as the filter narrowed, without ever looking wrong.
     */
    @Suppress("LongParameterList")
    @Query(
        "SELECT COUNT(DISTINCT s.session_id) AS totalCount, " +
        "COALESCE(SUM(CASE WHEN smp.status = 'flagged' THEN 1 ELSE 0 END), 0) AS unverifiedCount " +
        "FROM sessions s " +
        "LEFT JOIN samples smp ON s.session_id = smp.session_id AND smp.deleted_at is null" +
        SESSIONS_FILTER
    )
    fun observeSessionsCounts(
        userId: String,
        patientId: String,
        activeSessionId: String?,
        sinceMillis: Long,
        startMillis: Long?,
        endMillis: Long?,
        query: String,
    ): Flow<SessionsCountsRow>

    // `observeAllLocalPage` and `observeAllLocalCounts` are gone with LOCAL_SESSIONS_FILTER,
    // for the reason above. That filter carried the patient scope and the date range but no
    // owner guard at all, so a signed-out Session List showed every smear recorded under the
    // patient by anyone who had used the device.
}

/**
 * Shared WHERE predicate for the Records paginated page and totals queries.
 * Extracted here so both queries apply identical filtering and can never disagree.
 * Search LIKE clauses use `ESCAPE '\'` so the caller can safely escape `%`, `_`,
 * and `\` in the needle before passing it in. Species LIKE is intentionally left
 * without ESCAPE since species needles come from a fixed enum, not free text.
 */
private const val RECORDS_FILTER = """
  WHERE (s.user_id = :userId OR s.user_id IS NULL)
    AND (:startMillis IS NULL OR s.started_at >= :startMillis)
    AND (:endMillis   IS NULL OR s.started_at <= :endMillis)
    AND (:query = ''
         OR s.session_id LIKE '%' || :query || '%' ESCAPE '\'
         OR s.label      LIKE '%' || :query || '%' ESCAPE '\'
         OR EXISTS (SELECT 1 FROM detections dq JOIN samples sq ON sq.sample_id = dq.sample_id
                    WHERE sq.session_id = s.session_id AND sq.deleted_at is null
                      AND dq.verdict != 'false_positive'
                      AND COALESCE(dq.expert_class, dq.class_label) LIKE '%' || :query || '%' ESCAPE '\'))
    AND (:species IS NULL
         OR EXISTS (SELECT 1 FROM detections ds JOIN samples ss ON ss.sample_id = ds.sample_id
                    WHERE ss.session_id = s.session_id AND ss.deleted_at is null
                      AND ds.verdict != 'false_positive'
                      AND COALESCE(ds.expert_class, ds.class_label) LIKE '%' || :species || '%'))
"""

/**
 * Shared WHERE predicate for the Sessions paginated page and counts queries.
 * The list belongs to **one patient**: `:patientId` is a hard AND above everything else,
 * including the active-session exemption, because a smear open under patient A has no
 * business appearing in patient B's list. Within that patient the **active** session is
 * always visible; every other session appears when it falls within the recent window
 * (`:sinceMillis`) or within an explicit date range (`:startMillis`/`:endMillis`).
 *
 * That exemption used to read `ended_at IS NULL`, meaning "a session still open". Once
 * sessions stopped ending (86d4ab4vm) that matched every session ever started, and the
 * date filter and pagination this predicate exists to serve would have matched everything
 * while still looking correct. Pinning it to `:activeSessionId` keeps the original intent
 * exactly — never hide the smear the medtech is working in — and nothing else.
 * Pass null when there is no active session.
 *
 * Search LIKE clauses use `ESCAPE '\'` so the caller can safely escape `%`, `_`,
 * and `\` in the needle before passing it in.
 */
private const val SESSIONS_FILTER = """
  WHERE s.user_id = :userId
    AND s.patient_id = :patientId
    AND ( s.session_id = :activeSessionId
          OR (:startMillis IS NULL AND :endMillis IS NULL AND s.started_at >= :sinceMillis)
          OR (:startMillis IS NOT NULL AND s.started_at >= :startMillis AND s.started_at <= :endMillis) )
    AND (:query = ''
         OR s.session_id LIKE '%' || :query || '%' ESCAPE '\'
         OR s.label      LIKE '%' || :query || '%' ESCAPE '\')
"""

/** Aggregate row returned by [SessionDao.observeSessionsCounts]. */
data class SessionsCountsRow(
    @ColumnInfo(name = "totalCount") val totalCount: Int,
    /** Frames still awaiting review across the filtered sessions. See [SessionDao]. */
    @ColumnInfo(name = "unverifiedCount") val unverifiedCount: Int,
)

data class SessionWithStats(
    @Embedded val session: SessionEntity,
    @androidx.room.ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @androidx.room.ColumnInfo(name = "verifiedSamples") val verifiedSamples: Int,
    /**
     * Frames still awaiting review. Drives the Sessions header, which used to count open
     * sessions — a number that stopped meaning anything when sessions stopped ending.
     */
    @androidx.room.ColumnInfo(name = "unverifiedSamples") val unverifiedSamples: Int,
    @androidx.room.ColumnInfo(name = "totalEggs") val totalEggs: Int
)

/**
 * Slim projection returned by [SessionDao.observeSessionRecordsPage].
 * Carries only the aggregate columns needed by the Records screen; species labels
 * are fetched separately via [DetectionDao.getSpeciesLabelsForSessions].
 */
data class SessionRecordStatsRow(
    @Embedded val session: SessionEntity,
    @androidx.room.ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @androidx.room.ColumnInfo(name = "totalEggs") val totalEggs: Int,
)

/**
 * Aggregate totals returned by [SessionDao.observeSessionRecordsTotals].
 * All counts apply the same [RECORDS_FILTER] as the page query.
 */
data class RecordsTotalsRow(
    @ColumnInfo(name = "sessionCount") val sessionCount: Int,
    @ColumnInfo(name = "totalSamples") val totalSamples: Int,
    @ColumnInfo(name = "totalEggs") val totalEggs: Int,
)
