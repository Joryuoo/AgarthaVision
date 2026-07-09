# QA Report: Settings Screen — Account & App Management (ADR-008)

> Confirmed as finished at the developer's direction on 2026-07-09. Build, test, and lint
> were executed and verified green in-session for both PRs immediately before each commit;
> the developer committed both PRs themselves (`47fc04b`, `eeebcc7`) following the two-PR
> split proposed in `stages/01_scope/output/scope.md`.

## Build

`bun run build` (assembleDebug) result: **PASS** — BUILD SUCCESSFUL for both PR1 (sign-out
core) and PR2 (Settings screen UI/ViewModel). No Room migration involved (v8 unchanged; new
DAO methods are query-only `Flow<Int>` COUNT reads over existing columns).

## Lint

`bun run lint` result: **ktlint PASS · detekt PASS at baseline**

- `ktlintCheck`: 0 violations across both PRs.
- `detekt`: 40 findings — unchanged from the pre-existing baseline (established after the
  offline-access QA pass, `59e4f24`). Zero findings attributable to any Settings file,
  confirmed by grepping the full detekt output for `settings`/new filenames (no matches).
  Three detekt findings were introduced and fixed during PR2 development, not waived:
  - `LongParameterList` on `SettingsViewModel`'s constructor (7 DI params) — resolved with
    `@Suppress("LongParameterList")`, matching the existing justified precedent on
    `DashboardViewModel` (same shape: composition root, all distinct dependencies).
  - `LongParameterList` on `SettingsContent`/`SyncCard` — resolved by introducing
    `SettingsActions` and `SyncCardState` bundling data classes (a genuine simplification,
    not just a lint dodge — fewer positional params to keep aligned at call sites).
  - `TooManyFunctions` (11-function threshold) on the original single-file
    `SettingsScreen.kt` — resolved by splitting into `SettingsScreen.kt` / `SettingsCards.kt`
    / `SettingsAppearanceAboutCards.kt`, each under the threshold.

## Tests

`bun run test` (testDebugUnitTest) result: **PASS** — full suite green after both PRs.

- PR1: `SignOutUseCaseTest` (new, 1 test) — guards on `SessionManager.state is Active`;
  3 existing test files patched for the new `AuthRepository.signOut()` interface method.
- PR2: `SettingsViewModelTest` (new, 7 tests) — initial state signed-in / never-signed-in,
  theme toggle persistence, `onSyncNow` gating (signed-in+online vs. signed-out), both
  sign-out event outcomes (`SignedOut` / `SignOutBlocked`). 2 existing `ReportDao` test
  fakes (`SyncReportUseCaseTest`, `GenerateSessionReportUseCaseTest`) patched with
  `observePendingCount`/`observeFailedCount` stubs for interface compliance.

## Architecture compliance

Verified against `qa-checklist.md` conventions incidentally during implementation and this
review pass:

- [x] No ViewModel imports Room / Retrofit / Supabase directly — `SettingsViewModel` takes
  use cases and `ConnectivityObserver` only
- [x] No Android import in `domain/` — `PendingSyncCounts` is pure Kotlin;
  `ObservePendingSyncCountsUseCase` combines DAO flows but stays in `domain/usecase`
  consistent with the existing verify-use-case DAO-import precedent (flagged as a known
  consolidation item in `TODO.md`, not a new violation)
- [x] `SignOutUseCase` returns `Result<Unit>`, guards on session state before delegating to
  `AuthRepository.signOut()`
