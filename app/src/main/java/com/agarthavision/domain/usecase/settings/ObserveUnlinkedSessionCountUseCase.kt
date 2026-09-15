package com.agarthavision.domain.usecase.settings

import com.agarthavision.data.local.dao.SessionDao
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Live count of unowned, non-exempt local sessions recorded while signed out and still
 * claimable at the next login. Backs the signed-out "N not linked" badge in Settings.
 *
 * Note: reaches into the DAO directly by the same documented precedent as
 * [ObservePendingSyncCountsUseCase]. Consolidation is deferred to Phase 2 WorkManager work.
 */
class ObserveUnlinkedSessionCountUseCase @Inject constructor(
    private val sessionDao: SessionDao,
) {
    operator fun invoke(): Flow<Int> = sessionDao.observeUnlinkedCount()
}
