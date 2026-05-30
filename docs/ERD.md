# AgarthaVision ERD

This document is the source of truth for the AgarthaVision data model as
implemented in the codebase. It mirrors the structure of the original SDD ERD
(`docs/ERD.pdf`) — unified overview + per-module subsets — but reflects the
current Phase 1 implementation, not the SDD's full aspiration.

Differences from the SDD ERD:
- `DEVICES`, `INFERENCE_REQUESTS`, `EPG_CALCULATIONS`, `REPORT_SAMPLES`, and
  `SYNC_QUEUE` are **not modelled** in the codebase. Their concerns are either
  collapsed into existing tables (sync state on `samples.status`, EPG computed
  on-the-fly from `detections`) or deferred to Phase 2 (DEVICES tied to OTG
  hardware).
- `VALIDATION_RECORDS` is **scoped to Phase 2** but documented here as a
  forward-looking entity so the migration path is visible.
- `REPORTS` is **Phase 1 scope** — session reports only; administrative
  reports deferred.

---

## Unified ERD

The system supports the diagnostic data pipeline across image capture →
inference → human-in-the-loop verification → EPG aggregation → reporting →
cloud sync. Each sample moves through a four-state lifecycle:
**`flagged` → `verified` → `synced`** (with a `sync_failed` branch). The
SDD's richer 7-state lifecycle (Captured → Uploaded → Processed → Pending
Validation → Validated → Queued for Sync → Synced) is collapsed in Phase 1
because the codebase does not separately persist Inference Requests or a
Sync Queue.

### Entities

#### `profiles`  *(Supabase; no Room equivalent)*

**Purpose:** One row per authenticated user, linked to Supabase Auth.

Columns:
- `id` (PK, FK → `auth.users.id`)
- `full_name` (nullable)
- `role` (default `'medtech'`)
- `created_at`

In Room, `user_id` is a free-form string column on `samples` and `sessions`;
there is no local user table.

#### `sessions`  *(Room + Supabase)*

**Purpose:** One row per fecal-smear session (`label` is the smear name).
Roughly equivalent to the SDD `DIAGNOSTIC_SESSIONS` entity.

Columns:
- `session_id` (PK; `id` in Supabase)
- `user_id` (FK → `profiles.id` in Supabase)
- `device_id` (free-form string; no DEVICES table)
- `started_at`
- `ended_at` (nullable) — equivalent to SDD `completed_at`
- `label` (nullable) — codebase addition; smear name
- `notes` (nullable) — codebase addition; in-session observations

#### `samples`  *(Room + Supabase)*

**Purpose:** A captured frame and its verification metadata. A `flagged`
sample is locally cached pending medtech review; only `verified` samples
sync to Supabase. Roughly equivalent to the SDD `SAMPLES` entity.

Columns (Room ∪ Supabase):
- `sample_id` (PK; `id` in Supabase)
- `session_id` (FK → `sessions.session_id`)
- `user_id` (captured_by — FK → `profiles.id` in Supabase)
- `device_id` (free-form string)
- `timestamp` (Room) / `captured_at` (Supabase) — frame capture time
- `verified_at` (default `0` while flagged)
- `image_path` *(Room only)* — local JPEG path
- `storage_path` (nullable until synced) — Supabase Storage key
- `inference_model_version`
- `needs_reannotation` — Q4 "did the model miss eggs?"
- `gps_latitude`, `gps_longitude`, `gps_accuracy` (all nullable)
- `status` — `flagged` / `verified` / `synced` / `sync_failed`
- `user_note` (nullable)
- `is_manual` — `true` for manual captures (no AI prediction)
- `is_repeat` *(Room only)* — workflow flag; excluded from EPG, never synced
- `predictions_json` *(Room only)* — Gson blob of raw predictions while in
  the `flagged` phase; cleared on verify
- `image_width`, `image_height` *(Room only, nullable)*

#### `detections`  *(Room + Supabase)*

**Purpose:** Per-egg detection rows attached to a verified sample. A
detection only exists after the medtech verifies the parent sample; the
row itself is the validation event (see Module 2.2 note on Phase 2
`validation_records`).

Columns:
- `detection_id` (PK; `id` in Supabase)
- `sample_id` (FK → `samples.sample_id`, ON DELETE CASCADE)
- `class_label` — the model's prediction (canonical species name via
  `EggSpecies` enum)
