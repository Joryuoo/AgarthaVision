package com.agarthavision.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.agarthavision.data.local.mapper.effectiveInferenceState
import com.agarthavision.domain.inference.InferenceState
import com.google.gson.JsonParser
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
import java.io.File

/**
 * The project's first hand-written migration, 22 → 23 (14zcqntj6ny), run on a real file.
 *
 * Builds a version 22 database from the committed `22.json`, writes the rows a medtech's phone
 * would hold, then lets Room open it at version 23. **Room validates the migrated schema against
 * the entities on open** and throws if a column's type, nullability or default is off, so a
 * clean open is itself the schema check. The assertions after it check the data survived.
 *
 * Deliberately not `MigrationTestHelper`: that needs the schema JSON packaged as test assets.
 * Reading `22.json` straight from `app/schemas/` tests the file that is actually committed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration22To23Test {

    private lateinit var context: Context
    private val dbName = "migration-22-23.db"

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
    fun `the 22 json this test builds from is where it expects`() {
        // Guards the guard: a moved schema folder would otherwise fail every test below with a
        // confusing file error rather than naming the cause.
        assertTrue(schemaFile(22).isFile)
    }

    @Test
    fun `migrating keeps every sample and gives each the right inference state`() = runTest {
        createVersion22 { db ->
            db.execSQL(
                "INSERT INTO patients (patient_id, lastname, firstname, sex, birthdate, " +
                    "psgc_barangay_code, created_by, created_at, updated_at) " +
                    "VALUES ('p1', 'Cruz', 'Gerald', 'M', 0, '0102801001', 'u1', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO sessions (session_id, user_id, patient_id, device_id, started_at) " +
                    "VALUES ('s1', 'u1', 'p1', 'd1', 1)",
            )
            insertSample(db, id = "model-flagged", status = "flagged", isManual = false)
            insertSample(db, id = "manual-flagged", status = "flagged", isManual = true)
            insertSample(db, id = "verified", status = "verified", isManual = false)
        }

        val room = Room.databaseBuilder(context, AgarthaDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = room.sampleDao()

            val model = dao.getSampleById("model-flagged")!!
            val manual = dao.getSampleById("manual-flagged")!!
            val verified = dao.getSampleById("verified")!!

            // Captured before the queue existed, so capture had already waited for the model.
            assertEquals("ready", model.inferenceState)
            assertEquals(InferenceState.READY, model.effectiveInferenceState())
            assertEquals("manual", manual.inferenceState)
            assertEquals(InferenceState.MANUAL, manual.effectiveInferenceState())
            assertEquals(InferenceState.READY, verified.effectiveInferenceState())
            assertEquals(0, model.inferenceAttempts)

            // Nothing that existed before is queued: the queue starts empty after an update.
            assertTrue(dao.getQueuedInferenceSampleIds().isEmpty())
            // And nothing was wiped, which is what a destructive fallback would have done.
            assertEquals("verified", verified.status)
            assertEquals("session-note", verified.userNote)
        } finally {
            room.close()
        }
    }

    private fun insertSample(db: SQLiteDatabase, id: String, status: String, isManual: Boolean) {
        db.execSQL(
            "INSERT INTO samples (sample_id, session_id, user_id, device_id, timestamp, " +
                "image_path, status, is_manual, user_note) VALUES (?, 's1', 'u1', 'd1', 1, ?, ?, ?, ?)",
            arrayOf<Any>(id, "/tmp/$id.jpg", status, if (isManual) 1 else 0, "session-note"),
        )
    }

    /**
     * Creates the database file exactly as version 22 of the app would have left it: every table
     * and index from the committed `22.json`, Room's identity row, and `user_version` 22.
     */
    private fun createVersion22(seed: (SQLiteDatabase) -> Unit) {
        val schema = JsonParser.parseString(schemaFile(22).readText()).asJsonObject
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
            db.version = 22
            seed(db)
        }
    }

    private fun schemaFile(version: Int) =
        File("schemas/com.agarthavision.core.database.AgarthaDatabase/$version.json")

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
