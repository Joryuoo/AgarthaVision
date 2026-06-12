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
 * `reports.epg_per_species`. No Postgres enum exists yet.
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
 * A microscopy smear/session owned by a user.
 *
 * Supabase migrations:
 * - `0001_init.sql`: creates `sessions` with user/device/timing/notes fields.
 * - `0004_fix_profiles_rls_recursion.sql`: replaces admin select policy.
 * - `0005_session_label.sql`: adds nullable `label` and
 *   `sessions_user_started_idx`.
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

  device_id: string;
  // NOT NULL. Client-generated stable device identifier.

  started_at: TimestampTZ;
  // NOT NULL. Default `now()` in Supabase; epoch millis in Room.

  ended_at: TimestampTZ | null;
  // Nullable. Set when the session is explicitly ended.

  notes: string | null;
  // Nullable free-form operator notes.

  label: string | null;
  // Nullable human-friendly smear/session label added by migration `0005`.
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

  gps_lat: number | null;
  // Nullable latitude.

  gps_lng: number | null;
  // Nullable longitude.

  gps_accuracy_m: number | null;
  // Nullable accuracy in meters.

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

  is_repeat: boolean;
  // Room/domain-only. Allows UI/reporting to mark repeat captures without
  // changing remote schema in Phase 1.

  predictions_json: string | null;
  // Room-only raw inference payload/cache for local display and recovery.

  image_width: number | null;
  // Room-only captured image width in pixels.

  image_height: number | null;
  // Room-only captured image height in pixels.
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
  // Nullable corrected class. Used when verdict is `WRONG_CLASS`.

  created_at: TimestampTZ;
  // Supabase NOT NULL. Default `now()`.

  verified_by_user: boolean;
  // Room-only after migration `0002` dropped the Supabase column.
}

/**
 * Persisted session-level report generated from verified local samples.
 *
 * Supabase migrations:
 * - `0008_reports.sql`: creates `reports`, indexes, and owner/admin RLS.
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
  // NOT NULL. Default `0`; sum of confirmed detections used for EPG.

  positive_species: string[];
  // Supabase `text[]` NOT NULL default `{}`. Room stores as
  // `positive_species_json`.

  epg_per_species: Json;
  // Supabase `jsonb` NOT NULL default `{}`. Room stores as
  // `epg_per_species_json`.

  csv_file_path: string | null;
  // Nullable local/export path to generated CSV.

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
 * - Reports are implemented for session reports only; admin/cross-session
 *   report types require a future migration.
 */
export type GroundTruthNotes = never;
