package com.agarthavision.data.local.psgc

/**
 * Identity of the bundled PSGC dataset.
 *
 * [VINTAGE] is the seed trigger: [PsgcSeeder] re-seeds whenever the value recorded on the
 * device differs from this one, so changing the pinned PSGC release is a dataset swap plus
 * a one-line change here â€” no schema migration, no Room version bump.
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
    /** PSGC 4Q 2023 â€” `altcoder/philippines-psgc-shapefiles` @ `a44a7309`. 42,001 barangays. */
    const val VINTAGE = "4q2023"

    val ASSET_PATH = "psgc/psgc-barangays-$VINTAGE.csvgz"

    /**
     * SHA-256 of the committed asset, asserted by `PsgcAssetPackagingTest`.
     *
     * Nothing in the build ties the committed blob to `tools/psgc/build-psgc-asset.mjs`, and
     * `tools/` has no lockfile, so a regenerated asset is only probably byte-identical. This
     * makes an unintended swap fail a test instead of shipping. Regenerating deliberately
     * means updating this value in the same change â€” see `tools/psgc/README.md`.
     */
    const val ASSET_SHA256 = "18c324248b977da67b7aeba6d0a9ef13deb524bd1071fafcbfd08dd32c06308a"

    /** Barangays in this vintage. */
    const val BARANGAY_COUNT = 42_001
}
