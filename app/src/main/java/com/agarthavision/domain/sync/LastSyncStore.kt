package com.agarthavision.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class SyncCompletion(val completedAtMillis: Long, val itemsSynced: Int)

interface LastSyncStore {
    fun observe(userId: String): Flow<SyncCompletion?>
    suspend fun record(userId: String, completion: SyncCompletion)

    fun observeLastError(userId: String): Flow<String?> = flowOf(null)
    suspend fun recordLastError(userId: String, error: String) {}
}
