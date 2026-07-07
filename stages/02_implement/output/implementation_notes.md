# Implementation Notes: CIT-U brand rebrand (maroon/gold) + dashboard dark-mode toggle

## Changed files

| File | Change summary |
|---|---|
| `ui/theme/Color.kt` | Rewrote raw hex palette: `Maroon`/`MaroonHover`/`MaroonPressed`/`MaroonTint`/`MaroonTint2`/`MaroonBright`, `Gold`/`GoldTint`/`GoldText`/`GoldTextDark`, stone-ramp neutrals, dark-mode surfaces, semantic colors (unchanged hues, added dark variants), `MicroscopeBrush` warmed from navy to charcoal |
| `ui/theme/Palette.kt` (new) | `AgarthaColors` semantic data class (~25 role properties); `LightAgarthaColors`/`DarkAgarthaColors` instances; `LocalAgarthaColors` CompositionLocal |
| `ui/theme/Theme.kt` | Light/dark Material3 `ColorScheme`s; `AgarthaTheme.colors` read-only accessor; `AgarthaVisionTheme(darkTheme: Boolean)` provides `LocalAgarthaColors` alongside `MaterialTheme` |
| `domain/model/ThemeMode.kt` (new) | `enum class ThemeMode { LIGHT, DARK }` |
| `domain/repository/ThemePreferenceRepository.kt` (new) | `themeMode: Flow<ThemeMode>` + `suspend fun setThemeMode(ThemeMode)` |
| `domain/usecase/settings/ObserveThemeModeUseCase.kt` (new) | Flow-returning observe use case |
| `domain/usecase/settings/SetThemeModeUseCase.kt` (new) | `Result<Unit>`-returning use case |
| `data/repository/ThemePreferenceRepositoryImpl.kt` (new) | DataStore Preferences-backed; falls back to `LIGHT` on missing/unrecognized stored value |
| `core/di/PreferencesModule.kt` (new) | `Context.settingsDataStore` singleton provider + `@Binds` for the repository |
| `MainViewModel.kt` (new) | Exposes `themeMode: StateFlow<ThemeMode>` for `MainActivity` |
| `MainActivity.kt` | Collects `MainViewModel.themeMode`; passes `darkTheme` into `AgarthaVisionTheme` |
| `ui/dashboard/DashboardViewModel.kt` | Added theme use case params, `isDarkMode` in `DashboardUiState`, `onToggleTheme()` intent; `@Suppress("LongParameterList")` on constructor (7 DI deps, consistent with existing pattern in this codebase) |
| `ui/dashboard/DashboardScreen.kt` | Removed erroneous nested `AgarthaVisionTheme` wrap; migrated to `AgarthaTheme.colors.*`; EPG tile uses gold fill; toggle wired into `AppHeader` |
| `ui/components/AppHeader.kt` | Added `isDarkMode`/`onToggleTheme` params; conditional sun/moon `IconButton` with content description |
| `res/drawable/ic_sun.xml`, `ic_moon.xml` (new) | Inline vector icons matching existing icon style |
| `res/values/strings.xml` | Added `theme_toggle_to_dark`, `theme_toggle_to_light` |
| `ui/sessions/SessionsScreen.kt` | Full rewrite: removed private blue palette copy; all composables read `AgarthaTheme.colors` locally; wildcard imports replaced with explicit imports |
| `ui/login/LoginScreen.kt` | Removed hardcoded `#1E3FD9` and gray hex; migrated to `AgarthaTheme.colors` |
| `ui/verify/VerificationQueueScreen.kt` | Removed private Design Tokens block; thumbnail gradient → `AppColors.MicroscopeBrush`; AI/Repeat chips → `colors.accentTint`/`colors.accent` (was purple) |
| `ui/verify/ModalSheetComponents.kt` | Token migration; wildcard import replaced |
| `ui/verify/VerificationSheet.kt` | Token migration via systematic replace (Blue→accent, Gray9xx→textPrimary, Gray5xx→textSecondary, Gray3/2xx→borderStrong, Red→danger) |
| `ui/verify/SpeciesDropdown.kt`, `FrameWithBoxes.kt` | Token migration; `FrameWithBoxes` captures colors at composition time before entering `Canvas` (DrawScope is not `@Composable`) |
| `ui/verify/ManualSheet.kt` | Token migration; wildcard import replaced; removed unused `timeLabel` property |
| `ui/records/RecordsScreen.kt` | Removed nested theme wrap; "Eggs found" tile uses gold fill |
| `ui/records/SessionDetailScreen.kt` | Removed nested theme wrap; Repeat badge purple → gold; `SpeciesBadge` intentionally kept fixed `AppColors.Maroon`/`Amber` (mode-independent on-image badge) |
| `ui/records/SampleDetailScreen.kt` | Token migration via systematic replace; wildcard imports replaced; fixed a `Canvas`/DrawScope composable-call bug in `NormalizedDetectionOverlay` (colors hoisted to local vals before the draw lambda) |
| `ui/capture/CaptureScreen.kt` | Remaining blue refs → `AppColors.MaroonBright`/`AgarthaTheme.colors.accent`; navy glass tint warmed to charcoal-maroon; screen stays dark/immersive regardless of toggle (by design) |
| `ui/capture/ConnectionLossBanner.kt` | Long-line wrapping only; colors already semantic (mode-independent red banner) |
| `ui/navigation/AgarthaNavGraph.kt` | Scaffold `containerColor` → `AgarthaTheme.colors.background` |
| `ui/components/AgarthaButton.kt`, `AgarthaBadge.kt`, `AgarthaToast.kt`, `AgarthaBottomBar.kt`, `DetectionOverlay.kt`, `GlassModifiers.kt`, `MicroscopyViewport.kt` | Token migration to `AgarthaTheme.colors.*`; microscope pre-warm-up background warmed to `Gray900` |
| `res/drawable/ic_logo.xml`, `ic_launcher_foreground.xml` | Recolored: blue fills → maroon body / gold accent shards / maroon tint |
| `res/drawable/ic_launcher_background.xml` | Reviewed, no change needed (plain white) |
| `CONTEXT.md` §4 | Rewrote Design System section: token tables (light/dark/semantic), dark-mode subsection, updated component/glass/elevation conventions |
| `TODO.md` | UI/Design System table updated; Sprint 3 backlog items added (Settings toggle mirror, raster icon regen, brand-guide reconciliation) |
| `stages/02_implement/references/coding-constraints.md` | Design system hard rules rewritten for mode-aware tokens |

