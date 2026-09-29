package com.agarthavision.domain.repository

import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.model.TownCoverage
import kotlinx.coroutines.flow.Flow

/**
 * Per-town examined/positive smear counts for the My coverage card. See `CoverageDao` for
 * the underlying query and its examined/positive rule.
 */
interface CoverageRepository {
    fun observeTownCoverage(userId: String, window: TimeWindow): Flow<List<TownCoverage>>
}
