---
type: object
status: verified
verified: 2026-09-29
commit: feaa4803
entity: app/src/main/java/com/agarthavision/data/local/entity/SampleSpeciesFindingEntity.kt
---

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
  (`DetectionDao.kt::getConfirmedEggCountsForSession`). `bbox_*` is nullable precisely so a
  counted egg needs no box (`supabase/migrations/legacy-dev/0007_detection_bbox_nullable.sql`).
  The findings row is the one place the medtech's *total* for a species is stated outright;
  the detection rows are its per-egg expansion.

**Zero rows is a meaningful state, not a missing one.** It is how a clean field is recorded,
and it is why `EggSpecies` has no "no egg" member
(`app/src/main/java/com/agarthavision/domain/model/EggSpecies.kt`): a sentinel species would
have to be excluded from every species-keyed aggregation — density, training class balance —
forever.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:272-290`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `sample_id` | NOT NULL, FK → `samples(id)`, CASCADE |
| `species` | NOT NULL, CHECK non-blank — canonical class name, or free text the dropdown does not cover |
| `stage` | nullable. `0001_init.sql:283` hard-codes four values; `0005_verification_stage.sql` replaces that CHECK with "null or non-blank", so the app's stage names fit |
| `egg_count` | NOT NULL integer, CHECK `> 0`. The medtech's **field total** for this species and stage — the model's boxes included (`VerificationAnswers.fieldTotal`), or the boxed count when no total was typed |

**`stage` is live.** The stage question returned with `domain/model/EggStage.kt` (corticated or
decorticated, fertilized or unfertilized, or Other with free text). The mapper writes the display
name, and it is part of the row's identity: `VerificationMapper.kt::findingId` derives from
`(sample_id, species, stage)`, so one species at two stages is two rows. The KDoc on
`SampleSpeciesFindingEntity` that still says stage "is always null" is stale; the code wins.

Uniqueness is **two partial indexes**, not one constraint —
`sample_species_findings_unique_staged` where `stage is not null`, and
`..._unique_unstaged` where it is null (`0001_init.sql:295-301`): one row per species and stage
per sample.

RLS is scoped through the parent sample's `user_id`, the same pattern `detections` uses
(`0001_init.sql:457-491`). There is **no `user_id` on this table**. It carries a DELETE policy,
which `detections` deliberately does not — see *Does not hit*.

**Room** (`SampleSpeciesFindingEntity`)

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
- **Aggregated into** [`Report`](Report.md) — findings across all live samples in a session are
  aggregated by `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`) into
  `lpf_per_species` (a min–max density range per species).
- **Looks like but is not** an aggregate of `detections`. The two agree by construction — a
  species' detection rows are its kept model boxes plus one row per unboxed egg up to the total
  (`VerificationMapper.kt::toDetectionEntities`) — but the finding is where the total is stated,
  and the detections are derived from it, not the other way round.

## If you change this

**Hits**

- `domain/usecase/verify/SubmitVerificationUseCase.kt` — writes findings on sample verification.
- `data/local/dao/SampleSpeciesFindingDao.kt` — `replaceFindingsForSample` is wholesale, so a
  species removed on re-open actually disappears.
- `data/supabase/SampleRemoteDataSource.kt` — findings sync delete-then-insert, not upsert, for
  the same reason.
- The verification screen's "+ Add species" card and its field-total input
  (`ui/verify/VerificationFindings.kt`), and `Finding.kt`'s `boxedCountOf` / `fieldTotalOf` /
  `unboxedCountOf` helpers, which every count on this row goes through.
- `aggregateLpfPerSpecies` (`domain/usecase/reports/LpfAggregation.kt`) and `GenerateSessionReportUseCase.kt` —
  reads findings to produce the per-species LPF density range for session detail and the PDF report.

**Does not hit**

- **C8.** Deleting a findings row is not a rejection. A count is the medtech's *current
  statement*, like `samples.user_note` — the things C8 protects, the JPEG and the detection
  rows, are still never deleted. That is why this table has a DELETE policy and `detections`
  has none.

## Surfaces

Written by the verification screen on submit, and read back by it when a verified sample is
re-opened for editing. Read by `aggregateLpfPerSpecies` to compute the low-power-field range
for `SessionDetailViewModel` and `GenerateSessionReportUseCase`. Synced to Supabase with its
parent sample.

## See

`supabase/migrations/0001_init.sql:272-305`,
`app/src/main/java/com/agarthavision/data/local/entity/SampleSpeciesFindingEntity.kt`,
`app/src/main/java/com/agarthavision/domain/usecase/reports/LpfAggregation.kt`,
`schema.ts` (`SampleSpeciesFinding`).
