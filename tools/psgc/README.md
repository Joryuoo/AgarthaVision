# PSGC barangay asset

Generators for the bundled reference dataset behind the barangay picker, in two stages.

| Stage | Script | Reads | Writes | Network |
|---|---|---|---|---|
| 1 | `build-psgc-asset.py` | upstream PSA release | `tools/psgc/psgc-barangays-<vintage>.csvgz` | **yes** |
| 2 | `build-psgc-db.py` | that CSV | `app/src/main/assets/psgc/psgc-barangays-<vintage>.db` | no |

**Only the `.db` is packaged.** The CSV stays in the repo as the intermediate; shipping both
would put the same 42,010 rows in the APK twice. Splitting the stages means the shipped
database can be rebuilt anywhere, and is provably derived from the bytes the CSV's own
checksum pins.

Both outputs are **committed**. A normal build never runs either, and the app never fetches
reference data at runtime — medtechs work in areas with no cellular signal, so the dataset
ships in the APK and is copied into Room on first run.

```bash
python -m venv .venv
.venv/Scripts/pip install -r tools/psgc/requirements.txt   # POSIX: .venv/bin/pip
.venv/Scripts/python tools/psgc/build-psgc-asset.py        # stage 1, needs the network
python tools/psgc/build-psgc-db.py                         # stage 2, stdlib only
```

Stage 2 needs no third-party packages — it uses Python's own `sqlite3` — so it runs outside
the venv.

## Vintage

