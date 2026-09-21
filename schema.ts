/**
 * AgarthaVision Ground Truth Data Model
 *
 * This file is documentation only. It uses TypeScript-like syntax because it is
 * easier to scan than prose, but it is never compiled or executed.
 *
 * Source precedence:
 * 1. `supabase/migrations/*.sql` is the authority for actual Postgres tables,
 *    column nullability, defaults, foreign keys, RLS policies, and CHECKs.
 * 2. Room entities under `app/src/main/java/com/agarthavision/data/local/entity`
 *    describe the local offline cache and may intentionally contain Room-only
 *    fields.
 * 3. Domain models under `app/src/main/java/com/agarthavision/domain/model`
 *    describe app-facing semantics.
 *
 * The legacy ERD was cross-checked while creating this file. When the ERD and
 * migrations disagree, the migration SQL wins.
 */

type UUID = string;
type TimestampTZ = string; // Postgres timestamptz / epoch millis in Room where noted.
type Json = Record<string, unknown>;
type JsonArray = unknown[];

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

/**
 * Room/domain sample lifecycle.
 *
 * Supabase source of truth: no `samples.status` column exists in migrations
 * `0001` through `0008`; sync state is represented locally by Room and by
 * presence/absence of remote rows.
 *
 * Room/domain mirror:
 * - `domain/model/SampleStatus.kt`
 * - `data/local/entity/SampleEntity.kt`
 */
export enum SampleStatus {
  FLAGGED = "flagged",
  VERIFIED = "verified",
  SYNCED = "synced",
  SYNC_FAILED = "sync_failed",
}

/**
 * Human-in-the-loop verdict for a detection.
 *
 * Supabase source of truth:
 * - Created by `0002_verification_fields.sql`
 * - `detections.verdict text not null default 'CONFIRMED'`
 * - CHECK: `CONFIRMED`, `FALSE_POSITIVE`, `WRONG_CLASS`, `BOX_INCORRECT`
 *
 * Room/domain mirror:
 * - `domain/model/DetectionVerdict.kt`
 * - `data/local/entity/DetectionEntity.kt`
 *
 * Note: Room stores lowercase values; remote sync maps through
 * `DetectionVerdict.remoteValue` to the uppercase Postgres values.
 */
export enum DetectionVerdict {
  CONFIRMED = "confirmed",
  FALSE_POSITIVE = "false_positive",
  WRONG_CLASS = "wrong_class",
  BOX_INCORRECT = "box_incorrect",
}

/**
 * Egg class labels supported by the Phase 1 model and reports.
 *
 * Supabase source of truth: stored as text in `detections.class_label`,
 * `detections.expert_class`, `reports.positive_species`, and
 * `sample_species_findings.species`. No Postgres enum exists yet — the last
 * holds free text too, because "Other" lets a medtech name a species outside
 * this list.
 *
 * Domain mirror:
 * - `domain/model/EggSpecies.kt`
 */
export enum EggSpecies {
  ASCARIS = "Ascaris lumbricoides",
  TRICHURIS = "Trichuris trichiura",
  HOOKWORM = "Hookworm",
  OTHER = "Other",
}

/**
 * Persisted report category.
 *
 * Supabase source of truth:
 * - Created by `0008_reports.sql`
 * - CHECK: `report_type in ('session')`
 *
 * Room/domain mirror:
 * - `domain/model/ReportType.kt`
 * - `data/local/entity/ReportEntity.kt`
 */
export enum ReportType {
  SESSION = "session",
}

/**
 * Room-only sync state for reports.
 *
 * Supabase source of truth: no `reports.supabase_status` column exists in
 * migration `0008`; report sync state is local only.
 *
 * Room/domain mirror:
 * - `domain/model/ReportSyncStatus.kt`
 * - `data/local/entity/ReportEntity.kt`
 */
export enum ReportSyncStatus {
  PENDING = "pending",
  SYNCED = "synced",
  SYNC_FAILED = "sync_failed",
}

