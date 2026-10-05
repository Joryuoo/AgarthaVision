package com.agarthavision.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `MIGRATION_24_25` (14zcqntj2uz, `0015_patient_reports.sql`): `reports.session_id` becomes
 * nullable and `patient_id`/`session_ids_json` are added, rebuilding the table since SQLite
 * cannot drop a NOT NULL constraint with `ALTER TABLE`.
 *
 * Mirrors [Migration22To23Test]'s approach: build a version 24 database from the committed
 * `24.json`, seed it the way a medtech's phone would have, then let Room open it at version 25.
 * Room validates the migrated schema against the entities on open, so a clean open is itself
 * the schema check; the assertions after it check the data survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration24To25Test {

    private lateinit var context: Context
    private val dbName = "migration-24-25.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `the 24 json this test builds from is where it expects`() {
        assertTrue(schemaFile(24).isFile)
    }

    @Test
    fun `migrating keeps an existing session report with null patient_id and session_ids_json`() = runTest {
        createVersion24 { db ->
            db.execSQL(
                "INSERT INTO patients (patient_id, lastname, firstname, sex, birthdate, " +
                    "psgc_barangay_code, created_by, created_at, updated_at) " +
                    "VALUES ('p1', 'Cruz', 'Gerald', 'M', 0, '0102801001', 'u1', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO sessions (session_id, user_id, patient_id, device_id, started_at) " +
                    "VALUES ('s1', 'u1', 'p1', 'd1', 1)",
            )
            db.execSQL(
                "INSERT INTO reports (report_id, session_id, user_id, report_type, generated_at, " +
                    "total_samples, total_eggs_confirmed, positive_species_json, lpf_per_species_json, " +
                    "csv_file_path, pdf_file_path, supabase_status, created_at) " +
                    "VALUES ('r1', 's1', 'u1', 'session', 1, 3, 1, '[]', '{}', NULL, '/tmp/r1.pdf', " +
                    "'pending', 1)",
            )
        }

        val room = Room.databaseBuilder(context, AgarthaDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val report = room.reportDao().getReportById("r1")!!

            assertEquals("s1", report.sessionId)
            assertNull(report.patientId)
            assertNull(report.sessionIdsJson)
            assertEquals("/tmp/r1.pdf", report.pdfFilePath)
            assertEquals(3, report.totalSamples)
        } finally {
            room.close()
        }
    }

    @Test
    fun `migrating drops the NOT NULL constraint on session_id, allowing a patient-scoped report`() = runTest {
        createVersion24 { db ->
            db.execSQL(
                "INSERT INTO patients (patient_id, lastname, firstname, sex, birthdate, " +
                    "psgc_barangay_code, created_by, created_at, updated_at) " +
                    "VALUES ('p1', 'Cruz', 'Gerald', 'M', 0, '0102801001', 'u1', 1, 1)",
            )
        }

        val room = Room.databaseBuilder(context, AgarthaDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            room.reportDao().insertReport(
                com.agarthavision.data.local.entity.ReportEntity(
                    reportId = "r2",
                    sessionId = null,
                    patientId = "p1",
                    sessionIdsJson = """["s1","s2"]""",
                    userId = "u1",
                    reportType = "patient",
                    generatedAt = 2,
                    totalSamples = 5,
                    totalEggsConfirmed = 2,
                    positiveSpeciesJson = "[]",
                    lpfPerSpeciesJson = "{}",
                    csvFilePath = null,
                    pdfFilePath = "/tmp/r2.pdf",
                    supabaseStatus = "pending",
                    createdAt = 2,
                ),
            )

            val report = room.reportDao().getReportById("r2")!!

            assertNull(report.sessionId)
            assertEquals("p1", report.patientId)
            assertEquals("""["s1","s2"]""", report.sessionIdsJson)
        } finally {
            room.close()
        }
    }

    /**
     * Creates the database file exactly as version 24 of the app would have left it: every
     * table and index from the committed `24.json`, Room's identity row, and `user_version` 24.
     */
    private fun createVersion24(seed: (SQLiteDatabase) -> Unit) {
        val schema = JsonParser.parseString(schemaFile(24).readText()).asJsonObject
            .getAsJsonObject("database")
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getAsJsonArray("entities").forEach { element ->
                val entity = element.asJsonObject
                val table = entity["tableName"].asString
                db.execSQL(entity["createSql"].asString.replace(TABLE_NAME, table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    db.execSQL(index.asJsonObject["createSql"].asString.replace(TABLE_NAME, table))
                }
            }
            schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            db.version = 24
            seed(db)
        }
    }

    private fun schemaFile(version: Int) =
        File("schemas/com.agarthavision.core.database.AgarthaDatabase/$version.json")

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
