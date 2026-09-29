package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the My coverage card: rolls examined smears up per town so Home
 * can show where a medtech's positive cases are coming from.
 *
 * Mirrors [DetectionDao.observeSessionFindingsBetween]'s examined/positive rule and its
 * `patients -> psgc_barangays` join exactly, but counts smears rather than species pairs.
 */
@Dao
interface CoverageDao {
    /**
     * One row per town with at least one examined smear in the window. `townCode` is null
     * when the patient's barangay code isn't in the bundled PSGC list (a retired/unrecognized
     * code) — those smears still count toward overall totals, just not toward any town.
     */
    @Query(
        """
        SELECT b.city_muni_code AS townCode, COUNT(*) AS smearCount, SUM(sm.is_positive) AS positiveCount
        FROM (
            SELECT s.session_id AS session_id, p.psgc_barangay_code AS barangay_code,
                EXISTS (
                    SELECT 1 FROM samples sp
                    JOIN detections d ON d.sample_id = sp.sample_id
                    WHERE sp.session_id = s.session_id
                      AND sp.deleted_at is null
                      AND sp.status != 'flagged'
                      AND d.verdict != 'false_positive'
                ) AS is_positive
            FROM sessions s
            JOIN patients p ON p.patient_id = s.patient_id
            WHERE s.user_id = :userId
              AND s.started_at >= :startMillis
              AND s.started_at < :endMillis
              AND EXISTS (
                  SELECT 1 FROM samples sr
                  WHERE sr.session_id = s.session_id
                    AND sr.deleted_at is null
                    AND sr.status != 'flagged'
              )
        ) sm
        LEFT JOIN psgc_barangays b ON b.code = sm.barangay_code
        GROUP BY b.city_muni_code
        """,
    )
    fun observeTownCoverage(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TownCoverageRow>>
}

data class TownCoverageRow(
    val townCode: String?,
    val smearCount: Int,
    val positiveCount: Int,
)
