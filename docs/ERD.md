# AgarthaVision ERD

This document captures the current schema implemented in code for both the local
Room database and the Supabase (Postgres) backend. It reflects the codebase and
Supabase migrations as of the current repository state.

---

## Room (Local) Schema

### sessions
**Purpose:** One row per smear session recorded on-device.

Columns:
- session_id (PK)
- user_id (nullable)
- device_id
- started_at
- ended_at (nullable)
- notes (nullable)
- label (nullable)

### samples
**Purpose:** A captured frame that has been flagged and then verified (or is
currently flagged) on-device, with metadata and local image paths.

Columns:
- sample_id (PK)
- session_id
- user_id
- device_id
- timestamp
- verified_at (default 0 for flagged samples)
- image_path (local file path)
- storage_path (nullable)
- inference_model_version
- needs_reannotation
- gps_latitude (nullable)
- gps_longitude (nullable)
- gps_accuracy (nullable)
- status (e.g. flagged, verified, sync_failed)
- user_note (nullable)
- is_manual
- is_repeat (Room-only)
- predictions_json (nullable, JSON blob for flagged samples)
- image_width (nullable)
- image_height (nullable)

### detections
**Purpose:** Per-egg detection data associated with a verified sample.

Columns:
- detection_id (PK)
- sample_id (FK -> samples.sample_id, ON DELETE CASCADE)
- class_label
- confidence
- bbox_x (nullable)
- bbox_y (nullable)
- bbox_w (nullable)
- bbox_h (nullable)
- verdict
- expert_class (nullable)
- verified_by_user

### Room relationships
- sessions.session_id -> samples.session_id (logical relationship; no Room FK
  constraint defined in code).
- samples.sample_id -> detections.sample_id (enforced FK with cascade delete).
- user_id columns in Room are string identifiers for the authenticated user, but
  are not enforced as FKs locally.

---

## Supabase (Postgres) Schema

### profiles
**Purpose:** One row per authenticated user, linked to auth.users.

Columns:
- id (PK, FK -> auth.users.id)
- full_name (nullable)
- role (default 'medtech')
- created_at

### sessions
**Purpose:** One row per smear session uploaded to Supabase.

Columns:
- id (PK)
- user_id (FK -> profiles.id)
- device_id
- started_at
- ended_at (nullable)
- notes (nullable)
- label (nullable)

### samples
**Purpose:** Verified samples uploaded to Supabase with storage references.

Columns:
- id (PK)
- session_id (FK -> sessions.id)
- user_id (FK -> profiles.id)
- captured_at
- verified_at
- gps_latitude (nullable)
- gps_longitude (nullable)
- gps_accuracy (nullable)
- storage_path
- inference_model_version
- user_note (nullable)
- needs_reannotation
- is_manual

### detections
**Purpose:** Per-egg detection data associated with a sample.

Columns:
- id (PK)
- sample_id (FK -> samples.id)
- class_label
- confidence
- bbox_x (nullable)
- bbox_y (nullable)
- bbox_w (nullable)
- bbox_h (nullable)
- verdict
- expert_class (nullable)

### storage.objects (samples bucket)
**Purpose:** Supabase Storage objects for sample images. Objects are stored under
`{user_id}/{sample_id}.jpg` in the private `samples` bucket.

Columns (storage.objects is managed by Supabase; key fields used by the app):
- bucket_id
- name (path key)
- owner
- created_at

### Supabase relationships
- profiles.id -> sessions.user_id
- profiles.id -> samples.user_id
- sessions.id -> samples.session_id
- samples.id -> detections.sample_id
- auth.users.id -> profiles.id
- storage.objects.name uses samples storage_path convention, keyed by user_id and sample_id

---

## Cross-Store Notes

- Room keeps additional workflow-only fields (`status`, `is_repeat`,
  `predictions_json`, local `image_path`, and image dimensions) that do not
  exist in Supabase.
- Supabase storage is the source of truth for uploaded sample images, while Room
  keeps the on-device file path.
- Room allows flagged samples to persist locally even before verification; only
  verified samples are uploaded to Supabase.
