# validate

The human-in-the-loop gate. Nothing counts until this runs.

**Input** — a flagged frame and the medtech's answers.
**Output** — a `verified` [`Sample`](../objects/Sample.md) with N
[`Detection`](../objects/Detection.md) rows, and a sync attempt.

**consumes** [`Sample`](../objects/Sample.md) (status `flagged`)
**produces** [`Detection`](../objects/Detection.md), [`Sample`](../objects/Sample.md) (status `verified`)

## Movement

1. **Load the queue.** `FlaggedFrameStore.state` observes flagged samples for the active
   session and rebuilds `FlaggedFrame` objects, re-reading each JPEG from disk
   (`data/repository/FlaggedFrameStore.kt:59-75`, `:99-117`). The unverified queue is flat —
   there are no tabs dividing sources. When the queue has no rows, the screen indicates the status
   (nothing captured yet, all verified, or session complete). Duplicate frames are handled via soft-delete
   tombstoning (`deleted_at`), which excludes them from counts and queues.
2. **Review per box (pre-filled Q1–Q3, Add Egg).** For each detected egg, the verification sheet
   presents the model's prediction: is it an egg (Q1), is the bounding box placed correctly (Q2),
   and which species is it (Q3). These are pre-filled from model inference so the medtech confirms
   or overrides with minimal taps. Medtechs can also use "Add Egg" to draw/tag missed eggs on the frame.
   When the medtech confirms or re-selects a species, `species_touched` is marked `true` for retraining provenance.
   A "no" to the egg question records `FALSE_POSITIVE`. A "no" to the box question records `BOX_INCORRECT`
   while still asking for species.
3. **Derived / frame-level reannotation.** Missed eggs or misclassified detections feed
   `needs_reannotation` on the sample row (`SubmitVerificationUseCase.kt:54`).
4. **Compute the verdict.** One function, first-match-wins:
   not an egg → `FALSE_POSITIVE`; box wrong → `BOX_INCORRECT`; species is `OTHER` or differs
   from the model's → `WRONG_CLASS`; otherwise `CONFIRMED`
   (`data/local/mapper/VerificationMapper.kt:10-17`). Note a null species also yields `FALSE_POSITIVE`.
5. **Submit.** `VerificationViewModel.onSubmit` calls the use case and navigates on success.
6. **Update the sample and findings.** One UPDATE sets `status = verified`, `verified_at`,
   `needs_reannotation`, `user_note`, and nulls `predictions_json`
   (`domain/usecase/verify/SubmitVerificationUseCase.kt:50-56`). GPS and the legacy repeat flag
   are completely absent. `SampleSpeciesFindingDao.replaceFindingsForSample` updates the per-species
   counts for the low-power field (`:69-72`).
7. **Insert detections.** Predictions are mapped to `DetectionEntity` rows with their verdicts
   (`SubmitVerificationUseCase.kt:61-63`). **Both halves persist** — a rejection is a
   `FALSE_POSITIVE` row, never a deletion (`../../constraints.md` C8).
8. **Sync immediately.** `syncSampleUseCase(sampleId)` runs inline and `syncScheduler.requestSync()`
   enqueues background sync (`SubmitVerificationUseCase.kt:74-78`).

## Manual captures take the same path

There is no second use case and no second screen. A manual capture is a frame with no model
output, so it opens with exactly one finding whose `prediction` is null: the isEgg and
isBoxCorrect questions do not render, and the medtech names a species and a count directly.
`Finding.toDetectionEntity` writes that as **one** detection with `confidence = 1.0f`, all four
box columns null and `verdict = CONFIRMED`.

## Hits

- **Every downstream count.** A verdict is not just a label — the confirmed egg count query
  filters `verdict != 'false_positive'` (`data/local/dao/DetectionDao.kt:43`), and `expert_class`
  overrides `class_label` in the species grouping. Findings feed `aggregateLpfPerSpecies` for LPF ranges.
- **Verdict case.** Room lowercase, Postgres uppercase, mapped at the sync boundary
  (`domain/model/DetectionVerdict.kt:13-16`,
  `data/supabase/SampleRemoteDataSource.kt:79-97`).
- **Offline behaviour.** Verification needs no auth and no network
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:41-42`). The inline sync call simply
  fails safely and enqueues background sync via `SyncScheduler`.

## Does not hit

- **The bounding box.** `BOX_INCORRECT` records that the box was wrong; nothing corrects it.
  The original coordinates are stored unchanged and in-app box editing does not exist — the fix
  happens in offline annotation tooling.
- **The model.** Nothing here retrains or reweights anything. The verdicts accumulate as a
  corpus; consuming it is a separate, out-of-repo activity.
- **The image bytes.** Verification never rewrites the JPEG. Resizing happens later, in
  [`sync`](sync.md).

## Free-text audit (C13)

Every text-entry field reachable from verification/manual capture and the records screens,
audited for whether it is sanctioned free text, a dropdown-gated fallback, or not free text at
all.

- **Sanctioned free text (keep).** The per-detection sample note: `NoteField` in
  `ui/verify/VerificationSheet.kt:348-353` (call site) and `:482-497` (definition), and the
  equivalent `OutlinedTextField` in `ui/verify/ManualSheet.kt:313-329`. Both write to
  `state.userNote` / `samples.user_note` and land unchanged in the CSV `user_note` column
  (`domain/usecase/records/ReportCsvBuilder.kt:82`, `:108`). Also sanctioned: the session label
  entered at session creation (`ui/sessions/SessionsScreen.kt:436-441`), an administrative
  specimen identifier rather than a clinical observation, which flows into the CSV
  `session_label` header (`ReportCsvBuilder.kt:47`) and the PDF header
  (`domain/usecase/records/ReportPdfBuilder.kt:31`).
- **Dropdown-gated fallback (legitimate, but a *species* field, not remarks).** The "Other
  species" text field in `ui/verify/SpeciesDropdown.kt:113-122`, rendered only when
  `EggSpecies.OTHER` is selected. Its value becomes `expert_class`
  (`data/local/mapper/VerificationMapper.kt:24-39`), not a note — it names the organism, it
  doesn't annotate it.
- **Not actually free text.** The species dropdown's own `query` state
  (`ui/verify/SpeciesDropdown.kt:46`, `:61-63`, `:70-93`) is a live filter over the `EggSpecies`
  enum; the committed value only ever comes from a `DropdownMenuItem` tap
  (`SpeciesDropdown.kt:101-109`), never from the typed text itself.
- **No editable/free-text fields** exist on `ui/records/SampleDetailScreen.kt` or
  `ui/records/SessionDetailScreen.kt` — both are read-only presentations of already-committed
  data.

**Governing rule status.** The only clinical free-text field in the app is the dropdown-gated
"Other species" fallback above and medtech notes. The per-species LPF count uses the structured
findings inputs.

## The inconsistency worth knowing

Two aggregate queries disagree about what "confirmed" means. `getConfirmedEggCountsForSession`
— the one behind the report egg count — counts everything with
`verdict != 'false_positive'`, so `WRONG_CLASS` and `BOX_INCORRECT` boxes are counted as eggs
(`data/local/dao/DetectionDao.kt:43`). The dashboard trend queries use
`verdict = 'confirmed'` (`data/local/dao/DetectionDao.kt:66`, `:87`). Prose that says the report counts
"CONFIRMED detections" describes the second query, not the one that produces the number.
Clinically the first is defensible — a misclassified egg is still an egg — but the two should
not silently differ.
