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
identity gone and the medtech's unsynced work discarded; when the server refuses the account,
the identity gone and the account's clinical data wiped from the phone.

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
3. **Forget, then sign out.** `SupabaseAuthRepository.kt::signOut` removes the three cached keys
   first and revokes the Supabase session second. The order matters: a cached identity with no
   Supabase session is what the account check below reads as "the server refused this account",
   so the medtech's own sign-out must never pass through that state.

## Movement — the server refuses the account (14zcqntjph8)

An org admin offboards a medtech by deleting their login (or banning it). The phone learns it
the next time it reaches the server, and wipes itself. A password changed on the web or on
another phone revokes this phone's login the same way, and the phone cannot tell the two
apart, so the wipe is shaped to cost that medtech nothing but a sign-in.

1. **Checked at every sync pass, online only.** `data/sync/SyncWorker.kt::doWork` runs
   `EnforceAccountAccessUseCase` before pushing. Offline or with nobody signed in it does nothing
   and returns `UNKNOWN` (`EnforceAccountAccessUseCase::invoke`). Sync passes run at app start,
   after every save, and when the signal returns, so this is "the next time the phone reaches the
   server".
2. **The signal is a refused renewal of the login.**
   `SupabaseAccountAccessRepository::checkAccountAccess` answers `REFUSED` when the SDK holds no
   session while an identity is cached (supabase-kt drops the session on a 4xx renewal without
   saying why), or when a renewal it asks for is answered 4xx
   (`accessForRenewalStatus`). 408, 429, 5xx, timeouts and no network are `UNKNOWN`; a token that
   expired offline is the SDK's `RefreshFailure` and renews when the signal returns. Provider-
   neutral: it does not care whether the login was deleted or banned (D7). A successful answer is
   trusted for five minutes.
3. **On a refusal:** detach from the active session; `WipeLocalAccountDataUseCase` removes
   everything synced (`data/local/dao/AccountWipeDao.kt`), those rows' JPEGs, Coil's image caches
   and the exported report files, clears the initial-fetch flag, and counts the account's
   unsynced items; the count is stored (`SignedOutNoticeStore`); then `signOut`. **Every unsynced
   row stays, the signed-out account's included**, with the parents it needs: it uploads after
   that medtech signs back in (C8).
4. **The medtech sees why.** `MainViewModel.signedOutByServer` sends any open screen to Login with
   the graph popped (`AgarthaNavGraph`); `LoginScreen.kt::SignedOutNoticeCard` says the phone was
   signed out, to sign in with the new password if it changed, and how many unuploaded items
   are waiting. The notice stays until the next
   successful sign-in (`SignInUseCase`).

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
- `SupabaseAccountAccessRepository::checkAccountAccess`. Widen what counts as `REFUSED` and a
  working medtech's phone is wiped; the one rule is in `accessForRenewalStatus` and its test.
- The order inside `SupabaseAuthRepository.kt::signOut` (identity before session), for the same
  reason.

**Does not hit**
- `profiles`. The app never reads the row; role and admin capability live only in RLS
  ([`Profile`](../objects/Profile.md)).
- Frames still queued for inference in a session that *did* sync. They stay on the device.

## Surfaces

`ui/login/LoginScreen.kt` and `LoginViewModel` (including the signed-out-by-server notice), the
splash gate and the jump to Login in `MainActivity` / `AgarthaNavGraph`, and the sign-out button
and dialog on `ui/settings/SettingsScreen.kt`.

## See

`domain/usecase/auth/`, `data/repository/SupabaseAuthRepository.kt`,
`data/repository/SupabaseAccountAccessRepository.kt`, `data/local/dao/AccountWipeDao.kt`,
`domain/usecase/sync/FetchRemoteDataUseCase.kt`.
