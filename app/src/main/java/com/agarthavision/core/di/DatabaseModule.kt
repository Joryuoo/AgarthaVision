package com.agarthavision.core.di

import android.content.Context
import androidx.room.ExperimentalRoomApi
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agarthavision.core.database.ALL_MIGRATIONS
import com.agarthavision.core.database.AgarthaDatabase
import com.agarthavision.data.local.dao.ColleagueDao
import com.agarthavision.data.local.dao.CoverageDao
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.data.repository.AndroidReportPdfRenderer
import com.agarthavision.data.repository.BoundaryRepositoryImpl
import com.agarthavision.data.repository.ColleagueRepositoryImpl
import com.agarthavision.data.repository.CoverageRepositoryImpl
import com.agarthavision.data.repository.DetectionRepositoryImpl
import com.agarthavision.data.repository.DocumentsReportFileStore
import com.agarthavision.data.repository.LocalReportRepository
import com.agarthavision.data.repository.PatientAccessRepositoryImpl
import com.agarthavision.data.repository.PatientRepositoryImpl
import com.agarthavision.data.repository.PsgcRepositoryImpl
import com.agarthavision.data.repository.SampleRepositoryImpl
import com.agarthavision.data.repository.SessionRepositoryImpl
import com.agarthavision.data.repository.SupabaseAccountAccessRepository
import com.agarthavision.data.repository.SupabaseAuthRepository
import com.agarthavision.data.repository.SupabaseSampleImageRepository
import com.agarthavision.domain.repository.AccountAccessRepository
import com.agarthavision.domain.repository.AuthRepository
import com.agarthavision.domain.repository.BoundaryRepository
import com.agarthavision.domain.repository.ColleagueRepository
import com.agarthavision.domain.repository.CoverageRepository
import com.agarthavision.domain.repository.DetectionRepository
import com.agarthavision.domain.repository.PatientAccessRepository
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
@Suppress("TooManyFunctions") // One @Provides per DAO; splitting would just move them.
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // The spread copies ALL_MIGRATIONS once, when the database is first built. Keeping one list
    // that this and Migration22To23Test both read is worth more than that copy.
    @Suppress("SpreadOperator")
    @OptIn(ExperimentalRoomApi::class)
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
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_table_modification_log " +
                                "(table_id INTEGER PRIMARY KEY, invalidated INTEGER NOT NULL DEFAULT 0)",
                        )
                    }
                },
            )
            .setInMemoryTrackingMode(false)
            // Hand-written migrations first. Room uses one whenever it covers the upgrade, and
            // falls back to the destructive rebuild below only when none does. From version 23
            // the local database holds the inference queue, frames that exist nowhere else, so
            // every bump from 22 onward must ship a migration. See Migrations.kt.
            .addMigrations(*ALL_MIGRATIONS)
            // Phase 1 has no production data — destructive migrations are acceptable for any
            // upgrade that starts below version 22, which no hand-written migration covers.
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

    @Provides
    fun provideCoverageDao(database: AgarthaDatabase): CoverageDao = database.coverageDao()

    @Provides
    fun provideColleagueDao(database: AgarthaDatabase): ColleagueDao = database.colleagueDao()
}

/**
 * Binds repository interfaces to their data-layer implementations.
 */
@Suppress("TooManyFunctions") // One @Binds per repository interface; splitting would just move them.
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
    abstract fun bindColleagueRepository(
        implementation: ColleagueRepositoryImpl,
    ): ColleagueRepository

    @Binds
    abstract fun bindPatientAccessRepository(
        implementation: PatientAccessRepositoryImpl,
    ): PatientAccessRepository

    @Binds
    abstract fun bindCoverageRepository(
        implementation: CoverageRepositoryImpl,
    ): CoverageRepository

    @Binds
    abstract fun bindReportRepository(
        implementation: LocalReportRepository,
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindPsgcRepository(
        implementation: PsgcRepositoryImpl,
    ): PsgcRepository

    @Binds
    @Singleton
    abstract fun bindBoundaryRepository(
        implementation: BoundaryRepositoryImpl,
    ): BoundaryRepository

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

    @Binds
    abstract fun bindAccountAccessRepository(
        implementation: SupabaseAccountAccessRepository,
    ): AccountAccessRepository
}
