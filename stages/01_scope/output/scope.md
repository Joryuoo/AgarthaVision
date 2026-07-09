# Scope: Detekt Cleanup Chore — retire the 40-finding tracked-debt baseline

## Summary

Resolve the repo-wide detekt debt (`TODO.md` Known Issues: "40 findings ... accepted at
the last two QA gates ... needs a dedicated cleanup chore") by fixing what's genuinely
fixable and explicitly, individually suppressing (with justification comments) what isn't —
so the baseline becomes **0 unaddressed findings**, and the stage-03 zero-violation gate can
be enforced literally again instead of waived.

Current baseline (verified against `app/build/reports/detekt/detekt.txt`, generated
2026-07-09 during the Settings PR2 QA pass — no source has changed since): **40 findings**,
7 rule types, across 20 files (`main` + `test`).

| Rule | Count |
|---|---:|
| `LongParameterList` | 18 |
| `MaxLineLength` | 10 |
| `MagicNumber` | 5 |
| `TooManyFunctions` | 4 |
| `LongMethod` | 1 |
| `ReturnCount` | 1 |
| `UseCheckOrError` | 1 |
| **Total** | **40** |

## Affected files

No behavior changes anywhere in this chore — every fix is either a mechanical reflow, a
named-constant extraction, a parameter-bundling refactor (call sites updated, output
unchanged), or a documented `@Suppress`. Grouped by the PR split below.

| File | Finding(s) | Change description | Layer |
|---|---|---|---|
| `data/repository/DetectionRepositoryImpl.kt:33,35` | MaxLineLength ×2 | Wrap long `override`/lambda lines | Data |
| `data/local/dao/SampleDao.kt:47,50` | MaxLineLength ×2 | Wrap long `@Query` annotation strings | Data |
| `ui/sessions/SessionsScreen.kt:441` | MaxLineLength | Wrap long inline SVG path string constant | Presentation |
| `ui/sessions/SessionsViewModel.kt:74,177` | MaxLineLength ×2 | Wrap long filter lambda / string template | Presentation |
| `test/.../GenerateSessionReportUseCaseTest.kt:170,247` | MaxLineLength ×2 | Wrap long fake-override signatures | Test |
| `test/.../LocalIdentityCacheTest.kt:49` | MaxLineLength | Wrap long `LocalIdentity(...)` constructor call | Test |
| `test/.../SyncReportUseCaseTest.kt:123` | UseCheckOrError | `throw IllegalStateException(...)` → `error(...)` in stub | Test |
| `ui/records/SessionDetailScreen.kt:191,198` | MagicNumber ×2 | Extract `100`, `4` to named constants | Presentation |
| `ui/dashboard/DashboardViewModel.kt:311,313,315` | MagicNumber ×3 | Extract `3`, `4`, `5` to named constants (sparkline window sizes) | Presentation |
| `data/supabase/SyncSessionUseCase.kt:24` | ReturnCount | Evaluate guard-clause restructure vs. justified `@Suppress` | Data |
| `test/.../ReportCsvBuilderTest.kt:14` | LongMethod (94/80) | Split into arrange/act/assert helpers, or justified `@Suppress` (data-table-heavy CSV test) | Test |
| `data/local/dao/SampleDao.kt:15` (interface) | TooManyFunctions (20/11) | Decision needed — see §Decisions 1 | Data |
| `data/local/dao/SessionDao.kt:16` (interface) | TooManyFunctions (18/11) | Decision needed — see §Decisions 1 | Data |
| `ui/dashboard/DashboardScreen.kt:1` (file) | TooManyFunctions (13/11) | Split into `DashboardScreen.kt` + `DashboardCards.kt`, mirroring the Settings 3-file split precedent | Presentation |
| `ui/records/SessionDetailScreen.kt:3` (file) | TooManyFunctions (20/11) | Split into `SessionDetailScreen.kt` + `SessionDetailCards.kt` | Presentation |
| `domain/usecase/records/ReportCsvBuilder.kt:14,52` | LongParameterList ×2 (10/6, 8/6) | Bundle into a `ReportMetadata` domain-model data class; call sites updated (`GenerateSessionReportUseCase`, test) | Domain |
| `domain/usecase/records/GenerateSessionReportUseCase.kt:22` | LongParameterList (8/7) | `@Suppress` — composition root, 8 distinct DI dependencies, matches `DashboardViewModel`/`SettingsViewModel` precedent | Domain |
| `domain/usecase/sync/SyncPendingDataUseCase.kt:40` | LongParameterList (8/7) | `@Suppress` — same precedent | Domain |
| `data/repository/FlaggedFrameStore.kt:38` | LongParameterList (7/7) | `@Suppress` — same precedent (`@Singleton` composition root) | Data |
| `data/local/dao/SampleDao.kt:98` | LongParameterList (9/6, `updateSampleOnVerify`) | Decision needed — see §Decisions 2 | Data |
| `ui/sessions/SessionsScreen.kt:246,397,685` | LongParameterList ×3 (`SessionCard` 10/6, `KebabMenu` 6/6, `SheetInput` 7/6) | Bundle into state/action data classes (mirrors `SyncCardState`/`SettingsActions` precedent) | Presentation |
| `ui/records/SessionDetailScreen.kt:572,629` | LongParameterList ×2 (`SessionDetailPopulated` 7/6, `SessionDetailEmpty` 6/6) | Bundle into a shared state data class | Presentation |
| `ui/records/RecordsScreen.kt:262` | LongParameterList (`StatTile` 6/6) | Bundle color params into a `StatTileColors` data class, or `@Suppress` if call sites make bundling awkward (small leaf component, mostly default params) | Presentation |
| `ui/capture/CaptureScreen.kt:151` | LongParameterList (`CaptureScreen` 8/6) | Evaluate bundling vs. `@Suppress` — top-level screen composable, several params already have defaults | Presentation |
| `ui/components/AgarthaButton.kt:44` | LongParameterList (`AgarthaButton` 6/6) | Evaluate — shared design-system primitive used everywhere; bundling risks call-site churn across the whole app for marginal benefit | Presentation |
| `ui/dashboard/DashboardScreen.kt:382` | LongParameterList (`KpiTile` 9/6) | Bundle color params into a `KpiTileColors`/state data class | Presentation |
| `ui/login/LoginScreen.kt:347` | LongParameterList (`LoginInputGroup` 9/6) | Bundle keyboard/validation params into an `InputFieldConfig` data class | Presentation |
| `ui/verify/ModalSheetComponents.kt:83` | LongParameterList (`SheetActionRow` 6/6) | Bundle into a shared sheet-action state data class (reusable across `VerificationSheet`/`ManualCaptureSheet`) | Presentation |
| `test/.../ReportCsvBuilderTest.kt:113` | LongParameterList (`sample()` helper, 12/6) | Bundle into a test-fixture builder/data class with named defaults | Test |

## Schema / migration

None — pure refactor + lint-debt cleanup. No entity, DAO query semantics, Room schema
version, or Supabase migration changes anywhere in this chore.

## Design tokens (UI tasks only)

None new. Where Composable signatures are bundled into state/action data classes, no visual
output changes — this is a parameter-list simplification, not a redesign. No new colors,
radii, or spacing tokens.

## Decisions for the review gate

1. **DAO `TooManyFunctions` (`SampleDao` 20/11, `SessionDao` 18/11) — RECOMMENDED: audit for
   dead/redundant methods first, then justified `@Suppress` on the remainder.** Splitting a
   Room `@Dao` interface into multiple interfaces per entity is unusual and doesn't map
   cleanly onto Room's binding model (one DAO per entity is the idiomatic pattern this
   codebase already follows). Before suppressing, stage 02 should grep for any one-shot
   getters made redundant by the Settings PR's new `Flow<Int>` count queries (e.g. check
   whether `getSessionsPendingSync`-style one-shot getters are now superseded and dead) —
   if any are found, delete them first, which shrinks the count for free. Whatever remains
   gets a single `@Suppress("TooManyFunctions")` on the interface with a comment citing the
   Room one-DAO-per-entity convention.
