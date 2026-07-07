# Scope: CIT-U brand rebrand (maroon/gold) + dashboard dark-mode toggle

## Summary

Replace the cobalt "clinical blue" design system with CIT-U maroon/gold branding across all
screens (KPI: zero remaining blue components), and add a persisted light/dark theme toggle
on the Dashboard. Single theme, two modes — no second theme system (AGENTS.md non-negotiable).

Approved by developer in-session: brand hex sampled from the CIT-U reference image
(maroon `#8C1823`, gold `#FFB81C`); logo vector recolored in-repo; toggle lives on Dashboard.

## Affected files

| File | Change description | Layer |
|---|---|---|
| `ui/theme/Color.kt` | Replace `Blue*` ramp with `Maroon*`/`Gold*`; swap slate neutrals for stone ramp; add dark-mode raw values (`Dark*`, `MaroonBright`); remains the only raw-hex site | Presentation |
| `ui/theme/Palette.kt` (new) | `AgarthaColors` semantic palette (background, surface, border, textPrimary, textSecondary, accent + states/tints, gold set, semantic red/green/amber + tints) with `Light`/`Dark` instances + `LocalAgarthaColors` | Presentation |
| `ui/theme/Theme.kt` | Light + dark Material 3 `ColorScheme`s (primary = maroon, secondary = gold); `AgarthaVisionTheme(darkTheme: Boolean)` provides `LocalAgarthaColors`; maroon hero shadow spec | Presentation |
| `MainActivity.kt` | Collect theme preference and pass `darkTheme` into `AgarthaVisionTheme` | Presentation |
| `domain/model/ThemeMode.kt` (new) | `enum class ThemeMode { LIGHT, DARK }` — pure Kotlin | Domain |
| `domain/repository/ThemePreferenceRepository.kt` (new) | `val themeMode: Flow<ThemeMode>` + `suspend fun setThemeMode(ThemeMode)` | Domain |
| `domain/usecase/settings/ObserveThemeModeUseCase.kt` (new) | Flow-returning observe use case (precedent: `ObserveSessionReportsUseCase`) | Domain |
| `domain/usecase/settings/SetThemeModeUseCase.kt` (new) | Returns `Result<Unit>` per Conventions §7 | Domain |
| `data/repository/ThemePreferenceRepositoryImpl.kt` (new) | DataStore Preferences-backed impl; defaults to `LIGHT` on first launch | Data |
| `core/di/PreferencesModule.kt` (new) | `@Singleton` `DataStore<Preferences>` provider + `@Binds` for the repository | Core |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Add `androidx.datastore:datastore-preferences` | Build |
| `ui/dashboard/DashboardViewModel.kt` | Theme mode in screen state + toggle intent via the two use cases | Presentation |
| `ui/dashboard/DashboardScreen.kt` | Sun/moon toggle icon button in header row (inline SVG, ≥44 px target, content description); rebrand stat tiles (Sessions = maroon, EPG = gold fill + dark text), maroon sparkline/hero | Presentation |
| `ui/sessions/SessionsScreen.kt` | Delete private blue palette copy (lines 48–64); consume `AgarthaTheme` tokens; maroon Active pill, maroon New-session CTA | Presentation |
| `ui/login/LoginScreen.kt` | Remove hardcoded `#1E3FD9`/gray hex; maroon title/CTA/links | Presentation |
| `ui/capture/CaptureScreen.kt` | Hardcoded blue dot → accent token; glass tint navy → warm charcoal; REC red unchanged | Presentation |
| `ui/capture/ConnectionLossBanner.kt` | Local `RedColor` → semantic token | Presentation |
| `ui/records/SessionDetailScreen.kt` | Stray purple `#7C3AED` badge → sanctioned token; tab/accent rebrand | Presentation |
| `ui/records/RecordsScreen.kt`, `ui/records/SampleDetailScreen.kt` | Token migration (tabs, back link, meta rows) | Presentation |
| `ui/verify/VerificationSheet.kt`, `ModalSheetComponents.kt`, `SpeciesDropdown.kt`, `FrameWithBoxes.kt`, `ManualSheet.kt`, `VerificationQueueScreen.kt` | Token migration; AI-suggested chip + detection bbox → maroon | Presentation |
| `ui/components/AgarthaButton.kt`, `AgarthaBadge.kt`, `AgarthaToast.kt`, `AgarthaBottomBar.kt`, `AppHeader.kt`, `DetectionOverlay.kt`, `MicroscopyViewport.kt`, `GlassModifiers.kt`, `SvgIcon.kt` | Token migration; bottom-bar active state maroon; microscope gradient navy → warm dark; remove hardcoded border hex | Presentation |
| `ui/navigation/AgarthaNavGraph.kt` | Token migration (scaffold backgrounds) | Presentation |
| `res/drawable/ic_logo.xml` | Recolor vector: maroon body + gold accent (removes blue `#036BFC`-family fills) | Resources |
| `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml` | Launcher icon recolor to match | Resources |
| `CONTEXT.md` §4 | New token block (maroon/gold/stone + dark), elevation shadows → maroon, supersede "dark mode stays off", gold-vs-amber usage rule | Docs |
| `TODO.md` | Record rebrand + dark mode under UI/Design System; add follow-ups (Settings theme control) | Docs |

