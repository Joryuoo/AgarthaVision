package com.agarthavision.domain.usecase.home

import com.agarthavision.domain.model.ActivityItem
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
import com.agarthavision.domain.sync.LastSyncStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveRecentActivityUseCase @Inject constructor(
    private val sampleRepository: SampleRepository,
    private val sessionRepository: SessionRepository,
    private val patientRepository: PatientRepository,
    private val lastSyncStore: LastSyncStore,
) {
    operator fun invoke(
        userId: String,
        limit: Int,
        sinceMillis: Long? = null,
    ): Flow<List<ActivityItem>> = combine(
        sampleRepository.observeCaptureActivity(userId, limit),
        sampleRepository.observeVerifyActivity(userId, limit),
        sessionRepository.observeStartedActivity(userId, limit),
        patientRepository.observeAddedActivity(userId, limit),
        lastSyncStore.observe(userId),
    ) { captured, verified, started, added, lastSync ->
        val syncItems = if (lastSync != null && lastSync.itemsSynced > 0) {
            listOf(ActivityItem.SyncFinished(lastSync.itemsSynced, lastSync.completedAtMillis))
        } else {
            emptyList()
        }
        val all = captured + verified + started + added + syncItems
        val filtered = if (sinceMillis != null) {
            all.filter { it.occurredAt >= sinceMillis }
        } else {
            all
        }
        filtered.sortedByDescending { it.occurredAt }.take(limit)
    }
}