2. **`SampleDao.updateSampleOnVerify` (9/6 params) — RECOMMENDED: justified `@Suppress`, not
   a Room-update restructure.** Converting this to a partial-entity `@Update` would change
   the actual persistence mechanism (from a targeted column `@Query` to a full/partial
   entity write), which is a real behavior-risk change for a lint-only chore. Suppress with
   a comment noting each param binds a distinct `SET` column in a single verify-commit
   `UPDATE`.
3. **Composable parameter bundling — RECOMMENDED: bundle only where a real state/action
   grouping already exists conceptually; `@Suppress` small leaf/shared primitives.** Applying
   the `SyncCardState`/`SettingsActions` precedent from the Settings PR indiscriminately to
   all 11 flagged composables risks exactly what that PR's own notes warned against — "a
   real simplification, not just a lint dodge." Two are recommended for `@Suppress` instead
   of bundling: `AgarthaButton` (shared design-system primitive, used at ~20+ call sites —
   bundling would churn the whole app for a component whose 6 params are each independently
   meaningful) and `StatTile`/`CaptureScreen` only if stage 02 finds the bundle would be a
   single-use wrapper with no reuse value. Default to bundling everywhere else listed above
   (`SessionCard`, `KebabMenu`, `SheetInput`, `SessionDetailPopulated`, `SessionDetailEmpty`,
   `KpiTile`, `LoginInputGroup`, `SheetActionRow`) since each groups params that already
   travel together conceptually (a card's data+callbacks, a field's config, a sheet's
   action pair).
4. **`ReturnCount` (`SyncSessionUseCase.invoke`) and `LongMethod` (`ReportCsvBuilderTest`) —
   RECOMMENDED: inspect in stage 02, prefer fixing over suppressing since both are
   plausible test/guard-clause code smells, not architectural constraints.** No
   pre-judgment here — stage 02 reads both and decides fix-vs-suppress per actual content.
