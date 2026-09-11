package com.agarthavision.data.local.psgc

/**
 * Identity of the bundled PSGC dataset.
 *
 * [VINTAGE] is the seed trigger: [PsgcSeeder] re-seeds whenever the value recorded on the
 * device differs from this one, so changing the pinned PSGC release is a dataset swap plus
 * a one-line change here — no schema migration, no Room version bump.
 *
 * [ASSET_PATH] derives from [VINTAGE] so the constant and the file it names cannot drift
 * apart. Regenerate the asset with `python tools/psgc/build-psgc-asset.py`.
 *
 * The extension is `.csvgz`, not `.csv.gz`, and that is deliberate. AGP gunzips any asset
 * ending in `.gz` while packaging and drops the extension, so a file committed as
 * `psgc-barangays-<vintage>.csv.gz` reaches the device as `psgc-barangays-<vintage>.csv` holding
 * 4.14 MB of plain text. Opening the `.gz` path then throws `FileNotFoundException` on
 * every device. `.csvgz` is outside that special case, so what ships is the gzip stream
 * this file names and [PsgcSeeder] can keep reading it through `GZIPInputStream`.
 *
 * Which PSA release this tracks, and what moving it costs: `docs/map/objects/PsgcBarangay.md`.
 */
object PsgcDataset {
    /** PSGC 2Q 2026 - `yng-me/psgc` @ `83f506a7`, which bundles PSA's releases verbatim. */
    const val VINTAGE = "q2_2026"

    val ASSET_PATH = "psgc/psgc-barangays-$VINTAGE.csvgz"

    /**
     * SHA-256 of the committed asset, asserted by `PsgcAssetPackagingTest`.
     *
     * Nothing in the build reruns the generator, so this value is what ties the committed
     * blob to it: an unintended swap fails a test instead of shipping. The generator's
     * dependencies are pinned in `tools/psgc/requirements.txt`, so a rebuild is reproducible
     * and a changed hash means the *data* moved. Regenerating deliberately means updating
     * this value in the same change — see `tools/psgc/README.md`.
     */
    const val ASSET_SHA256 = "1386cf337bfeacb18cee6a487dcb33654eb0259dc366cfe185a326643b2c0e13"

    /** Barangays in this vintage. */
    const val BARANGAY_COUNT = 42_010

    /** Regions in this vintage — eighteen since the Negros Island Region was created. */
    const val REGION_COUNT = 18
}
