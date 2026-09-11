package com.agarthavision.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
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
     * Barangays whose [PsgcBarangayEntity.searchText] contains **every** term, prefix
     * matches on the first term first, then alphabetical.
     *
     * [terms] must come from [com.agarthavision.data.local.psgc.PsgcSearchQuery.terms],
     * which folds case and escapes the `LIKE` wildcards. Returns nothing for no terms
     * rather than the whole country.
     *
     * A full 42k-row scan, measured in single-digit milliseconds, which is why
     * [PsgcBarangayEntity] carries no index: a leading-wildcard `LIKE` cannot use one.
     */
    suspend fun search(terms: List<String>, limit: Int): List<PsgcBarangayEntity> {
        if (terms.isEmpty()) return emptyList()
        return searchRaw(searchQuery(terms = terms, limit = limit))
    }

    /**
     * Raw because the number of `LIKE` clauses depends on how many words were typed, which
     * a static `@Query` cannot express. Use [search]; this is its execution half.
     */
    @RawQuery
    suspend fun searchRaw(query: SupportSQLiteQuery): List<PsgcBarangayEntity>

    private fun searchQuery(terms: List<String>, limit: Int): SupportSQLiteQuery {
        val where = terms.joinToString(separator = " AND ") { TERM_PREDICATE }
        return SimpleSQLiteQuery(
            """
            SELECT * FROM psgc_barangays
            WHERE $where
            ORDER BY
                CASE WHEN search_text LIKE ? || '%' ESCAPE '\' THEN 0 ELSE 1 END,
                name
            LIMIT ?
            """.trimIndent(),
            // Binds in statement order: one per term, then the first term again for the
            // prefix ranking, then the cap.
            (terms + terms.first() + limit).toTypedArray(),
        )
    }

    private companion object {
        private const val TERM_PREDICATE = "search_text LIKE '%' || ? || '%' ESCAPE '\\'"
    }
}
