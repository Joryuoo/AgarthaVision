package com.agarthavision.data.local.psgc

/**
 * Identity of the bundled PSGC dataset.
 *
 * [VINTAGE] is the seed trigger: [PsgcSeeder] re-seeds whenever the value recorded on the
 * device differs from this one, so changing the pinned PSGC release is a dataset swap plus
 * a one-line change here — no schema migration, no Room version bump.
 *
 * [ASSET_PATH] derives from [VINTAGE] so the constant and the file it names cannot drift
 * apart. Regenerate the asset with `python tools/psgc/build-psgc-db.py`.
 *
 * **The asset is a SQLite database, not a CSV.** It holds one table shaped like
 * [com.agarthavision.data.local.entity.PsgcBarangayEntity] and no `room_master_table`:
 * [PsgcSeeder] attaches it and copies the rows out, so it is never opened as a Room database
 * and never has to match Room's `identityHash`. The CSV it is built from stays in
 * `tools/psgc/` and is not packaged — shipping both would put the same 42,010 rows in the
 * APK twice.
 *
 * A `.gz` extension is still worth avoiding here, and the reason is worth keeping: AGP
 * gunzips any asset ending in `.gz` while packaging and drops the extension, so a file
 * committed as `psgc-barangays-<vintage>.csv.gz` reached the device as
 * `psgc-barangays-<vintage>.csv` and every open threw `FileNotFoundException`. That is what
 * `PsgcAssetPackagingTest` exists to catch. `.db` is outside that special case.
 *
 * Which PSA release this tracks, and what moving it costs: `docs/map/objects/PsgcBarangay.md`.
 */
object PsgcDataset {
    /** PSGC 2Q 2026 - `yng-me/psgc` @ `83f506a7`, which bundles PSA's releases verbatim. */
    const val VINTAGE = "q2_2026"

    val ASSET_PATH = "psgc/psgc-barangays-$VINTAGE.db"

    /**
     * SHA-256 of the committed asset, asserted by `PsgcAssetPackagingTest`.
     *
     * Nothing in the build reruns the generator, so this value is what ties the committed
     * blob to it: an unintended swap fails a test instead of shipping. `build-psgc-db.py`
     * pins the SQLite page size and inserts in key order, so a rebuild from the same CSV is
     * byte-identical and a changed hash means the *data* moved. Regenerating deliberately
     * means updating this value in the same change — see `tools/psgc/README.md`.
     */
    const val ASSET_SHA256 = "d3fec0c3184422dfbe8131e60fe498d3f4efe6548aae59238069e9856bb37762"

    /** Barangays in this vintage. */
    const val BARANGAY_COUNT = 42_010

    /** Regions in this vintage — eighteen since the Negros Island Region was created. */
    const val REGION_COUNT = 18

    /**
     * Barangays with no province, in highly urbanised and independent cities.
     *
     * Those cities occupy the province slot themselves, so PSGC gives them no province. The
     * figure is vintage-specific and asserted by `PsgcDatasetIntegrityTest`; it was 3,083
     * under the 4Q 2023 dataset this replaced.
     */
    const val PROVINCELESS_COUNT = 3_025
}
