package com.agarthavision.data.local.psgc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * Unit tests for [PsgcCsvParser] — pure JVM, no device, no Room, no asset manager.
 *
 * The shapes covered here are the ones the real dataset actually contains and that a
 * naive split(",") would corrupt: 133 barangay names carry commas, 3,083 barangays have no
 * province, 897 carry a sub-municipality in `search_extra`, and 439 contain `ñ`.
 */
class PsgcCsvParserTest {

    private val header =
        "code,name,city_muni_code,city_muni_name,province_code,province_name," +
            "region_code,region_name,search_extra"

    private fun parse(vararg rows: String) =
        PsgcCsvParser.parse(StringReader((listOf(header) + rows).joinToString("\n")))

    @Test
    fun `maps a barangay in a province`() {
        val barangay = parse(
            "0102801001,Adams,0102801000,Adams,0102800000,Ilocos Norte,0100000000,Region I,",
        ).single()

        assertEquals("0102801001", barangay.code)
        assertEquals("Adams", barangay.name)
        assertEquals("Adams", barangay.cityMuniName)
        assertEquals("Ilocos Norte", barangay.provinceName)
        assertEquals("Region I", barangay.regionName)
    }

    @Test
    fun `keeps a quoted name containing a comma intact`() {
        val barangay = parse(
            "\"0102801002\",\"Bgy. No. 42, Apaya\",0102801000,Adams,0102800000," +
                "Ilocos Norte,0100000000,Region I,",
        ).single()

        assertEquals("Bgy. No. 42, Apaya", barangay.name)
        assertEquals("Adams", barangay.cityMuniName)
    }

    @Test
    fun `reads a doubled quote as a literal quote`() {
        val barangay = parse(
            "0102801003,\"Sitio \"\"Bato\"\", Norte\",0102801000,Adams,0102800000," +
                "Ilocos Norte,0100000000,Region I,",
        ).single()

        assertEquals("Sitio \"Bato\", Norte", barangay.name)
    }

    @Test
    fun `nulls an empty province rather than storing a blank`() {
        val barangay = parse(
            "0730600001,Adlaon,0730600000,City of Cebu,,,0700000000,Region VII,",
        ).single()

        assertNull(barangay.provinceCode)
        assertNull(barangay.provinceName)
    }

    @Test
    fun `folds search text to lower case including non-ascii`() {
        val barangay = parse(
            "0102801004,Santo Niño,0102801000,Adams,0102800000,Ilocos Norte,0100000000,Region I,",
        ).single()

        assertTrue(barangay.searchText.contains("santo niño"))
    }

    @Test
    fun `search text carries the parents but not the region`() {
        val barangay = parse(
            "0102801005,Bani,0102801000,Adams,0102800000,Ilocos Norte,0100000000,Region I,",
        ).single()

        assertEquals("bani adams ilocos norte", barangay.searchText)
    }

    @Test
    fun `search text carries a sub-municipality so it stays findable`() {
        val barangay = parse(
            "1380601001,Barangay 1,1380600000,City of Manila,,,1300000000,NCR,Tondo I/II",
        ).single()

        assertTrue(barangay.searchText.contains("tondo i/ii"))
        assertEquals("City of Manila", barangay.cityMuniName)
    }

    @Test
    fun `skips the header and blank lines`() {
        val barangays = parse(
            "0102801001,Adams,0102801000,Adams,0102800000,Ilocos Norte,0100000000,Region I,",
            "",
            "0102801002,Bani,0102801000,Adams,0102800000,Ilocos Norte,0100000000,Region I,",
        )

        assertEquals(listOf("Adams", "Bani"), barangays.map { it.name })
    }

    @Test
    fun `rejects a row with the wrong column count`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parse("0102801001,Adams,0102801000,Adams")
        }

        assertTrue(error.message!!.contains("line 2"))
    }

    @Test
    fun `rejects a row missing its city or municipality`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parse("0102801001,Adams,0102801000,,0102800000,Ilocos Norte,0100000000,Region I,")
        }

        assertTrue(error.message!!.contains("0102801001"))
    }

    @Test
    fun `rejects a nameless row`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("1303901906,,1303901000,Manila,,,1300000000,NCR,")
        }
    }
}
