# File tree

Annotated. Generated from the real tree, then annotated with what each area is *for* — not a
file listing. For a specific file's behaviour, open the card in `map/`, not this page.

```
AgarthaVision/
├── SESSION_INIT.md            Session entry point. Read once, then route
├── AGENTS.example.md          Template for each member's gitignored AGENTS.md (personal agent rules)
├── README.md                  Human-facing project README
├── CONTRIBUTING.md            Human-facing contribution guide; points here for the rules
├── local.properties.example   Placeholder template for the gitignored local.properties (C10)
├── schema.ts                  Ground-truth data model, documentation only — never compiled
├── docs/                      This shelf. See docs/CONTEXT.md
│
├── app/                       The one Gradle module. Everything Android lives here
├── supabase/migrations/       Postgres schema + RLS this repo owns (legacy-dev/ archive; the console's admin set is elsewhere)
├── inference/                 The self-hosted FastAPI inference container
├── branding/                  Logo SVGs
├── tools/psgc/                Generator for the bundled PSGC asset. Run by hand, output committed
├── tools/geo/                 Generator for the bundled offline boundary assets. Run by hand, output committed
├── gradle/                    Wrapper + version catalog
├── .github/                   CI workflows (build-and-test.yml)
└── .husky/                    Git hooks — commit-msg, pre-commit, pre-push
```

## `app/` — the Android client

```
app/
├── build.gradle.kts           Module config, BuildConfig secret plumbing, dependency list
├── schemas/                   Exported Room schema JSON, one file per version
└── src/
    ├── main/
    │   ├── AndroidManifest.xml    Permissions, single Activity, FileProvider for report sharing
    │   ├── assets/psgc/           Bundled PSGC barangay dataset, gzipped. Room-seeded on first run
    │   ├── assets/geo/            Bundled offline province/town boundary geometry, custom binary format
    │   ├── assets/models/         The on-device model: <model_version>.tflite (Git LFS) + .json manifest
    │   ├── res/                   Launcher icons, strings, themes
    │   └── java/com/agarthavision/
    │       ├── MainActivity.kt · MainViewModel.kt · AgarthaVisionApp.kt
    │       ├── core/          Platform services. Everything Android-specific that is not UI
    │       ├── domain/        Pure Kotlin. Models, repository interfaces, use cases
    │       ├── data/          Room, Supabase, Retrofit, mappers, repository implementations
    │       └── ui/            Compose screens, ViewModels, navigation, theme
    ├── test/                  JVM unit tests, mirroring the main package layout.
    │                          test/…/ui/ also holds Compose UI tests, run under Robolectric
    ├── androidTest/           Instrumented tests: the stub and OnDeviceInferenceParityTest.
    │                          Test device only — they uninstall the app (commands.md)
    └── debug/                 Debug-variant manifest
```

### `core/` — platform services

| Folder | For |
|---|---|
| `camera/` | `CameraManager` binds Preview + ImageAnalysis; `FrameSampler` caches every analyzed frame as time-stamped JPEG bytes — no timer, no dispatch to inference (the shutter does that) |
| `connectivity/` | `NetworkMonitor` polls inference `/health`; `ConnectivityObserver` reports device network state |
| `database/` | `AgarthaDatabase` — the Room database declaration and its version number (v24); `Migrations.kt` — the hand-written migrations, from 22 → 23 on |
| `di/` | Hilt modules. `DatabaseModule` also carries every repository `@Binds` |
| `session/` | `SessionManager` + `SessionState` + `ActiveSessionIdStore`. The app-scoped record of which smear is open, and the pointer that survives process death |
| `sync/` | DataStore-backed sync bookkeeping: `DataStoreLastSyncStore`, `FetchOutcomeStore`, `InitialFetchStateStore` |
| `util/` | `ElapsedClock`, `DeviceIdProvider`, `ImageExtensions` (frame → square 640 JPEG), date bucketing and range sanitising, `NameMasking`, `SqlLike` |

### `domain/` — pure Kotlin

