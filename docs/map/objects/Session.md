# Session

**One sentence.** One fecal smear — the unit of work a medtech opens, captures into, and ends.
Product says "smear" or "slide"; the table and the Room entity both say `sessions`.

## Why this shape

A session is not "an app session" and not "a recording". It is one Kato-Katz slide, because
that is the unit EPG is computed over and the unit a report covers. A separate `smears` table
was considered and rejected — one slide per session is the operational norm.

The Room row is deliberately more permissive than the Postgres row: it allows a null owner and
carries two local-only sync columns, so a medtech can work with no account and no network and
have the row claimed later. Postgres never sees that state.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:32-39`, plus `0005` and `0010`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `user_id` | **NOT NULL**, FK → `profiles(id)` |
| `device_id` | NOT NULL text |
| `started_at` | NOT NULL, default `now()` |
| `ended_at` | nullable |
| `notes` | nullable |
| `label` | nullable — added by `supabase/migrations/0005_session_label.sql:9-10` |
| `psgc_barangay_code` | nullable, `CHECK ~ '^[0-9]{10}$'` — added by `supabase/migrations/0010_session_psgc_barangay.sql:46-48` |

Index `sessions_user_started_idx (user_id, started_at desc)` —
`supabase/migrations/0005_session_label.sql:12-13`. Partial index
`sessions_psgc_barangay_idx` on non-null barangay codes —
`supabase/migrations/0010_session_psgc_barangay.sql:50-52`.

`psgc_barangay_code` is the patient's barangay and the **only** key the surveillance map
aggregates on. Barangay level only: the code resolves upward to city/municipality, province
and region by itself, so there are deliberately no denormalised parent columns. It is a plain
value, not a foreign key — the barangay reference data is on-device only. See
[`PsgcBarangay`](PsgcBarangay.md).

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/SessionEntity.kt:14-65`)

PK column is `session_id`, not `id`. Two differences that matter:

- `user_id` is **nullable** (`SessionEntity.kt:19-20`) — a session started before anyone
  signed in has no owner. Postgres forbids this; the gap is closed by claim-before-sync.
- Two **Room-only** columns with no Postgres equivalent: `supabase_status`
  (`SessionEntity.kt:55-56`, values from `domain/model/SessionSyncStatus.kt:14-18`) and
  `claim_exempt` (`SessionEntity.kt:63-64`).

Documented shape and the mismatch list: `schema.ts:174-211`, `schema.ts:633-639`.

The `id` ↔ `session_id` translation happens in exactly one place:
`data/supabase/SessionRemoteDataSource.kt:63-73`.

## Connected to

- **Owned by** [`Profile`](Profile.md) — remotely required, locally optional.
- **References** [`PsgcBarangay`](PsgcBarangay.md) by code, with no foreign key. Aggregated
  per barangay by `public.barangay_prevalence()`
  (`supabase/migrations/0010_session_psgc_barangay.sql:71-131`).
- **Owns** [`Sample`](Sample.md), 1 → many, ON DELETE CASCADE
  (`supabase/migrations/0001_init.sql:45`).
- **Owns** [`Report`](Report.md), 1 → many, ON DELETE CASCADE
  (`supabase/migrations/0008_reports.sql:15`). Multiple reports per session are intended.
- **Looks like but is not** `SessionState` (`core/session/SessionState.kt`). That is the
  in-memory Active/Idle flag driving the capture UI; it holds a `SessionEntity` but is not
  persisted and does not survive process death.

## If you change this

**Hits**
- `SessionManager` — start, resume, pause/resume inference, and end all read and write this
  row (`core/session/SessionManager.kt:57-144`).
- The claim path. `claimUnownedSessions` and `getClaimableSessions` filter on `user_id IS NULL`
  and `claim_exempt` (`data/local/dao/SessionDao.kt:83`, `:104`); the sample and report claims
  cascade off the session ids (`domain/usecase/auth/ClaimLocalDataUseCase.kt:44-50`).
- The sync ordering. Sessions push first because samples and reports FK to them
  (`domain/usecase/sync/SyncPendingDataUseCase.kt:66-74`).
- The CSV header block, which prints session id, label, timestamps, and device id
  (`domain/usecase/records/ReportCsvBuilder.kt:43-51`).
- The New Session sheet, which requires a barangay before a session can start
  (`ui/sessions/SessionsViewModel.kt:195-207`). Existing sessions keep a null code and
  nothing backfills them, so they are simply absent from the map.

**Does not hit**
- Supabase, if you are adding a Room-only column. `supabase_status` and `claim_exempt` both
  exist locally with **no migration**, and that is deliberate — sync only runs authenticated
  and post-claim. Adding a third local column needs a Room version bump and nothing else.
- The Room migration path. `fallbackToDestructiveMigration(dropAllTables = true)` is in force
  (`core/di/DatabaseModule.kt:54`), so a version bump wipes the device rather than migrating.
  Acceptable in Phase 1; there is no production data.

## Surfaces

Written by `SessionManager` (`core/session/SessionManager.kt`) and the claim use case. Read by
the session picker (`ui/sessions/SessionsScreen.kt`), the Dashboard active-session card, every
Records screen, and the report generator. Pushed to Supabase by
`data/supabase/SessionRemoteDataSource.kt`.

## See

`supabase/migrations/0001_init.sql:32-39`, `supabase/migrations/0005_session_label.sql`,
`supabase/migrations/0010_session_psgc_barangay.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/SessionEntity.kt`, `schema.ts:174-211`.
