package com.agarthavision.data.local.species

import android.util.Log
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.data.local.entity.SpeciesSuggestionEntity
import com.agarthavision.domain.model.EggSpecies
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Keeps the offline species suggestion index populated.
 *
 * Follows [com.agarthavision.data.local.psgc.PsgcSeeder]: seed if empty, refresh when the
 * source changes, never throw. A missing suggestion list must degrade to plain free text —
 * losing a verified frame because an autocomplete failed would be a far worse bug than the
 * inconsistent spellings this exists to prevent.
 *
 * **Gated on the table being empty, not on a recorded marker.** `PsgcSeeder`'s doc explains
 * why, and the same hazard applies here with more force: PB-03 bumped Room to 13 and
 * `fallbackToDestructiveMigration(dropAllTables = true)` is in force, so the version bump
 * wipes this table. A "seeded" flag with no emptiness check would leave the index
 * permanently blank on every device that upgraded.
 */
class SpeciesSuggestionSeeder @Inject constructor(
    private val dao: SpeciesSuggestionDao,
) {
    /**
     * Seeds the canonical [EggSpecies] names when the table is empty, so a fresh install is
     * never blank. Returns rows written, or 0 when the index already holds something.
     */
    suspend fun seedIfNeeded(): Int = guarded("seeding") {
        if (dao.count() > 0) return@guarded 0
        val seeds = EggSpecies.entries.mapNotNull { it.canonicalClass }
        dao.insertAll(seeds.toEntities())
        seeds.size
    }

    /**
     * Folds every species already on the device into the index.
     *
     * Called after a successful fetch pass (PB-08a) so the two reference caches stay in
     * step, and additive by design: a name entered on another device arrives with the rows
     * that carry it, and a name entered here survives until those rows are deleted.
     *
     * Re-seeds the canonical names too, which is what makes this safe to call on a table a
     * destructive migration has just emptied.
     */
    suspend fun refresh(): Int = guarded("refresh") {
        val canonical = EggSpecies.entries.mapNotNull { it.canonicalClass }
        val observed = dao.observedSpecies()
        val all = (canonical + observed)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        dao.insertAll(all.toEntities())
        all.size
    }

    private fun List<String>.toEntities(): List<SpeciesSuggestionEntity> = map {
        // Lowercased in Kotlin rather than by SQL lower(), which folds ASCII only.
        SpeciesSuggestionEntity(species = it, lookup = it.lowercase())
    }

    /**
     * Swallows failures so a broken index cannot block a submission, while letting a
     * cancelled application scope actually cancel rather than be logged as a failure.
     */
    private suspend fun guarded(what: String, block: suspend () -> Int): Int =
        runCatching { block() }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "Species suggestion $what failed; the field falls back to free text.", throwable)
            0
        }

    private companion object {
        const val TAG = "SpeciesSuggestionSeeder"
    }
}
