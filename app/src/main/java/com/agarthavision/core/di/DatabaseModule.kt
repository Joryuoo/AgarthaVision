package com.agarthavision.core.di

// TODO: Uncomment when AgarthaDatabase and DAOs are defined in data/local/
//
// import android.content.Context
// import androidx.room.Room
// import com.agarthavision.data.local.AgarthaDatabase
// import dagger.Module
// import dagger.Provides
// import dagger.hilt.InstallIn
// import dagger.hilt.android.qualifiers.ApplicationContext
// import dagger.hilt.components.SingletonComponent
// import javax.inject.Singleton
//
// @Module
// @InstallIn(SingletonComponent::class)
// object DatabaseModule {
//     @Provides @Singleton
//     fun provideDatabase(@ApplicationContext ctx: Context): AgarthaDatabase =
//         Room.databaseBuilder(ctx, AgarthaDatabase::class.java, "agarthavision.db")
//             .build()
//
//     @Provides fun provideSampleDao(db: AgarthaDatabase) = db.sampleDao()
//     @Provides fun provideDetectionDao(db: AgarthaDatabase) = db.detectionDao()
// }

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.SampleDao
import com.agarthavision.data.repository.SampleRepositoryImpl
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
    /**
     * Provides the app-wide Room database instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AgarthaDatabase =
        Room.databaseBuilder(
            context,
            AgarthaDatabase::class.java,
            "agarthavision.db",
        ).build()

    /**
     * Provides the DAO for captured samples.
     */
    @Provides
    fun provideSampleDao(database: AgarthaDatabase): SampleDao =
        database.sampleDao()
}

/**
 * Binds repository interfaces to their data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /**
     * Binds the Room-backed sample repository implementation.
     */
    @Binds
    abstract fun bindSampleRepository(
        implementation: SampleRepositoryImpl,
    ): SampleRepository
}