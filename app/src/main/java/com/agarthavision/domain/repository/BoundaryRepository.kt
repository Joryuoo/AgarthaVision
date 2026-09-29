package com.agarthavision.domain.repository

import com.agarthavision.domain.geo.AreaDirectory
import com.agarthavision.domain.geo.BoundarySet

/**
 * Offline province/town boundary geometry, read from the bundled binary assets — see
 * `tools/geo/README.md` for how they are built and `data/geo/GeoDataset.kt` for their identity.
 */
interface BoundaryRepository {
    /** All 85 province-level units. */
    suspend fun provinces(): BoundarySet

    /** Cached province-level units if already loaded in memory, or `null`. */
    fun cachedProvincesOrNull(): BoundarySet? = null

    /** The full town/province directory, independent of loaded geometry. */
    suspend fun directory(): AreaDirectory

    /** Cached area directory if already loaded in memory, or `null`. */
    fun cachedDirectoryOrNull(): AreaDirectory? = null

    /** A province's towns, or `null` if no town-level geometry is bundled for it. */
    suspend fun townsOf(provinceKey: String): BoundarySet?

    /** Cached towns of a province if already in memory, or `null`. */
    fun cachedTownsOfOrNull(provinceKey: String): BoundarySet? = null
}