/**
 * Origin of a detection frame in the app workflow.
 *
 * Supabase source of truth: no dedicated column or enum exists in migrations
 * `0001` through `0008`. Manual captures are represented by
 * `samples.is_manual = true` and by detections with nullable bounding boxes.
 *
 * Domain mirror:
 * - `domain/model/FrameSource.kt`
 */
export enum FrameSource {
  MODEL = "model",
  MANUAL = "manual",
}

// ---------------------------------------------------------------------------
// Supabase + Room entities
// ---------------------------------------------------------------------------

/**
 * User profile row attached to Supabase Auth users.
 *
 * Supabase migrations:
 * - `0001_init.sql`: creates `profiles`, `handle_new_user()`, base RLS.
 * - `0004_fix_profiles_rls_recursion.sql`: replaces admin-readable policies
 *   with `public.is_admin(uuid)` to avoid recursive profile reads.
 *
 * Room mirror: none. User identity comes from Supabase Auth session state.
 */
export interface Profile {
  id: UUID;
  // PK. FK -> auth.users(id). DELETE CASCADE.

  full_name: string | null;
  // Nullable display name copied from auth metadata on signup when available.

  role: "medtech" | "admin";
  // NOT NULL. Default `medtech`. CHECK in migration `0001`.

  created_at: TimestampTZ;
  // NOT NULL. Default `now()`.
}

/**
 * A patient: the unit a medtech works from. A patient owns sessions; a session
 * is one fecal smear.
 *
 * Supabase migrations:
 * - `0001_init.sql` (patient-based consolidation): creates `patients`, the
 *   `patient_users` join, and the `on_patient_created` auto-link trigger.
 *
 * Room mirror:
 * - `PatientEntity.kt`
 */
export interface Patient {
  id: UUID;
  // PK.

  lastname: string;
  // NOT NULL, non-blank CHECK.

  firstname: string;
  // NOT NULL, non-blank CHECK.

  middle_name: string | null;
  // Nullable on purpose — many patients do not supply one, and a required field
  // would only collect junk.

  sex: "M" | "F";
  // NOT NULL. CHECK in ('M','F'). Male/Female only, matching how DOH and WHO STH
  // surveillance data is stratified.

  birthdate: string;
  // NOT NULL Postgres `date`. Birthdate, not age: age is recomputed per encounter
  // from this, so a record does not silently go stale as time passes.

  psgc_barangay_code: string;
  // NOT NULL. Canonical zero-padded 10-digit PSGC ('0102801001'), CHECK
  // `^[0-9]{10}$`. Moved here from `sessions` — the barangay belongs to the
  // patient, does not change per smear, and is the unit surveillance aggregates
  // on. This is the key `barangay_prevalence()` groups by and the key the
  // choropleth joins against PSGC boundary GeoJSON.

  created_by: UUID;
  // NOT NULL FK -> profiles(id). Provenance only — it grants no visibility.
  // Access resolves through `patient_users`.

  created_at: TimestampTZ;
  updated_at: TimestampTZ;
  // Both NOT NULL, default `now()`.
}

/**
 * The patient <-> user join. **This is what patient visibility resolves
 * through**, not `patients.created_by`.
 *
 * A patient links to many users, so an admin can grant a second medtech access
 * by inserting a row here. The creator's own row is written by the
 * `on_patient_created` trigger rather than by the client: the `patients` SELECT
 * policy reads this table, so without the row the inserting medtech cannot read
 * back the patient they just created.
 *
 * Room mirror:
 * - `PatientUserEntity.kt`
 */
export interface PatientUser {
  patient_id: UUID;
  user_id: UUID;
  // Composite PK. Both FKs, both ON DELETE CASCADE.

  linked_at: TimestampTZ;
  // NOT NULL. Default `now()`.
}

