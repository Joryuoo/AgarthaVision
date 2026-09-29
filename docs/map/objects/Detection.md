---
type: object
status: verified
verified: 2026-09-29
commit: feaa4803
entity: app/src/main/java/com/agarthavision/data/local/entity/DetectionEntity.kt
---

# Detection

**One sentence.** One egg — a single model prediction or manual tag inside a sample, carrying
both what the model said and what the human decided. Table and Room entity are both
`detections`.

## Why this shape

This is the clinical core of the system. A detection is a *pair*: the model's claim
(`class_label`, `confidence`, bounding box) and the human's ruling (`verdict`, `expert_class`),
stored side by side and never collapsed. That is what makes a rejection into training data
instead of a deletion, and it is why there is no `REJECTED` sample state — a rejection is a row
with `verdict = FALSE_POSITIVE`.

Because both halves persist, `detections` doubles as the retraining corpus and as the audit
trail for every human decision. Since `0004_predictions.sql` the model's claim is also kept whole
and unedited in [`Prediction`](Prediction.md), which a prediction-backed detection links to —
so a redraw no longer costs the corpus the box the model actually drew.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:224-245`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `sample_id` | NOT NULL, FK → `samples(id)`, CASCADE |
| `class_label` | NOT NULL — what the model said |
| `confidence` | NOT NULL real, CHECK between 0 and 1 (`0001_init.sql:228`) |
| `bbox_x/y/w/h` | real, **nullable** (`0001_init.sql:232-235`). The box a human stands behind — see *Box provenance* below |
| `verdict` | NOT NULL, default `'CONFIRMED'`, CHECK in (`CONFIRMED`, `FALSE_POSITIVE`, `WRONG_CLASS`, `BOX_INCORRECT`) (`0001_init.sql:237-238`) |
| `expert_class` | nullable — the corrected species. Set when the verdict is `WRONG_CLASS`, or when the verdict is `BOX_INCORRECT` and the medtech corrected the species |
| `stage` | nullable text, added by `0005_verification_stage.sql`. The STH developmental stage the medtech picked; part of an added row's identity, so one species at two stages is two rows |
| `prediction_id` | nullable, FK `(prediction_id, sample_id)` → `predictions(id, sample_id)`, unique where set (`0004_predictions.sql`). Null on an egg the medtech added, and on a pre-0004 row whose provenance could not be established. **Room has no such column**; the push derives it from the ordinal |

**Box provenance** (14zcqnthrx6, 14zcqnthrx8). `VerificationMapper` writes the model's box only
where a human stood behind it: a `BOX_INCORRECT` row the medtech did not redraw is written with
**no box** (`data/local/mapper/VerificationMapper.kt::toDetectionEntity`), where it used to fall back to the
model's rejected geometry and so passed the exhaustiveness rule. Every linked row therefore
answers "who drew this box?" by itself, with no float comparison:

| Row | Box | Who drew it |
|---|---|---|
| `prediction_id` set, `CONFIRMED` / `WRONG_CLASS` / `FALSE_POSITIVE` | set | the model; kept (a false positive's box is the hard-negative region) |
| `prediction_id` set, `BOX_INCORRECT` | set | the medtech — a redraw |
| `prediction_id` set, `BOX_INCORRECT` | null | nobody — rejected, not redrawn |
| `prediction_id` null | set | the medtech — an added egg they located |
| `prediction_id` null | null | nobody — counted, not located |

The reopen path reads the same rule: on `BOX_INCORRECT`, a stored box *is* the redraw
(`OpenVerificationTargetUseCase.kt::toAnswers`). The half-pixel comparison it
replaces is gone.

**Pre-0004 rows.** `0004`'s backfill links legacy prediction-backed rows by recomputing the
derived detection id in SQL, but only on samples with no `BOX_INCORRECT` model row — on those
the stored box is provably the model's. A sample with one keeps every link null: unknown, not
"added". A legacy `BOX_INCORRECT` row may still carry the model's rejected box, and on the
device it reopens as redrawn, which keeps Q2 locked and rewrites the same geometry.

**Why `expert_class` widened.** An egg with a misplaced box is still an egg and still has to be
counted, so the species question is now asked whenever the medtech says the box contains one —
not only when they also say the box is correctly placed. The verdict precedence is unchanged
(`BOX_INCORRECT` still outranks `WRONG_CLASS`); only the column's population rule widened.

**`species_touched` was dropped** (`0006_drop_species_touched.sql`, Room version 22). It was
meant to separate "a human confirmed this" from "a human did not object" on a pre-filled row,
but it recorded taps: a medtech who read a row and agreed submitted it untouched. Everything it
marked for a real reason is carried elsewhere — `WRONG_CLASS` for a picked species, a null
`prediction_id` for an added egg — and box provenance is the table above. Submitting a
pre-filled row is the medtech's confirmation of it.

Index on `verdict` for retraining queries (`0001_init.sql:256`).
`verified_by_user` was **dropped** from Postgres in legacy-dev migration 0002, and from Room at
version 22.

**Room** (`DetectionEntity`)

PK column is `detection_id`. Room carries `stage` too. One thing to know:

- **Case mismatch.** Room stores lowercase (`confirmed`, `false_positive`, …) and Postgres
  stores uppercase. `DetectionVerdict` carries both and maps at the boundary —
  `domain/model/DetectionVerdict.kt`,
  `SampleRemoteDataSource.kt::toInsertRow` (the `DetectionEntity` overload). Queries that hardcode a verdict string must
  pick the right case for the store they are querying.

**Contradiction in the source, unresolved:** the class KDoc on `DetectionEntity` says bounding
boxes are "normalized 0–1" while the KDoc on `bboxX` says "source-image pixels", and the SQL
comment at `0001_init.sql:232` also says normalized. **The pixel reading is correct** — the
server returns `box.xywh`, which is centre-x, centre-y, width, height in pixels
(`inference/server.py::infer`), and the mapper stores those values unchanged
(`VerificationMapper.kt::toDetectionEntity`). Treat the "normalized" comments as stale.

Documented shape: `schema.ts` (`Detection`).

## Connected to

- **Owned by** [`Sample`](Sample.md). Deleting a sample cascades.
- **Aggregated into** [`Report`](Report.md) — counted, never copied.
- **Scoped by** [`Profile`](Profile.md) indirectly: `detections` RLS runs through the parent
  sample's `user_id`, not its own (`supabase/migrations/0001_init.sql:431-455`).
  There is no `user_id` on this table.
- **Looks like but is not** `PredictionDto` (`data/remote/dto/InferenceResponseDto.kt::PredictionDto`).
  That is the wire shape from the inference server: `class`, `confidence`, `x`, `y`, `width`,
  `height`, with no verdict and no id. It becomes a `DetectionEntity` only at verification
  time.

## If you change this

**Hits**
- The verdict computation — the questionnaire-to-verdict function is the single decision point
  (`data/local/mapper/VerificationMapper.kt::computeVerdict`).
- Confirmed egg counts. `getConfirmedEggCountsForSession` resolves species as
  `COALESCE(expert_class, class_label)`, so `expert_class` silently overrides the model's label
  in every count and every report (`DetectionDao.kt::getConfirmedEggCountsForSession`). Counts all detections
  where `d.verdict != 'false_positive'`.
- Both sides of the case mapping, if you add or rename a verdict: the Postgres CHECK
  (`0001_init.sql:238`), the enum (`domain/model/DetectionVerdict.kt`), and every raw query
  string that names a verdict, in four DAOs — the list is in
  [effects](../effects/CONTEXT.md#changing-validation-logic).
- The report generator, which emits `model_class`, `expert_class`, and `verdict`.

**Does not hit**
- The overlay geometry. `FrameWithBoxes` renders the frame's predictions and the answers being
  edited (`ui/verify/FrameBoxes.kt`), not persisted `DetectionEntity` rows — changing the entity's
  box columns does not change what the medtech sees while verifying.
- Sample-level state. `needs_reannotation` is a *frame*-level answer set on the sample
  (`SubmitVerificationUseCase::invoke`, from the view model's `missedEgg`), not stored on any
  detection.

## Surfaces

Written only at verification, by `SubmitVerificationUseCase` — one path for both sources since
86d4ab4tq. An added species writes **one row per egg the model did not box**: the field total
minus the model's boxes of that species, each keyed by species and slot
(`VerificationMapper.kt::addedDetectionIdFor`), with `confidence = 1.0f` and the box the medtech
drew for that slot, or none. A lower total on re-submit prunes the slots no longer written;
prediction-backed rows are never pruned (`SubmitVerificationUseCase::invoke`). A
prediction-backed row judged `BOX_INCORRECT` and not redrawn keeps the model's confidence and
has all four box columns null. Read by Sample Detail, the confirmed egg counts query, and the
report generator. Pushed by `SampleRemoteDataSource.kt::syncSample`.

## See

`supabase/migrations/0001_init.sql:224-245`,
`app/src/main/java/com/agarthavision/data/local/entity/DetectionEntity.kt`,
`data/local/mapper/VerificationMapper.kt`, `schema.ts` (`Detection`).
