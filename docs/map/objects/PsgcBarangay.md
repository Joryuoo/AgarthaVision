# PsgcBarangay

**One sentence.** A barangay from the Philippine Standard Geographic Code — the unit of
analysis for STH surveillance mapping, and the only reference dataset the app bundles.
Product says "the patient's barangay"; the Room table says `psgc_barangays`.

## Why this shape

Every sample historically carried a GPS fix, but nothing ever read it, and it was removed in
the patient-based consolidation (`supabase/migrations/0001_init.sql:205-208`). The fix was taken
at the moment of capture (the medtech at the microscope), recording **where the smear was read,
not where the infection came from**. Plotted, it produced a map of laboratories.

STH surveillance also does not map individual cases. Prevalence is reported per administrative
unit and compared against WHO thresholds — above ~20% means continue intervention, below ~10%
means the area can be reassessed. Individual pins are neither the unit of decision nor safe to
publish. So the mapping key is the patient's barangay, which medtechs already have permission
to record, in clinic or on field collection.

**One column, not four.** A barangay code resolves upward to city/municipality, province and
region through the code itself, so `patients` carries only the barangay code
(`supabase/migrations/0001_init.sql:98-100`). The parent names in this table exist so the picker
can be read and searched, not so patient or session rows denormalise them.

**Bundled, not fetched.** Medtechs collect in far-flung areas with no cellular signal — the
premise of the whole offline-first architecture. The dataset ships in the APK and is
Room-seeded on first run, so no part of the picker has a network path to fail on.

**No Supabase table.** This is the one entity here with no remote mirror. The surveillance map
joins `patients.psgc_barangay_code` against PSGC boundary GeoJSON; a server-side barangay
table would be a second copy of a published classification with nothing to reconcile it
against.

## Shape

