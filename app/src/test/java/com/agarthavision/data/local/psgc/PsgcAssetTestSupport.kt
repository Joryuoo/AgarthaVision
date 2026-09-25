package com.agarthavision.data.local.psgc

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.agarthavision.data.local.entity.PsgcBarangayEntity
import java.io.File

/**
 * Reads the bundled PSGC asset the way [PsgcSeeder] does, for tests that assert on its rows.
 *
 * Staged to a real file first because SQLite opens paths and an asset inside the APK is not
 * one — the same reason the seeder stages it.
 */
internal fun stagePsgcAsset(context: Context): File =
    File.createTempFile("psgc-test-", ".db", context.cacheDir).also { staged ->
        context.assets.open(PsgcDataset.ASSET_PATH).use { asset ->
            staged.outputStream().use { asset.copyTo(it) }
        }
    }

/** Every row in the bundled asset, mapped to the entity the app stores. */
internal fun readBundledBarangays(context: Context): List<PsgcBarangayEntity> {
    val staged = stagePsgcAsset(context)
    try {
        SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                """
                SELECT code, name, city_muni_code, city_muni_name,
                       province_code, province_name, region_code, region_name, search_text
                FROM psgc_barangays
                """.trimIndent(),
                null,
            ).use { cursor ->
                return buildList {
                    while (cursor.moveToNext()) {
                        add(
                            PsgcBarangayEntity(
                                code = cursor.getString(0),
                                name = cursor.getString(1),
                                cityMuniCode = cursor.getString(2),
                                cityMuniName = cursor.getString(3),
                                provinceCode = cursor.getString(4),
                                provinceName = cursor.getString(5),
                                regionCode = cursor.getString(6),
                                regionName = cursor.getString(7),
                                searchText = cursor.getString(8),
                            ),
                        )
                    }
                }
            }
        }
    } finally {
        staged.delete()
    }
}

/** Table names the bundled asset declares, used to prove what it does and does not carry. */
internal fun bundledAssetTables(context: Context): Set<String> {
    val staged = stagePsgcAsset(context)
    try {
        SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                return buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
        }
    } finally {
        staged.delete()
    }
}
