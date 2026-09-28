# Prediction

**One sentence.** One box the model returned for a verified frame, exactly as the model said
it — class, confidence, geometry — before any human ruled on it. Table `predictions`; on the
device it has no table of its own and lives inside `samples.predictions_json`.

## Why this shape

A [`Detection`](Detection.md) is the human's ruling; this is the claim it rules on. Keeping the
two apart is what lets the corpus hold *both* the model's box and the medtech's correction of
it. Before this table the server kept the model's claim only inside the detection row, and a
redraw overwrote that row's box — so for every redrawn egg the model's geometry was gone, and
"did a human draw this box?" could only be answered by comparing floats against a column the
server never had (14zcqnthrx8).

It is **immutable**: a model's output is a fact about one inference run. The client writes it
insert-if-absent and the table grants no UPDATE or DELETE policy.

It exists **only for verified samples**, structurally: it references `samples`, a sample row is
pushed only once verified, and the predictions are pushed in the same sync call as their sample
(`data/supabase/SampleRemoteDataSource.kt:43`).

**No Room table, deliberately.** A Room table means a version bump, and a bump is destructive on
this project (`core/di/DatabaseModule.kt:83`) — it would wipe every unsynced sample on the
device. `predictions_json` already holds the same list, so rows are built from it on push and
folded back into it on pull (`data/local/mapper/SamplePrediction.kt`).

## Shape

**Postgres** (`supabase/migrations/0004_predictions.sql`)

| Field | Constraint |
|---|---|
| `id` | PK, **client-derived** — `predictionIdFor(sample_id, ordinal)` (`data/local/mapper/VerificationMapper.kt:96`) |
| `sample_id` | NOT NULL, FK → `samples(id)`, CASCADE |
| `ordinal` | NOT NULL, ≥ 0. Index into the frame's prediction list; UNIQUE with `sample_id` |
| `class_label` | NOT NULL — the server's raw label |
| `confidence` | NOT NULL real, CHECK between 0 and 1 |
| `bbox_x/y/w/h` | NOT NULL real — centre-x, centre-y, width, height in source-image pixels, the same space as `detections.bbox_*` |

`UNIQUE (id, sample_id)` exists only as the target of the composite FK from
`detections(prediction_id, sample_id)`, which stops a detection pointing at another sample's
prediction.

**Room:** none. `samples.predictions_json` is the local form — a JSON list of `PredictionDto`,
in ordinal order (`data/inference/PredictionMapper.kt`).

## Connected to

- **Owned by** [`Sample`](Sample.md). CASCADE.
- **Ruled on by** at most one [`Detection`](Detection.md), through `detections.prediction_id`
  (partial unique index). A prediction with no detection does not occur in practice — every
  prediction-backed finding writes one row.
- **Keyed like** the detection it produces: both ids derive from the same ordinal
  (`detectionIdFor` / `predictionIdFor`). That shared ordinal is the join, on the device and in
  the push.
- **Looks like but is not** `PredictionDto` (`data/remote/dto/InferenceResponseDto.kt`) — the
  wire shape, with no id and no ordinal. Nor the domain `Prediction`, which is the same four
  numbers without a place in a frame; `SamplePrediction` is the one with a place.

## If you change this

**Hits**
- The id derivation. `0004_predictions.sql` recomputes `predictionIdFor` and `detectionIdFor`
  in SQL for its backfill. `VerificationMapperTest` pins the detection derivation to ids that
  exist on the live server; change a key string and that fails first.
- The ordinal. A list with a hole would slide every later prediction onto its neighbour's
  detection, which is why a partial set is never folded back into `predictions_json`
  (`toFramePredictionsOrNull`).
- The insert row in `SampleRemoteDataSource` — a column missing from it is silently dropped.

**Does not hit**
- Verdicts, counts, reports. Nothing aggregates predictions; every count reads `detections`.
- The overlay during first verification, which renders the live inference response.

## Surfaces

Written by the sync push, in `SampleRemoteDataSource.syncSample`, after the sample and before
its detections. Read by the sync pull (`FetchRemoteDataUseCase.pullChildRowsFor`), which
restores `predictions_json`, so reopening a sample on a device that did not capture it draws
the model's real boxes. The retraining pipeline reads it beside `detections`.

## See

`supabase/migrations/0004_predictions.sql`, `data/local/mapper/SamplePrediction.kt`,
`data/supabase/SampleRemoteDataSource.kt`, `schema.ts` (`Prediction`).
