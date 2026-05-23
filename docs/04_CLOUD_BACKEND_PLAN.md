# AgarthaVision · Cloud Backend Plan (Phase 1 MVP)

> **Phase 1 (this doc):** Supabase (Postgres + Auth + Storage) + Roboflow Hosted Inference. No self-hosted backend.
>
> **Phase 2 (future):** Self-hosted FastAPI + local inference hardware + Philippine-region storage for DPA compliance. Out of scope here — superseded by ADR-002.

The mobile app talks directly to two managed services. There is no FastAPI service to deploy, no Celery worker, no Docker compose, no S3 bucket to provision. The "backend" is configuration.

---

## 1. Tech Stack

| Layer              | Phase 1 (this doc)                                | Phase 2 target                         |
|--------------------|---------------------------------------------------|----------------------------------------|
| Auth               | Supabase Auth (email/password, JWT)               | Self-hosted OIDC or Better Auth        |
| Database           | Supabase Postgres (managed)                       | Self-hosted PostgreSQL 16              |
| Object Storage     | Supabase Storage (S3-compatible buckets)          | Self-hosted MinIO or local NAS         |
| Inference          | Roboflow Hosted Inference (Public workspace)      | Self-hosted YOLO on local GPU          |
| Schema migrations  | Supabase migrations (SQL files in `supabase/`)    | Alembic                                |
| Real-time          | Supabase Realtime (Postgres LISTEN/NOTIFY)        | WebSocket / SSE                        |

See [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) for the rationale, trade-offs, and migration path.

---

## 2. Repository Layout

There is no separate backend repository for Phase 1. Supabase + Roboflow assets live alongside the mobile app:

```
AgarthaVision/
├── app/                    # Android app
├── supabase/               # Supabase project artifacts
│   ├── config.toml         # Local dev config (supabase CLI)
│   ├── migrations/         # SQL migration files
│   │   └── 0001_init.sql   # Initial schema (see §4)
│   ├── functions/          # Edge Functions (Deno) — optional, post-MVP
│   └── seed.sql            # Seed data for local dev
├── roboflow/               # Roboflow project config (read-only reference)
│   └── model.yaml          # Class labels, confidence threshold, model version
└── docs/
```

The Supabase CLI manages `supabase/` locally; production state lives in the Supabase dashboard for the project.

---

## 3. Data Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              Mobile (Android)                                │
│                                                                              │
│  CameraX ImageAnalysis ──┐                                                   │
│                          │ 1 frame / 2s (sampled)                            │
│                          ▼                                                   │
│             ┌─────────────────────────┐         No detection: discard frame  │
│             │  RoboflowClient         │◄────────                             │
│             │  (HTTPS POST /infer)    │                                      │
│             └────────────┬────────────┘                                      │
│                          │ detection JSON (bbox + class + conf)              │
│                          ▼                                                   │
│             ┌─────────────────────────┐                                      │
│             │  Save flagged frame +   │                                      │
│             │  Sample row to Room     │ (status = FLAGGED_PENDING_REVIEW)    │
│             └────────────┬────────────┘                                      │
│                          │                                                   │
│                          ▼                                                   │
│             ┌─────────────────────────┐                                      │
│             │  Sonner: "egg detected" │                                      │
│             │  + thumbnail in UI      │                                      │
│             └────────────┬────────────┘                                      │
│                          │ user taps to verify                               │
│                          ▼                                                   │
│             ┌─────────────────────────┐                                      │
│             │  Verification screen    │ (status → VERIFIED or REJECTED)      │
│             │  (recording stops)      │                                      │
│             └────────────┬────────────┘                                      │
│                          │ on verify                                         │
└──────────────────────────┼───────────────────────────────────────────────────┘
                           │
                           │ supabase-kt SDK
                           ▼
              ┌─────────────────────────┐         ┌─────────────────────────┐
              │  Supabase Storage       │         │  Supabase Postgres      │
              │  bucket: samples        │         │  table:  samples        │
              │  (verified JPEG)        │         │  table:  detections     │
              └─────────────────────────┘         │  table:  sessions       │
                                                  └─────────────────────────┘
```

**Critical property:** Roboflow does not store any image. It receives a frame, returns a detection JSON, and forgets. All persistence is local (Room) or remote (Supabase).

---

## 4. Database Schema (Supabase Postgres)

Schema is defined as SQL migrations in `supabase/migrations/`. Run via `supabase db push` for cloud or `supabase migration up` for local dev.

### 4.1 Initial Migration — `0001_init.sql`

```sql
-- Extensions
create extension if not exists "uuid-ossp";

