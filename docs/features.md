# Features

What ships today, verified by opening the screen, the ViewModel, and the use case behind it.
Anything not on the "ships" list is on the ghost list at the bottom — do not present a ghost
as working.

## Ships

### Auth and identity
- **Email/password sign-in** through Supabase Auth. No sign-up flow — accounts are
  provisioned in the Supabase dashboard. `ui/login/LoginScreen.kt`,
  `domain/usecase/auth/SignInUseCase.kt`, `data/repository/SupabaseAuthRepository.kt::signIn`.
- **Mandatory login.** Sign-in is required on first launch before accessing patients, sessions,
  or capture: `MainActivity` picks the start destination from `MainViewModel.authGate`
  (`domain/usecase/auth/ResolveAuthGateUseCase.kt`). Unowned local data and deferred-claim
  flows (`ClaimLocalDataUseCase`, `claim_exempt`) have been removed; all rows have an owner
  from the moment they are created.
- **Cached local identity.** The signed-in user id, email, and display name are cached in
  DataStore so offline work is attributed to the last medtech
  (`data/repository/SupabaseAuthRepository.kt::cacheIdentity`).
- **Sign-out** revokes the Supabase token *and* clears the cached identity, returning the
  device to the signed-out state. `domain/usecase/auth/SignOutUseCase.kt`,
  `data/repository/SupabaseAuthRepository.kt::signOut`.
- **Change password** from Settings (14zcqntjph9): current password, new, confirm. Online only;
  offline the screen says so and the button is disabled. The current password is checked first,
  and a wrong one is refused on its field. This phone stays signed in and local data is
  untouched; other devices need the new password. `ui/settings/ChangePasswordScreen.kt`,
  `domain/usecase/auth/ChangePasswordUseCase.kt`,
  `data/repository/SupabaseAuthRepository.kt::changePassword`.
- **Signed out and wiped when the account is removed** (14zcqntjph8). The first sync pass after
  the server refuses to renew the login (the login was deleted or banned, or its password was
  changed elsewhere) removes everything synced from the phone: patients, sessions, samples,
  reports, JPEGs and exported report files. Unsynced work stays and uploads after the medtech
  signs back in. The login screen says why and how many unuploaded items are waiting. Offline, a
  timeout, a 5xx or an ordinary expired token change nothing.
  `domain/usecase/auth/EnforceAccountAccessUseCase.kt`,
  `domain/usecase/auth/WipeLocalAccountDataUseCase.kt`,
  `data/repository/SupabaseAccountAccessRepository.kt`.

### Patients
- **Patient = primary clinical unit.** Medtechs organize work around patients; a patient owns
  sessions (`User -> Patient -> Session -> Sample`). `ui/patients/PatientsScreen.kt`,
  `ui/patients/PatientFormScreen.kt`, `domain/model/Patient.kt`.
- **Demographics.** Lastname, firstname, optional middle name, sex (`M`/`F`, `domain/model/Sex.kt`),
  and birthdate.
- **Birthdate over age.** Birthdate is stored (epoch millis at midnight Asia/Manila,
  `PatientEntity.kt::birthdate`) rather than age, so age is recomputed dynamically per encounter and
  never becomes silently stale.
- **PSGC barangay on the patient.** The 10-digit zero-padded PSGC code
  (`patients.psgc_barangay_code`, checked with `~ '^[0-9]{10}$'`) lives on the patient, not the
  session, because surveillance aggregates on the patient's residence.
- **Offline barangay picker.** Type-to-filter dropdown over all 42,010 Philippine barangays,
  seeded into Room from a bundled asset (`ui/components/SearchableDropdown.kt`,
  `data/local/psgc/PsgcSeeder.kt`, `domain/usecase/sessions/SearchBarangaysUseCase.kt`).
- **Visibility via `patient_users`.** Access resolves through the `patient_users` join table rather
  than `created_by` (`PatientUserEntity.kt`, `0001_init.sql:117-122`). The `on_patient_created`
  trigger auto-links the creator server-side so they can read back the row immediately.
- **Shared patient history.** A medtech assigned to a patient downloads and reads the patient's
  whole history — colleagues' sessions, samples, frames and reports included — while authorship
  stays with whoever recorded it (`0007_patient_shared_history.sql`,
  `domain/repository/PatientAccessRepository.kt`). An assignment the server removes leaves the
  patient list at the next pull (`FetchRemoteDataUseCase.kt::removeRevokedLinks`).
