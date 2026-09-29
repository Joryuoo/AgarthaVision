package com.agarthavision.domain.sync

import kotlinx.coroutines.flow.Flow

data class SyncCompletion(val completedAtMillis: Long, val itemsSynced: Int)

interface LastSyncStore {
    fun observe(userId: String): Flow<SyncCompletion?>
    suspend fun record(userId: String, completion: SyncCompletion)
}
