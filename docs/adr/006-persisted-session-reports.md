# ADR-006: Persisted Session Reports (Phase 1)

## Status

Proposed. 2026-05-29.

Builds on [ADR-005](005-session-as-smear-manual-capture-and-repeat-flag.md)
(session = fecal smear, EPG ships in Phase 1) and the codebase ERD
([docs/ERD.md](../ERD.md) Module 3.1). Supersedes the ad-hoc CSV export in
the original `ExportSessionUseCase`.

## Context

Through Sprint 2 the medtech could trigger a one-shot CSV export of a
session via a Download icon on `SessionDetailScreen`. The CSV was written
into the device's Downloads folder by `DownloadsSessionReportRepository` and
forgotten — no row, no metadata, no audit, no aggregate stats, no Supabase
sync. Three operational gaps emerged:

1. **No audit trail of report generation.** "When did the medtech share
   this session? Who? What did the numbers look like at that point?" was
   unanswerable. The CSV existed (maybe) in Downloads; nothing tied it back
   to a database row.
2. **Aggregate stats lived only inside the CSV body.** Re-deriving the
   session's positivity-state, EPG-per-species, or total-eggs from the
   filesystem required a parse. The same numbers had to be recomputed every
   time a UI surface needed them.
3. **No cross-device delivery.** A physician or medspecialist couldn't
   request a report; only the medtech who generated it had access, and only
   to the version saved on their device.

The SDD ERD (`docs/ERD.pdf`) anticipated this with a `REPORTS` entity in
two variants — `session` and `administrative`. The administrative variant
is a Phase 2 concern; the session variant is needed now because **the
diagnostic workflow exists to produce a defensible report**, and a CSV
write-and-forget doesn't qualify.

## Decision

Persist session reports as first-class rows in both Room and Supabase.
Row-only sync — the CSV file stays local; the cloud mirror carries
metadata + aggregates only. Multiple reports per session are allowed and
ordered by `generated_at` DESC.

### Schema (Supabase migration `0008_reports.sql`)

```sql
create table public.reports (
    id                   uuid primary key default uuid_generate_v4(),
    session_id           uuid not null references public.sessions(id) on delete cascade,
    user_id              uuid not null references public.profiles(id),
    report_type          text not null default 'session' check (report_type in ('session')),
    generated_at         timestamptz not null default now(),
    total_samples        integer not null,
    total_eggs_confirmed integer not null,
    positive_species     text[] not null default '{}',
    epg_per_species      jsonb not null default '{}'::jsonb,
    csv_file_path        text,
    created_at           timestamptz not null default now()
);
```

RLS policies: owner-select + owner-insert, matching the `sessions` /
`samples` pattern. Indices on `(session_id, generated_at desc)` and
`(user_id, generated_at desc)`. Commented revert ships at the bottom of
the migration file. The `report_type` CHECK constraint allows only
`'session'` today; Phase 2 will extend it to include `'administrative'`.

### Room mirror

`ReportEntity` covers the same columns plus a Room-only `supabase_status`
(`pending` / `synced` / `sync_failed`) following the `samples.status` retry
pattern. `positive_species` and `epg_per_species` are Gson-serialized on
the Room side because Room doesn't natively store collection types. Domain
type is `Map<String, Int>` for EPG; per-key normalization goes through
`EggSpecies.fromClassLabel()`.

### Workflow

1. Medtech taps **Generate Report** in the new Reports card on
   `SessionDetailScreen`. The icon-based Download action is removed.
