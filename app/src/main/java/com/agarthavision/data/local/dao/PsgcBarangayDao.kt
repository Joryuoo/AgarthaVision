package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agarthavision.data.local.entity.PsgcBarangayEntity

/**
 * Data access object for the bundled PSGC barangay reference data.
 *
 * Seeded from an asset by [com.agarthavision.data.local.psgc.PsgcSeeder] and read-only
 * thereafter.
 */
@Dao
interface PsgcBarangayDao {
    @Query("SELECT COUNT(*) FROM psgc_barangays")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(barangays: List<PsgcBarangayEntity>)

    /**
     * Clears the reference table so a new PSGC vintage can replace it.
     *
     * Not a C8 concern: this is bundled reference data with no clinical meaning and no
     * remote mirror. Nothing here is a sample, a detection, or a Storage object, and a
     * session keeps its barangay code regardless of what this table holds.
     */
    @Query("DELETE FROM psgc_barangays")
    suspend fun deleteAll()

    @Query("SELECT * FROM psgc_barangays WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): PsgcBarangayEntity?

    /**
     * Infix search over [PsgcBarangayEntity.searchText], prefix matches first then
     * alphabetical.
     *
     * [pattern] must already be lowercased and `LIKE`-escaped — see
     * [com.agarthavision.data.repository.PsgcRepositoryImpl]. A full 42k-row scan, which
     * measures in single-digit milliseconds and is why no index exists.
     */
    @Query(
        """
        SELECT * FROM psgc_barangays
        WHERE search_text LIKE '%' || :pattern || '%' ESCAPE '\'
        ORDER BY
            CASE WHEN search_text LIKE :pattern || '%' ESCAPE '\' THEN 0 ELSE 1 END,
            name
        LIMIT :limit
        """,
    )
    suspend fun search(pattern: String, limit: Int): List<PsgcBarangayEntity>
}
