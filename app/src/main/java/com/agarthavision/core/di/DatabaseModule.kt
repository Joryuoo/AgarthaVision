package com.agarthavision.core.di

import android.content.Context
import androidx.room.Room
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.data.repository.AndroidReportPdfRenderer
import com.agarthavision.data.repository.DetectionRepositoryImpl
import com.agarthavision.data.repository.DocumentsReportFileStore
import com.agarthavision.data.repository.LocalReportRepository
import com.agarthavision.data.repository.PatientRepositoryImpl
import com.agarthavision.data.repository.PsgcRepositoryImpl
import com.agarthavision.data.repository.SampleRepositoryImpl
import com.agarthavision.data.repository.SessionRepositoryImpl
import com.agarthavision.data.repository.SupabaseAuthRepository
import com.agarthavision.data.repository.SupabaseSampleImageRepository
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientRepository
import com.agarthavision.domain.repository.PsgcRepository
import com.agarthavision.domain.repository.ReportFileStore
import com.agarthavision.domain.repository.ReportPdfRenderer
import com.agarthavision.domain.repository.ReportRepository
import com.agarthavision.domain.repository.SampleImageRepository
import com.agarthavision.domain.repository.SampleRepository
import com.agarthavision.domain.repository.SessionRepository
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
            //
            // dropAllTables = false, deliberately. On Room 2.7.0 `true` drops every table in
            // the file including Room's own `room_table_modification_log`, and does not
            // recreate it, so the first Flow collected after a destructive migration dies
            // with `no such table: room_table_modification_log` from the invalidation
            // tracker. Reproduced on the 15 -> 16 upgrade: the app crashes on first launch
            // and only recovers on the second. `false` drops the tables Room knows about,
            // which is every table this schema declares, and leaves its bookkeeping alone.
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    @Provides
    fun provideSampleDao(database: AgarthaDatabase): SampleDao = database.sampleDao()

    @Provides
    fun provideSessionDao(database: AgarthaDatabase): SessionDao = database.sessionDao()

    @Provides
    fun providePatientDao(database: AgarthaDatabase): PatientDao = database.patientDao()

    @Provides
    fun provideDetectionDao(database: AgarthaDatabase): DetectionDao = database.detectionDao()

    @Provides
    fun provideReportDao(database: AgarthaDatabase): ReportDao = database.reportDao()

    @Provides
    fun providePsgcBarangayDao(database: AgarthaDatabase): PsgcBarangayDao =
        database.psgcBarangayDao()

    @Provides
    fun provideSpeciesSuggestionDao(
        database: AgarthaDatabase,
    ): SpeciesSuggestionDao = database.speciesSuggestionDao()

    @Provides
    fun provideSampleSpeciesFindingDao(
        database: AgarthaDatabase,
    ): SampleSpeciesFindingDao = database.sampleSpeciesFindingDao()
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
    abstract fun bindSampleImageRepository(
        implementation: SupabaseSampleImageRepository,
    ): SampleImageRepository

    @Binds
    abstract fun bindDetectionRepository(
        implementation: DetectionRepositoryImpl,
    ): DetectionRepository

    @Binds
    abstract fun bindSessionRepository(
        implementation: SessionRepositoryImpl,
    ): SessionRepository

    @Binds
    abstract fun bindPatientRepository(
        implementation: PatientRepositoryImpl,
    ): PatientRepository

    @Binds
    abstract fun bindReportRepository(
        implementation: LocalReportRepository,
    ): ReportRepository

    @Binds
    abstract fun bindPsgcRepository(
        implementation: PsgcRepositoryImpl,
    ): PsgcRepository

    @Binds
    abstract fun bindReportFileStore(
        implementation: DocumentsReportFileStore,
    ): ReportFileStore

    @Binds
    abstract fun bindReportPdfRenderer(
        implementation: AndroidReportPdfRenderer,
    ): ReportPdfRenderer

    /**
     * Provides the Supabase-backed authentication repository.
     */
    @Binds
    abstract fun bindAuthRepository(
        implementation: SupabaseAuthRepository,
    ): AuthRepository
}