/**
 * One fecal smear, owned by a patient and captured by a user.
 *
 * Supabase migrations:
 * - `0001_init.sql` (patient-based consolidation): creates `sessions` with
 *   `patient_id`, `label` and the two indexes. Three columns present in the
 *   legacy-dev history are deliberately absent — see the interface below.
 *
 * Room mirror:
 * - `SessionEntity.kt`
 */
export interface Session {
  id: UUID;
  // Supabase PK. Room column: `session_id` PK.

  user_id: UUID;
  // Supabase NOT NULL FK -> profiles(id). Room allows null so an offline or
  // pre-auth session can exist before ownership is known.

  patient_id: UUID;
  // NOT NULL FK -> patients(id). A session is always created from a patient's
  // session list, so the patient is known at creation and this is never null.

  device_id: string;
  // NOT NULL. Client-generated stable device identifier.

  started_at: TimestampTZ;
  // NOT NULL. Default `now()` in Supabase; epoch millis in Room.

  label: string | null;
  // Nullable human-friendly smear label. Auto-generated as
  // `C.G.-0730600000-001` (initials, the patient's barangay code, then the Nth
  // smear for that patient) and editable thereafter. Cosmetic and deliberately
  // not unique — the session UUID is the real key.

  // ── Deliberately absent, all three ────────────────────────────────────────
  // `notes`     — removed. It was being used as an ad-hoc patient identifier
  //               (`SessionDetailScreen`'s `patientIdOrNote`); the Patient
  //               entity is what replaces it.
  // `ended_at`  — removed. Sessions never end (86d4ab4vm), so nothing wrote it,
  //               and a column with no writer is a trap: `ended_at IS NULL`
  //               silently matches every row while still looking like a filter.
  // `psgc_barangay_code` — moved to `patients`. See `Patient` above.
}

/**
 * Bundled PSGC barangay reference data. **Room-only — there is no Supabase
 * table.** Seeded from an APK asset on first run so the picker works with no
 * cellular signal.
 *
 * Pinned to PSGC 2Q 2026 (42,010 barangays, 18 regions). The Admin Website's
 * boundary GeoJSON must join on this same vintage — see
 * `docs/map/objects/PsgcBarangay.md` and `tools/psgc/README.md`, which record why
 * the earlier 4Q 2023 pin misfiled 1,763 barangays across the Negros Island
 * Region and Sulu reorganisations.
 *
 * Room mirror:
 * - `PsgcBarangayEntity.kt`, Room schema v10.
 */
export interface PsgcBarangay {
  code: string;
  // PK. Canonical zero-padded 10-digit PSGC.

  name: string;
  // NOT NULL barangay name.

  city_muni_code: string;
  city_muni_name: string;
  // NOT NULL. For Manila's 897 barangays this is the chartered city, not the
  // sub-municipality that is their direct PSGC parent.

  province_code: string | null;
  province_name: string | null;
  // Null for the 3,083 barangays in highly urbanised and independent cities:
  // those cities occupy the province slot themselves, so PSGC gives them no
  // province. Not missing data.

  region_code: string;
  region_name: string;
  // NOT NULL.

  search_text: string;
  // NOT NULL pre-lowercased search haystack: barangay, city/municipality and
  // province names plus Manila's sub-municipality. Region names are excluded as
  // boilerplate. Folded in Kotlin, not SQL — `lower()` is ASCII-only and 439
  // names contain 'n' with a tilde.
}

/**
 * A captured microscope frame/sample and its sync metadata.
 *
 * Supabase migrations:
 * - `0001_init.sql` (consolidated): creates `samples` with `captured_at`,
 *   `verified_at`, `storage_path`, `inference_model_version`, `user_note`,
 *   `needs_reannotation`, `is_manual`, and `deleted_at`.
 * - Historical development migrations archived under `legacy-dev/` (`0001` through `0013`).
 *
 * Room mirror:
 * - `SampleEntity.kt`
 *
 * Important differences:
 * - In Supabase, the capture timestamp column is named `captured_at` (timestamptz).
 *   In Room, it is named `timestamp` (epoch millis).
 * - There is no remote `samples.status` or `created_at` column; status is Room/domain-only.
 * - `image_path` is Room-only (local disk path); image bytes in the cloud live in Storage.
 */
