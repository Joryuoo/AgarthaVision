package com.agarthavision.domain.model

import com.agarthavision.data.local.mapper.toDomain
import com.agarthavision.data.local.mapper.toEntity
import com.agarthavision.ui.records.isBinomial
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What a stored report says about a session, and keeps saying (PB-18, PB-20).
 *
 * **A report is a snapshot.** The range is persisted rather than recomputed on read, so
 * correcting a sample afterwards does not rewrite a document that has already gone out. The
 * descriptor is the one part that *is* derived — from `max`, which is itself stored — so it
 * cannot drift from the range beside it and needs no column of its own.
 */
class LpfReportSnapshotTest {

    private val gson = Gson()

    private fun report(lpf: Map<String, LpfDensity>) = Report(
        id = "rpt-1",
        sessionId = "session-1",
        userId = "user-1",
        reportType = ReportType.SESSION,
        generatedAt = Instant.ofEpochMilli(1_000L),
        totalSamples = 10,
        totalEggsConfirmed = 7,
        positiveSpecies = lpf.filterValues { it.max > 0 }.keys.sorted(),
        lpfPerSpecies = lpf,
        csvFilePath = null,
        pdfFilePath = null,
        supabaseStatus = ReportSyncStatus.PENDING,
    )

    @Test
    fun `a stored report keeps the range it was generated with`() {
        val original = report(mapOf("Ascaris lumbricoides" to LpfDensity(min = 0, max = 4)))

        val restored = original.toEntity(gson).toDomain(gson)

        val ascaris = restored.lpfPerSpecies["Ascaris lumbricoides"]!!
        assertEquals(0, ascaris.min)
        assertEquals(4, ascaris.max)
    }

    @Test
    fun `the descriptor survives the round trip without being stored`() {
        val original = report(mapOf("Trichuris trichiura" to LpfDensity(min = 0, max = 12)))

        val restored = original.toEntity(gson).toDomain(gson)

        // Derived from the stored max, so there is no column to migrate and no way for the two
        // to disagree - which is the failure a persisted descriptor would eventually produce.
        assertEquals(LpfDescriptor.NUMEROUS, restored.lpfPerSpecies["Trichuris trichiura"]!!.descriptor)
    }

    @Test
    fun `a wholly negative session stores a report with no species rows`() {
        val original = report(emptyMap())

        val restored = original.toEntity(gson).toDomain(gson)

        // Not an error and not an empty state: ten clean fields are the most common outcome in
        // surveillance, and the screen and the PDF both say "no parasites found" for it.
        assertTrue(restored.lpfPerSpecies.isEmpty())
        assertTrue(restored.positiveSpecies.isEmpty())
    }

    @Test
    fun `a species never seen carries no reading`() {
        assertNull(LpfDensity(min = 0, max = 0).descriptor)
    }

    @Test
    fun `binomials are italic and common names are not`() {
        // C11: species names render as italic binomials. "Hookworm" is a common name covering
        // two genera, so italicising it would be wrong in the one place type carries meaning.
        assertTrue("Ascaris lumbricoides".isBinomial())
        assertTrue("Trichuris trichiura".isBinomial())
        assertFalse(EggSpecies.HOOKWORM.displayName.isBinomial())
    }
}
