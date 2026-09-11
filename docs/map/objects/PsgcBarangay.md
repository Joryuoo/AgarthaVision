# PsgcBarangay

**One sentence.** A barangay from the Philippine Standard Geographic Code — the unit of
analysis for STH surveillance mapping, and the only reference dataset the app bundles.
Product says "the patient's barangay"; the Room table says `psgc_barangays`.

## Why this shape

Every sample already carries a GPS fix and nothing has ever read it
(`supabase/migrations/0001_init.sql:49-51`). That is not an oversight to correct by drawing
pins: the fix is taken at the moment of capture, which is the medtech at the microscope, so it
records **where the smear was read, not where the infection came from**. Plotted, it produces
a map of laboratories. The fix stays as provenance for the audit trail.

STH surveillance also does not map individual cases. Prevalence is reported per administrative
unit and compared against WHO thresholds — above ~20% means continue intervention, below ~10%
means the area can be reassessed. Individual pins are neither the unit of decision nor safe to
publish. So the mapping key is the patient's barangay, which medtechs already have permission
to record, in clinic or on field collection.

**One column, not four.** A barangay code resolves upward to city/municipality, province and
region through the code itself, so `sessions` carries only the barangay code
(`supabase/migrations/0010_session_psgc_barangay.sql:37-39`). The parent names in this table
exist so the picker can be read and searched, not so the session can denormalise them.

**Bundled, not fetched.** Medtechs collect in far-flung areas with no cellular signal — the
premise of the whole offline-first architecture. The dataset ships in the APK and is
Room-seeded on first run, so no part of the picker has a network path to fail on.

**No Supabase table.** This is the one entity here with no remote mirror. The surveillance map
joins `sessions.psgc_barangay_code` against PSGC boundary GeoJSON; a server-side barangay
table would be a second copy of a published classification with nothing to reconcile it
against.

## Shape

