# Features

What ships today, verified by opening the screen, the ViewModel, and the use case behind it.
Anything not on the "ships" list is on the ghost list at the bottom — do not present a ghost
as working.

## Ships

### Auth and identity
- **Email/password sign-in** through Supabase Auth. No sign-up flow — accounts are
  provisioned in the Supabase dashboard. `ui/login/LoginScreen.kt`,
  `domain/usecase/auth/SignInUseCase.kt`, `data/repository/SupabaseAuthRepository.kt:54-60`.
- **Offline-first entry.** The app opens on the Dashboard, not Login
  (`ui/navigation/AgarthaNavGraph.kt:120`). Login is an explicit destination reached from the
  account banner, and is required only to enable cloud upload.
- **Cached local identity.** The signed-in user id, email, and display name are cached in
  DataStore so offline work is attributed to the last medtech
  (`data/repository/SupabaseAuthRepository.kt:74-86`).
- **Sign-out** revokes the Supabase token *and* clears the cached identity, returning the
  device to the never-signed-in state. `domain/usecase/auth/SignOutUseCase.kt`,
  `data/repository/SupabaseAuthRepository.kt:64-71`.
- **Deferred claim.** Work recorded while signed out is owned by nobody
  (`user_id = NULL`) and is claimed at the next login, cascading sessions → samples → reports
  (`domain/usecase/auth/ClaimLocalDataUseCase.kt:33-53`).

### Sessions
- **Session = one fecal smear.** Start with a label, optional notes; only an explicit End
  Session writes `ended_at`. `core/session/SessionManager.kt:55-73`, `:117-140`.
- **Session picker and resume** for a still-open smear (`core/session/SessionManager.kt:79-89`).
- **Per-session "link to account" opt-out** (`claim_exempt`), excluding a session from the
  login claim. `domain/usecase/sessions/SetSessionClaimExemptUseCase.kt`,
  `data/local/entity/SessionEntity.kt:46-52`.

### Capture and inference
- **Continuous microscope feed analysis.** CameraX `ImageAnalysis` only — there is no
  `ImageCapture` use case (`core/camera/CameraManager.kt:43-136`).
- **2-second frame sampling** with in-flight skip rather than queueing
  (`core/camera/FrameSampler.kt:36`, `:66-68`).
- **Synchronous inference** against the self-hosted FastAPI container, called through
  `RemoteInferenceEngine` behind the `InferenceEngine` interface
  (`data/remote/InferenceApi.kt:19-25`, `data/inference/RemoteInferenceEngine.kt`,
  `domain/usecase/capture/InferFrameUseCase.kt`). Cloud is the only backend: on-device TFLite
  was built, benchmarked at 20.8 s per frame against a 2-second capture cadence, and deferred
  to `feat/offline-inference`.
- **Connection-loss detection.** `GET /health` every 10 s while a session is active; two
  consecutive failures flip to disconnected (`core/connectivity/NetworkMonitor.kt:59-79`) and
  surface as `ui/capture/ConnectionLossBanner.kt`.
- **Inference auto-pause** whenever a sheet or child screen is foregrounded
  (`core/session/SessionManager.kt:94-109`, `ui/capture/CaptureViewModel.kt:161-172`).
- **Manual capture** of the live frame with no AI involvement, for specimens the model missed
  (`ui/capture/CaptureViewModel.kt:129-155`, `domain/usecase/verify/SubmitManualCaptureUseCase.kt`).

### Validation (human-in-the-loop)
- **Per-box verdict questionnaire** producing `CONFIRMED` / `FALSE_POSITIVE` /
  `WRONG_CLASS` / `BOX_INCORRECT` (`data/local/mapper/VerificationMapper.kt:10-17`).
