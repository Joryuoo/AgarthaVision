# Sprint 1 Progress Report

**Last updated:** 2026-05-25
**Spec authority:** [../03_MOBILE_APP_PLAN.md](../03_MOBILE_APP_PLAN.md) (Phase 1 MVP)
**Architecture authority:** [../adr/002-supabase-and-roboflow-for-mvp.md](../adr/002-supabase-and-roboflow-for-mvp.md) · [../adr/003-self-hosted-inference-container.md](../adr/003-self-hosted-inference-container.md) · [../adr/004-verification-as-hitl-correction.md](../adr/004-verification-as-hitl-correction.md) · [../02_PROJECT_ARCHITECTURE.md](../02_PROJECT_ARCHITECTURE.md)

> **2026-05-25 Joryuoo Sprint 1 updates.** `feat(capture): wire recording sessions to
> Supabase` completed the `SessionManager`/`CaptureViewModel` session-control path:
> sessions are inserted locally and into Supabase on start, closed locally and remotely
> on stop, and tied to the authenticated Supabase user. `feat(sprint-1.8): add Supabase
> sample sync use case` completed the sync-side upload path: verified sample JPEGs are
> resized to 640x640, uploaded to private Supabase Storage, inserted into `samples` and
> `detections`, and the local Room status is updated to `SYNCED`/`SYNC_FAILED`. Room is
> now schema version 3 with ADR-004 sample/detection metadata fields exported in
> `app/schemas/.../3.json`.

> **2026-05-25 verification redesign — ADR-004.** The current spec's `Reject = delete`
> behavior was a drift from the team's actual intent. Per ADR-004, all flagged samples
> persist; each detection carries a per-box `verdict ∈ {CONFIRMED, FALSE_POSITIVE, WRONG_CLASS, BOX_INCORRECT}`;
> VerificationSheet becomes a stepped 3-question dropdown flow; the server drops its
> `CONFIDENCE_THRESHOLD` post-filter in the upcoming `v2` image. Schema migration
> `0002_verification_fields.sql` is written but **not yet applied** — pending team review.

> **2026-05-25 inference container live on GHCR.** DMKuZu built and pushed
> `ghcr.io/dmkuzu/agartha-inference:v1`. ROCm validated on DigitalOcean MI300X; one
> dev droplet was provisioned, smoke-tested (`/health` + `/infer` against an Ascaris
> JPEG, response = 0.96 confidence), and destroyed. The mobile team can now connect.
> Demo workflow documented in `inference/README.md` + `inference/test.md`.

> **2026-05-24 architectural pivot — ADR-003.** Inference moved off **Roboflow Hosted**
> and onto a **self-hosted FastAPI container** running on a rented GPU droplet
> (default DigitalOcean MI300X, NVIDIA fallback). The custom Ultralytics fork
> (YOLOv26 + EfficientNetV2) is incompatible with Roboflow's hosted runtime, so the
> mobile data layer now talks to `InferenceApi` (Retrofit) instead of `RoboflowApi`.
> Response shape is unchanged. See ADR-003.

> **2026-05-24 scaffold-readiness PR landed.** DMKuZu shipped one large PR that demolished
> the snapshot-capture architecture, completed Sprint 0, and aligned all docs with the
> inference pivot. Teammates can now pick up parallel Sprint 1 tracks (Login, FrameSampler,
> CaptureScreen, VerificationSheet, SyncSampleUseCase) without stepping on each other.

---

## Sprint 0 — Scaffold and Foundation

