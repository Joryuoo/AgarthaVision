# Session

**One sentence.** One fecal smear — the unit of work a medtech opens, captures into, and never ends,
now belonging to a [`Patient`](Patient.md). Product says "smear" or "slide"; the table and the Room
entity both say `sessions`.

## Why this shape

A session is not "an app session" and not "a recording". It is one fecal smear (Direct Smear),
because that is the unit LPF density is computed over and the unit a report covers. A separate
`smears` table was considered and rejected — one slide per session is the operational norm.

A session belongs to a [`Patient`](Patient.md). Login is mandatory, so every session has a known
`user_id` and `patient_id` from creation. `ended_at`, `notes`, `psgc_barangay_code`, and `claim_exempt`
have been removed: smears stay open, patient identity replaces ad-hoc notes, barangay moved to the
patient, and unowned data is no longer possible.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:150-163`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `user_id` | **NOT NULL**, FK → `profiles(id)` |
| `patient_id` | **NOT NULL**, FK → `patients(id)` |
| `device_id` | NOT NULL text |
| `started_at` | NOT NULL timestamptz, default `now()` |
| `label` | nullable text (auto-generated e.g. `C.G.-0730600000-001`) |

Indexes: `sessions_user_started_idx (user_id, started_at desc)` and
`sessions_patient_idx (patient_id, started_at desc)` (`0001_init.sql:175-176`).

**Deliberately absent, all four:**
- `notes` — removed. Replaced by the [`Patient`](Patient.md) entity.
- `ended_at` — removed. Sessions never end (86d4ab4vm); smears stay open for re-examination.
- `psgc_barangay_code` — moved to [`Patient`](Patient.md), which is the unit surveillance aggregates on.
- `claim_exempt` — removed. Login is mandatory.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/SessionEntity.kt`)

PK column is `session_id`, not `id`. Key details:

- `patient_id` is a foreign key to `PatientEntity` (`patient_id`).
- `supabase_status` is Room-only (`'synced'`, `'pending'`, `'sync_failed'`).
- The `id` ↔ `session_id` translation happens in `data/supabase/SessionRemoteDataSource.kt`.

Documented shape: `schema.ts:244-276`.

## Connected to

- **Owned by** [`Patient`](Patient.md) (`patient_id` FK) and [`Profile`](Profile.md) (`user_id` FK).
- **Owns** [`Sample`](Sample.md), 1 → many, ON DELETE CASCADE (`supabase/migrations/0001_init.sql:183`).
  (Room declares `NO_ACTION` locally to prevent accidental cascading deletion per C8).
- **Owns** [`Report`](Report.md), 1 → many, ON DELETE CASCADE (`0001_init.sql:311`). Multiple reports
  per session are intended.
- **Looks like but is not** `SessionState` (`core/session/SessionState.kt`). That is the
  in-memory Active/Idle flag driving the capture UI; it holds a `SessionEntity` but is not
  persisted and does not survive process death. What *is* persisted is a pointer to the active
  session id (`core/session/ActiveSessionIdStore.kt`), restored at launch.

## If you change this

**Hits**
- `SessionManager` — start, resume, pause/resume inference, restore and detach all read and
  write this row (`core/session/SessionManager.kt`).
- The sync ordering. Patients push first, then sessions, because samples and reports FK to them
  (`domain/usecase/sync/SyncPendingDataUseCase.kt:76-90`).
- The PDF header block, which prints session id, label, patient details, timestamps, and device id.
- The New Session sheet, which creates a session for an already-selected patient.

**Does not hit**
- **Supabase**, if you are adding a Room-only column. `supabase_status` exists locally with no migration.
- **The Room migration path.** `fallbackToDestructiveMigration(dropAllTables = true)` is in force
  (`core/di/DatabaseModule.kt:54`), so a version bump wipes the device rather than migrating.

## Surfaces

Written by `SessionManager` (`core/session/SessionManager.kt`). Read by the session picker
(`ui/sessions/SessionsScreen.kt`), the Dashboard active-session card, Patient Sessions, every
Records screen, and the report generator. Pushed to Supabase by
`data/supabase/SessionRemoteDataSource.kt`.

## See

`supabase/migrations/0001_init.sql:150-177`,
`app/src/main/java/com/agarthavision/data/local/entity/SessionEntity.kt`,
`schema.ts:244-276`.
