# Implementation Notes: Production Settings screen (PR2 of 2, Settings scope)

## Changed files

| File | Change summary |
|---|---|
| `domain/model/PendingSyncCounts.kt` (new) | `data class PendingSyncCounts(pendingSessions, pendingSamples, pendingReports, failed)` + `totalPending`/`allSynced` derived properties |
| `domain/usecase/settings/ObservePendingSyncCountsUseCase.kt` (new) | Combines the three DAO count flows into one `Flow<PendingSyncCounts>` for a given `userId` |
| `data/local/dao/SessionDao.kt`, `SampleDao.kt`, `ReportDao.kt` | Added `observePendingCount(userId): Flow<Int>` / `observeFailedCount(userId): Flow<Int>` query-only methods per DAO (no schema change) |
| `ui/settings/SettingsViewModel.kt` (new) | `StateFlow<SettingsUiState>` combining identity, connectivity, theme mode, pending-sync counts, syncing state; intents `onToggleTheme`, `onSyncNow`, `onSignOut`; one-shot `SharedFlow<SettingsEvent>` for `SignedOut`/`SignOutBlocked` |
| `ui/settings/SettingsScreen.kt` (new) | Top-level screen composable, `SettingsContent`, sign-out dialogs, preview |
| `ui/settings/SettingsCards.kt` (new) | `SettingsSection`, `SettingsCard`, `AccountCard`, `SyncCard` (+ `SyncCardState`, `SyncCounts`, `SyncCountRow`, `SyncStatusBadge`) |
| `ui/settings/SettingsAppearanceAboutCards.kt` (new) | `AppearanceCard`, `AboutCard` (+ `AboutLabel`/`AboutValue`) — split out to keep each file under detekt's function-count threshold |
| `ui/settings/SettingsScreenPlaceholder.kt` | Deleted |
| `ui/navigation/AgarthaNavGraph.kt` | Wired `SettingsScreen` in place of the placeholder; `onSignInClick` navigates to `Screen.Login.route` |
| `app/src/main/res/values/strings.xml` | Added `settings_*` string block (titles, account/sync/appearance/about copy, sign-out dialog) |
| `app/src/test/.../SyncReportUseCaseTest.kt`, `GenerateSessionReportUseCaseTest.kt` | Added `observePendingCount`/`observeFailedCount` stubs to the two `ReportDao` fakes — required by the new interface methods, unrelated to those tests' assertions |
| `app/src/test/.../ui/settings/SettingsViewModelTest.kt` (new) | 7 tests covering signed-in/never-signed-in initial state, theme toggle persistence, sync-now gating (signed-in+online vs. signed-out), and both sign-out event outcomes |

## Decisions made

- **No new drawable icons.** The design system rule is "no external icon library / hand-crafted inline SVG only," and this task didn't warrant new SVG assets for a first pass — the sync/connectivity/theme states are communicated through text + semantic color (success/warning/danger tints), matching `DashboardScreen`'s existing account/sync banner, which also has no dedicated icon.
- **`SyncCard` takes a `SyncCardState` data class instead of 5 primitive params.** Originally written with 6 individual parameters, which tripped detekt's `LongParameterList` (threshold 6). Bundling into a state object is a real simplification (fewer things to keep positionally aligned at call sites), not just a lint dodge.
- **Split into three files** (`SettingsScreen.kt`, `SettingsCards.kt`, `SettingsAppearanceAboutCards.kt`) to stay under detekt's per-file `TooManyFunctions` threshold (11). This mirrors the existing codebase pattern where large screens (`DashboardScreen.kt`, `SessionDetailScreen.kt`) already sit at or over that threshold and are accepted as debt — rather than add to that debt, this task split proactively.
- **`@Suppress("LongParameterList")` on `SettingsViewModel`'s constructor** (7 DI params), matching the identical justified precedent on `DashboardViewModel` — both are composition roots for their screen and every parameter is a distinct, non-overlapping dependency.
- **`About` section reads `BuildConfig.VERSION_NAME`/`BuildConfig.DEBUG` directly** rather than adding a dedicated use case — this is build metadata, not app state, and every other screen in the codebase that needs build info reads `BuildConfig` inline.

## Deferred (not in this PR)

- New Settings-specific iconography (sign-out, sync, account icons) — flagged as a possible follow-up if the design review asks for it; out of scope per the "no icon library, hand-crafted SVG" rule without an explicit design ask.
- Nothing else from the original scope draft was deferred; both PR1 (sign-out core) and PR2 (this PR) together implement the full `stages/01_scope/output/scope.md`.

## Tests added / updated

| Test file | What it covers |
|---|---|
| `ui/settings/SettingsViewModelTest.kt` (new) | Initial state (signed-in / never-signed-in), theme toggle persistence, `onSyncNow` gating on identity + connectivity, `onSignOut` success (`SignedOut` event) and failure (`SignOutBlocked` event) |
| `data/supabase/SyncReportUseCaseTest.kt` | Interface-compliance fix only (`FakeReportDao`) |
| `domain/usecase/records/GenerateSessionReportUseCaseTest.kt` | Interface-compliance fix only (`NoOpReportDao`) |

## Verification

- `:app:compileDebugKotlin`: BUILD SUCCESSFUL
- `:app:compileDebugUnitTestKotlin`: BUILD SUCCESSFUL
- `:app:testDebugUnitTest`: BUILD SUCCESSFUL, all tests pass
- `ktlintCheck`: 0 violations
- `detekt`: 40 findings — identical to the PR1 baseline; zero attributable to any file touched in this PR (verified via full detekt output grep for `settings`/new filenames — no matches)
