package com.agarthavision.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agarthavision.data.local.dao.DetectionDao
import com.agarthavision.data.local.dao.PatientDao
import com.agarthavision.data.local.dao.PsgcBarangayDao
import com.agarthavision.data.local.dao.ReportDao
import com.agarthavision.data.local.dao.SampleDao
import com.agarthavision.data.local.dao.SampleSpeciesFindingDao
import com.agarthavision.data.local.dao.SessionDao
import com.agarthavision.data.local.dao.SpeciesSuggestionDao
import com.agarthavision.data.local.entity.DetectionEntity
import com.agarthavision.data.local.entity.PatientEntity
import com.agarthavision.data.local.entity.PatientUserEntity
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import com.agarthavision.data.local.entity.ReportEntity
import com.agarthavision.data.local.entity.SampleEntity
import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.data.local.entity.SessionEntity
import com.agarthavision.data.local.entity.SpeciesSuggestionEntity

/**
 * AgarthaVision Room database.
 *
 * Phase 1 schema covers Room mirrors of the Supabase `sessions`, `samples`,
 * `detections`, and `reports` tables. See schema.ts.
 *
 * Version 7 adds persisted reports. Version 8 (offline access, ADR-007) adds
 * `sessions.supabase_status` + `sessions.claim_exempt` and relaxes `samples.user_id`
 * to nullable. Version 9 adds `reports.pdf_file_path` (`0011_reports_pdf_and_lpf.sql`).
 * Version 10 adds `sessions.psgc_barangay_code` and the bundled `psgc_barangays`
 * reference table behind the session barangay picker
 * (`0010_session_psgc_barangay.sql`).
 *
 * `psgc_barangays` is the one table here with no Supabase mirror: it is reference data
 * seeded from an APK asset by [com.agarthavision.data.local.psgc.PsgcSeeder], and the
 * surveillance map joins on the code a patient stores rather than on this table.
 *
 * Version 12 adds the `sample_species_findings` table and `detections.species_touched`
 * (`0012_polyparasitism_findings.sql`) plus `samples.deleted_at`
 * (`0013_sample_soft_delete.sql`).
 *
 * Version 13 is the patient-based schema (`0001_init.sql` on the new `agarthavision`
 * project). It adds `patients` and the `patient_users` join, gives `sessions` a
 * `patient_id`, and drops five columns: `sessions.ended_at`, `sessions.notes`,
 * `sessions.psgc_barangay_code`, `sessions.claim_exempt` and all three `samples.gps_*`,
 * plus `reports.epg_per_species_json`.
 *
 * Version 14 adds `species_suggestions`, the offline index behind the "Other species"
 * field (PB-08b). Like `psgc_barangays` it has no Supabase mirror — it is derived locally
 * from rows the device already holds.
 *
 * **It is a bump rather than an addition at 13, and that is not fussiness.** Version 13 is
 * already committed and on devices. Adding a table without changing the number is precisely
 * the equal-version-different-hash case described below: destructive fallback does not
 * fire, Room throws `Room cannot verify the data integrity` on open, and every device
 * carrying the other build crashes at launch. A version bump is cheap and a collision is
 * not.
 *
 * **Version 13 owns the whole shape.** `claim_exempt` is dropped here rather than in the
 * mandatory-login change that makes it dead, because a second schema change at the same
 * version is precisely the collision described below — the login work removes Kotlin, not
 * columns.
 *
 * **The jump from 10 to 12 was deliberate, and 11 is still left free.** Three branches wanted version 10
 * at once — `feat/sample-geospatial-mapping` (`psgc_barangays`), which won it and is merged
 * above; `feature/editable-report` (`detections.stage`), which lost it and was reverted on
 * staging with 86d4a6jwy deprioritised; and this one. Room only falls back destructively on a
 * version *change*; at an equal version with a different identity hash it throws
 * `IllegalStateException: Room cannot verify the data integrity` on open, and every device
 * carrying one of the other builds crashes at launch. That has already happened once on this
 * project. Leaving 11 free keeps a slot for the stage work when it returns. Versions are only
 * an ordering token under destructive fallback, so a skipped number costs nothing.
 *
 * No hand-written `Migration` is supplied: per [DatabaseModule] the app
 * uses `fallbackToDestructiveMigration`, so a version bump recreates the tables from
 * these entities. Acceptable in Phase 1 (no production data). Local schema history is
 * exported under `app/schemas/`.
 */
@Database(
    entities = [
        SampleEntity::class,
        SessionEntity::class,
        PatientEntity::class,
        PatientUserEntity::class,
        DetectionEntity::class,
        ReportEntity::class,
        SampleSpeciesFindingEntity::class,
        PsgcBarangayEntity::class,
        SpeciesSuggestionEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class AgarthaDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun sessionDao(): SessionDao
    abstract fun patientDao(): PatientDao
    abstract fun detectionDao(): DetectionDao
    abstract fun reportDao(): ReportDao
    abstract fun psgcBarangayDao(): PsgcBarangayDao
    abstract fun speciesSuggestionDao(): SpeciesSuggestionDao

    abstract fun sampleSpeciesFindingDao(): SampleSpeciesFindingDao
}
