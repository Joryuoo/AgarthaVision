package com.agarthavision.data.local.psgc

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PsgcBarangayDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Copies the bundled PSGC barangay dataset into Room.
 *
 * Called once per launch from [com.agarthavision.AgarthaVisionApp]. The dataset ships in the
 * APK and is never fetched at point of use — medtechs collect in areas with no cellular
 * signal, so the picker has to work with the radio off.
 *
 * Re-entrant, and deliberately gated on **two** conditions:
 *
 * - The table being empty. `fallbackToDestructiveMigration(dropAllTables = true)` is in force
 *   (`core/di/DatabaseModule.kt`), so any Room version bump wipes this table; the recorded
 *   vintage alone would wrongly report it as already seeded.
 * - The recorded vintage differing from [PsgcDataset.VINTAGE]. This is what makes changing
 *   the pinned PSGC release a dataset swap plus a constant, with no migration.
 *
 * **The asset is a SQLite file, not a CSV, and this class no longer parses anything.** It
 * used to gunzip a CSV, build 42,010 entities in Kotlin and insert them in chunks, which cost
 * roughly a minute on a Redmi Note 11 and made the app feel unresponsive for the whole of a
 * first launch. The rows now move in one `INSERT ... SELECT` over an attached database.
 *
 * The asset carries no `room_master_table` and is never opened as a Room database, which is
 * what keeps it independent of Room's `identityHash`: bumping the schema version does not
 * mean regenerating it. It cannot be a second Room database either — `PatientDao` joins
 * `psgc_barangays` against `patients` for the patient search, and SQLite cannot join across
 * connections. Hence copying into the main database rather than reading the asset in place.
 */
class PsgcSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AgarthaDatabase,
    private val barangayDao: PsgcBarangayDao,
    private val settings: DataStore<Preferences>,
) {
    /**
     * Seeds when needed and returns the number of rows written, or `0` when the device
     * already holds this vintage.
     *
     * Never throws: a failure here must not stop the app from launching, because capture
     * and verification do not depend on the picker. An unseeded table shows an empty
     * barangay list, and the next launch retries.
     */
    suspend fun seedIfNeeded(): Int {
        return runCatching {
            if (!needsSeed()) return 0
            val inserted = replaceAll()
            settings.edit { it[SEEDED_VINTAGE] = PsgcDataset.VINTAGE }
            inserted
        }.getOrElse { throwable ->
            // Seeding runs in the application scope; if that scope is cancelled the work
            // should stop, not be logged as a seeding failure and swallowed.
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "PSGC seeding failed; the barangay picker will be empty.", throwable)
            0
        }
    }

    private suspend fun needsSeed(): Boolean {
        val seededVintage = settings.data.first()[SEEDED_VINTAGE]
        return seededVintage != PsgcDataset.VINTAGE || barangayDao.count() == 0
    }

    /**
     * Replaces the table from the bundled database, and returns the row count that landed.
     *
     * `ATTACH` is issued outside any transaction because SQLite rejects it inside one, which
     * is why this does not use `database.withTransaction` the way the rest of the data layer
     * does. The replacement itself still gets a transaction — an interrupted copy must not
     * leave the picker holding half a country — it is just opened around the two statements
     * rather than around the attach.
     */
    private suspend fun replaceAll(): Int {
        val staged = stageAsset()
        try {
            val db = database.openHelper.writableDatabase
            db.execSQL("ATTACH DATABASE ? AS $SOURCE_SCHEMA", arrayOf(staged.absolutePath))
            try {
                db.beginTransaction()
                try {
                    db.execSQL("DELETE FROM $TABLE")
                    db.execSQL(COPY_SQL)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                db.execSQL("DETACH DATABASE $SOURCE_SCHEMA")
            }
        } finally {
            staged.delete()
        }
        return barangayDao.count()
    }

    /**
     * Unpacks the asset to a real file, because SQLite attaches paths and an asset inside the
     * APK is not one. Written to `cacheDir` and deleted in the same call: it is a copy of
     * something the APK already carries, so nothing is lost if the process dies mid-seed and
     * the next launch simply stages it again.
     */
    private fun stageAsset(): File {
        val staged = File.createTempFile("psgc-", ".db", context.cacheDir)
        context.assets.open(PsgcDataset.ASSET_PATH).use { asset ->
            staged.outputStream().use { asset.copyTo(it) }
        }
        return staged
    }

    private companion object {
        private const val TAG = "PsgcSeeder"

        private val SEEDED_VINTAGE = stringPreferencesKey("psgc_seeded_vintage")

        private const val TABLE = "psgc_barangays"

        /** Attach alias for the bundled file. Scoped to one call, so it only has to be unused. */
        private const val SOURCE_SCHEMA = "psgc_src"

        /**
         * Columns are named rather than `SELECT *` so a column added to either side fails
         * loudly instead of shifting every value one place to the left.
         */
        private val COPY_SQL = """
            INSERT INTO $TABLE (
                code, name, city_muni_code, city_muni_name,
                province_code, province_name, region_code, region_name, search_text
            )
            SELECT
                code, name, city_muni_code, city_muni_name,
                province_code, province_name, region_code, region_name, search_text
            FROM $SOURCE_SCHEMA.$TABLE
        """.trimIndent()
    }
}