**Postgres** — none. The only remote trace is `sessions.psgc_barangay_code`
(`supabase/migrations/0010_session_psgc_barangay.sql:37-39`), nullable, with
`CHECK (psgc_barangay_code ~ '^[0-9]{10}$')`, plus the partial index
`sessions_psgc_barangay_idx` (`:41-43`).

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/PsgcBarangayEntity.kt:25-68`),
schema v9 (`core/database/AgarthaDatabase.kt:44`)

| Field | Constraint |
|---|---|
| `code` | PK. Canonical zero-padded 10-digit PSGC |
| `name` | NOT NULL |
| `city_muni_code` / `city_muni_name` | NOT NULL |
| `province_code` / `province_name` | **Nullable** — null for 3,083 barangays |
| `region_code` / `region_name` | NOT NULL |
| `search_text` | NOT NULL, pre-lowercased search haystack |

No index, deliberately. Every query is a primary-key lookup or the infix `LIKE` in
`data/local/dao/PsgcBarangayDao.kt:50-53`, and a leading-wildcard `LIKE` cannot use one.

### The vintage is pinned — and it is a convention, not a detail

| | |
|---|---|
| Vintage | **PSGC 4Q 2023** (31 December 2023) |
| Source | `altcoder/philippines-psgc-shapefiles`, `dist/PH_Adm{1,2,3,4}*.csv` |
| Pinned commit | `a44a73091f19e4950dbdc0d7cb77a5e17b101a0a` |
| Code system | Current **10-digit** PSGC, zero-padded |
| Barangays | 42,001 |
| Asset | `app/src/main/assets/psgc/psgc-barangays-4q2023.csv.gz`, 340 KB |

Codes are revised as barangays are created, split, merged and renamed. **If the bundled code
list and the boundary GeoJSON come from different releases, the choropleth silently fails to
join for the affected units — a bug that looks like missing data rather than a version
mismatch.** That is the whole reason this vintage is pinned rather than chosen for freshness:
`faeldon/philippines-json-maps` `2023/`, the boundary set the Admin Website renders, is
generated from these exact shapefiles.

**Newer is not automatically better here.** Two things were checked and rejected:

- The boundary lineage stops at 4Q 2023. `altcoder`'s `main` and `master` are byte-identical
  and no later branch or tag exists, so there is no newer release to move *both* halves to.
- The newer code lists in circulation are on the **legacy 9-digit PSGC** (Adams `012801001`,
  Cebu City `072217`) against this dataset's 10-digit form (`0102801001`). That is a different
  code system, not a newer vintage: the two join at essentially nothing, and zero-padding does
  not reconcile them — PSA's published crosswalk would be required.

**Known cost, accepted:** barangays created or renamed after 2023 are absent from the picker
*and* from the map, consistently. A session in such a barangay cannot be recorded at its true
code.

**Changing the vintage** is a dataset swap plus one constant, with no migration: see
`tools/psgc/README.md`. `PsgcSeeder` re-seeds when `PsgcDataset.VINTAGE` changes
(`data/local/psgc/PsgcSeeder.kt:58-61`), and the Admin Website's boundary GeoJSON has to move
to the matching release in the same change.

### Aggregate before display — the privacy rule

Two rules, settled here because the barangay code is patient-locating data collected under a
permission the medtech holds:

1. **The admin map shows per-unit prevalence, never rows.** A barangay with one or two smears
   is effectively an identified patient, so `public.barangay_prevalence()` withholds figures
   below a minimum cell size and returns the row with `suppressed = true`
   (`supabase/migrations/0010_session_psgc_barangay.sql:58-62`, `:110-116`). That lets the map
   distinguish "too few to report" from "no data" without disclosing the count. The threshold
   is deliberately **not** a parameter — a caller must not be able to lower it.
2. **The map stays an admin surveillance view.** The patient-facing report stays clinical,
   with no embedded map. Nothing in `domain/usecase/reports/` reads the barangay code.

Access is an RPC rather than a view because it has to be decided per row-owner, and `GRANT`
cannot tell an admin from a medtech — both hold the `authenticated` role. The function is
`security definer` behind an `is_admin()` guard
(`supabase/migrations/0010_session_psgc_barangay.sql:79-81`).

## Connected to

- **Referenced by** [`Session`](Session.md) — `sessions.psgc_barangay_code` holds a `code`.
  A plain value, **not** a foreign key: this table is on-device only.
- **Aggregated with** [`Sample`](Sample.md) and [`Detection`](Detection.md) by
  `public.barangay_prevalence()`, which counts *sessions* (one smear, one specimen) and treats
  a smear as positive on any detection that is not `FALSE_POSITIVE` — the same rule the app's
  egg count uses (`data/local/dao/DetectionDao.kt:43`).
- **Looks like but is not** the GPS fix on `Sample`. That is capture provenance and is never
  a mapping key.
- **Not** PostGIS. Because the map keys on PSGC, the choropleth is a `GROUP BY`, not a spatial
  query, so the extension is deliberately not enabled.

## If you change this

**Hits**
- `PsgcSeeder`. The seed gate is `count() == 0 || storedVintage != VINTAGE`
  (`data/local/psgc/PsgcSeeder.kt:58-61`). **Both halves are load-bearing** — see "Does not
  hit" below.
- `PsgcCsvParser`, which is strict on purpose: a malformed row aborts rather than seeding a
  barangay with no city (`data/local/psgc/PsgcCsvParser.kt:57-67`).
- The asset generator and its three upstream shapes (`tools/psgc/build-psgc-asset.mjs`):
  numerically-stored codes, Manila's sub-municipalities, and unnamed sliver polygons.
- `search_text`, built in the parser. Case is folded **in Kotlin**, not by SQL `lower()`,
  which is ASCII-only — 439 barangay names contain `ñ`.
- Search matches **each whitespace-separated term** (`data/local/dao/PsgcBarangayDao.kt:50-53`).
  PSA spells the city "City of Cebu", so a contiguous match on "cebu city" — what a medtech
  types — finds nothing.

**Does not hit**
- Supabase, if you are changing this table's shape. There is no remote counterpart; only
  `sessions.psgc_barangay_code` crosses the wire.
- The vintage record, if you only bump the Room version. `fallbackToDestructiveMigration(dropAllTables = true)`
  (`core/di/DatabaseModule.kt:52`) wipes this table, and the recorded vintage would then
  wrongly report the device as seeded. This is why the gate also checks the row count.
- Capture, verification or reports. Nothing in those flows reads a barangay.
- Existing sessions. The column is nullable and nothing backfills it; sessions created before
  the picker keep a null code and are simply absent from the map.

## Surfaces

Written once by `PsgcSeeder`, fired from `AgarthaVisionApp` (`AgarthaVisionApp.kt:29`) — from
the Application rather than a ViewModel, because nothing on screen waits for it and routing a
data-layer concern through a ViewModel would breach C1. Read by the barangay picker in the New
Session sheet (`ui/sessions/SessionsScreen.kt`, via `SearchBarangaysUseCase`). The code it
produces is read remotely only by `public.barangay_prevalence()`.

## See

`supabase/migrations/0010_session_psgc_barangay.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/PsgcBarangayEntity.kt`,
`app/src/main/java/com/agarthavision/data/local/psgc/`, `tools/psgc/README.md`,
`schema.ts` (`PsgcBarangay`).
