# QA Report: Offline Access — direct entry, deferred login, local-first sessions (ADR-007)

> Confirmed as finished at the developer's direction on 2026-07-09. Lint, tests, and build
> were executed and fixed in-session immediately before this report; the developer waived a
> from-scratch re-run ("we already did the checks for this session") and committed the
> feature themselves as `59e4f24`.

## Build

`bun run build` (assembleDebug) result: **PASS** — BUILD SUCCESSFUL after fixing the
`FlaggedFrameStore` identity read (`observeLocalIdentity().map { it?.userId }` replacing the
removed `userIdFlow`) and regenerating the Room v8 schema export.

## Lint

`bun run lint` result: **ktlint PASS · detekt WAIVED**

- `ktlintCheck`: 0 violations.
- `detekt`: 40 findings remain. The one finding introduced during this QA pass
  (`TooGenericExceptionThrown` from the `SessionManagerTest` stub fix) was resolved with a
  justified `@Suppress` (verified 41 → 40). The remaining 40 were not re-audited per finding;
  the count is up from the 31-finding baseline recorded in the previous task's QA report.
  **Developer decision:** accepted as tracked debt (consistent with the prior 31-finding
  waiver); a dedicated repo-wide detekt cleanup chore is now recorded in `TODO.md` Known
  Issues. Note the stage-03 contract nominally requires zero violations — this is an explicit
  override, not an oversight.

## Tests

`bun run test` (testDebugUnitTest) result: **PASS** — 99/99.

Notable work during this pass:
- 8 test files updated to the ADR-007 interfaces (`AuthRepository`, `SessionRepository`,
  `ReportDao.claimReportsForSessions`, new ViewModel constructors): `SyncReportUseCaseTest`,
  `GenerateSessionReportUseCaseTest`, `GetRecordsUseCaseTest`, `GetSampleDetailUseCaseTest`,
  `SubmitManualCaptureUseCaseTest`, `DashboardViewModelTest`, `SessionPickerViewModelTest`.
- `LoginViewModelTest` rewritten for the new no-auto-forward flow, including a new assertion
  that successful login invokes `ClaimLocalDataUseCase` then `SyncPendingDataUseCase`.
- Fixed a genuine latent defect in `SessionManagerTest`: the remote-close-failure stub used
  `any()` for a nullable `notes` param, which mockito-kotlin never matches against `null`,
  so the failure path was never actually exercised. Fixed with `anyOrNull()` +
  `thenAnswer { throw ... }`.

## Architecture compliance

The full `qa-checklist.md` walkthrough was **not re-executed** — waived by the developer
alongside the re-run. Items verified incidentally during the fix work:

- [x] No ViewModel imports Room / Retrofit / Supabase — the pre-existing
  `SessionsViewModel → SessionRemoteDataSource` violation is fixed (VM now takes
  `ObserveLocalIdentityUseCase` / `SetSessionClaimExemptUseCase` / `ClaimLocalDataUseCase`)
- [x] No Android import in `domain/` — `LocalIdentity`, `SessionSyncStatus`, new use cases
  are pure Kotlin
- [x] Use Cases return `Result<T>` — `SyncPendingDataUseCase → Result<SyncSummary>`,
  `ClaimLocalDataUseCase → Result<Int>`
- [x] Room v8 schema export `app/schemas/.../8.json` generated and committed

## Documentation sync

- [x] `CONTEXT.md` updated — ADR-007 recorded in §8; §3 navigation flow reflects
  Dashboard-first entry
- [x] `schema.ts` updated per the stage-01 scope (Room-only columns + nullable `user_id`)
- [x] `TODO.md` updated — offline-access row now records the passed verification with only
  the device E2E outstanding; detekt debt added to Known Issues (done alongside this report)
- [ ] `AGENTS.md` — no new non-negotiable introduced; not applicable

## PR draft

**Title:** feat(core): offline access — direct entry, deferred login, local-first sessions

**Summary:**
- App opens straight to the Dashboard; Login is an explicit destination used only to enable
  cloud upload (ADR-007)
- Sessions, manual captures, and verification work fully offline; local rows are attributed
  to the cached `LocalIdentity` or stored unowned (`user_id = NULL`)
- Successful login silently claims eligible unowned sessions (cascading to samples/reports)
  and triggers an FK-ordered pending-sync pass (sessions → samples → reports)
- Per-session "Link to account" opt-out toggle (`claim_exempt`) with a neutral "Not linked"
  badge; unlink allowed only while sync-PENDING
- Room v7 → v8 (`sessions.supabase_status`, `sessions.claim_exempt`, nullable
  `samples.user_id`); **no Supabase schema change** (claim-before-sync keeps every remote
  row owned)

**Test plan:**
- [x] `bun run build` — BUILD SUCCESSFUL
- [x] `bun run test` — 99/99 pass
- [x] `ktlintCheck` — 0 violations
- [ ] `detekt` — 40 findings waived as tracked debt (see Lint section)
- [ ] Physical-device E2E pass (Sprint 3 backlog #1) — offline start → manual capture →
  verify → login → silent claim → sync; "Link to account" toggle; sync-now button

**Branch:** `feat/core-offline-access` (convention-compliant)
**Target:** `main` (per this repo's confirmed actual workflow; no `develop` branch)
**Commit:** `59e4f24 feat(core): offline access` — committed directly by the developer.

---

## Developer decisions (resolved)

1. **Re-run of lint/test/build for QA**: waived — results from this session's verified run
   stand. ✅ Confirmed.
2. **Detekt zero-violation gate**: 40 findings accepted as tracked debt; cleanup chore
   recorded in `TODO.md`. ✅ Confirmed.
3. **Commit**: made directly by the developer as a single commit rather than the suggested
   3-PR split. ✅ Confirmed.
4. **Device E2E**: remains outstanding; tracked as Sprint 3 backlog #1, not a blocker for
   closing this stage. ✅ Confirmed.