export interface Sample {
  id: UUID;
  // Supabase PK. Room column: `sample_id` PK.

  session_id: UUID;
  // Supabase NOT NULL FK -> sessions(id). DELETE CASCADE in Postgres (NO_ACTION in Room per C8).

  user_id: UUID;
  // Supabase NOT NULL FK -> profiles(id). Room allows null for offline/pre-auth capture.

  captured_at: TimestampTZ;
  // Supabase NOT NULL timestamptz. Room column: `timestamp` (epoch millis).

  verified_at: TimestampTZ;
  // Supabase NOT NULL default `now()`. Room column: `verified_at` (epoch millis, 0 = unset).

  storage_path: string;
  // Supabase NOT NULL. Object key in bucket `samples`, convention `{user_id}/{sample_id}.jpg`.
  // Room stores nullable until upload succeeds.

  inference_model_version: string;
  // Supabase NOT NULL. Room default is `'unknown'`.

  user_note: string | null;
  // Nullable per-sample medtech note.

  needs_reannotation: boolean;
  // Supabase NOT NULL default `false`. Flagged when verification indicates follow-up needed.

  is_manual: boolean;
  // Supabase NOT NULL default `false`. Set for manual captures (no AI inference).

  deleted_at: TimestampTZ | null;
  // Nullable timestamptz. Null means live. A verified sample is never hard-deleted (C8) —
  // it is tombstoned here, hiding it from all UI lists, counts, and reports while preserving
  // retraining detections and Storage assets. Unverified frames are hard-deleted on-device.
  // Every query listing or counting live samples must filter `deleted_at is null`.

  // ── Room-only columns (not present in Supabase samples table) ───────────────
  image_path: string;
  // Room-only. Local absolute file path captured by the Android camera.

  device_id: string;
  // Room-only. Supabase keeps device ownership on `sessions.device_id`.

  status: SampleStatus;
  // Room/domain-only sync state machine.

  predictions_json: string | null;
  // Room-only raw inference payload/cache for display and recovery.

  image_width: number | null;
  // Room-only pixel width.

  image_height: number | null;
  // Room-only pixel height.

  // ── Deliberately absent ───────────────────────────────────────────────────
  // `gps_latitude` / `gps_longitude` / `gps_accuracy` — removed. The fix was
  // taken at the microscope, so it recorded where the smear was read, not where
  // the infection came from; plotted, it mapped laboratories. Geospatial
  // mapping keys on `patients.psgc_barangay_code` instead.
}

/**
 * A model- or user-created parasite egg detection attached to a sample.
 *
 * Supabase migrations:
 * - `0001_init.sql` (consolidated): creates `detections` with class, confidence,
 *   nullable bboxes, verdict, expert_class, and `species_touched`.
 * - Historical development migrations archived under `legacy-dev/`.
 *
 * Room mirror:
 * - `DetectionEntity.kt`
 */
export interface Detection {
  id: UUID;
  // Supabase PK. Room column: `detection_id` PK.

  sample_id: UUID;
  // Supabase NOT NULL FK -> samples(id). DELETE CASCADE.

  class_label: string;
  // NOT NULL model-predicted class label.

  confidence: number;
  // NOT NULL model confidence as a floating-point value.

  bbox_x: number | null;
  // Nullable; manual detections may not have a bounding box.

  bbox_y: number | null;
  // Nullable.

  bbox_w: number | null;
  // Nullable.

  bbox_h: number | null;
  // Nullable.

  verdict: DetectionVerdict;
  // Supabase NOT NULL default `CONFIRMED` with uppercase CHECK values.
  // Room stores lowercase domain values and maps them for remote sync.

