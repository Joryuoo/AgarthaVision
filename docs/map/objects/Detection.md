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
| `expert_class` | nullable — the corrected species. Set when the verdict is `WRONG_CLASS`, and **since `0012` also when the verdict is `BOX_INCORRECT`** and the medtech corrected the species (`0002_verification_fields.sql:38-39` describes the narrower original rule) |
| `stage` | nullable, CHECK in (`UNFERTILIZED`, `UNEMBRYONATED`, `EMBRYONATED`, `LARVATED`) — optional egg/parasite stage (`supabase/migrations/0010_verification_stage.sql`). Ascaris morphology values `CORTICATED`/`DECORTICATED`/`FERTILIZED` are deliberately excluded for now. |
| `species_touched` | NOT NULL boolean, default `false` (`supabase/migrations/0012_polyparasitism_findings.sql`) — see below |

**Why `expert_class` widened.** An egg with a misplaced box is still an egg and still has to be
counted, so the species question is now asked whenever the medtech says the box contains one —
not only when they also say the box is correctly placed. The verdict precedence is unchanged
(`BOX_INCORRECT` still outranks `WRONG_CLASS`); only the column's population rule widened.
`0002` is applied and is not edited (C6), so this card and `schema.ts` carry the current rule.

**`species_touched` is provenance, not a verdict.** Species fields are pre-filled from the model
output so the medtech edits only what is wrong. That means an untouched submission yields
`CONFIRMED` — "a human did not object" quietly stored as "a human confirmed this" — and since
`detections` doubles as the retraining corpus, the difference matters. The flag is `true` only
when the medtech made a deliberate selection, **including re-picking the pre-filled value**.
Retraining should weight `false` rows lower. It is deliberately not a new `DetectionVerdict`
member: that would mean touching the Supabase CHECK constraint and every query naming a
verdict, for a signal a boolean carries. It is also deliberately **not** a reuse of
`verified_by_user`, which is dead drift (ticket 86d4akgmf).

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

| Field | Constraint |
|---|---|
| `stage` | nullable `TEXT`, added at Room version 10 (`core/database/AgarthaDatabase.kt`). Mirrors the Postgres `stage` column and is mapped via `EggStage.fromValue`/`.value` (`data/local/mapper/DetectionMapper.kt`). |

**Contradiction in the source, unresolved:** the entity's class KDoc says bounding boxes are
"normalized 0–1" (`DetectionEntity.kt:13`) while the field KDoc directly beneath says
"source-image pixels" (`DetectionEntity.kt:43-46`); `0001_init.sql:64` also says normalized.
**The pixel reading is correct** — the server returns `box.xywh`, which is centre-x, centre-y,
width, height in pixels (`inference/server.py:47-55`), and the mapper stores those values
unchanged (`data/local/mapper/VerificationMapper.kt:36-39`). Treat the "normalized" comments as
stale.

Documented shape: `schema.ts:298-335`.

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
- The CSV, which emits `model_class`, `expert_class`, `verdict`, and now `stage` as separate
  columns (`domain/usecase/records/ReportCsvBuilder.kt`).

**Does not hit**
- The overlay geometry. `FrameWithBoxes` renders from the live
  `PredictionDto` during verification, not from persisted `DetectionEntity` rows — changing the
  entity's box columns does not change what the medtech sees while verifying.
- Sample-level state. `needs_reannotation` is a *frame*-level answer set on the sample
  (`domain/usecase/verify/SubmitVerificationUseCase.kt:38`), not derived from any detection.

## Surfaces

Written only at verification, by `SubmitVerificationUseCase` — one path for both sources since
86d4ab4tq. A finding with no prediction (a manual capture, or a species the medtech added to an
AI frame) becomes one row with `confidence = 1.0f` and all four box columns null. Read by Sample Detail, the EPG aggregate, the Dashboard trend queries, and
the CSV builder. Pushed by `data/supabase/SampleRemoteDataSource.kt:41-43`.

## See

`supabase/migrations/0002_verification_fields.sql`,
`supabase/migrations/0007_detection_bbox_nullable.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/DetectionEntity.kt`,
`data/local/mapper/VerificationMapper.kt`, `schema.ts:298-335`.