-- ── profiles ────────────────────────────────────────────────────────────────
-- One row per authenticated user. Linked to auth.users via id.
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text,
    role text not null default 'medtech' check (role in ('medtech', 'admin')),
    created_at timestamptz not null default now()
);

-- ── sessions ────────────────────────────────────────────────────────────────
-- A capture session = one run of the recording UI by one medtech.
create table public.sessions (
    id uuid primary key default uuid_generate_v4(),
    user_id uuid not null references public.profiles(id),
    device_id text not null,                        -- Settings.Secure.ANDROID_ID
    started_at timestamptz not null default now(),
    ended_at timestamptz,
    notes text
);

-- ── samples ─────────────────────────────────────────────────────────────────
-- A sample = a flagged frame the user verified. Pre-verification frames live only on-device.
create table public.samples (
    id uuid primary key default uuid_generate_v4(),
    session_id uuid not null references public.sessions(id) on delete cascade,
    user_id uuid not null references public.profiles(id),
    captured_at timestamptz not null,                -- when the frame was captured on-device
    verified_at timestamptz not null default now(),  -- when synced to cloud
    gps_latitude double precision,
    gps_longitude double precision,
    gps_accuracy real,
    storage_path text not null,                      -- Supabase Storage object key
    roboflow_model_version text not null,
    user_note text
);

-- ── detections ──────────────────────────────────────────────────────────────
-- One detection per flagged egg in a verified sample. A sample can have many.
create table public.detections (
    id uuid primary key default uuid_generate_v4(),
    sample_id uuid not null references public.samples(id) on delete cascade,
    class_label text not null,                       -- e.g. 'ascaris_lumbricoides'
    confidence real not null check (confidence between 0 and 1),
    bbox_x real not null,                            -- normalized 0-1
    bbox_y real not null,
    bbox_w real not null,
    bbox_h real not null,
    verified_by_user boolean not null default true   -- the user accepted this detection
);

-- ── indexes ─────────────────────────────────────────────────────────────────
create index samples_session_idx on public.samples(session_id);
create index samples_user_idx    on public.samples(user_id);
create index detections_sample_idx on public.detections(sample_id);
create index detections_class_idx  on public.detections(class_label);

-- ── RLS policies ────────────────────────────────────────────────────────────
alter table public.profiles   enable row level security;
alter table public.sessions   enable row level security;
alter table public.samples    enable row level security;
alter table public.detections enable row level security;

-- Profile: each user reads their own row; admins can read everyone.
create policy "profiles_select_own"
on public.profiles for select
using ( auth.uid() = id or (select role from public.profiles where id = auth.uid()) = 'admin' );

-- Session: medtech reads + writes own; admin reads all.
create policy "sessions_select_own"
on public.sessions for select
using ( auth.uid() = user_id or (select role from public.profiles where id = auth.uid()) = 'admin' );

create policy "sessions_insert_own"
on public.sessions for insert
with check ( auth.uid() = user_id );

-- Samples: same scoping pattern.
create policy "samples_select_own"
on public.samples for select
using ( auth.uid() = user_id or (select role from public.profiles where id = auth.uid()) = 'admin' );

create policy "samples_insert_own"
on public.samples for insert
with check ( auth.uid() = user_id );

-- Detections: scoped via the parent sample.
create policy "detections_select_via_sample"
on public.detections for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
        and ( s.user_id = auth.uid() or (select role from public.profiles where id = auth.uid()) = 'admin' )
    )
);

