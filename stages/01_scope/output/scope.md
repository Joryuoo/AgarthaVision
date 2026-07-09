# Scope: Offline Access — Direct Entry, Deferred Login, Local-First Sessions

## Summary

Remove the login wall: the app opens straight into the Dashboard, sessions can be started
and verified fully offline against Room, and Supabase login becomes an explicit action —
required only to upload. Identity is handled with a **cached last-known identity + deferred
ownership claim** model: offline work is attributed to the last signed-in medtech when one
exists, otherwise stored unowned and claimed at the next login, after which a pending-sync
pass pushes sessions → samples → reports to Supabase.

## How this is usually done (context for review)

The standard offline-first pattern for single-operator field apps has three parts, and this
plan follows it:

1. **Local-first writes.** Every row is written to Room first with a local sync status;
   the cloud is a mirror, never a gate. (Samples and reports already work this way —
   sessions are the exception this task fixes.)
2. **Cached identity, deferred auth.** The device remembers *who* the last authenticated
   user was (a DataStore snapshot of user id + email) separately from *whether* Supabase
   currently holds a valid token. New offline rows are attributed to the cached identity;
   a fresh install that has never logged in stores rows with `user_id = NULL` and they are
   claimed at first login. Login itself always requires connectivity.
3. **Trigger-based sync.** Pending rows are pushed when (a) login succeeds, (b) the app
   starts while authenticated + online, or (c) connectivity returns. A durable
   WorkManager-backed queue with backoff stays Phase 2, as already planned.

## Current-state findings (verified in code)

- `AgarthaNavGraph.kt` starts at `Screen.Login`; `LoginViewModel.checkPersistedSession()`
  auto-forwards when a persisted Supabase session exists. There is no sign-out anywhere.
- `SessionManager.startSession()` **cannot run offline**: it throws without an
  authenticated user, and if the remote insert fails it marks the local session ended and
  rethrows. `stopSession()`'s remote close failure is also unrecoverable (no retry state).
- `SessionEntity.userId` is *already nullable* — schema.ts documents this as intentional
  for pre-auth/offline sessions. Nothing uses that affordance yet, and no column records
  whether a session row exists remotely.
- `SampleEntity.userId` is **non-null**, set at flag-persist time
  (`PersistFlaggedFrameUseCase` → `SampleMapper`). `SubmitVerificationUseCase` and
  `SubmitManualCaptureUseCase` both hard-fail without `getCurrentUserId()`.
- Sync primitives exist and are local-status driven (`SyncSampleUseCase`,
  `SyncReportUseCase` in `data/supabase/`; `getSamplesPendingSync` / `getReportsPendingSync`
  DAOs) — but no orchestrated retry entry point was found in code (docs claim "retried on
  next session start"; stage 02 must confirm and, if absent, this task's sync trigger
  replaces it).
- `NetworkMonitor` only probes the **inference container** during an active session. There
  is no device-level connectivity observer (needed for login gating and sync triggers).
- `SessionsViewModel` imports `data.supabase.SessionRemoteDataSource` directly — an
  existing hard-rule-1 violation this task fixes while replacing that identity read.
- `DashboardViewModel` gates every flow on `userIdFlow.filterNotNull()` — a never-logged-in
  or offline cold start would show a permanently empty dashboard.
- **Honest limitation:** inference is cloud-hosted, so offline sessions cannot produce AI
  detections (NetworkMonitor already stops recording on inference loss — unchanged).
  Offline capture value = **manual captures** + verifying already-flagged frames + local
  records/EPG/reports. The UI must not pretend otherwise.

## Decisions from the review gate

1. **Attribution policy — DECIDED: hybrid.** Cached identity when present, else NULL +
   claim at login. Consequence to be aware of alongside decision 2: under hybrid, rows
   attributed to the cached last user are assigned **silently**; the claim flow and the
   "Link to account" toggle only ever cover NULL-owned rows (fresh installs /
   never-logged-in devices). Assumes one-medtech-per-device as the operational norm
   (recorded in ADR-007).