  expert_class: string | null;
  // Nullable corrected class. Used when verdict is `WRONG_CLASS` or `BOX_INCORRECT`
  // and the medtech corrected the species.

  species_touched: boolean;
  // NOT NULL default `false`. True when the medtech made a deliberate species selection,
  // including re-picking the pre-filled value. False means pre-fill was untouched.
  // Critical for retraining corpus provenance.

  created_at: TimestampTZ;
  // Supabase NOT NULL default `now()`.

  // ── Room-only column ───────────────────────────────────────────────────────
  verified_by_user: boolean;
  // Room-only. Dropped from Supabase in legacy-dev migration 0002.
}

/**
 * One species finding a medtech logged on a single frame, with that species'
 * low-power-field egg count.
 *
 * One field can hold eggs of more than one species, so a frame carries zero or more
 * findings. The count is per species, never a frame total: WHO infection-intensity
 * thresholds are species-specific. Zero rows represents a clean field.
 *
 * Supabase migrations:
 * - `0001_init.sql` (consolidated): creates `sample_species_findings` with nullable `stage`
 *   and partial unique indexes for staged and unstaged findings.
 * - Historical development migrations archived under `legacy-dev/`.
 *
 * Room mirror:
 * - `SampleSpeciesFindingEntity.kt`
 */
export interface SampleSpeciesFinding {
  id: UUID;
  // Supabase PK. Room column: `finding_id` PK, derived deterministically from
  // (sample_id, species) so an edit replaces rather than duplicates.

  sample_id: UUID;
  // NOT NULL FK to `samples.id`, ON DELETE CASCADE.

  species: string;
  // NOT NULL, non-blank. Canonical class name, or free text when outside the dropdown.

  stage: string | null;
  // ALWAYS NULL, and dormant. Reserved hook for future developmental stage classification.
  // Kept nullable; unstaged unique index is in force.

  egg_count: number;
  // NOT NULL, CHECK > 0. Eggs of this species in this one low-power field.
}

/**
 * Persisted session-level report generated from verified local samples.
 *
 * Supabase migrations:
 * - `0001_init.sql` (consolidated): creates `reports` with `pdf_file_path` and `lpf_per_species`.
 * - Historical development migrations archived under `legacy-dev/`.
 *
 * Room mirror:
 * - `ReportEntity.kt`
 */
export interface Report {
  id: UUID;
  // Supabase PK. Room column: `report_id` PK.

  session_id: UUID;
  // Supabase NOT NULL FK -> sessions(id). DELETE CASCADE.

  user_id: UUID;
  // Supabase NOT NULL FK -> profiles(id). DELETE CASCADE.

  report_type: ReportType;
  // NOT NULL. Default `session`. CHECK currently allows only `session`.

  generated_at: TimestampTZ;
  // NOT NULL. Default `now()` in Supabase; epoch millis in Room.

  total_samples: number;
  // NOT NULL. Count of non-deleted verified samples in the session at report time.

  total_eggs_confirmed: number;
  // NOT NULL. Sum of confirmed detections across the smear.

  positive_species: string[];
  // Supabase `text[]` NOT NULL default `{}`. Room stores as `positive_species_json`.

  lpf_per_species: Record<string, { mean: number; min: number; max: number }>;
  // Supabase `jsonb` NOT NULL default `{}`. Room stores as `lpf_per_species_json`.
  // Per-species low-power-field density range (PB-17/18), replacing Kato-Katz EPG.

  csv_file_path: string | null;
  // Nullable local/export path to generated CSV.

  pdf_file_path: string | null;
  // Nullable local/export path to generated PDF. Device-local file or URI.

  created_at: TimestampTZ;
  // Supabase NOT NULL default `now()`. Room column: `created_at` (epoch millis).

  // ── Room-only column ───────────────────────────────────────────────────────
  supabase_status: ReportSyncStatus;
  // Room-only sync state machine. No Supabase column.

