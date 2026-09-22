package com.agarthavision.domain.patient

import com.agarthavision.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CodenameGeneratorTest {

    private val referenceToday = LocalDate.of(2026, 9, 22)

    @Test
    fun `isCodename correctly validates format`() {
        assertTrue(CodenameGenerator.isCodename("M24-001"))
        assertTrue(CodenameGenerator.isCodename("F05-002"))
        assertTrue(CodenameGenerator.isCodename("M00-1234"))
        assertFalse(CodenameGenerator.isCodename("Cruz"))
        assertFalse(CodenameGenerator.isCodename("Cruz, Gerald"))
        assertFalse(CodenameGenerator.isCodename("M2-001"))
        assertFalse(CodenameGenerator.isCodename("X24-001"))
        assertFalse(CodenameGenerator.isCodename(null))
        assertFalse(CodenameGenerator.isCodename(""))
    }

    @Test
    fun `bucketPrefix computes sex and two digit age`() {
        val male24 = CodenameGenerator.bucketPrefix(
            Sex.MALE,
            LocalDate.of(2002, 9, 22),
            today = referenceToday,
        )
        assertEquals("M24", male24)

        val female5 = CodenameGenerator.bucketPrefix(
            Sex.FEMALE,
            LocalDate.of(2021, 9, 22),
            today = referenceToday,
        )
        assertEquals("F05", female5)

        val infant0 = CodenameGenerator.bucketPrefix(
            Sex.MALE,
            LocalDate.of(2026, 5, 1),
            today = referenceToday,
        )
        assertEquals("M00", infant0)
    }

    @Test
    fun `nextPatientNumber increments from highest sequence`() {
        val existing = listOf("M24-001", "M24-002", "M24-005")
        val next = CodenameGenerator.nextPatientNumber("M24", existing)
        assertEquals(6, next)
    }

    @Test
    fun `nextPatientNumber returns 1 when bucket is empty`() {
        val existing = listOf("F30-001", "M25-002")
        val next = CodenameGenerator.nextPatientNumber("M24", existing)
        assertEquals(1, next)
    }

    @Test
    fun `generate produces standard codename format`() {
        val codename = CodenameGenerator.generate(
            sex = Sex.MALE,
            birthdate = LocalDate.of(2002, 1, 1),
            existingCodenames = listOf("M24-001"),
            today = referenceToday,
        )
        assertEquals("M24-002", codename)
    }

    @Test
    fun `generate handles first patient in bucket`() {
        val codename = CodenameGenerator.generate(
            sex = Sex.FEMALE,
            birthdate = LocalDate.of(2021, 1, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
        )
        assertEquals("F05-001", codename)
    }
}
