# AgarthaVision · Cloud Backend Plan (Phase 1 MVP)

> **Phase 1 (this doc):** Supabase (Postgres + Auth + Storage) + a **self-hosted FastAPI inference container** running on a rented GPU droplet. Per [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) and [ADR-003](adr/003-self-hosted-inference-container.md).
>
> **Phase 2 (future):** Owned GPU hardware running the same container + self-hosted Postgres in Philippine region for DPA compliance. Out of scope here.

The mobile app talks directly to two services: a managed Supabase project (managed by DMKuZu) and a self-hosted inference container (built and deployed by DMKuZu, runnable on any Docker-capable GPU host). There is no orchestrator, no Celery worker, no API gateway.

---

## 1. Tech Stack

| Layer              | Phase 1 (this doc)                                | Phase 2 target                         |
|--------------------|---------------------------------------------------|----------------------------------------|
| Auth               | Supabase Auth (email/password, JWT)               | Self-hosted OIDC or Better Auth        |
| Database           | Supabase Postgres (managed)                       | Self-hosted PostgreSQL 16              |
| Object Storage     | Supabase Storage (S3-compatible buckets)          | Self-hosted MinIO or local NAS         |
| Inference          | Self-hosted FastAPI + custom Ultralytics on a rented GPU droplet (default DO MI300X) | Same container on owned GPU hardware |
| Schema migrations  | Supabase migrations (SQL files in `supabase/`)    | Alembic                                |
| Real-time          | Supabase Realtime (Postgres LISTEN/NOTIFY)        | WebSocket / SSE                        |

See [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) for the Supabase decision and
[ADR-003](adr/003-self-hosted-inference-container.md) for the inference-container pivot
(superseded the original Roboflow Hosted Inference choice).

---

## 2. Repository Layout

There is no separate backend repository for Phase 1. Supabase artifacts and the inference container live alongside the mobile app:

```
AgarthaVision/
├── app/                    # Android app
├── supabase/               # Supabase project artifacts
│   └── migrations/         # SQL migration files
│       └── 0001_init.sql   # Initial schema (see §4) — run via web dashboard
├── inference/              # Self-hosted inference container (DMKuZu)
│   ├── Dockerfile          # Base: rocm/pytorch or pytorch/pytorch CUDA runtime
│   ├── requirements.txt    # FastAPI + custom Ultralytics fork
│   ├── server.py           # FastAPI: POST /infer + GET /health
│   ├── weights/            # best.pt (Git LFS) baked into the image at build
│   └── README.md           # Build / push / deploy runbook
└── docs/
```

The Supabase project is managed entirely via the web dashboard (no CLI required for Phase 1).
The inference container image is published to GHCR and pulled onto whatever GPU droplet
DMKuZu spins up for testing or demos.

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
│             │  InferenceApi (Retrofit)│◄────────                             │
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
│             │  Verification sheet     │ (per-box verdict ∈ CONFIRMED /       │
│             │  (recording stops)      │  FALSE_POSITIVE / WRONG_CLASS /      │
│             │  Per ADR-004            │  BOX_INCORRECT — sample persists)    │
│             └────────────┬────────────┘                                      │
│                          │ on submit                                         │
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

**Critical property:** the inference container does not persist any image. It receives a frame, runs inference, returns a detection JSON, and forgets. All persistence is local (Room) or remote (Supabase Storage).

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
-- A sample = a flagged frame the expert submitted, regardless of verdict mix.
-- Per ADR-004, samples persist even when every detection is FALSE_POSITIVE — that's
-- the labeled-false-positive case, not a deletion. Pre-submit frames live only on-device.
create table public.samples (
    id uuid primary key default uuid_generate_v4(),
    session_id uuid not null references public.sessions(id) on delete cascade,
    user_id uuid not null references public.profiles(id),
    captured_at timestamptz not null,                -- when the frame was captured on-device
    verified_at timestamptz not null default now(),  -- when the expert submitted the sheet
    gps_latitude double precision,
    gps_longitude double precision,
    gps_accuracy real,
    storage_path text not null,                      -- Supabase Storage object key
    inference_model_version text not null,           -- 0001 had roboflow_model_version; renamed in 0002 (ADR-004)
    needs_reannotation boolean not null default false, -- set by frame-level Q4 (false negatives) — added in 0002
    user_note text
);

