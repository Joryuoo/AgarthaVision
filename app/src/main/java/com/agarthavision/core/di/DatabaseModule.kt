package com.agarthavision.core.di

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.repository.DetectionRepositoryImpl
import com.agarthavision.data.repository.SampleRepositoryImpl
import com.agarthavision.data.repository.SupabaseAuthRepository
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.SampleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides Room database, DAOs, and repository bindings for local persistence.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AgarthaDatabase =
        Room.databaseBuilder(
            context,
            AgarthaDatabase::class.java,
            "agarthavision.db",
        )
            // Phase 1 has no production data — destructive migrations are acceptable.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSampleDao(database: AgarthaDatabase): SampleDao = database.sampleDao()

    @Provides
    fun provideSessionDao(database: AgarthaDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideDetectionDao(database: AgarthaDatabase): DetectionDao = database.detectionDao()
}

/**
 * Binds repository interfaces to their data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindSampleRepository(
        implementation: SampleRepositoryImpl,
    ): SampleRepository

    @Binds
    abstract fun bindDetectionRepository(
        implementation: DetectionRepositoryImpl,
    ): DetectionRepository

    /**
     * Provides the Supabase-backed authentication repository.
     */
    @Binds
    abstract fun bindAuthRepository(
        implementation: SupabaseAuthRepository,
    ): AuthRepository
}
