#!/usr/bin/env python3
"""Packs the committed intermediate GeoJSON into the binary assets the app ships.

    python tools/geo/build-geo-asset.py

Reads `tools/geo/build/provinces-<vintage>.geojson.gz`, `tools/geo/build/towns-<vintage>.geojson.gz`
and `tools/geo/town-keys-<vintage>.csv` (all committed, all produced by the earlier pipeline
stages - see `tools/geo/README.md`), and writes:

    app/src/main/assets/geo/ph-provinces-<vintage>.bin
    app/src/main/assets/geo/ph-towns-<vintage>.bin

stdlib only, no network. Deterministic: the same inputs always produce the same bytes (features
are sorted by code before writing).

## Binary format "AVGE" v1

Little-endian throughout. Both files share this header:

    magic        4 bytes ASCII "AVGE"
    version      u8 = 1
    kind         u8 (1 = provinces, 2 = towns)
    vintageLen   u8
    vintage      `vintageLen` ASCII bytes, e.g. "q2_2026"
    lonMin       f32 |  shared quantization frame. Padded a couple percent past the real
    latMin       f32 |  Philippines extent (including the Kalayaan/Spratly claim, which pulls
    lonMax       f32 |  the true bounds west of the usual ~116.9-126.7 figure) so no real point
    latMax       f32 |  sits exactly on the frame edge. Coordinates quantize to a 0..65535 grid
                          per axis within this frame - see `quantize_lon`/`quantize_lat`.

**Provinces file body** (kind=1):

    townCount            u16          - full PSGC town directory, not just towns with geometry
    townCount x {
        code              i32
        provinceKey       i32
        hasGeometry       u8
        nameLen           u8
        name              `nameLen` bytes UTF-8
    }
    areaCount             u16         - always 85: one Area record per province-level unit
    areaCount x Area record

**Towns file body** (kind=2):

    provinceCount         u16
    provinceCount x {
        provinceKey       i32
        byteOffset        u32        - relative to the start of the Area-records section below
        areaCount         u16
    }
    (Area records for every town, grouped contiguously by province, same order as the index)

**Area record** (shared shape for a province or a town):

    code          i32
    parentCode    i32   - a province's parent is its region code; a town's parent is its provinceKey
    nameLen       u8
    name          `nameLen` bytes UTF-8
    bbox          4 x u16   quantized (minX, minY, maxX, maxY)
    label         2 x u16   quantized interior label point (ring centroid)
    ringCount     varint
    ringCount x {
        pointCount    varint
        first point   2 x u16 absolute quantized (x, y)
        (pointCount - 1) more points, each a zigzag-varint delta from the previous point's
        *quantized integer* position, interleaved dx, dy, dx, dy, ...
    }

Varint: standard LEB128, 7 bits per byte, high bit = continuation.
Zigzag: `(n << 1) ^ (n >> 31)` for a signed 32-bit `n`, matching protobuf's `sint32` encoding.
Both are implemented here by hand (`write_varint`/`zigzag`), not pulled from a dependency.
"""

from __future__ import annotations

import csv
import gzip
import hashlib
import json
import struct
import sys
from pathlib import Path

VINTAGE = "q2_2026"

BUILD_DIR = Path("tools/geo/build")
TOWNS_GEOJSON = BUILD_DIR / f"towns-{VINTAGE}.geojson.gz"
PROVINCES_GEOJSON = BUILD_DIR / f"provinces-{VINTAGE}.geojson.gz"
TOWN_KEYS_CSV = Path("tools/geo") / f"town-keys-{VINTAGE}.csv"

ASSETS_DIR = Path("app/src/main/assets/geo")
PROVINCES_OUT = ASSETS_DIR / f"ph-provinces-{VINTAGE}.bin"
TOWNS_OUT = ASSETS_DIR / f"ph-towns-{VINTAGE}.bin"

EXPECTED_TOWN_COUNT = 1_642
EXPECTED_PROVINCE_COUNT = 85

PROVINCES_BUDGET_BYTES = 200 * 1024
TOWNS_BUDGET_BYTES = 900 * 1024

