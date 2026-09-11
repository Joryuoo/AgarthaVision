#!/usr/bin/env node
/**
 * Builds the bundled PSGC barangay asset shipped in `app/src/main/assets/psgc/`.
 *
 * Run this only to change the PSGC vintage. The output is committed, so a normal build
 * never needs it and the app never fetches reference data at runtime.
 *
 *   node tools/psgc/build-psgc-asset.mjs
 *
 * Source, vintage, and why this vintage is pinned: `tools/psgc/README.md` and
 * `docs/map/objects/PsgcBarangay.md`. The script is strict on purpose — any row it cannot
 * fully resolve aborts the build rather than shipping a blank label into the picker.
 */
import { gzipSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const VINTAGE = "4q2023";
const UPSTREAM = "altcoder/philippines-psgc-shapefiles";
const UPSTREAM_SHA = "a44a73091f19e4950dbdc0d7cb77a5e17b101a0a";
const BASE = `https://raw.githubusercontent.com/${UPSTREAM}/${UPSTREAM_SHA}/dist`;

// `.csvgz`, not `.csv.gz`: AGP gunzips any asset ending in `.gz` while packaging and
// strips the extension, which leaves the app opening a path that does not exist.
// See the KDoc on PsgcDataset.
const OUT = join("app", "src", "main", "assets", "psgc", `psgc-barangays-${VINTAGE}.csvgz`);
const HEADER = [
  "code",
  "name",
  "city_muni_code",
  "city_muni_name",
  "province_code",
  "province_name",
  "region_code",
  "region_name",
  "search_extra",
].join(",");

const PSGC_DIGITS = 10;
/** A PSGC code's city/municipality lives in its first seven digits. */
const CITY_MUNI_PREFIX = 7;
/** A chartered city occupies the province slot, so its own code stops at five digits. */
const CHARTERED_CITY_PREFIX = 5;

/** Minimal RFC 4180 reader — barangay names legitimately contain commas. */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  const endField = () => {
    row.push(field);
    field = "";
  };
  const endRow = () => {
    endField();
    if (row.length > 1 || row[0] !== "") rows.push(row);
    row = [];
  };
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    if (quoted) {
      if (c !== '"') field += c;
      else if (text[i + 1] === '"') {
        field += '"';
        i += 1;
      } else quoted = false;
    } else if (c === '"') quoted = true;
    else if (c === ",") endField();
    else if (c === "\n") endRow();
    else if (c !== "\r") field += c;
  }
  endRow();
  const [head, ...body] = rows;
  return body.map((cells) => Object.fromEntries(head.map((k, i) => [k, (cells[i] ?? "").trim()])));
}

/** Upstream stores PSGC numerically, so regions 01-09 arrive a leading zero short. */
function normaliseCode(raw, context) {
  if (!/^\d{1,10}$/.test(raw)) throw new Error(`Not a PSGC code: "${raw}" (${context})`);
  return raw.padStart(PSGC_DIGITS, "0");
}

/** Quote only when the value would otherwise break the row. */
function csvCell(value) {
  return /[",\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value;
}

async function fetchLevel(file) {
  const url = `${BASE}/${file}`;
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET ${url} -> ${response.status}`);
  return parseCsv(await response.text());
}

function indexNames(rows, codeKey, nameKey) {
  const byCode = new Map();
  for (const row of rows) {
    const name = row[nameKey];
    if (name) byCode.set(normaliseCode(row[codeKey], codeKey), name);
  }
  return byCode;
}

async function main() {
  console.log(`PSGC vintage ${VINTAGE} - ${UPSTREAM}@${UPSTREAM_SHA.slice(0, 10)}`);
  const [regions, provinces, cityMunis, adm4] = await Promise.all(
    [
      "PH_Adm1_Regions.csv",
      "PH_Adm2_ProvDists.csv",
      "PH_Adm3_MuniCities.csv",
      "PH_Adm4_BgySubMuns.csv",
    ].map(fetchLevel),
  );

  const regionNames = indexNames(regions, "adm1_psgc", "adm1_en");
  const provinceNames = indexNames(provinces, "adm2_psgc", "adm2_en");
  const cityMuniNames = indexNames(cityMunis, "adm3_psgc", "adm3_en");

  // Manila's 14 sub-municipalities share the barangay file but are not barangays: they are
  // the direct parents of its 897 barangays, and their own parent columns are zeroed.
  const subMuniNames = indexNames(
    adm4.filter((r) => r.geo_level === "SubMun"),
    "adm4_psgc",
    "adm4_en",
  );

  const skipped = { nameless: 0, notBarangay: 0 };
  const seen = new Set();
  const lines = [HEADER];

  for (const row of adm4) {
    if (row.geo_level !== "Bgy") {
      if (row.geo_level === "SubMun") skipped.notBarangay += 1;
      else skipped.nameless += 1; // Unnamed sliver polygons carried by the upstream shapefile.
      continue;
    }
    const code = normaliseCode(row.adm4_psgc, "adm4_psgc");
    const name = row.adm4_en;
    if (!name) {
      skipped.nameless += 1;
      continue;
    }
    if (seen.has(code)) throw new Error(`Duplicate barangay code ${code} (${name})`);
    seen.add(code);

    const regionCode = normaliseCode(row.adm1_psgc, "adm1_psgc");
    const regionName = regionNames.get(regionCode);
    if (!regionName) throw new Error(`Barangay ${code} (${name}) has no region ${regionCode}`);

    // Highly urbanised and independent cities sit in the province slot, so they are
    // deliberately absent from the province list. Province stays empty for them.
    const provinceCode = normaliseCode(row.adm2_psgc, "adm2_psgc");
    const provinceName = provinceNames.get(provinceCode) ?? "";

    // Resolve the direct parent, then roll a sub-municipality up to its chartered city so
    // the picker reads "City of Manila"; the sub-municipality stays searchable via
    // search_extra. Manila barangay numbers are unique city-wide, so nothing is ambiguous.
    const parentCode = normaliseCode(row.adm3_psgc, "adm3_psgc");
    let cityMuniCode = parentCode;
    let searchExtra = "";
    const subMuniName = subMuniNames.get(parentCode);
    if (subMuniName) {
      cityMuniCode = `${parentCode.slice(0, CHARTERED_CITY_PREFIX)}`.padEnd(PSGC_DIGITS, "0");
      searchExtra = subMuniName;
    }
    const cityMuniName = cityMuniNames.get(cityMuniCode);
    if (!cityMuniName) {
      throw new Error(`Barangay ${code} (${name}) has no city/municipality ${cityMuniCode}`);
    }

    lines.push(
      [
        code,
        name,
        cityMuniCode,
        cityMuniName,
        provinceCode === parentCode || !provinceName ? "" : provinceCode,
        provinceName,
        regionCode,
        regionName,
        searchExtra,
      ]
        .map(csvCell)
        .join(","),
    );
  }

  const raw = Buffer.from(`${lines.join("\n")}\n`, "utf8");
  const gzipped = gzipSync(raw, { level: 9 });
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, gzipped);

  const kb = (n) => `${(n / 1024).toFixed(0)} KB`;
  console.log(`${seen.size} barangays - raw ${kb(raw.length)} - gzip ${kb(gzipped.length)}`);
  console.log(
    `skipped ${skipped.notBarangay} sub-municipality rows, ${skipped.nameless} unnamed rows`,
  );
  console.log(`wrote ${OUT}`);
}

await main();