  // ── Deliberately absent ───────────────────────────────────────────────────
  // `epg_per_species` — removed. EPG is eggs-per-gram via Kato-Katz; Philippine
  // medtechs use direct smear, so the x24 multiplier was wrong for the method in
  // use. WHO's light/moderate/heavy bands are defined only against EPG and there
  // is no published intensity table for direct smear to rescale them to. The
  // per-species min-max LPF range (`lpf_per_species`) replaces both.
}

/**
 * Phase 2 placeholder for validation/audit events.
 *
 * Supabase migrations: none through `0008`.
 * Room mirror: none.
 *
 * Intended purpose: preserve a traceable record of human validation actions,
 * expert overrides, QA review, and optional model-improvement feedback. The
 * table should not be treated as implemented until a future migration creates
 * it.
 */
export interface ValidationRecord {
  id: UUID;
  // Planned PK.

  sample_id: UUID;
  // Planned FK -> samples(id).

  detection_id: UUID | null;
  // Planned nullable FK -> detections(id) for detection-level review events.

  user_id: UUID;
  // Planned FK -> profiles(id), reviewer/operator.

  action: string;
  // Planned event type, for example confirm/reject/reclassify/rebox.

  old_value: Json | null;
  // Planned before-state payload.

  new_value: Json | null;
  // Planned after-state payload.

  created_at: TimestampTZ;
  // Planned creation timestamp.
}

/**
 * Supabase-managed Storage object backing sample images.
 *
 * Supabase migrations:
 * - No project migration creates `storage.objects`; Supabase owns the physical
 *   table.
 * - `0003_storage_rls.sql` creates policies for the `samples` bucket and
 *   enforces object names under `{auth.uid()}/{sample_id}.jpg` with
 *   `storage.foldername(name)`.
 * - `0009_storage_admin_read.sql`: adds an admin-only SELECT policy using
 *   `public.is_admin(auth.uid())`. Policies are OR'd, so admins can read across
 *   all user folders while writes stay owner-scoped through `0003`.
 *
 * Room mirror: none. `SampleEntity.storage_path` stores the object key after
 * upload.
 *
 * Note: the app should interact through Supabase Storage APIs, not by writing
 * directly to `storage.objects`.
 */
export interface StorageObject {
  id: UUID;
  // Supabase-managed PK.

  bucket_id: "samples";
  // FK -> storage.buckets(id). App policies in `0003` are scoped to `samples`.

  name: string;
  // Object key. App convention and RLS: `{user_id}/{sample_id}.jpg`.

  owner: UUID | null;
  // Supabase-managed auth owner in older Storage schemas.

  owner_id: string | null;
  // Supabase-managed auth owner in newer Storage schemas.

  created_at: TimestampTZ;
  // Supabase-managed creation timestamp.

  updated_at: TimestampTZ;
  // Supabase-managed update timestamp.

  last_accessed_at: TimestampTZ | null;
  // Nullable Supabase-managed access timestamp.

  metadata: Json | null;
  // Nullable object metadata.

  path_tokens: string[] | null;
  // Supabase-managed path tokenization used by Storage helpers/policies.

  version: string | null;
  // Supabase-managed object version when available.

  user_metadata: Json | null;
  // Nullable caller-provided object metadata when supported by the project.
}

// ---------------------------------------------------------------------------
// Relationship matrix
// ---------------------------------------------------------------------------

