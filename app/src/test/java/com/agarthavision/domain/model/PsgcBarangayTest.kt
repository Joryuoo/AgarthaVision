package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PsgcBarangay.parentPath], the picker's secondary line, and
 * [PsgcBarangay.fullAddress], the single display form for a patient's location.
 */
class PsgcBarangayTest {

    @Test
    fun `parent path shows the province when there is one`() {
        val barangay = PsgcBarangay(
            code = "0102801001",
            name = "Adams",
            cityMuniName = "Adams",
            provinceName = "Ilocos Norte",
            regionName = "Region I (Ilocos Region)",
        )

        assertEquals("Adams · Ilocos Norte", barangay.parentPath)
    }

    @Test
    fun `parent path falls back to the region for a chartered city`() {
        val barangay = PsgcBarangay(
            code = "0730600001",
            name = "Adlaon",
            cityMuniName = "City of Cebu",
            provinceName = null,
            regionName = "Region VII (Central Visayas)",
        )

        assertEquals("City of Cebu · Region VII (Central Visayas)", barangay.parentPath)
    }

    @Test
    fun `full address includes the province when there is one`() {
        val barangay = PsgcBarangay(
            code = "0102801001",
            name = "Adams",
            cityMuniName = "Adams",
            provinceName = "Ilocos Norte",
            regionName = "Region I (Ilocos Region)",
        )

        assertEquals("Adams, Adams, Ilocos Norte", barangay.fullAddress)
    }

    @Test
    fun `full address omits the province for a chartered city`() {
        val barangay = PsgcBarangay(
            code = "0730600001",
            name = "Adlaon",
            cityMuniName = "City of Cebu",
            provinceName = null,
            regionName = "Region VII (Central Visayas)",
        )

        assertEquals("Adlaon, City of Cebu", barangay.fullAddress)
    }
}