2. `GenerateSessionReportUseCase` loads samples + detections, computes the
   aggregates (`total_samples`, `total_eggs_confirmed`, `positive_species`,
   `epg_per_species`) using `EpgCalculator.epg`, builds the CSV via
   `ReportCsvBuilder`, writes it to Downloads via `DownloadsReportFileStore`
   (keyed off `reportId` so multiple reports per session don't collide), and
   inserts the row with `supabase_status = 'pending'`.
3. `SyncReportUseCase` is invoked immediately to mirror the row to
   Supabase. On success the row flips to `synced`; on failure to
   `sync_failed`. There is no auto-retry — symmetric with sample sync
   behavior, which also has no retry today.
4. The Reports list shows each row with timestamp + positive-species
   summary + sync badge (Pending / Synced / Sync failed). Tapping a row
   dispatches `Intent.ACTION_SEND` with `text/csv` via FileProvider so the
   medtech can hand the CSV to a physician via the system share sheet.
5. A snackbar confirms generation: "Generated · shared to Downloads".

### CSV format

A comment-prefixed header block followed by per-detection rows. The header
carries the same aggregates persisted on the row (so an offline reader can
reconstruct the metadata without DB access). New columns vs. the old
export:

- **Added:** `model_class`, `expert_class`, `verdict`, `model_version`.
- **Removed:** `storage_path` (internal detail; not useful to a physician).
- **Renamed:** `confidence` → `model_confidence`.

This makes the model-vs-medtech disagreement signal visible in the CSV —
useful for ML retraining feedback and for medical defensibility ("the
medtech accepted prediction X; reclassified prediction Y to Z").

### Excluded from Phase 1

- **Administrative cross-session reports** (SDD §3.2). Schema currently
  rejects `report_type = 'administrative'` via the CHECK constraint. Phase 2
  will relax this and add `date_range_start` / `date_range_end` columns plus
  the corresponding aggregation logic.
- **PDF generation.** CSV is sufficient for Phase 1 sharing.
- **Uploading the CSV file to Supabase Storage.** Only the row is mirrored;
  re-sharing requires the medtech to regenerate locally. This keeps Storage
  policy simple and avoids needing a new bucket.
- **Auto-retry of failed report syncs on connectivity resume.** Mirrors the
  current sample-sync behavior, which also has no retry.
- **`detections.rejection_note`** (a structured "why did the medtech reject
  this prediction?" column). Captured here as a deferral; see the
  VALIDATION_RECORDS discussion in [docs/ERD.md](../ERD.md) §2.

## Consequences

### Positive

- Every report generation produces a durable audit row with who / when /
  what-the-numbers-were. The CSV is now reproducible from the row.
- Aggregate stats live in one place. UI surfaces (and any future PDF /
  email export) can read directly from the row.
- The `reports` table is the foundation for the Phase 2 administrative
  variant — adding a new `report_type` row + cross-session aggregation logic
  is purely additive.
- Multiple reports per session let the medtech regenerate after adding
  more verified samples without losing the prior version.

### Negative

- One more table to maintain in two stores (Room + Supabase). RLS policy
  drift between sessions/samples/reports is a real risk.
- The Room enum `ReportSyncStatus` (`pending` / `synced` / `sync_failed`)
  is the third copy of this lifecycle pattern in the codebase (samples,
  reports, conceptually flagged frames). Worth consolidating into a shared
  `SyncStatus` helper later.
- `FileProvider` had to be added to the manifest. New attack surface (kept
  minimal: `exported="false"`, `grantUriPermissions="true"`, paths scoped
  to Downloads + app files).

### Operational

- The medtech needs to push `0008_reports.sql` to Supabase before the
  sync path can succeed at runtime. Local generation will work either way;
  failed syncs will flip to `sync_failed` and stay there until the migration
  runs.
- Reports never get deleted in Phase 1. If this becomes a UX issue (clutter)
  add a deletion action later — schema doesn't need to change.

## Verification

- ✅ `./gradlew :app:compileDebugKotlin`
- ✅ `./gradlew :app:assembleDebug`
- ✅ `./gradlew :app:testDebugUnitTest` — 76 passing, 0 failing
- ✅ `0008_reports.sql` parses against Supabase (revert block commented
  out; uncomment + run for rollback)
- ⏳ Manual emulator pass to verify the Reports card layout, snackbar
  timing, and share intent — left for the implementer post-merge

## Open / follow-up

- A `detections.rejection_note` column for structured "why" data on
  rejections / reclassifications. Trivial to add; deferred pending UX work.
- Background retry of `sync_failed` reports (and samples) on connectivity
  resume. Plan referenced `NetworkMonitor` but that loop only probes the
  inference container today — neither samples nor reports get retried.
  Future "sync robustness" track.
- A shared `SyncStatus` enum + DAO helper to avoid the per-table copies.