2. **Claim UX — DECIDED (v3): silent claim-all + per-session opt-out toggle. No login
   dialog.** Login never interrupts: on success, all *eligible* unowned sessions are
   claimed silently by the logging-in account and queued for sync (one line of disclosure
   copy on the Login screen). The "choose" control moves out of the login flow into the
   session UI: every unowned session carries a **"Link to account" toggle (default ON)**
   in its card overflow / Session Detail actions. Toggling OFF marks the session
   claim-exempt — the silent login claim skips it, it stays local-only and unsynced, and
   its card shows a neutral "Not linked" badge. A signed-in user can flip it back ON
   later, which claims + syncs that one session (cascade to its samples/reports).
   Reverting a claim ("unlink") is allowed only while the session is still sync-PENDING;
   once synced, ownership is permanent (the cloud row already exists under that user).
   Accepted trade-off vs. the dialog: zero friction for the normal case, but on a shared
   device the borrower must remember to toggle their session OFF before the owner's next
   login — recorded as an ADR-007 consequence.
3. **Manual "Sync now" on Dashboard — DECIDED: include.** Ships in this task, wired to
   `SyncPendingDataUseCase`, disabled while offline or signed out.

## Offline analytics (clarification for review)

Yes — analytics over unsynced, locally verified samples work offline **by construction**,
because every read path is Room-first and ignores sync status: EPG
(`SessionEggCountUseCase` + `EpgCalculator`) counts CONFIRMED detections from Room;
Records/Session Detail/Sample Detail read Room (sample images prefer the local file —
the Supabase signed-URL path is only a fallback); CSV report generation
(`GenerateSessionReportUseCase`) is fully local (only the report *row* syncs); Dashboard
KPIs/sparkline/species aggregate Room via repositories. `VERIFIED` / `SYNC_FAILED`
samples count identically to `SYNCED` ones. The only thing blocking offline analytics
today is **identity gating** — `DashboardViewModel.filterNotNull()` and owner-filtered
queries render nothing for a signed-out user — which is exactly what this task's
owner-or-unowned query changes fix. No separate analytics work is needed.

## Affected files

