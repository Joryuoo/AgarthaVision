# validate

The human-in-the-loop gate. Nothing counts until this runs.

**Input** — a flagged frame and the medtech's answers.
**Output** — a `verified` [`Sample`](../objects/Sample.md) with N
[`Detection`](../objects/Detection.md) rows, and a sync attempt.

**consumes** [`Sample`](../objects/Sample.md) (status `flagged`)
**produces** [`Detection`](../objects/Detection.md), [`Sample`](../objects/Sample.md) (status `verified`)

## Movement — AI frames

1. **Load the queue.** `FlaggedFrameStore.state` observes flagged samples for the active
   session and rebuilds `FlaggedFrame` objects, re-reading each JPEG from disk
   (`data/repository/FlaggedFrameStore.kt:58-74`, `:101-119`). Each sheet pages only through
   its own source: the AI sheet cycles `FrameSource.MODEL` frames **that are not marked
   repeat**, the manual sheet cycles `FrameSource.MANUAL`, and `Frame n/N` counts that subset
   rather than the whole queue. Marking the open frame repeat drops it from the cycle: it stays
   on screen so the mark can be undone, but reports no position and both frame buttons dim. The
   host picks a sheet from the frame it opened with and never re-evaluates, so crossing
   between the two would render the wrong questions.
2. **Answer per box.** The sheet collects, per detection: is it an egg, is the box correct,
   which species (`domain/usecase/verify/VerificationAnswers.kt:5-20`). A "no" at any step
   short-circuits the rest — `isComplete` encodes exactly which questions still matter
   (`VerificationAnswers.kt:11-20`).
3. **Answer once per frame.** A frame-level "did the model miss any eggs?" question feeds
   `needs_reannotation` (`ui/verify/VerificationViewModel.kt:217`).
4. **Compute the verdict.** One function, first-match-wins:
   not an egg → `FALSE_POSITIVE`; box wrong → `BOX_INCORRECT`; species is `OTHER` or differs
   from the model's → `WRONG_CLASS`; otherwise `CONFIRMED`
   (`data/local/mapper/VerificationMapper.kt:10-17`). **This is the whole clinical decision
   rule.** Note a null species also yields `FALSE_POSITIVE`
   (`VerificationMapper.kt:13`).
5. **Submit.** `VerificationViewModel.onSubmit` calls the use case and dismisses on success
   (`ui/verify/VerificationViewModel.kt:271-296`).
6. **Update the sample.** One UPDATE sets `status = verified`, `verified_at`,
   `needs_reannotation`, the note, the repeat flag, GPS — and **nulls `predictions_json`**,
   because the cache's job is done (`domain/usecase/verify/SubmitVerificationUseCase.kt:34-44`,
   `data/local/dao/SampleDao.kt:93-122`). GPS is fetched here, not at capture, and returns null
   on denial or timeout rather than throwing
   (`domain/usecase/verify/SubmitVerificationUseCase.kt:31`).
7. **Insert detections.** Predictions are zipped with answers, one row each
   (`domain/usecase/verify/SubmitVerificationUseCase.kt:46-49`). The model's label is
   canonicalised into `class_label`, and the expert's correction goes to `expert_class`
   (`data/local/mapper/VerificationMapper.kt:24-39`). **Both halves persist** — a rejection is a
   row, never a deletion (`../../constraints.md` C8).
8. **Sync immediately.** `syncSampleUseCase(sampleId)` runs inline — see [`sync`](sync.md)
   (`domain/usecase/verify/SubmitVerificationUseCase.kt:51`).

## Movement — manual captures

Same destination, shorter path, and since this pass the manual sheet pages its queue the same
way the AI sheet does. `SubmitManualCaptureUseCase` requires a species, resolves the
label (canonical name, or free text for `OTHER`), and writes **one** detection with
`confidence = 1.0f`, all four box columns null, `verdict = CONFIRMED`, and `expert_class` set to
the same label (`domain/usecase/verify/SubmitManualCaptureUseCase.kt:34-72`). It sets
`needs_reannotation = true` unconditionally
(`domain/usecase/verify/SubmitManualCaptureUseCase.kt:50`), so every manual capture is queued
for offline annotation.

## Hits

- **Every downstream count.** A verdict is not just a label — the EPG aggregate filters on it,
  and `expert_class` overrides `class_label` in the species grouping
  (`data/local/dao/DetectionDao.kt:35`, `:43`).
- **Verdict case.** Room lowercase, Postgres uppercase, mapped at the sync boundary
  (`domain/model/DetectionVerdict.kt:13-16`,
  `data/supabase/SampleRemoteDataSource.kt:80-94`). Adding a verdict means touching the enum,
  the Postgres CHECK (`supabase/migrations/0002_verification_fields.sql:32`), and every raw
  query string that names one.
- **Offline behaviour.** Verification needs no auth and no network
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:27-28`). The inline sync call simply
  fails and marks the row `sync_failed`.
- The queue's visibility gate — see the identity caveat in [`capture`](capture.md).

## Does not hit

- **The bounding box.** `BOX_INCORRECT` records that the box was wrong; nothing corrects it.
  The original coordinates are stored unchanged and in-app box editing does not exist — the fix
  happens in offline annotation tooling.
- **The model.** Nothing here retrains or reweights anything. The verdicts accumulate as a
  corpus; consuming it is a separate, out-of-repo activity.
- **The image bytes.** Verification never rewrites the JPEG. Resizing happens later, in
  [`sync`](sync.md).

## The inconsistency worth knowing

Two aggregate queries disagree about what "confirmed" means. `getConfirmedEggCountsForSession`
— the one behind EPG and every report — counts everything with
`verdict != 'false_positive'`, so `WRONG_CLASS` and `BOX_INCORRECT` boxes are counted as eggs
(`data/local/dao/DetectionDao.kt:43`). The dashboard trend queries use
`verdict = 'confirmed'` (`data/local/dao/DetectionDao.kt:66`, `:87`). Prose that says EPG counts
"CONFIRMED detections" describes the second query, not the one that produces the number.
Clinically the first is defensible — a misclassified egg is still an egg — but the two should
not silently differ.
