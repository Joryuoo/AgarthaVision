package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity

/**
 * Reads and writes the per-species egg counts a medtech logged on one frame.
 *
 * There is deliberately no session-level aggregate query here. Rolling findings up into a
 * session figure is an average across the fields examined, not a sum — a density that gets
 * summed across 10 fields is not a density — and that belongs to the reporting tickets
 * (86d4a6jxw owns the unit, 86d4a6jyy owns the template). Adding a dead aggregate now would
 * be unused code.
 */
@Dao
interface SampleSpeciesFindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFindings(findings: List<SampleSpeciesFindingEntity>)

    @Query(
        "SELECT * FROM sample_species_findings WHERE sample_id = :sampleId " +
            "ORDER BY species ASC",
    )
    suspend fun getFindingsForSample(sampleId: String): List<SampleSpeciesFindingEntity>

    @Query(
        """
        SELECT f.* FROM sample_species_findings f
        JOIN samples s ON s.sample_id = f.sample_id
        WHERE s.session_id = :sessionId
          AND (s.user_id = :userId OR s.user_id IS NULL)
          AND s.deleted_at is null
        """,
    )
    suspend fun getFindingsForSession(
        sessionId: String,
        userId: String?,
    ): List<SampleSpeciesFindingEntity>

    @Query("DELETE FROM sample_species_findings WHERE sample_id = :sampleId")
    suspend fun deleteFindingsForSample(sampleId: String)

    @Query("DELETE FROM sample_species_findings WHERE sample_id IN (:sampleIds)")
    suspend fun deleteFindingsForSamples(sampleIds: List<String>)

    /**
     * Replaces a sample's findings wholesale.
     *
     * The medtech's current statement supersedes their previous one, so a species they
     * removed on re-open has to actually disappear — an insert-or-replace alone would leave
     * the stale row behind and inflate the count. This is not a C8 deletion: a count is a
     * current statement, like `samples.user_note`, and the things C8 protects — the JPEG and
     * the detection rows — are untouched.
     */
    @Transaction
    suspend fun replaceFindingsForSample(
        sampleId: String,
        findings: List<SampleSpeciesFindingEntity>,
    ) {
        deleteFindingsForSample(sampleId)
        insertFindings(findings)
    }

    /**
     * [replaceFindingsForSample] across a batch, for the pull path.
     *
     * **The sample ids are the scope, not the findings.** A sample the server holds no findings
     * for is a clean field, and its local rows have to go — passing only the ids that appear in
     * [findings] would leave a stale count on exactly the sample whose species were all removed.
     * So the delete is keyed on every id in [sampleIds], and [findings] is whatever came back
     * for them.
     *
     * The caller chunks [sampleIds] to stay under the SQLite host-parameter limit (E5), the same
     * bound the remote fetches are chunked by.
     */
    @Transaction
    suspend fun replaceFindingsForSamples(
        sampleIds: List<String>,
        findings: List<SampleSpeciesFindingEntity>,
    ) {
        if (sampleIds.isEmpty()) return
        deleteFindingsForSamples(sampleIds)
        if (findings.isNotEmpty()) {
            insertFindings(findings)
        }
    }
}
