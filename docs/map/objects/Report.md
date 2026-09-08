# Report

**One sentence.** A snapshot of one session's findings at one moment — counts, EPG per species,
and a pointer to a CSV file. Table and Room entity are both `reports`; the product calls the
artefact a "session report".

## Why this shape

A report is a *snapshot*, not a view. Generating one twice produces two rows, ordered newest
first, because the underlying samples can change between generations and a clinical record must
be reproducible. That is why the aggregates are denormalised into columns rather than computed
on read.

The split matters: **the numbers sync, the file does not.** Row-only sync keeps the CSV on the
device — a deliberate Phase 1 boundary that avoids uploading anything file-shaped to Storage
besides sample images.

## Shape

**Postgres** (`supabase/migrations/0008_reports.sql:13-25`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `session_id` | NOT NULL, FK → `sessions(id)`, CASCADE |
| `user_id` | NOT NULL, FK → `profiles(id)` |
| `report_type` | NOT NULL default `'session'`, **CHECK allows only `'session'`** (`0008_reports.sql:17`) |
| `generated_at` | NOT NULL, default `now()` |
| `total_samples` | integer **NOT NULL, no default** (`0008_reports.sql:19`) |
| `total_eggs_confirmed` | integer NOT NULL, no default |
| `positive_species` | `text[]` NOT NULL default `'{}'` |
| `epg_per_species` | `jsonb` NOT NULL default `'{}'` |
| `csv_file_path` | nullable text — a **device-local** path, meaningless to any other client |
| `created_at` | NOT NULL, default `now()` |

Indexes on `(session_id, generated_at desc)` and `(user_id, generated_at desc)` —
`0008_reports.sql:27-30`.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/ReportEntity.kt:39-78`)

PK column is `report_id`. Differences:

- `supabase_status` is **Room-only** — no Postgres column exists
  (`ReportEntity.kt:73-74`, `domain/model/ReportSyncStatus.kt`, `schema.ts:101-115`).
- The two collection columns are stored as Gson strings, `positive_species_json` and
  `epg_per_species_json` (`ReportEntity.kt:62-68`), and expanded back into `List<String>` and
  `JsonObject` at the sync boundary (`data/supabase/ReportRemoteDataSource.kt:37-59`).
- `generated_at` and `created_at` are epoch millis locally, ISO strings remotely
  (`data/supabase/ReportRemoteDataSource.kt:49`).

`schema.ts:362-363` claims `total_samples` defaults to 0. The migration gives it no default —
`0008_reports.sql:19`. The migration wins.

## Connected to

- **Owned by** [`Session`](Session.md) and [`Profile`](Profile.md).
- **Aggregates** [`Detection`](Detection.md) through [`Sample`](Sample.md) — it stores counts,
  never rows.
- **Looks like but is not** the CSV file. The file lives in `Documents/AgarthaVision/` under a
  name built from the session and report ids (`data/repository/DocumentsReportFileStore.kt:31-38`)
  and is shared from `ui/records/ReportSharing.kt`. `csv_file_path` is a pointer to it that no
  other device can resolve — and its shape depends on the API level the report was written on: a
  MediaStore `content://` URI on 29+, an absolute path on 26-28.

## If you change this

**Hits**
- `GenerateSessionReportUseCase` — it computes every aggregate and writes the file before the
  row (`domain/usecase/records/GenerateSessionReportUseCase.kt:48-95`).
- `ReportInsertRow`, or your column never reaches Postgres
  (`data/supabase/ReportRemoteDataSource.kt:61-83`).
- The two Gson serialisation points, if you touch either collection column.
- The Settings pending/failed counts, which read `supabase_status`
  (`data/local/dao/ReportDao.kt:46`, `:53`).

**Does not hit**
- Already-generated CSV files. Files are written once and never rewritten; changing the row
  leaves stale files on disk under their old names.
- EPG itself. The multiplier lives in `core/util/EpgCalculator.kt:12` and the counting rule in
  `data/local/dao/DetectionDao.kt:33-52`. A report stores the answer; it does not define it.
- The `administrative` report type. It is named in a comment (`0008_reports.sql:9`) but the
  CHECK rejects it — see the ghost list in `../../features.md`.

## Surfaces

Written by `GenerateSessionReportUseCase`, triggered from Session Detail
(`ui/records/SessionDetailViewModel.kt:104`). Read by the Reports card on
`ui/records/SessionDetailScreen.kt` and by the Settings sync counters. Pushed by
`data/supabase/SyncReportUseCase.kt:25-35` — row only, no file.

**Note:** report generation is the one flow that still **requires** a signed-in user
(`GenerateSessionReportUseCase.kt:38-46`), unlike capture and verification, which work
offline. Generating a report while signed out fails.

## See

`supabase/migrations/0008_reports.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/ReportEntity.kt`,
`domain/usecase/records/GenerateSessionReportUseCase.kt`, `schema.ts:346-384`.
