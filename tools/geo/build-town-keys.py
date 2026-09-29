#!/usr/bin/env python3
"""Builds the town-to-province join key table used to attribute boundary geometry to PSGC codes.

    python tools/geo/build-town-keys.py

Reads the bundled PSGC barangay database (the same asset `PsgcSeeder` copies into Room,
`app/src/main/assets/psgc/psgc-barangays-<vintage>.db`), collapses it to one row per town
(`city_muni_code`), and writes `tools/geo/town-keys-<vintage>.csv`. That CSV is what
`build-geo-shapes.sh` joins onto the source shapefile's attribute table, and what
`build-geo-asset.py` uses to attach `parentCode`/`province_key`/name/island-group metadata to
each shape.

stdlib only - `sqlite3` and `csv`, no third-party dependency, matching `build-psgc-db.py`.

## Two problems this script solves

**1. Vintage mismatch.** The shapefile this pipeline joins against
(`altcoder/philippines-psgc-shapefiles`) is pinned at the 4Q 2023 PSGC vintage. Two regions
were renumbered since then - see `docs/map/objects/PsgcBarangay.md` "Why the source changed".
This script computes each town's *2023-vintage* `src_code` by reversing that renumbering, so
`build-geo-shapes.sh` can join current (`VINTAGE`) codes onto old-vintage shapefile attributes.
The forward renumbering (2023 -> current) was a leading-prefix change on province- and
city-level codes only; barangay-level digits were untouched, so reversing it means rewriting
just the first five characters of the affected towns' codes.

**2. Missing province codes.** 34 towns in the bundled PSGC data have no `province_code` -
highly urbanised or independent cities, plus every NCR town. A committed override table
(`geographic-province-overrides.csv`) assigns each of those a `province_key`, so every town
ends up with exactly one province-level parent and the boundary data has no orphans.
"""

from __future__ import annotations

import csv
import sqlite3
import sys
from pathlib import Path

VINTAGE = "q2_2026"

PSGC_DB = Path("app/src/main/assets/psgc") / f"psgc-barangays-{VINTAGE}.db"
OVERRIDES_CSV = Path("tools/geo/geographic-province-overrides.csv")
OUT = Path("tools/geo") / f"town-keys-{VINTAGE}.csv"

EXPECTED_TOWN_COUNT = 1_642
EXPECTED_PROVINCE_COUNT = 85

# NCR has no province level in PSGC. Every NCR town is grouped under this pseudo-province so
# the boundary tree still has exactly one root per town. Confirmed unused by any real PSGC
# province code in the bundled q2_2026 asset.
NCR_PSEUDO_PROVINCE_CODE = "1300000000"
NCR_PSEUDO_PROVINCE_NAME = "National Capital Region"

# Reverse of the 2023 -> current PSGC renumbering, keyed by the *current* 5-character
# province/city-level code prefix, value is the 2023-vintage prefix to substitute. Confirmed
# against the actual shapefile attribute table (`PH_Adm3_MuniCities.shp.dbf`) for this build:
# Negros Occidental, Negros Oriental, Siquijor and the City of Bacolod moved from regions
# 06/07 into the new region 18 (Negros Island Region, RA 12000); Sulu moved from the old
# ARMM region 19 into region 09.
REVERSE_REGION_REMAP = {
    "18045": "06045",  # Negros Occidental
    "18046": "07046",  # Negros Oriental
    "18061": "07061",  # Siquijor
    "18302": "06302",  # City of Bacolod (own code, not a province code - it has no province)
    "09066": "19066",  # Sulu
}

# Region-code first-two-digits -> island group. Mirrors `IslandGroup.fromRegionCode` in
# `domain/geo/IslandGroup.kt` - the two must agree, and `GeoDatasetIntegrityTest` exercises the
# Kotlin side against every province this script emits.
LUZON = {"01", "02", "03", "04", "05", "13", "14", "17"}
VISAYAS = {"06", "07", "08", "18"}
MINDANAO = {"09", "10", "11", "12", "16", "19"}


