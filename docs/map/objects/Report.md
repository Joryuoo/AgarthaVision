---
type: object
status: verified
verified: 2026-09-30
commit: b64271d2
entity: app/src/main/java/com/agarthavision/data/local/entity/ReportEntity.kt
---

# Report

**One sentence.** A snapshot of one session's — or, since 14zcqntj2uz, one patient's pooled
sessions' — findings at one moment: counts, LPF density per species, and a pointer to a PDF
file. Table and Room entity are both `reports`. PDF-only since 86d4be47c — `csv_file_path` is a
legacy column kept for reports generated before that change (C8: nothing is deleted); no new
report ever writes one.

**Two scopes, one table.** A row is either a **session report** (`session_id` set, `patient_id`
null) or a **patient report** (`patient_id` set, `session_id` null, `session_ids` naming every
session pooled into it) — `reports_scope_check` enforces exactly one shape, never both or
neither (`supabase/migrations/0015_patient_reports.sql`). A patient report's aggregates are
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
Both screens that can open a report's PDF call it on a missing file: Session Detail
(`SessionDetailViewModel.restoreReportFiles`) and, for a patient report, the Reports tab
(`RecordsViewModel.restoreReportFiles`) — a patient report card is openable straight from there,
so it needs the same recovery, not just the session it happens to route into.
A legacy CSV-only report (`pdf_file_path` null, `csv_file_path` set) has nothing to restore —
the CSV format is retired, so `RestoreReportFilesUseCase` fails rather than inventing a PDF, and
leaves `csv_file_path` exactly as it was.

A file the device no longer holds is skipped on upload rather than failing the row: losing the
bytes must not cost the metadata too.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:309-322`, widened by `0015_patient_reports.sql`)

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

**Room** (`ReportEntity`, version 25 — `MIGRATION_24_25`)

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

- **Read by** the author, an admin, and — since `0007_patient_shared_history.sql:177-179` —
  every medtech assigned to the patient. An admin reads the row but not the file, which prints
  the patient's name (`0013_super_admin_reads_deidentified.sql:113`). This reverses `0003_reports_bucket.sql`'s "not
  patient-linked" rule, rows and bucket together; the pull brings colleagues' reports down
  (`ReportRemoteDataSource.kt::fetchReportsForSessions`, beside `::fetchOwnReports`) and
  Session Detail lists them (`ReportDao::observeReportsForSession`).
- **Patient reports are author-only.** A colleague cannot read one, row or file: the read policies
  key on the session, and a patient report has none. Its insert must also pass 0010's
  own-session rule, which `0015_patient_reports.sql` re-expresses over `session_ids`.
- **A patient report pools only its own patient's sessions.** Since
  `0016_patient_report_own_patient_sessions.sql` (14zcqntk68x) every session in `session_ids`
  must be the writer's own **and** have `sessions.patient_id = reports.patient_id`, and the
  array must name at least one session. This is the session report's own-session test, applied
  to each pooled session, plus the patient match a session report gets for free: its patient is
  whatever its session says, where a patient report stores `patient_id` on its own. The same
  file fixes `sessions.patient_id` once written, so the match cannot be met by moving a session
  in and back out. Console org admins read patient reports by `patient_id` (admin/0004), which
  is why that key has to be trustworthy.
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
`ui/records/SessionDetailScreen.kt`, by the cross-patient Reports tab
(`ui/records/RecordsScreen.kt`/`RecordsViewModel.kt`), and by the Settings sync counters. Pushed by
`data/supabase/SyncReportUseCase.kt` — the row, then the PDF into the `reports` bucket
(see [`StorageObject`](StorageObject.md)). PDF is the only format (86d4be47c).

**Note:** report generation requires a **cached local identity** and the session's
[`Patient`](Patient.md) already on this device.
