---
type: process
status: verified
verified: 2026-09-30
commit: b64271d2
---

# validate

The human-in-the-loop gate. Nothing counts until this runs.

**Input** — a flagged frame whose inference has settled (`ready` or `manual`), and the medtech's
answers.
**Output** — a `verified` [`Sample`](../objects/Sample.md), its
[`Detection`](../objects/Detection.md) rows and [`Finding`](../objects/Finding.md) rows, and a
sync attempt.

**consumes** [`Sample`](../objects/Sample.md) (status `flagged`, or `verified` on re-edit)
**produces** [`Detection`](../objects/Detection.md), [`Finding`](../objects/Finding.md),
[`Sample`](../objects/Sample.md) (status `verified`)

## Movement

1. **Load the queue.** `ObserveVerificationQueueUseCase` feeds the queue screen; the sheet itself
   reads `FlaggedFrameStore.state`, which rebuilds `FlaggedFrame` objects from Room and re-reads
   each JPEG from disk (`FlaggedFrameStore.kt::toFlaggedFrame`). The unverified queue is flat —
   no tabs by source. Each row shows a Queued / In inference / Ready / Manual badge
   (`ui/verify/InferenceStateBadge.kt`). Tombstoned samples are excluded everywhere.
2. **Wait for the model, or cancel it.** While a frame's inference is queued or running, Model
   Output shows "Frame is in inference" and Add species, remarks and Submit are locked
   (`ui/verify/InferencePending.kt`). "Cancel inference" asks first, then makes it a manual
   capture (`CancelInferenceUseCase`). The result replaces the spinner in place when it lands.
3. **Review each model box — three pre-checked statements.** Q1 "There is a parasitic egg in
   this box", Q2 "The bounding box is correctly placed", Q3 "This egg is <species>"
   (`VerificationSheet.kt::CheckQuestion`). All three are pre-filled from the model, so an
   untouched box is the medtech's confirmation of it. Unchecking Q1 hides the rest. Unchecking Q2
   keeps Q3 visible — a misplaced box still holds a countable egg — and offers **Redraw the box**
   (`ui/verify/DrawMode.kt`). Once a box is redrawn, Q2 is latched unchecked and disabled.
   Unchecking Q3 opens the species dropdown, and a stage dropdown where the species has stages.
4. **Add species, counted per field.** "+ Add species" opens one card per species (and stage):
   the medtech enters the field's **total** eggs of that species, floored at what the model
   already boxed (`VerificationViewModel::onFieldTotalChanged`, `VerificationAnswers.fieldTotal`).
   **Locate eggs** optionally draws a box for each unboxed egg (`drawnBoxes`); it never gates
   submit, so declaring missed eggs never costs more than ignoring them.
5. **Derived Q4.** `needs_reannotation` is not asked. It is true when an added species' total
   exceeds its boxed count (`VerificationUiState.missedEgg`), and null for a manual capture or a
   frame still pending.
6. **Compute the verdict.** One function, first-match-wins: not an egg → `FALSE_POSITIVE`; box
   wrong → `BOX_INCORRECT`; no species → `FALSE_POSITIVE`; species `OTHER` or differs from the
   model's → `WRONG_CLASS`; otherwise `CONFIRMED` (`VerificationMapper.kt::computeVerdict`).
7. **Submit.** `VerificationViewModel.onSubmit` calls `SubmitVerificationUseCase`, which refuses a
   frame whose inference is still pending (`InferencePendingException`, checked against both the
   frame and the row), then in `::invoke`:
   - one UPDATE sets `status = verified`, `verified_at`, `needs_reannotation`, `user_note` and
     `is_edited`, leaving `predictions_json` in place for sync (`SampleDao::updateSampleOnVerify`);
   - writes every detection — **both halves persist**; a rejection is a `FALSE_POSITIVE` row
     (`List<Finding>.toDetectionEntities`, one row per egg: kept model boxes plus one per unboxed
     added egg, keyed by species and slot);
   - prunes added slots a lower total no longer writes, never touching a model box's row;
   - replaces the sample's findings wholesale (`SampleSpeciesFindingDao::replaceFindingsForSample`).
8. **Sync, without waiting.** `BackgroundSamplePush.push` starts `syncSampleUseCase(sampleId)`
   in a process-lifetime scope and then `syncScheduler.requestSync()`, and the save returns
   without awaiting either: the sheet closes once the Room writes land, online or off
   (14zcqntk2pb). Offline, the push fails safely and the worker catches up. See [`sync`](sync.md).
