package com.agarthavision.domain.repository

import com.agarthavision.domain.model.PsgcBarangay

/**
 * Read access to the bundled PSGC barangay reference data.
 *
 * Always local. The dataset ships in the APK and is Room-seeded, so every call here works
 * with the radio off — that is the point, since medtechs collect in areas with no signal.
 */
interface PsgcRepository {
    /**
     * Barangays matching [query] anywhere in their name, city/municipality or province,
     * prefix matches first. Capped at [limit] because there are 42,001 barangays and a
     * short query matches thousands.
     */
    suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay>
}
