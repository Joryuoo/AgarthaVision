# Features

What ships today, verified by opening the screen, the ViewModel, and the use case behind it.
Anything not on the "ships" list is on the ghost list at the bottom — do not present a ghost
as working.

## Ships

### Auth and identity
- **Email/password sign-in** through Supabase Auth. No sign-up flow — accounts are
  provisioned in the Supabase dashboard. `ui/login/LoginScreen.kt`,
  `domain/usecase/auth/SignInUseCase.kt`, `data/repository/SupabaseAuthRepository.kt:54-60`.
- **Mandatory login.** Sign-in is required on first launch before accessing patients, sessions,
  or capture (`ui/navigation/AgarthaNavGraph.kt:115-125`). Unowned local data and deferred-claim
  flows (`ClaimLocalDataUseCase`, `claim_exempt`) have been removed; all rows have an owner
  from the moment they are created.
- **Cached local identity.** The signed-in user id, email, and display name are cached in
  DataStore so offline work is attributed to the last medtech
  (`data/repository/SupabaseAuthRepository.kt:74-86`).
- **Sign-out** revokes the Supabase token *and* clears the cached identity, returning the
  device to the signed-out state. `domain/usecase/auth/SignOutUseCase.kt`,
  `data/repository/SupabaseAuthRepository.kt:64-71`.

### Patients
- **Patient = primary clinical unit.** Medtechs organize work around patients; a patient owns
  sessions (`User -> Patient -> Session -> Sample`). `ui/patients/PatientsScreen.kt`,
  `ui/patients/PatientFormScreen.kt`, `domain/model/Patient.kt`.
- **Demographics.** Lastname, firstname, optional middle name, sex (`M`/`F`, `domain/model/Sex.kt`),
  and birthdate.
- **Birthdate over age.** Birthdate is stored (epoch millis at midnight Asia/Manila,
  `PatientEntity.kt:59`) rather than age, so age is recomputed dynamically per encounter and
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
- **No client delete.** Patient deletion is restricted to server administrators; no client path
  or DAO method allows deleting a patient.
- **Non-cascading edits.** Editing a patient's details does not cascade to existing session
  labels or past generated reports.

### Sessions
- **Session = one fecal smear.** A session belongs to a patient (`patient_id` FK) and never ends
  (`core/session/SessionManager.kt`, `data/local/entity/SessionEntity.kt`).
- **Smear label.** Auto-generated as initials, barangay code, and sequential smear index
  (e.g., `C.G.-0730600000-001`), editable thereafter.
- **Notes, ended_at, and claim_exempt removed.** Notes were replaced by the Patient entity;
  `ended_at` was dropped because smears remain open; `claim_exempt` was dropped with mandatory
  login.
- **Session picker and resume** for resuming an open smear (`core/session/SessionManager.kt:79-89`).
- **Sign-out detaches rather than ends** (`SessionManager.clearActive`), and the active session is
  restored at launch (`SessionManager.restoreActiveSession`).

### Capture and inference
- **One-shot manual shutter capture.** CameraX `ImageAnalysis` runs continuously to supply preview
  and frame cache; tapping the shutter triggers `CaptureFieldUseCase` once for that field
  (`core/camera/CameraManager.kt`, `core/camera/FrameSampler.kt`, `ui/capture/CaptureViewModel.kt`).
  Continuous/timed auto-sampling is removed.
- **AI-vs-Manual by outcome.** A successful response from the inference container (even with zero
  detections) records an **AI Capture** (`FrameSource.MODEL`). A transport failure
  (`InferenceConnectionException`) records a **Manual Capture** (`FrameSource.MANUAL`), so network
  disconnections never drop a capture.
- **Synchronous cloud inference** against the self-hosted FastAPI container
  (`data/remote/InferenceApi.kt`, `data/inference/RemoteInferenceEngine.kt`).
- **Connection-loss detection.** Regular `/health` checks while capture is mounted flip to
  disconnected on two consecutive failures and surface `ui/capture/ConnectionLossBanner.kt`.

### Validation (human-in-the-loop)
- **Model output pre-filling.** A detected box opens with Q1 (is egg: yes), Q2 (box correct: yes),
  and Q3 (species: model class) pre-filled. Answering is only required when correcting the model.
  Untouched pre-fills submit with `species_touched = false`; touching/confirming sets it to `true`.
- **Derived Q4.** The "missed eggs" flag (`needs_reannotation`) is derived automatically when
  eggs are added that the model never boxed, rather than asking a separate question.
- **Always-available Add Egg.** An "Add Egg" button on every frame replaces the old separate manual
  checklist, allowing findings to be added with or without bounding boxes.
