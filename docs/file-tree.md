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
├── supabase/migrations/       Postgres schema + RLS (0001_init.sql; legacy-dev/ archive)
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
| `database/` | `AgarthaDatabase` — the Room database declaration and its version number (v23); `Migrations.kt` — the hand-written migrations, from 22 → 23 on |
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
| `legacy-dev/` | Pre-patient migrations `0001`–`0013`, unedited, still the description of `agarthavision-dev` and `agarthavision-prod`, which `staging` and `main` point at. Never applied to `agarthavision`. Its `README.md` says why. Pre-consolidation numbers 0003, 0004 and 0006 name different files here, so cite them with the `legacy-dev/` prefix |

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
