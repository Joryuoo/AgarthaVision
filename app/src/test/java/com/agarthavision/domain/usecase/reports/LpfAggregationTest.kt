package com.agarthavision.domain.usecase.reports

import com.agarthavision.data.local.entity.SampleSpeciesFindingEntity
import com.agarthavision.domain.model.LpfDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-species LPF range, and the four ways of getting it wrong (PB-17).
 *
 * 86d4a6jxw's build spec defined the session figure as a mean over recorded frames, and left
 * "mean, or the highest single field?" open. Consultation settled it as neither: a **min–max
 * range** across the fields examined, with the qualitative descriptor read off the worst field.
 * Each test below is a distinct failure mode rather than a variation on one.
 */
class LpfAggregationTest {

    private var next = 0

    private fun finding(sampleId: String, species: String, eggCount: Int) =
        SampleSpeciesFindingEntity(
            findingId = "f${next++}",
            sampleId = sampleId,
            species = species,
            stage = null,
            eggCount = eggCount,
        )

    @Test
    fun `the fields holding none of a species pull the minimum to zero`() {
        // Ten fields, Ascaris in three of them.
        val findings = listOf(
            finding("s1", "Ascaris lumbricoides", 1),
            finding("s2", "Ascaris lumbricoides", 4),
            finding("s3", "Ascaris lumbricoides", 2),
        )

        val ascaris = aggregateLpfPerSpecies(findings, fieldCount = 10)["Ascaris lumbricoides"]!!

        // 0-4, not 1-4. The seven clean fields are zeros, not gaps: dropping them and taking
        // the smallest non-zero count would overstate every result on every report.
        assertEquals(0, ascaris.min)
        assertEquals(4, ascaris.max)
        assertEquals(LpfDescriptor.FEW, ascaris.descriptor)
    }

    @Test
    fun `a species present in every field keeps its real minimum`() {
        // The other half of the same rule. With no empty field there is no zero to pull down
        // to, and a range that always started at 0 would say nothing.
        val findings = listOf(
            finding("s1", "Hookworm", 3),
            finding("s2", "Hookworm", 5),
        )

        val hookworm = aggregateLpfPerSpecies(findings, fieldCount = 2)["Hookworm"]!!

        assertEquals(3, hookworm.min)
        assertEquals(5, hookworm.max)
    }

    @Test
    fun `ten clean fields are a real result, not an absence`() {
        // The most common outcome in surveillance, and the one a report is most often needed
        // for. No species rows, which the caller renders as "no parasites found" - never as an
        // empty state and never as a reason to produce no report at all.
        val result = aggregateLpfPerSpecies(emptyList(), fieldCount = 10)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `two species in one field are two independent ranges`() {
        val findings = listOf(
            finding("s1", "Ascaris lumbricoides", 2),
            finding("s1", "Hookworm", 1),
            finding("s2", "Ascaris lumbricoides", 3),
        )

        val result = aggregateLpfPerSpecies(findings, fieldCount = 2)

        // Not one combined figure: polyparasitism is the case the findings table exists for.
        assertEquals(2, result["Ascaris lumbricoides"]!!.min)
        assertEquals(3, result["Ascaris lumbricoides"]!!.max)
        assertEquals(0, result["Hookworm"]!!.min)
        assertEquals(1, result["Hookworm"]!!.max)
    }

    @Test
    fun `one heavy field among nine clean is read off the heavy one`() {
        val findings = listOf(finding("s10", "Trichuris trichiura", 12))

        val trichuris = aggregateLpfPerSpecies(findings, fieldCount = 10)["Trichuris trichiura"]!!

        // The whole reason the descriptor comes from the max. A mean would call this 1.2 an
        // hour after a medtech looked at a field full of eggs.
        assertEquals(0, trichuris.min)
        assertEquals(12, trichuris.max)
        assertEquals(LpfDescriptor.NUMEROUS, trichuris.descriptor)
    }

    @Test
    fun `several rows for one species in one field are one field's count`() {
        // The findings table allows more than one row per species per frame; the field's count
        // is their sum, not the larger of them, or a heavy field would read as two light ones.
        val findings = listOf(
            finding("s1", "Ascaris lumbricoides", 4),
            finding("s1", "Ascaris lumbricoides", 3),
        )

        val ascaris = aggregateLpfPerSpecies(findings, fieldCount = 1)["Ascaris lumbricoides"]!!

        assertEquals(7, ascaris.min)
        assertEquals(7, ascaris.max)
    }

    @Test
    fun `a species never seen has no descriptor at all`() {
        // Null rather than a fifth "none" band: naming a descriptor for a species with no eggs
        // in any field is saying something about an organism the medtech did not find.
        assertNull(LpfDescriptor.forMax(0))
    }

    @Test
    fun `the descriptor bands are the ones PB-17's examples imply`() {
        assertEquals(LpfDescriptor.RARE, LpfDescriptor.forMax(1))
        assertEquals(LpfDescriptor.RARE, LpfDescriptor.forMax(LpfDescriptor.RARE_MAX))
        assertEquals(LpfDescriptor.FEW, LpfDescriptor.forMax(LpfDescriptor.RARE_MAX + 1))
        // The ticket's own worked examples: a worst field of 4 is "few", one of 12 is
        // "numerous". Both boundaries fall out of these bands.
        assertEquals(LpfDescriptor.FEW, LpfDescriptor.forMax(4))
        assertEquals(LpfDescriptor.MODERATE, LpfDescriptor.forMax(LpfDescriptor.MODERATE_MAX))
        assertEquals(LpfDescriptor.NUMEROUS, LpfDescriptor.forMax(LpfDescriptor.MODERATE_MAX + 1))
        assertEquals(LpfDescriptor.NUMEROUS, LpfDescriptor.forMax(12))
    }

    @Test
    fun `a field count smaller than the findings cover cannot invent a minimum`() {
        // Defensive: if the caller counts fewer fields than the findings actually span, the
        // two disagree. Taking the larger is the reading that cannot turn a miscount into a
        // non-zero minimum on a clinical report.
        val findings = listOf(
            finding("s1", "Hookworm", 2),
            finding("s2", "Hookworm", 6),
            finding("s3", "Hookworm", 4),
        )

        val hookworm = aggregateLpfPerSpecies(findings, fieldCount = 1)["Hookworm"]!!

        assertEquals(2, hookworm.min)
        assertEquals(6, hookworm.max)
    }
}