- **Per-box verdict questionnaire.** Evaluates to `CONFIRMED`, `FALSE_POSITIVE`, `WRONG_CLASS`,
  or `BOX_INCORRECT` (`data/local/mapper/VerificationMapper.kt:38-45`).
- **Verification queue.** A unified queue screen with `UNVERIFIED` and `VERIFIED` buckets
  (`domain/model/QueueSample.kt:QueueBucket`, `ui/verify/VerificationQueueScreen.kt`,
  `ui/verify/VerificationQueueViewModel.kt`). Supports batch hard-deletion of unverified frames.
- **Bounding box overlay.** Renders box primitives with interactive drag adjustments
  (`ui/verify/FrameWithBoxes.kt`).
- **Tombstoning duplicates.** A duplicate verified sample is tombstoned via `samples.deleted_at`
  (`supabase/migrations/0013_sample_soft_delete.sql`), hiding it from queries, counts, and reports
  while preserving its detections in the retraining corpus (C8). The legacy `is_repeat` flag is
  completely removed.
- **Per-sample free-text note** (`SampleEntity.kt:97-98`, `data/local/dao/SampleDao.kt`).
- **No developmental stage question.** `sample_species_findings.stage` remains nullable and
  dormant.

### Sync
- **Verify-time sync**: Resizes JPEG to 640×640, uploads to Storage, upserts sample and detection
  rows, and updates sync status (`data/supabase/SyncSampleUseCase.kt`).
- **Catch-up pass in FK-safe order**: Pushes in sequence: `patients -> sessions -> samples -> reports`
  (`domain/usecase/sync/SyncPendingDataUseCase.kt:76-90`).
- **WorkManager sync worker**: `@HiltWorker` `SyncWorker` enqueued as unique work with network
  constraints and exponential backoff (`core/sync/SyncScheduler.kt`, `data/sync/SyncWorker.kt`).

### Records and reports
- **Records browser** over verified samples (`ui/records/RecordsScreen.kt`,
  `domain/usecase/records/GetRecordsUseCase.kt`).
- **Session detail** with per-species counts and LPF density ranges (`ui/records/SessionDetailViewModel.kt`).
- **LPF Density ranges (Direct Smear).** Replaces EPG. Reported per species as a `min..max` range
  across all fields examined in a session (clean fields contribute 0), paired with a qualitative
  descriptor (*rare / few / moderate / numerous*). Aggregated via `aggregateLpfPerSpecies`
  (`domain/usecase/reports/LpfAggregation.kt`, `domain/usecase/reports/SessionEggCountUseCase.kt`).
- **Detection count rule:** Counts non-false-positive detections
  (`data/local/dao/DetectionDao.kt:43`: `d.verdict != 'false_positive'`).
- **Sample detail with image fallback** — local file first, then 15-minute signed Supabase Storage URL.
- **PDF-only session reports.** Generated as a PDF artifact on-device via `ReportPdfBuilder.kt` and
  `ReportPdfRenderer.kt`, stored in `Documents/AgarthaVision/`, and tracked in Room/Supabase
  (`domain/usecase/records/GenerateSessionReportUseCase.kt`).

### Shell and appearance
- **Screens**: Login, Dashboard, Patients, PatientSessions, PatientForm, Capture, Reports,
  SessionDetail, SampleDetail, VerificationQueue, Settings (`ui/navigation/AgarthaNavGraph.kt:44-89`).
- **Bottom tab bar with four tabs**: Home, Patients, Reports, Settings
  (`ui/components/AgarthaBottomBar.kt:53-61`).
- **Light/dark toggle** persisted in DataStore; Capture is exempt and stays dark (`ui/theme/Theme.kt`).
- **Settings**: account details, sync queue status, manual sync triggers, theme toggle, sign-out.

### Backend and inference service
- **Consolidated Postgres migration**: `supabase/migrations/0001_init.sql` on the `agarthavision`
  project. Pre-patient migrations `0001`–`0013` archived under `supabase/migrations/legacy-dev/`.
- **FastAPI inference container**: `GET /health` and `POST /infer`, bearer-token authentication,
  model weights bundled (`inference/server.py:29`, `:34`, `inference/Dockerfile`).

## Ghosts — named but not wired

| Ghost | Where it appears | Reality |
|---|---|---|
| `validation_records` table | `schema.ts:595-618` | **Not implemented.** No migration creates it; no Room mirror; Phase 2 audit trail |
| `administrative` report type | `supabase/migrations/0001_init.sql:313` | The CHECK allows only `'session'` |
| `samples.status` in Postgres | legacy ERD | Room/domain only — no remote column |
| `reports.supabase_status` in Postgres | `schema.ts:553` | Room-only column |
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
