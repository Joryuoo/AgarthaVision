package com.agarthavision.data.local.species

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.usecase.verify.SearchSpeciesSuggestionsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In-memory Room tests for the offline species suggestion index.
 *
 * The destructive-migration case is the one that matters. `PsgcSeeder`'s doc explains why a
 * recorded marker alone is not enough, and the same applies here: every Room version bump
 * wipes this table under `fallbackToDestructiveMigration(dropAllTables = true)`, so a
 * "seeded" flag with no emptiness check would leave the index permanently blank on every
 * upgraded device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeciesSuggestionSeederTest {

    private lateinit var db: AgarthaDatabase
    private lateinit var dao: SpeciesSuggestionDao
    private lateinit var seeder: SpeciesSuggestionSeeder
    private lateinit var search: SearchSpeciesSuggestionsUseCase

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.speciesSuggestionDao()
        seeder = SpeciesSuggestionSeeder(dao)
        search = SearchSpeciesSuggestionsUseCase(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val canonicalCount = EggSpecies.entries.count { it.canonicalClass != null }

    @Test
    fun `a fresh install is seeded with the canonical species rather than left empty`() = runTest {
        assertEquals(canonicalCount, seeder.seedIfNeeded())
        assertEquals(canonicalCount, dao.count())
    }

    @Test
    fun `seeding is a no-op once the table holds something`() = runTest {
        seeder.seedIfNeeded()

        assertEquals(0, seeder.seedIfNeeded())
        assertEquals(canonicalCount, dao.count())
    }

    @Test
    fun `an emptied table re-seeds rather than trusting a stale marker`() = runTest {
        seeder.seedIfNeeded()

        // What a destructive migration leaves behind at the next version bump.
        db.clearAllTables()

        assertEquals(canonicalCount, seeder.seedIfNeeded())
        assertTrue(dao.count() > 0)
    }

    @Test
    fun `refresh re-seeds the canonical names on an empty table`() = runTest {
        assertTrue(seeder.refresh() >= canonicalCount)
        assertTrue(dao.count() >= canonicalCount)
    }

    @Test
    fun `refresh is idempotent - the same name does not duplicate`() = runTest {
        seeder.refresh()
        val afterFirst = dao.count()

        seeder.refresh()

        // species is the primary key, so a name seen twice collapses to one row.
        assertEquals(afterFirst, dao.count())
    }

    // ── the search ────────────────────────────────────────────────────────────

    @Test
    fun `a prefix match is case-insensitive`() = runTest {
        seeder.seedIfNeeded()

        val lower = search("asc").getOrThrow()
        val upper = search("ASC").getOrThrow()

        assertEquals(listOf("Ascaris lumbricoides"), lower)
        assertEquals(lower, upper)
    }

    @Test
    fun `a query shorter than the minimum returns empty rather than failing`() = runTest {
        seeder.seedIfNeeded()

        val result = search("a")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `a prefix that matches nothing returns empty`() = runTest {
        seeder.seedIfNeeded()

        assertTrue(search("zzz").getOrThrow().isEmpty())
    }

    @Test
    fun `it is a prefix match, not a contains match`() = runTest {
        seeder.seedIfNeeded()

        // "lumbricoides" is inside "Ascaris lumbricoides" but does not begin it. The point
        // is to complete what is being typed, not to surface everything containing it.
        assertTrue(search("lumbri").getOrThrow().isEmpty())
    }

    @Test
    fun `searching an empty index degrades to no suggestions rather than an error`() = runTest {
        val result = search("asc")

        // A missing index must fall back to plain free text; losing a verified frame to a
        // failed autocomplete would be far worse than an inconsistent spelling.
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }
}
