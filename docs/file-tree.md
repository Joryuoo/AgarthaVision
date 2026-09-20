# File tree

Annotated. Generated from the real tree, then annotated with what each area is *for* — not a
file listing. For a specific file's behaviour, open the card in `map/`, not this page.

```
AgarthaVision/
├── SESSION_INIT.md            Session entry point. Read once, then route
├── README.md                  Human-facing project README
├── schema.ts                  Ground-truth data model, documentation only — never compiled
├── docs/                      This shelf. See docs/CONTEXT.md
│
├── app/                       The one Gradle module. Everything Android lives here
├── supabase/migrations/       Postgres schema + RLS (0001_init.sql; legacy-dev/ archive)
├── inference/                 The self-hosted FastAPI inference container
├── branding/                  Logo SVGs
├── tools/psgc/                Generator for the bundled PSGC asset. Run by hand, output committed
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
    │   ├── res/                   Launcher icons, strings, themes
    │   └── java/com/agarthavision/
    │       ├── MainActivity.kt · MainViewModel.kt · AgarthaVisionApp.kt
    │       ├── core/          Platform services. Everything Android-specific that is not UI
    │       ├── domain/        Pure Kotlin. Models, repository interfaces, use cases
    │       ├── data/          Room, Supabase, Retrofit, mappers, repository implementations
    │       └── ui/            Compose screens, ViewModels, navigation, theme
    ├── test/                  JVM unit tests, mirroring the main package layout.
    │                          ui/verify/ also holds Compose UI tests, run under Robolectric
    ├── androidTest/           Instrumented tests. Generated stub only so far
    └── debug/                 Debug-variant manifest
```

### `core/` — platform services

| Folder | For |
|---|---|
| `camera/` | `CameraManager` binds Preview + ImageAnalysis; `FrameSampler` caches every analyzed frame as time-stamped JPEG bytes — no timer, no dispatch to inference (the shutter does that) |
| `connectivity/` | `NetworkMonitor` polls inference `/health`; `ConnectivityObserver` reports device network state |
| `database/` | `AgarthaDatabase` — the Room database declaration and its version number (v16) |
| `di/` | Hilt modules. `DatabaseModule` also carries every repository `@Binds` |
| `session/` | `SessionManager` + `SessionState` + `ActiveSessionIdStore`. The app-scoped record of which smear is open, and the pointer that survives process death |
| `sync/` | `WorkManagerSyncScheduler` implementing the pure `domain/sync/SyncScheduler` port |
| `util/` | `ElapsedClock`, `DeviceIdProvider`, image conversion helpers |

### `domain/` — pure Kotlin

| Folder | For |
|---|---|
| `model/` | Domain models and the enums that define the vocabulary: `Patient`, `Sex`, `QueueSample`, `SampleStatus`, `DetectionVerdict`, `EggSpecies`, `FrameSource`, `ReportType`, `ReportSyncStatus`, `SessionSyncStatus` |
| `repository/` | Interfaces only. Implementations live in `data/` |
| `usecase/auth` | Sign in, sign out, observe identity |
| `usecase/patients` | Create, update, search, and observe patients |
| `usecase/sessions` | Start session, observe active session, picker, search barangays |
| `usecase/capture` | Infer a frame, persist a flagged frame, delete one |
| `usecase/inference` | Connection exception type and network error mapping |
| `usecase/verify` | Submit verification, open target, answer model, derived Q4, Add Egg |
| `usecase/sync` | The trigger-based catch-up pass in FK-safe order |
| `usecase/records` | Records list, session samples, sample detail, image source resolution, report generation, PDF building |
| `usecase/reports` | Per-species LPF range aggregation (`LpfAggregation.kt`) and session egg counts |
| `usecase/settings` | Theme mode; pending sync counts |

Contains no Android imports. Does import `data/` in a few boundary files — see `constraints.md` C3.

### `data/` — persistence and the network

| Folder | For |
|---|---|
| `local/entity/` | Room entities (`PatientEntity`, `PatientUserEntity`, `SessionEntity`, `SampleEntity`, `DetectionEntity`, `ReportEntity`, `SampleSpeciesFindingEntity`, `PsgcBarangayEntity`, `SpeciesSuggestionEntity`) |
| `local/dao/` | Room queries for each entity. The LPF query lives in `DetectionDao` |
| `local/mapper/` | Entity ↔ domain conversion, plus `VerificationMapper` which computes verdicts and derived IDs |
| `local/psgc/` | `PsgcSeeder` for bundled barangay dataset |
| `local/species/` | `SpeciesSuggestionSeeder` and repository for offline autocomplete index |
| `local/` | `SampleImageStore` — on-device JPEG files under `users/{owner}/samples/` |
| `remote/` | Retrofit interface to the inference container, and its DTOs |
| `supabase/` | Remote data sources and per-entity sync use cases (`PatientRemoteDataSource`, `SyncPatientUseCase`, etc.) |
| `sync/` | `SyncWorker` `@HiltWorker` executing background sync passes |
| `repository/` | Repository implementations, `FlaggedFrameStore`, report file store |

### `ui/` — Compose

| Folder | For |
|---|---|
| `navigation/` | `AgarthaNavGraph` — routes, start destination, bottom-bar visibility |
| `theme/` | The design system. Raw hex exists here and nowhere else |
| `icons/` | Material Symbols exports (`AgarthaIcons.kt`) |
| `components/` | Shared composables: buttons, badges, bottom bar, toast, capture frame boundary, microscopy viewport, glass modifiers, `EmptyState.kt` |
| `capture/` | The dark immersive capture screen and its connection-loss banner |
| `patients/` | Patients list, patient creation/edit form, and their ViewModels |
| `verify/` | Unified verification queue, one verification sheet, findings UI with Add Egg, box overlay with interactive drag primitive, species dropdown |
| `records/` | Records list, session detail with LPF range cards, sample detail, and report sharing |
| `dashboard/` `sessions/` `login/` `settings/` | The remaining screens |

## `supabase/migrations/`

Numbered, committed, applied by hand in the Supabase dashboard. Never run programmatically.

| Path | Purpose |
|---|---|
| `0001_init.sql` | Consolidated schema for patient records architecture on `agarthavision` (`profiles`, `patients`, `patient_users`, `sessions`, `samples`, `detections`, `sample_species_findings`, `reports`, base RLS) |
| `legacy-dev/` | Archived pre-patient migrations `0001`–`0013` as applied to `agarthavision-dev` and `agarthavision-prod` |

## `inference/`

`server.py` (the two endpoints), `Dockerfile` (ROCm PyTorch base), `requirements.txt`,
`weights/best.pt`, plus Kaggle notebook and notes. Not part of the Gradle build.

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
├── map/
│   ├── objects/        Nine object cards — the nouns (Patient, Profile, Session, Sample, Detection, Finding, Report, StorageObject, PsgcBarangay)
│   ├── processes/      Five process cards — the verbs
│   └── effects/        Change-impact index
└── _archive/           Superseded. Never implement against it
```

## Not in this repository

No `local.properties` — it is gitignored and you create it from `local.properties.example`.
No `TODO.md`, by rule.