**Postgres** — none. The only remote trace is `patients.psgc_barangay_code`
(`supabase/migrations/0001_init.sql:98-100`), NOT NULL, with
`CHECK (psgc_barangay_code ~ '^[0-9]{10}$')`, plus the index
`patients_psgc_barangay_idx` (`:108`).

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/PsgcBarangayEntity.kt:25-68`),
schema v10 (`core/database/AgarthaDatabase.kt:46`)

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
| Vintage | **PSGC 2Q 2026** (released 13 July 2026) |
| Source | `yng-me/psgc`, `R/sysdata.rda` — PSA's releases bundled verbatim |
| Pinned commit | `83f506a7f5c89b7fd2f84fbebb9a0bfd275f0bc0` |
| Code system | Current **10-digit** PSGC, zero-padded |
| Barangays | 42,010 |
| Regions | 18 |
| Asset | `app/src/main/assets/psgc/psgc-barangays-q2_2026.db`, 6.6 MB (≈1.2 MB in the APK) |
| Source | `tools/psgc/psgc-barangays-q2_2026.csvgz`, 342 KB — in the repo, **not** packaged |

Codes are revised as barangays are created, split, merged and renamed, and as whole provinces
are moved between regions. **If the bundled code list and the boundary GeoJSON come from
different releases, the choropleth silently fails to join for the affected units — a bug that
looks like missing data rather than a version mismatch.** That is why the vintage is pinned
and why it is written down here rather than left implicit in a filename.

### Why this moved off 4Q 2023

The dataset was originally built at 4Q 2023 from
`altcoder/philippines-psgc-shapefiles`. That repository stopped publishing after September
2024, and PSA did not stop: by 2Q 2026 **1,763 barangays — 4.2% of the country — carried codes
that the bundled list no longer had.**

| Reorganisation | Effect | Barangays |
|---|---|---|
| **Negros Island Region** created, RA 12000 (2Q 2024) | Negros Occidental, Negros Oriental, Siquijor and the City of Bacolod left Regions VI and VII for region `18` | **1,353** |
| **Sulu leaves BARMM**, following the Supreme Court ruling on the Bangsamoro Organic Law | moved to Region IX | **410** |
| Barangays created since 4Q 2023 | absent from the picker entirely | 9 |
| Barangays merged or retired | still offered by the picker | 1 |

PSA kept the lower digits in both reorganisations and changed only the leading region prefix
— Negros Occidental went `0604500000` → `1804500000` — so this was never a code-system change,
just a stale list.

Why it mattered rather than being cosmetic: those units rolled up to the **wrong region** on a
choropleth that compares prevalence against WHO thresholds, and `patients.psgc_barangay_code`
is written once with nothing to backfill it, so a patient registered in Bacolod or Sulu would have
carried a retired code permanently. `PsgcSeederTest` now pins both cases.

**Two widely-used alternatives were checked and rejected.** `psgc.gitlab.io` and `psgc.cloud`
both serve **9-digit** codes against this dataset's 10-digit form, and both were still
pre-NIR at 17 regions. A 9-digit list is a different code system, not a newer vintage: the
two join at essentially nothing and zero-padding does not reconcile them.

### The boundary half is still outstanding

The Admin Website's choropleth joins on these codes, so its boundary GeoJSON has to come from
the same vintage — and as of 2026-09-11 **no published boundary set exists newer than 4Q
2023**: `faeldon/philippines-json-maps` derives from the retired `altcoder` shapefiles, and
HDX's COD-AB is likewise 2023.

That is a constraint on task `86d43e3vu`, not on this repo, and it is not blocking: **the map
has not been built yet**, so there is no existing choropleth for this move to break. Moving
the code list now is the cheap moment. Whoever builds the map has to source or derive 2026
boundaries; because PSA retained the lower digits, remapping the 2023 set is a leading-prefix
change on the four affected units rather than a re-survey.

**Changing the vintage again** is a dataset swap plus four constants, with no migration: see
`tools/psgc/README.md`. `PsgcSeeder` re-seeds when `PsgcDataset.VINTAGE` changes
(`data/local/psgc/PsgcSeeder.kt:72-75`); `ASSET_SHA256`, `BARANGAY_COUNT` and `REGION_COUNT`
move with it, and `PsgcAssetPackagingTest` and `PsgcDatasetIntegrityTest` fail until they do.

### Aggregate before display — the privacy rule

Two rules, settled here because the barangay code is patient-locating data collected under a
permission the medtech holds:

1. **The admin map shows per-unit prevalence, never rows.** A barangay with one or two smears
   is effectively an identified patient, so `public.barangay_prevalence()` withholds figures
   below a minimum cell size and returns the row with `suppressed = true`
   (`supabase/migrations/0001_init.sql:576-633`). That lets the map
   distinguish "too few to report" from "no data" without disclosing the count. The threshold
   is deliberately **not** a parameter — a caller must not be able to lower it.
2. **The map stays an admin surveillance view.** The patient-facing report stays clinical,
   with no embedded map. Nothing in `domain/usecase/reports/` reads the barangay code.

Access is an RPC rather than a view because it has to be decided per row-owner, and `GRANT`
cannot tell an admin from a medtech — both hold the `authenticated` role. The function is
`security definer` behind an `is_admin()` guard
(`supabase/migrations/0001_init.sql:578-581`).

## Connected to

- **Referenced by** [`Patient`](Patient.md) — `patients.psgc_barangay_code` holds a `code`.
  A plain value, **not** a foreign key: this table is on-device only.
- **Aggregated with** [`Session`](Session.md), [`Sample`](Sample.md) and [`Detection`](Detection.md) by
  `public.barangay_prevalence()`, which counts *sessions* (one smear, one specimen) joined
  through `patients`, treating a smear as positive on any detection that is not `FALSE_POSITIVE`
  — the same rule the app's egg count uses (`data/local/dao/DetectionDao.kt:43`).
- **Not** PostGIS. Because the map keys on PSGC, the choropleth is a `GROUP BY`, not a spatial
  query, so the extension is deliberately not enabled.

## If you change this

**Hits**
- `PsgcSeeder`. The seed gate is `count() == 0 || storedVintage != VINTAGE`
  (`data/local/psgc/PsgcSeeder.kt:72-75`). **Both halves are load-bearing** — see "Does not
  hit" below.
- The bundled SQLite asset. `PsgcSeeder` stages it out of assets and moves the rows with one
  `INSERT ... SELECT` over an `ATTACH`, which replaced gunzipping a CSV and building 42,010
  entities in Kotlin — about a minute on a low-end device. `ATTACH` is issued outside a
  transaction because SQLite rejects it inside one; the replacement still gets its own.
- The asset generators. `tools/psgc/build-psgc-asset.py` fetches upstream and writes the CSV to
  `tools/psgc/`, which is **not packaged**; `tools/psgc/build-psgc-db.py` reads that committed
  file offline and writes the `.db` the APK ships. Only the first needs the network. Upstream
  shapes the first one handles:
  units with no `geographic_level`, Manila's sub-municipalities, and chartered cities with no
  province. Its dependencies are pinned in `tools/psgc/requirements.txt`.
- `search_text`, built by the generator. Case is folded **in Python**, not by SQL `lower()`,
  which is ASCII-only — 438 barangay names in this vintage carry non-ASCII characters. It was
  folded in Kotlin until 86d4brr1f moved generation off the device; the two were proved
  identical over all 42,010 rows before `PsgcCsvParser` was deleted.
- Search matches **each whitespace-separated term** (`data/local/dao/PsgcBarangayDao.kt:50-53`).
  PSA spells the city "City of Cebu", so a contiguous match on "cebu city" — what a medtech
  types — finds nothing.

**Does not hit**
- Supabase, if you are changing this table's shape. There is no remote counterpart; only
  `patients.psgc_barangay_code` crosses the wire.
- The vintage record, if you only bump the Room version. `fallbackToDestructiveMigration(dropAllTables = true)`
  (`core/di/DatabaseModule.kt:54`) wipes this table, and the recorded vintage would then
  wrongly report the device as seeded. This is why the gate also checks the row count.
- Capture, verification or reports. Nothing in those flows reads a barangay.
- Existing patients. The column is NOT NULL with CHECK regex; every patient in the database
  holds a valid 10-digit PSGC code.

## Surfaces

Written once by `PsgcSeeder`, fired from `AgarthaVisionApp` (`AgarthaVisionApp.kt:29`) — from
the Application rather than a ViewModel, because nothing on screen waits for it and routing a
data-layer concern through a ViewModel would breach C1. Read by the barangay picker in the Patient
Form (`ui/patients/PatientFormScreen.kt`, via `PatientFormViewModel.kt`, `BarangayPickerDelegate.kt`,
and `SearchBarangaysUseCase`). The code it produces is stored on `Patient` and read remotely only
by `public.barangay_prevalence()`.

## See

`supabase/migrations/0001_init.sql`,
`app/src/main/java/com/agarthavision/data/local/entity/PsgcBarangayEntity.kt`,
`app/src/main/java/com/agarthavision/data/local/psgc/`, `tools/psgc/README.md`,
`schema.ts` (`PsgcBarangay`), `docs/map/objects/Patient.md`.