- `confidence`
- `bbox_x`, `bbox_y`, `bbox_w`, `bbox_h` (all nullable; null for manual
  captures)
- `verdict` — `confirmed` / `false_positive` / `wrong_class` / `box_incorrect`
  (encodes SDD's `is_false_positive` + `validation_status`)
- `expert_class` (nullable) — medtech-supplied corrected class when
  `verdict = wrong_class`
- `verified_by_user` *(Room only)*

#### `reports`  *(Room + Supabase — Phase 1)*

**Purpose:** One row per generated session report. Records the aggregate
stats at generation time + a pointer to the local CSV file. Multiple reports
per session are allowed (ordered by `generated_at` descending). Roughly
equivalent to the SDD `REPORTS` entity (session variant only).

Columns:
- `report_id` (PK; `id` in Supabase)
- `session_id` (FK → `sessions.session_id`, ON DELETE CASCADE)
- `user_id` (generated_by — FK → `profiles.id` in Supabase)
- `report_type` — `session` only in Phase 1
- `generated_at`
- `total_samples`
- `total_eggs_confirmed`
- `positive_species` — array/JSON of canonical species names with ≥1
  confirmed egg
- `epg_per_species` — JSON map of canonical species → EPG integer
- `csv_file_path` *(Room)* — local path; nullable if the file was deleted
- `supabase_status` *(Room only)* — `pending` / `synced` / `sync_failed`
- `created_at`

The CSV file itself is **not uploaded** to Supabase Storage; only the
metadata row is mirrored. The medtech regenerates locally to re-share.

#### `validation_records`  *(Phase 2 — not yet implemented)*

**Purpose:** Audit log of every human-in-the-loop validation action.
Captures who/when/what/why for each medtech decision, including sample-level
actions (e.g. Q4 "model missed eggs") and detection-level edits
(reclassification, false-positive marking). Roughly equivalent to the SDD
`VALIDATION_RECORDS` entity.

Phase 1 collapses this concern into the operational tables: the existence
of a verified `samples` row identifies *who* (via `user_id`) and *when*
(via `verified_at`), and the `detections.verdict` + `detections.expert_class`
columns encode *what was decided*. The Phase 1 schema cannot capture
multiple actions over time, rejection reasons, or freeform remarks.

Anticipated columns (Phase 2):
- `validation_id` (PK)
- `sample_id` (FK → `samples.sample_id`)
- `detection_id` (FK → `detections.detection_id`, **nullable** — null for
  sample-level actions)
- `user_id` (validated_by — FK → `profiles.id`)
- `action` — ENUM (e.g. `confirm`, `reject_false_positive`, `reclassify`,
  `mark_repeat`, `flag_missed_eggs`)
- `previous_value` (TEXT)
- `new_value` (TEXT)
- `rejection_reason` (nullable)
- `remarks` (nullable)
- `validated_at`

**Status: deferred to Phase 2.** Listed here so the migration target is
visible.

#### `storage.objects` (Supabase Storage — `samples` bucket)

**Purpose:** Supabase Storage objects for sample JPEGs. Keyed by
`{user_id}/{sample_id}.jpg` in the private `samples` bucket. Owned and
managed by Supabase; the app interacts via `SampleRemoteDataSource`.

Note: `reports` does **not** use Supabase Storage in Phase 1 (decision per
the REPORTS rollout plan — row-only sync).

---

### Relationship matrix

The matrix lists every relationship in the codebase ERD. Cardinality on the
left is the parent side; "Phase 2" relationships are listed for
forward-visibility but not yet enforced.

| From entity | Cardinality | To entity | Description |
|---|---|---|---|
| `profiles` | 1 : N | `sessions` | A user creates many sessions. |
| `profiles` | 1 : N | `samples` | A user captures many samples (via `samples.user_id`). |
| `profiles` | 1 : N | `reports` | A user generates many reports (via `reports.user_id`). |
| `profiles` | 1 : N | `validation_records` *(Phase 2)* | A user performs many validation actions. |
| `sessions` | 1 : N | `samples` | A session contains many samples. |
| `sessions` | 1 : N | `reports` | A session can produce multiple reports (ordered by `generated_at` DESC). |
| `samples` | 1 : N | `detections` | A verified sample has many detections (ON DELETE CASCADE). |
| `samples` | 1 : 1 | `storage.objects` | Each verified sample has at most one JPEG in the `samples` bucket. |
| `samples` | 1 : N | `validation_records` *(Phase 2)* | A sample receives many validation actions. |
| `detections` | 1 : N | `validation_records` *(Phase 2)* | A detection can be validated/re-validated multiple times. |

Relationships from the SDD ERD that are **not** modelled in the codebase
(intentional simplification or Phase 2 scope):

- `DEVICES → sessions` and `DEVICES → samples`: no devices table; `device_id`
  is a free-form string.
- `samples → INFERENCE_REQUESTS`: not modelled; inference metadata is
  denormalized onto `samples.inference_model_version`.
- `samples → EPG_CALCULATIONS`: not modelled; EPG is computed on-the-fly
  from `detections` × `EpgCalculator.MULTIPLIER`.
- `samples → SYNC_QUEUE`: not modelled; sync state is on `samples.status`.
- `reports → REPORT_SAMPLES`: not modelled; the CSV file enumerates the
  included samples, and aggregate stats are stored on the `reports` row
  itself.

---

## Module 1: Image Capture and Payload Encapsulation

### 1.1 Live Image Acquisition

The medtech runs an active session and the camera pipeline (`FrameSampler` +
`InferFrameUseCase`) submits frames to the inference container. Positive
detections create a flagged sample, persisted to Room with
`status = 'flagged'` via `PersistFlaggedFrameUseCase`. Manual captures
follow the same path with `is_manual = true` and `predictions_json = null`.

**Primary entity (written):** `samples` (status `flagged`).

**Supporting entities (read):**
- `profiles` — captured_by FK target (in Supabase). In Room, the user_id is
  a free-form string sourced from `AuthRepository.getCurrentUserId()`.
- `sessions` — `session_id` FK target. The active session is sourced from
  `SessionManager.state`.
- *(SDD only)* `DEVICES` — would be the `device_id` FK target. Not
  implemented; the codebase uses a free-form string.

**Relationships exercised:**

| Relationship | Codebase fulfillment |
|---|---|
| `profiles` 1 : N `samples` | `samples.user_id` set from `AuthRepository`. |
| `sessions` 1 : N `samples` | `samples.session_id` set from `SessionManager`. |
| `samples` 1 : 1 `storage.objects` *(deferred until verify)* | JPEG is written locally via `SampleImageStore.persistJpeg`; not uploaded yet. |

### 1.2 Image Payload Transmission

After the medtech verifies a flagged sample, its status transitions to
`verified` and `SyncSampleUseCase` uploads the JPEG + metadata to Supabase.
Failure transitions to `sync_failed` and is retried by the connectivity
monitor. The SDD's `INFERENCE_REQUESTS` (per-call audit metadata) and
`SYNC_QUEUE` (retry queue) are not modelled — the lifecycle is collapsed
into `samples.status`.

**Primary entity (updated):** `samples` (status `flagged` → `verified` →
`synced`, with `sync_failed` branch).

**Supporting entities (read):**
- `detections` — joined into the sync payload via `DetectionDao`.
- `storage.objects` — populated on successful upload; `samples.storage_path`
  set to the bucket key.
- *(SDD only)* `INFERENCE_REQUESTS`, `SYNC_QUEUE` — not implemented.

**Relationships exercised:**

| Relationship | Codebase fulfillment |
|---|---|
| `samples` 1 : N `detections` | Detection rows uploaded alongside the sample. |
| `samples` 1 : 1 `storage.objects` | JPEG resized to 640×640 @ JPEG 80% and uploaded to `{user_id}/{sample_id}.jpg`. |

---

## Module 2: Verification Dashboard

### 2.1 Diagnostic Result Rendering

The medtech opens a flagged sample in `VerificationSheet`. The UI reads the
sample's predictions (from `samples.predictions_json` for flagged frames, or
from `detections` for already-verified samples on `SampleDetailScreen`), and
displays per-detection metadata: class label, confidence, bounding box. The
EPG card on `SessionDetailScreen` aggregates confirmed detections via
`DetectionDao.getConfirmedEggCountsForSession` × `EpgCalculator.MULTIPLIER`.

