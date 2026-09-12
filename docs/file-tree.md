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
├── supabase/migrations/       Postgres schema + RLS. The authority for the remote shape
├── inference/                 The self-hosted FastAPI inference container
├── branding/                  Logo SVGs
├── gradle/                    Wrapper + version catalog
└── .husky/                    Git hooks — the only mechanical rule enforcement in the repo
```

## `app/` — the Android client

```
app/
├── build.gradle.kts           Module config, BuildConfig secret plumbing, dependency list
├── schemas/                   Exported Room schema JSON, one file per version
└── src/
    ├── main/
    │   ├── AndroidManifest.xml    Permissions, single Activity, FileProvider for CSV sharing
    │   ├── res/                   Icons (hand-built stroke drawables), strings, themes
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
| `database/` | `AgarthaDatabase` — the Room database declaration and its version number |
| `di/` | Hilt modules. `DatabaseModule` also carries every repository `@Binds` |
| `location/` | `FusedLocationProvider` — GPS behind a domain interface, returns null rather than throwing |
| `session/` | `SessionManager` + `SessionState`. The app-scoped record of which smear is open |
| `util/` | `EpgCalculator` (the multiplier), `DeviceIdProvider`, image conversion helpers |

### `domain/` — pure Kotlin

| Folder | For |
|---|---|
| `model/` | Domain models and the enums that define the vocabulary: `SampleStatus`, `DetectionVerdict`, `EggSpecies`, `FrameSource`, `ReportType`, `ReportSyncStatus`, `SessionSyncStatus` |
| `repository/` | Interfaces only. Implementations live in `data/` |
| `usecase/auth` | Sign in, sign out, observe identity, claim unowned local data |
| `usecase/capture` | Infer a frame, persist a flagged frame, delete one |
| `usecase/inference` | Connection exception type and network error mapping |
| `usecase/verify` | Submit a verification, submit a manual capture, the answer model |
| `usecase/sync` | The trigger-based catch-up pass |
| `usecase/records` | Records list, session samples, sample detail, image source resolution, report generation, CSV building |
| `usecase/reports` | Per-session egg counts and EPG |
| `usecase/sessions` `usecase/settings` | Claim-exempt toggle; theme mode; pending sync counts |

Contains no Android imports. Does import `data/` in eleven files — see `constraints.md` C3.

### `data/` — persistence and the network

| Folder | For |
|---|---|
| `local/entity/` | Room entities. Room-only columns live here and are documented as such |
| `local/dao/` | Room queries. The EPG aggregate query lives in `DetectionDao` |
| `local/mapper/` | Entity ↔ domain conversion, plus `VerificationMapper` which computes the verdict |
| `local/` | `SampleImageStore` — on-device JPEG files under `users/{owner}/samples/` |
| `remote/` | Retrofit interface to the inference container, and its DTOs |
| `supabase/` | Remote data sources and the per-entity sync use cases |
| `repository/` | Repository implementations, `FlaggedFrameStore`, the CSV file store |

### `ui/` — Compose

| Folder | For |
|---|---|
| `navigation/` | `AgarthaNavGraph` — routes, start destination, bottom-bar visibility |
| `theme/` | The design system. Raw hex exists here and nowhere else |
| `components/` | Shared composables: buttons, badges, bottom bar, toast, capture frame boundary, microscopy viewport, glass modifiers, hand-drawn icons |
| `capture/` | The dark immersive capture screen and its connection-loss banner |
| `verify/` | Verification queue, the one verification sheet (both sources), findings UI, box overlay, species dropdown |
| `records/` | Records list, session detail, sample detail, and their cards |
| `dashboard/` `sessions/` `login/` `settings/` | The remaining screens |

## `supabase/migrations/`

Numbered, committed, applied by hand in the Supabase dashboard. Never run programmatically.

| File | Adds |
|---|---|
| `0001_init.sql` | `profiles`, `sessions`, `samples`, `detections`, the new-user trigger, base RLS |
| `0002_verification_fields.sql` | `detections.verdict` + CHECK, `expert_class`, `samples.needs_reannotation`; renames the model-version column; drops `verified_by_user` |
| `0003_storage_rls.sql` | Owner-scoped policies on the private `samples` bucket. Deliberately no DELETE policy |
| `0004_fix_profiles_rls_recursion.sql` | `public.is_admin(uuid)` and the policies that use it |
| `0005_session_label.sql` | `sessions.label` |
| `0006_sample_is_manual.sql` | `samples.is_manual` |
| `0007_detection_bbox_nullable.sql` | Nullable bounding boxes for manual captures |
| `0008_reports.sql` | The `reports` table, its indexes, and its RLS |
| `0009_storage_admin_read.sql` | Admin read access to the `samples` bucket. Read-only by design — still no DELETE policy |
| `0010_verification_stage.sql` | `detections.stage` for optional egg/parasite stage classification |
| `0011_reports_pdf_and_lpf.sql` | `reports.pdf_file_path` |
| `0012_polyparasitism_findings.sql` | `sample_species_findings`, `detections.species_touched`, and the UPDATE policies an editable sample needs to re-sync |
| `0013_sample_soft_delete.sql` | `samples.deleted_at` and the partial index over live rows |

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
│   ├── objects/        Six object cards — the nouns
│   ├── processes/      Five process cards — the verbs
│   └── effects/        Change-impact index
└── _archive/           Superseded. Never implement against it
```

## Not in this repository

No `.github/` — there is no CI. No `local.properties` — it is gitignored and you create it
from `local.properties.example`. No `TODO.md`, by rule.
