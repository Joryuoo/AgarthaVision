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
**Output** — a persisted [`Report`](../objects/Report.md) row and a PDF file in
`Documents/AgarthaVision/`. PDF-only (86d4be47c) — a report never carries a CSV any more;
`csv_file_path` survives only as a legacy column for reports generated before this change.

**consumes** [`Session`](../objects/Session.md), [`Sample`](../objects/Sample.md),
[`Detection`](../objects/Detection.md), [`Patient`](../objects/Patient.md)
**produces** [`Report`](../objects/Report.md)

## Movement

1. **Require cached identity, ownership, and the session's patient on-device.**
   `GenerateSessionReportUseCase` fails if there is no cached local identity, if the session is
   missing, if the session is not owned by the current user, or if the session's patient has
   never synced to this device (`GenerateSessionReportUseCase::invoke`). A session with no
   verified samples is refused too. Works offline when signed in and the patient is already local.
   On a colleague's session Session Detail offers no generate button and names the author instead
   (`SessionDetailContentState.readOnlyAuthor`, 14zcqntjph6); the reports that session already has
   stay listed and open for the assigned medtech, files included (`RestoreReportFilesUseCase`
   builds the path from the report's own `user_id`).
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
7. **Resolve the patient header.** Name, sex, age (computed at the same instant as the report's
   `generatedAt`), and the full address via `PsgcBarangay.fullAddress` (or the raw PSGC code when
   it no longer resolves — the same display form the patient report and the session patient card
   use) are assembled into a `ReportPatient`
   (`domain/usecase/records/GenerateSessionReportUseCase.kt`).
8. **Build the PDF.** `ReportPdfBuilder` builds the clinical document with a patient block,
   session metadata and per-species LPF ranges; `AndroidReportPdfRenderer` draws it. PDF is the
   only format (86d4be47c).
9. **Write the file** to `Documents/AgarthaVision/`
   (`data/repository/DocumentsReportFileStore.kt`).
10. **Insert the row** with `supabase_status = pending`, then push it inline. `SyncReportUseCase`
    pushes the row and uploads the PDF to the `reports` bucket
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
- **The row's file paths on other devices.** `pdf_file_path` stays device-local (and
  `csv_file_path` is legacy-only); another device reads the PDF from the `reports` bucket through
  `RestoreReportFilesUseCase`, which repoints its own copy of the row at the file it writes.
- **Sample or detection state.** Reporting is read-only over both. Generating a report does not
  mark anything as reported.

## Patient report

14zcqntj2uz. Pools every session `PatientReportScope` resolves to into one PDF-only document,
rather than a session's own smear. Written by `GeneratePatientReportUseCase`
(`domain/usecase/records/GeneratePatientReportUseCase.kt`), reached from the Sessions screen's
"Generate report" sheet (`ui/sessions/PatientReportSheet.kt`).

**Input** — a patient id, a signed-in medtech, and a `PatientReportScope` (a date range and/or
an explicit session-id subset; both default to "every session").
**Output** — a persisted `Report` row with `report_type = 'patient'`, `patient_id` set,
`session_id` null, and `session_ids` naming every session actually included; a PDF in
`Documents/AgarthaVision/`.

1. **Resolve scope.** `resolvePatientReportSessions` (pure, C2) filters the patient's sessions
   by the scope's inclusive date range and/or session-id subset. A session id in the subset that
   does not belong to the patient is a caller error (`IllegalArgumentException`), not a silent
   drop.
2. **Drop empty sessions.** Any resolved session with zero verified samples is excluded
   entirely — not reported as a zero row — so an unexamined smear cannot pad the "sessions
   covered" count on a clinical document. If nothing survives, generation fails with
   `NO_VERIFIED_SAMPLES_MESSAGE`. The "generate report" sheet shows these sessions disabled
   rather than hiding them (`GetPatientReportCandidatesUseCase`), and they never start checked.
   Picking a date range re-derives the checklist's selection to sessions that are both eligible
   and inside it (`SessionsViewModel.onReportDateRangeSelected`), so a checked session can never
   silently fall out of scope. If the medtech still taps Generate with nothing selected — e.g. a
   range with no eligible sessions in it — the sheet shows "No verified samples in the selected
   range." without calling the use case at all, distinct from `NO_VERIFIED_SAMPLES_MESSAGE`,
   which means the patient has no verified samples anywhere.
3. **Pool, don't sum per-session ranges.** Every surviving session's findings are collected into
   one list and passed to a single `aggregateLpfPerSpecies(all, totalFieldsAcrossSessions)` call
   — the headline species table is the pooled range across every included session, not each
   session's range combined after the fact. The per-session breakdown table keeps each smear's
   own range visible alongside the pooled figure.
4. **Resolve the patient header.** Full `displayName`, age computed at the same instant as
   `generatedAt`, sex, and the full address via `PsgcBarangay.fullAddress` (falling back to the
   raw PSGC code when the barangay no longer resolves).
5. **Build and write.** `PatientReportPdfBuilder` assembles the pure-data document;
   `AndroidPatientReportPdfRenderer` paginates it — an unbounded session count means the
   per-session table can span multiple pages, repeating its column headers on each new one.
6. **Insert and sync.** Same shape as a session report: insert with `supabase_status = pending`,
   then push row and PDF bytes to `public.reports` / the `reports` Storage bucket
   (`data/supabase/SyncReportUseCase.kt`). RLS additionally requires the inserting user to hold
   a `patient_users` link to the patient (`supabase/migrations/0015_patient_reports.sql`).

Every generation mints a new row, exactly like a session report — regenerating for the same
patient does not update or replace an earlier one.

An unrecognised `report_type` pulled from Supabase (a report type this build predates) is
skipped during sync rather than stored and later mis-decoded (`FetchRemoteDataUseCase`).

## Storage note

The PDF *does* reach Supabase now — `SyncReportUseCase` uploads it to the `reports` bucket
alongside the metadata row, and `RestoreReportFilesUseCase` pulls it back down on a device that
doesn't have it (see [sync](sync.md)). `pdf_file_path` itself stays a device-local path (a
MediaStore id or absolute path) that no other client can resolve directly — the bucket, not the
row, is what makes the file portable.
