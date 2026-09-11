#!/usr/bin/env python3
"""Builds the bundled PSGC barangay asset shipped in `app/src/main/assets/psgc/`.

Run this only to change the PSGC vintage. The output is committed, so a normal build never
needs it and the app never fetches reference data at runtime.

    python -m venv .venv && .venv/Scripts/pip install -r tools/psgc/requirements.txt
    .venv/Scripts/python tools/psgc/build-psgc-asset.py

Source, vintage, and why this vintage: `tools/psgc/README.md` and
`docs/map/objects/PsgcBarangay.md`. The script is strict on purpose — any row it cannot fully
resolve aborts the build rather than shipping a blank label into the picker.

Why this replaced `build-psgc-asset.mjs`: the previous source
(`altcoder/philippines-psgc-shapefiles`) stopped publishing after 4Q 2023, which stranded the
dataset three years behind PSA and, in particular, before the Negros Island Region existed.
The upstream here tracks PSA release by release and is the same 10-digit code system, so the
vintage can move again without another source change.
"""

from __future__ import annotations

import gzip
import hashlib
import io
import sys
import urllib.request
from pathlib import Path

import rdata

# ── Pinned upstream ───────────────────────────────────────────────────────────
# `yng-me/psgc`, an R package that bundles PSA's PSGC releases verbatim. Pinned to a commit
# so a rebuild is reproducible; bumping the vintage means bumping this too.
VINTAGE = "q2_2026"
RELEASE = "Q2_2026"
UPSTREAM = "yng-me/psgc"
UPSTREAM_SHA = "83f506a7f5c89b7fd2f84fbebb9a0bfd275f0bc0"
UPSTREAM_URL = f"https://raw.githubusercontent.com/{UPSTREAM}/{UPSTREAM_SHA}/R/sysdata.rda"

# `.csvgz`, not `.csv.gz`: AGP gunzips any asset ending in `.gz` while packaging and strips
# the extension, which leaves the app opening a path that does not exist. See PsgcDataset.
OUT = Path("app/src/main/assets/psgc") / f"psgc-barangays-{VINTAGE}.csvgz"

HEADER = (
    "code,name,city_muni_code,city_muni_name,"
    "province_code,province_name,region_code,region_name,search_extra"
)

PSGC_DIGITS = 10
# A PSGC code is RR PPP MM BBB: region, province, city/municipality, barangay.
REGION_PREFIX = 2
PROVINCE_PREFIX = 5
CITY_MUNI_PREFIX = 7


def parent(code: str, prefix: int) -> str:
    """The code of the unit `prefix` digits up the hierarchy."""
    return code[:prefix].ljust(PSGC_DIGITS, "0")


def csv_cell(value: str) -> str:
    """Quote only when the value would otherwise break the row."""
    if any(ch in value for ch in ',"\n'):
        escaped = value.replace('"', '""')
        return f'"{escaped}"'
    return value


def load_release() -> "list[dict[str, str]]":
    print(f"PSGC vintage {VINTAGE} - {UPSTREAM}@{UPSTREAM_SHA[:10]} ({RELEASE})")
    with urllib.request.urlopen(UPSTREAM_URL) as response:  # noqa: S310 - pinned https URL
        blob = response.read()
    print(f"fetched sysdata.rda - {len(blob) / 1024:.0f} KB")

    converted = rdata.conversion.convert(rdata.parser.parse_file(io.BytesIO(blob)))
    releases = converted["psgc_releases"]
    available = [str(k) for k in releases.keys()]
    if RELEASE not in available:
        raise SystemExit(f"{RELEASE} not in upstream. Available: {', '.join(available)}")

    frame = releases[RELEASE]
    return frame.to_dict("records")


def main() -> None:
    rows = load_release()

    # One index over every level. Some units carry no `geographic_level` at all — the
    # Special Geographic Area in BARMM and City of Isabela both sit in the province slot
    # without being labelled `Prov` — so resolving by code shape rather than by label is
    # what keeps their barangays from being dropped.
    name_by_code: dict[str, str] = {}
    sub_municipalities: set[str] = set()
    barangays: list[dict[str, str]] = []

    for row in rows:
        code = str(row["psgc_code"]).strip()
        name = str(row["area_name"]).strip()
        level = str(row["geographic_level"] or "").strip()
        if not code or not name:
            continue
        if len(code) != PSGC_DIGITS or not code.isdigit():
            raise SystemExit(f"Not a PSGC code: {code!r} ({name})")
        name_by_code[code] = name
        if level == "SubMun":
            sub_municipalities.add(code)
        elif level == "Bgy":
            barangays.append({"code": code, "name": name})

    lines = [HEADER]
    seen: set[str] = set()

    for barangay in barangays:
        code, name = barangay["code"], barangay["name"]
        if code in seen:
            raise SystemExit(f"Duplicate barangay code {code} ({name})")
        seen.add(code)

        region_code = parent(code, REGION_PREFIX)
        region_name = name_by_code.get(region_code)
        if not region_name:
            raise SystemExit(f"Barangay {code} ({name}) has no region {region_code}")

        # Resolve the direct parent, then roll a sub-municipality up to its chartered city so
        # the picker reads "City of Manila"; the sub-municipality stays searchable through
        # search_extra. Manila barangay numbers are unique city-wide, so nothing is ambiguous.
        city_muni_code = parent(code, CITY_MUNI_PREFIX)
        search_extra = ""
        if city_muni_code in sub_municipalities:
            search_extra = name_by_code[city_muni_code]
            city_muni_code = parent(code, PROVINCE_PREFIX)

        city_muni_name = name_by_code.get(city_muni_code)
        if not city_muni_name:
            raise SystemExit(f"Barangay {code} ({name}) has no city/municipality {city_muni_code}")

        # Highly urbanised and independent cities occupy the province slot themselves, so
        # their province resolves to the city and is deliberately left blank.
        province_code = parent(code, PROVINCE_PREFIX)
        province_name = name_by_code.get(province_code, "")
        if province_code == city_muni_code or not province_name:
            province_code, province_name = "", ""

        lines.append(
            ",".join(
                csv_cell(value)
                for value in (
                    code,
                    name,
                    city_muni_code,
                    city_muni_name,
                    province_code,
                    province_name,
                    region_code,
                    region_name,
                    search_extra,
                )
            )
        )

    raw = ("\n".join(lines) + "\n").encode("utf-8")
    # mtime=0 so the gzip header carries no timestamp and a rebuild is byte-comparable.
    buffer = io.BytesIO()
    with gzip.GzipFile(fileobj=buffer, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write(raw)
    packed = buffer.getvalue()

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(packed)

    print(f"{len(seen)} barangays - raw {len(raw) / 1024:.0f} KB - gzip {len(packed) / 1024:.0f} KB")
    print(f"regions {sum(1 for c in name_by_code if c.endswith('0' * 8))}")
    print(f"sha256 {hashlib.sha256(packed).hexdigest()}")
    print(f"wrote {OUT.as_posix()}")


if __name__ == "__main__":
    sys.exit(main())