MAGIC = b"AVGE"
VERSION = 1
KIND_PROVINCES = 1
KIND_TOWNS = 2

QUANT_MAX = 65535  # 0..65535 grid per axis (u16)

# Padded a couple of percent past the pipeline's actual combined extent (114.28-126.61 lon,
# 4.59-21.12 lat), which already includes the Kalayaan (Spratly) claim west of the usual
# ~116.9 figure. Recomputed from the real data below and asserted to still fit this frame -
# these numbers only need to change if a future vintage's geometry falls outside them.
FRAME_LON_MIN = 113.5
FRAME_LAT_MIN = 4.0
FRAME_LON_MAX = 127.5
FRAME_LAT_MAX = 21.7


def quantize(value: float, lo: float, hi: float) -> int:
    if value < lo or value > hi:
        raise SystemExit(f"value {value} outside quantization frame [{lo}, {hi}]")
    q = round((value - lo) / (hi - lo) * QUANT_MAX)
    return max(0, min(QUANT_MAX, q))


def quantize_lon(lon: float) -> int:
    return quantize(lon, FRAME_LON_MIN, FRAME_LON_MAX)


def quantize_lat(lat: float) -> int:
    return quantize(lat, FRAME_LAT_MIN, FRAME_LAT_MAX)


def zigzag_encode(n: int) -> int:
    return (n << 1) if n >= 0 else ((-n << 1) - 1)


def write_varint(buf: bytearray, value: int) -> None:
    if value < 0:
        raise ValueError("varint must be non-negative (zigzag-encode signed values first)")
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            buf.append(byte | 0x80)
        else:
            buf.append(byte)
            return


def write_u8(buf: bytearray, value: int) -> None:
    buf.extend(struct.pack("<B", value))


def write_u16(buf: bytearray, value: int) -> None:
    buf.extend(struct.pack("<H", value))


def write_i32(buf: bytearray, value: int) -> None:
    buf.extend(struct.pack("<i", value))


def write_u32(buf: bytearray, value: int) -> None:
    buf.extend(struct.pack("<I", value))


def write_f32(buf: bytearray, value: float) -> None:
    buf.extend(struct.pack("<f", value))


def write_name(buf: bytearray, name: str) -> None:
    encoded = name.encode("utf-8")
    if len(encoded) > 255:
        raise SystemExit(f"name too long for u8 length prefix: {name!r}")
    write_u8(buf, len(encoded))
    buf.extend(encoded)


def write_header(buf: bytearray, kind: int) -> None:
    buf.extend(MAGIC)
    write_u8(buf, VERSION)
    write_u8(buf, kind)
    vintage_bytes = VINTAGE.encode("ascii")
    write_u8(buf, len(vintage_bytes))
    buf.extend(vintage_bytes)
    write_f32(buf, FRAME_LON_MIN)
    write_f32(buf, FRAME_LAT_MIN)
    write_f32(buf, FRAME_LON_MAX)
    write_f32(buf, FRAME_LAT_MAX)


def polygons_of(geometry: dict) -> "list[list[list[list[float]]]]":
    """Returns a list of polygons (each a list of rings) regardless of Polygon/MultiPolygon."""
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["coordinates"]
    raise SystemExit(f"unexpected geometry type {geometry['type']}")


def rings_of(geometry: dict) -> "list[list[list[float]]]":
    """Flattens Polygon/MultiPolygon into one flat list of rings (exteriors and holes alike).

    Even-odd ray casting (`HitTest.pointInRings`) treats a hole and a second disjoint exterior
    the same way as long as every ring is included, so a MultiPolygon town/province just
    becomes one `AreaShape` with more rings - no separate multi-part representation needed.
    """
    rings = []
    for polygon in polygons_of(geometry):
        for ring in polygon:
            if len(ring) < 4:  # GeoJSON rings repeat the first point as the last
                raise SystemExit(f"ring with fewer than 4 points (3 distinct): {ring}")
            rings.append(ring)
    return rings


