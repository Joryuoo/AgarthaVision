package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.SpeciesSuggestionEntity

/**
 * Reads and refreshes the offline species suggestion index.
 *
 * None of these queries touch `samples`, so `SoftDeleteGuardTest` has nothing to enforce
 * here and this file is deliberately not in its `daoFiles` list. If a future method does
 * join `samples`, add it there in the same change.
 */
@Dao
interface SpeciesSuggestionDao {

    /**
     * Names beginning with [prefix], case-insensitively, alphabetical.
     *
     * A prefix match rather than a contains match: the point is to complete what the
     * medtech is typing, and a leading wildcard would surface unrelated names that merely
     * contain the fragment.
     *
     * `ESCAPE` is not optional: the needle is free text, so without it a typed `_` is a
     * single-character wildcard and a typed `%` matches the whole index. The caller escapes
     * with `core/util/escapeLike`.
     */
    @Query(
        """
        SELECT * FROM species_suggestions
        WHERE lookup LIKE :prefix || '%' ESCAPE '\'
        ORDER BY species ASC
        LIMIT :limit
        """,
    )
    suspend fun searchByPrefix(prefix: String, limit: Int): List<SpeciesSuggestionEntity>

    @Query("SELECT COUNT(*) FROM species_suggestions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suggestions: List<SpeciesSuggestionEntity>)

    /**
     * Distinct species already recorded on this device, from both places one can appear:
     * a logged finding, and an expert-corrected detection class.
     *
     * Deleted samples are excluded. A tombstoned sample's species is still in the
     * retraining corpus by design (C8), but a name that only survives on a rejected frame
     * should not be offered as though it were established — and every query that counts
     * samples filters `deleted_at is null`.
     */
    @Query(
        """
        SELECT DISTINCT f.species FROM sample_species_findings f
        INNER JOIN samples sa ON sa.sample_id = f.sample_id
        WHERE sa.deleted_at is null AND TRIM(f.species) <> ''
        UNION
        SELECT DISTINCT d.expert_class FROM detections d
        INNER JOIN samples sa ON sa.sample_id = d.sample_id
        WHERE sa.deleted_at is null
          AND d.expert_class IS NOT NULL
          AND TRIM(d.expert_class) <> ''
        """,
    )
    suspend fun observedSpecies(): List<String>
}
