---
type: process
status: verified
verified: 2026-09-29
commit: feaa4803
---

# sign-in

Getting a medtech onto the device, and off it.

**Input** — a dashboard-provisioned email and password, and a network.
**Output** — a cached `LocalIdentity` that every later write is owned by; on sign-out, that
identity gone and the medtech's unsynced work discarded.

**consumes** [`Profile`](../objects/Profile.md) (through Supabase Auth)
**produces** the cached identity; pulls [`Patient`](../objects/Patient.md),
[`Session`](../objects/Session.md), [`Sample`](../objects/Sample.md) and
[`Report`](../objects/Report.md) rows onto the device

## Movement — launch

1. **Gate on the cached identity, not live auth.** `MainViewModel` runs
   `ResolveAuthGateUseCase`: a cached user id means `Authed`, none means `NeedsLogin`, and
   `MainActivity` picks the start destination from it while the splash holds on `Loading`. The gate
   is **first-run only** — once anyone has signed in on this device, an expired token or an offline
   cold start still opens the app. That is the point: a medtech in a barangay with no signal must
   not be sent to a login screen whose submit is disabled offline.

## Movement — sign in

1. **Online only.** `LoginViewModel` blocks submit without connectivity (`ConnectivityObserver`).
   There is no sign-up flow; accounts are made in the Supabase dashboard.
2. **Authenticate, then cache.** `SignInUseCase` → `SupabaseAuthRepository.kt::signIn` signs in
   with email and password, then `::cacheIdentity` writes user id, email and display name
   (`full_name` from auth metadata) to DataStore.
3. **Push, then pull, before leaving the screen.** `LoginViewModel::syncAndFetch` awaits
   `SyncPendingDataUseCase` and then `FetchRemoteDataUseCase`, which brings the account's
   patients, sessions, samples (with predictions, detections and findings) and reports down, then
   starts the image cache. This is the one sync call that is awaited directly rather than
   scheduled: the medtech needs their patients on the device before leaving the clinic. See
   [`sync`](sync.md).

## Movement — sign out

1. **Detach, do not end.** `SignOutUseCase` calls `SessionManager.clearActive`; the session stays
   open, as sessions never end.
2. **Discard unsynced work — before clearing the identity.** `DiscardUnsyncedDataUseCase`, scoped
   by the cached user id, deletes the medtech's reports, samples, sessions and patients that never
   reached Supabase, plus flagged frames under those sessions, then their JPEGs. The Settings
   dialog says how many items that is and that it cannot be undone
   (`settings_sign_out_dialog_body_pending`). It runs before `signOut` because `signOut` clears the
   id it is scoped by.
3. **Sign out and forget.** `SupabaseAuthRepository.kt::signOut` revokes the Supabase session and
   removes the three cached keys.

## Why this shape

Offline-first work needs an owner before it exists: a patient belongs to a user, a session to a
patient. So one sign-in is required, once; after that the cached identity carries attribution
through any amount of offline work. The discard on sign-out exists because the push queue is
scoped by `user_id` — left behind, another medtech's unsynced rows would sit invisible and never
upload.

## If you change this

**Hits**
- Every owner-scoped query and the sync push set, which all key on the cached user id.
- The order inside `SignOutUseCase`. Discard after `signOut` silently discards nothing.
- `DiscardUnsyncedDataUseCase` deletes verified samples outright. That is inside C8 only because
  they never reached the corpus; anything that widens what it selects has to keep that true.

**Does not hit**
- `profiles`. The app never reads the row; role and admin capability live only in RLS
  ([`Profile`](../objects/Profile.md)).
- Frames still queued for inference in a session that *did* sync. They stay on the device.

## Surfaces

`ui/login/LoginScreen.kt` and `LoginViewModel`, the splash gate in `MainActivity`, and the
sign-out button and dialog on `ui/settings/SettingsScreen.kt`.

## See

`domain/usecase/auth/`, `data/repository/SupabaseAuthRepository.kt`,
`domain/usecase/sync/FetchRemoteDataUseCase.kt`.
