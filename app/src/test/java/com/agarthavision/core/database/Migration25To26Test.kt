package com.agarthavision.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.agarthavision.data.local.entity.ColleagueEntity
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
 * `MIGRATION_25_26` (14zcqntx3v6, `0019_require_profile_full_name.sql`): `colleagues.full_name`
 * becomes NOT NULL, backfilling any existing null rows to 'Medtech'.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration25To26Test {

    private lateinit var context: Context
    private val dbName = "migration-25-26.db"

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
    fun `the 25 json this test builds from is where it expects`() {
        assertTrue(schemaFile(25).isFile)
    }

    @Test
    fun `migrating backfills null full_name to Medtech and preserves existing names`() = runTest {
        createVersion25 { db ->
            db.execSQL("INSERT INTO colleagues (user_id, full_name) VALUES ('u1', 'Maria Santos')")
            db.execSQL("INSERT INTO colleagues (user_id, full_name) VALUES ('u2', NULL)")
        }

        val room = Room.databaseBuilder(context, AgarthaDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val colleagues = room.colleagueDao()
            assertEquals("Maria Santos", colleagues.getFullName("u1"))
            assertEquals("Pending Profile Setup", colleagues.getFullName("u2"))

            colleagues.upsertColleagues(listOf(ColleagueEntity(userId = "u3", fullName = "Juan dela Cruz")))
            assertEquals("Juan dela Cruz", colleagues.getFullName("u3"))
        } finally {
            room.close()
        }
    }

    private fun createVersion25(seed: (SQLiteDatabase) -> Unit) {
        val schema = JsonParser.parseString(schemaFile(25).readText()).asJsonObject
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
            db.version = 25
            seed(db)
        }
    }

    private fun schemaFile(version: Int) =
        File("schemas/com.agarthavision.core.database.AgarthaDatabase/$version.json")

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}

