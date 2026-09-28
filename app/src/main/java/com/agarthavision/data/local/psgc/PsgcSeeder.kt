package com.agarthavision.data.local.psgc

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.entity.PsgcBarangayEntity
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
 * - The table being empty. `fallbackToDestructiveMigration(dropAllTables = false)` is in force
 *   (`core/di/DatabaseModule.kt`), so any Room version bump wipes this table; the recorded
 *   vintage alone would wrongly report it as already seeded.
 * - The recorded vintage differing from [PsgcDataset.VINTAGE]. This is what makes changing
 *   the pinned PSGC release a dataset swap plus a constant, with no migration.
 *
 * **The asset is a SQLite file, not a CSV.** It used to gunzip a CSV and build 42,010
 * entities in Kotlin, which cost roughly a minute on a Redmi Note 11 and made the app feel
 * unresponsive for the whole of a first launch. The rows now come straight off a cursor
 * against the bundled `.db` file, in chunks, through the DAO — not `ATTACH DATABASE`, which
 * an earlier version of this class used: attaching a second file to Room's WAL-mode pooled
 * connection makes Android's framework try to disable WAL, which throws
 * `IllegalStateException` the moment any other connection in the pool is mid-transaction,
 * which at app startup it usually is. Opening the asset as its own standalone, read-only
 * `SQLiteDatabase` outside Room's pool avoids that entirely.
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
     * The copy is atomic: `database.withTransaction` wraps the DELETE + all INSERT batches
     * so an interrupted copy cannot leave the picker holding half a country.
     *
     * **Why no ATTACH DATABASE here:** the previous implementation issued
     * `ATTACH DATABASE … AS psgc_src` on Room's `openHelper.writableDatabase` to move the
     * rows via a single `INSERT … SELECT`. Android's SQLite framework automatically calls
     * `disableWriteAheadLogging()` when ATTACH is issued against a WAL-mode connection, and
     * that reconfiguration requires every other connection in the pool to be idle first.
     * Because multiple coroutines touch the database concurrently at startup, a connection
     * is almost always busy, causing an `IllegalStateException` on every first launch. The
     * fix opens the staged asset file as a plain, read-only `android.database.sqlite.SQLiteDatabase`
     * outside Room's pool — no ATTACH, no WAL reconfiguration, no race.
     */
    private suspend fun replaceAll(): Int {
        val staged = stageAsset()
        try {
            val rows = readStagedRows(staged)
            database.withTransaction {
                barangayDao.deleteAll()
                rows.chunked(COPY_CHUNK_SIZE).forEach { chunk -> barangayDao.insertAll(chunk) }
            }
        } finally {
            staged.delete()
        }
        return barangayDao.count()
    }

    /** Reads every row of the staged asset's `psgc_barangays` table into entities. */
    private fun readStagedRows(staged: File): List<PsgcBarangayEntity> =
        SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { src ->
            src.rawQuery(
                "SELECT code, name, city_muni_code, city_muni_name," +
                    " province_code, province_name, region_code, region_name, search_text" +
                    " FROM $TABLE",
                null,
            ).use { cursor -> cursor.toBarangayEntities() }
        }

    private fun Cursor.toBarangayEntities(): List<PsgcBarangayEntity> {
        val codeIndex = getColumnIndexOrThrow("code")
        val nameIndex = getColumnIndexOrThrow("name")
        val cityMuniCodeIndex = getColumnIndexOrThrow("city_muni_code")
        val cityMuniNameIndex = getColumnIndexOrThrow("city_muni_name")
        val provinceCodeIndex = getColumnIndexOrThrow("province_code")
        val provinceNameIndex = getColumnIndexOrThrow("province_name")
        val regionCodeIndex = getColumnIndexOrThrow("region_code")
        val regionNameIndex = getColumnIndexOrThrow("region_name")
        val searchTextIndex = getColumnIndexOrThrow("search_text")
        return buildList {
            while (moveToNext()) {
                add(
                    PsgcBarangayEntity(
                        code = getString(codeIndex),
                        name = getString(nameIndex),
                        cityMuniCode = getString(cityMuniCodeIndex),
                        cityMuniName = getString(cityMuniNameIndex),
                        provinceCode = getString(provinceCodeIndex),
                        provinceName = getString(provinceNameIndex),
                        regionCode = getString(regionCodeIndex),
                        regionName = getString(regionNameIndex),
                        searchText = getString(searchTextIndex),
                    ),
                )
            }
        }
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

        /** Rows inserted per Room transaction batch. Keeps heap pressure low on first seed. */
        private const val COPY_CHUNK_SIZE = 1_000
    }
}