| | |
|---|---|
| Vintage | **PSGC 2Q 2026** (released 13 July 2026) |
| Source | [`yng-me/psgc`](https://github.com/yng-me/psgc) `R/sysdata.rda`, which bundles PSA's releases verbatim |
| Pinned commit | `83f506a7f5c89b7fd2f84fbebb9a0bfd275f0bc0` |
| Code system | Current **10-digit** PSGC, zero-padded |
| Barangays | 42,010 |
| Regions | 18 |
| Size | 4.13 MB raw, **342 KB** gzipped |
| SHA-256 | `1386cf337bfeacb18cee6a487dcb33654eb0259dc366cfe185a326643b2c0e13` |

The SHA-256, the row count and the region count are also `PsgcDataset.ASSET_SHA256`,
`BARANGAY_COUNT` and `REGION_COUNT`, asserted by `PsgcAssetPackagingTest` and
`PsgcDatasetIntegrityTest`. Regenerating the asset means updating those constants in the same
change, or the suite fails — which is the point. `requirements.txt` pins the generator's
dependencies, so a rebuild that changes the hash means the *data* moved, not the toolchain.

### Why the source changed

This asset was previously built from
[`altcoder/philippines-psgc-shapefiles`](https://github.com/altcoder/philippines-psgc-shapefiles)
at 4Q 2023. That repository stopped publishing after September 2024, which stranded the
dataset before two reorganisations that matter for a map keyed on PSGC:

- **Negros Island Region** (RA 12000, 2Q 2024). Negros Occidental, Negros Oriental, Siquijor
  and the City of Bacolod moved out of Regions VI and VII into region `18`. PSA kept the
  lower digits, so this is a leading-prefix change on **1,353 barangays**.
- **Sulu leaves BARMM** (following the Supreme Court ruling on the Bangsamoro Organic Law).
  Its **410 barangays** moved to Region IX.

Under the old vintage all 1,763 of those resolved to the wrong region, and since
`sessions.psgc_barangay_code` is written once with nothing to backfill it, every smear read
in those areas would have carried a retired code permanently.

Two widely-used alternatives were checked and rejected: `psgc.gitlab.io` and `psgc.cloud`
both serve **9-digit** codes and were still pre-NIR at 17 regions.

### The boundary half

The Admin Website's choropleth joins on these codes, so its boundary GeoJSON must come from
the same vintage. As of 2026-09-11 no published boundary set exists newer than 4Q 2023 —
`faeldon/philippines-json-maps` derives from the retired `altcoder` shapefiles and HDX's
COD-AB is also 2023. **That work has not been built yet** (task `86d43e3vu`), which is why
moving the code list now is the cheap moment to do it: there is no existing map to break.
Whoever builds it has to source or derive 2026 boundaries; because PSA retained the lower
digits, remapping the 2023 set is a leading-prefix change on the four affected units.

## Changing the vintage

1. Point `UPSTREAM_SHA`, `VINTAGE` and `RELEASE` in `build-psgc-asset.py` at the new release.
   `RELEASE` must be one of the release names the upstream bundles; the script lists them and
   aborts if the name is unknown.
2. Run both stages, in order. Each aborts rather than emitting a row it cannot fully resolve,
   so a changed upstream shape surfaces as a failure, not as blank labels in the picker.
   Stage 2 also re-checks the barangay and region counts before it writes.
3. Update `PsgcDataset.VINTAGE`, `ASSET_SHA256`, `BARANGAY_COUNT` and `REGION_COUNT` — the
   generator prints the hash and the counts. The seeder re-seeds when `VINTAGE` changes, so
   devices pick the new data up on next launch without a migration.
4. Delete the superseded `.csvgz` **and** `.db`, and update the table above plus the object
   card. `ASSET_SHA256` is the hash of the `.db`, which is what ships.
5. Re-point the Admin Website's boundary GeoJSON at the matching release.

## Output format

**Stage 1** writes gzipped CSV, one header row, RFC 4180 quoting (133 barangay names contain
commas). **Stage 2** writes one SQLite table, `psgc_barangays`, whose columns mirror
`PsgcBarangayEntity` and carry the same values, except that an empty `province_code` or
`province_name` becomes `NULL` and `search_extra` is folded into `search_text` rather than
stored. The shared columns:

| Column | Notes |
|---|---|
| `code` | Barangay PSGC, 10 digits zero-padded. Primary key. |
| `name` | Barangay name. |
| `city_muni_code` / `city_muni_name` | Always present. |
| `province_code` / `province_name` | **Empty for 3,025 barangays** — highly urbanised and independent cities occupy the province slot themselves, so PSGC gives them no province. |
| `region_code` / `region_name` | Always present. |
| `search_extra` | Extra search terms, not displayed. Set for Manila's 897 barangays only. CSV only. |
| `search_text` | `.db` only. Barangay, city/municipality, province and `search_extra`, space-joined, blanks dropped, lowercased. Folded in Python, not by SQL `lower()`, which is ASCII-only — 438 names carry characters outside it. |

## Upstream shapes the generator handles

- **Units with no `geographic_level`.** The Special Geographic Area in BARMM
  (`1999900000`) and City of Isabela (`0990100000`) are unlabelled rows that nonetheless sit
  in the province slot. The generator resolves parents by **code shape** rather than by that
  label, so their barangays are not dropped.
- **Manila's sub-municipalities.** Its 14 sub-municipalities (Tondo, Sampaloc, …) are the
  direct parents of Manila's 897 barangays. They are excluded from the barangay list and
  rolled up to `City of Manila` for display, with the sub-municipality name kept in
  `search_extra` so typing "tondo" still finds them. Manila barangay numbers are unique
  city-wide, so nothing becomes ambiguous. Consequence worth knowing: for these 897 rows
  `city_muni_code` is the chartered city, not the code you would get by truncating the
  barangay code — that yields the sub-municipality. `PsgcDatasetIntegrityTest` pins this.
- **Chartered cities have no province.** A highly urbanised or independent city occupies the
  province slot itself, so its province code resolves to the city. Those 3,025 rows carry an
  empty province rather than a repeated city name.
- **Non-ASCII names.** 438 barangay names carry characters outside ASCII (`ñ` and friends),
  which is why the search haystack is lowercased in Kotlin rather than by SQL `lower()`.

## Verifying the committed asset

Nothing in the build reruns the generators, so the SHA-256 above is what ties
`app/src/main/assets/psgc/psgc-barangays-q2_2026.db` to the scripts that produced it.
`PsgcAssetPackagingTest` asserts it on every test run, along with the file being a SQLite
database and carrying no `room_master_table` — that absence is what keeps the asset
independent of Room's `identityHash`, so a schema version bump never means regenerating it.
To check by hand:

```powershell
Get-FileHash app/src/main/assets/psgc/psgc-barangays-q2_2026.db -Algorithm SHA256
```

`build-psgc-db.py` pins the SQLite page size and inserts in key order, so a rebuild from the
same CSV is byte-identical rather than merely equivalent.

Because `requirements.txt` pins the generator's dependencies and the gzip header is written
with `mtime=0`, a rebuild from the same pinned upstream should be byte-identical. A changed
hash therefore means the data moved — treat it as a finding, not as noise, and reconcile it
against the row assertions in `PsgcSeederTest` and `PsgcDatasetIntegrityTest`.