| Folder | For |
|---|---|
| `model/` | Domain models and the enums that define the vocabulary: `Patient`, `Sex`, `QueueSample`, `SampleStatus`, `DetectionVerdict`, `EggSpecies`, `EggStage`, `FrameSource`, `LpfDensity`, `ReportFormat`, `ReportType`, the four sync-status enums, Home and coverage models |
| `patient/` | `CodenameGenerator` — names for patients recorded without one |
| `session/` | `SessionLabelGenerator` — the auto-generated smear label |
| `sync/` | The `SyncScheduler` and `LastSyncStore` ports |
| `geo/` | Offline boundary geometry primitives: `IslandGroup`, `GeoBounds`, `GeoProjection`, `AreaShape`/`BoundarySet`/`AreaDirectory`, `ViewFit`, `HitTest`, `PositiveRateBin` |
| `repository/` | Interfaces only. Implementations live in `data/` |
| `inference/` | `InferenceEngine` and its result types, `Prediction`, `ImageBox`, `InferenceState`, and the background queue: `InferenceQueue`, `InferenceQueueProcessor` (the one consumer) and `CloudCircuitBreaker` |
| `usecase/auth` | Sign in, sign out, the login gate (`ResolveAuthGateUseCase`), observe identity, discard unsynced data |
| `usecase/home` | Home dashboard feeds: KPIs, findings, needs-attention, recent activity, session list |
| `usecase/patients` | Observe the patients list. Create and edit go through `PatientRepository` from the form's ViewModel (C1) |
| `usecase/sessions` | Generate a session label, search barangays. Starting a session is `SessionManager` |
| `usecase/capture` | Save a captured frame queued for inference, persist a flagged frame, delete one |
| `usecase/inference` | Cancel a sample's inference; connection and pending-inference exception types; network error mapping |
| `usecase/verify` | Submit verification, open target, the answer model (`VerificationAnswers`, `Finding` and its count helpers), queue observation and delete, species suggestions |
| `usecase/sync` | The trigger-based catch-up pass in FK-safe order |
| `usecase/records` | Records list, session samples, sample detail, image source resolution, report generation, PDF and CSV building |
| `usecase/reports` | Per-species LPF range aggregation (`LpfAggregation.kt`) and session egg counts |
| `usecase/settings` | Theme mode; pending sync counts |
| `usecase/coverage` | My coverage: aggregation, framing, and loading province/town boundary geometry |

Contains no Android imports. Does import `data/` in a few boundary files — see `constraints.md` C3.

### `data/` — persistence and the network

| Folder | For |
|---|---|
| `local/entity/` | Room entities (`PatientEntity`, `PatientUserEntity`, `SessionEntity`, `SampleEntity`, `DetectionEntity`, `ReportEntity`, `SampleSpeciesFindingEntity`, `PsgcBarangayEntity`, `SpeciesSuggestionEntity`) |
| `local/dao/` | Room queries for each entity. The LPF query lives in `DetectionDao` |
| `local/mapper/` | Entity ↔ domain conversion, plus `VerificationMapper` which computes verdicts and derived IDs |
| `local/psgc/` | `PsgcSeeder` for bundled barangay dataset |
| `geo/` | `GeoDataset` identity, `BoundaryBinaryReader` for the "AVGE" binary boundary format — pure JVM, no Android import |
| `local/species/` | `SpeciesSuggestionSeeder` and repository for offline autocomplete index |
| `local/` | `SampleImageStore` — on-device JPEG files under `users/{owner}/samples/` |
| `inference/` | `RemoteInferenceEngine` (the cloud container) and `PredictionMapper` |
| `inference/queue/` | `WorkManagerInferenceQueue`, `InferenceQueueWorker` and `InferenceRetryWorker` — run the inference queue's passes through WorkManager, one at a time |
| `inference/ondevice/` | `OnDeviceInferenceEngine` and its parts: `ModelStore` compiles a bundled model with LiteRT, `FramePreprocessor` letterboxes, `YoloOutputDecoder` decodes and runs NMS |
| `remote/` | Retrofit interface to the inference container, and its DTOs |
| `supabase/` | Remote data sources and per-entity sync use cases (`PatientRemoteDataSource`, `SyncPatientUseCase`, etc.), `RestoreReportFilesUseCase` |
| `sync/` | `SyncWorker` (`@HiltWorker`) and `WorkManagerSyncScheduler`, the `domain/sync/SyncScheduler` implementation |
| `repository/` | Repository implementations, `FlaggedFrameStore`, `InferenceQueueRepositoryImpl`, the report file store, `AndroidReportPdfRenderer` |

