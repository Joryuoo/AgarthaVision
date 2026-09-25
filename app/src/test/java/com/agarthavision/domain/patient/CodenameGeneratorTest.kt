package com.agarthavision.domain.patient

import com.agarthavision.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CodenameGeneratorTest {

    private val referenceToday = LocalDate.of(2026, 9, 22)

    @Test
    fun `isCodename correctly validates format`() {
        // legacy numbered formats
        assertTrue(CodenameGenerator.isCodename("VISION-M22-001"))
        assertTrue(CodenameGenerator.isCodename("VISION-M24-001"))
        assertTrue(CodenameGenerator.isCodename("VISION-F05-002"))
        assertTrue(CodenameGenerator.isCodename("M24-001"))
        assertTrue(CodenameGenerator.isCodename("F05-002"))
        assertTrue(CodenameGenerator.isCodename("VISION-M00-1234"))
        // new word-stacked formats
        assertTrue(CodenameGenerator.isCodename("ALPHA-F22"))
        assertTrue(CodenameGenerator.isCodename("ALPHATEKNOY-F22"))
        assertTrue(CodenameGenerator.isCodename("VISION-M22"))
        // bare new format (no word prefix)
        assertTrue(CodenameGenerator.isCodename("M24"))
        // not codenames
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
    fun `generate with empty list produces valid codename matching regex`() {
        val codename = CodenameGenerator.generate(
            sex = Sex.MALE,
            birthdate = LocalDate.of(2002, 1, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
        )
        // Must end with the bucket prefix
        assertTrue(
            "Expected codename to end with -M24, got: $codename",
            codename.endsWith("-M24"),
        )
        // Must be a valid codename
        assertTrue(
            "Expected isCodename to return true for: $codename",
            CodenameGenerator.isCodename(codename),
        )
    }

    @Test
    fun `generate with empty list for female produces valid codename`() {
        val codename = CodenameGenerator.generate(
            sex = Sex.FEMALE,
            birthdate = LocalDate.of(2021, 1, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
        )
        assertTrue(
            "Expected codename to end with -F05, got: $codename",
            codename.endsWith("-F05"),
        )
        assertTrue(CodenameGenerator.isCodename(codename))
    }

    @Test
    fun `generate is deterministic for the same seed`() {
        val seed = 42L
        val first = CodenameGenerator.generate(
            sex = Sex.MALE,
            birthdate = LocalDate.of(2002, 1, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
            random = kotlin.random.Random(seed),
        )
        val second = CodenameGenerator.generate(
            sex = Sex.MALE,
            birthdate = LocalDate.of(2002, 1, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
            random = kotlin.random.Random(seed),
        )
        assertEquals("Same seed must produce same codename", first, second)
    }

    @Test
    fun `generate stacks a word on collision`() {
        val seed = 42L
        // Determine the first word drawn by this seed with an empty list
        val firstResult = CodenameGenerator.generate(
            sex = Sex.FEMALE,
            birthdate = LocalDate.of(2004, 6, 1),
            existingCodenames = emptyList(),
            today = referenceToday,
            random = kotlin.random.Random(seed),
        )
        // firstResult is something like "BRAVO-F22"
        // Now call generate with that candidate already taken, using the same seed
        val stacked = CodenameGenerator.generate(
            sex = Sex.FEMALE,
            birthdate = LocalDate.of(2004, 6, 1),
            existingCodenames = listOf(firstResult),
            today = referenceToday,
            random = kotlin.random.Random(seed),
        )
        assertNotEquals("Stacked result must differ from the taken codename", firstResult, stacked)
        assertTrue(
            "Stacked result must end with the same bucket suffix",
            stacked.endsWith("-F22"),
        )
        // Stacked word segment is longer (original word + at least one more word, no separator)
        val firstWord = firstResult.substringBefore('-')
        assertTrue(
            "Stacked word segment must start with the first word: got $stacked",
            stacked.startsWith(firstWord),
        )
        assertTrue(
            "Stacked result must be longer than the first result",
            stacked.length > firstResult.length,
        )
        assertTrue(CodenameGenerator.isCodename(stacked))
    }

    @Test
    fun `generate stacks through multiple collisions producing a third unique candidate`() {
        // Derive deterministic first and second candidates so we can pre-populate both as taken.
        val seed = 7L
        val bucket = LocalDate.of(2004, 6, 1) // F22 bucket

        val first = CodenameGenerator.generate(
            sex = Sex.FEMALE, birthdate = bucket, existingCodenames = emptyList(),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        val second = CodenameGenerator.generate(
            sex = Sex.FEMALE, birthdate = bucket, existingCodenames = listOf(first),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        // second must itself be longer than first (two words stacked)
        assertTrue(
            "second must be longer than first: first=$first second=$second",
            second.length > first.length,
        )
        // third call: both first and second are taken
        val third = CodenameGenerator.generate(
            sex = Sex.FEMALE, birthdate = bucket, existingCodenames = listOf(first, second),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        assertNotEquals("third must differ from first", first, third)
        assertNotEquals("third must differ from second", second, third)
        assertTrue("third must be longer than second: second=$second third=$third", third.length > second.length)
        assertTrue("third must be a valid codename: $third", CodenameGenerator.isCodename(third))
        assertTrue("third must end with -F22", third.endsWith("-F22"))
    }

    @Test
    fun `generate terminates and produces unique result with many pre-existing in same bucket`() {
        // 10 pre-existing entries in the M24 bucket; generate() must exit and not be any of them.
        val birthdate = LocalDate.of(2002, 9, 22) // M24 on referenceToday
        val pool = listOf("ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO",
            "FOXTROT", "GOLF", "HOTEL", "INDIA", "JULIETT")
        val existing = pool.map { "$it-M24" }

        // Use a different seed each time to avoid dependence on a specific word draw.
        for (seed in 1L..5L) {
            val result = CodenameGenerator.generate(
                sex = Sex.MALE, birthdate = birthdate, existingCodenames = existing,
                today = referenceToday, random = kotlin.random.Random(seed),
            )
            assertFalse("result must not be any of the pre-existing: $result", result in existing)
            assertTrue("result must be a valid codename: $result", CodenameGenerator.isCodename(result))
            assertTrue("result must end with -M24", result.endsWith("-M24"))
        }
    }

    @Test
    fun `generate determinism - two independent same-seed calls with collision produce the same result`() {
        val seed = 99L
        val birthdate = LocalDate.of(2002, 9, 22)
        // Pre-populate with the no-collision first draw to force a collision path.
        val firstDraw = CodenameGenerator.generate(
            sex = Sex.MALE, birthdate = birthdate, existingCodenames = emptyList(),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        val callA = CodenameGenerator.generate(
            sex = Sex.MALE, birthdate = birthdate, existingCodenames = listOf(firstDraw),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        val callB = CodenameGenerator.generate(
            sex = Sex.MALE, birthdate = birthdate, existingCodenames = listOf(firstDraw),
            today = referenceToday, random = kotlin.random.Random(seed),
        )
        assertEquals("Same seed + same taken set must produce identical result on collision path", callA, callB)
    }

    @Test
    fun `isCodename rejects whitespace-only string`() {
        assertFalse(CodenameGenerator.isCodename("   "))
    }

    @Test
    fun `isCodename rejects trailing dash F22-`() {
        assertFalse(CodenameGenerator.isCodename("F22-"))
    }

    @Test
    fun `isCodename rejects ALPHA- with no sexage`() {
        assertFalse(CodenameGenerator.isCodename("ALPHA-"))
    }

    @Test
    fun `isCodename rejects lowercase alpha-f22`() {
        // Codenames are always generated uppercase; a lowercase stored value is not a codename.
        assertFalse(CodenameGenerator.isCodename("alpha-f22"))
    }

    @Test
    fun `isCodename rejects two-digit numeric suffix F22-01`() {
        // The legacy format requires at least 3 digits in the numeric suffix.
        assertFalse(CodenameGenerator.isCodename("F22-01"))
    }

    @Test
    fun `isCodename rejects bare single-digit age M2`() {
        // Age token must be exactly two digits.
        assertFalse(CodenameGenerator.isCodename("M2"))
    }

    @Test
    fun `false positive in existingCodenames list does not cause crash or incorrect result`() {
        // A named-patient lastname that happens to end in "-M24" (e.g. from a LIKE false-positive
        // in getExistingCodenamesByPrefix) must not crash generate() — it just adds a non-matching
        // entry to the taken set, which is harmless because "De-M24" != "WORD-M24".
        val falsePositive = "De-M24"
        val birthdate = LocalDate.of(2002, 9, 22)
        val result = CodenameGenerator.generate(
            sex = Sex.MALE, birthdate = birthdate,
            existingCodenames = listOf(falsePositive),
            today = referenceToday, random = kotlin.random.Random(1L),
        )
        assertTrue("Result must be a valid codename: $result", CodenameGenerator.isCodename(result))
        assertTrue("Result must end with -M24", result.endsWith("-M24"))
        // The false positive does NOT collide unless the drawn candidate happens to literally
        // equal "De-M24", which cannot happen (pool words contain no lowercase letters or hyphens
        // within the word segment).
        assertNotEquals(
            "Result must not equal the false-positive string",
            falsePositive, result,
        )
    }
}
