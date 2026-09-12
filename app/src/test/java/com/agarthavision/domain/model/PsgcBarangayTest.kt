package com.agarthavision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PsgcBarangay.parentPath], the picker's secondary line.
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
}