-- ── detections ──────────────────────────────────────────────────────────────
-- One row per model-predicted bounding box on a submitted sample. Each row carries
-- the expert's verdict (added in 0002). A sample can have many.
create table public.detections (
    id uuid primary key default uuid_generate_v4(),
    sample_id uuid not null references public.samples(id) on delete cascade,
    class_label text not null,                       -- the model's predicted class, e.g. 'Ascaris lumbricoides'
    confidence real not null check (confidence between 0 and 1),
    bbox_x real not null,                            -- normalized 0-1
    bbox_y real not null,
    bbox_w real not null,
    bbox_h real not null,
    verdict text not null default 'CONFIRMED'        -- expert's verdict per ADR-004; added in 0002
        check (verdict in ('CONFIRMED','FALSE_POSITIVE','WRONG_CLASS','BOX_INCORRECT')),
    expert_class text,                               -- expert's corrected class when verdict = WRONG_CLASS; added in 0002
    verified_by_user boolean not null default true   -- DEPRECATED as of ADR-004 — read `verdict` instead. Kept for backward compatibility with rows written before 0002.
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

### 4.1.2 Verification Redesign — `0002_verification_fields.sql`

Per [ADR-004](adr/004-verification-as-hitl-correction.md), every flagged sample
persists (including false positives), and each detection carries a per-box expert
verdict. The migration:

- Renames `samples.roboflow_model_version` → `samples.inference_model_version` (the
  column already stored the self-hosted version per ADR-003; only the name was stale).
- Adds `samples.needs_reannotation boolean` (set by frame-level Q4 in the verification sheet).
- Adds `detections.verdict text` with a `CHECK` constraint over `{CONFIRMED, FALSE_POSITIVE, WRONG_CLASS, BOX_INCORRECT}`.
- Adds `detections.expert_class text` (nullable; populated only when `verdict = WRONG_CLASS`).
- Marks `detections.verified_by_user` deprecated via a column comment; backfills any `false` rows to `verdict = FALSE_POSITIVE` for continuity.
- Creates `detections_verdict_idx` so retraining queries by verdict are cheap.

See [`supabase/migrations/0002_verification_fields.sql`](../supabase/migrations/0002_verification_fields.sql).

### 4.2 Storage Bucket

Create a single private bucket `samples` via the Supabase dashboard or CLI:

```bash
supabase storage create samples --public=false
```

RLS policies are defined in [`supabase/migrations/0003_storage_rls.sql`](../supabase/migrations/0003_storage_rls.sql) — apply after bucket creation. Three policies scope INSERT / SELECT / UPDATE to the authenticated user's own folder (`{user_id}/`). No DELETE policy is intentional: samples persist indefinitely per ADR-004.

Path convention: `{user_id}/{sample_id}.jpg`.

---

## 5. Self-Hosted Inference Container

Per [ADR-003](adr/003-self-hosted-inference-container.md), Phase 1 inference runs in a
FastAPI + custom Ultralytics container that DMKuZu builds, publishes to GHCR, and
deploys to a rented GPU droplet for testing and demo windows.

### 5.1 Container Image

The image is built from `inference/Dockerfile` and published to
**`ghcr.io/dmkuzu/agartha-inference:<tag>`** (public).

| Component | Detail |
|-----------|--------|
| Base image | `rocm/pytorch:latest` (AMD MI300X default) or `pytorch/pytorch:2.x-cuda12-cudnn8-runtime` (NVIDIA fallback) |
| Model | Custom Ultralytics fork — YOLOv26 head + EfficientNetV2 backbone. Pinned by commit SHA in `requirements.txt`. |
| Weights | `inference/weights/best.pt`, tracked via Git LFS, **baked into the image at build time** so cold start is just `docker pull` + start (no S3 download). |
| Entry point | `uvicorn server:app --host 0.0.0.0 --port 8000` |

### 5.2 Inference Endpoint

```
POST  http://<droplet-ip>:8000/infer
Authorization: Bearer ${INFERENCE_API_KEY}
Content-Type: image/jpeg
Body: <raw JPEG bytes>
```

Response (200) — single detection:

```json
{
  "predictions": [
    {
      "class": "Hookworm",
      "confidence": 0.938,
      "x": 2620.47, "y": 1693.42,
      "width": 887.17, "height": 701.91
    }
  ],
  "image": { "width": 5184, "height": 3456 }
}
```

Response (200) — multiple detections (cross-class overlap, same frame):

```json
{
  "predictions": [
    {
      "class": "Trichuris trichiura",
      "confidence": 0.8626,
      "x": 2663.69, "y": 1686.46,
      "width": 589.75, "height": 469.69
    },
    {
      "class": "Ascaris lumbricoides",
      "confidence": 0.3369,
      "x": 2663.33, "y": 1689.74,
      "width": 586.93, "height": 476.68
    }
  ],
  "image": { "width": 5184, "height": 3456 }
}
```

**Class labels emitted by the model** (verbatim strings as returned in the `class` field):

| Label | Species |
|---|---|
| `Ascaris lumbricoides` | Roundworm egg (fertilised) |
| `Trichuris trichiura` | Whipworm egg |
| `Hookworm` | Hookworm egg |

These are the only classes in `best.pt` as of 2026-05-25. Any string outside this list
is a forward-incompatibility signal — `InferenceMapper` should treat an unknown class as
`WRONG_CLASS`-candidate and preserve the raw string in `class_label`.

**Coordinates** are in the original image's pixel space (not the model's 640×640 input
space). Use `image.width` / `image.height` to normalise to 0–1 if needed.

