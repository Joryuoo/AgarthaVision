package com.agarthavision.core.database

import android.content.Context
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the Room schema version and the shape this branch added to it.
 *
 * This exists because the same collision has already happened once. Staging and the barangay
 * branch each bumped the database 8 → 9 independently, and merged, the code declared version 9
 * with a schema that was neither side's v9. `fallbackToDestructiveMigration` does not cover
 * that: destructive fallback only fires on a version *change*, so at an equal version with a
 * different identity hash Room throws `IllegalStateException: Room cannot verify the data
 * integrity` when it opens the file, and every device carrying the other build crashes at
 * launch.
 *
 * Two branches were in flight declaring version 10 when this one was cut
 * (`feat/sample-geospatial-mapping` and `feature/editable-report`), which is why this branch
 * skips to 12 rather than taking the next free-looking number. A version bump is cheap and a
 * collision is not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgarthaDatabaseSchemaTest {

    private lateinit var database: AgarthaDatabase

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room
            .inMemoryDatabaseBuilder(context, AgarthaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the database opens at the declared version`() {
        assertEquals(EXPECTED_VERSION, database.openHelper.writableDatabase.version)
    }

    @Test
    fun `the polyparasitism findings table is present`() {
        assertTrue(
            "sample_species_findings is missing from v$EXPECTED_VERSION — a mixed-species " +
                "field would have nowhere to be recorded.",
            tables().contains("sample_species_findings"),
        )
        val columns = columnsOf("sample_species_findings")
        listOf("finding_id", "sample_id", "species", "stage", "egg_count").forEach { column ->
            assertTrue("sample_species_findings.$column is missing", columns.contains(column))
        }
    }

    @Test
    fun `a finding stage is nullable because not every species defines one`() {
        // EggStage.validFor returns an empty list for EggSpecies.OTHER. A NOT NULL here would
        // make a free-text species impossible to count.
        assertFalse(isNotNull("sample_species_findings", "stage"))
    }

    @Test
    fun `the species provenance flag is present and is not verified_by_user`() {
        val columns = columnsOf("detections")
        assertTrue(
            "detections.species_touched is missing — an untouched model pre-fill would be " +
                "indistinguishable from a deliberate human confirmation in the training corpus.",
            columns.contains("species_touched"),
        )
        // Deliberately a new column rather than reusing the dead one. If verified_by_user has
        // gone, ticket 86d4akgmf landed and this assertion is the one to delete.
        assertTrue(columns.contains("verified_by_user"))
    }

    @Test
    fun `samples carry a nullable tombstone`() {
        assertTrue(
            "samples.deleted_at is missing — a verified duplicate could not be removed " +
                "without breaching C8.",
            columnsOf("samples").contains("deleted_at"),
        )
        // Nullable: null means live, and almost every row is live.
        assertFalse(isNotNull("samples", "deleted_at"))
    }

    @Test
    fun `samples no longer carry is_repeat`() {
        // The flag existed only because there was no way to delete a duplicate. deleted_at
        // provides that, so the workaround went with it (86d4ab4vm).
        assertFalse(
            "samples.is_repeat should be gone - duplicates are deleted now, not flagged.",
            columnsOf("samples").contains("is_repeat"),
        )
    }

    @Test
    fun `the tables the earlier versions added are still present`() {
        // If one of these goes missing, a merge dropped a side.
        val tables = tables()
        listOf("samples", "sessions", "detections", "reports").forEach { table ->
            assertTrue("$table is missing from v$EXPECTED_VERSION", tables.contains(table))
        }
        assertTrue(columnsOf("reports").contains("pdf_file_path"))
        assertTrue(columnsOf("detections").contains("stage"))
    }

    private fun tables(): List<String> =
        database.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }

    private fun columnsOf(table: String): List<String> =
        database.openHelper.writableDatabase
            .query("PRAGMA table_info($table)")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
            }

    private fun isNotNull(table: String, column: String): Boolean =
        database.openHelper.writableDatabase
            .query("PRAGMA table_info($table)")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                var result = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) {
                        result = cursor.getInt(notNullIndex) == 1
                    }
                }
                result
            }

    private companion object {
        /** Keep in step with `AgarthaDatabase.version` and `app/schemas/…/<n>.json`. */
        private const val EXPECTED_VERSION = 12
    }
}
