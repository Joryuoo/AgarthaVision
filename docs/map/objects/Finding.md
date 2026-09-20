# Finding

**One sentence.** One species-and-stage the medtech logged on a single frame, carrying that
pair's egg count for that low-power field. Table and Room entity are both
`sample_species_findings`; the UI calls them findings.

## Why this shape

One field can hold eggs of more than one species, and that is normal. Dr. Bayron, 2026-09-07:
*"It is normal to see different egg types within the same field."* The app could not write that
down — verification captured exactly one species per frame — so a mixed field was unrecordable.

**The count is per species and stage, never a frame total.** WHO infection-intensity thresholds
are species-specific and differ by more than an order of magnitude: Ascaris heavy is
≥ 50,000 EPG, hookworm heavy is ≥ 4,000. A combined "eggs in this field" number cannot be
graded against either, so it is not a number worth storing.

Two shapes that look easier and are both wrong:

- **A single `samples.lpf_count` integer.** Cannot represent a mixed field, which is the whole
  point.
- **A count column on `detections`.** That table is one row per *detected egg*, so the model's
  per-species count is already `COUNT(*) GROUP BY species`
  (`app/src/main/java/com/agarthavision/data/local/dao/DetectionDao.kt:33-52`). More
  importantly a manual capture has no detection rows to hang a count on — `bbox_*` is nullable
  precisely so a medtech can tag a frame without drawing a box
  (`supabase/migrations/0007_detection_bbox_nullable.sql`). For those frames the findings rows
  are the only count source that exists.

**Zero rows is a meaningful state, not a missing one.** It is how a clean field is recorded,
and it is why `EggSpecies` has no "no egg" member
(`app/src/main/java/com/agarthavision/domain/model/EggSpecies.kt`): a sentinel species would
have to be excluded from every species-keyed aggregation — density, training class balance —
forever.

## Shape

**Postgres** (`supabase/migrations/0012_polyparasitism_findings.sql`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `sample_id` | NOT NULL, FK → `samples(id)`, CASCADE |
| `species` | NOT NULL, CHECK non-blank — canonical class name, or free text the dropdown does not cover |
| `stage` | nullable, CHECK in (`UNFERTILIZED`, `UNEMBRYONATED`, `EMBRYONATED`, `LARVATED`) — **always null, dormant.** See below. |
| `egg_count` | NOT NULL integer, CHECK `> 0` |

**`stage` is dormant and always null.** It was created for 86d4a6jwy, which staging reverted
(`9dcfd5d`) and deprioritised: the four values the CHECK hard-codes were never checked against
literature, and Ascaris could only be tagged `UNFERTILIZED` — the one stage that is never
infective — while the embryonated fertilised egg the consultation cared about was
unselectable. `0012` is applied and frozen under C6, so the column stays; no code path reads or
writes it, `FindingRow` carries no stage, and the row id derives from `(sample_id, species)`
alone. **Reviving the ticket needs a migration widening that CHECK before anything is written
here**, plus a matching change to the id derivation in `VerificationMapper`.

Uniqueness is **two partial indexes**, not one constraint —
`sample_species_findings_unique_staged` where `stage is not null`, and
`..._unique_unstaged` where it is null. With `stage` always null, the unstaged index is the one
in force, and it is exactly the uniqueness the app wants: one row per species per sample. A single `unique nulls not distinct` would have been
tidier but needs PostgreSQL 15; nothing upserts this table (the client deletes a sample's rows
and reinserts), so no PostgREST `on_conflict` target is needed and the weaker form costs
nothing.

RLS is scoped through the parent sample's `user_id`, the same pattern `detections` uses in
`0001_init.sql:119-139`. There is **no `user_id` on this table**. It carries a DELETE policy,
which `detections` deliberately does not — see *Does not hit*.

**Room**
(`app/src/main/java/com/agarthavision/data/local/entity/SampleSpeciesFindingEntity.kt`)

PK column is `finding_id`, derived deterministically from `(sampleId, species, stage)` rather
than random, so re-submitting an edited sample replaces the row it corrects instead of
inserting a duplicate beside it.

**One divergence from Postgres.** SQLite unique indexes always treat NULLs as distinct, so a
duplicate `(sample_id, species, null stage)` slips past Room while Postgres rejects it. The
writer groups by `(species, stage)` before inserting, so it cannot arise in practice; the Room
index is a backstop, not the mechanism.

## Connected to

- **Owned by** [`Sample`](Sample.md) — CASCADE on delete, one sample to many findings.
- **Sits beside** [`Detection`](Detection.md) — same parent, different question. A detection
  is *one egg the model boxed*; a finding is *how many of this species and stage are in this
  field*. A frame can have detections and no findings (a clean field the medtech confirmed
  empty), findings and no detections (a manual capture), or both.
- **Looks like but is not** an aggregate of `detections`. For AI frames the two agree by
  construction, because a confirmed box contributes exactly one egg. For manual frames there
  is nothing to aggregate.

## If you change this

**Hits**

- `domain/usecase/verify/SubmitVerificationUseCase.kt` — the only writer.
- `data/local/dao/SampleSpeciesFindingDao.kt` — `replaceFindingsForSample` is wholesale, so a
  species removed on re-open actually disappears; `getFindingsForSession` feeds reporting.
- `domain/usecase/reports/LpfAggregation.kt` — `aggregateLpfPerSpecies` aggregates findings per
  species across all session fields into `min..max` LPF ranges and qualitative descriptors.
- `domain/usecase/reports/SessionEggCountUseCase.kt` & `domain/usecase/records/GenerateSessionReportUseCase.kt` —
  fetch findings to compute the LPF range for the screen, CSV, and PDF reports.
- `data/supabase/SampleRemoteDataSource.kt` — findings sync delete-then-insert, not upsert, for
  the same reason.
- The verification screen's "add another species" row and its per-species count input.

**Does not hit**

- **Total confirmed egg counts.** The session's total egg count is still summed from
  `DetectionDao.getConfirmedEggCountsForSession`. Findings rows provide the per-species per-field
  distribution (LPF range) only. EPG and the WHO infectivity tier were deleted entirely (PB-16,
  ticket 86d4be3wz).
- **C8.** Deleting a findings row is not a rejection. A count is the medtech's *current
  statement*, like `samples.user_note` — the things C8 protects, the JPEG and the detection
  rows, are still never deleted. That is why this table has a DELETE policy and `detections`
  has none.

## Surfaces

Written by the verification screen on submit, and read back by it when a verified sample is
re-opened for editing. Synced to Supabase with its parent sample. Read by `SessionEggCountUseCase`
and `GenerateSessionReportUseCase` to compute the per-species LPF range via `aggregateLpfPerSpecies`.

## See

`supabase/migrations/0012_polyparasitism_findings.sql` owns the truth.
`app/src/main/java/com/agarthavision/data/local/entity/SampleSpeciesFindingEntity.kt` mirrors it.
Decision records: ticket 86d4ab4tq (findings shape); ticket 86d4be3zb (LPF range definition);
ticket 86d4be3wz (removal of EPG and WHO infectivity tier).

