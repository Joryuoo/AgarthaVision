package com.agarthavision.data.repository

import com.agarthavision.data.local.dao.CoverageDao
import com.agarthavision.domain.model.TimeWindow
import com.agarthavision.domain.model.TownCoverage
import com.agarthavision.domain.repository.CoverageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CoverageRepositoryImpl @Inject constructor(
    private val coverageDao: CoverageDao,
) : CoverageRepository {
    override fun observeTownCoverage(userId: String, window: TimeWindow): Flow<List<TownCoverage>> =
        coverageDao.observeTownCoverage(userId, window.startMillis, window.endMillis).map { rows ->
            rows.map { row ->
                TownCoverage(
                    townCode = row.townCode,
                    smearCount = row.smearCount,
                    positiveCount = row.positiveCount,
                )
            }
        }
}