5. **Suggested PR split (mirrors the Settings feature's proven PR1→PR2 workflow — implement,
   verify, human review/commit, repeat):**
   - **PR1 — Mechanical, zero-risk (18 findings):** all `MaxLineLength` (10), `MagicNumber`
     (5), `UseCheckOrError` (1), plus a decision + fix/suppress each for `ReturnCount` (1)
     and `LongMethod` (1). No signature changes, no call-site updates.
   - **PR2 — DI/DAO composition-root suppressions + `ReportCsvBuilder` bundling (5 findings
     across 4 constructs, resolving 6 raw findings incl. the 2 `ReportCsvBuilder` sites):**
     `GenerateSessionReportUseCase`, `SyncPendingDataUseCase`, `FlaggedFrameStore`
     constructors get justified `@Suppress`; `ReportCsvBuilder` gets bundled into a
     `ReportMetadata` data class (real fix, touches `GenerateSessionReportUseCase` call site
     + `ReportCsvBuilderTest`); `SampleDao.updateSampleOnVerify` gets justified `@Suppress`.
   - **PR3 — TooManyFunctions file/interface splits (4 findings):** `DashboardScreen.kt` and
     `SessionDetailScreen.kt` split into screen+cards files (Settings precedent);
     `SampleDao`/`SessionDao` audited for dead methods then `@Suppress`d.
   - **PR4 — Composable parameter bundling (11 findings across 11 composables + 1 test
     helper):** the largest and highest-touch PR — bundles `SessionCard`, `KebabMenu`,
     `SheetInput`, `SessionDetailPopulated`, `SessionDetailEmpty`, `KpiTile`,
     `LoginInputGroup`, `SheetActionRow`, `ReportCsvBuilderTest.sample()`; `@Suppress`es
     `AgarthaButton` and (pending stage-02 judgment) `StatTile`/`CaptureScreen`. Highest risk
     of the four PRs — touches the most call sites; recommend running the affected screens
     manually (Dashboard, Sessions, Session Detail, Records, Login, Verify sheets, Capture)
     after this PR, not just unit tests, since Compose preview functions don't catch every
     visual regression.

## Tests to add / update

No new test *cases* — this chore is refactor-only, so correctness is proven by "tests still
pass, behavior unchanged," not by new coverage. Updates required:
- `ReportCsvBuilderTest` — call sites updated for the new `ReportMetadata` bundling (PR2).
- Any test that directly calls a bundled Composable in a `@Preview` or via Compose testing
  APIs — search for direct calls to the 8 bundled composables before each PR in that group;
  none are currently under Compose UI test (`androidx.compose.ui.test`) per the existing
  `app/src/test/` layout (JVM unit tests only), so this is expected to be a compile-time
  check, not a test-suite change.
- Full `bun run test` + `bun run lint` (ktlint + detekt) re-run after each PR — detekt count
  must strictly decrease each time, reaching **0** after PR4.

## Architecture checks

- [x] No ViewModel imports Room / Retrofit / Supabase — unaffected; no ViewModel signatures
  change layer boundaries in this chore
- [x] Use Cases are single-responsibility, return `Result<T>` — unaffected; `ReportCsvBuilder`
  bundling only changes its parameter shape, not its responsibility or return type
- [x] Repositories are the single source of truth — unaffected
- [x] `domain/` has zero Android imports — the new `ReportMetadata` data class (domain
  model) must stay pure Kotlin, same as every other `domain/model/*`
- [x] Entities (Room) in `data/`, domain models in `domain/` — `ReportMetadata` goes in
  `domain/model/`, not `data/`
- [x] Interfaces in `domain/`, implementations in `data/` — unaffected
- [x] `@Singleton` scoping unaffected — `FlaggedFrameStore` stays `@Singleton`; its
  `@Suppress` doesn't touch scoping, only the constructor's lint annotation

No hard-rule violations anywhere in this chore; it is a pure internal-quality pass.

## Deferred

- Any detekt findings introduced *after* this chore (future PRs) are the responsibility of
  whichever PR introduces them — this chore's job is only to clear the pre-existing 40.
- No detekt rule threshold or suppression baseline (`detekt.yml` / `baseline.xml`) changes
  are in scope — every fix is a source-code or targeted `@Suppress` change, not a
  config-level relaxation. If the developer would rather adopt a `baseline.xml` for the
  suppressed findings instead of inline `@Suppress` comments, flag that as an alternative
  approach to confirm before PR2/PR3 start (inline is recommended for consistency with the
  existing `DashboardViewModel`/`SettingsViewModel` `@Suppress` precedent already in the
  codebase).
- `TODO.md`'s stage-03 "nominal zero-violation bar" language gets updated to drop the
  waiver note once this chore's PR4 lands and a clean `bun run lint` is confirmed — that
  doc update happens in PR4, not as a separate task.
