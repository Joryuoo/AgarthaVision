package com.agarthavision.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IslandGroupTest {

    @Test
    fun `every real PSGC region prefix resolves to the expected island group`() {
        val expected = mapOf(
            "01" to IslandGroup.LUZON,
            "02" to IslandGroup.LUZON,
            "03" to IslandGroup.LUZON,
            "04" to IslandGroup.LUZON,
            "05" to IslandGroup.LUZON,
            "13" to IslandGroup.LUZON,
            "14" to IslandGroup.LUZON,
            "17" to IslandGroup.LUZON,
            "06" to IslandGroup.VISAYAS,
            "07" to IslandGroup.VISAYAS,
            "08" to IslandGroup.VISAYAS,
            "18" to IslandGroup.VISAYAS,
            "09" to IslandGroup.MINDANAO,
            "10" to IslandGroup.MINDANAO,
            "11" to IslandGroup.MINDANAO,
            "12" to IslandGroup.MINDANAO,
            "16" to IslandGroup.MINDANAO,
            "19" to IslandGroup.MINDANAO,
        )

        assertEquals(18, expected.size)

        expected.forEach { (prefix, group) ->
            val code = prefix + "00000000"
            assertEquals("region prefix $prefix", group, IslandGroup.fromRegionCode(code))
        }
    }

    @Test
    fun `an unknown region prefix throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            IslandGroup.fromRegionCode("9900000000")
        }
    }

    @Test
    fun `prefix 00 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            IslandGroup.fromRegionCode("0000000000")
        }
    }
}
