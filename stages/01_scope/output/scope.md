# Scope: Settings Screen — Account & App Management (ADR-007 follow-ups)

## Summary

Replace the Settings placeholder with the production Settings screen and make it the home
for **account management** (identity display, sign-in entry, and a new **sign-out** flow —
the app currently has none) and **app management** (theme-toggle mirror, sync status with
pending/failed counts and a manual "Sync now"/retry affordance, app info). This closes the
gaps deliberately deferred by ADR-007 — no sign-out, Dashboard-only account affordances —
plus Sprint 3 backlog #3 (manual retry surface), #4 (Settings rework), and #10 (theme
mirror), and introduces **ADR-008** to record the sign-out semantics.

## Current-state findings (verified in code this session)

- `ui/settings/SettingsScreenPlaceholder.kt` is a one-line `Text("Settings — TODO")` stub;
  `AgarthaNavGraph.kt:55` already defines `Screen.Settings` and wires the placeholder at
  line 305 with no ViewModel. The bottom tab bar (`AgarthaBottomBar`) already carries a
  Settings destination per the design spec.
- **`AuthRepository` has no sign-out method at all.** `SupabaseAuthRepository` caches the
  `LocalIdentity` under three DataStore keys (`local_identity_user_id` / `_email` /
  `_display_name`) with no clear path — sign-out is a small, well-bounded change: revoke the
  Supabase token + clear three keys.
- Everything the sync section needs already exists and is reusable as-is:
  `SyncPendingDataUseCase`, `ConnectivityObserver`, `ObserveLocalIdentityUseCase`,
  `ObserveThemeModeUseCase` / `SetThemeModeUseCase`. No new infrastructure.
- Pending-sync DAO reads are one-shot suspend getters (`getSessionsPendingSync`, etc.);
  Settings wants live counts → add `Flow<Int>` COUNT queries to the three DAOs (query-only,
  no schema change).
- `domain/usecase/auth/HasActiveSessionUseCase.kt` appears orphaned after the ADR-007
  LoginViewModel rewrite — stage 02 must verify references and delete it if dead.
- `AuthRepository.getCurrentUserId()` (live token) vs `currentLocalUserId()` (cached)
  duplication: once sign-out can clear the cache, every caller's choice of semantic matters.
  Stage 02 audits call sites (e.g. `FlaggedFrameStore.clear()` uses `getCurrentUserId()`).
- Commitlint scopes (`capture inference dashboard reports theme core data ci docs`) have no
  `settings` scope — add it to `commitlint.config.js` in this task's first PR.

## Decisions for the review gate

1. **Sign-out semantics (→ ADR-008) — RECOMMENDED: full local sign-out.** Revoke the
   Supabase token **and** clear the cached `LocalIdentity`, returning the device to the
   never-signed-in state: subsequent offline work is stored unowned (`user_id = NULL`) and
   claimed at the next login, exactly per the existing ADR-007 claim model. Already-owned
   rows (synced or PENDING) **keep** their `user_id` — ownership was attributed at write
   time and the per-session unlink flow already governs reverting it. Trade-off vs. the
   alternative (revoke token only, keep cached identity): full sign-out means a signed-out
   device stops silently attributing work to the last medtech — which **partially mitigates
   the ADR-007 shared-device fail-open consequence** (the borrower can sign the owner out
   first) at the cost of the "convenient re-attribution" behavior. Token-only sign-out would
   preserve attribution but makes sign-out nearly meaningless on a shared device.
