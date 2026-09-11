# PSGC barangay asset

Generator for `app/src/main/assets/psgc/psgc-barangays-<vintage>.csvgz`, the bundled
reference dataset behind the session barangay picker.

The output is **committed**. A normal build never runs this, and the app never fetches
reference data at runtime — medtechs work in areas with no cellular signal, so the dataset
ships in the APK and is Room-seeded on first run.

```bash
node tools/psgc/build-psgc-asset.mjs
```

## Vintage

| | |
|---|---|
| Vintage | **PSGC 4Q 2023** (31 December 2023) |
| Source | [`altcoder/philippines-psgc-shapefiles`](https://github.com/altcoder/philippines-psgc-shapefiles) `dist/PH_Adm{1,2,3,4}*.csv` |
| Pinned commit | `a44a73091f19e4950dbdc0d7cb77a5e17b101a0a` |
| Code system | Current **10-digit** PSGC, zero-padded |
| Barangays | 42,001 |
| Size | 4.14 MB raw, **340 KB** gzipped |
| SHA-256 | `18c324248b977da67b7aeba6d0a9ef13deb524bd1071fafcbfd08dd32c06308a` |

The SHA-256 and the 42,001 row count are also `PsgcDataset.ASSET_SHA256` and
`PsgcDataset.BARANGAY_COUNT`, asserted by `PsgcAssetPackagingTest` and
`PsgcDatasetIntegrityTest`. Regenerating the asset means updating both constants in the same
change, or the suite fails — which is the point.

**Before bumping the vintage, read the cost table in `docs/map/objects/PsgcBarangay.md`.** It
is not a one-line change: the boundary GeoJSON has to move to the same release, and as of
2026-09-11 no published set exists newer than 4Q 2023.

Why this vintage rather than the newest PSA release: the boundary GeoJSON the Admin Website
renders ([`faeldon/philippines-json-maps`](https://github.com/faeldon/philippines-json-maps)
`2023/`) is generated from these exact shapefiles, so the code list and the boundaries share
one vintage **and** one code system. A mismatch there makes the choropleth silently fail to
join for the affected units — a bug that looks like missing data. Full reasoning, including
why the newer 9-digit PSGC lists are not usable here, is in
[`docs/map/objects/PsgcBarangay.md`](../../docs/map/objects/PsgcBarangay.md).

## Changing the vintage

1. Point `UPSTREAM_SHA` (and `VINTAGE`) in `build-psgc-asset.mjs` at the new release.
2. Run the generator. It aborts rather than emitting a row it cannot fully resolve, so a
   changed upstream shape surfaces as a build failure, not as blank labels in the picker.
3. Update `PsgcDataset.VINTAGE` in
   `app/src/main/java/com/agarthavision/data/local/psgc/PsgcDataset.kt` to match the new
   filename. The seeder re-seeds when that string changes, so devices pick the new data up
   on next launch without a migration.
4. Delete the superseded `.csvgz`, and update the table above plus the object card.
5. Re-point the Admin Website's boundary GeoJSON at the matching release.

## Output format

Gzipped CSV, one header row, RFC 4180 quoting (133 barangay names contain commas):

| Column | Notes |
|---|---|
| `code` | Barangay PSGC, 10 digits zero-padded. Primary key. |
| `name` | Barangay name. |
| `city_muni_code` / `city_muni_name` | Always present. |
| `province_code` / `province_name` | **Empty for 3,083 barangays** — highly urbanised and independent cities occupy the province slot themselves, so PSGC gives them no province. |
| `region_code` / `region_name` | Always present. |
| `search_extra` | Extra search terms, not displayed. Set for Manila's 897 barangays only. |

## Upstream shapes the generator handles

- **Numerically stored codes.** Upstream drops the leading zero on regions 01–09, so
  `0102801001` arrives as `102801001`. Every code is padded back to 10 digits.
- **Manila's sub-municipalities.** Its 14 sub-municipalities (Tondo, Sampaloc, …) are rows in
  the *barangay* file with zeroed parent columns, and they are the direct parents of Manila's
  897 barangays. They are excluded from the barangay list and rolled up to `City of Manila`
  for display, with the sub-municipality name kept in `search_extra` so typing "tondo" still
  finds them. Manila barangay numbers are unique city-wide, so nothing becomes ambiguous.
  Consequence worth knowing: for these 897 rows `city_muni_code` is the chartered city, not
  the code you would get by truncating the barangay code — that yields the sub-municipality.
- **Unnamed sliver polygons.** Two rows in NCR's first district carry geometry but no name.
  They are dropped; the generator reports the count.

## Verifying the committed asset

Nothing in the build ties `app/src/main/assets/psgc/psgc-barangays-4q2023.csvgz` to the
script that produced it, and `tools/` carries no lockfile, so a regenerated asset is only
*probably* byte-identical. The SHA-256 above is what is committed today. Check it before
trusting a locally rebuilt file:

```powershell
Get-FileHash app/src/main/assets/psgc/psgc-barangays-4q2023.csvgz -Algorithm SHA256
```

A different hash is not automatically wrong — a newer Node or zlib can change the gzip
container without changing a single row — but it does mean the asset is no longer the one
this repository was reviewed against, and the row assertions in `PsgcSeederTest` are the
thing to trust over the hash.
