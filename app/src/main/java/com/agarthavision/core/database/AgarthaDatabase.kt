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
 * Version 7 adds persisted reports. Local schema history is exported under
 * `app/schemas/`.
 */
@Database(
    entities = [
        SampleEntity::class,
        SessionEntity::class,
        DetectionEntity::class,
        ReportEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AgarthaDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun sessionDao(): SessionDao
    abstract fun detectionDao(): DetectionDao
    abstract fun reportDao(): ReportDao
}