| §   | Deliverable                               | Status | Notes |
|-----|-------------------------------------------|--------|-------|
| 0.1 | Project / Gradle structure                | ✅     | `bun run build` expected to pass |
| 0.2 | KomoUI theme (`Color`, `Radius`, `Type`, `Theme`) | ✅ | All four files present in `ui/theme/` |
| 0.3 | Supabase + Inference `BuildConfig` fields | ✅     | `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `INFERENCE_URL`, `INFERENCE_API_KEY` wired in `app/build.gradle.kts`. `supabase-kt` BOM + `ktor-client-okhttp` added to `libs.versions.toml`. |
| 0.4 | Navigation shell (`NavHost` + `Login`, `Capture`, `Records`, `Settings` routes) | ✅ | `AgarthaNavGraph` is a real `NavHost`. Each route renders a placeholder until the assigned teammate implements the real screen. |
| 0.5 | Room database shell — `SampleEntity`, `DetectionEntity`, `SessionEntity` + DAOs | ✅ | All three entities present at `data/local/entity/`. DAOs at `data/local/dao/`. `AgarthaDatabase` is now version 3 with ADR-004 sync/verification fields and `fallbackToDestructiveMigration(dropAllTables = true)`. |
| 0.6 | Hilt setup (`@HiltAndroidApp`, `DatabaseModule`, `SupabaseModule`, `InferenceModule`) | ✅ | All four modules in place. `SupabaseModule` provides a `SupabaseClient`; `InferenceModule` provides a Retrofit `InferenceApi`. |

### Sprint 0 Acceptance Criteria

| Criterion | Status |
|---|---|
| App launches and shows Login screen placeholder | ✅ |
| `BuildConfig.SUPABASE_URL` and `BuildConfig.INFERENCE_URL` are wired | ✅ |
| `bun run build` and `bun run lint` pass | ⚠️ pending verification on teammate machines |
| Room schema `3.json` exported | ✅ `app/schemas/com.agarthavision.core.database.AgarthaDatabase/3.json` exists |

---

## Sprint 1 — Parallel tracks (open)

Sprint 1 implementation begins now. Each track depends only on Phase 0 (the scaffold)
and is independent of DMKuZu's inference-container work in Phase 1.

| §    | Deliverable                                                          | Owner | Status |
|------|----------------------------------------------------------------------|-------|--------|
| 1.0  | `LoginScreen` + `LoginViewModel` (Supabase Auth)                     | Beansman | ⬜ open |
| 1.1  | Wire `SessionManager` into `CaptureViewModel`; add Supabase `sessions` row insert | Joryuoo + IgnisFrostburn | ✅ done 2026-05-25 — `SessionManager` writes `sessions` locally/remotely; `CaptureViewModel` exposes start/stop recording state |
| 1.2  | `CaptureScreen` UI on top of `MicroscopyViewport` + `bindAnalysis()` | IgnisFrostburn | ✅ done 2026-05-25 — `CaptureScreen` hosts `MicroscopyViewport` + session controls; wired to `CaptureViewModel` |
| 1.3  | `FrameSampler` (`ImageAnalysis.Analyzer`, 2s throttle)               | IgnisFrostburn | ✅ done 2026-05-25 — `FrameSampler` throttles to 2s and dispatches to `InferFrameUseCase` |
| 1.4  | `InferFrameUseCase` (calls `InferenceApi.infer`, handles 4xx/5xx)    | IgnisFrostburn | ✅ done 2026-05-25 — `InferFrameUseCase` calls `InferenceApi` and flags frames |
| 1.5  | Detection toasts (observe `FlaggedFrameStore.state`)                 | Beansman + IgnisFrostburn | ✅ done 2026-05-25 — Detection toasts (Sonner) observe `FlaggedFrameStore` and show in `CaptureScreen` |
| 1.6  | `VerificationSheet` + `VerificationViewModel`                        | Beansman | ⬜ open |
| 1.7  | `FlaggedFrame` model + `FlaggedFrameStore` (in-memory + disk cache)  | IgnisFrostburn | ✅ done 2026-05-25 — `FlaggedFrame` model and `FlaggedFrameStore` (in-memory) implemented |
| 1.8  | `SyncSampleUseCase` (Supabase Storage upload + Postgres insert)      | Joryuoo | ✅ done 2026-05-25 — resizes JPEG to 640x640, uploads to `samples` bucket, inserts sample/detection rows, updates local sync status |
| 1.9a | ADR-004 + amend `03_MOBILE_APP_PLAN.md` §1.6/§1.7/§1.9 + amend `04_CLOUD_BACKEND_PLAN.md` + write `0002_verification_fields.sql` | DMKuZu | ✅ docs written 2026-05-25 |
| 1.9b | `NetworkMonitor` (active `/health` probe every 10s) + `InferenceConnectionException` + Retrofit→domain error mapper | DMKuZu (after 1.9a accepted by team) | ✅ done 2026-05-25 |
| 1.10 | Metadata binding per VERIFIED sample (`user_id`, `verified_at`, etc.) | Joryuoo (rolls into SyncSampleUseCase) | ⚠️ partial — sync row mapping supports `user_id`, `captured_at`, `verified_at`, GPS fields, `storage_path`, `inference_model_version`, and `needs_reannotation`; final GPS-at-verify/source data wiring depends on `VerificationSheet`/`FlaggedFrameStore` |

### Sprint 1 Acceptance Criteria

| Criterion | Status |
|---|---|
| Cold start → Login screen | ✅ (placeholder; real Login pending) |
| Successful login → Capture screen | ⬜ real Login/Auth flow still pending |
| Recording session activates frame sampling at 2s intervals | ✅ |
| Egg detection → Sonner toast within ~3s | ✅ (logic implemented) |
| Tapping toast opens VerificationSheet and stops recording | ⬜ |
| Submit (any verdict mix) → Room `VERIFIED` row + per-detection verdicts + Supabase `SYNCED` | ⚠️ sync backend exists (`SyncSampleUseCase` + ADR-004 Room fields), but end-to-end submit is blocked by `VerificationSheet` and `FlaggedFrameStore` |
| All-FALSE_POSITIVE submit still persists (no deletion) | ⚠️ supported by sync schema/row mapping, but not yet exercised end-to-end until VerificationSheet exists |
| Connection loss → recording stops, banner shown, preview stays live, already-flagged frames still verifiable | ⬜ |
| GPS populated on verify if granted; null if denied | ⬜ |

---

## DMKuZu — inference container track (Phase 1 of the active plan)

Runs in parallel with the teammate tracks. Mobile code reads `INFERENCE_URL` from
`local.properties`; the value is populated once the droplet is up.

| Step | Status |
|------|--------|
| ROCm validation of custom Ultralytics fork on MI300X (pivot to NVIDIA if it fails) | ✅ validated 2026-05-25 on DigitalOcean MI300X |
| `inference/Dockerfile` + `inference/server.py` + `inference/requirements.txt` | ✅ |
| `inference/weights/best.pt` (Tabada hands off; DMKuZu via Git LFS) | ✅ in repo via Git LFS |
| Build + push to `ghcr.io/dmkuzu/agartha-inference:v1` | ✅ pushed 2026-05-25 |
| Provision GPU droplet, run container, smoke-test `/infer` + `/health`, destroy | ✅ dev droplet validated and destroyed 2026-05-25 |
| `inference/README.md` runbook + `inference/test.md` smoke-test reference | ✅ |
| `v2` image: remove `CONFIDENCE_THRESHOLD` post-filter (per ADR-004) | ✅ already absent in shipped `server.py` — no separate v2 needed |

---

## Outside-codebase work — done

| Task | Owner | Status |
|------|-------|--------|
| Supabase dev project provisioned | DMKuZu | ✅ |
| `supabase/migrations/0001_init.sql` applied to dev project | DMKuZu | ✅ |
| `SUPABASE_URL_DEV` + `SUPABASE_ANON_KEY_DEV` shared with team | DMKuZu | ✅ |
| Supabase prod project provisioned | DMKuZu / Joryuoo | ⬜ open (defer until demo) |
| Test accounts provisioned via Supabase Auth dashboard | Joryuoo | ⬜ open |
| Roboflow workspace + model import | ~~Tabada~~ | ❌ obsolete (per ADR-003) |

---

## Legacy code already removed (Phase 0 PR)

Deleted in the scaffold-readiness PR. Listed here as historical record so nobody tries
to re-add them:

- `domain/usecase/capture/CaptureSampleUseCase.kt` (snapshot-based; replaced by `InferFrameUseCase`)
- `ui/components/ShutterButton.kt` (no shutter in continuous-capture UX)
- `ui/components/MicroscopyViewport.kt` — rewritten to keep only the viewport; `MicroscopyScreen` deleted
- `core/location/LocationProvider.kt` + `DefaultLocationProvider.kt` (duplicate; replaced by `domain/repository/LocationProvider.kt` + `core/location/FusedLocationProvider.kt`)
- `core/di/CoreModule.kt` (bound the deleted interface)
- `data/remote/api.kt` + `dto.kt` + `mapper.kt` (empty stubs; replaced by `InferenceApi.kt` and `data/remote/dto/InferenceResponseDto.kt`)
- `worker/SyncWorker.kt` + `worker/ReportGenerationWorker.kt` (Phase 2 scope)
- `core/database/Database.kt` (renamed to `AgarthaDatabase.kt`)
- `data/local/entity.kt` / `dao.kt` / `mapper.kt` (split into per-class files in `entity/`, `dao/`, `mapper/` subdirectories)
- 18-state `SampleStatus` enum (replaced by 4-state Phase 1 enum)

---

## Summary

Phase 0 (scaffold) is done. Sprint 1 implementation is open and unblocked across five
parallel tracks. DMKuZu is now working the inference container in parallel — mobile
code is already wired against the `InferenceApi` interface, so it can be developed and
unit-tested without waiting for the live endpoint.
