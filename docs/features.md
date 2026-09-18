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
  (`domain/usecase/auth/ClaimLocalDataUseCase.kt:33-53`). Unowned rows are visible to every
  caller on the device — Records, Sessions, and Verify all read them without a sign-in. The
  records DAO predicate is `(user_id = :userId OR user_id IS NULL)`, so a signed-out caller
  sees unowned rows only, never another medtech's data left on a shared phone
  (`data/local/dao/SessionDao.kt:39`, `SampleDao.kt:52`, `DetectionDao.kt:40`).

### Sessions
- **Session = one fecal smear, and it does not end.** Start with a label and optional notes;
  it then stays open, because the medtech keeps coming back to the smear - correcting a sample,
  generating a report from whatever is verified so far (86d4ab4vm).
  `core/session/SessionManager.kt`.
- **`ended_at` is legacy.** Nothing writes it any more. The column stays nullable and
  `SessionRemoteDataSource.closeSession` stays with it, because sessions closed before this
  change are real history; `resumeSession` still refuses to reopen one.
- **Sign-out detaches rather than ends** (`SessionManager.clearActive`), and the open session is
  restored at the next launch (`SessionManager.restoreActiveSession`) - without which a process
  restart would render the verification queue empty while the smear was still open.
- **Session picker and resume** for a still-open smear (`core/session/SessionManager.kt:79-89`).
- **Per-session "link to account" opt-out** (`claim_exempt`), excluding a session from the
  login claim. `domain/usecase/sessions/SetSessionClaimExemptUseCase.kt`,
  `data/local/entity/SessionEntity.kt:58-64`.
- **Patient barangay, required at session start.** A PSGC-coded barangay is the unit the
  surveillance map aggregates on; the capture-time GPS fix stays audit provenance and is
  still read by nothing. `ui/sessions/SessionsViewModel.kt:195-207`,
  `supabase/migrations/0010_session_psgc_barangay.sql`.
- **Offline barangay picker** over all 42,010 barangays, type-to-filter with results in a
  lazily-rendered list. The PSGC dataset ships in the APK (342 KB gzipped) and is Room-seeded
  on first run, so it works with the radio off — there is no network path on this route.
  `ui/components/SearchableDropdown.kt`, `data/local/psgc/PsgcSeeder.kt`,
  `domain/usecase/sessions/SearchBarangaysUseCase.kt`. Vintage pin and privacy rule:
  `docs/map/objects/PsgcBarangay.md`.

### Capture and inference
- **Continuous microscope feed analysis.** CameraX `ImageAnalysis` only — there is no
  `ImageCapture` use case (`core/camera/CameraManager.kt:43-136`). `FrameSampler` caches the
  latest frame as JPEG bytes on every frame and no longer dispatches to inference
  (`core/camera/FrameSampler.kt`).
- **Manual-trigger capture, one frame per field.** The medtech taps the shutter; the cached
  frame is snapshotted and run through inference once (`ui/capture/CaptureViewModel.kt`
  `onCapture`, `domain/usecase/capture/CaptureFieldUseCase.kt`). There is no timer — the old
  2-second auto-sampling was removed because a fecal smear is read by choosing ~10 likely
  fields, not by sweeping the slide continuously.
- **AI-vs-Manual by outcome.** Any inference response — zero detections included — records an
  **AI Capture** (`FrameSource.MODEL`); an `InferenceConnectionException` records a **Manual
  Capture** (`FrameSource.MANUAL`), so a lost connection never silently drops the tap
  (`domain/usecase/capture/CaptureFieldUseCase.kt`).
- **Synchronous inference** against the self-hosted FastAPI container, called through
  `RemoteInferenceEngine` behind the `InferenceEngine` interface
  (`data/remote/InferenceApi.kt:19-25`, `data/inference/RemoteInferenceEngine.kt`). Cloud is the
  only backend: on-device TFLite was built, benchmarked at 20.8 s per frame, and deferred to
  `feat/offline-inference`.
- **Connection-loss detection.** `GET /health` every 10 s **while a capture screen is
  mounted**; two consecutive failures flip to disconnected
  (`core/connectivity/NetworkMonitor.kt`) and surface as `ui/capture/ConnectionLossBanner.kt`.
  Gated on the screen rather than the session since 86d4ab4vm: a session that never ends would
  otherwise poll forever, to drive a banner nobody is looking at.

### Validation (human-in-the-loop)
- **Per-box verdict questionnaire** producing `CONFIRMED` / `FALSE_POSITIVE` /
  `WRONG_CLASS` / `BOX_INCORRECT` (`data/local/mapper/VerificationMapper.kt:10-17`).