**Cross-class overlapping boxes.** YOLO's NMS is class-aware by default — it suppresses
duplicate boxes of the *same* class, but two boxes of *different* classes on the same
physical egg both survive. This is intentional: each becomes a separate `DetectionEntity`
with its own per-box verdict, giving the retraining pipeline labeled evidence of model
confusion. The expert handles them via the stepped Q1/Q2/Q3 flow in VerificationSheet
(the high-confidence box is typically `CONFIRMED`; the cross-class duplicate is marked
`WRONG_CLASS` with the correct species in Q3).

The response shape intentionally matches Roboflow's so the mobile DTO is provider-agnostic.
When `predictions` is empty, the mobile client discards the frame immediately — no toast,
no local persistence.

> **`CONFIDENCE_THRESHOLD` post-filter — already removed.** `inference/server.py` in the
> current codebase has no confidence threshold post-filter. The server returns every
> prediction the model emits; the model's internal objectness threshold still
> short-circuits "nothing here" frames to an empty `predictions[]`. The ADR-004 note
> about a separate "v2 image to remove the filter" is **no longer pending** — the shipped
> `server.py` already reflects this behaviour.

`GET /health` returns 200 OK once the model is loaded — used to verify the droplet is
ready before pointing the mobile app at it. The mobile `NetworkMonitor` (see
[03_MOBILE_APP_PLAN.md §1.9](03_MOBILE_APP_PLAN.md)) polls this endpoint every 10 seconds
while a recording session is active.

### 5.3 `InferenceApi` (mobile-side)

Lives in `data/remote/InferenceApi.kt`. Retrofit + OkHttp. Bearer-token auth is injected
by the OkHttp interceptor wired in `InferenceModule`.

```kotlin
interface InferenceApi {
    @POST("infer")
    suspend fun infer(@Body image: RequestBody): Response<InferenceResponseDto>
}
```

`InferenceResponseDto` (in `data/remote/dto/`) mirrors the response shape above. A
`InferenceMapper` (Sprint 1 work — jojseph) translates predictions into local `Detection`
domain models.

### 5.4 Sampling and Backpressure

Frame sampling is the mobile app's responsibility (see `03_MOBILE_APP_PLAN.md` §1). Default: **1 frame every 2 seconds.** The shutter does not produce frames directly — `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` does, and the analyzer enforces the 2-second interval.

If a frame upload is in flight when the next interval ticks, **skip** — don't queue. This keeps memory bounded and avoids stale-frame results when the medtech moves the slide.

### 5.5 ROCm Validation Gate (DMKuZu)

Before committing the Dockerfile to use `rocm/pytorch:latest`, DMKuZu must validate that
the custom Ultralytics fork loads `best.pt` and runs inference on ROCm. If it doesn't
(a real risk — Ultralytics on ROCm is unreliable for custom heads), switch the default
to `pytorch/pytorch:2.x-cuda12-cudnn8-runtime` and use RunPod A5000 (~$0.69/hr) or
Lambda Labs A10 (~$0.50/hr) instead. The image, server, and mobile client all stay the
same — only the base image and provider change.

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

A submitted sample (per [ADR-004](adr/004-verification-as-hitl-correction.md), every
sample — including ones with all `FALSE_POSITIVE` verdicts) syncs to Supabase via two
operations:

1. **Upload the JPEG to Storage** at path `{user_id}/{sample_id}.jpg`.
2. **Insert rows** into `samples` + `detections` referencing that path. Each
   `detections` row carries its expert `verdict` (and `expert_class` when applicable).

Both are wrapped in a single `SyncSampleUseCase` that runs after the medtech submits the verification sheet.

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

### 8.1 Supabase (web dashboard — no CLI required)

No local Supabase stack for Phase 1. Both debug and release builds hit the cloud project.
See [01_ENVIRONMENT_SETUP.md §A.15](01_ENVIRONMENT_SETUP.md) for the full setup steps.