- **Colleagues' records are read-only.** A colleague's session opens in Session Detail rather than
  Capture, and a colleague's sample offers no edit, re-verify, add-species, redraw or delete; both
  say "Recorded by …" (`ui/components/ReadOnlyAuthorNote.kt`,
  `domain/model/RecordAuthorship.kt::isColleagueRecord`). Names come from the `colleagues` cache
  (`0008_colleague_names.sql`, Room v24).
- **No client delete.** Patient deletion is restricted to server administrators; no client path
  or DAO method allows deleting a patient.
- **Non-cascading edits.** Editing a patient's details does not cascade to existing session
  labels or past generated reports.

### Sessions
- **Session = one fecal smear.** A session belongs to a patient (`patient_id` FK) and never ends
  (`core/session/SessionManager.kt`, `data/local/entity/SessionEntity.kt`).
- **Smear label.** Auto-generated from the patient as surname abbreviation and first initial, sex
  and age, and the patient's smear sequence (e.g. `LDNJ-M21-S01`,
  `domain/session/SessionLabelGenerator.kt`), editable thereafter by long-pressing the session card, unique per patient.
- **Notes, ended_at, and claim_exempt removed.** Notes were replaced by the Patient entity;
  `ended_at` was dropped because smears remain open; `claim_exempt` was dropped with mandatory
  login.
- **Session picker and resume** for resuming an open smear (`core/session/SessionManager.kt::startSession`).
- **Sign-out detaches rather than ends** (`SessionManager.clearActive`), and the active session is
  restored at launch (`SessionManager.restoreActiveSession`).

### Capture and inference
- **One-shot manual shutter capture.** CameraX `ImageAnalysis` runs continuously to supply preview
  and frame cache; tapping the shutter triggers `CaptureFieldUseCase` once for that field
  (`core/camera/CameraManager.kt`, `core/camera/FrameSampler.kt`, `ui/capture/CaptureViewModel.kt`).
  Continuous/timed auto-sampling is removed.
- **Capture saves at once; inference runs in the background.** The tap saves the frame queued for
  a model and returns. `InferenceQueueProcessor` gives it a model output later, one frame at a
  time: the self-hosted FastAPI container first (`data/inference/RemoteInferenceEngine.kt`), the
  bundled on-device model when the container cannot be reached
  (`data/inference/ondevice/OnDeviceInferenceEngine.kt`), with a circuit breaker so a dead
  server is not retried on every frame. Any model answer, zero detections included, makes an
  **AI Capture** (`FrameSource.MODEL`); a cancel, or both engines failing five times, makes a
  **Manual Capture** (`FrameSource.MANUAL`).
- **Connection-loss detection.** Regular `/health` checks while capture is mounted flip to
  disconnected on two consecutive failures and surface `ui/capture/ConnectionLossBanner.kt`.

### Validation (human-in-the-loop)
- **Model output pre-filling, as checkboxes.** Each detected box shows three pre-checked
  statements: Q1 "There is a parasitic egg in this box", Q2 "The bounding box is correctly
  placed", Q3 "This egg is <species>" (`ui/verify/VerificationSheet.kt::CheckQuestion`).
  Answering is only required when correcting the model; submitting an untouched row is the
  medtech's confirmation of it.
- **Derived Q4.** The "missed eggs" flag (`needs_reannotation`) is derived automatically when a
  species' egg count exceeds what the model boxed, rather than asking a separate question.
- **Add species, counted per field.** "+ Add species" opens one card per species with the
  field's **total** egg count for that species, floored at what the model already boxed
  (`domain/usecase/verify/VerificationAnswers.kt::fieldTotal`). "Locate eggs" optionally draws a
  box for each unboxed egg (`drawnBoxes`); drawing never gates submit.
- **Per-box verdict questionnaire.** Evaluates to `CONFIRMED`, `FALSE_POSITIVE`, `WRONG_CLASS`,
  or `BOX_INCORRECT` (`data/local/mapper/VerificationMapper.kt::computeVerdict`).
- **Nothing is annotated while a model is still running.** A frame whose inference is queued or
  running shows "Frame is in inference", locks Add species, remarks and Submit, and offers
  "Cancel inference", which turns it into a manual capture (`ui/verify/InferencePending.kt`,
  `domain/usecase/inference/CancelInferenceUseCase.kt`). Queue rows carry a Queued / In inference
  / Ready / Manual badge (`ui/verify/InferenceStateBadge.kt`).
- **Verification queue.** A unified queue screen with `UNVERIFIED` and `VERIFIED` buckets
  (`domain/model/QueueSample.kt:QueueBucket`, `ui/verify/VerificationQueueScreen.kt`,
  `ui/verify/VerificationQueueViewModel.kt`). Supports batch hard-deletion of unverified frames.
