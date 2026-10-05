package com.agarthavision.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.agarthavision.data.local.entity.ColleagueEntity
import com.google.gson.JsonParser
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
import java.io.File

/**
 * Room 23 → 24, the `colleagues` name cache (14zcqntjph6), run on a real file.
 *
 * Built the same way as [Migration22To23Test]: a version 23 database from the committed
 * `23.json`, a phone's worth of rows in it, then Room opens it at version 24. **Room validates
 * the migrated schema against the entities on open**, so a clean open proves the new table
 * matches `ColleagueEntity`. The assertions after it check that nothing already there moved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration23To24Test {

    private lateinit var context: Context
    private val dbName = "migration-23-24.db"

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
    fun `the 23 json this test builds from is where it expects`() {
        assertTrue(schemaFile(23).isFile)
    }

    @Test
    fun `migrating adds an empty colleagues table and keeps every sample`() = runTest {
        createVersion23 { db ->
            db.execSQL(
                "INSERT INTO patients (patient_id, lastname, firstname, sex, birthdate, " +
                    "psgc_barangay_code, created_by, created_at, updated_at) " +
                    "VALUES ('p1', 'Cruz', 'Gerald', 'M', 0, '0102801001', 'u1', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO sessions (session_id, user_id, patient_id, device_id, started_at) " +
                    "VALUES ('s1', 'u1', 'p1', 'd1', 1)",
            )
            // A queued frame: the kind of row that exists nowhere but this phone.
            db.execSQL(
                "INSERT INTO samples (sample_id, session_id, user_id, device_id, timestamp, " +
                    "image_path, status, inference_state) " +
                    "VALUES ('queued', 's1', 'u1', 'd1', 1, '/tmp/queued.jpg', 'flagged', 'queued')",
            )
        }

        val room = Room.databaseBuilder(context, AgarthaDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val queued = room.sampleDao().getSampleById("queued")
            assertEquals("queued", queued?.inferenceState)

            val colleagues = room.colleagueDao()
            assertNull(colleagues.getFullName("u2"))
            colleagues.upsertColleagues(listOf(ColleagueEntity(userId = "u2", fullName = null)))
            // A profile with no name set is legal on the server, so it is legal here.
            assertNull(colleagues.getFullName("u2"))
            colleagues.upsertColleagues(listOf(ColleagueEntity(userId = "u2", fullName = "Maria Santos")))
            assertEquals("Maria Santos", colleagues.getFullName("u2"))
        } finally {
            room.close()
        }
    }

    /** The database file exactly as version 23 of the app would have left it. */
    private fun createVersion23(seed: (SQLiteDatabase) -> Unit) {
        val schema = JsonParser.parseString(schemaFile(23).readText()).asJsonObject
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
            db.version = 23
            seed(db)
        }
    }

    private fun schemaFile(version: Int) =
        File("schemas/com.agarthavision.core.database.AgarthaDatabase/$version.json")

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