The SDD's `INFERENCE_REQUESTS` (model version, preprocessing method, timing)
and `EPG_CALCULATIONS` (durable per-sample EPG with AI vs technician split)
are not modelled. Model version is denormalized onto
`samples.inference_model_version`; EPG is recomputed per query.

**Primary entities (read):** `detections`, `samples`.

**Supporting entities (read):**
- *(SDD only)* `INFERENCE_REQUESTS`, `EPG_CALCULATIONS` — not implemented.

**Relationships exercised:**

| Relationship | Codebase fulfillment |
|---|---|
| `samples` 1 : N `detections` | Verification sheet enumerates detections per sample. |
| `sessions` 1 : N `samples` | EPG card aggregates across all verified samples in a session. |

### 2.2 Human-in-the-Loop Validation

The medtech answers per-detection questions (Q1 egg / Q2 box / Q3 species)
and a sample-level Q4 (model missed eggs). The submit flow inserts
`detections` rows with `verdict` + `expert_class` and updates the parent
`samples` row (`status='verified'`, `needs_reannotation`, `user_note`,
`is_repeat`, gps_*, verified_at).

**Phase 1 audit model:** the existence of a detection row IS the validation
event. `samples.user_id` + `samples.verified_at` identify who and when;
`detections.verdict` + `detections.expert_class` capture what was decided.
This denormalization is intentional and adequate for the single-medtech,
no-re-validation Phase 1 workflow.

