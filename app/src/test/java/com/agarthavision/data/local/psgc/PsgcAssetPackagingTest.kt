package com.agarthavision.data.local.psgc

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * Guards how the PSGC dataset is *packaged*, as opposed to what it contains.
 *
 * This exists because the picker once shipped completely broken and nothing caught it. The
 * asset was committed as `psgc-barangays-4q2023.csv.gz`; AGP gunzips any asset whose name
 * ends in `.gz` while packaging and strips the extension, so what reached the APK was
 * `psgc-barangays-4q2023.csv` holding 4.14 MB of plain text while [PsgcDataset.ASSET_PATH]
 * still named the `.gz` path. Every seed threw `FileNotFoundException`, [PsgcSeeder] caught
 * it and logged at warn, and the medtech saw a barangay field that never matched anything —
 * with session creation blocked on selecting one.
 *
 * These assertions are deliberately about the file and not the rows, so a failure here says
 * "the asset is not reaching the device" rather than "the data is wrong". The row-level
 * contract lives in [PsgcSeederTest] and [PsgcDatasetIntegrityTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PsgcAssetPackagingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `asset path stays outside AGP's gz special case`() {
        assertFalse(
            "PsgcDataset.ASSET_PATH must not end in .gz — AGP decompresses and renames those " +
                "assets during packaging, so the path would not exist on a device.",
            PsgcDataset.ASSET_PATH.endsWith(".gz"),
        )
    }

    @Test
    fun `the asset is present under the name the app opens`() {
        // Reading it, not just listing it: this is the exact call PsgcSeeder makes, and it is
        // the one that used to throw.
        val bytes = context.assets.open(PsgcDataset.ASSET_PATH).use { it.readBytes() }
        assertTrue("The bundled PSGC asset is empty.", bytes.isNotEmpty())
    }

    @Test
    fun `the asset is a SQLite database and not something packaging rewrote`() {
        val header = context.assets.open(PsgcDataset.ASSET_PATH).use { stream ->
            ByteArray(SQLITE_MAGIC.size).also { stream.read(it) }
        }

        // Every SQLite file opens with this literal. The asset used to be gzip and the same
        // assertion was made on 0x1f 0x8b; the point is unchanged, which is that packaging
        // must hand the device the bytes this constant names.
        assertEquals(
            "The bundled PSGC asset does not start with the SQLite file header.",
            SQLITE_MAGIC.toList(),
            header.toList(),
        )
    }

    @Test
    fun `the asset carries the barangay table and no Room metadata`() {
        // This is what keeps the asset independent of Room's identityHash. The moment it
        // carries room_master_table somebody has regenerated it as a Room database, and it
        // starts having to be rebuilt on every schema version bump.
        val tables = bundledAssetTables(context)

        assertTrue("The asset has no psgc_barangays table: $tables", "psgc_barangays" in tables)
        assertFalse(
            "The asset carries room_master_table, which recouples it to Room's schema version.",
            "room_master_table" in tables,
        )
    }

    @Test
    fun `the committed asset is the one this vintage was reviewed against`() {
        val bytes = context.assets.open(PsgcDataset.ASSET_PATH).use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

        assertEquals(
            "The bundled PSGC asset does not match PsgcDataset.ASSET_SHA256. If the dataset " +
                "was regenerated on purpose, update the constant and the vintage table in " +
                "tools/psgc/README.md in the same change.",
            PsgcDataset.ASSET_SHA256,
            digest,
        )
    }

    private companion object {
        /** `SQLite format 3` and its terminating NUL — the first 16 bytes of any SQLite file. */
        private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII) + 0
    }
}