def ring_points(ring: "list[list[float]]") -> "list[list[float]]":
    """Drops GeoJSON's closing duplicate point - callers reconstruct closure implicitly."""
    pts = ring[:-1] if ring[0] == ring[-1] else ring[:]
    if len(pts) < 3:
        raise SystemExit(f"ring has fewer than 3 distinct points: {ring}")
    return pts


def centroid_of(rings: "list[list[list[float]]]") -> "tuple[float, float]":
    """Simple vertex-average centroid of the outer extent - good enough for label placement."""
    xs = [p[0] for ring in rings for p in ring_points(ring)]
    ys = [p[1] for ring in rings for p in ring_points(ring)]
    return sum(xs) / len(xs), sum(ys) / len(ys)


def bbox_of(rings: "list[list[list[float]]]") -> "tuple[float, float, float, float]":
    xs = [p[0] for ring in rings for p in ring_points(ring)]
    ys = [p[1] for ring in rings for p in ring_points(ring)]
    return min(xs), min(ys), max(xs), max(ys)


def write_area_record(buf: bytearray, code: int, parent_code: int, name: str, geometry: dict) -> None:
    rings = rings_of(geometry)
    min_x, min_y, max_x, max_y = bbox_of(rings)
    label_x, label_y = centroid_of(rings)

    write_i32(buf, code)
    write_i32(buf, parent_code)
    write_name(buf, name)
    write_u16(buf, quantize_lon(min_x))
    write_u16(buf, quantize_lat(min_y))
    write_u16(buf, quantize_lon(max_x))
    write_u16(buf, quantize_lat(max_y))
    write_u16(buf, quantize_lon(label_x))
    write_u16(buf, quantize_lat(label_y))

    write_varint(buf, len(rings))
    for ring in rings:
        pts = ring_points(ring)
        write_varint(buf, len(pts))
        prev_qx = prev_qy = None
        for i, (lon, lat) in enumerate(pts):
            qx, qy = quantize_lon(lon), quantize_lat(lat)
            if i == 0:
                write_u16(buf, qx)
                write_u16(buf, qy)
            else:
                write_varint(buf, zigzag_encode(qx - prev_qx))
                write_varint(buf, zigzag_encode(qy - prev_qy))
            prev_qx, prev_qy = qx, qy


def load_geojson(path: Path) -> dict:
    if not path.exists():
        raise SystemExit(f"missing {path.as_posix()} - run build-geo-shapes.sh first")
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        return json.load(handle)


