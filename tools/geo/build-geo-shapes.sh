#!/usr/bin/env bash
# Downloads the source shapefile, verifies it, and simplifies it into the two committed
# intermediate GeoJSON files `build-geo-asset.py` reads.
#
#   tools/geo/build-geo-shapes.sh
#
# Needs a network connection and `npx` (Node/npm). Nothing here is packaged in the APK -
# `build-geo-asset.py` (stdlib only, no network) is what produces the shipped assets, from the
# committed output of this script.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

VINTAGE="q2_2026"
CACHE_DIR=".cache"
BUILD_DIR="build"
ZIP_URL="https://media.githubusercontent.com/media/altcoder/philippines-psgc-shapefiles/a44a7309/dist/PH_Adm3_MuniCities.shp.zip"
ZIP_SHA256="9bb847cfed70656b1ad67ac8648da7867a52df69e3150eff141cd8345b9027c2"
ZIP_PATH="$CACHE_DIR/PH_Adm3_MuniCities.shp.zip"
SHP_PATH="$CACHE_DIR/PH_Adm3_MuniCities.shp.shp"
TOWN_KEYS_CSV="town-keys-$VINTAGE.csv"

# Pinned exact version, confirmed to exist on npm at the time this script was written
# (`npm view mapshaper versions --json`). Pin a new one deliberately if you bump this -
# don't float on a range.
MAPSHAPER="mapshaper@0.6.121"

mkdir -p "$CACHE_DIR" "$BUILD_DIR"

if [ ! -f "$TOWN_KEYS_CSV" ]; then
  echo "missing $TOWN_KEYS_CSV - run build-town-keys.py first" >&2
  exit 1
fi

if [ ! -f "$ZIP_PATH" ]; then
  echo "downloading $ZIP_URL"
  curl -sSL -o "$ZIP_PATH" "$ZIP_URL"
fi

echo "verifying checksum"
ACTUAL_SHA256="$(sha256sum "$ZIP_PATH" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$ZIP_SHA256" ]; then
  echo "checksum mismatch: expected $ZIP_SHA256, got $ACTUAL_SHA256" >&2
  echo "the upstream file has changed - do not proceed without re-verifying provenance" >&2
  exit 1
fi

if [ ! -f "$SHP_PATH" ]; then
  echo "unzipping"
  unzip -o -q "$ZIP_PATH" -d "$CACHE_DIR"
fi

# The shapefile's own name has a doubled `.shp` (`PH_Adm3_MuniCities.shp.shp`, alongside an
# identical `Municities.shp.shp`) - that is how the upstream release ships it, not a mistake
# here. Inspected with a hand-rolled .dbf header parser (stdlib `struct`, no `ogrinfo`/GDAL
# available in this environment) rather than `mapshaper -i ... info`, which needed the
# reprojection step first. The real join field is `adm3_psgc` (not `adm2_psgc` and not the
# plan's original guess), confirmed against `town-keys-<vintage>.csv`'s `src_code` column with
# an exact 1,642/1,642 match - see `build-town-keys.py`'s module docstring for the renumbering
# this join relies on.
#
# Attribute fields actually present: adm1_psgc (region), adm2_psgc (province), adm3_psgc
# (town - the join key), adm3_en (name), geo_level, len_crs/area_crs/len_km/area_km2 (unused).
# All four PSGC fields are plain numeric (DBF type N) with no leading zero - 2023-vintage
# region 1-9 codes are stored as 9 digits, region 10+ as 10. `build-town-keys.py`'s
# `src_code_of` produces exactly that shape so the join in step 2 below matches 1:1.

echo "1/2: towns"
npx -y -p "$MAPSHAPER" mapshaper \
  -i "$SHP_PATH" encoding=latin1 -proj wgs84 \
  -join "$TOWN_KEYS_CSV" keys=adm3_psgc,src_code fields=code,name,province_key,region_code \
  -filter-fields code,name,province_key,region_code \
  -simplify visvalingam weighted percentage=1% keep-shapes \
  -clean \
  -o "$BUILD_DIR/towns-$VINTAGE.geojson" precision=0.0001 format=geojson force

echo "2/2: provinces (dissolved from towns, simplified further)"
npx -y -p "$MAPSHAPER" mapshaper \
  -i "$SHP_PATH" encoding=latin1 -proj wgs84 \
  -join "$TOWN_KEYS_CSV" keys=adm3_psgc,src_code fields=code,name,province_key,province_name,region_code \
  -filter-fields code,name,province_key,province_name,region_code \
  -simplify visvalingam weighted percentage=1% keep-shapes \
  -clean \
  -dissolve province_key copy-fields=province_name,region_code \
  -simplify visvalingam weighted percentage=25% keep-shapes \
  -filter-islands min-area=0.5km2 -clean \
  -o "$BUILD_DIR/provinces-$VINTAGE.geojson" precision=0.0001 format=geojson force

echo "gzipping committed intermediates"
gzip -kf "$BUILD_DIR/towns-$VINTAGE.geojson" "$BUILD_DIR/provinces-$VINTAGE.geojson"

echo "done - review then commit build/*.geojson.gz, run build-geo-asset.py next"
ls -la "$BUILD_DIR"
