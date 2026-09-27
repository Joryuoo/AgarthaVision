package com.agarthavision.data.repository

import android.content.Context
import com.agarthavision.data.geo.BoundaryBinaryReader
import com.agarthavision.data.geo.GeoDataset
import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.BoundarySet
import com.agarthavision.domain.repository.BoundaryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads the bundled "AVGE" boundary assets (see `tools/geo/README.md` and
 * [com.agarthavision.data.geo.BoundaryBinaryReader]) and caches what it parses.
 *
 * The province set and directory parse once per process — a few tens of thousands of points,
 * cheap to hold in memory but not free to reparse on every screen visit — and are guarded by a
 * [Mutex] rather than a bare nullable `var`, since the first province-screen visit and the
 * first "my coverage" computation could plausibly race on cold start. Per-province town sets
 * are kept in a small LRU (size [TOWN_CACHE_SIZE]) so paging through a handful of provinces in
 * one session does not re-read assets, without holding every province's towns in memory
 * forever.
 */
class BoundaryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BoundaryRepository {

    private val provincesMutex = Mutex()
    private var cachedProvinces: BoundarySet? = null
    private var cachedDirectory: AreaDirectory? = null

    private val townsMutex = Mutex()
    private var cachedTownsIndex: Map<String, IntRange>? = null
    private var cachedTownsBytes: ByteArray? = null
    private val townCache = object : LinkedHashMap<String, BoundarySet>(
        TOWN_CACHE_INITIAL_CAPACITY,
        TOWN_CACHE_LOAD_FACTOR,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BoundarySet>): Boolean =
            size > TOWN_CACHE_SIZE
    }

    override suspend fun provinces(): BoundarySet {
        loadProvincesIfNeeded()
        return cachedProvinces!!
    }

    override fun cachedProvincesOrNull(): BoundarySet? = cachedProvinces

    override fun cachedDirectoryOrNull(): AreaDirectory? = cachedDirectory

    override suspend fun directory(): AreaDirectory {
        loadProvincesIfNeeded()
        return cachedDirectory!!
    }

    override fun cachedTownsOfOrNull(provinceKey: String): BoundarySet? =
        synchronized(townCache) { townCache[provinceKey] }

    override suspend fun townsOf(provinceKey: String): BoundarySet? =
        townsMutex.withLock {
            val cached = synchronized(townCache) { townCache[provinceKey] }
            if (cached != null) return@withLock cached

            val index = cachedTownsIndex ?: loadTownsIndex().also { cachedTownsIndex = it }
            val byteRange = index[provinceKey]
            if (byteRange == null) {
                null
            } else {
                val bytes = cachedTownsBytes ?: readAsset(GeoDataset.TOWNS_ASSET_PATH).also { cachedTownsBytes = it }
                val set = withContext(Dispatchers.Default) {
                    BoundaryBinaryReader.readTowns(bytes, byteRange)
                }
                townCache[provinceKey] = set
                set
            }
        }

    private suspend fun loadProvincesIfNeeded() {
        if (cachedProvinces != null) return

        provincesMutex.withLock {
            if (cachedProvinces != null) return

            val bytes = readAsset(GeoDataset.PROVINCES_ASSET_PATH)
            val (set, directory) = withContext(Dispatchers.Default) {
                BoundaryBinaryReader.readProvinces(bytes, GeoDataset.VINTAGE)
            }
            cachedProvinces = set
            cachedDirectory = directory
        }
    }

    private suspend fun loadTownsIndex(): Map<String, IntRange> {
        val bytes = cachedTownsBytes ?: readAsset(GeoDataset.TOWNS_ASSET_PATH).also { cachedTownsBytes = it }
        return withContext(Dispatchers.Default) {
            BoundaryBinaryReader.readTownsIndex(bytes, GeoDataset.VINTAGE)
        }
    }

    private suspend fun readAsset(path: String): ByteArray =
        withContext(Dispatchers.IO) {
            context.assets.open(path).use { it.readBytes() }
        }

    private companion object {
        const val TOWN_CACHE_SIZE = 8
        const val TOWN_CACHE_INITIAL_CAPACITY = 8
        const val TOWN_CACHE_LOAD_FACTOR = 0.75f
    }
}
