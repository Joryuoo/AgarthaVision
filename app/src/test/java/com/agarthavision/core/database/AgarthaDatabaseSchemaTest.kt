package com.agarthavision.core.database

import android.content.Context
import androidx.room.Room
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
 * Pins the Room schema version and the shape the PSGC work added to it.
 *
 * This exists because staging and the barangay branch each bumped the database 8 → 9
 * independently — staging for `reports.pdf_file_path`, this branch for `psgc_barangays` and
 * `sessions.psgc_barangay_code`. Merged, the code declared version 9 with a schema that was
 * neither side's v9. `fallbackToDestructiveMigration` does not cover that: destructive
 * fallback only fires on a version *change*, so at an equal version with a different identity
 * hash Room throws `IllegalStateException: Room cannot verify the data integrity` when it
 * opens the file, and every device carrying the other build crashes at launch.
 *
 * A version bump is cheap and a collision is not, so this test fails loudly when the declared
 * version and the exported schema history disagree about what v[EXPECTED_VERSION] contains.
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
    fun `both halves of the merged schema are present`() {
        val tables = database.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }

        // The branch's half.
        assertTrue("psgc_barangays is missing from v$EXPECTED_VERSION", tables.contains("psgc_barangays"))
        // Staging's half — if this goes missing the merge dropped a side.
        assertTrue("reports is missing from v$EXPECTED_VERSION", tables.contains("reports"))

        assertTrue(
            "sessions.psgc_barangay_code is missing — the picker would write to nothing.",
            columnsOf("sessions").contains("psgc_barangay_code"),
        )
        assertTrue(
            "reports.pdf_file_path is missing — staging's v9 was lost in the merge.",
            columnsOf("reports").contains("pdf_file_path"),
        )
    }

    @Test
    fun `the barangay code is nullable so sessions predating the picker survive`() {
        // Nothing backfills older rows, and the destructive migration means a device may hold
        // sessions created before the column existed. A NOT NULL here would be unrecoverable.
        val notNull = database.openHelper.writableDatabase
            .query("PRAGMA table_info(sessions)")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                var result = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "psgc_barangay_code") {
                        result = cursor.getInt(notNullIndex) == 1
                    }
                }
                result
            }
        assertEquals(false, notNull)
    }

    private fun columnsOf(table: String): List<String> =
        database.openHelper.writableDatabase
            .query("PRAGMA table_info($table)")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
            }

    private companion object {
        /** Keep in step with `AgarthaDatabase.version` and `app/schemas/…/<n>.json`. */
        private const val EXPECTED_VERSION = 10
    }
}