| File | Change description | Layer |
|---|---|---|
| `ui/navigation/AgarthaNavGraph.kt` | `startDestination` → Dashboard; Login stays a route, entered explicitly from Dashboard, gains back navigation; successful login pops back instead of resetting the stack | Presentation |
| `ui/login/LoginViewModel.kt` | Delete `checkPersistedSession()` auto-forward; gate submit on connectivity (offline → inline message, disabled CTA); on success: silent `ClaimLocalDataUseCase` (all non-exempt unowned sessions) → trigger pending sync → emit pop-back event | Presentation |
| `ui/login/LoginScreen.kt` | Back affordance; offline state copy; one-line claim disclosure ("Unlinked local sessions on this device will be added to this account"); `strings.xml` additions | Presentation |
| `ui/dashboard/DashboardViewModel.kt` | Identity via `ObserveLocalIdentityUseCase` (nullable-tolerant — remove `filterNotNull` gate); expose signed-in/offline/pending-upload state incl. pending session count; `onSyncNow` intent → `SyncPendingDataUseCase` | Presentation |
| `ui/dashboard/DashboardScreen.kt` | Signed-out / offline / "N items pending upload" banner with **Sign in** CTA and **Sync now** button (disabled offline/signed-out, progress state while syncing); replace mocked `userName` fallback for anonymous state | Presentation |
| `ui/sessions/SessionsViewModel.kt` | Replace direct `SessionRemoteDataSource` read (hard-rule fix) with `ObserveLocalIdentityUseCase`; owner-or-unowned session query so offline/null identity still lists local work; derive per-session link state (unowned / not-linked / pending / synced); `onToggleAccountLink` intent → `SetSessionClaimExemptUseCase` or `ClaimLocalDataUseCase(ids)` per state | Presentation |
| `ui/sessions/SessionsScreen.kt` | Neutral "Not linked" badge on unowned/exempt session cards; overflow action "Link to account / Don't link to account" (label follows link state; hidden once synced) | Presentation |
| `domain/model/LocalIdentity.kt` (new) | `data class LocalIdentity(userId, email, displayName?)` — pure Kotlin | Domain |
| `domain/model/SessionSyncStatus.kt` (new) | `PENDING / SYNCED / SYNC_FAILED` mirroring `ReportSyncStatus` (enum consolidation stays a tracked known issue) | Domain |
| `domain/repository/AuthRepository.kt` | Add `observeLocalIdentity(): Flow<LocalIdentity?>`, `suspend fun isAuthenticated(): Boolean`; fix `userIdFlow` semantics (currently a one-shot flow) | Domain |
| `domain/usecase/auth/ObserveLocalIdentityUseCase.kt` (new) | Flow-returning observe use case (existing `Observe*` precedent) | Domain |
| `domain/usecase/auth/ClaimLocalDataUseCase.kt` (new) | `invoke(userId, sessionIds = null)`: `UPDATE user_id` on unowned **non-exempt** sessions (`null` ids = all eligible, the login path; explicit ids = the manual "Link to account" action) **cascading to their samples and reports** → `Result<Int>` (rows claimed); idempotent; only touches `user_id IS NULL` rows | Domain |
| `domain/usecase/sessions/SetSessionClaimExemptUseCase.kt` (new, new subpackage) | Toggle a session's claim exemption → `Result<Unit>`. Exempt ON allowed on unowned sessions; also unlinks a claimed session back to unowned+exempt **only while `supabase_status = PENDING`** (guard error once synced) | Domain |
| `domain/usecase/sync/SyncPendingDataUseCase.kt` (new) | Orchestrates FK-ordered push: pending sessions → samples → reports; no-op with `Result.success(SKIPPED)` when unauthenticated/offline | Domain |
| `domain/usecase/verify/SubmitVerificationUseCase.kt` | Drop hard auth requirement; owner from session/cached identity (nullable); sync attempt stays opportunistic (offline → `SYNC_FAILED`, picked up later) | Domain |
| `domain/usecase/verify/SubmitManualCaptureUseCase.kt` | Same auth relaxation as above | Domain |
| `domain/usecase/capture/PersistFlaggedFrameUseCase.kt` | Attribute sample owner from active session / cached identity instead of requiring auth | Domain |
| `data/repository/SupabaseAuthRepository.kt` | Persist `LocalIdentity` to DataStore on successful sign-in; implement `observeLocalIdentity` / `isAuthenticated`; keep Supabase session restore behavior | Data |
| `data/supabase/SessionRemoteDataSource.kt` | `insert` → `upsert` (idempotent re-push, also carries `ended_at`/`notes` for sessions closed offline) | Data |
| `data/supabase/SyncSessionUseCase.kt` (new) | Push one pending session row; set `SessionSyncStatus` accordingly (mirrors `SyncReportUseCase` shape) | Data |
| `data/local/entity/SessionEntity.kt` | Add Room-only `supabase_status` (default `synced` for existing rows) and `claim_exempt` (default `false`) columns | Data |
| `data/local/entity/SampleEntity.kt` | `userId: String` → `String?` (Room-only relaxation; remote stays NOT NULL — enforced by claim-before-sync) | Data |
| `data/local/dao/SessionDao.kt` | Owner-or-unowned observe queries; `getSessionsPendingSync`; claim `UPDATE` scoped to non-exempt / explicit ids; `claim_exempt` toggle + PENDING-guarded unlink `UPDATE` | Data |
| `data/local/dao/SampleDao.kt` | Nullable-owner handling in existing queries; claim `UPDATE`; keep `getSamplesPendingSync` (claimed rows only) | Data |
| `data/local/dao/ReportDao.kt` | Claim `UPDATE` for unowned reports | Data |
| `data/local/mapper/SampleMapper.kt` | Nullable owner mapping | Data |
| `core/database/AgarthaDatabase.kt` + `app/schemas/.../8.json` | Room v7→v8 migration (see below) | Core |
| `core/session/SessionManager.kt` | `startSession`: local insert always (owner = cached identity or null, status `PENDING`), remote push best-effort, **no rollback/throw on remote failure**; `stopSession`: local end always, remote close best-effort → status stays `PENDING` on failure | Core |
| `core/connectivity/ConnectivityObserver.kt` (new) | `@Singleton` ConnectivityManager network-callback → `StateFlow<Boolean>`; constructor-injected `@ApplicationContext` (no new DI module); distinct from inference-only `NetworkMonitor` | Core |
| `CONTEXT.md` | §3 navigation flow, §5 Auth/Sync sections, new **ADR-007** (offline-first entry, cached identity, deferred claim, trigger-based sync) | Docs |
| `schema.ts` | Note `SampleEntity.user_id` Room nullability + `sessions.supabase_status` Room-only column | Docs |
| `TODO.md` | Record feature status; add follow-ups (sign-out, Settings account section, enum consolidation) | Docs |

