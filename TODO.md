# AgarthaVision TODO

Current implementation audit and next-step list. This file consolidates the former sprint
plans and progress reports after checking the current codebase.

Legend: ✅ done · ⏳ partial · ❌ not started

## Implemented Features (by module)

### Sprint 0 / Foundation

| Track | Status | Codebase check |
|---|---:|---|
| Android project setup, Gradle version catalog, Bun scripts | ✅ | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `package.json` |
| Local secrets through `local.properties` / BuildConfig | ✅ | `local.properties.example`, app Gradle BuildConfig fields |
| MVVM + Clean Architecture package layout | ✅ | `core`, `domain`, `data`, and `ui` packages exist; Phase 2 worker package is not present |
| Hilt dependency injection | ✅ | `core/di/*Module.kt`, `@HiltAndroidApp`, injected ViewModels/use cases |
| Room database + schema export | ✅ | `AgarthaDatabase`, DAOs, entities, `app/schemas/.../1.json` through `7.json` |
| Navigation shell and core theme | ⏳ | Navigation and screens exist; theme consolidation still open |

### Module 1A / Mobile Capture Client

| Track | Status | Codebase check |
|---|---:|---|
| CameraX preview and frame analysis | ✅ | `core/camera/CameraManager.kt`, `FrameSampler.kt`, `ui/capture/CaptureScreen.kt` |
| One-frame-per-2s sampling with in-flight skip | ✅ | `FrameSampler` |
| Inference request/response DTOs and Retrofit API | ✅ | `data/remote/InferenceApi.kt`, DTOs, mappers |
| Capture metadata binding: UUID, timestamp, GPS, device ID, session ID | ✅ | capture/inference use cases, `LocationProvider`, `DeviceIdProvider`, `SessionManager` |
| Cloud connection monitor stops recording on inference outage | ✅ | `NetworkMonitor`, capture ViewModel integration |
| Manual capture for missed eggs | ✅ | `SubmitManualCaptureUseCase`, `ManualCaptureSheet`, nullable bbox support |

### Module 2A / Cloud Backend and Sync

| Track | Status | Codebase check |
|---|---:|---|
| Supabase Auth login/session restore/logout | ✅ | `AuthRepositoryImpl`, auth use cases, login UI |
| Postgres migrations for profiles/sessions/samples/detections/storage/reports | ✅ | `supabase/migrations/0001` through `0008` |
| Private Storage bucket path convention and signed upload | ✅ | `SampleRemoteDataSource`, `SupabaseSampleImageRepository` |
| Sample + detection sync on verification | ✅ | `SyncSampleUseCase`, `SampleRemoteDataSource` |
| Report metadata sync | ✅ | `SyncReportUseCase`, `ReportRemoteDataSource` |
| Retry behavior for failed sample/report syncs | ⏳ | sample retry exists around session flow; report retry/backoff needs hardening |
| WorkManager/offline sync queue | ❌ | No worker package currently; Phase 2 will add WorkManager-backed sync |

### Module 3 / In-App Verification

| Track | Status | Codebase check |
|---|---:|---|
| Flagged-frame queue and detection toast | ✅ | `FlaggedFrameStore`, capture UI/ViewModel |
| Verification sheet with per-box verdicts | ✅ | `VerificationSheet`, `SubmitVerificationUseCase` |
| Persist rejected detections as labeled data | ✅ | `DetectionVerdict`, `DetectionEntity`, migration `0002` |
| Wrong-class correction and missed-egg reannotation flag | ✅ | verification UI/use cases and `needs_reannotation` |
| Box editing in app | ❌ | explicitly deferred; use offline annotation tools when needed |
| Persistent flagged queue across restart/logout | ❌ | Phase 2 |

### Module 4 / Records, EPG, and Reports

| Track | Status | Codebase check |
|---|---:|---|
| Records list and session detail | ✅ | `ui/records`, `SessionDetailScreen`, DAOs/use cases |
| Sample detail with local image plus Supabase signed-URL fallback | ✅ | `ResolveSampleImageSourceUseCase`, `SupabaseSampleImageRepository` |
| User notes and repeat flag | ✅ | sample/domain/entity fields and records UI |
| EPG calculation with repeat exclusion | ✅ | `EpgCalculator`, `SessionEggCountUseCase`, unit tests |
| CSV session report generation | ✅ | `GenerateSessionReportUseCase`, report tests |
| Persisted Room/Supabase report rows | ✅ | `ReportEntity`, `ReportDao`, migration `0008`, sync data source |
| Share generated CSV via FileProvider | ⏳ | sharing path exists; device/manual verification still needed |
| Administrative cross-session reports and PDF output | ❌ | Phase 2 |

### UI / Design System

