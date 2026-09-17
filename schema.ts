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
 * - `0001_init.sql`: creates `samples`.
 * - `0002_verification_fields.sql`: renames `roboflow_model_version` to
 *   `inference_model_version` and adds `needs_reannotation`.
 * - `0003_storage_rls.sql`: defines Storage path policy for sample images.
 * - `0004_fix_profiles_rls_recursion.sql`: replaces admin select policy.
 * - `0006_sample_is_manual.sql`: adds `is_manual`.
 * - `0013_sample_soft_delete.sql`: adds `deleted_at` and the partial index
 *   `samples_live_session_idx` over live rows.
 *
 * Room mirror:
 * - `SampleEntity.kt`
 *
 * Important ERD conflict:
 * - The legacy ERD names `samples.status`, but no Supabase migration creates a
 *   remote status column. `status` is Room/domain-only.
 */
export interface Sample {
  id: UUID;
  // Supabase PK. Room column: `sample_id` PK.

  session_id: UUID;
  // Supabase NOT NULL FK -> sessions(id). DELETE CASCADE.

  user_id: UUID;
  // Supabase NOT NULL FK -> profiles(id). DELETE CASCADE.

  timestamp: TimestampTZ;
  // Supabase NOT NULL default `now()`; epoch millis in Room.

  image_path: string;
  // NOT NULL. Local file path captured by the Android app. In Supabase this is
  // metadata only; Storage object bytes live under `storage.objects`.

  storage_path: string;
  // Supabase NOT NULL. Object key in bucket `samples`, convention
  // `{user_id}/{sample_id}.jpg`. Room stores nullable until upload succeeds.

  inference_model_version: string | null;
  // Nullable. Renamed from `roboflow_model_version` by migration `0002`.
  // Room default is `unknown`.

  needs_reannotation: boolean;
  // NOT NULL. Default `false`. Set when manual/HITL review requires follow-up.

  verified_at: TimestampTZ | null;
  // Nullable remote timestamp. Room stores epoch millis with `0` as unset.

  // ── Deliberately absent ───────────────────────────────────────────────────
  // `gps_latitude` / `gps_longitude` / `gps_accuracy` — removed. The fix was
  // taken at the microscope, so it recorded where the smear was read, not where
  // the infection came from; plotted, it mapped laboratories. Geospatial
  // mapping keys on `patients.psgc_barangay_code` instead. Nothing ever read
  // these three columns.

  user_note: string | null;
  // Nullable per-sample note.

  is_manual: boolean;
  // NOT NULL. Default `false`. Added by migration `0006`; manual captures use
  // this plus nullable detection boxes.

  created_at: TimestampTZ;
  // Supabase NOT NULL. Default `now()`.

  device_id: string;
  // Room-only. Supabase keeps device ownership at `sessions.device_id`.

  status: SampleStatus;
  // Room/domain-only. No Supabase column.

  predictions_json: string | null;
  // Room-only raw inference payload/cache for local display and recovery.

  image_width: number | null;
  // Room-only captured image width in pixels.

  image_height: number | null;
  // Room-only captured image height in pixels.

  deleted_at: TimestampTZ | null;
  // Nullable after migration `0013_sample_soft_delete.sql`. Null means live. A verified
  // sample is never hard-deleted (C8) — it is tombstoned here, which hides it from every
  // queue, count and report while its detections stay in the retraining corpus and its
  // Storage object stays put. Unverified frames are hard-deleted instead, on-device.
  // EVERY query that lists or counts samples must filter `deleted_at is null`.
}

