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
   (`domain/usecase/records/GenerateSessionReportUseCase.kt:50-59`). Works offline when signed
   in.
2. **Gather.** Fetch the session's live verified samples (`deleted_at is null`), their detections,
   and findings (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
3. **Count.** `getConfirmedEggCountsForSession` groups by `COALESCE(expert_class, class_label)`,
   joins to live samples, and excludes flagged samples and false positives
   (`data/local/dao/DetectionDao.kt:33-52`).
4. **Fetch Findings.** Fetch the per-species egg counts logged by the medtech for each field
   (`data/local/dao/SampleSpeciesFindingDao.kt`).
5. **Compute LPF Density.** Philippine medtechs use Direct Smear, so density is reported per 
   Low Power Field (LPF) via `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`).
   - **Range:** the min and max egg count across fields (empty fields contribute 0).
   - **Mean:** average eggs per field across examined fields.
6. **Normalise species** to canonical names.
7. **Build Document.** Build the clinical PDF (or CSV) report with patient header, session metadata,
   and per-species LPF density ranges.
8. **Write the file** to `Documents/AgarthaVision/`
   (`data/repository/DocumentsReportFileStore.kt`).
9. **Insert the row** with `supabase_status = pending`, then push it —
   row only, no file upload (`domain/usecase/records/GenerateSessionReportUseCase.kt`).

## Live LPF, without a report

`SessionEggCountUseCase` runs the same aggregation without writing anything. It is what 
Session Detail displays before anyone generates a report.

## Hits

- **The counting rule.** Any change to the WHERE clause in
  `data/local/dao/DetectionDao.kt:33-52` or how findings are stored in `sample_species_findings`
  silently changes counts everywhere.
- **The report format**, which is a clinical document handed to patients and health workers.
- Report generation is idempotent in the sense that it always creates a *new* row; it never
  updates an existing one.

## Does not hit

- **Existing reports or their files.** Regenerating writes a new row and a new file under a new
  report id. Old reports stay on disk under their old names.
- **Storage.** The PDF/CSV never reaches Supabase — only the metadata row does. `pdf_file_path`
  and `csv_file_path` are device-local paths that no other client can resolve.
- **Sample or detection state.** Reporting is read-only over both. Generating a report does not
  mark anything as reported.