### `ui/` — Compose

| Folder | For |
|---|---|
| `navigation/` | `AgarthaNavGraph` — routes, start destination, bottom-bar visibility |
| `theme/` | The design system. Raw hex exists here and nowhere else |
| `icons/` | Material Symbols exports (`AgarthaIcons.kt`) |
| `components/` | Shared composables: buttons, badges, bottom bar, toast, capture frame boundary, microscopy viewport, glass modifiers, `EmptyState.kt` |
| `capture/` | The dark immersive capture screen and its connection-loss banner |
| `patients/` | Patients list, patient creation/edit form, and their ViewModels |
| `verify/` | Verification queue with inference badges, the verification sheet (checkbox questions, Add species, pending-inference state), box overlay and draw mode, species and stage dropdowns |
| `records/` | Records list, session detail with LPF range cards, sample detail, report export (PDF/CSV) and sharing |
| `dashboard/` | Home: KPI pager, needs-attention strip, recent session and activity; `coverage/` holds the My coverage card |
| `coverage/` | Full-screen My coverage map and province sheet |
| `sessions/` · `sessionlist/` · `activity/` | Patient's sessions and the New Session sheet · all sessions list · activity feed |
| `image/` | Coil fetcher and keyer that load a sample's JPEG from disk or Storage |
| `login/` · `settings/` | The remaining screens |

## `supabase/migrations/`

Numbered, committed, applied by hand in the Supabase dashboard. Never run programmatically.

This is the one home for which file describes which project.

| Path | Purpose |
|---|---|
| `0001_init.sql` | Consolidated schema for the patient-records project `agarthavision` (`profiles`, `patients`, `patient_users`, `sessions`, `samples`, `detections`, `sample_species_findings`, `reports`, base RLS, the `samples` bucket policies, `barangay_prevalence()`) |
| `0002_optional_patient_firstname.sql` | Drops the non-blank CHECK on `patients.firstname`, for codenamed patients |
| `0003_reports_bucket.sql` | The private `reports` Storage bucket and its policies |
| `0004_predictions.sql` | The `predictions` table and `detections.prediction_id` |
| `0005_verification_stage.sql` | `detections.stage`; widens the findings `stage` CHECK |
| `0006_drop_species_touched.sql` | Drops `detections.species_touched` |
| `0011_profile_outlives_login.sql` | `profiles.id` stops referencing `auth.users`; adds `profiles.account_id`, cleared when the login is deleted, so offboarding by deleting a login keeps authorship |
| `0012_deidentified_reads.sql` | `patients_deidentified`, `sessions_deidentified`, `samples_deidentified`: super-admin-only views without name, sex, birthdate, session label or sample note. Additive; step 1 of 3 |
| `0013_super_admin_reads_deidentified.sql` | Removes the super admin branch from the `patients`, `sessions` and `samples` read policies and the `reports` bucket. Step 3, applied after the Admin Console started reading the views (14zcqntjvky). Both files are applied to `agarthavision`, checked on 2026-10-02 |
| `0014_super_admins.sql` | Super admins become rows in `super_admins`, one per grant, revoked by tombstone; `is_admin()` reads it, and `profiles.role` is retired in place |
| `legacy-dev/` | Pre-patient migrations `0001`–`0013`, unedited, still the description of `agarthavision-dev` and `agarthavision-prod`, which `staging` and `main` point at. Never applied to `agarthavision`. Its `README.md` says why. Pre-consolidation numbers 0003, 0004 and 0006 name different files here, so cite them with the `legacy-dev/` prefix |

### The Admin Console's migrations — same database, other repository

