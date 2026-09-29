---
type: process
status: verified
verified: 2026-09-29
commit: feaa4803
---

# session-lifecycle

Opening a smear, working in it, coming back to it, and letting go of it. It never ends.

**Input** — a selected patient and a label.
**Output** — a `sessions` row, and an active-session pointer that capture reads.

**consumes** [`Patient`](../objects/Patient.md)
**produces** [`Session`](../objects/Session.md)

## Movement

1. **Label it.** The New Session sheet (`SessionsScreen.kt::NewSessionSheet`) pre-fills a label
   from `GenerateSessionLabelUseCase`, built by `domain/session/SessionLabelGenerator.kt`:
   `LDNJ-M21-S01` — surname abbreviation and first initial, sex and age, the patient's smear
   sequence. The medtech may edit it.
2. **Validate.** `SessionsViewModel::onCreateSession` trims and uppercases the label, rejects a
   blank one, and rejects one this patient already has (`SessionRepository.isSessionLabelTaken`)
   before the Room unique index on `(patient_id, label)` would.
3. **Create and push.** `SessionManager.kt::startSession` requires the cached identity (the login
   gate guarantees one), writes the row `pending` with the device id, tries a direct Supabase
   upsert (`pushSessionInsert`, which never throws), makes it active, and calls
   `syncScheduler.requestSync()` for the case where the direct push could not land.
4. **Activate.** `activate` sets `SessionState.Active` and writes the id to
   `ActiveSessionIdStore`, the one piece of session state that survives process death.
   `NetworkMonitor` and capture both key off `SessionState`.
5. **Resume.** Tapping an existing smear calls `SessionManager::resumeSession`: no Room write, no
   Supabase call, just activation.
6. **Restore at launch.** `AgarthaVisionApp.onCreate` calls `restoreActiveSession`, which
   re-activates the stored id only if the state is still `Idle`, with a compare-and-set so a
   user action that raced it wins. A stored id that no longer resolves clears itself.
7. **Detach.** `SessionManager::clearActive` clears the pointer and goes `Idle` without touching
   the row. Sign-out uses it ([`sign-in`](sign-in.md)).

## Why this shape

A session is one fecal smear, and a medtech goes back to a smear — so there is no end state,
no `ended_at`, and nothing that closes it. What looks like "session state" is only which smear
the app is working in right now, which is why it lives in memory plus one persisted pointer
rather than on the row.

## If you change this

**Hits**
- Capture. `CaptureViewModel.onCapture` needs `SessionState.Active` and files the frame under its
  id; a wrong active id files a patient's image under another patient's smear.
- The label rules, in three places that must agree: the generator, the ViewModel pre-check, and
  the Room unique index. `MAX_LABEL_LENGTH` in the generator and `SESSION_LABEL_MAX_LENGTH` in
  `ui/sessions/SessionInputLimits.kt` are separate constants kept in step by hand.
- Sync order: patients before sessions ([`sync`](sync.md)).

**Does not hit**
- Postgres uniqueness. Labels are unique per patient only on the device; the server does not
  constrain them.
- The inference queue. Frames already captured keep their session id whatever the active
  session becomes.

## Surfaces

`ui/sessions/SessionsScreen.kt` (a patient's sessions and the New Session sheet),
`ui/sessionlist/SessionListScreen.kt` (all sessions), the Home recent-session card, and capture.

## See

`core/session/SessionManager.kt`, `core/session/ActiveSessionIdStore.kt`,
`domain/session/SessionLabelGenerator.kt`, `ui/sessions/SessionsViewModel.kt`.
