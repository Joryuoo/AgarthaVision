package com.agarthavision.data.geo

/**
 * Identity of the bundled offline boundary dataset. Mirrors
 * [com.agarthavision.data.local.psgc.PsgcDataset] deliberately — [VINTAGE] must equal
 * [com.agarthavision.data.local.psgc.PsgcDataset.VINTAGE] exactly, since the boundary geometry
 * is only meaningful joined against that exact PSGC release. See `tools/geo/README.md`.
 */
object GeoDataset {
    const val VINTAGE = "q2_2026"

    val PROVINCES_ASSET_PATH = "geo/ph-provinces-$VINTAGE.bin"
    val TOWNS_ASSET_PATH = "geo/ph-towns-$VINTAGE.bin"

    /** SHA-256 of the committed asset, asserted by `GeoAssetPackagingTest`. */
    const val PROVINCES_SHA256 = "6b06912dccae4dcb6ac37a42424341bdd217896b770c1ab8fcac3144cbea2e05"

    /** SHA-256 of the committed asset, asserted by `GeoAssetPackagingTest`. */
    const val TOWNS_SHA256 = "b0fd9b963afeb8fbf1df5ce3d05b7569799bae8e5ecd52128468f6cc52b23ead"

    /** Province-level units: 84 real PSGC provinces plus the NCR pseudo-province. */
    const val PROVINCE_UNIT_COUNT = 85

    const val TOWN_COUNT = 1_642
}