## Schema / migration

None — theme preference is device-local via DataStore Preferences. No Room migration
(stays v7), no Supabase migration (stays `0008`), `schema.ts` untouched.

## Design tokens (UI tasks only)

Light (WCAG AA verified):

- `AppColors.Maroon #8C1823` — primary accent; 9.3:1 on white (CTAs, active states, focus rings, AI bboxes)
- `AppColors.MaroonHover #75141E` · `MaroonPressed #5E1018`
- `AppColors.MaroonTint #F9E8EA` · `MaroonTint2 #FCF3F4` — active cards / info banners
- `AppColors.Gold #FFB81C` — brand highlight, **fill-only with dark text** (11:1 with `Gray900`); never text-on-white
- `AppColors.GoldTint #FFF4D6` · `GoldText #7A5A00` (6.4:1 on white, 5.8:1 on tint)
- Neutrals: stone ramp `OffWhite #FAFAF9 · Gray50 #F5F5F4 · Gray100 #E7E5E4 · Gray200 #D6D3D1 · Gray300 #A8A29E · Gray500 #78716C · Gray700 #44403C · Gray900 #1C1917`
- Semantic unchanged: `Red #DC2626`, `Green #16A34A`, `Amber #D97706` (+tints). Rule: gold = brand, amber = caution, never adjacent; destructive stays bright red, visibly distinct from maroon.

Dark:

- `DarkBackground #171412` · `DarkSurface #1F1B18` · `DarkSurfaceAlt #262220` · `DarkBorder #37322E`
- `DarkTextPrimary #F5F5F4` (≥12:1) · `DarkTextSecondary #A8A29E` (≥4.5:1)
- `MaroonBright #D9707A` — dark-mode accent text/icons (5.3:1 on background); filled buttons keep `Maroon` + white label (9.3:1 internal)
- `Gold` unchanged (9.8:1 on dark background)
- Brightened semantic tints for dark surfaces (red/green/amber)

Typography (`Inter`, tabular numerals), spacing grid, radius scale, `DialogShape`: unchanged.
Capture screen remains always-dark (tool mode), independent of the toggle.

## Tests to add / update

- `SetThemeModeUseCaseTest` — persists mode; returns `Result.success`; surfaces repository failure.
- `ObserveThemeModeUseCaseTest` — emits repository flow; defaults to `LIGHT`.
- `ThemePreferenceRepositoryImplTest` — round-trip with an in-memory/fake DataStore; unknown stored value falls back to `LIGHT`.
- `DashboardViewModelTest` — update/add: toggle intent flips state and invokes `SetThemeModeUseCase`; initial state reflects observed mode.
- Existing test suite must stay green (rebrand is token-level; no behavioral change expected elsewhere).

## Architecture checks

- [x] No ViewModel imports Room / Retrofit / Supabase — Dashboard VM sees use cases only
- [x] No Android import in `domain/` — `ThemeMode` + repository interface are pure Kotlin; DataStore lives in `data/`
- [x] Use Case returns `Result<T>` — `SetThemeModeUseCase: Result<Unit>`; observe use case returns `Flow` (existing precedent)
- [x] Repository interface in `domain/`, impl in `data/`, bound via Hilt `@Binds`
- [x] New entity in `data/local/entity/`, domain model in `domain/model/` — no new entity; `ThemeMode` in `domain/model/`
- `@Singleton` only for the `DataStore` provider (app-scoped I/O), consistent with rule 7

## Known-issues cross-reference (TODO.md)

- "Settings screen is placeholder" (Sprint 3 #4): the toggle ships on Dashboard per request; mirroring it in Settings is deferred and noted as a follow-up.
- "Capture top-bar icon density" (Sprint 3 #5): no toggle on Capture — avoids worsening this issue; Capture is always dark anyway.
- "Theme consolidation still open" (Sprint 0 table): this task completes consolidation onto the single rebranded theme.

## Deferred

- Official CIT-U brand-guide hex swap (sampled values approved; swap is a `Color.kt`-only change if official values arrive).
- Theme control inside the Settings screen (belongs to Sprint 3 #4 Settings rework).
- Three-state "follow system" theme mode; `prefers-reduced-motion` handling.
- Box-editing, admin reports, and all other Phase 2 items — untouched.
- Physical-device E2E screenshot pass in both modes — executed in stage 03 QA, not here.