- **Bounding box overlay and redraw.** `ui/verify/FrameWithBoxes.kt` renders the boxes;
  `ui/verify/DrawMode.kt` draws a replacement box, or a box for an added egg.
- **Tombstoning duplicates.** A duplicate verified sample is tombstoned via `samples.deleted_at`
  (`supabase/migrations/0001_init.sql:202`), hiding it from queries, counts, and reports
  while preserving its detections in the retraining corpus (C8). The legacy `is_repeat` flag is
  completely removed.
- **Per-sample free-text note** (`SampleEntity.kt::userNote`, `data/local/dao/SampleDao.kt`).
- **Developmental stage question.** Added and model-box species/stage prompts ask for STH
  developmental stage (`domain/model/EggStage.kt`); `sample_species_findings.stage` and
  `detections.stage` are written and are part of each row's identity, so two cards naming one
  species at different stages persist as distinct rows instead of overwriting each other.

### Sync
- **Verify-time sync**: Resizes JPEG to 640×640, uploads to Storage, upserts sample and detection
  rows, and updates sync status (`data/supabase/SyncSampleUseCase.kt`).
- **Catch-up pass in FK-safe order**: Pushes in sequence: `patients -> sessions -> samples -> reports`
  (`domain/usecase/sync/SyncPendingDataUseCase.kt::invoke`).
- **WorkManager sync worker**: `@HiltWorker` `SyncWorker` enqueued as unique work with network
  constraints and exponential backoff (`domain/sync/SyncScheduler.kt`, implemented by
  `data/sync/WorkManagerSyncScheduler.kt`; `data/sync/SyncWorker.kt`).

### Records and reports
- **Records browser** over verified samples (`ui/records/RecordsScreen.kt`,
  `domain/usecase/records/GetRecordsUseCase.kt`).
- **Session detail** with per-species counts and LPF density ranges (`ui/records/SessionDetailViewModel.kt`).
- **LPF Density ranges & Parasite Burden (Direct Smear).** Replaces WHO Kato-Katz EPG infectivity tiers (PB-16 / `4909f78`). WHO/DOH light/moderate/heavy intensity tables are defined strictly for Kato-Katz EPG and cannot be rescaled to Direct Smear LPF. The app reports LPF density as a `min..max` range across all fields examined in a session (clean fields contribute 0), paired with the conventional wet-mount descriptor (*rare / few / moderate / numerous*) and defensible estimated parasite burden levels (*Low / Moderate / High Burden*). Re-adding a clinical intensity tier requires an explicit cutoff table signed off clinically for Direct Smear LPF by name and date. Aggregated via `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`, `domain/model/LpfDensity.kt`).
- **Detection count rule:** Counts non-false-positive detections
  (`data/local/dao/DetectionDao.kt::getConfirmedEggCountsForSession`: `d.verdict != 'false_positive'`).
- **Sample detail with image fallback** — local file first, then 15-minute signed Supabase Storage URL.
- **PDF-only session reports.** Generated as a PDF on-device
  (`domain/usecase/records/ReportPdfBuilder.kt`, `data/repository/AndroidReportPdfRenderer.kt`)
  with a patient header, stored in `Documents/AgarthaVision/`, tracked in Room and Supabase, and
  mirrored to the `reports` Storage bucket so another device can open it
  (`domain/usecase/records/GenerateSessionReportUseCase.kt`,
  `data/supabase/SyncReportUseCase.kt`, `data/supabase/RestoreReportFilesUseCase.kt`).
- **Patient reports (14zcqntj2uz).** Pool every session a `PatientReportScope` resolves to
  (defaulting to all of a patient's sessions; a date range and/or an explicit session subset
  narrow it) into one PDF-only document, reached from the Sessions screen's "Generate report"
  sheet (`ui/sessions/PatientReportSheet.kt`). Findings are pooled into a single
  `aggregateLpfPerSpecies` call across every included session (not summed per-session ranges),
  with a per-session breakdown table alongside it. Sessions with zero verified samples are
  excluded and shown disabled in the picker
  (`domain/usecase/records/GeneratePatientReportUseCase.kt`,
  `domain/usecase/records/GetPatientReportCandidatesUseCase.kt`).

### Home dashboard
- **KPI tile pager.** Page 1 of the Home pager shows the four activity tiles (Sessions,
  Positive rate, To review, AI agreement); page 2 shows **My coverage**
  (`ui/dashboard/KpiPager.kt`, `ui/dashboard/coverage/MyCoverageCard.kt`).
