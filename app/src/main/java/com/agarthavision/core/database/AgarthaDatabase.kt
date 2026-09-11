package com.agarthavision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity

/**
 * AgarthaVision Room database.
 *
 * Phase 1 schema covers Room mirrors of the Supabase `sessions`, `samples`,
 * `detections`, and `reports` tables. See schema.ts.
 *
 * Version 7 adds persisted reports. Version 8 (offline access, ADR-007) adds
 * `sessions.supabase_status` + `sessions.claim_exempt` and relaxes `samples.user_id`
 * to nullable. Version 9 adds `reports.pdf_file_path` (`0011_reports_pdf_and_lpf.sql`).
 * Version 10 adds the optional `detections.stage` column for egg/parasite stage
 * classification (`0010_verification_stage.sql`).
 * No hand-written `Migration` is supplied: per [DatabaseModule] the app
 * uses `fallbackToDestructiveMigration`, so a version bump recreates the tables from
 * these entities. Acceptable in Phase 1 (no production data). Local schema history is
 * exported under `app/schemas/`.
 */
@Database(
    entities = [
        SampleEntity::class,
        SessionEntity::class,
        DetectionEntity::class,
        ReportEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AgarthaDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun sessionDao(): SessionDao
    abstract fun detectionDao(): DetectionDao
    abstract fun reportDao(): ReportDao
}
