package com.agarthavision.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Caching and LRU behaviour of [BoundaryRepositoryImpl], against the real committed assets.
 *
 * `Context.getAssets()` returns a final platform `AssetManager`, so there is no seam to count
 * `open()` calls through a mock the way [PsgcRepositoryCacheTest] counts DAO calls. Instead,
 * caching is proven by **reference identity**: [BoundarySet] instances are only ever created
 * inside the parse path, so a second call returning the exact same instance means the parse
 * did not run again, and a call returning a *different* instance after enough distinct
 * provinces were loaded means the entry really was evicted and reparsed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BoundaryRepositoryImplTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `second provinces call returns the same cached instance`() = runTest {
        val repository = BoundaryRepositoryImpl(context)

        val first = repository.provinces()
        val second = repository.provinces()

        assertSame("provinces() should not reparse on a second call", first, second)
    }

    @Test
    fun `second directory call returns the same cached instance`() = runTest {
        val repository = BoundaryRepositoryImpl(context)

        val first = repository.directory()
        val second = repository.directory()

        assertSame("directory() should not reparse on a second call", first, second)
    }

    @Test
    fun `townsOf a still-cached province returns the same instance`() = runTest {
        val repository = BoundaryRepositoryImpl(context)

        val first = repository.townsOf(PROVINCE_KEYS[0])
        val second = repository.townsOf(PROVINCE_KEYS[0])

        assertNotNull(first)
        assertSame("a repeated lookup of the same province should hit the LRU cache", first, second)
    }

    @Test
    fun `loading a 9th distinct province evicts the least-recently-used entry`() = runTest {
        val repository = BoundaryRepositoryImpl(context)

        // Load 8 distinct provinces, filling the LRU (size 8) in this order.
        val loaded = PROVINCE_KEYS.take(8).map { key -> key to repository.townsOf(key) }
        loaded.forEach { (key, set) -> assertNotNull("expected geometry for $key", set) }

        // A 9th distinct province forces an eviction.
        val ninth = repository.townsOf(PROVINCE_KEYS[8])
        assertNotNull(ninth)

        // The first-loaded, least-recently-touched entry should have been evicted and
        // reparsed into a new instance...
        val firstAgain = repository.townsOf(PROVINCE_KEYS[0])
        assertNotSame(
            "the least-recently-used province should have been evicted, not the 2nd-8th",
            loaded[0].second,
            firstAgain,
        )

        // ...while the most-recently-used of the original eight is still cached.
        val eighthAgain = repository.townsOf(PROVINCE_KEYS[7])
        assertSame(
            "the most-recently-used of the original 8 should still be cached",
            loaded[7].second,
            eighthAgain,
        )
    }

    @Test
    fun `townsOf an unknown province key returns null`() = runTest {
        val repository = BoundaryRepositoryImpl(context)

        assertEquals(null, repository.townsOf("9999999999"))
    }

    private companion object {
        // The first nine province_key values from tools/geo/town-keys-q2_2026.csv, sorted.
        val PROVINCE_KEYS = listOf(
            "0102800000",
            "0102900000",
            "0103300000",
            "0105500000",
            "0200900000",
            "0201500000",
            "0203100000",
            "0205000000",
            "0205700000",
        )
    }
}
