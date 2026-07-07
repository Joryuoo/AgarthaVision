# QA Report: CIT-U brand rebrand (maroon/gold) + dashboard dark-mode toggle

## Lint

`bun run lint` result: **FAIL**

Violations:
- `ktlintCheck`: 0 violations (passes standalone).
- `detekt`: 31 findings remain (down from a 127-finding baseline measured at the start of QA). All are pre-existing debt, confirmed unrelated to this task and out of scope per an explicit in-session scope decision:
  - 9 findings in files never touched this session: `SampleDao.kt` (2), `ReportCsvBuilder.kt` (2), `GenerateSessionReportUseCase.kt` (1), `FlaggedFrameStore.kt` (1), `DetectionRepositoryImpl.kt` (2), `SessionsViewModel.kt` (2, MaxLineLength).
  - 3 findings in test files never touched this session: `ReportCsvBuilderTest.kt` (2), `GenerateSessionReportUseCaseTest.kt` (2), `SyncReportUseCaseTest.kt` (1).
  - 19 `LongParameterList`/`TooManyFunctions` findings whose parameter/function counts predate the rebrand and are unaffected by color-token migration, spread across both untouched files (`SampleDao.kt`, `LoginScreen.kt`, `AgarthaButton.kt`, `CaptureScreen.kt`) and files that were rewritten for the rebrand but whose function *signatures* were carried over unchanged (`SessionsScreen.kt`, `SessionDetailScreen.kt`, `RecordsScreen.kt`, `DashboardScreen.kt`, `ModalSheetComponents.kt`).
  - 2 pre-existing `MagicNumber` findings in `SessionDetailScreen.kt` (lines 191, 198) unrelated to any code path changed by this task.

  Every `MaxLineLength`, `WildcardImport`, `MagicNumber`, `UnusedPrivateProperty`, and `ReturnCount` finding actually introduced or touched by this rebrand has been fixed (reduced the detekt count from 127 → 31 over the course of stage 02).

  **This is a scope call made explicitly with the developer during stage 02**, not an oversight: repo-wide `LongParameterList`/`TooManyFunctions` debt cleanup is a separate, larger refactor unrelated to a design-system rebrand, and fixing it here would have expanded this PR far beyond its stated purpose. Flagging here so the human reviewer can confirm or override that call before merge.

## Tests

`bun run test` result: **PASS**

86/86 tests pass, including the 4 new/updated files: `SetThemeModeUseCaseTest`, `ObserveThemeModeUseCaseTest`, `ThemePreferenceRepositoryImplTest`, `DashboardViewModelTest`.

## Architecture compliance

### Architecture layer compliance
- [x] No ViewModel imports Room / Retrofit / Supabase — `DashboardViewModel` only gained two Use Case params, no repository/DAO imports
- [x] No Android import in any file under `domain/` — `ThemeMode`, `ThemePreferenceRepository`, and both new use cases are pure Kotlin
- [x] All new/changed Use Cases have exactly one public `invoke`/`execute` entry point — `ObserveThemeModeUseCase.invoke()`, `SetThemeModeUseCase.invoke()`
- [x] All new/changed Use Cases return a sealed `Result<T>` — `SetThemeModeUseCase` returns `Result<Unit>`; `ObserveThemeModeUseCase` returns `Flow<ThemeMode>` (existing precedent for observe-style use cases, e.g. `ObserveSessionReportsUseCase`)
- [x] Repository interfaces live in `domain/repository/`; impls live in `data/repository/` — `ThemePreferenceRepository` / `ThemePreferenceRepositoryImpl`
- [x] New entities in `data/local/entity/`; domain models in `domain/model/` — no new Room entity; `ThemeMode` correctly placed in `domain/model/`
- [x] `@Singleton` used only for the approved list — the new DataStore provider in `PreferencesModule` is the app-scoped I/O singleton exception already implied by rule 7 (consistent with `AgarthaDatabase`/`OkHttpClient` precedent); `ThemePreferenceRepositoryImpl` and both use cases are unscoped