## Schema / migration

**Room v8** (Supabase: **none** — remote schema, RLS, and migrations `0001–0008` unchanged):

- `sessions`: add `supabase_status TEXT NOT NULL DEFAULT 'synced'` and
  `claim_exempt INTEGER NOT NULL DEFAULT 0`. Default `synced` is correct for pre-existing
  rows (the old code only kept sessions whose remote insert succeeded); the rare
  pre-feature row whose remote insert failed was already marked ended and stays local-only
  — accepted, noted in the migration KDoc.
- `samples`: relax `user_id` to nullable (SQLite requires table-rebuild migration:
  create/copy/drop/rename).
- Export `app/schemas/.../8.json`; add a Room `MigrationTest` if instrumented tests exist
  for prior versions (stage 02 verify — otherwise document manual check).

**RLS compatibility:** unchanged policies hold because sync only ever runs authenticated
and post-claim, so `user_id = auth.uid()` on every insert/upsert. Stage 02 must verify the
`sessions` UPDATE policy permits the new upsert path (`closeSession` UPDATE works today, so
owner-scoped UPDATE exists; confirm against `supabase/migrations/0001`).

## Design tokens (UI tasks only)

Banner/state/dialog UI only — existing tokens, no new colors or radii:
`AgarthaTheme.colors.accent` (Sign in CTA, Sync now button), `warning`/`warningTint`/
`warningText` (offline + pending-upload states, amber = sync warning per design system),
`success`/`successTint` (all-synced), Caption/Label typography with `tnum` for pending
counts, `pill` radius for status badges, hairline border card per no-shadow rule.
"Not linked" badge: neutral pill (`gray-100` fill, `gray-700` text) — local-only is a
neutral state, not a warning; amber stays reserved for pending-sync. The link toggle
follows the design system Toggle spec (44×26 pill, off `gray-200`, on accent); overflow
menu rows keep ≥44px touch targets and content descriptions.

## Tests to add / update

- `SessionManagerTest` (update): offline/no-auth start creates local `PENDING` row without
  throwing; remote-insert failure no longer ends the session; offline `stopSession` keeps
  `PENDING` with `endedAt` set locally.
- `LoginViewModelTest` (update): no auto-forward on cold start; offline submit blocked with
  message; success silently claims eligible sessions then syncs and pops — no dialog events.
- `ClaimLocalDataUseCaseTest` (new): claims unowned **non-exempt** sessions cascading to
  their samples/reports; skips exempt sessions and their children; explicit-ids mode claims
  only those ids; skips rows already owned; returns claimed count; idempotent.
- `SetSessionClaimExemptUseCaseTest` (new): toggles exemption on unowned sessions; unlink
  reverts an owned session to unowned+exempt only while `PENDING`; returns failure once
  `SYNCED`.
- `SessionsViewModelTest` (update, additional): per-session link-state derivation
  (unowned / not-linked / pending / synced); `onToggleAccountLink` routes to exempt-toggle
  vs. manual claim correctly per state.