| Track | Status | Codebase check |
|---|---:|---|
| CIT-U maroon/gold rebrand (replaces cobalt "Clinical Pulse" palette) | ✅ | `AppColors` (maroon/gold/stone raw values), `ui/theme/Palette.kt` (`AgarthaColors` mode-aware wrapper), all screens migrated off `AppColors.Blue*` |
| Light/dark theme toggle | ✅ | `ThemeMode`, `ThemePreferenceRepository` (DataStore-backed), `ObserveThemeModeUseCase`/`SetThemeModeUseCase`, `MainViewModel`, toggle in `AppHeader` on Dashboard |
| Clinical microscopy typography, spacing, component tokens | ✅ | MaterialTheme, spacing, typography, and Agartha components are the canonical token surface |
| Capture dark immersive mode (theme-toggle-exempt) | ✅ | capture UI components; glass tint warmed from navy to charcoal-maroon |
| Login, Dashboard, Session Picker, Capture, Verify Queue, Records, Session Detail, Sample Detail | ✅ | screen packages exist |
| Settings screen | ⏳ | placeholder exists, full settings experience not implemented; theme toggle currently lives only on Dashboard (see Sprint 3 backlog) |
| Removal of KomoUI usage | ✅ | dependency, imports, and app-source references removed; final grep/compile audit passed |
| App icon (`mipmap-*/ic_launcher*.webp`) | ❌ | Raster launcher icons are still the old blue mark — vector `ic_logo.xml`/`ic_launcher_foreground.xml` are recolored, but the baked `.webp` mipmaps need regenerating with image tooling outside this session |

### Testing / CI

| Track | Status | Codebase check |
|---|---:|---|
| Unit tests for EPG, reports, sync, records, auth/capture ViewModels | ✅ | `app/src/test/...` |
| ktlint/detekt scripts | ✅ | `package.json`, Gradle plugins |
| GitHub Actions workflows and PR template | ❌ | `.github/` is not present |
| Full real-device E2E pass | ⏳ | required for Sprint 3 hardening |

## Known Issues / Technical Debt

- Settings screen is still a placeholder and needs real options, session/account affordances, and QA copy.
- Capture top bar is near its icon-density limit; Settings/Help should move behind an overflow menu.
- Report sync retry behavior is not robust yet; `sync_failed` report rows need background retry/backoff or an explicit manual retry affordance.
- `.github` CI workflows and pull-request template are documented but not created.
- `0008_reports.sql` uses an inline admin-role subquery instead of the `public.is_admin(uuid)` helper used by migration `0004`.
- Administrative report types are deferred; Supabase currently CHECKs `report_type in ('session')` only.
- Optional `detections.rejection_note` / reviewer free-text reason is deferred.
- Shared sync-state abstractions are duplicated (`SampleStatus`, `ReportSyncStatus`); consider a common local sync helper when adding WorkManager.
- Supabase production project/application status should be confirmed before demos or deployments; migrations are committed but applied manually.

## Sprint 3 Backlog

1. Run a full physical-device E2E pass: login, start session, inference health, flag frame, verify, manual capture, end session, records, report generation, share.
2. Confirm `0008_reports.sql` is applied in the active Supabase project and that report rows sync with RLS enabled.
3. Harden report/sample retry behavior for connectivity resume, or clearly defer it behind a manual retry state.
4. Replace remaining placeholder Settings UI with production actions and account/session information.
5. Add Capture overflow menu for secondary actions so top-bar density stays readable on narrow devices.
6. Add `.github` workflows for build/test/lint/commitlint plus a PR template.
7. Add the Kato-Katz multiplier citation near `EpgCalculator.MULTIPLIER`.
8. Normalize report/admin RLS style by replacing the `0008` inline admin check with `public.is_admin(uuid)` in a follow-up migration.
9. Re-run lint/tests/build after documentation cleanup and record the result in the PR.
10. Mirror the light/dark theme toggle into the Settings screen once its production UI is built (currently Dashboard-only).
11. Regenerate the raster `mipmap-*/ic_launcher*.webp` launcher icons in CIT-U maroon/gold (vector sources are already recolored; only the baked mipmaps are stale).
12. If an official CIT-U brand guide is published, reconcile the sampled hex values in `AppColors` (`#8C1823` maroon, `#FFB81C` gold) against it.

## Phase 2 Roadmap

- Replace phone-camera capture with USB OTG frames from a modular microscope capture device and dedicated on-prem inference hardware.
- Move from managed Supabase to self-hosted FastAPI + PostgreSQL in a Philippine region, with MinIO/NAS storage and owned GPU inference.
- Add a DOH-validated `prep_methods` table to replace the hardcoded EPG multiplier.
- Build offline-first sync with WorkManager, durable SyncQueue rows, backoff, and conflict handling.
- Persist the per-account flagged-frame queue across process death, logout, and device restart.
- Add `validation_records` as a first-class audit trail for review/correction events.
- Add administrative cross-session reporting, PDF output, and DOH-formatted exports.
- Add model-training feedback workflows using confirmed/false-positive/wrong-class detections.

## Deferred Documentation

- The former `docs/class/*.md` and `docs/sequence/*.md` files contained Mermaid UML diagram specs. They were intentionally not carried forward as source-of-truth prose because class and sequence diagrams can be regenerated from code when needed.
- Historical ADR and sprint-progress details are now collapsed into `CONTEXT.md` and this file. Keep new decisions close to the code change and update the root docs only when the actual contract, workflow, or backlog changes.