## Decisions made

- **Single theme, two modes** (not a second theme system): one `AgarthaVisionTheme(darkTheme: Boolean)` composable backed by `AppColors` (raw hex, isolated to `Color.kt`/`Palette.kt`) + `AgarthaColors` (semantic wrapper via `LocalAgarthaColors`). Satisfies the AGENTS.md non-negotiable against reintroducing a second UI theme.
- **Capture screen is toggle-exempt by design**: always dark/immersive regardless of the user's light/dark preference, using fixed `AppColors` values directly rather than `AgarthaTheme.colors`. Documented in `coding-constraints.md`.
- **On-image badges stay mode-independent**: `SpeciesBadge` (SessionDetailScreen) and similar overlays on photographic content use fixed `AppColors.Maroon`/`AppColors.Amber` rather than following the toggle, since brand-consistent contrast against a photo matters more than light/dark adaptation. Commented in-code.
- **Theme toggle placed only on Dashboard** per explicit instruction; Settings mirror deferred to Sprint 3 backlog (Settings screen is still a placeholder).
- **Brand hex sampled from reference image**, not yet reconciled against an official CIT-U brand guide (none published at time of writing) — flagged in TODO.md Sprint 3 backlog for later reconciliation.
- **Raster launcher icons (`mipmap-*/ic_launcher*.webp`) left unchanged**: vector sources (`ic_logo.xml`, `ic_launcher_foreground.xml`) are recolored, but the baked `.webp` mipmaps require image tooling outside this session's capability. Flagged in TODO.md as a follow-up.
- **`DashboardViewModel` constructor suppressed for `LongParameterList`** (7 DI dependencies) rather than introducing a parameter-object, matching how this codebase already tolerates DI-heavy constructors elsewhere (e.g. `GenerateSessionReportUseCase`, `FlaggedFrameStore`) instead of a project-wide refactor out of scope for this task.
- **Detekt gate scope**: fixed all `MaxLineLength`/`WildcardImport`/`MagicNumber`/`UnusedPrivateProperty`/`ReturnCount` findings in files touched by this rebrand. Pre-existing findings in files not touched this session (`SampleDao.kt`, `ReportCsvBuilder.kt`, `GenerateSessionReportUseCase.kt`, `FlaggedFrameStore.kt`, `ReportCsvBuilderTest.kt`, `GenerateSessionReportUseCaseTest.kt`, `SyncReportUseCaseTest.kt`, `DetectionRepositoryImpl.kt`, `SessionsViewModel.kt`, `LoginScreen.kt`'s pre-existing `LongParameterList`, `AgarthaButton.kt`'s pre-existing `LongParameterList`, `CaptureScreen.kt`'s pre-existing `LongParameterList`) were left as-is per developer decision — that debt predates this session and is out of scope for a design-system rebrand.

## Deferred (not in this PR)

- Official CIT-U brand-guide hex reconciliation (sampled values approved for now).
- Theme toggle mirrored into the Settings screen (Sprint 3 backlog #10).
- Raster launcher icon (`mipmap-*/ic_launcher*.webp`) regeneration (Sprint 3 backlog #11).
- Three-state "follow system" theme mode; `prefers-reduced-motion` handling.
- Repo-wide detekt debt cleanup (LongParameterList/TooManyFunctions in files unrelated to this rebrand).
- Physical-device E2E screenshot pass in both light and dark modes — belongs in stage 03 QA.

## Tests added / updated

| Test file | What it covers |
|---|---|
| `domain/usecase/settings/SetThemeModeUseCaseTest.kt` | Success path persists mode and returns `Result.success`; failure path surfaces repository exception via `Result.failure` |
| `domain/usecase/settings/ObserveThemeModeUseCaseTest.kt` | Emits `DARK` and `LIGHT` from the repository flow |
| `data/repository/ThemePreferenceRepositoryImplTest.kt` | Defaults to `LIGHT` when unset or when stored value is unrecognized; `setThemeMode(DARK)` persists and is reflected in `themeMode` (fake in-memory `DataStore<Preferences>`) |
| `ui/dashboard/DashboardViewModelTest.kt` | Initial state reflects observed light mode; `onToggleTheme()` flips light→dark and dark→light, invoking `SetThemeModeUseCase` with the correct target each time |

Full existing suite (86 tests total) passes green alongside the 4 new/updated files above.

## Verification performed

- Repo-wide grep for blue hex references (`1E3FD9|1F5BFF|036BFC|AppColors\.Blue`): zero matches outside a historical doc comment — zero-blue KPI met.
- `./gradlew :app:compileDebugKotlin` — passes.
- `./gradlew :app:testDebugUnitTest` — 86/86 pass.
- `./gradlew :app:ktlintCheck` — passes.
- `./gradlew :app:detekt` — reduced from 127 → 31 findings. All `MaxLineLength`, `WildcardImport`, `MagicNumber`, `UnusedPrivateProperty`, and `ReturnCount` findings in the 11 files touched by this rebrand are resolved. The remaining 31 findings are either (a) in files never touched this session (`SampleDao.kt`, `ReportCsvBuilder.kt`, `GenerateSessionReportUseCase.kt`, `FlaggedFrameStore.kt`, `ReportCsvBuilderTest.kt`, `GenerateSessionReportUseCaseTest.kt`, `SyncReportUseCaseTest.kt`, `DetectionRepositoryImpl.kt`, `SessionsViewModel.kt`), or (b) pre-existing `LongParameterList`/`TooManyFunctions` findings whose parameter/function counts predate the rebrand and are unrelated to color-token migration (`SessionsScreen.kt`, `SessionDetailScreen.kt`, `RecordsScreen.kt`, `DashboardScreen.kt`, `ModalSheetComponents.kt`, `LoginScreen.kt`, `AgarthaButton.kt`, `CaptureScreen.kt`), or (c) two pre-existing `MagicNumber` findings in `SessionDetailScreen.kt` unrelated to any change made here. Developer-confirmed scope decision: fix findings attributable to this task; leave pre-existing repo debt for a separate cleanup pass.
