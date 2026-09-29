---
type: process
status: verified
verified: 2026-09-29
commit: feaa4803
---

# report

Turning a session's verified samples into a number a lab can act on, and a file it can hand
over.

**Input** — a session id and a signed-in medtech.
**Output** — a persisted [`Report`](../objects/Report.md) row and a PDF (or CSV) file in
`Documents/AgarthaVision/`.

**consumes** [`Session`](../objects/Session.md), [`Sample`](../objects/Sample.md),
[`Detection`](../objects/Detection.md)
**produces** [`Report`](../objects/Report.md)

## Movement

1. **Require cached identity and ownership.** `GenerateSessionReportUseCase` fails if there is
   no cached local identity, if the session is missing, or if the session is not owned by the current user
   (`GenerateSessionReportUseCase::invoke`). A session with no verified samples is refused too.
   Works offline when signed in.
2. **Gather.** Fetch the session's live verified samples (`deleted_at is null`), their detections,
   and findings (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
3. **Count.** `getConfirmedEggCountsForSession` groups by `COALESCE(expert_class, class_label)`,
   joins to live samples, and excludes flagged samples and false positives
   (`DetectionDao.kt::getConfirmedEggCountsForSession`).
4. **Fetch Findings.** Fetch the per-species egg counts logged by the medtech for each field
   (`data/local/dao/SampleSpeciesFindingDao.kt`).
5. **Compute LPF Density.** Philippine medtechs use Direct Smear, so density is reported per 
   Low Power Field (LPF) via `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`).
   - **Range:** the min and max egg count across fields (empty fields contribute 0). **Not a
     mean** — `LpfDensity` holds only `min` and `max`, and the qualitative descriptor is read
     off `max`.
6. **Normalise species** to canonical names.
7. **Build the document** in the format the medtech picked (`ReportFormat`): a PDF from
   `ReportPdfBuilder` drawn by `AndroidReportPdfRenderer`, or a CSV from `ReportCsvBuilder`, with
   patient header, session metadata and per-species LPF ranges.
8. **Write the file** to `Documents/AgarthaVision/`
   (`data/repository/DocumentsReportFileStore.kt`).
9. **Insert the row** with `supabase_status = pending`, then push it inline. `SyncReportUseCase`
   pushes the row and uploads the file to the `reports` bucket
   (see [`sync`](sync.md) and [`StorageObject`](../objects/StorageObject.md)), and
   `syncScheduler.requestSync()` is the fallback when that push cannot land.

## Live LPF, without a report

`SessionEggCountUseCase` runs the same aggregation without writing anything. It is what 
Session Detail displays before anyone generates a report.

## Hits

- **The counting rule.** Any change to the WHERE clause in
  `DetectionDao.kt::getConfirmedEggCountsForSession` or how findings are stored in `sample_species_findings`
  silently changes counts everywhere.
- **The report format**, which is a clinical document handed to patients and health workers.
- Report generation is idempotent in the sense that it always creates a *new* row; it never
  updates an existing one.

## Does not hit

- **Existing reports or their files.** Regenerating writes a new row and a new file under a new
  report id. Old reports stay on disk under their old names.
- **The row's file paths on other devices.** `pdf_file_path` and `csv_file_path` stay
  device-local; another device reads the file from the `reports` bucket through
  `RestoreReportFilesUseCase`, which repoints its own copy of the row at the file it writes.
- **Sample or detection state.** Reporting is read-only over both. Generating a report does not
  mark anything as reported.
