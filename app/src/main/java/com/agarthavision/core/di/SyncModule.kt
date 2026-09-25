package com.agarthavision.core.di

import com.agarthavision.data.sync.WorkManagerSyncScheduler
import com.agarthavision.domain.sync.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the sync scheduler port to its WorkManager implementation.
 *
 * Its own module rather than a pair added to `DatabaseModule`, because nothing here is about
 * persistence: a use case asking for a sync should not have to reach through the database
 * graph to find the thing that schedules one.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        implementation: WorkManagerSyncScheduler,
    ): SyncScheduler
}