export type RelationshipMatrix = [
  {
    from: "auth.users";
    cardinality: "1 -> 0..1";
    to: "profiles";
    description: "Each Supabase Auth user receives one profile via `handle_new_user()`; profile deletion cascades from auth user deletion.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "patients";
    description: "A user creates many patients; `patients.created_by` is provenance only and grants no access.";
  },
  {
    from: "patients";
    cardinality: "many <-> many";
    to: "profiles";
    description: "Through `patient_users`. This is what patient visibility resolves through. The creator's row is written by the `on_patient_created` trigger; any further link is an admin action.";
  },
  {
    from: "patients";
    cardinality: "1 -> many";
    to: "sessions";
    description: "A patient owns many smears; `sessions.patient_id` is NOT NULL and is known at creation.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "sessions";
    description: "A user owns many microscopy sessions; `sessions.user_id` is required remotely.";
  },
  {
    from: "sessions";
    cardinality: "1 -> many";
    to: "samples";
    description: "A session contains many captured frames/samples; deleting the session cascades to samples.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "samples";
    description: "A user owns many sample rows directly for RLS and admin queries.";
  },
  {
    from: "samples";
    cardinality: "1 -> many";
    to: "detections";
    description: "A sample can have zero or more detections; deleting the sample cascades to detections.";
  },
  {
    from: "sessions";
    cardinality: "1 -> many";
    to: "reports";
    description: "A session can have generated report snapshots; deleting the session cascades to reports.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "reports";
    description: "A user owns report rows directly for RLS and query filtering.";
  },
  {
    from: "samples";
    cardinality: "1 -> 0..1";
    to: "storage.objects";
    description: "`samples.storage_path` points to one object in bucket `samples`; local-only samples may have no uploaded object yet.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "storage.objects";
    description: "Storage RLS permits users to read/write objects only in their own top-level folder.";
  },
  {
    from: "samples";
    cardinality: "1 -> many";
    to: "validation_records";
    description: "Planned Phase 2 audit trail for sample-level validation events.";
  },
  {
    from: "detections";
    cardinality: "1 -> many";
    to: "validation_records";
    description: "Planned Phase 2 audit trail for detection-level corrections.";
  },
  {
    from: "profiles";
    cardinality: "1 -> many";
    to: "validation_records";
    description: "Planned Phase 2 reviewer/operator ownership for validation events.";
  },
];

// ---------------------------------------------------------------------------
// Cross-source caveats
// ---------------------------------------------------------------------------

/**
 * Confirmed migration-vs-ERD differences:
 *
 * - Remote `samples.status` does not exist. It is Room/domain-only.
 * - Remote `samples.captured_at` (timestamptz) is mirrored as `timestamp` (epoch millis) in Room.
 * - Remote `samples` has no `image_path` or `created_at` column; `image_path` is Room-only.
 * - Remote `reports.lpf_per_species` (jsonb) stores the min–max LPF density range, replacing Kato-Katz EPG.
 * - Remote `detections.verified_by_user` was dropped in migration `0002`; Room
 *   still keeps it locally.
 * - Remote detection verdict values are uppercase. Room/domain values are
 *   lowercase and are mapped before sync.
 * - Remote detection bounding boxes are nullable after migration `0007`.
 * - Remote `samples.storage_path` is NOT NULL; Room keeps it nullable until the
 *   upload succeeds.
 * - Remote `sessions.user_id` is NOT NULL; Room keeps it nullable for local
 *   resilience before auth ownership is attached.
 * - Room `sessions` keeps one Room-only column, `supabase_status`
 *   (pending/synced/sync_failed, `SessionSyncStatus`), which does not exist in
 *   Supabase. Its sibling `claim_exempt` is gone: login is mandatory on first
 *   run, so every row has an owner from the moment it is created and the whole
 *   deferred-claim axis it served has nothing left to do.
 * - Reports are implemented for session reports only; admin/cross-session
 *   report types require a future migration.
 * - Room `psgc_barangays` has no Supabase counterpart at all. It is bundled
 *   reference data for the barangay picker; the surveillance map joins
 *   `patients.psgc_barangay_code` against PSGC boundary GeoJSON instead. The
 *   `barangay_prevalence()` RPC reaches that code through
 *   `sessions -> patients`; the unit of observation is still the session, i.e.
 *   one smear.
 * - PostGIS is deliberately not enabled. The map keys on PSGC, so the
 *   choropleth is a GROUP BY rather than a spatial query.
 */
export type GroundTruthNotes = never;
