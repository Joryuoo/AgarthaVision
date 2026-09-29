package com.agarthavision.data.geo

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

/**
 * Guards how the boundary assets are *packaged*, mirroring
 * [com.agarthavision.data.local.psgc.PsgcAssetPackagingTest] — a `.bin` extension keeps AGP's
 * `.gz` auto-decompression special case out of play (see [GeoDataset]'s KDoc and
 * `tools/geo/build-geo-asset.py`'s "Do not name the output files `.gz`" note).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeoAssetPackagingTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `asset paths stay outside AGP's gz special case`() {
        assertTrue(!GeoDataset.PROVINCES_ASSET_PATH.endsWith(".gz"))
        assertTrue(!GeoDataset.TOWNS_ASSET_PATH.endsWith(".gz"))
    }

    @Test
    fun `both assets open and start with the AVGE magic`() {
        val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
        val townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }

        assertEquals("AVGE", String(provincesBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("AVGE", String(townsBytes.copyOfRange(0, 4), Charsets.US_ASCII))
    }

    @Test
    fun `header vintage equals GeoDataset VINTAGE`() {
        val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
        val townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }

        assertEquals(GeoDataset.VINTAGE, readVintage(provincesBytes))
        assertEquals(GeoDataset.VINTAGE, readVintage(townsBytes))
    }

    @Test
    fun `sha256 of the committed assets matches GeoDataset`() {
        val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
        val townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }

        assertEquals(GeoDataset.PROVINCES_SHA256, sha256(provincesBytes))
        assertEquals(GeoDataset.TOWNS_SHA256, sha256(townsBytes))
    }

    @Test
    fun `both files are within their size budgets`() {
        val provincesBytes = context.assets.open(GeoDataset.PROVINCES_ASSET_PATH).use { it.readBytes() }
        val townsBytes = context.assets.open(GeoDataset.TOWNS_ASSET_PATH).use { it.readBytes() }

        assertTrue("provinces asset exceeds 200 KB budget: ${provincesBytes.size}", provincesBytes.size <= 200 * 1024)
        assertTrue("towns asset exceeds 900 KB budget: ${townsBytes.size}", townsBytes.size <= 900 * 1024)
    }

    private fun readVintage(bytes: ByteArray): String {
        // magic(4) + version(1) + kind(1) + vintageLen(1) + vintage bytes
        val vintageLen = bytes[6].toInt() and 0xFF
        return String(bytes.copyOfRange(7, 7 + vintageLen), Charsets.US_ASCII)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