def load_town_keys() -> "list[dict]":
    if not TOWN_KEYS_CSV.exists():
        raise SystemExit(f"missing {TOWN_KEYS_CSV.as_posix()} - run build-town-keys.py first")
    with TOWN_KEYS_CSV.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def sha256_of(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_provinces_asset(provinces_fc: dict, town_keys: "list[dict]") -> bytes:
    buf = bytearray()
    write_header(buf, KIND_PROVINCES)

    towns_sorted = sorted(town_keys, key=lambda row: int(row["code"]))
    write_u16(buf, len(towns_sorted))
    for row in towns_sorted:
        write_i32(buf, int(row["code"]))
        write_i32(buf, int(row["province_key"]))
        write_u8(buf, 1)  # hasGeometry - every town joined successfully, see build-town-keys.py
        write_name(buf, row["name"])

    features = sorted(provinces_fc["features"], key=lambda f: int(f["properties"]["province_key"]))
    write_u16(buf, len(features))
    for feature in features:
        props = feature["properties"]
        code = int(props["province_key"])
        parent_code = int(props["region_code"])
        name = props["province_name"]
        write_area_record(buf, code, parent_code, name, feature["geometry"])

    return bytes(buf)


def build_towns_asset(towns_fc: dict) -> bytes:
    buf = bytearray()
    write_header(buf, KIND_TOWNS)

    features_by_province: "dict[int, list[dict]]" = {}
    for feature in towns_fc["features"]:
        province_key = int(feature["properties"]["province_key"])
        features_by_province.setdefault(province_key, []).append(feature)

    province_keys = sorted(features_by_province)
    index_buf = bytearray()
    records_buf = bytearray()
    write_u16(index_buf, len(province_keys))

    for province_key in province_keys:
        features = sorted(features_by_province[province_key], key=lambda f: int(f["properties"]["code"]))
        offset_before = len(records_buf)
        for feature in features:
            props = feature["properties"]
            write_area_record(records_buf, int(props["code"]), province_key, props["name"], feature["geometry"])
        write_i32(index_buf, province_key)
        write_u32(index_buf, offset_before)
        write_u16(index_buf, len(features))

    buf.extend(index_buf)
    buf.extend(records_buf)
    return bytes(buf)


def assert_int32_safe(value: int, context: str) -> None:
    if not (-(2 ** 31) <= value <= 2 ** 31 - 1):
        raise SystemExit(f"{context} value {value} does not fit in a signed 32-bit int")


def main() -> None:
    provinces_fc = load_geojson(PROVINCES_GEOJSON)
    towns_fc = load_geojson(TOWNS_GEOJSON)
    town_keys = load_town_keys()

    if len(town_keys) != EXPECTED_TOWN_COUNT:
        raise SystemExit(f"{len(town_keys)} rows in town-keys CSV, expected {EXPECTED_TOWN_COUNT}")
    if len(towns_fc["features"]) != EXPECTED_TOWN_COUNT:
        raise SystemExit(f"{len(towns_fc['features'])} town features, expected {EXPECTED_TOWN_COUNT}")
    if len(provinces_fc["features"]) != EXPECTED_PROVINCE_COUNT:
        raise SystemExit(f"{len(provinces_fc['features'])} province features, expected {EXPECTED_PROVINCE_COUNT}")

    town_codes = {int(row["code"]) for row in town_keys}
    feature_codes = {int(f["properties"]["code"]) for f in towns_fc["features"]}
    if town_codes != feature_codes:
        raise SystemExit("town-keys CSV and towns geojson disagree on the set of town codes")

    for row in town_keys:
        assert_int32_safe(int(row["code"]), "town code")
        assert_int32_safe(int(row["province_key"]), "province_key")
    for feature in provinces_fc["features"]:
        assert_int32_safe(int(feature["properties"]["province_key"]), "province code")
        assert_int32_safe(int(feature["properties"]["region_code"]), "region code")

    for feature in list(provinces_fc["features"]) + list(towns_fc["features"]):
        for ring in rings_of(feature["geometry"]):
            if len(ring_points(ring)) < 3:
                raise SystemExit(f"ring with fewer than 3 points in {feature['properties']}")

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    provinces_bytes = build_provinces_asset(provinces_fc, town_keys)
    towns_bytes = build_towns_asset(towns_fc)

    if len(provinces_bytes) > PROVINCES_BUDGET_BYTES:
        raise SystemExit(
            f"provinces asset {len(provinces_bytes)} bytes exceeds budget {PROVINCES_BUDGET_BYTES} "
            "- simplify more aggressively in build-geo-shapes.sh"
        )
    if len(towns_bytes) > TOWNS_BUDGET_BYTES:
        raise SystemExit(
            f"towns asset {len(towns_bytes)} bytes exceeds budget {TOWNS_BUDGET_BYTES} "
            "- simplify more aggressively in build-geo-shapes.sh"
        )

    PROVINCES_OUT.write_bytes(provinces_bytes)
    TOWNS_OUT.write_bytes(towns_bytes)

    provinces_point_count = sum(
        len(ring_points(ring)) for f in provinces_fc["features"] for ring in rings_of(f["geometry"])
    )
    towns_point_count = sum(
        len(ring_points(ring)) for f in towns_fc["features"] for ring in rings_of(f["geometry"])
    )

    print(f"{PROVINCES_OUT.as_posix()}: {len(provinces_bytes)} bytes, {provinces_point_count} points")
    print(f"  sha256 {sha256_of(provinces_bytes)}")
    print(f"{TOWNS_OUT.as_posix()}: {len(towns_bytes)} bytes, {towns_point_count} points")
    print(f"  sha256 {sha256_of(towns_bytes)}")


if __name__ == "__main__":
    sys.exit(main())
