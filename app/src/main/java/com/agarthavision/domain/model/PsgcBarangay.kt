package com.agarthavision.domain.model

/**
 * A barangay from the Philippine Standard Geographic Code — the unit of analysis for STH
 * surveillance mapping.
 *
 * [code] is the canonical zero-padded 10-digit PSGC and is the only field that leaves the
 * app: it is what a session stores and what the Admin Website's choropleth joins on. The
 * names exist so the picker can be read and searched.
 *
 * A barangay code resolves upward to city/municipality, province and region through the code
 * itself, which is why a session stores this one value rather than four denormalised columns.
 *
 * See `docs/map/objects/PsgcBarangay.md`.
 */
data class PsgcBarangay(
    val code: String,
    val name: String,
    val cityMuniName: String,
    /**
     * Null for barangays in highly urbanised and independent cities — those cities occupy
     * the province slot themselves, so PSGC assigns them no province.
     */
    val provinceName: String?,
    val regionName: String,
) {
    /**
     * Where the barangay sits, for the picker's secondary line — "City of Cebu · Region VII
     * (Central Visayas)" for a chartered city, "Adams · Ilocos Norte" elsewhere.
     */
    val parentPath: String
        get() = listOfNotNull(cityMuniName, provinceName ?: regionName).joinToString(separator = " · ")
}
