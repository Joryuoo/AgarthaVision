# Report

**One sentence.** A snapshot of one session's findings at one moment — counts, LPF density 
per species, and a pointer to a CSV or PDF file. Table and Room entity are both `reports`; the product calls the
artefact a "session report".

## Why this shape

A report is a *snapshot*, not a view. Generating one twice produces two rows, ordered newest
first, because the underlying samples can change between generations and a clinical record must
be reproducible. That is why the aggregates are denormalised into columns rather than computed
on read.

The split matters: **the numbers and the file sync separately.** The row goes to Postgres; the
CSV or PDF goes to the `reports` Storage bucket at `{user_id}/{report_id}.{ext}`, a path derived
from the row rather than stored on it.

They have to travel separately because `csv_file_path` and `pdf_file_path` are device-local — a
MediaStore id or an absolute path — so on any device but the one that generated the report, the
row names a file that was never there. That was the "No app available" bug: the row arrived, the
document did not. `SyncReportUseCase` uploads the bytes; `RestoreReportFilesUseCase` pulls them
back on first open and repoints the row at the local copy it writes, so the second open is a
plain local read.

A file the device no longer holds is skipped on upload rather than failing the row: losing the
bytes must not cost the metadata too.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:309-322`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `session_id` | NOT NULL, FK → `sessions(id)`, CASCADE |
| `user_id` | NOT NULL, FK → `profiles(id)` |
| `report_type` | NOT NULL default `'session'`, **CHECK allows only `'session'`** |
| `generated_at` | NOT NULL, default `now()` |
| `total_samples` | integer **NOT NULL, no default** |
| `total_eggs_confirmed` | integer NOT NULL, no default |
| `positive_species` | `text[]` NOT NULL default `'{}'` |
| `lpf_per_species` | `jsonb` NOT NULL default `'{}'` (Replaces Kato-Katz `epg_per_species`) |
| `csv_file_path` | nullable text — a **device-local** path; other devices restore from Storage |
| `pdf_file_path` | nullable text — mirrors `csv_file_path`; device-local path or URI |
| `created_at` | NOT NULL, default `now()` |

Indexes on `(session_id, generated_at desc)` and `(user_id, generated_at desc)`.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/ReportEntity.kt:39-86`)

PK column is `report_id`. Differences:

- `supabase_status` is **Room-only** — no Postgres column exists.
- The two collection columns are stored as Gson strings, `positive_species_json` and
  `lpf_per_species_json`, and expanded back into `List<String>` and
  `Map<String, LpfDensity>` at the sync boundary.
- `generated_at` and `created_at` are epoch millis locally, ISO strings remotely.
- `total_samples` counts live verified samples (`deleted_at is null`).

## Connected to

- **Owned by** [`Session`](Session.md) and [`Profile`](Profile.md).
- **Aggregates** [`Detection`](Detection.md) through [`Sample`](Sample.md) — it stores counts,
  never rows.
- **Aggregates** findings from `sample_species_findings` table into LPF density.
- **Looks like but is not** the CSV or PDF file. Both live in `Documents/AgarthaVision/`
  and are shared from `ui/records/ReportSharing.kt`. Since 86d4bzm9g they are also mirrored to the
  `reports` Storage bucket, which is what makes a report readable on a second device.

## If you change this

**Hits**
- `GenerateSessionReportUseCase` — it computes every aggregate and writes the chosen format's
  single file before the row (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
- `ReportInsertRow`, or your column never reaches Postgres
  (`data/supabase/ReportRemoteDataSource.kt`).
- The two Gson serialisation points, if you touch either collection column.
- `ReportPdfBuilder` / `ReportPdfRenderer`, if you touch anything the PDF renders — the header
  block, the per-species table, or the units it reports.

## Surfaces

Written by `GenerateSessionReportUseCase`, triggered from Session Detail
(`ui/records/SessionDetailViewModel.kt`). Read by the Reports card on
`ui/records/SessionDetailScreen.kt` and by the Settings sync counters. Pushed by
`data/supabase/SyncReportUseCase.kt` — row only, no file.

**Note:** report generation requires a **cached local identity**.
