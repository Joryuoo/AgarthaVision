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
     * Barangays whose [PsgcBarangayEntity.searchText] contains **every** term in [terms],
     * ranked by how closely the `name` column matches the joined [nameTerms], then
     * alphabetical.
     *
     * [terms] and [nameTerms] must come from
     * [com.agarthavision.data.local.psgc.PsgcSearchQuery.parse], which folds case and
     * escapes the `LIKE` wildcards. [nameTerms] defaults to [terms] — the no-comma case —
     * and drives ranking alone when the caller typed a comma, so "lahug, city of cebu"
     * ranks by "lahug" rather than the full, diluted phrase. Returns nothing for no terms
     * rather than the whole country.
     *
     * Ranking tiers (ORDER BY CASE on `name`):
     *   0 – exact name match (`lower(name) == joined nameTerms`)
     *   1 – name starts with the joined nameTerms
     *   2 – name contains the joined nameTerms as a contiguous substring
     *   3 – every individual nameTerm appears somewhere in name (any order/position)
     *   4 – everything else (still included via the WHERE filter)
     *
     * A full 42k-row scan, measured in single-digit milliseconds, which is why
     * [PsgcBarangayEntity] carries no index: a leading-wildcard `LIKE` cannot use one.
     */
    suspend fun search(
        terms: List<String>,
        limit: Int,
        nameTerms: List<String> = terms,
    ): List<PsgcBarangayEntity> {
        if (terms.isEmpty()) return emptyList()
        return searchRaw(
            psgcSearchQuery(terms = terms, nameTerms = nameTerms.ifEmpty { terms }, limit = limit),
        )
    }

    /**
     * Raw because the number of `LIKE` clauses depends on how many words were typed, which
     * a static `@Query` cannot express. Use [search]; this is its execution half.
     */
    @RawQuery
    suspend fun searchRaw(query: SupportSQLiteQuery): List<PsgcBarangayEntity>
}

private const val TERM_PREDICATE = """search_text LIKE '%' || ? || '%' ESCAPE '\'"""

/**
 * Builds the statement for [PsgcBarangayDao.search]: one `LIKE` per [terms] entry, ANDed,
 * ranked by how closely the row's `name` column matches the joined [nameTerms] (5-tier
 * CASE).
 *
 * A file-private function rather than a DAO member because Room only processes annotated
 * methods, and an interface cannot hold a private companion.
 */
private fun psgcSearchQuery(
    terms: List<String>,
    nameTerms: List<String>,
    limit: Int,
): SupportSQLiteQuery {
    val where = terms.joinToString(separator = " AND ") { TERM_PREDICATE }
    // Tier 3: every individual nameTerm must appear in name. Generates one LIKE predicate
    // per term so the WHEN clause works for any number of search words.
    val tier3When = nameTerms.joinToString(separator = " AND ") {
        """lower(name) LIKE '%' || ? || '%' ESCAPE '\'"""
    }
    val joinedName = nameTerms.joinToString(" ")
    // Bind-argument order must mirror the positional ? in the SQL string:
    //   1. WHERE-clause: one bind per term
    //   2. ORDER BY tier 0 (exact), tier 1 (starts-with), tier 2 (contains): joinedName x3
    //   3. ORDER BY tier 3: one bind per nameTerm again
    //   4. LIMIT
    val args = buildList<Any> {
        addAll(terms)       // WHERE
        add(joinedName)     // tier 0
        add(joinedName)     // tier 1
        add(joinedName)     // tier 2
        addAll(nameTerms)   // tier 3
        add(limit)
    }
    return SimpleSQLiteQuery(
        """
        SELECT * FROM psgc_barangays
        WHERE $where
        ORDER BY
            CASE
                WHEN lower(name) = ? THEN 0
                WHEN lower(name) LIKE ? || '%' ESCAPE '\' THEN 1
                WHEN lower(name) LIKE '%' || ? || '%' ESCAPE '\' THEN 2
                WHEN $tier3When THEN 3
                ELSE 4
            END,
            name
        LIMIT ?
        """.trimIndent(),
        args.toTypedArray(),
    )
}