Short version:
1. Create a dev project at [supabase.com](https://supabase.com).
2. Run `supabase/migrations/0001_init.sql` via **SQL Editor** in the dashboard.
3. Copy **Project URL** + **Publishable (anon) key** from **Settings → API** into `local.properties`.
4. `BuildConfig.SUPABASE_URL` (debug) reads `SUPABASE_URL_DEV` from `local.properties`.

Schema changes: write a new `supabase/migrations/000N_*.sql` file, commit it to git, then run it manually in the SQL Editor.

### 8.2 Inference container

For development, DMKuZu spins up a GPU droplet, pulls the latest image from GHCR, and
shares the IP + Bearer key via team chat. The mobile app reads these from `local.properties`.

Smoke-test the endpoint manually before wiring the mobile client:

```bash
# Health check
curl -i http://<droplet-ip>:8000/health

# One inference against a known-positive JPEG
curl -X POST "http://<droplet-ip>:8000/infer" \
  -H "Authorization: Bearer ${INFERENCE_API_KEY}" \
  -H "Content-Type: image/jpeg" \
  --data-binary @sample.jpg
```

When DMKuZu is done testing, **destroy the droplet** — GPU droplets bill by the second.
The image stays on GHCR; the next session is one `docker pull` away.

### 8.3 Bun Scripts

No CLI-based database scripts for Phase 1 — schema changes go through the web dashboard.
The only relevant script is the standard build/test cycle already in `package.json`.

---

## 9. Production Deployment

There is nothing to deploy for Phase 1.

- **Supabase Production:** Create a project at [supabase.com](https://supabase.com), copy the URL + anon key into the mobile app's `release` `BuildConfig`, run `supabase db push --linked` to apply migrations.
- **Inference container:** spin up the demo droplet (DigitalOcean MI300X or NVIDIA fallback), `docker pull` the GHCR image, `docker run --gpus all -p 8000:8000 -e INFERENCE_API_KEY=...` and share the IP + key via team chat. Tear down after the demo.

Promote a migration from dev to prod: paste the new `supabase/migrations/000N_*.sql` file
into the prod project's SQL editor and run it.

---

## 10. Cost Envelope

Phase 1 is designed to keep cash burn near zero between demos.

| Service              | Cost                                                             | Risk / Mitigation                                |
|----------------------|------------------------------------------------------------------|--------------------------------------------------|
| Supabase Free        | 500 MB Postgres, 1 GB Storage, 50k MAU, 2 GB egress              | Storage fills first — JPEG ~500 KB × 4,000 samples ≈ 2 GB. Resize to 640×640 before upload (~80 KB) buys 25k samples. |
| GHCR (public)        | Free for public images                                           | Model weights are world-readable. Acceptable per ADR-003 (same trade-off as a Roboflow Public workspace). |
| GPU droplet (testing)| DigitalOcean MI300X ≈ $1.99/hr. NVIDIA fallback: RunPod A5000 ≈ $0.69/hr, Lambda A10 ≈ $0.50/hr. | Bills by the second — **destroy after every test/demo session.** A 30-min demo at $1.99/hr costs $1. Leaving the droplet running 24h costs $48. |
| Bandwidth (mobile→droplet) | Included in droplet egress allowance for any provider     | Negligible at 1 frame / 2s × ~80 KB JPEG ≈ 144 MB per hour of recording. |

Verify pricing at [supabase.com/pricing](https://supabase.com/pricing) and your chosen
GPU provider before each demo — pricing tiers shift periodically.

---

## 11. What This Doc Replaces (Phase 1)

The original SDD described a self-hosted FastAPI + Celery + YOLO + PostgreSQL + MinIO stack
for the full team to deliver. Most of it is **deferred to Phase 2** per
[ADR-002](adr/002-supabase-and-roboflow-for-mvp.md). The inference half then pivoted away
from Roboflow per [ADR-003](adr/003-self-hosted-inference-container.md). The net Phase 1 surface:

| Was (original SDD)                      | Now (Phase 1)                                                         |
|-----------------------------------------|-----------------------------------------------------------------------|
| FastAPI app at `agarthavision-backend/` | A thin inference-only FastAPI in `inference/` (DMKuZu) + Supabase     |
| `POST /v1/inference/submit`             | `POST <droplet>:8000/infer` (called directly from mobile)             |
| `GET /v1/inference/{id}/status`         | Synchronous — inference returns predictions in the same response      |
| `POST /v1/sync/validation`              | `supabase-kt`: `client.postgrest["samples"].insert(...)`              |
| Celery workers + Redis                  | None. Inference is synchronous per frame.                             |
| Alembic migrations                      | `supabase/migrations/` SQL files run via web dashboard                |
| MinIO / S3                              | Supabase Storage                                                      |
| JWT issuance                            | Supabase Auth                                                         |
| DOH-formatted PDF export                | Out of MVP scope — deferred                                           |

The deferred work isn't lost; it's tracked in
[ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) as the Phase 2 migration path.