- **My coverage card.** Rolls examined smears up to province level via each patient's PSGC
  barangay, shaded by positive rate on a small offline choropleth map
  (`ui/dashboard/coverage/MiniChoroplethMap.kt`), with a rate/smear-count summary beside it.
  Framed as a single province, an island group, or the whole country depending on how many
  provinces have data in the selected period (`domain/usecase/coverage/CoverageAggregation.kt`,
  `domain/usecase/coverage/ObserveMyCoverageUseCase.kt`). Tapping the card opens the full-screen
  **My coverage** map (`Screen.MyCoverage`, `ui/coverage/MyCoverageScreen.kt`) — a pannable/
  zoomable province choropleth with island-group filter chips, a period toggle local to the
  screen, and a per-province bottom sheet (`ui/coverage/ProvinceSheet.kt`) showing the positive
  rate, species mix, and towns ranked by positive rate.

### Shell and appearance
- **Screens**: the `Screen` routes in `ui/navigation/AgarthaNavGraph.kt` — Login, Dashboard,
  Patients, PatientSessions, PatientForm, Capture, Reports, VerificationQueue, SessionDetail,
  SampleDetail, SessionList, MyCoverage, Activity, Settings, ChangePassword.
- **Bottom tab bar with four tabs**: Home, Patients, Reports, Settings
  (`ui/components/AgarthaBottomBar.kt::Tab`).
- **Light/dark toggle** persisted in DataStore; Capture is exempt and stays dark (`ui/theme/Theme.kt`).
- **First-run onboarding**: four-page pager with Skip, Back, a "Step X of Y" screen-reader label,
  illustrations chosen by the active theme, and a one-time entrance animation that is switched off when the
  system "Remove animations" setting is on (`ui/onboarding/OnboardingScreen.kt::OnboardingContent`,
  `ui/components/ReducedMotion.kt`). Completion is saved through `OnboardingPreferenceRepository` first, then the app
  goes to Login on first run, or to the Dashboard for an already-signed-in user.
- **Settings**: account details, change password, sync queue status, manual sync triggers, theme
  toggle, sign-out.

### Backend and inference service
- **Postgres schema**: see [`file-tree.md`](file-tree.md#supabasemigrations) for the migration
  layout and the `legacy-dev/` archive.
- **FastAPI inference container**: `GET /health` and `POST /infer`, bearer-token authentication,
  model weights bundled, requests micro-batched on a bounded queue with one worker per GPU
  (`inference/server.py::health`, `::infer`, `::worker`; `inference/README.md` "Queue and
  batching").

## Ghosts — named but not wired

| Ghost | Where it appears | Reality |
|---|---|---|
| `validation_records` table | `schema.ts` `ValidationRecord` | **Not implemented.** No migration creates it; no Room mirror; Phase 2 audit trail |
| `administrative` report type | `supabase/migrations/0001_init.sql:313` | The CHECK allows only `'session'` |
| `samples.status` in Postgres | legacy ERD | Room/domain only — no remote column |
| `reports.supabase_status` in Postgres | `schema.ts` `Report.supabase_status` | Room-only column |
| Admin dashboard / cross-session reporting | Product docs | `is_admin()` exists in SQL; no admin UI in the mobile client |
| Roboflow hosted inference | `local.properties.example`, DTO comments | Dead path; self-hosted container is the single inference backend |
| `commitlint` / `lint-staged` | `commitlint.config.js`, `lint-staged.config.js` | Config files committed; `.husky/commit-msg` enforces format directly |

## Phase 2 — deferred by decision, not oversight

Self-hosted FastAPI + PostgreSQL + MinIO on owned hardware; a DOH-validated `prep_methods`
table describing how a smear was prepared; capture moving off the phone camera onto dedicated
hardware over USB OTG.

PDF report export is the current format. Its per-species metric is an **LPF range**, not EPG:
the lowest and highest count of that species in any single field of the session, with a qualitative
descriptor read off the highest one (PB-17). A field holding none of a species contributes a zero,
not a gap, so a species seen in 3 of 10 fields reads `0–4` rather than `1–4`. The denominator is
however many fields the medtech recorded — ten is typical practice, not a rule the app enforces.
**It is not a mean.** **Eggs per gram is gone** (PB-16): it is defined for Kato-Katz, Philippine
medtechs use Direct Smear, and the ×24 volumetric multiplier was wrong for the method in use.

The WHO light/moderate/heavy tier went with it rather than being rescaled. That classification
is defined only as eggs-per-gram measured by Kato-Katz; there is no WHO or DOH intensity table
for direct fecal smear. What stands in its place is the conventional semi-quantitative descriptor
attached to the LPF range.
