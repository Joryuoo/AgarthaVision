package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.LpfDensity
import com.agarthavision.domain.model.LpfDescriptor
import com.agarthavision.domain.model.ParasiteBurdenLevel
import com.agarthavision.domain.model.PatientReportPdfDocument
import com.agarthavision.domain.model.PatientReportPdfHeader
import com.agarthavision.domain.model.PatientReportSessionRow
import com.agarthavision.domain.model.ReportPdfSampleItem
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sex
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowPdfDocument::class])
class AndroidPatientReportPdfRendererTest {

    private lateinit var context: Context
    private lateinit var renderer: AndroidPatientReportPdfRenderer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        renderer = AndroidPatientReportPdfRenderer(context)
    }

    @Test
    fun `render single-page patient summary with negative smear produces 1 page`() = runTest {
        val sessionRows = listOf(
            PatientReportSessionRow(
                sessionId = "sess-001",
                sessionLabel = "SESSION 1",
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                sampleCount = 5,
                eggsConfirmed = 0,
                positiveSpecies = emptyList(),
                lpfPerSpecies = emptyMap(),
            ),
        )
        val doc = createPatientDocument(
            speciesRows = emptyList(),
            sessionRows = sessionRows,
            appendixSamples = emptyList(),
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render single-page patient summary with positive findings produces 1 page`() = runTest {
        val speciesRows = listOf(
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.ASCARIS.displayName,
                min = 0,
                max = 3,
                descriptor = LpfDescriptor.FEW,
                burdenLevel = ParasiteBurdenLevel.LOW,
            ),
        )
        val sessionRows = listOf(
            PatientReportSessionRow(
                sessionId = "sess-001",
                sessionLabel = "SESSION 1",
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                sampleCount = 5,
                eggsConfirmed = 3,
                positiveSpecies = listOf(EggSpecies.ASCARIS.displayName),
                lpfPerSpecies = mapOf(EggSpecies.ASCARIS.displayName to LpfDensity(min = 0, max = 3)),
            ),
            PatientReportSessionRow(
                sessionId = "sess-002",
                sessionLabel = "SESSION 2",
                startedAt = Instant.parse("2026-11-02T09:00:00Z"),
                sampleCount = 5,
                eggsConfirmed = 0,
                positiveSpecies = emptyList(),
                lpfPerSpecies = emptyMap(),
            ),
        )
        val doc = createPatientDocument(
            speciesRows = speciesRows,
            sessionRows = sessionRows,
            appendixSamples = emptyList(),
            organizationName = "St. Jude Clinic",
            generatedByEmail = "medtech@stjude.org",
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render paginated patient report across many session rows spans multiple pages`() = runTest {
        val sessionRows = (1..35).map { i ->
            PatientReportSessionRow(
                sessionId = "sess-%03d-long-uuid".format(i),
                sessionLabel = "SESSION $i",
                startedAt = Instant.parse("2026-10-01T08:00:00Z").plusSeconds((i * 86400).toLong()),
                sampleCount = 5,
                eggsConfirmed = i % 3,
                positiveSpecies = if (i % 3 > 0) listOf(EggSpecies.ASCARIS.displayName) else emptyList(),
                lpfPerSpecies = if (i % 3 > 0) {
                    mapOf(EggSpecies.ASCARIS.displayName to LpfDensity(min = 0, max = i % 3))
                } else {
                    emptyMap()
                },
            )
        }
        val doc = createPatientDocument(
            speciesRows = listOf(
                ReportPdfSpeciesRow(
                    speciesDisplayName = EggSpecies.ASCARIS.displayName,
                    min = 0,
                    max = 2,
                    descriptor = LpfDescriptor.RARE,
                    burdenLevel = ParasiteBurdenLevel.LOW,
                ),
            ),
            sessionRows = sessionRows,
            appendixSamples = emptyList(),
        )
        val bytes = renderer.render(doc)

        // 35 rows plus hero card and letterhead will exceed single page height and paginate to 2 pages
        assertValidPdf(bytes, expectedPages = 2)
    }

    @Test
    fun `render patient report with 5 appendix samples adds 2 appendix pages`() = runTest {
        val sessionRows = listOf(
            PatientReportSessionRow(
                sessionId = "sess-001",
                sessionLabel = "SESSION 1",
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                sampleCount = 5,
                eggsConfirmed = 2,
                positiveSpecies = listOf(EggSpecies.ASCARIS.displayName),
                lpfPerSpecies = mapOf(EggSpecies.ASCARIS.displayName to LpfDensity(min = 0, max = 2)),
            ),
        )
        val samples = (1..5).map { i ->
            ReportPdfSampleItem(
                sampleId = "sample-$i",
                sessionLabel = "SESSION 1",
                fieldIndex = i,
                imagePath = null,
                eggCount = i,
                findingsSummary = "${EggSpecies.ASCARIS.displayName} $i LPF",
            )
        }
        val doc = createPatientDocument(
            sessionRows = sessionRows,
            appendixSamples = samples,
        )
        val bytes = renderer.render(doc)

        // 1 summary page + 2 appendix pages (ceil(5/4) = 2) = 3 total pages
        assertValidPdf(bytes, expectedPages = 3)
    }

    @Test
    fun `render paginated session table with appendix samples appends appendix pages correctly`() = runTest {
        val sessionRows = (1..35).map { i ->
            PatientReportSessionRow(
                sessionId = "sess-%03d".format(i),
                sessionLabel = "SESSION $i",
                startedAt = Instant.parse("2026-10-01T08:00:00Z").plusSeconds((i * 86400).toLong()),
                sampleCount = 5,
                eggsConfirmed = 0,
                positiveSpecies = emptyList(),
                lpfPerSpecies = emptyMap(),
            )
        }
        val samples = (1..5).map { i ->
            ReportPdfSampleItem(
                sampleId = "sample-$i",
                sessionLabel = "SESSION 1",
                fieldIndex = i,
                imagePath = null,
                eggCount = 1,
                findingsSummary = "${EggSpecies.ASCARIS.displayName} 1 LPF",
            )
        }
        val doc = createPatientDocument(
            sessionRows = sessionRows,
            appendixSamples = samples,
        )
        val bytes = renderer.render(doc)

        // 2 summary pages + 2 appendix pages = 4 total pages
        assertValidPdf(bytes, expectedPages = 4)
    }

    @Test
    fun `render patient report with various scope date combinations succeeds`() = runTest {
        val combinations = listOf(
            LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 1, 1) to null,
            null to LocalDate.of(2026, 1, 31),
            null to null,
        )
        for ((start, end) in combinations) {
            val doc = createPatientDocument(
                scopeStart = start,
                scopeEnd = end,
                sessionRows = emptyList(),
            )
            val bytes = renderer.render(doc)
            assertValidPdf(bytes, expectedPages = 1)
        }
    }

    @Test
    fun `render session row with null sessionLabel and missing density formats correctly`() = runTest {
        val sessionRows = listOf(
            PatientReportSessionRow(
                sessionId = "1234567890abcdef",
                sessionLabel = null, // tests sessionId.take(8)
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                sampleCount = 3,
                eggsConfirmed = 1,
                positiveSpecies = listOf(EggSpecies.HOOKWORM.displayName),
                lpfPerSpecies = emptyMap(), // tests species without density
            ),
        )
        val doc = createPatientDocument(sessionRows = sessionRows)
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render patient report with real bitmap sample decodes and renders in appendix`() = runTest {
        val tempImage = File.createTempFile("patient_sample", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            FileOutputStream(tempImage).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val samples = listOf(
                ReportPdfSampleItem(
                    sampleId = "sample-with-img",
                    sessionLabel = "SESSION 1",
                    fieldIndex = 1,
                    imagePath = tempImage.absolutePath,
                    eggCount = 2,
                    findingsSummary = "${EggSpecies.ASCARIS.displayName} 2 LPF",
                ),
            )
            val doc = createPatientDocument(appendixSamples = samples)
            val bytes = renderer.render(doc)

            assertValidPdf(bytes, expectedPages = 2)
        } finally {
            tempImage.delete()
        }
    }

    @Test
    fun `render patient report with different sex values succeeds`() = runTest {
        for (sex in listOf(Sex.MALE, Sex.FEMALE, null)) {
            val doc = createPatientDocument(sex = sex, sessionRows = emptyList())
            val bytes = renderer.render(doc)
            assertValidPdf(bytes, expectedPages = 1)
        }
    }

    @Test
    fun `render patient report with multi-species session rows wrapping across multiple lines produces valid pdf`() =
        runTest {
        val sessionRows = listOf(
            PatientReportSessionRow(
                sessionId = "sess-multi-001",
                sessionLabel = "SESSION 1",
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                sampleCount = 10,
                eggsConfirmed = 12,
                positiveSpecies = listOf(
                    EggSpecies.ASCARIS.displayName,
                    EggSpecies.HOOKWORM.displayName,
                    EggSpecies.TRICHURIS.displayName,
                    "Atypical Species With Extremely Long Name That Exceeds Findings Column Width",
                ),
                lpfPerSpecies = mapOf(
                    EggSpecies.ASCARIS.displayName to LpfDensity(min = 1, max = 5),
                    EggSpecies.HOOKWORM.displayName to LpfDensity(min = 0, max = 3),
                    EggSpecies.TRICHURIS.displayName to LpfDensity(min = 2, max = 4),
                ),
            ),
        )
        val speciesRows = listOf(
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.ASCARIS.displayName,
                min = 1,
                max = 5,
                descriptor = LpfDescriptor.MODERATE,
                burdenLevel = ParasiteBurdenLevel.MODERATE,
            ),
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.HOOKWORM.displayName,
                min = 0,
                max = 3,
                descriptor = LpfDescriptor.FEW,
                burdenLevel = ParasiteBurdenLevel.LOW,
            ),
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.TRICHURIS.displayName,
                min = 2,
                max = 4,
                descriptor = LpfDescriptor.MODERATE,
                burdenLevel = ParasiteBurdenLevel.MODERATE,
            ),
        )
        val doc = createPatientDocument(
            speciesRows = speciesRows,
            sessionRows = sessionRows,
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render patient report with many multi-species tall rows paginates cleanly across pages`() =
        runTest {
        val sessionRows = (1..15).map { i ->
            PatientReportSessionRow(
                sessionId = "sess-%03d".format(i),
                sessionLabel = "SESSION $i",
                startedAt = Instant.parse("2026-10-01T08:00:00Z").plusSeconds((i * 86400).toLong()),
                sampleCount = 5,
                eggsConfirmed = 6,
                positiveSpecies = listOf(
                    EggSpecies.ASCARIS.displayName,
                    EggSpecies.HOOKWORM.displayName,
                    EggSpecies.TRICHURIS.displayName,
                ),
                lpfPerSpecies = mapOf(
                    EggSpecies.ASCARIS.displayName to LpfDensity(min = 1, max = 3),
                    EggSpecies.HOOKWORM.displayName to LpfDensity(min = 0, max = 2),
                    EggSpecies.TRICHURIS.displayName to LpfDensity(min = 1, max = 1),
                ),
            )
        }
        val doc = createPatientDocument(
            sessionRows = sessionRows,
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 2)
    }

    private fun assertValidPdf(bytes: ByteArray, expectedPages: Int) {
        assertNotNull(bytes)
        assertTrue("PDF bytes must not be empty", bytes.isNotEmpty())
        val magic = String(bytes.sliceArray(0 until 5), Charsets.US_ASCII)
        assertEquals("%PDF-", magic)
        assertEquals(expectedPages, extractPdfPageCount(bytes))
    }

    @Suppress("LongParameterList")
    private fun createPatientDocument(
        sex: Sex? = Sex.FEMALE,
        scopeStart: LocalDate? = LocalDate.of(2026, 10, 1),
        scopeEnd: LocalDate? = LocalDate.of(2026, 10, 31),
        speciesRows: List<ReportPdfSpeciesRow> = emptyList(),
        sessionRows: List<PatientReportSessionRow> = emptyList(),
        appendixSamples: List<ReportPdfSampleItem> = emptyList(),
        organizationName: String? = "Barangay Health Unit",
        generatedByEmail: String? = "admin@clinic.ph",
    ): PatientReportPdfDocument {
        return PatientReportPdfDocument(
            header = PatientReportPdfHeader(
                reportId = "rep-pat-12345678",
                patientDisplayName = "Maria Clara",
                ageYears = 28,
                sex = sex,
                address = "Zone 4, Barangay Poblacion",
                scopeStart = scopeStart,
                scopeEnd = scopeEnd,
                generatedBy = "Pedro Penduko, RMT",
                generatedByEmail = generatedByEmail,
                organizationName = organizationName,
                generatedAt = Instant.parse("2026-11-05T12:00:00Z"),
                totalSessions = sessionRows.size,
                totalSamples = sessionRows.sumOf { it.sampleCount },
                totalEggsConfirmed = sessionRows.sumOf { it.eggsConfirmed },
                positiveSpecies = speciesRows.map { it.speciesDisplayName },
            ),
            speciesRows = speciesRows,
            sessionRows = sessionRows,
            appendixSamples = appendixSamples,
        )
    }
}