create policy "detections_insert_via_sample"
on public.detections for insert
with check (
    exists (
        select 1 from public.samples s
        where s.id = sample_id and s.user_id = auth.uid()
    )
);
```

### 4.2 Storage Bucket

Create a single private bucket `samples` via the Supabase dashboard or CLI:

```bash
supabase storage create samples --public=false
```

Then add an RLS policy on `storage.objects` to scope reads/writes by user. Path convention: `{user_id}/{sample_id}.jpg`.

---

## 5. Roboflow Inference

### 5.1 Account + Workspace

Create a free **Public** workspace at [roboflow.com](https://roboflow.com). Public workspaces give unlimited free hosted inference at the cost of the model + dataset being viewable by anyone with the workspace URL. For the MVP demo window this trade-off is accepted ([ADR-002](adr/002-supabase-and-roboflow-for-mvp.md)).

Required environment variables (mobile-side `BuildConfig`):

| Key                          | Where                                |
|------------------------------|--------------------------------------|
| `ROBOFLOW_API_KEY`           | Roboflow workspace → API Keys        |
| `ROBOFLOW_PROJECT_SLUG`      | URL slug of the model project        |
| `ROBOFLOW_MODEL_VERSION`     | Numeric version (e.g. `3`)           |

### 5.2 Inference Endpoint

Use Roboflow's Hosted Inference HTTP API directly — no SDK needed for a single endpoint.

```
POST https://detect.roboflow.com/{project_slug}/{model_version}
  ?api_key={ROBOFLOW_API_KEY}
  &confidence=40
  &overlap=30
Content-Type: image/jpeg
Body: <raw JPEG bytes>
```

Response (200):

```json
{
  "predictions": [
    {
      "class": "ascaris_lumbricoides",
      "confidence": 0.91,
      "x": 312, "y": 188,
      "width": 64, "height": 48
    }
  ],
  "image": { "width": 640, "height": 640 }
}
```

When `predictions` is empty, the mobile client discards the frame immediately — no toast, no local persistence.

### 5.3 `RoboflowClient` (mobile-side)

Lives in `data/remote/RoboflowClient.kt`. OkHttp + Retrofit.

```kotlin
interface RoboflowApi {
    @POST("{project}/{version}")
    suspend fun infer(
        @Path("project") project: String,
        @Path("version") version: Int,
        @Query("api_key") apiKey: String,
        @Query("confidence") confidence: Int = 40,
        @Query("overlap") overlap: Int = 30,
        @Body image: RequestBody,
    ): Response<RoboflowInferenceDto>
}
```

`RoboflowInferenceDto` mirrors the response shape above. A `RoboflowMapper` translates predictions into local `Detection` domain models.

### 5.4 Sampling and Backpressure

Frame sampling is the mobile app's responsibility (see `03_MOBILE_APP_PLAN.md` §1). Default: **1 frame every 2 seconds.** The shutter does not produce frames directly — `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` does, and the analyzer enforces the 2-second interval.

If a frame upload is in flight when the next interval ticks, **skip** — don't queue. This keeps memory bounded and avoids stale-frame results when the medtech moves the slide.

---

## 6. Auth Flow

Supabase Auth handles registration, login, JWT issuance, and refresh. The mobile app uses [`supabase-kt`](https://github.com/supabase-community/supabase-kt).

### 6.1 Login Screen

First screen on app launch. Email + password (magic links and OAuth deferred to post-MVP).

```kotlin
val client = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth)
    install(Postgrest)
    install(Storage)
}

// Sign in
client.auth.signInWith(Email) {
    email = "medtech@example.ph"
    password = "..."
}
```

The `supabase-kt` Auth plugin persists the session locally and refreshes the JWT automatically. After successful login, navigate to the capture screen.

### 6.2 Session Bootstrap

On every cold start:

1. Read the persisted session from `supabase-kt`'s storage adapter.
2. If valid → navigate to capture screen.
3. If expired but refresh token valid → refresh silently.
4. If no session → show login screen.

### 6.3 Profile Row

Insert a `profiles` row on first sign-in via a Postgres trigger:

```sql
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, role) values (new.id, 'medtech');
    return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();
```

Adding this trigger is part of `0001_init.sql`.

---

## 7. Sync Flow

A verified sample syncs to Supabase via two operations:

1. **Upload the JPEG to Storage** at path `{user_id}/{sample_id}.jpg`.
2. **Insert rows** into `samples` + `detections` referencing that path.

Both are wrapped in a single `SyncSampleUseCase` that runs after the medtech taps Verify on the verification sheet.

```kotlin
suspend fun syncSample(sample: VerifiedSample): Result<Unit> = runCatching {
    val path = "${client.auth.currentUserOrNull()?.id}/${sample.id}.jpg"
    client.storage["samples"].upload(path, sample.imageBytes)
    client.postgrest["samples"].insert(sample.toRow())
    client.postgrest["detections"].insert(sample.detections.map { it.toRow() })
}
```

If the network drops mid-sync, the sample stays in Room with `status = FLAGGED_PENDING_REVIEW` (or `SYNC_FAILED` if the verify step had already completed locally). The next session start triggers a one-shot retry for any pending uploads.

A WorkManager `SyncWorker` is **not** required for Phase 1 — the simplicity of "verify → sync once" makes it unnecessary. WorkManager arrives in Phase 2 if we need offline-first semantics for entire sessions.

---

## 8. Local Development

### 8.1 Supabase CLI

```bash
# macOS / Linux
brew install supabase/tap/supabase

