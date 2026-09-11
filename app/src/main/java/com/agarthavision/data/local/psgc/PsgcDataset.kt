package com.agarthavision.data.local.psgc

/**
 * Identity of the bundled PSGC dataset.
 *
 * [VINTAGE] is the seed trigger: [PsgcSeeder] re-seeds whenever the value recorded on the
 * device differs from this one, so changing the pinned PSGC release is a dataset swap plus
 * a one-line change here — no schema migration, no Room version bump.
 *
 * [ASSET_PATH] derives from [VINTAGE] so the constant and the file it names cannot drift
 * apart. Regenerate the asset with `node tools/psgc/build-psgc-asset.mjs`.
 *
 * The extension is `.csvgz`, not `.csv.gz`, and that is deliberate. AGP gunzips any asset
 * ending in `.gz` while packaging and drops the extension, so a file committed as
 * `psgc-barangays-4q2023.csv.gz` reaches the device as `psgc-barangays-4q2023.csv` holding
 * 4.14 MB of plain text. Opening the `.gz` path then throws `FileNotFoundException` on
 * every device. `.csvgz` is outside that special case, so what ships is the gzip stream
 * this file names and [PsgcSeeder] can keep reading it through `GZIPInputStream`.
 *
 * Why 4Q 2023 rather than the newest PSA release: `docs/map/objects/PsgcBarangay.md`.
 */
object PsgcDataset {
    /**
     * PSGC 4Q 2023 — `altcoder/philippines-psgc-shapefiles` @ `a44a7309`. 42,001 barangays.
     *
     * The committed asset is SHA-256
     * `18c324248b977da67b7aeba6d0a9ef13deb524bd1071fafcbfd08dd32c06308a`.
     * See `tools/psgc/README.md` for why that hash is recorded and what a mismatch means.
     */
    const val VINTAGE = "4q2023"

    val ASSET_PATH = "psgc/psgc-barangays-$VINTAGE.csvgz"
}