2. **Sign-out with unsynced data — RECOMMENDED: warn, don't block.** Confirmation dialog
   shows pending counts ("N items not yet uploaded — they stay on this device and upload
   the next time this account signs in"); the user can proceed. Blocking would trap a
   borrower behind someone else's upload. Hard block only while a capture session is
   **Active** ("End the session before signing out") — simplest safe rule.
   Sign-out also clears the in-memory `FlaggedFrameStore` — Phase 1 docs already declare
   flagged frames "lost on logout"; until now there was no logout to honor that.
3. **Retry affordance scope — RECOMMENDED: reuse, don't build.** The Settings sync card
   shows per-type pending + failed counts and a "Sync now" button wired to the existing
   `SyncPendingDataUseCase` (disabled offline / signed-out, progress while running). This
   satisfies Sprint 3 #3's "or clearly defer behind a manual retry state" arm; automatic
   backoff stays deferred with the Phase 2 WorkManager queue.
4. **Theme toggle placement — RECOMMENDED: mirror, not move.** Settings gets the toggle
   (Sprint 3 #10); the Dashboard header sun/moon stays. One preference, two surfaces, both
   reading the same DataStore-backed use cases — no divergence risk.

## Affected files

| File | Change description | Layer |
|---|---|---|
| `ui/settings/SettingsScreen.kt` (new) | Production screen, four sections — **Account** (signed-in: name/email + Sign out; cached-only: identity + "Signed out" note + Sign in CTA; never-signed-in: Sign in CTA), **Data & Sync** (connectivity state, per-type pending/failed counts, Sync now), **Appearance** (theme toggle), **About** (version, environment). Sign-out confirmation `AlertDialog` (`DialogShape`) with pending-count copy | Presentation |
| `ui/settings/SettingsViewModel.kt` (new) | Single `StateFlow<SettingsState>` combining identity, connectivity, pending counts, theme mode, sync-in-progress; intents: `onSignOut` (guard Active session → error event), `onSyncNow`, `onToggleTheme`; one-shot events via `SharedFlow` | Presentation |
| `ui/settings/SettingsScreenPlaceholder.kt` | Delete | Presentation |
| `ui/navigation/AgarthaNavGraph.kt` | Wire `SettingsScreen` with `hiltViewModel()`; Login route reachable from Settings' Sign in CTA (same destination the Dashboard banner uses) | Presentation |
| `domain/repository/AuthRepository.kt` | Add `suspend fun signOut()` (KDoc: revokes the live session **and** clears the cached identity per ADR-008) | Domain |
| `domain/usecase/auth/SignOutUseCase.kt` (new) | `invoke(): Result<Unit>`; fails with a domain error while `SessionManager.state` is Active; clears `FlaggedFrameStore`; local clearing must succeed even when the remote token revocation fails (offline sign-out works) | Domain |
| `domain/model/PendingSyncCounts.kt` (new) | `data class PendingSyncCounts(sessions, samples, reports, failed)` — pure Kotlin | Domain |
| `domain/usecase/sync/ObservePendingSyncCountsUseCase.kt` (new) | Combines the three DAO count flows → `Flow<PendingSyncCounts>` (follows the existing verify-use-case DAO-import precedent; consolidation stays a known issue) | Domain |
| `data/repository/SupabaseAuthRepository.kt` | Implement `signOut()`: `runCatching { supabase.auth.signOut() }` then always clear the three identity keys | Data |
| `data/local/dao/SessionDao.kt` / `SampleDao.kt` / `ReportDao.kt` | Add `Flow<Int>` COUNT queries for pending / failed rows (query-only) | Data |
| `domain/usecase/auth/HasActiveSessionUseCase.kt` | Delete if verified unreferenced (stage 02 check) | Domain |
| `app/src/main/res/values/strings.xml` | Settings section titles, sign-out copy + dialog, sync states, about labels | Presentation |
| `commitlint.config.js` | Add `settings` to allowed scopes | Tooling |
| `CONTEXT.md` | New **ADR-008** (sign-out semantics: full local sign-out, warn-don't-block, Active-session guard); §3 note Settings is a full destination; §4 screens note; ADR-007 consequences updated (shared-device mitigation) | Docs |
| `TODO.md` | Mark Sprint 3 #4 and #10 ✅, #3 ⏳→manual-retry-shipped; resolve ADR-007 known-issue arms (a) sign-out and (c) Settings mirror | Docs |

## Schema / migration

**None** — no Room migration (v8 unchanged; new DAO methods are queries over existing
columns) and no Supabase migration (sign-out is token + DataStore only; RLS untouched).

## Design tokens (UI tasks only)

Existing tokens only — no new colors or radii. Section cards: surface + 1px `gray-100`
hairline, `md` radius, no shadow. Section labels: Micro/eyebrow (10–11/600 uppercase,
`gray-500`). Rows: Body 15/500 labels, Caption metadata, `tnum` on all counts and the
version string. Sign out: destructive pill button (`red` fill / white text) behind an
`AlertDialog` with `shape = DialogShape`. Sync now: accent pill, disabled state per spec;
pending counts amber/`amber-tint` badge, all-synced `green`/`green-tint`, offline
`gray` neutral. Theme toggle: 44×26 pill, off `gray-200`, on accent. Icons: lucide-style
inline SVG, 1.5–1.8 stroke; all rows ≥44px touch targets with content descriptions.
All copy in `strings.xml`.

## Tests to add / update

- `SignOutUseCaseTest` (new): clears token and cached identity; returns failure while a
  capture session is Active; still clears local identity when remote revocation throws
  (offline sign-out); clears the flagged-frame store.
- `SupabaseAuthRepositoryTest` (update/new): `signOut()` removes all three DataStore keys;
  `observeLocalIdentity()` emits `null` afterward; remote failure doesn't abort local clear.
- `SettingsViewModelTest` (new): state renders signed-in / cached-only / never-signed-in
  identity variants; `onSignOut` blocked with event while session Active, otherwise invokes
  use case; `onSyncNow` invokes `SyncPendingDataUseCase` and is disabled offline/signed-out;
  theme toggle round-trips through the use cases; pending/failed counts surface in state.
- `ObservePendingSyncCountsUseCaseTest` (new): combines DAO flows; zero-state; failed
  counted separately from pending.
- DAO count queries: covered via fake-DAO usage in the use-case test (instrumented DAO
  tests remain the existing convention gap — unchanged).

## Architecture checks

- [x] No ViewModel imports Room / Retrofit / Supabase — `SettingsViewModel` takes use cases
  + `ConnectivityObserver`/`SessionManager` (existing core precedent)
- [x] No Android import in `domain/` — `PendingSyncCounts`, both new use cases pure Kotlin;
  ⚠ `ObservePendingSyncCountsUseCase` imports Room DAOs, following the existing verify/sync
  use-case precedent (tracked known issue, not expanded here)
- [x] Use Case returns `Result<T>` — `SignOutUseCase: Result<Unit>`; `Observe*` returns
  `Flow` (existing precedent)
- [x] Repository interface in `domain/`, impl in `data/` — `AuthRepository` extension only
- [x] New entity in `data/local/entity/`, domain model in `domain/model/` — no new entity;
  `PendingSyncCounts` in `domain/model/`
- [x] `@Singleton` discipline — no new singletons; ViewModel and use cases unscoped

## Known-issues cross-reference (TODO.md)

- **Settings placeholder (Known Issues #1, Sprint 3 #4):** resolved by this task.
- **ADR-007 follow-ups (a) sign-out, (c) Settings account mirror:** resolved; **(b)
  fail-open link toggle:** partially mitigated (sign-out ends silent attribution on shared
  devices) — the opt-out toggle itself is unchanged, note stays with softened wording.
- **Report retry not robust (Sprint 3 #3):** manual-retry arm shipped here (Sync now +
  failed counts); automatic backoff remains Phase 2.
- **Theme toggle mirror (Sprint 3 #10):** resolved.
- **Detekt debt (40 findings):** untouched by this task — separate cleanup chore; this
  task must add zero new findings.
- **Duplicated sync-state enums:** read-only consumption here; consolidation still waits
  for the WorkManager work.

## Deferred (explicitly out of scope)

- Password change / profile editing (accounts are dashboard-provisioned per §5; no
  self-service credential management in Phase 1).
- Multi-user-per-device data isolation (Phase 2; sign-out only mitigates).
- "Clear local data" / storage-management action — destructive; needs its own design
  (interaction with unsynced rows and the no-DELETE training-data policy, ADR-004).
- WorkManager durable sync queue, backoff, conflict handling (Phase 2, unchanged).
- Capture overflow menu (Sprint 3 #5) — separate Capture-screen task.
- Repo-wide detekt cleanup chore (tracked in Known Issues, separate PR).
- Notification/language preferences, admin flows, PDF reports.

## Suggested PR split (≤400-line PRs per CONTEXT.md §6)

1. `feat(core): sign-out — AuthRepository.signOut + SignOutUseCase + ADR-008` (+
   commitlint `settings` scope, `HasActiveSessionUseCase` removal if dead).
2. `feat(settings): production Settings screen — account, sync, appearance, about` (+
   DAO count queries, `ObservePendingSyncCountsUseCase`, docs/TODO sync).