# Initialize the local Supabase stack
cd AgarthaVision
supabase init               # creates supabase/ directory
supabase start              # spins up local Postgres + Auth + Storage on Docker
supabase db push            # apply migrations to local DB
```

Local dev URLs (printed by `supabase start`):

| Service          | URL                                |
|------------------|------------------------------------|
| API              | `http://localhost:54321`           |
| Studio (UI)      | `http://localhost:54323`           |
| Inbucket (email) | `http://localhost:54324`           |

The Android app's `BuildConfig.SUPABASE_URL` in `debug` builds should point to `http://10.0.2.2:54321` (the emulator alias for localhost).

### 8.2 Roboflow

There is no "local Roboflow." Use the hosted endpoint for dev too. Test the endpoint manually before wiring the mobile client:

```bash
curl -X POST \
  "https://detect.roboflow.com/${ROBOFLOW_PROJECT_SLUG}/${ROBOFLOW_MODEL_VERSION}?api_key=${ROBOFLOW_API_KEY}&confidence=40" \
  -H "Content-Type: image/jpeg" \
  --data-binary @sample.jpg
```

### 8.3 Bun Scripts

Add to the existing root `package.json`:

```json
{
  "scripts": {
    "db:start": "supabase start",
    "db:stop": "supabase stop",
    "db:reset": "supabase db reset",
    "db:diff": "supabase db diff --use-migra",
    "db:push": "supabase db push"
  }
}
```

---

## 9. Production Deployment

There is nothing to deploy for Phase 1.

- **Supabase Production:** Create a project at [supabase.com](https://supabase.com), copy the URL + anon key into the mobile app's `release` `BuildConfig`, run `supabase db push --linked` to apply migrations.
- **Roboflow:** Already hosted. The Public workspace is the production endpoint.

Promote a migration from local to production:

```bash
supabase link --project-ref <prod-project-ref>
supabase db push
```

---

## 10. Cost Envelope

Phase 1 is designed to fit free tiers for the MVP demo window.

| Service              | Free tier limit                                                  | Risk                                              |
|----------------------|------------------------------------------------------------------|---------------------------------------------------|
| Supabase Free        | 500 MB Postgres, 1 GB Storage, 50k MAU, 2 GB egress              | Storage fills first — JPEG ~500 KB × 4,000 samples ≈ 2 GB. Resize to 640×640 before upload (~80 KB) buys 25k samples. |
| Roboflow Public      | Unlimited inferences (model is public)                           | Privacy of model + training data. Acceptable for demo window per ADR-002. |
| Roboflow Private Free| ~1,000 inferences / month                                        | Insufficient for continuous mode (see ADR-002 math). |

Verify current limits at [supabase.com/pricing](https://supabase.com/pricing) and [roboflow.com/pricing](https://roboflow.com/pricing) before each demo — both providers restructure tiers periodically.

---

## 11. What This Doc Replaces (Phase 1)

The previous version of this doc described a self-hosted FastAPI + Celery + YOLO + PostgreSQL + MinIO stack. All of that is **deferred to Phase 2** per [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md). For Phase 1:

| Was                                  | Now                                       |
|--------------------------------------|-------------------------------------------|
| FastAPI app at `agarthavision-backend/` | No backend repo. Supabase project + Roboflow workspace. |
| `POST /v1/inference/submit`          | `POST detect.roboflow.com/{project}/{version}` (called directly from mobile) |
| `GET /v1/inference/{id}/status`      | Synchronous — Roboflow returns predictions in the same response |
| `POST /v1/sync/validation`           | `supabase-kt`: `client.postgrest["samples"].insert(...)` |
| Celery workers + Redis               | None. Inference is synchronous per frame. |
| Alembic migrations                   | `supabase/migrations/` SQL files          |
| MinIO / S3                           | Supabase Storage                          |
| JWT issuance                         | Supabase Auth                             |
| DOH-formatted PDF export             | Out of MVP scope — deferred                |

The deferred work isn't lost; it's tracked in [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) as the Phase 2 migration path.