- **Frame-level "missed eggs" question** setting `needs_reannotation`
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:38`).
- **Verification queue** as a full screen with filtering
  (`ui/verify/VerificationQueueScreen.kt`, `ui/verify/VerificationQueueViewModel.kt`).
- **Bounding-box overlay** with a toggle (`ui/verify/FrameWithBoxes.kt`).
- **Repeat flag** — mark a sample as an already-counted egg; excluded from EPG, never synced,
  and it does not block ending a session
  (`data/local/dao/SampleDao.kt:84-85`, `data/local/entity/SampleEntity.kt:83-90`).
- **Per-sample free-text note** (`data/local/dao/SampleDao.kt:93-122`).

### Sync
- **Verify-time sync**: resize the JPEG to 640×640 at quality 80, upload to Storage, insert
  the `samples` row, then the `detections` rows, then mark `synced` — or `sync_failed`
  (`data/supabase/SyncSampleUseCase.kt:29-49`, `:81-84`).
- **Trigger-based catch-up pass** in FK-safe order sessions → samples → reports, skipping
  cleanly when signed out or offline (`domain/usecase/sync/SyncPendingDataUseCase.kt:60-81`).
- **Pending / failed sync counts** surfaced in Settings
  (`data/local/dao/SampleDao.kt:137`, `:145`).

### Records and reports
- **Records browser** over verified samples (`ui/records/RecordsScreen.kt`,
  `domain/usecase/records/GetRecordsUseCase.kt`).
- **Session detail** with per-species counts and EPG (`ui/records/SessionDetailViewModel.kt:63-79`).
- **Sample detail with image fallback** — local file first, then a 15-minute signed Storage URL
  (`domain/usecase/records/ResolveSampleImageSourceUseCase.kt:17-41`,
  `data/supabase/SampleRemoteDataSource.kt:51-55`).
- **EPG** = confirmed egg count × 24 (Kato-Katz multiplier), repeats excluded
  (`core/util/EpgCalculator.kt:12-17`, `data/local/dao/DetectionDao.kt:33-52`). Pending
  replacement by LPF (Low Power Field) density under ticket 86d4a6jxw — every report
  surface below still reports EPG until that lands.
- **Persisted session reports** in Room and Supabase, multiple per session, newest first.
  Row-only sync — the CSV and PDF files themselves stay on the device
  (`domain/usecase/records/GenerateSessionReportUseCase.kt:37-97`,
  `data/supabase/SyncReportUseCase.kt:25-35`).
- **CSV export** to `Documents/AgarthaVision/` with a comment-prefixed header block, then
  shared from the generation snackbar or a report row
  (`data/repository/DocumentsReportFileStore.kt:31-38`,
  `domain/usecase/records/ReportCsvBuilder.kt:14-34`,
  `ui/records/ReportSharing.kt:30-37`).
- **PDF export** — the patient-facing artifact. A report is generated in the single format the
  medtech picks (PDF or CSV), so it carries one file, opened/shared in that format from the
  generation snackbar and the Reports list (`domain/usecase/records/ReportPdfBuilder.kt`,
  `domain/repository/ReportPdfRenderer.kt`, `data/repository/AndroidReportPdfRenderer.kt`,
  `ui/records/ReportSharing.kt:shareReportPdf`).

### Shell and appearance
- **Nine screens**: Login, Dashboard, Sessions, Capture, Verification Queue, Records, Session
  Detail, Sample Detail, Settings (`ui/navigation/AgarthaNavGraph.kt:42-56`).
- **Bottom tab bar with four tabs** — Home, Sessions, Records, Settings
  (`ui/components/AgarthaBottomBar.kt:44-55`). It shows on those four root destinations only;
  `bottomBarRoutes` (`:63-68`) is the gate, checked in `AgarthaNavGraph`, so Login, Capture
  and every drill-down hide it. Settings has no back button because it is a tab, not a
  drill-down.
- **Light/dark toggle** persisted in DataStore, with Capture exempt and always dark
  (`domain/usecase/settings/SetThemeModeUseCase.kt`, `ui/theme/Palette.kt`,
  `ui/theme/Theme.kt`).
- **Settings**: account, data and sync counts, manual sync, appearance, about, sign-out
  (`ui/settings/SettingsViewModel.kt:59-136`).

### Backend and inference service
- Eight applied Postgres migrations, `0001`–`0008`, with owner-scoped RLS throughout.
- FastAPI container with `GET /health` and `POST /infer`, bearer-token auth, weights baked in
  (`inference/server.py:29`, `:34`, `inference/Dockerfile`).

## Ghosts — named but not wired

| Ghost | Where it appears | Reality |
|---|---|---|
| `validation_records` table | `schema.ts:397-421`, `schema.ts:538-553` | **Not implemented.** No migration through `0008` creates it; no Room mirror; nothing writes to it. Phase 2 audit trail |
| WorkManager sync queue | `app/build.gradle.kts:164` | Dependency declared, **no `Worker` class exists**. Phase 1 sync is foreground and trigger-based |
| `administrative` report type | `supabase/migrations/0008_reports.sql:9` | Reserved in a comment; the CHECK allows only `session` (`0008_reports.sql:17`) |
| `samples.status` in Postgres | legacy ERD | Room/domain only — no migration creates it (`schema.ts:210-212`) |
| `reports.supabase_status` in Postgres | `schema.ts:382-383` | Room-only column |
| Admin dashboard / cross-session reporting | Product docs | The `admin` role and `is_admin()` exist in SQL (`0001_init.sql:13`, `0004_fix_profiles_rls_recursion.sql:4-16`); no admin UI exists in the app |
| Roboflow hosted inference | `local.properties.example`, DTO comments | Dead path. Superseded by the self-hosted container; the response shape is kept compatible only |
| In-app bounding-box editing | Verification design | Deferred to offline annotation tooling. `BOX_INCORRECT` records the problem; nothing fixes the box in-app |
| CI workflows | Git workflow docs | **No `.github/` directory exists** |
| `commitlint` / `lint-staged` | `commitlint.config.js`, `lint-staged.config.js` | Config committed, wired to no hook |

## Phase 2 — deferred by decision, not oversight

Self-hosted FastAPI + PostgreSQL + MinIO on owned hardware; a DOH-validated `prep_methods`
table replacing the hardcoded EPG multiplier; a durable offline sync queue with backoff; a
per-account persistent flagged-frame queue; capture moving off the phone camera onto dedicated
hardware over USB OTG, with the phone becoming a verification and reporting client only.

PDF report export is no longer deferred (ticket 86d4a6jyy) — see "Records and reports" above.
Its per-species number still reports EPG rather than a DOH-validated density; that swap is
ticket 86d4a6jxw's separate work.
