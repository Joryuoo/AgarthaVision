# Offline province/town boundary asset

Generators for the offline map boundary geometry the app ships in `app/src/main/assets/geo/`,
in three stages, mirroring `tools/psgc/`'s split between network and stdlib-only steps.

| Stage | Script | Reads | Writes | Network |
|---|---|---|---|---|
| 1 | `build-town-keys.py` | bundled PSGC asset, `geographic-province-overrides.csv` | `town-keys-<vintage>.csv` | no |
| 2 | `build-geo-shapes.sh` | upstream shapefile, `town-keys-<vintage>.csv` | `build/towns-<vintage>.geojson.gz`, `build/provinces-<vintage>.geojson.gz` | **yes** |
| 3 | `build-geo-asset.py` | the two `.geojson.gz` files, `town-keys-<vintage>.csv` | `app/src/main/assets/geo/ph-{provinces,towns}-<vintage>.bin` | no |

**Only the two `.bin` files are packaged.** The GeoJSON stays in the repo as the reviewable
intermediate (readable diffs, inspectable in any GIS tool), and the CSV as the join key that
ties both the GeoJSON and the binary asset back to the exact PSGC codes the app already ships
in `app/src/main/assets/psgc/`. Splitting the stages means stage 3 needs no network and no
third-party dependency, so the shipped binary is provably derived from committed, reviewable
bytes.

```bash
python tools/geo/build-town-keys.py     # stage 1, stdlib only
tools/geo/build-geo-shapes.sh           # stage 2, needs the network + npx (mapshaper)
python tools/geo/build-geo-asset.py     # stage 3, stdlib only
```

## Source and license

- **Boundary geometry**: [`altcoder/philippines-psgc-shapefiles`](https://github.com/altcoder/philippines-psgc-shapefiles),
  commit `a44a7309`, file `dist/PH_Adm3_MuniCities.shp.zip`
  (sha256 `9bb847cfed70656b1ad67ac8648da7867a52df69e3150eff141cd8345b9027c2`). That repository
  itself derives the shapefiles from **OCHA HDX's COD-AB Philippines administrative
  boundaries** dataset, which is PSA/NAMRIA-derived reference geometry licensed
  **CC BY-IGO 3.0** — attribution is required wherever this boundary data is surfaced in the
  product. The processing tool (`altcoder/philippines-psgc-shapefiles` itself, which merely
  reshapes the source COD-AB data and attaches PSGC codes) is **MIT** licensed.
- **Attribution text**, for whoever builds the map UI that surfaces this data (Phase 9/10 of
  this ticket): *"Administrative boundaries: OCHA Philippines Common Operational Datasets
  (COD-AB), CC BY-IGO 3.0."* This phase deliberately does not build an attribution screen —
  see the ticket for scope — but the text needs to land somewhere visible before boundary
  geometry ships in a released build.
- **PSGC join data**: `app/src/main/assets/psgc/psgc-barangays-q2_2026.db`, already bundled —
  see `tools/psgc/README.md`.

## Why the shapefile is a 2023-vintage join, not a direct one

The shapefile's own codes (`adm3_psgc` etc.) are frozen at the 4Q 2023 PSGC vintage — the
source repository stopped publishing after that (see `tools/psgc/README.md` "Why the source
changed" for the full history). Two regions were renumbered since: Negros Island Region (RA
12000) and Sulu leaving BARMM. `build-town-keys.py` computes each current-vintage town's
2023-vintage `src_code` by reversing that renumbering — a leading-prefix substitution on the
five affected province/city-level codes — and that is what `build-geo-shapes.sh`'s join
matches against the shapefile's `adm3_psgc` field. Confirmed against the actual shapefile
attribute table for this build: **1,642/1,642 towns join with zero misses and zero extras.**

## Null-province towns

34 towns carry no `province_code` in the bundled PSGC data — highly urbanised/independent
cities, plus every NCR town, which PSGC gives no province level at all.
`geographic-province-overrides.csv` places each of those 34 into a province-level parent (16
real provinces plus one pseudo-province `1300000000` for the 17 NCR towns, named "National
Capital Region"), so every town has exactly one province-level parent. That yields **85
province-level units** total (84 real PSGC provinces + the NCR pseudo-province). Both the row
count (34) and the unit count (85) are asserted by `build-town-keys.py`.

## Rebuilding for a future PSGC vintage change

1. Confirm the bundled PSGC asset (`tools/psgc/`) has already moved to the new vintage — this
   pipeline's `VINTAGE` constant in all three scripts must match `PsgcDataset.VINTAGE` exactly.
2. Re-run `build-town-keys.py`. If a new region renumbering happened, add its prefix
   substitution to `REVERSE_REGION_REMAP` and re-verify the join count against the shapefile
   (or a newer shapefile, if one becomes available at a matching vintage — check first, this
   whole reverse-remap step exists only because no newer boundary source exists as of this
   writing).
3. If the null-province town set changed, update `geographic-province-overrides.csv` and
   re-run stage 1 — it aborts loudly on any uncovered or stale override row.
4. Re-run `build-geo-shapes.sh`, then `build-geo-asset.py`. Update `GeoDataset.VINTAGE`,
   `PROVINCES_SHA256` and `TOWNS_SHA256` in `data/geo/GeoDataset.kt` with the values the last
   script prints.
5. Delete the superseded `.bin` files, `.geojson.gz` files, and `town-keys-<old>.csv`.

## Toolchain notes (what actually ran, and why it differs from a naive first guess)

- **mapshaper version**: pinned to `0.6.121` (`npm view mapshaper versions --json` to confirm
  it exists before pinning it in `build-geo-shapes.sh`; the npm registry no longer lists any
  `0.5.x`, and `0.7.x` was avoided to stay on a version line with a long track record).
- **Join field**: `adm3_psgc`, confirmed by parsing the `.dbf` header directly (this
  environment has no `ogrinfo`/GDAL) — the shapefile's attribute table is
  `adm1_psgc` (region), `adm2_psgc` (province), `adm3_psgc` (town, the join key), `adm3_en`
  (name), plus `geo_level`/`len_crs`/`area_crs`/`len_km`/`area_km2` (all unused here).
- **Simplification**: `visvalingam weighted percentage=1%` for towns (from the projected,
  cleaned shapefile), then a further `percentage=25%` after dissolving to province level (the
  dissolved outlines are far simpler already, so a much higher percentage still looks clean).
  These percentages were chosen after measuring the final **binary** asset size against the
  200 KB / 900 KB budgets — the intermediate GeoJSON is far larger (uncompressed) than the
  packed binary, because the binary quantizes points to 16 bits and delta-encodes them as
  varints. At 1%/25% the shipped assets measured 93,306 bytes (provinces) and 475,000 bytes
  (towns) — comfortably inside budget with room for a future vintage to grow slightly without
  needing a re-tune.
- **Islands filter**: `-filter-islands min-area=0.5km2` on the dissolved province layer only,
  to drop slivers the dissolve introduces along boundaries it merges away — not applied to the
  town layer, since a small offshore island can be an entire barangay's only landmass.
- **CRS**: the source shapefile ships in `WGS_1984_UTM_Zone_51N` (meters); `-proj wgs84`
  reprojects to lon/lat before anything else runs.
- **Kalayaan (Spratly) claim**: `Kalayaan` (town code `1705321000`, part of Palawan) genuinely
  extends to about 114.28°E, west of the ~116.9°E figure often quoted as the Philippines'
  westernmost point. The binary asset's quantization frame is padded to include it
  (`FRAME_LON_MIN = 113.5` in `build-geo-asset.py`) rather than clipping it as an outlier.
