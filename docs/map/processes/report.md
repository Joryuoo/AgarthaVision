---
type: process
status: verified
verified: 2026-09-30
commit: b64271d2
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
   Works offline when signed in. On a colleague's session Session Detail offers no generate
   button and names the author instead (`SessionDetailContentState.readOnlyAuthor`,
   14zcqntjph6); the reports that session already has stay listed and open for the assigned
   medtech, files included (`RestoreReportFilesUseCase` builds the path from the report's own
   `user_id`).
2. **Gather.** Fetch the session's live verified samples (`deleted_at is null`), their detections,
   and findings (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
3. **Count.** `getConfirmedEggCountsForSession` groups by `COALESCE(expert_class, class_label)`,
   joins to live samples, and excludes flagged samples and false positives
   (`DetectionDao.kt::getConfirmedEggCountsForSession`).
4. **Fetch Findings.** Fetch the per-species egg counts logged by the medtech for each field
   (`data/local/dao/SampleSpeciesFindingDao.kt`).
5. **Compute LPF Density.** Philippine medtechs use Direct Smear, so density is reported per 
   Low Power Field (LPF) via `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`).
   - **Range Definition:** Per species, `min(eggCount) .. max(eggCount)` across all fields recorded in the session. A field with none of that species contributes `0`, NOT absence (dropping empty fields would systematically overstate every result). Zero-egg fields exist because zero-detection inference results are persisted (`86d4a6prb`).
   - **Denominator:** Whatever the medtech recorded — no floor, no cap. Ten fields is typical practice, not an app-enforced rule.
   - **Descriptor & Burden Level:** The qualitative descriptor comes from the highest single field (worst field), NOT the mean: `rare` (1–2), `few` (3–5), `moderate` (6–10), `numerous` (>10). A single heavy field must not be averaged away by clean ones (`LpfDensity.kt::LpfDescriptor`). Accompanied by an estimated parasite burden level (`LpfDensity.kt::ParasiteBurdenLevel`).
   - **No EPG / Infectivity Tiers:** There is no WHO or DOH intensity table for Direct Smear, and WHO Kato-Katz EPG thresholds (Ascaris 5k/50k; Trichuris 1k/10k; Hookworm 2k/4k) cannot be rescaled to LPF counts (`4909f78`). Re-adding a clinical intensity tier requires an explicit cutoff table signed off clinically for Direct Smear LPF by name and date.
   - **Wholly negative session:** A session with 0 eggs across all fields is a valid result (`0–0 LPF`) and generates a report stating no parasites found.
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
