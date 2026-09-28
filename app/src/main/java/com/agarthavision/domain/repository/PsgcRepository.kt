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
     * prefix matches first. Capped at [limit] because there are 42,010 barangays and a
     * short query matches thousands.
     */
    suspend fun searchBarangays(query: String, limit: Int): List<PsgcBarangay>

    /**
     * The barangay for a stored [code], or null when no row matches.
     *
     * The reverse of what the picker does, and the first thing in the app to need it:
     * sessions have stored a `psgc_barangay_code` since `0010` and rendered it nowhere, so
     * the DAO has had `getByCode` all along with nothing exposing it. The patient list is
     * the first screen that has to turn a stored code back into a name.
     *
     * **Nullable on purpose.** A PSGC vintage change can retire a code that a patient row
     * still carries. The caller renders the raw code in that case rather than crashing or
     * showing a blank where a barangay should be.
     */
    suspend fun getBarangay(code: String): PsgcBarangay?
}
