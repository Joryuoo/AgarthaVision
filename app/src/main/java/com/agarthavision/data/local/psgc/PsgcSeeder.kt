package com.agarthavision.data.local.psgc

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.PsgcBarangayDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Seeds the bundled PSGC barangay dataset into Room.
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
     * Replaces the table in one transaction, inserting in chunks so neither the parse nor
     * the insert holds all 42,001 rows at once. One transaction keeps a failure from
     * leaving a partially-seeded table behind.
     */
    private suspend fun replaceAll(): Int {
        var inserted = 0
        database.withTransaction {
            barangayDao.deleteAll()
            context.assets.open(PsgcDataset.ASSET_PATH).use { asset ->
                InputStreamReader(GZIPInputStream(asset), Charsets.UTF_8).buffered().useLines { lines ->
                    PsgcCsvParser.parseLines(lines).chunked(CHUNK_SIZE).forEach { chunk ->
                        barangayDao.insertAll(chunk)
                        inserted += chunk.size
                    }
                }
            }
        }
        return inserted
    }

    private companion object {
        private const val TAG = "PsgcSeeder"

        private val SEEDED_VINTAGE = stringPreferencesKey("psgc_seeded_vintage")

        /** Big enough to keep SQLite busy, small enough to stay off the allocation radar. */
        private const val CHUNK_SIZE = 2_000
    }
}