9. **Input is held while it saves.** `isSubmitting` lays an input blocker over the sheet and clears
   its semantics, and the view model refuses paging, back, discard and `setFrame` until it ends
   (`VerificationSheet.kt::SavingInputBlocker`, `VerificationViewModel.kt::requestLeave`).

## A colleague's sample is read-only

A sample another medtech wrote — on a patient both are assigned to (14zcqntjph5) — opens in
Sample Detail with no Edit and no View detection, and a "Recorded by …" note in their place
(`SampleDetailScreen.kt::SampleDetailContent`, `ui/components/ReadOnlyAuthorNote.kt`). Every
editing action lives behind those two — re-verify, add species, redraw, remarks — so none is
reachable. `OpenVerificationTargetUseCase` and `SubmitVerificationUseCase` both refuse the sample
with `ReadOnlyRecordException` as the backstop: the server lets only the author update it, so an
edit would otherwise be written here and refused on push without an error (14zcqntjph6). The rule
is one function, `domain/model/RecordAuthorship.kt::isColleagueRecord`.

## Manual captures take the same path

There is no second use case and no second screen. A manual capture — no model reachable, a
cancel, or five failed passes — is a frame with no model output: no box questions render, and
the medtech adds species with totals directly. Each egg becomes a detection with
`confidence = 1.0f`, `verdict = CONFIRMED`, and the drawn box for its slot or none.

## Hits

- **Every downstream count.** The confirmed egg count filters `verdict != 'false_positive'`
  (`DetectionDao.kt::getConfirmedEggCountsForSession`), and `expert_class` overrides
  `class_label` in the species grouping. Findings feed `aggregateLpfPerSpecies` for LPF ranges.
- **Verdict case.** Room lowercase, Postgres uppercase, mapped at the sync boundary
  (`domain/model/DetectionVerdict.kt`).
- **Offline behaviour.** Verification needs no auth and no network; only the sync step does.
- **The count helpers.** `Finding.kt`'s `boxedCountOf`, `fieldTotalOf` and `unboxedCountOf` are
  what the card, the detection rows, the findings rows and `missedEgg` all agree through.

## Does not hit

- **The model's own claim.** A redraw writes the medtech's box on the detection; the model's box
  stays in `predictions` untouched (see [`Prediction`](../objects/Prediction.md)).
- **The model.** Nothing here retrains or reweights anything. The verdicts accumulate as a
  corpus; consuming it is a separate, out-of-repo activity.
- **The image bytes.** Verification never rewrites the JPEG. Resizing happens later, in
  [`sync`](sync.md).

## Free-text audit (C13)

Every text-entry field reachable from verification and the records screens.

- **Sanctioned free text.** The sample remark (`VerificationSheet.kt::NoteField`), written to
  `samples.user_note` and exported unchanged in the CSV `user_note` column
  (`ReportCsvBuilder.kt::CSV_HEADER`). The session label typed in the New Session sheet
  (`SessionsScreen.kt::NewSessionSheet`) — an administrative specimen identifier, printed in the
  CSV and PDF headers.
- **Dropdown-gated fallbacks** — they name the organism or stage, they do not annotate it. The
  "Other species" field in `SpeciesDropdown`, shown only for `EggSpecies.OTHER`, with offline
  suggestions from earlier entries (`SearchSpeciesSuggestionsUseCase`); its value becomes
  `expert_class`. The "Other stage" field in `StageDropdown`, shown only for `EggStage.OTHER`.
- **Not free text.** The species and stage dropdowns themselves are read-only
  `ExposedDropdownMenu`s; a value comes only from a menu tap. The field total is a number.
- **No editable fields** on `SampleDetailScreen` or `SessionDetailScreen`.

## Unified egg counting rule

All queries counting eggs across sessions, reports, and patients use the single rule
`d.verdict != 'false_positive'`. Clinically, a misclassified egg (`WRONG_CLASS`) or misplaced
box (`BOX_INCORRECT`) is still a confirmed egg — only rejected boxes (`FALSE_POSITIVE`) are
excluded. This ensures that the patient's Sessions list cards, Session Detail, and generated
reports always agree on the exact same total egg count.

`SessionDao::observeSessionsPage` filters `d.verdict != 'false_positive'`.
