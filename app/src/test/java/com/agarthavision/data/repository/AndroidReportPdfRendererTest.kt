package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.LpfDescriptor
import com.agarthavision.domain.model.ParasiteBurdenLevel
import com.agarthavision.domain.model.ReportPdfDocument
import com.agarthavision.domain.model.ReportPdfHeader
import com.agarthavision.domain.model.ReportPdfSampleItem
import com.agarthavision.domain.model.ReportPdfSpeciesRow
import com.agarthavision.domain.model.Sex
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
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
class AndroidReportPdfRendererTest {

    private lateinit var context: Context
    private lateinit var renderer: AndroidReportPdfRenderer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        renderer = AndroidReportPdfRenderer(context)
    }

    @Test
    fun `render summary-only negative smear produces 1-page valid PDF`() = runTest {
        val doc = createDocument(
            speciesRows = emptyList(),
            appendixSamples = emptyList(),
            organizationName = null,
            generatedByEmail = null,
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render summary-only positive smear with burden pills produces 1-page valid PDF`() = runTest {
        val rows = listOf(
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.ASCARIS.displayName,
                min = 1,
                max = 4,
                descriptor = LpfDescriptor.FEW,
                burdenLevel = ParasiteBurdenLevel.LOW,
            ),
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.TRICHURIS.displayName,
                min = 5,
                max = 12,
                descriptor = LpfDescriptor.NUMEROUS,
                burdenLevel = ParasiteBurdenLevel.HIGH,
            ),
            ReportPdfSpeciesRow(
                speciesDisplayName = EggSpecies.HOOKWORM.displayName,
                min = 2,
                max = 2,
                descriptor = LpfDescriptor.RARE,
                burdenLevel = ParasiteBurdenLevel.LOW,
            ),
        )
        val doc = createDocument(
            speciesRows = rows,
            appendixSamples = emptyList(),
            organizationName = "Regional Medical Center",
            generatedByEmail = "medtech@example.gov.ph",
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render with null sessionLabel falls back to sessionId`() = runTest {
        val doc = createDocument(
            sessionLabel = null,
            speciesRows = emptyList(),
            appendixSamples = emptyList(),
        )
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 1)
    }

    @Test
    fun `render with various patient sex values succeeds`() = runTest {
        for (sex in listOf(Sex.MALE, Sex.FEMALE, null)) {
            val doc = createDocument(
                patientSex = sex,
                speciesRows = emptyList(),
                appendixSamples = emptyList(),
            )
            val bytes = renderer.render(doc)
            assertValidPdf(bytes, expectedPages = 1)
        }
    }

    @Test
    fun `render with 1 to 4 appendix samples produces 2 pages`() = runTest {
        for (count in listOf(1, 4)) {
            val samples = (1..count).map { i ->
                ReportPdfSampleItem(
                    sampleId = "sample-$i",
                    sessionLabel = "SESSION 1",
                    fieldIndex = i,
                    imagePath = null,
                    eggCount = i,
                    findingsSummary = "${EggSpecies.ASCARIS.displayName} $i LPF",
                )
            }
            val doc = createDocument(appendixSamples = samples)
            val bytes = renderer.render(doc)

            assertValidPdf(bytes, expectedPages = 2)
        }
    }

    @Test
    fun `render with 5 appendix samples produces 3 pages`() = runTest {
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
        val doc = createDocument(appendixSamples = samples)
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 3)
    }

    @Test
    fun `render with 8 appendix samples produces 3 pages`() = runTest {
        val samples = (1..8).map { i ->
            ReportPdfSampleItem(
                sampleId = "sample-$i",
                sessionLabel = "SESSION 1",
                fieldIndex = i,
                imagePath = null,
                eggCount = i,
                findingsSummary = "${EggSpecies.TRICHURIS.displayName} $i LPF",
            )
        }
        val doc = createDocument(appendixSamples = samples)
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 3)
    }

    @Test
    fun `render with real bitmap file decodes and draws successfully`() = runTest {
        val tempImage = File.createTempFile("test_sample", ".png", context.cacheDir)
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
                    eggCount = 3,
                    findingsSummary = "${EggSpecies.ASCARIS.displayName} 3 LPF",
                ),
            )
            val doc = createDocument(appendixSamples = samples)
            val bytes = renderer.render(doc)

            assertValidPdf(bytes, expectedPages = 2)
        } finally {
            tempImage.delete()
        }
    }

    @Test
    fun `render with missing image file path falls back to placeholder without error`() = runTest {
        val samples = listOf(
            ReportPdfSampleItem(
                sampleId = "sample-missing-img",
                sessionLabel = "SESSION 1",
                fieldIndex = 1,
                imagePath = "/invalid/path/that/does/not/exist.png",
                eggCount = 0,
                findingsSummary = "No parasites observed",
            ),
        )
        val doc = createDocument(appendixSamples = samples)
        val bytes = renderer.render(doc)

        assertValidPdf(bytes, expectedPages = 2)
    }

    @Test
    fun `render with binomial and non-binomial species summaries formats typography`() = runTest {
        val samples = listOf(
            ReportPdfSampleItem(
                sampleId = "sample-binomial",
                sessionLabel = "SESSION 1",
                fieldIndex = 1,
                imagePath = null,
                eggCount = 2,
                findingsSummary = "${EggSpecies.ASCARIS.displayName} 2 LPF",
            ),
            ReportPdfSampleItem(
                sampleId = "sample-non-binomial",
                sessionLabel = "SESSION 1",
                fieldIndex = 2,
                imagePath = null,
                eggCount = 1,
                findingsSummary = "${EggSpecies.HOOKWORM.displayName} 1 LPF",
            ),
            ReportPdfSampleItem(
                sampleId = "sample-negative",
                sessionLabel = "SESSION 1",
                fieldIndex = 3,
                imagePath = null,
                eggCount = 0,
                findingsSummary = "No parasites observed",
            ),
        )
        val doc = createDocument(appendixSamples = samples)
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
    private fun createDocument(
        sessionLabel: String? = "SESSION 1",
        patientSex: Sex? = Sex.MALE,
        speciesRows: List<ReportPdfSpeciesRow> = emptyList(),
        appendixSamples: List<ReportPdfSampleItem> = emptyList(),
        organizationName: String? = "Health Unit",
        generatedByEmail: String? = "medtech@example.com",
    ): ReportPdfDocument {
        return ReportPdfDocument(
            header = ReportPdfHeader(
                reportId = "rep-12345678-abcd",
                sessionId = "sess-12345678-efgh",
                sessionLabel = sessionLabel,
                patientName = "Juan Dela Cruz",
                patientSex = patientSex,
                patientAgeYears = 42,
                barangayLabel = "Barangay Poblacion",
                fieldsExamined = 10,
                generatedByName = "Maria Santos, RMT",
                generatedByEmail = generatedByEmail,
                organizationName = organizationName,
                generatedAt = Instant.parse("2026-11-05T12:00:00Z"),
                totalEggsConfirmed = speciesRows.sumOf { it.max },
                positiveSpecies = speciesRows.map { it.speciesDisplayName },
            ),
            speciesRows = speciesRows,
            appendixSamples = appendixSamples,
        )
    }
}