/**
 * A model- or user-created parasite egg detection attached to a sample.
 *
 * Supabase migrations:
 * - `0001_init.sql`: creates `detections` with class/confidence/bounding box.
 * - `0002_verification_fields.sql`: adds verdict/expert_class, drops remote
 *   `verified_by_user`, and adds `detections_verdict_idx`.
 * - `0004_fix_profiles_rls_recursion.sql`: replaces admin select policy.
 * - `0007_detection_bbox_nullable.sql`: makes `bbox_x`, `bbox_y`, `bbox_w`,
 *   and `bbox_h` nullable for manual detections.
 * - `0012_polyparasitism_findings.sql`: adds `species_touched`, and adds the UPDATE
 *   policy `detections_update_via_sample` that re-syncing an edited sample needs.
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
  // Nullable after migration `0007`; manual detections may not have a box.

  bbox_y: number | null;
  // Nullable after migration `0007`.

  bbox_w: number | null;
  // Nullable after migration `0007`.

  bbox_h: number | null;
  // Nullable after migration `0007`.

  verdict: DetectionVerdict;
  // Supabase NOT NULL default `CONFIRMED` with uppercase CHECK values.
  // Room stores lowercase domain values and maps them for remote sync.

  expert_class: string | null;
  // Nullable corrected class. Used when verdict is `WRONG_CLASS` — and, from
  // `0012_polyparasitism_findings.sql` on, also when verdict is `BOX_INCORRECT` and the
  // medtech corrected the species. An egg with a misplaced box is still an egg and still
  // has to be counted, so the species question is asked whenever the box contains one.
  // `0002_verification_fields.sql` describes the narrower rule; it is applied and not
  // edited (C6), so this is the current one.

  created_at: TimestampTZ;
  // Supabase NOT NULL. Default `now()`.

  verified_by_user: boolean;
  // Room-only after migration `0002` dropped the Supabase column.

  species_touched: boolean;
  // NOT NULL. Default `false`. Added by `0012_polyparasitism_findings.sql`. True when the
  // medtech made a deliberate species selection on this box, including re-picking the
  // value pre-filled from the model. False means the pre-fill was submitted untouched: a
  // non-objection, not a confirmation. Provenance only — `verdict` is unaffected — but it
  // matters because `detections` doubles as the retraining corpus. Deliberately NOT a
  // reuse of the dead `verified_by_user`.
}

/**
 * One species finding a medtech logged on a single frame, with that species'
 * low-power-field egg count.
 *
 * One field can hold eggs of more than one species, and that is normal, so a frame carries
 * zero or more of these. The count is per species, never a frame total: WHO
 * infection-intensity thresholds are species-specific and differ by more than an order of
 * magnitude, so a combined per-field number cannot be graded.
 *
 * Zero rows is a meaningful state — a clean field — which is why `EggSpecies` has no
 * "no egg" member.
 *
 * Supabase migrations:
 * - `0012_polyparasitism_findings.sql`: creates the table, its two partial unique indexes,
 *   and its RLS policies (scoped through the parent sample, like `detections`).
 *
 * Room mirror:
 * - `SampleSpeciesFindingEntity.kt`
 */
export interface SampleSpeciesFinding {
  id: UUID;
  // Supabase PK. Room column: `finding_id` PK, derived deterministically from
  // (sample_id, species) so an edit replaces rather than duplicates.

  sample_id: UUID;
  // NOT NULL. FK to `samples.id`, ON DELETE CASCADE.

  species: string;
  // NOT NULL, non-blank. Canonical class name, or free text when the dropdown does not
  // cover the species. Same convention as `detections.class_label` / `expert_class`.

  stage: string | null;
  // ALWAYS NULL, and dormant. `0012` created it with a CHECK on 'UNFERTILIZED' |
  // 'UNEMBRYONATED' | 'EMBRYONATED' | 'LARVATED' for ticket 86d4a6jwy, which staging then
  // reverted (`9dcfd5d`) and deprioritised — those four values were never checked against
  // literature, and Ascaris could only be tagged UNFERTILIZED, the one stage that is never
  // infective. The column stays because 0012 is applied and frozen (C6); nothing in the app
  // reads or writes it, and `sample_species_findings_unique_unstaged` is the index in force.
  // Reviving the ticket needs a migration widening that CHECK first.

  egg_count: number;
  // NOT NULL, CHECK > 0. Eggs of this species in this one low-power field. A count of zero
  // is the absence of a row, not a row holding zero.
}

/**
 * Persisted session-level report generated from verified local samples.
 *
 * Supabase migrations:
 * - `0008_reports.sql`: creates `reports`, indexes, and owner/admin RLS.
 * - `0011_reports_pdf_and_lpf.sql`: adds `pdf_file_path` (this file's change only; any
 *   LPF columns in that same numbered slot belong to ticket 86d4a6jxw's separate work).
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
  // NOT NULL. Default `0`.

  total_eggs_confirmed: number;
  // NOT NULL. Default `0`; sum of confirmed detections across the smear.

  positive_species: string[];
  // Supabase `text[]` NOT NULL default `{}`. Room stores as
  // `positive_species_json`.

  // ── Deliberately absent ───────────────────────────────────────────────────
  // `epg_per_species` — removed. EPG is eggs-per-gram via Kato-Katz; Philippine
  // medtechs use direct smear, so the x24 multiplier was wrong for the method in
  // use. WHO's light/moderate/heavy bands are defined only against EPG and there
  // is no published intensity table for direct smear to rescale them to, so the
  // infectivity tier goes with it. The per-species min-max LPF range replaces
  // both. Do not add an EPG column back without clinical sign-off.

  csv_file_path: string | null;
  // Nullable local/export path to generated CSV.

  pdf_file_path: string | null;
  // Nullable local/export path to generated PDF. Added by `0011_reports_pdf_and_lpf.sql`.
  // Mirrors csv_file_path: device-local, meaningless to any other client.

  created_at: TimestampTZ;
  // Supabase NOT NULL. Default `now()`.

  supabase_status: ReportSyncStatus;
  // Room-only sync state. No Supabase column.
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
