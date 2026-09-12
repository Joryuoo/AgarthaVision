package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity

/**
 * Reads and writes the per-species, per-stage egg counts a medtech logged on one frame.
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
            "ORDER BY species ASC, stage ASC",
    )
    suspend fun getFindingsForSample(sampleId: String): List<SampleSpeciesFindingEntity>

    @Query("DELETE FROM sample_species_findings WHERE sample_id = :sampleId")
    suspend fun deleteFindingsForSample(sampleId: String)

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
}
