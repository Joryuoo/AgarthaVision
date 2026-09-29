# Report

**One sentence.** A snapshot of one session's — or, since 14zcqntj2uz, one patient's pooled
sessions' — findings at one moment: counts, LPF density per species, and a pointer to a PDF
file. Table and Room entity are both `reports`. PDF-only since 86d4be47c — `csv_file_path` is a
legacy column kept for reports generated before that change (C8: nothing is deleted); no new
report ever writes one.

**Two scopes, one table.** A row is either a **session report** (`session_id` set, `patient_id`
null) or a **patient report** (`patient_id` set, `session_id` null, `session_ids` naming every
session pooled into it) — `reports_scope_check` enforces exactly one shape, never both or
neither (`supabase/migrations/0007_patient_reports.sql`). A patient report's aggregates are
computed by pooling every included session's findings into one `aggregateLpfPerSpecies` call,
not by summing each session's own range; see [report](../processes/report.md#patient-report).

## Why this shape

A report is a *snapshot*, not a view. Generating one twice produces two rows, ordered newest
first, because the underlying samples can change between generations and a clinical record must
be reproducible. That is why the aggregates are denormalised into columns rather than computed
on read.

The split matters: **the numbers and the file sync separately.** The row goes to Postgres; the
PDF goes to the `reports` Storage bucket at `{user_id}/{report_id}.pdf`, a path derived from the
row rather than stored on it.

They have to travel separately because `pdf_file_path` is device-local — a MediaStore id or an
absolute path — so on any device but the one that generated the report, the row names a file
that was never there. That was the "No app available" bug: the row arrived, the document did
not. `SyncReportUseCase` uploads the bytes; `RestoreReportFilesUseCase` pulls them back on first
open and repoints the row at the local copy it writes, so the second open is a plain local read.
A legacy CSV-only report (`pdf_file_path` null, `csv_file_path` set) has nothing to restore —
the CSV format is retired, so `RestoreReportFilesUseCase` fails rather than inventing a PDF, and
leaves `csv_file_path` exactly as it was.

A file the device no longer holds is skipped on upload rather than failing the row: losing the
bytes must not cost the metadata too.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:309-322`, widened by `0007_patient_reports.sql`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `session_id` | FK → `sessions(id)`, CASCADE. NOT NULL only when `report_type = 'session'` |
| `patient_id` | FK → `patients(id)`. NOT NULL only when `report_type = 'patient'` (0007) |
| `session_ids` | `uuid[]`, patient reports only (0007) — the sessions pooled into this snapshot |
| `user_id` | NOT NULL, FK → `profiles(id)` |
| `report_type` | NOT NULL default `'session'`, **CHECK allows `'session'` or `'patient'`** |
| `generated_at` | NOT NULL, default `now()` |
| `total_samples` | integer **NOT NULL, no default** |
| `total_eggs_confirmed` | integer NOT NULL, no default |
| `positive_species` | `text[]` NOT NULL default `'{}'` |
| `lpf_per_species` | `jsonb` NOT NULL default `'{}'` (Replaces Kato-Katz `epg_per_species`) |
| `csv_file_path` | nullable text — **legacy only**; no report generated since 86d4be47c writes one |
| `pdf_file_path` | nullable text — a **device-local** path or URI; other devices restore from Storage |
| `created_at` | NOT NULL, default `now()` |

`reports_scope_check` (0007) enforces that a row is exactly one of session-scoped
(`session_id` set, `patient_id` null) or patient-scoped (`patient_id` set, `session_id` null).
Indexes on `(session_id, generated_at desc)`, `(user_id, generated_at desc)`, and
`(patient_id, generated_at desc) where patient_id is not null`.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/ReportEntity.kt`, version 24 —
`MIGRATION_23_24`)

PK column is `report_id`. Differences:

- `session_id` is nullable in Room too (was NOT NULL through version 23); `patient_id` and
  `session_ids_json` mirror Postgres's `patient_id`/`session_ids`, the latter as a Gson JSON
  array of session ids rather than a native array column.
- `supabase_status` is **Room-only** — no Postgres column exists.
- The collection columns are stored as Gson strings — `positive_species_json`,
  `lpf_per_species_json`, `session_ids_json` — and expanded back into `List<String>`,
  `Map<String, LpfDensity>`, and `List<String>` at the sync boundary.
- `generated_at` and `created_at` are epoch millis locally, ISO strings remotely.
- `total_samples` counts live verified samples (`deleted_at is null`) in scope: the one session
  for a session report, or every included session pooled together for a patient report.

## Connected to

- **Owned by** [`Session`](Session.md) (session reports) or [`Patient`](Patient.md) (patient
  reports, 0007) and [`Profile`](Profile.md).
- **Aggregates** [`Detection`](Detection.md) through [`Sample`](Sample.md) — it stores counts,
  never rows.
- **Aggregates** findings from `sample_species_findings` table into LPF density.
- **Looks like but is not** the PDF file. It lives in `Documents/AgarthaVision/` and is shared
  from `ui/records/ReportSharing.kt`. Since 86d4bzm9g it is also mirrored to the `reports`
  Storage bucket, which is what makes a report readable on a second device.

## If you change this

**Hits**
- `GenerateSessionReportUseCase` — it computes every aggregate, resolves the patient header,
  and writes the PDF before the row (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
- `ReportInsertRow`, or your column never reaches Postgres
  (`data/supabase/ReportRemoteDataSource.kt`).
- The two Gson serialisation points, if you touch either collection column.
- `ReportPdfBuilder` / `ReportPdfRenderer`, if you touch anything the PDF renders — the header
  block, the per-species table, or the units it reports.
- `GeneratePatientReportUseCase` / `PatientReportPdfBuilder` / `AndroidPatientReportPdfRenderer`
  — the patient-scoped equivalents; `buildReportSpeciesRows` is shared between both so the two
  documents never describe the same findings differently.

## Surfaces

Written by `GenerateSessionReportUseCase`, triggered from Session Detail
(`ui/records/SessionDetailViewModel.kt`). Read by the Reports card on
`ui/records/SessionDetailScreen.kt` and by the Settings sync counters. Pushed by
`data/supabase/SyncReportUseCase.kt` — row and PDF bytes both.

**Note:** report generation requires a **cached local identity** and the session's
[`Patient`](Patient.md) already on this device.