The `agarthavision` database has a **second migration set** that this repository does not hold:
`supabase/migrations/admin/NNNN_*.sql` in the Admin Console's repository
(`kazuretsu/AgarthaVision-Admin`). It has its own number sequence, so the two repositories never
mint the same number, and it is applied by hand in the dashboard like this one. **Reading only
this folder no longer gives you the whole schema.** This section is the one home for what that
set does to it. The decision behind it is D4 and the migration convention in the ClickUp page
*Decisions & Risks*. No admin SQL is copied here.

**The rule: additive only.** An admin migration adds tables, functions, triggers and permissive
policies. It never `ALTER`s or `DROP`s a table, column, function or policy this repository owns.
A change to the shape or the policies of an app-owned table belongs in this folder instead.

**What it adds**, as of the console's `staging` (`cb8246c`, 2026-10-01), which holds the same
SQL as the branches it merged (`feat/organizations` `219d9cc4`, `feat/lab-scoping` `ce0dd6cb`,
`feat/audit-trail` `4e5390d4`). All three files were applied to `agarthavision` on 2026-10-01;
`admin/0001`'s backfill is logged in `admin_audit_log` at 14:17 UTC.

| Admin file | Adds |
|---|---|
| `admin/0001_organizations.sql` | Laboratory organizations. New tables `organizations`, `organization_members` (one organization per user; `org_admin` / `medtech` is a role on the membership, never on `profiles`), `patient_organizations` (which laboratory owns a patient) and the append-only `admin_audit_log`. Also `console_*` functions, and a backfill that puts every existing non-admin profile and every existing patient in one starting organization. Requires app `0001`–`0006` |
| `admin/0002_patient_scoping.sql` | The `patients` trigger below, and org-admin read access to the laboratory's records |
| `admin/0003_audit_exports.sql` | `console_record_export`, which records research exports in the audit log. No app-owned object touched |

**Where it touches app-owned objects**:

- **One trigger on `patients`.** `console_on_patient_created` is `AFTER INSERT` and runs
  `console_assign_patient_organization()`. It files the new patient under the organization of
  `created_by`'s membership. It **never raises**: every error is caught and logged as a warning,
  and the patient is left without an organization (risk R4). An error raised there would fail
  patient sync for every medtech. It sits beside the app's own `on_patient_created`, and neither
  depends on the other.