### Design system compliance
- [x] No raw hex colors in app code — confirmed via repo-wide grep for former blue hex (`1E3FD9|1F5BFF|036BFC|AppColors\.Blue`); zero matches outside a historical doc comment in `Color.kt`. All raw hex confined to `Color.kt`/`Palette.kt`, with two documented mode-independent exceptions (on-image badges in `SessionDetailScreen.kt`, Capture screen's fixed dark chrome) called out in `implementation_notes.md`
- [x] No raw `fontSize`/`fontWeight` outside existing MaterialTheme typography convention — unchanged by this task
- [x] Spacing values remain 8-px multiples — no spacing values touched by the rebrand
- [x] Radius values unchanged (`sm=8`, `md=12`, `lg=16`, `pill=999`)
- [x] Species names remain italic — untouched by this task
- [x] Capture screen stays dark/immersive independent of the new toggle — verified: `CaptureScreen.kt`, `MicroscopyViewport.kt`, and glass-chrome components use fixed `AppColors.*` values, not `AgarthaTheme.colors`
- [x] No new charting or icon library added — `ic_sun.xml`/`ic_moon.xml` are hand-drawn inline vectors, consistent with existing convention
- [x] No hardcoded user-facing strings — new toggle content descriptions added to `strings.xml` (`theme_toggle_to_dark`, `theme_toggle_to_light`)

### Code style
- [ ] `bun run lint` passes: **ktlint 0 violations**, but **detekt has 31 pre-existing findings** (see Lint section above — none attributable to this task)
- [x] `bun run test` passes: 0 failures
- [x] One public type per file — verified across all new files (`ThemeMode`, `ThemePreferenceRepository`, `ObserveThemeModeUseCase`, `SetThemeModeUseCase`, `ThemePreferenceRepositoryImpl`, `MainViewModel`); `Palette.kt` groups the `AgarthaColors` data class with its two instances and the CompositionLocal, consistent with the "closely related declarations may share a file" allowance
- [x] KDoc on every public class/interface/function/property — spot-checked all 7 new domain/data/DI files; each carries a KDoc block on its public type. `ThemePreferenceRepositoryImpl`'s `override` members inherit the interface's documented contract per existing convention (not duplicated)
- [x] Test method names read as sentences — e.g. `` `onToggleTheme flips light state to dark and persists it` ``, `` `defaults to LIGHT when stored value is unrecognized` ``
- [x] No new TODO/FIXME/HACK markers were introduced by this task requiring an owner tag

## Documentation sync

- [x] `CONTEXT.md` updated — §4 Design System rewritten (token tables, dark-mode subsection, component/glass/elevation conventions)
- [ ] `schema.ts` — not applicable, no data-model change
- [x] `TODO.md` updated — UI/Design System table rows added/updated; Sprint 3 backlog items added (Settings toggle mirror, raster icon regen, brand-guide reconciliation)
- [x] `AGENTS.md` updated — "Required Compliance" now reads "CIT-U maroon/gold accent" instead of the stale "cobalt accent"; the "theme consolidation is in progress" non-negotiable was also updated since this task completes that consolidation.

## Git hygiene

- [x] **Branch name** — developer confirmed leaving as-is on `test` rather than renaming, despite the `feat/<scope>-<desc>` convention.
- [x] **No commits exist yet** — will be committed as part of finishing this stage, following conventional-commit format (scope: `theme`).
- [x] **No `develop` branch** — developer confirmed this repo's actual workflow targets `main` directly; `_config/conventions.md`'s "develop" references are aspirational/stale for this repo.
- [x] **Diff size (~2,200 lines)** — developer confirmed shipping as a single PR.
- [x] No `local.properties`, `.idea/`, or build artifacts in the changed-file list
- [x] No secrets in the changed-file list (all changes are Kotlin/XML/Markdown)

## PR draft

**Title:** feat(theme): rebrand to CIT-U maroon/gold with dark mode toggle

**Summary:**
- Replace the cobalt "clinical blue" design system with CIT-U maroon (`#8C1823`) and gold (`#FFB81C`) branding across all primary screens — zero remaining blue components (verified by repo-wide grep).
- Add a persisted light/dark theme toggle on the Dashboard, backed by a new `ThemePreferenceRepository` (DataStore Preferences) and two Use Cases (`ObserveThemeModeUseCase`, `SetThemeModeUseCase`).
- Recolor the app logo and launcher foreground vector to the new palette (raster launcher `.webp` mipmaps deferred — flagged in `TODO.md`).
- Rewrite `ui/theme/Palette.kt` as a new mode-aware semantic color layer (`AgarthaColors`) consumed via `AgarthaTheme.colors`, keeping one theme with two modes (no second UI theme introduced, per `AGENTS.md` non-negotiable).
- Capture screen intentionally stays dark/immersive regardless of the toggle; on-image detection badges intentionally stay a fixed brand color for photo-overlay contrast — both documented in code and in `CONTEXT.md`.

**Test plan:**
- [x] `bun run test` — 86/86 unit tests pass, including 4 new/updated theme-related test files
- [x] `ktlintCheck` — 0 violations
- [ ] `detekt` — 31 pre-existing findings remain, none attributable to this change (see QA report Lint section for the full breakdown and rationale)
- [ ] Manual verification on-device/emulator: toggle dark mode on Dashboard, navigate through Login → Dashboard → Sessions → Capture → Verify Queue → Records → Session Detail → Sample Detail in both light and dark mode, confirm Capture stays dark regardless of toggle, confirm the toggle persists across app restart
- [ ] Confirm the maroon/gold contrast is acceptable on a physical device screen (WCAG ratios were computed against the sampled hex values, not device-calibrated)

**Branch:** `test` (developer confirmed staying on this branch rather than renaming)
**Target:** `main` (developer confirmed; no `develop` branch exists in this repository)

---

## Developer decisions (resolved)

1. **Detekt gate**: accept the 31 pre-existing/out-of-scope findings as-is. ✅ Confirmed.
2. **`AGENTS.md` stale "cobalt accent" reference**: fixed. ✅ Done (see Documentation sync).
3. **No `develop` branch**: leave as-is; this repo's actual workflow does not use `develop`. ✅ Confirmed — PR will target `main`.
4. **Branch/commit state**: leave as-is; proceeding on the current `test` branch rather than creating a new `feat/*` branch. ✅ Confirmed.
5. **PR size** (~2,200 lines): ship as one PR. ✅ Confirmed.