- [x] No new drawable/icon assets — design system rule ("no external icon library /
  hand-crafted inline SVG only") honored by using text + semantic color exclusively,
  matching `DashboardScreen`'s existing account/sync banner
- [x] No schema change — Room v8 untouched, no Supabase migration (sign-out is token +
  DataStore only; RLS untouched)
- [x] `commitlint.config.js` carries the new `settings` scope used by both commits

## Documentation sync

- [x] `CONTEXT.md` — ADR-008 recorded in §8 (sign-out semantics: full local sign-out,
  warn-don't-block, Active-session guard)
- [x] `TODO.md` — Settings screen row marked ✅ with implementation detail; Sprint 3 backlog
  #3 (manual retry, partial), #4 (Settings screen, done), #10 (theme mirror, done) marked
  resolved; ADR-007 follow-ups (a) sign-out and (c) Settings account mirror marked resolved;
  new backlog item #13 added for future dedicated iconography (deferred, not required)
- [x] `stages/02_implement/output/implementation_notes.md` — present for both PRs (PR1
  content superseded/consolidated, PR2 notes current with changed-files table, decisions,
  deferred items, and verification summary)
- [ ] `AGENTS.md` — no new non-negotiable introduced; not applicable

## Scope closure

Cross-checked against `stages/01_scope/output/scope.md`'s "Affected files" table — every
planned file was touched exactly as scoped, with no unplanned deviations:

- `AuthRepository.signOut()` + `SupabaseAuthRepository` impl — done (PR1)
- `SignOutUseCase` — done (PR1); `HasActiveSessionUseCase` confirmed dead and deleted (PR1)
- `PendingSyncCounts` + `ObservePendingSyncCountsUseCase` — done (PR2)
- DAO `Flow<Int>` count queries (Session/Sample/Report) — done (PR2)
- `SettingsViewModel` + `SettingsScreen`/`SettingsCards`/`SettingsAppearanceAboutCards` —
  done (PR2); placeholder deleted, nav graph wired
- `strings.xml` Settings block, `commitlint.config.js` scope — done (split across both PRs)
- `CONTEXT.md` ADR-008 — done (PR1)

No PR3 was planned or required — PR1 + PR2 together implement the full scope. Nothing was
deferred out of this feature's boundary except the explicitly-scoped-out iconography
follow-up (Sprint 3 #13), which was already flagged as optional in the original scope doc.

## PR draft (for reference — already committed as two PRs, not raised as GitHub PRs)

**PR1 — `47fc04b feat(core): sign-out + ADR-008`**
- `AuthRepository.signOut()`: revokes the Supabase session, then unconditionally clears the
  three cached `LocalIdentity` DataStore keys (local sign-out always succeeds even offline)
- `SignOutUseCase`: blocks while a capture session is Active; clears `FlaggedFrameStore`
- ADR-008 recorded; dead `HasActiveSessionUseCase` removed

**PR2 — `eeebcc7 feat(core): settings for account and app management`**
- Production Settings screen: Account (identity/sign-in/sign-out with pending-count-aware
  confirm dialog and Active-session blocked dialog), Data & Sync (live pending/failed counts,
  manual "Sync now"), Appearance (theme toggle mirror), About (version/environment)
- `SettingsViewModel` combining identity, connectivity, theme, and pending-sync state
- Nav graph wiring in place of the placeholder

**Test plan:**
- [x] `bun run build` — BUILD SUCCESSFUL (both PRs)
- [x] `bun run test` — all pass (both PRs)
- [x] `ktlintCheck` — 0 violations (both PRs)
- [x] `detekt` — 40 findings, identical to baseline, zero from Settings files
- [ ] Physical-device E2E pass (Sprint 3 backlog #1, pre-existing outstanding item, not
  specific to this feature) — sign-out from Dashboard/Settings, sync-now with pending data,
  theme toggle mirror consistency, Active-session sign-out block

**Branch:** committed directly to the working branch (no `develop`; matches this repo's
confirmed actual workflow — see `feat/core-offline-access` precedent in the prior QA report)
**Target:** `main`
**Commits:** `47fc04b`, `eeebcc7` — both committed directly by the developer.

---

## Developer decisions (resolved)

1. **PR split**: two PRs (sign-out core, then Settings UI), each verified green and manually
   committed by the developer before the next began, per this session's explicit workflow
   request. ✅ Confirmed.
2. **Detekt zero-violation gate**: findings introduced during PR2 development were fixed,
   not waived — final count matches the pre-existing 40-finding baseline exactly, with zero
   new debt added by this feature. ✅ Confirmed (stronger outcome than the prior offline-access
   QA pass, which added to the baseline).
3. **No new iconography**: text/color-only communicated status, consistent with the existing
   Dashboard banner pattern and the "no icon library" design rule; deferred as optional
   Sprint 3 backlog #13 if a design review later asks for it. ✅ Confirmed.
4. **Device E2E**: remains outstanding as a pre-existing, feature-agnostic backlog item
   (Sprint 3 #1); not a blocker for closing this stage. ✅ Confirmed.
