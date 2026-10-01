---
type: process
status: verified
verified: 2026-10-01
commit: b64271d2
---

# sign-in

Getting a medtech onto the device, and off it.

**Input** — a dashboard-provisioned email and password, and a network.
**Output** — a cached `LocalIdentity` that every later write is owned by; on sign-out, that
identity gone and the medtech's unsynced work discarded; on a password change, the same identity
signing in with a new password.

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

## Movement — change password (14zcqntjph9)

Accounts are invite-only: the invitee sets a first password on the Admin Console. After that
the medtech changes it from Settings.

1. **Reached from Settings, signed in only.** `SettingsCards.kt::ChangePasswordRow` opens
   `ui/settings/ChangePasswordScreen.kt` (`Screen.ChangePassword`). The row stays tappable
   offline; the screen says it needs a connection.
2. **Checked on the phone first.** `ChangePasswordViewModel::onSubmit` refuses an empty field, a
   confirmation that does not match, and a new password equal to the current one, without
   calling the server. Offline, `canSubmit` is false and the form shows
   `change_password_offline_notice`.
3. **Online only.** `ChangePasswordUseCase` answers `NoConnection` offline without trying.
4. **Current password, then new.** `SupabaseAuthRepository.kt::changePassword` signs in again
   with the session's email and the current password. A wrong one is
   `WrongCurrentPassword` (`passwordCheckFailure`) and nothing changes. Then `updateUser` sets
   the new one; the provider's strength rules, and its `same_password`, come back through
   `passwordUpdateFailure`. Supabase does not require the current password, so checking it is
   our choice: a phone left unlocked cannot have its password changed by whoever picks it up.
5. **This phone stays signed in.** The re-sign-in replaced its session with a fresh one for the
   same account, and Supabase keeps the session that made the change. The cached identity is
   untouched, so every owner-scoped query and the unsynced push queue carry on as before.
6. **Other sign-ins end.** Supabase revokes every other session of the account on a password
   change. Other phones and the Admin Console then need the new password.

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

- Re-checking the current password by signing in. Swap it for `updateUser` alone and anyone
  holding an unlocked phone can change the password; swap it for Supabase's reauthentication
  nonce and the medtech needs their email open to change a password they already know.

**Does not hit**
- Local data, on a password change. Nothing is pushed, pulled, discarded or re-owned.
- `profiles`. The app never reads the row; role and admin capability live only in RLS
  ([`Profile`](../objects/Profile.md)).
- Frames still queued for inference in a session that *did* sync. They stay on the device.

## Surfaces

`ui/login/LoginScreen.kt` and `LoginViewModel`, the splash gate in `MainActivity`, the
sign-out button and dialog on `ui/settings/SettingsScreen.kt`, and
`ui/settings/ChangePasswordScreen.kt` with `ChangePasswordViewModel`.

## See

`domain/usecase/auth/`, `data/repository/SupabaseAuthRepository.kt`,
`domain/usecase/sync/FetchRemoteDataUseCase.kt`.