def island_group_of(region_code: str) -> str:
    prefix = region_code[:2]
    if prefix in LUZON:
        return "LUZON"
    if prefix in VISAYAS:
        return "VISAYAS"
    if prefix in MINDANAO:
        return "MINDANAO"
    raise SystemExit(f"unknown region code prefix {prefix!r} (region {region_code})")


def src_code_of(code: str) -> str:
    """Reverses the 2023 -> current renumbering and drops the leading zero the old shapefile
    dataset never carried (region 01-09 codes were stored as 9-digit numbers there)."""
    prefix5 = code[:5]
    remapped = REVERSE_REGION_REMAP.get(prefix5, prefix5) + code[5:]
    return remapped[1:] if remapped[0] == "0" else remapped


def read_overrides() -> "dict[str, str]":
    if not OVERRIDES_CSV.exists():
        raise SystemExit(f"missing {OVERRIDES_CSV.as_posix()}")
    overrides: dict[str, str] = {}
    with OVERRIDES_CSV.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            town_code = row["town_code"].strip()
            province_key = row["province_key"].strip()
            if not town_code or not province_key:
                raise SystemExit(f"bad override row {row}")
            if town_code in overrides:
                raise SystemExit(f"duplicate override for town {town_code}")
            overrides[town_code] = province_key
    return overrides


def main() -> None:
    if not PSGC_DB.exists():
        raise SystemExit(f"missing {PSGC_DB.as_posix()} - build the PSGC asset first")

    overrides = read_overrides()

    connection = sqlite3.connect(PSGC_DB)
    try:
        cursor = connection.execute(
            "SELECT DISTINCT city_muni_code, city_muni_name, province_code, province_name, "
            "region_code FROM psgc_barangays ORDER BY city_muni_code"
        )
        towns = cursor.fetchall()

        province_names: dict[str, str] = {}
        for _, _, p_code, p_name, _ in towns:
            if p_code is not None:
                province_names[p_code] = p_name
        province_names[NCR_PSEUDO_PROVINCE_CODE] = NCR_PSEUDO_PROVINCE_NAME
    finally:
        connection.close()

    used_overrides: set[str] = set()
    rows = []
    for code, name, p_code, p_name, region_code in towns:
        if p_code is None:
            if code not in overrides:
                raise SystemExit(f"town {code} ({name}) has no province_code and no override")
            province_key = overrides[code]
            used_overrides.add(code)
            province_name = province_names.get(province_key)
            if province_name is None:
                raise SystemExit(f"override for {code} points at unknown province {province_key}")
        else:
            province_key = p_code
            province_name = p_name

        rows.append(
            {
                "code": code,
                "src_code": src_code_of(code),
                "name": name,
                "province_key": province_key,
                "province_name": province_name,
                "region_code": region_code,
                "island_group": island_group_of(region_code),
            }
        )

    unused_overrides = set(overrides) - used_overrides
    if unused_overrides:
        raise SystemExit(f"unused override rows for towns not found (or not null-province): {sorted(unused_overrides)}")

    if len(rows) != EXPECTED_TOWN_COUNT:
        raise SystemExit(f"{len(rows)} towns, expected {EXPECTED_TOWN_COUNT}")

    provinces = {row["province_key"] for row in rows}
    if len(provinces) != EXPECTED_PROVINCE_COUNT:
        raise SystemExit(f"{len(provinces)} distinct province_key values, expected {EXPECTED_PROVINCE_COUNT}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["code", "src_code", "name", "province_key", "province_name", "region_code", "island_group"],
        )
        writer.writeheader()
        writer.writerows(rows)

    print(f"{len(rows)} towns, {len(provinces)} provinces")
    print(f"wrote {OUT.as_posix()}")


if __name__ == "__main__":
    sys.exit(main())
