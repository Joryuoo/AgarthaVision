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
trail for every human decision.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:59-69`, plus `0002` and `0007`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `sample_id` | NOT NULL, FK → `samples(id)`, CASCADE |
| `class_label` | NOT NULL — what the model said |
| `confidence` | NOT NULL real, CHECK between 0 and 1 (`0001_init.sql:63`) |
| `bbox_x/y/w/h` | real, **nullable since** `supabase/migrations/0007_detection_bbox_nullable.sql:10-14` |
| `verdict` | NOT NULL, default `'CONFIRMED'`, CHECK in (`CONFIRMED`, `FALSE_POSITIVE`, `WRONG_CLASS`, `BOX_INCORRECT`) — `supabase/migrations/0002_verification_fields.sql:30-32` |
| `expert_class` | nullable — the corrected species, set when the verdict is `WRONG_CLASS` (`0002_verification_fields.sql:38-39`) |

Index on `verdict` for retraining queries (`0002_verification_fields.sql:47`).
`verified_by_user` was **dropped** from Postgres by `0002_verification_fields.sql:42-43`.

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/DetectionEntity.kt:28-68`)

PK column is `detection_id`. Two things to know:

- **Case mismatch.** Room stores lowercase (`confirmed`, `false_positive`, …) and Postgres
  stores uppercase. `DetectionVerdict` carries both and maps at the boundary —
  `domain/model/DetectionVerdict.kt:9-27`,
  `data/supabase/SampleRemoteDataSource.kt:79-97`. Queries that hardcode a verdict string must
  pick the right case for the store they are querying.
- `verified_by_user` **still exists in Room** (`DetectionEntity.kt:66-67`) even though Postgres
  dropped it, and is not in the insert row.

**Contradiction in the source, unresolved:** the entity's class KDoc says bounding boxes are
"normalized 0–1" (`DetectionEntity.kt:13`) while the field KDoc directly beneath says
"source-image pixels" (`DetectionEntity.kt:43-46`); `0001_init.sql:64` also says normalized.
**The pixel reading is correct** — the server returns `box.xywh`, which is centre-x, centre-y,
width, height in pixels (`inference/server.py:47-55`), and the mapper stores those values
unchanged (`data/local/mapper/VerificationMapper.kt:36-39`). Treat the "normalized" comments as
stale.

Documented shape: `schema.ts:356-393`.

## Connected to

- **Owned by** [`Sample`](Sample.md). Deleting a sample cascades.
- **Aggregated into** [`Report`](Report.md) — counted, never copied.
- **Scoped by** [`Profile`](Profile.md) indirectly: `detections` RLS runs through the parent
  sample's `user_id`, not its own (`supabase/migrations/0004_fix_profiles_rls_recursion.sql:46-57`).
  There is no `user_id` on this table.
- **Looks like but is not** `PredictionDto` (`data/remote/dto/InferenceResponseDto.kt:17-24`).
  That is the wire shape from the inference server: `class`, `confidence`, `x`, `y`, `width`,
  `height`, with no verdict and no id. It becomes a `DetectionEntity` only at verification
  time.

## If you change this

**Hits**
- The verdict computation — the questionnaire-to-verdict function is the single decision point
  (`data/local/mapper/VerificationMapper.kt:10-17`).
- EPG. `getConfirmedEggCountsForSession` resolves species as
  `COALESCE(expert_class, class_label)`, so `expert_class` silently overrides the model's label
  in every count and every report (`data/local/dao/DetectionDao.kt:33-52`).
- Both sides of the case mapping, if you add or rename a verdict: the Postgres CHECK
  (`0002_verification_fields.sql:32`), the enum (`domain/model/DetectionVerdict.kt:13-16`), and
  every raw query string that names a verdict (`data/local/dao/DetectionDao.kt:43`, `:66`,
  `:87`).
- The CSV, which emits `model_class`, `expert_class`, and `verdict` as separate columns.

**Does not hit**
- The overlay geometry. `FrameWithBoxes` renders from the live
  `PredictionDto` during verification, not from persisted `DetectionEntity` rows — changing the
  entity's box columns does not change what the medtech sees while verifying.
- Sample-level state. `needs_reannotation` is a *frame*-level answer set on the sample
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:38`), not derived from any detection.

## Surfaces

Written only at verification: `SubmitVerificationUseCase.kt:46-49` for AI frames,
`SubmitManualCaptureUseCase.kt:58-72` for manual ones (one row, `confidence = 1.0f`, all four
box columns null). Read by Sample Detail, the EPG aggregate, the Dashboard trend queries, and
the CSV builder. Pushed by `data/supabase/SampleRemoteDataSource.kt:41-43`.

## See

`supabase/migrations/0002_verification_fields.sql`,
`supabase/migrations/0007_detection_bbox_nullable.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/DetectionEntity.kt`,
`data/local/mapper/VerificationMapper.kt`, `schema.ts:356-393`.