- **Permissive `SELECT` policies for the org admin**, mostly named `"<table>: org admin reads
  their organization"` (the findings one says `findings:`, and the `profiles` and Storage ones
  end in `organization's members` and `samples: … organization's frames`), on these app-owned
  tables:
  - `patients`
  - `patient_users`
  - `sessions`
  - `samples`
  - `detections`
  - `predictions`
  - `sample_species_findings`
  - `reports`
  - `profiles` (the laboratory's own members only)
  - `storage.objects` (the `samples` bucket only)

  Permissive policies are combined with `OR`, so these policies only add rows an org admin can
  read. Nothing a medtech or a super admin can see changes, and the app holds no organization
  data.

  **The phone does not pick these up.** An org admin may use the phone app; in a small laboratory
  the org admin is often a medtech as well. The phone's pull therefore scopes itself rather than
  leaving it to RLS (14zcqntjt3p), so whoever signs in downloads what a medtech's policies would
  give them and no more. That is their own rows, plus the full history of the patients
  their own `patient_users` rows name (`domain/usecase/sync/FetchRemoteDataUseCase.kt`):
  - their own links only (`data/supabase/PatientRemoteDataSource.kt::fetchPatientLinks`), then
    those patients by id (`::fetchPatients`)
  - sessions, samples and reports, each as "own" plus "under these parents"
    (`SessionRemoteDataSource.kt::fetchOwnSessions` / `::fetchSessionsForPatients`, and the
    same pair on `SampleRemoteDataSource.kt` and `ReportRemoteDataSource.kt`). The parents are
    read from the device (`SessionDao::getSessionIdsOnLinkedPatients`)
  - colleagues' names by id, for the authors on those patients only
    (`ColleagueDao::getColleagueIdsOnLinkedPatients`,
    `ProfileRemoteDataSource.kt::fetchColleagues`)

  So `admin/0002` changes nothing on a phone. **A super admin does not use the phone**: they
  belong to no organization, so a patient they registered would have none. Were one to sign in,
  `0013_super_admin_reads_deidentified.sql:38` has already taken `is_admin()` off
  `patients_select_linked` (`supabase/migrations/0001_init.sql:359-367`), and the branch left on
  `patient_users_select_own` (`0001_init.sql:396-398`) is filtered out by the link fetch above.
  A phone that synced as an org admin before this landed may still hold their laboratory's rows.
  Nothing removes them: they stay hidden behind the same `patient_users` scoping every local
  query uses, and an ordinary sign-out keeps local data. Clearing the app's storage, or
  reinstalling, does remove them.
- **Foreign keys into app tables.**
  - Both of these cascade on delete, though C8 means neither delete happens:
    - `patient_organizations.patient_id → patients(id)`
    - `organization_members.user_id → profiles(id)`. Since app `0011` a deleted login keeps its
      profile, so an offboarded medtech's membership stays, still `active`, with no login.
  - Three actor columns set null on delete:
    - `organizations.created_by → profiles(id)`
    - `organization_members.added_by → profiles(id)`
    - `admin_audit_log.actor_id → profiles(id)`

**What this means for a migration written here.** The admin set reads these app-owned names:

- `patients.id` and `patients.created_by`
- `sessions.patient_id`
- `samples.session_id` and `samples.storage_path`
- the `sample_id` columns of `detections`, `predictions` and `sample_species_findings`
- `reports.session_id`
- `patient_users.patient_id`
- `profiles.id`, `profiles.role` and `profiles.full_name`. Since `0014`, `role` grants nothing in
  the database, but the console's own gate still reads it until it switches to `is_admin()`
  (14zcqntjwjf). Until then a super admin granted by hand needs both a `super_admins` row and
  `role = 'admin'`
- that `profiles.id` *is* the login id: the set compares `auth.uid()` with
  `organization_members.user_id` and joins `auth.users` on `profiles.id`. Since `0011` the login
  is `profiles.account_id`, equal to `id` today; resolving profiles through `account_id` (the
  rehire step) breaks the console
- `public.is_admin(uuid)`, which since `0014` reads `super_admins`. Same signature, same answers
- the `{user_id}/{sample_id}.jpg` key shape in the `samples` bucket

Renaming or changing any of these breaks the console without an error on this side. The
`patients` trigger fails **silently**: new patients stop being assigned to a laboratory, and sync
carries on. Tell the console team before merging such a change.

## `inference/`

`server.py` (the two endpoints, a bounded queue and per-GPU micro-batching), `tests/` (pytest
against a fake model), `Dockerfile` (ROCm PyTorch base), `requirements.txt`,
`weights/` (the trained checkpoints, Git LFS: `yolo26n-efficientnetv2b0.pt`, which the server
runs, and the `yolo26n-mobilenetv4convsmall.pt` candidate), plus Kaggle notebook and notes.
`export/` turns a checkpoint into the on-device TFLite models and checks them against it (`export/README.md`). Not part of the Gradle build.

The exported models themselves ship in `app/src/main/assets/models/`, one `<model_version>.tflite`
(Git LFS) and `<model_version>.json` manifest per bundled build.

## `docs/`

```
docs/
├── CONTEXT.md          What belongs on the shelf and what does not
├── constraints.md      The 13 rules, with enforcement points
├── non-negotiables.md  The terse never-do-this list
├── stack.md            Versions, read off the build files
├── commands.md         Everything runnable
├── features.md         What ships, and the ghost list
├── CHANGELOG.md        What changed, from git history
├── file-tree.md        This file
├── patient-pii-position.md  Written position on patient PII, RA 10173, and synthetic validation data
├── map/
│   ├── objects/        Object cards — the nouns. Listed in objects/CONTEXT.md
│   ├── processes/      Process cards — the verbs. Listed in processes/CONTEXT.md
│   └── effects/        Change-impact index
└── _archive/           Superseded. Never implement against it
```

## Not in this repository

No `local.properties` — it is gitignored and you create it from `local.properties.example`.
No `TODO.md`, by rule.
