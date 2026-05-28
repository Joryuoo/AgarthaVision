package com.agarthavision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SessionEntity

/**
 * AgarthaVision Room database.
 *
 * Phase 1 schema covers three entities — mirrors of the Supabase `sessions`,
 * `samples`, and `detections` tables. See docs/03_MOBILE_APP_PLAN.md §1.7 +
 * docs/04_CLOUD_BACKEND_PLAN.md §4.
 *
 * Version 3 adds ADR-004 verification/sync metadata. There is no production data
 * yet, so a destructive migration is acceptable.
 */
@Database(
    entities = [
        SampleEntity::class,
        SessionEntity::class,
        DetectionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AgarthaDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun sessionDao(): SessionDao
    abstract fun detectionDao(): DetectionDao
}
