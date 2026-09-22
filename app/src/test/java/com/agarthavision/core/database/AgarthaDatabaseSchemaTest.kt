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
 * Pins the Room schema version and the shape every merged branch added to it.
 *
 * This exists because the collision has already happened once. Staging and the barangay branch
 * each bumped the database 8 -> 9 independently, and merged, the code declared version 9 with a
 * schema that was neither side's v9. `fallbackToDestructiveMigration` does not cover that:
 * destructive fallback only fires on a version *change*, so at an equal version with a different
 * identity hash Room throws `IllegalStateException: Room cannot verify the data integrity` when
 * it opens the file, and every device carrying the other build crashes at launch.
 *
 * It then nearly happened a second time. Three branches declared version 10 at once —
 * `feat/sample-geospatial-mapping` (`psgc_barangays`), `feature/editable-report`
 * (`detections.stage`) and this one. The barangay branch kept 10, the stage work was reverted on
 * staging (86d4a6jwy, deprioritised), and this branch skipped to 12 rather than taking the next
 * free-looking number. A version bump is cheap and a collision is not.
 *
 * So the assertions below are deliberately a **union across branches**, not this branch's half:
 * each one names the merge that would have dropped it.
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
    fun `a finding stage is nullable because nothing writes one yet`() {
        // The column mirrors `0012_polyparasitism_findings.sql`, which is applied and frozen
        // under C6 — but 86d4a6jwy was reverted on staging and deprioritised, so no code path
        // sets it. Nullable is what keeps that dormant rather than broken. See Finding.md.
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
    fun `detections carry no stage column`() {
        // Not an omission. Staging reverted 86d4a6jwy (9dcfd5d) because the four stages shipped
        // there were never checked against literature — Ascaris could only be tagged
        // UNFERTILIZED, the one stage that is never infective. This branch cherry-picked that
        // commit before the revert existed, so this merge is the exact place it could come back
        // by accident. It must not.
        assertFalse(columnsOf("detections").contains("stage"))
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
    fun `patients and the visibility join exist`() {
        assertTrue(
            "patients is missing from v$EXPECTED_VERSION — a session has nothing to belong to.",
            tables().contains("patients"),
        )
        assertTrue(
            "patient_users is missing — patient visibility resolves through it, so without " +
                "it a medtech can read no patient at all.",
            tables().contains("patient_users"),
        )
        listOf(
            "lastname", "firstname", "middle_name", "sex", "birthdate",
            "psgc_barangay_code", "created_by", "created_at", "updated_at",
        ).forEach { column ->
            assertTrue("patients.$column is missing.", columnsOf("patients").contains(column))
        }
        // Only the middle name is optional — the rest identify the patient.
        assertFalse(isNotNull("patients", "middle_name"))
        assertTrue(isNotNull("patients", "lastname"))
        assertTrue(isNotNull("patients", "birthdate"))
        assertTrue(isNotNull("patients", "psgc_barangay_code"))
    }

    @Test
    fun `a session belongs to a patient`() {
        assertTrue(
            "sessions.patient_id is missing — sessions would still hang off the user alone.",
            columnsOf("sessions").contains("patient_id"),
        )
        // Not null: a session is always created from a patient's session list, so the
        // patient is known at creation. A nullable column here would let an orphan smear
        // exist and quietly drop out of every per-patient report.
        assertTrue(isNotNull("sessions", "patient_id"))
    }

    @Test
    fun `sessions no longer carry notes, ended_at, psgc_barangay_code or claim_exempt`() {
        // Each removal has its own reason and each would be easy to restore by reflex:
        //  - ended_at: sessions never end (86d4ab4vm), so nothing wrote it and
        //    `ended_at IS NULL` silently matched every row while still reading as a filter.
        //  - notes: it was doubling as an ad-hoc patient identifier. Patient replaces it.
        //  - psgc_barangay_code: moved to the patient, the unit surveillance aggregates on.
        //  - claim_exempt: login is mandatory on first run, so every row has an owner.
        listOf("notes", "ended_at", "psgc_barangay_code", "claim_exempt").forEach { column ->
            assertFalse(
                "sessions.$column should be gone at v$EXPECTED_VERSION.",
                columnsOf("sessions").contains(column),
            )
        }
    }

    @Test
    fun `samples no longer carry a GPS fix`() {
        // The fix was taken at the microscope, so it recorded where the smear was read, not
        // where the infection came from. Mapping keys on the patient's barangay now.
        listOf("gps_latitude", "gps_longitude", "gps_accuracy").forEach { column ->
            assertFalse(
                "samples.$column should be gone at v$EXPECTED_VERSION.",
                columnsOf("samples").contains(column),
            )
        }
    }

    @Test
    fun `reports no longer carry an EPG figure`() {
        // EPG is eggs-per-gram via Kato-Katz; these smears are direct smears, so the x24
        // multiplier was wrong for the method in use. A stale EPG surviving into a generated
        // report is a clinical error, not a cosmetic one.
        assertFalse(
            "reports.epg_per_species_json should be gone at v$EXPECTED_VERSION.",
            columnsOf("reports").contains("epg_per_species_json"),
        )
    }

    @Test
    fun `the barangay reference table survived the merge`() {
        assertTrue(
            "psgc_barangays is missing from v$EXPECTED_VERSION — the picker has no data.",
            tables().contains("psgc_barangays"),
        )
    }

    // There is deliberately no assertion that `sessions` carries a barangay code. Two tests
    // here used to make one, contradicting `sessions no longer carry notes, ended_at,
    // psgc_barangay_code or claim_exempt` a few cases above. Room 13 moved the code to the
    // patient — it is the unit surveillance aggregates on and it does not change from one
    // smear to the next — and `patients and the visibility join exist` pins it there.

    @Test
    fun `the tables the earlier versions added are still present`() {
        // If one of these goes missing, a merge dropped a side.
        val tables = tables()
        listOf(
            "samples", "sessions", "detections", "reports",
            // v13 and v14's own additions, so a later merge cannot quietly drop them either.
            "patients", "patient_users", "species_suggestions",
        ).forEach { table ->
            assertTrue("$table is missing from v$EXPECTED_VERSION", tables.contains(table))
        }
        assertTrue(
            "reports.pdf_file_path is missing — staging's v9 was lost in the merge.",
            columnsOf("reports").contains("pdf_file_path"),
        )
        assertTrue(
            "reports.lpf_per_species_json is missing — ticket 86d4a6jxw replacement of EPG failed.",
            columnsOf("reports").contains("lpf_per_species_json"),
        )
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

    @Test
    fun `patients carries an index on updated_at for recent activity sort`() {
        val indices = database.openHelper.writableDatabase
            .query("PRAGMA index_list('patients')")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
            }
        val indexedColumns = indices.flatMap { indexName ->
            database.openHelper.writableDatabase
                .query("PRAGMA index_info('$indexName')")
                .use { cursor ->
                    val colIndex = cursor.getColumnIndexOrThrow("name")
                    buildList { while (cursor.moveToNext()) add(cursor.getString(colIndex)) }
                }
        }
        assertTrue(
            "patients.updated_at is not indexed at v$EXPECTED_VERSION — recent-activity sort would scan.",
            indexedColumns.contains("updated_at"),
        )
    }

    @Test
    fun `sessions carry a unique per-patient label index`() {
        // v17 (86d4bzjhw): per-patient label uniqueness is enforced at the SQLite level.
        // Without this index two concurrent offline creates with the same label collide
        // silently into a duplicate row pair that confuses the medtech's list.
        val indexNames = database.openHelper.writableDatabase
            .query("PRAGMA index_list('sessions')")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
            }
        val uniqueIndex = indexNames.firstOrNull { name ->
            val columns = database.openHelper.writableDatabase
                .query("PRAGMA index_info($name)")
                .use { cursor ->
                    val colIndex = cursor.getColumnIndexOrThrow("name")
                    buildList { while (cursor.moveToNext()) add(cursor.getString(colIndex)) }
                }
            columns.containsAll(listOf("patient_id", "label")) && columns.size == 2
        }
        // Confirm the index is actually unique.
        val isUnique = uniqueIndex != null && database.openHelper.writableDatabase
            .query("PRAGMA index_list('sessions')")
            .use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val uniqueIndex2 = cursor.getColumnIndexOrThrow("unique")
                var result = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == uniqueIndex) {
                        result = cursor.getInt(uniqueIndex2) == 1
                    }
                }
                result
            }
        assertTrue(
            "sessions(patient_id, label) unique index is missing — duplicate labels for the " +
                "same patient can slip through on concurrent offline creates.",
            isUnique,
        )
    }

    private companion object {
        /** Keep in step with `AgarthaDatabase.version` and `app/schemas/…/<n>.json`. */
        private const val EXPECTED_VERSION = 18
    }
}
