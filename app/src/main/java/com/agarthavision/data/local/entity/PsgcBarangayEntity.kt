package com.agarthavision.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for one PSGC barangay — the offline reference data behind the session
 * barangay picker.
 *
 * Read-only after seeding. This is bundled reference data, not clinical data: it has no
 * Supabase mirror, nothing syncs it, and `sessions.psgc_barangay_code` stores the [code]
 * rather than a foreign key, so the surveillance map keys on PSGC without this table
 * needing to exist server-side. See `docs/map/objects/PsgcBarangay.md`.
 *
 * [code] is the canonical zero-padded 10-digit PSGC (`0102801001`), which is the form the
 * Admin Website's boundary GeoJSON is joined on. Never store the numerically-shortened
 * form upstream ships.
 *
 * Deliberately carries no index. Every query is either a primary-key lookup or the
 * infix `LIKE` in [com.agarthavision.data.local.dao.PsgcBarangayDao.search], and a
 * leading-wildcard `LIKE` cannot use an index — one would cost APK size and seed time to
 * buy nothing.
 */
@Entity(tableName = "psgc_barangays")
data class PsgcBarangayEntity(
    @PrimaryKey
    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "city_muni_code")
    val cityMuniCode: String,

    @ColumnInfo(name = "city_muni_name")
    val cityMuniName: String,

    /**
     * Null for the 3,083 barangays in highly urbanised and independent cities. Those
     * cities occupy the province slot themselves, so PSGC gives them no province — this
     * is the classification's shape, not missing data.
     */
    @ColumnInfo(name = "province_code")
    val provinceCode: String?,

    @ColumnInfo(name = "province_name")
    val provinceName: String?,

    @ColumnInfo(name = "region_code")
    val regionCode: String,

    @ColumnInfo(name = "region_name")
    val regionName: String,

    /**
     * Pre-lowercased haystack for [com.agarthavision.data.local.dao.PsgcBarangayDao.search]:
     * barangay, city/municipality and province names, plus Manila's sub-municipality.
     *
     * Lowercased in Kotlin rather than by SQL `lower()`, which folds ASCII only — 439
     * barangay names contain `ñ`. Region names are deliberately left out: they are
     * boilerplate ("Region I (Ilocos Region)"), so indexing them would make the word
     * "region" match almost every row.
     */
    @ColumnInfo(name = "search_text")
    val searchText: String,
)