- `DashboardViewModelTest` (update, additional): `onSyncNow` invokes
  `SyncPendingDataUseCase`; disabled state when offline or signed out; in-progress state.
- `SyncPendingDataUseCaseTest` (new): FK order sessions→samples→reports; skips cleanly when
  unauthenticated or offline; per-row failure marks `SYNC_FAILED` without aborting the pass.
- `SubmitVerificationUseCaseTest` / `SubmitManualCaptureUseCaseTest` (update): verification
  persists without auth; owner attribution from session/cached identity; offline sync
  failure leaves `SYNC_FAILED` and still returns success for the local write.
- `SupabaseAuthRepositoryTest` (new/update): identity cached on sign-in; observe emits after
  process-death simulation (fake DataStore).
- `SessionsViewModelTest` / `DashboardViewModelTest` (update): null-identity state lists
  local sessions / renders dashboard instead of empty-loading; pending-upload counts.

## Architecture checks

- [x] No ViewModel imports Room / Retrofit / Supabase — **fixes** the existing
  `SessionsViewModel` → `SessionRemoteDataSource` violation; new VM inputs are use cases
  + core (`ConnectivityObserver`, `SessionManager` — existing precedent)
- [x] No Android import in `domain/` — `LocalIdentity`, `SessionSyncStatus`, new use cases
  are pure Kotlin; ConnectivityManager stays in `core/` ⚠ pre-existing tension: verify use
  cases already import Room DAOs / `data.supabase` — this task follows that precedent
  rather than expanding scope; consolidation noted in TODO.md
- [x] Use Case returns `Result<T>` — `ClaimLocalDataUseCase: Result<Int>`,
  `SyncPendingDataUseCase: Result<SyncSummary>`; `Observe*` returns `Flow` (precedent)
- [x] Repository interface in `domain/`, impl in `data/` — `AuthRepository` extension only
- [x] New entity in `data/local/entity/`, domain model in `domain/model/` — no new entity;
  new columns on existing entities; `LocalIdentity`/`SessionSyncStatus` in `domain/model/`
- [x] `@Singleton` only for app-scoped singletons — `ConnectivityObserver` qualifies (rule 7)

## Known-issues cross-reference (TODO.md)

- **WorkManager/offline sync queue (❌, Phase 2):** this task ships trigger-based
  foreground sync only — an explicit stepping stone; the Phase 2 durable-queue item stays.
- **Report retry not robust (Sprint 3 #3):** partially addressed — reports now ride the
  pending-sync pass; backoff still deferred.
- **Duplicated sync-state enums:** `SessionSyncStatus` knowingly adds a third; consolidate
  when WorkManager lands (keep the known issue open, reference it in the new code's KDoc).
- **Settings placeholder (Sprint 3 #4):** sign-in/account affordance lands on Dashboard;
  the Settings account section (incl. sign-out) remains with the Settings rework.
- **Persistent flagged queue (Phase 2):** unchanged — unverified FLAGGED frames are still
  lost on process death; only *verified* samples survive offline.

## Deferred (explicitly out of scope)

- WorkManager durable queue, backoff, and conflict resolution (sync is last-write-wins
  upsert; single-operator data makes conflicts implausible in Phase 1).
- Sign-out flow and multi-user-per-device data isolation (the per-session "Don't link"
  toggle mitigates mis-claiming, but it is opt-out: hybrid attribution plus silent
  claim-all still assume one medtech per device; flagged as ADR-007 consequence).
- Offline AI inference — impossible by design (cloud inference container); Phase 2 on-prem
  hardware addresses it.
- Settings account UI, admin flows, PDF/administrative reports.

## Suggested PR split (≤400-line PRs per CONTEXT.md §6)

1. `feat(core)`: identity cache + `ConnectivityObserver` + `AuthRepository` extension + Room v8.
2. `feat(capture)`: offline-first `SessionManager` + auth-relaxed verify/manual/persist use cases.
3. `feat(dashboard)`: direct-entry navigation + Login rework + claim/sync orchestration + Dashboard state UI + docs (ADR-007, schema.ts, TODO.md).
