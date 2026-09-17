#!/usr/bin/env python3
"""Builds the SQLite asset the app ships, from the CSV `build-psgc-asset.py` produces.

    python tools/psgc/build-psgc-db.py

Two stages, deliberately split. `build-psgc-asset.py` reaches upstream and normalises a PSA
release into `tools/psgc/psgc-barangays-<vintage>.csvgz`; this reads that committed file and
writes `app/src/main/assets/psgc/psgc-barangays-<vintage>.db`. Only the first stage needs the
network, so the shipped database can be rebuilt anywhere, and it is provably derived from the
exact bytes the CSV's own checksum pins.

Only the `.db` is packaged. The CSV stays in the repo as the intermediate — shipping both
would put the same 42,010 rows in the APK twice.

## Why a prebuilt database

The app used to gunzip the CSV, parse it in Kotlin and insert 42,010 rows on first launch,
which cost about a minute on a low-end device and made the bottom-bar tabs feel dead while it
ran. `PsgcSeeder` now copies this file out of assets and moves the rows with one
`INSERT ... SELECT`.

The table below mirrors `PsgcBarangayEntity`. It is not a Room database and carries no
`room_master_table`: the rows are copied into the real schema, never opened as one. That is
what keeps this asset independent of Room's `identityHash`, so bumping the database version
never means regenerating it.

`search_text` is built here rather than in Kotlin now that nothing parses the CSV on device.
The rule is the one `PsgcCsvParser.searchTextOf` used: barangay, city/municipality, province
and Manila's sub-municipality, space-joined, blanks dropped, lowercased. Lowercasing happens
here, in Python, for the same reason it happened in Kotlin — SQLite's `lower()` folds ASCII
only, and 438 barangay names carry characters outside it. `PsgcDatasetIntegrityTest` asserts
the result over every row.
"""

from __future__ import annotations

import csv
import gzip
import hashlib
import io
import sqlite3
import sys
from pathlib import Path

VINTAGE = "q2_2026"

SRC = Path("tools/psgc") / f"psgc-barangays-{VINTAGE}.csvgz"
OUT = Path("app/src/main/assets/psgc") / f"psgc-barangays-{VINTAGE}.db"

# Asserted by PsgcDataset/PsgcDatasetIntegrityTest. A rebuild that moves either number means
# the vintage moved, and both constants change in the same commit.
BARANGAY_COUNT = 42_010
REGION_COUNT = 18

SCHEMA = """
CREATE TABLE psgc_barangays (
    code           TEXT NOT NULL PRIMARY KEY,
    name           TEXT NOT NULL,
    city_muni_code TEXT NOT NULL,
    city_muni_name TEXT NOT NULL,
    province_code  TEXT,
    province_name  TEXT,
    region_code    TEXT NOT NULL,
    region_name    TEXT NOT NULL,
    search_text    TEXT NOT NULL
)
"""

# Room's own table carries no index on this table and neither does the asset: every query is
# a primary-key lookup or an infix LIKE, and a leading-wildcard LIKE cannot use an index.
# See PsgcBarangayEntity.


def search_text(name: str, city_muni_name: str, province_name: str, search_extra: str) -> str:
    """The lowercased haystack, field for field as `PsgcCsvParser.searchTextOf` built it."""
    parts = [name, city_muni_name, province_name, search_extra]
    return " ".join(part for part in parts if part.strip()).lower()


def read_rows() -> "list[tuple]":
    if not SRC.exists():
        raise SystemExit(f"missing {SRC.as_posix()} - run build-psgc-asset.py first")

    with gzip.open(SRC, "rt", encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle)
        header = next(reader)
        expected = [
            "code", "name", "city_muni_code", "city_muni_name",
            "province_code", "province_name", "region_code", "region_name", "search_extra",
        ]
        if header != expected:
            raise SystemExit(f"unexpected header {header}")

        rows = []
        seen: set[str] = set()
        for line_no, field in enumerate(reader, start=2):
            if not any(f.strip() for f in field):
                continue
            if len(field) != len(expected):
                raise SystemExit(f"line {line_no} has {len(field)} fields, expected {len(expected)}")
            code, name, cm_code, cm_name, p_code, p_name, r_code, r_name, extra = field
            if not code.strip() or not name.strip():
                raise SystemExit(f"line {line_no} has no code or name")
            if not cm_name.strip() or not r_name.strip():
                raise SystemExit(f"barangay {code} has no city/municipality or region")
            if code in seen:
                raise SystemExit(f"duplicate barangay code {code} ({name})")
            seen.add(code)
            rows.append((
                code,
                name,
                cm_code,
                cm_name,
                # Blank rather than absent for chartered cities - see PsgcBarangayEntity.
                p_code or None,
                p_name or None,
                r_code,
                r_name,
                search_text(name, cm_name, p_name, extra),
            ))
    return rows


def main() -> None:
    rows = read_rows()

    if len(rows) != BARANGAY_COUNT:
        raise SystemExit(f"{len(rows)} barangays, expected {BARANGAY_COUNT}")
    regions = {row[6] for row in rows}
    if len(regions) != REGION_COUNT:
        raise SystemExit(f"{len(regions)} regions, expected {REGION_COUNT}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()

    connection = sqlite3.connect(OUT)
    try:
        # Pinned so a rebuild is byte-comparable rather than merely equivalent, the same
        # discipline `build-psgc-asset.py` keeps with `mtime=0` on the gzip header.
        connection.execute("PRAGMA page_size = 4096")
        connection.execute("PRAGMA journal_mode = DELETE")
        connection.execute(SCHEMA)
        # Sorted so the b-tree is built in key order and the file does not depend on the
        # order the CSV happened to arrive in.
        connection.executemany(
            "INSERT INTO psgc_barangays VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            sorted(rows, key=lambda row: row[0]),
        )
        connection.commit()
        connection.execute("VACUUM")
        connection.commit()
    finally:
        connection.close()

    packed = OUT.read_bytes()
    print(f"{len(rows)} barangays - {len(packed) / 1024:.0f} KB")
    print(f"regions {len(regions)}")
    print(f"sha256 {hashlib.sha256(packed).hexdigest()}")
    print(f"wrote {OUT.as_posix()}")


if __name__ == "__main__":
    sys.exit(main())
