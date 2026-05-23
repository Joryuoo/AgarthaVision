package com.agarthavision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agarthavision.data.local.SampleDao
import com.agarthavision.data.local.SampleEntity

/**
 * Main Room database for AgarthaVision local persistence.
 */
@Database(
    entities = [
        SampleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AgarthaDatabase : RoomDatabase() {
    /**
     * Provides access to captured sample records.
     */
    abstract fun sampleDao(): SampleDao
}