**Phase 2 — `validation_records`** introduces an explicit per-action audit
trail with rejection reasons, freeform remarks, multi-action history, and
FK references to both `samples` and `detections` (the latter nullable for
sample-level actions). Not implemented in Phase 1; documented in this ERD
for forward visibility.

**Primary entities (written, Phase 1):** `samples` (UPDATE on verify),
`detections` (INSERT).

**Primary entity (written, Phase 2):** `validation_records` (INSERT per
action).

**Relationships exercised (Phase 1):**

| Relationship | Codebase fulfillment |
|---|---|
| `samples` 1 : N `detections` | Detections inserted at verify time, with verdict + expert_class. |

**Relationships exercised (Phase 2 — anticipated):**

| Relationship | Anticipated fulfillment |
|---|---|
| `samples` 1 : N `validation_records` | Per-action row for sample-level edits (e.g. Q4 missed-eggs). |
| `detections` 1 : N `validation_records` | Per-action row for reclassification, false-positive marking, etc. |
| `profiles` 1 : N `validation_records` | `validated_by` records the actor. |

---

## Module 3: Reporting, Analytics, and Administrative Dashboard

### 3.1 Session-Based User Report Generation

The medtech generates a report from `SessionDetailScreen` via
`GenerateSessionReportUseCase`. The use case loads the session's verified
samples + detections, computes aggregates (`total_samples`,
`total_eggs_confirmed`, `positive_species`, `epg_per_species`), writes a
CSV to `Downloads/`, inserts a `reports` row with `supabase_status='pending'`,
and triggers `SyncReportUseCase` to mirror the row to Supabase. Multiple
reports per session are allowed; the list view orders by `generated_at`
descending.

The CSV body is per-detection (one row per detection; samples with no
detections still emit one row) with a comment-prefixed header block at the
top (session metadata + aggregate stats).

**Primary entities (written):** `reports`.

**Supporting entities (read):**
- `sessions` — header metadata (label, started_at, ended_at).
- `samples` — verified samples in the session.
- `detections` — per-row data + aggregate egg counts.
- *(SDD only)* `REPORT_SAMPLES`, `EPG_CALCULATIONS` — not implemented.
  Included samples are enumerated in the CSV file; EPG is computed inline.

**Relationships exercised:**

| Relationship | Codebase fulfillment |
|---|---|
| `sessions` 1 : N `reports` | Multiple reports per session, ordered by `generated_at` DESC. |
| `profiles` 1 : N `reports` | `reports.user_id` records the generator. |
| `sessions` 1 : N `samples` | Aggregate stats computed over the session's verified samples. |
| `samples` 1 : N `detections` | Per-detection CSV rows + per-species egg counts. |

### 3.2 Administrative Aggregated Report Generation

**Status: deferred — not implemented in Phase 1.**

Per the SDD, this workflow generates a `reports` row with
`report_type='administrative'` spanning all sessions and users for a given
date range, with cross-session aggregates (positivity_rate, species
distribution, severity trends). The Phase 1 `reports` schema does not
include `date_range_start` / `date_range_end` columns — those are part of
the Phase 2 expansion.

When implemented, this module would read across `profiles`, `sessions`,
`samples`, `detections`, and (Phase 2) `validation_records` to produce
system-wide analytics.
