package com.agarthavision.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 22 → 23: the background inference queue's two columns on `samples`.
 *
 * **The first hand-written migration in this project, and why this version earned one.** Every
 * earlier bump fell back to a destructive rebuild (see `DatabaseModule`), which wipes the local
 * database. That was acceptable while a wipe only cost data Supabase still had. From version 23
 * the local database holds the inference queue: frames captured and not yet verified exist
 * nowhere else. A wipe would destroy them, so this bump migrates instead.
 *
 * Both statements only add. Nothing is dropped, rewritten or deleted (C8):
 * - `inference_state` defaults to `ready`, which is what every existing non-manual row already
 *   is: capture used to wait for the model before it wrote the row.
 * - Existing manual rows are then marked `manual`. Reads go through
 *   `effectiveInferenceState()`, which would give the same answer from `is_manual` alone, but
 *   the stored value should not contradict the row.
 * - `inference_attempts` starts at 0.
 *
 * The column definitions must match `SampleEntity` exactly, defaults included, or Room refuses
 * to open the migrated database. `Migration22To23Test` opens one to prove they do.
 */
val MIGRATION_22_23: Migration = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `samples` ADD COLUMN `inference_state` TEXT NOT NULL DEFAULT 'ready'",
        )
        db.execSQL(
            "ALTER TABLE `samples` ADD COLUMN `inference_attempts` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL("UPDATE `samples` SET `inference_state` = 'manual' WHERE `is_manual` = 1")
    }
}

/**
 * Version 23 → 24: the `colleagues` name cache (14zcqntjph6).
 *
 * One new table and nothing else, so no existing row is touched (C8). It starts empty and the
 * next pull fills it; until then a colleague's record reads "another medtech". The definition
 * must match `ColleagueEntity` exactly or Room refuses to open the migrated file, which
 * `Migration23To24Test` checks by opening one.
 */
val MIGRATION_23_24: Migration = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `colleagues` " +
                "(`user_id` TEXT NOT NULL, `full_name` TEXT, PRIMARY KEY(`user_id`))",
        )
    }
}

/** Every hand-written migration, for `DatabaseModule` and the migration test to share. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_22_23, MIGRATION_23_24)