- **Frame-level "missed eggs" question** setting `needs_reannotation`
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:38`).
- **Verification queue** as a full screen with filtering
  (`ui/verify/VerificationQueueScreen.kt`, `ui/verify/VerificationQueueViewModel.kt`).
  The queue distinguishes three empty states: never-had-items (queue is clear), filtered
  (a chip is hiding rows), and all-verified (every frame in this session has been checked).
  The all-verified state surfaces a "View session records" CTA that navigates to the
  session's detail screen. An `IconButton` on the Session Detail app bar opens the queue
  directly when the session is active and has pending frames
  (`ui/records/SessionDetailScreen.kt`, `ui/records/SessionDetailViewModel.kt`).
- **Bounding-box overlay** with a toggle (`ui/verify/FrameWithBoxes.kt`).
- **Delete a duplicate** — a sample captured twice is removed rather than flagged. Unverified
  frames are hard-deleted; a verified sample is tombstoned via `samples.deleted_at`, which hides
  it from every queue, count and report while its detections stay in the retraining corpus (C8).
  This replaced the **Repeat flag** (86d4ab4vm), which existed only because deletion was not
  possible. That flag was excluded from EPG, never synced,
  and it does not block ending a session
  (`data/local/dao/SampleDao.kt:84-85`, `data/local/entity/SampleEntity.kt:83-90`).
- **Per-sample free-text note** (`data/local/dao/SampleDao.kt:93-122`).
- **No developmental-stage question.** 86d4a6jwy added one; staging reverted it (`9dcfd5d`)
  because the four stages it shipped were never checked against literature — Ascaris could only
  be tagged `UNFERTILIZED`, the one stage that is never infective — and the ticket is
  deprioritised. `sample_species_findings.stage` survives as a dormant, always-null column
  because `0012` is applied and frozen (C6).

### Sync
- **Verify-time sync**: resize the JPEG to 640×640 at quality 80, upload to Storage, insert
  the `samples` row, then the `detections` rows, then mark `synced` — or `sync_failed`
  (`data/supabase/SyncSampleUseCase.kt:29-49`, `:81-84`).
- **Trigger-based catch-up pass** in FK-safe order sessions → samples → reports, skipping
  cleanly when signed out or offline (`domain/usecase/sync/SyncPendingDataUseCase.kt:60-81`).
- **Pending / failed sync counts** surfaced in Settings
  (`data/local/dao/SampleDao.kt:137`, `:145`).
- **Signed-out "N not linked" badge**: when signed out and unowned local sessions exist
  (`user_id IS NULL AND claim_exempt = 0`), the sync badge shows "N not linked" and a
  helper line prompts sign-in to link them; uses `SessionDao.observeUnlinkedCount()` via
  `ObserveUnlinkedSessionCountUseCase` and `syncBadgeState()` in `ui/settings/SettingsCards.kt`.

### Records and reports
- **Records browser** over verified samples, including unowned local sessions
  (`ui/records/RecordsScreen.kt`, `domain/usecase/records/GetRecordsUseCase.kt`).
  Records reads unowned local data the same way Sessions and Verify do — no sign-in required.
  Report generation still requires a cached local identity.
- **Session detail** with per-species counts and LPF density (`ui/records/SessionDetailViewModel.kt`).
  Shows `NOT_FOUND` / `NOT_VISIBLE` empty states instead of a perpetual skeleton when the session
  is absent or belongs to a different account.
- **LPF Density** is the primary reported unit (Philippine Direct Smear method). It is calculated
  per species as the mean egg count across all fields examined in a session, reported alongside
  the observed range (min-max). Denominator is the total count of recorded frames, including
  clean ones.
  (`ui/records/SessionDetailScreen.kt:LpfHeroCard`, `domain/usecase/reports/SessionEggCountUseCase.kt`).
- **Sample detail with image fallback** — local file first, then a 15-minute signed Storage URL
  (`domain/usecase/records/ResolveSampleImageSourceUseCase.kt:17-41`,
  `data/supabase/SampleRemoteDataSource.kt:51-55`).
- **Persisted session reports** in Room and Supabase, multiple per session, newest first.
  Row-only sync — the CSV and PDF files themselves stay on the device
  (`domain/usecase/records/GenerateSessionReportUseCase.kt:37-97`,
  `data/supabase/SyncReportUseCase.kt:25-35`).
- **CSV export** to `Documents/AgarthaVision/` with a comment-prefixed header block, then
  shared from the generation snackbar or a report row
  (`data/repository/DocumentsReportFileStore.kt:31-38`,
  `domain/usecase/records/ReportCsvBuilder.kt:14-34`,
  `ui/records/ReportSharing.kt:30-37`). The row also emits a `stage` column
  (`domain/usecase/records/ReportCsvBuilder.kt`).
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
- Eleven numbered Postgres migrations, `0001`–`0011`, with owner-scoped RLS throughout.
  `0010_session_psgc_barangay.sql` is the one not yet applied — see its header on apply order.
- FastAPI container with `GET /health` and `POST /infer`, bearer-token auth, weights baked in
  (`inference/server.py:29`, `:34`, `inference/Dockerfile`).

## Ghosts — named but not wired

| Ghost | Where it appears | Reality |
|---|---|---|
| `validation_records` table | `schema.ts:463-487`, `schema.ts:605-622` | **Not implemented.** No migration through `0011` creates it; no Room mirror; nothing writes to it. Phase 2 audit trail |
| ~~WorkManager sync queue~~ | — | **Built** (86d4brr1f). `SyncWorker` behind the `SyncScheduler` port; unique work, network constraint, exponential backoff. See `map/processes/sync.md` |
| `administrative` report type | `supabase/migrations/0008_reports.sql:9` | Reserved in a comment; the CHECK allows only `session` (`0008_reports.sql:17`) |
| `samples.status` in Postgres | legacy ERD | Room/domain only — no migration creates it (`schema.ts:270-272`) |
| `reports.supabase_status` in Postgres | `schema.ts:448-449` | Room-only column |
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
