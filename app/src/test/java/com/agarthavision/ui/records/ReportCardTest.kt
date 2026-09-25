package com.agarthavision.ui.records

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import com.agarthavision.domain.model.Report
import com.agarthavision.domain.model.ReportSyncStatus
import com.agarthavision.domain.model.ReportType
import com.agarthavision.ui.theme.AgarthaVisionTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for [ReportCard], covering ticket 14zcqnthuad's swap of the
 * old text-button [ActionChip]s for icon-based [ReportIconAction]s.
 *
 * [ReportCard] is `internal` (widened from `private`) solely to allow direct
 * invocation from this test module, matching the precedent in StatusPillTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReportCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun baseReport(pdfFilePath: String?, csvFilePath: String?) = Report(
        id = "report-1",
        sessionId = "session-1",
        userId = "user-1",
        reportType = ReportType.SESSION,
        generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        totalSamples = 1,
        totalEggsConfirmed = 2,
        positiveSpecies = emptyList(),
        lpfPerSpecies = emptyMap(),
        csvFilePath = csvFilePath,
        pdfFilePath = pdfFilePath,
        supabaseStatus = ReportSyncStatus.SYNCED,
    )

    private data class CardActions(
        val onSessionClick: () -> Unit = {},
        val onOpenPdf: () -> Unit = {},
        val onOpenCsv: () -> Unit = {},
        val onSharePdf: () -> Unit = {},
        val onShareCsv: () -> Unit = {},
    )

    private fun setCard(report: Report, actions: CardActions = CardActions()) {
        composeRule.setContent {
            AgarthaVisionTheme {
                ReportCard(
                    report = report,
                    onSessionClick = actions.onSessionClick,
                    onOpenPdf = actions.onOpenPdf,
                    onOpenCsv = actions.onOpenCsv,
                    onSharePdf = actions.onSharePdf,
                    onShareCsv = actions.onShareCsv,
                )
            }
        }
    }

    @Test
    fun `report with both pdf and csv renders all four icon actions`() {
        setCard(baseReport(pdfFilePath = "/tmp/report.pdf", csvFilePath = "/tmp/report.csv"))

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_csv)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_csv)).assertExists()
    }

    @Test
    fun `report with only pdf renders pdf actions and no csv actions`() {
        setCard(baseReport(pdfFilePath = "/tmp/report.pdf", csvFilePath = null))

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_csv)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_csv)).assertDoesNotExist()
    }

    @Test
    fun `report with only csv renders csv actions and no pdf actions`() {
        setCard(baseReport(pdfFilePath = null, csvFilePath = "/tmp/report.csv"))

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_csv)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_csv)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_pdf)).assertDoesNotExist()
    }

    @Test
    fun `clicking Open PDF invokes onOpenPdf only, not onSessionClick`() {
        var openPdfClicks = 0
        var sessionClicks = 0
        setCard(
            baseReport(pdfFilePath = "/tmp/report.pdf", csvFilePath = "/tmp/report.csv"),
            CardActions(onSessionClick = { sessionClicks++ }, onOpenPdf = { openPdfClicks++ }),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).performClick()

        assertEquals("expected one onOpenPdf click, got $openPdfClicks", 1, openPdfClicks)
        assertEquals("onSessionClick should not fire from icon click, got $sessionClicks", 0, sessionClicks)
    }

    @Test
    fun `clicking Share CSV invokes onShareCsv only, not onSessionClick`() {
        var shareCsvClicks = 0
        var sessionClicks = 0
        setCard(
            baseReport(pdfFilePath = "/tmp/report.pdf", csvFilePath = "/tmp/report.csv"),
            CardActions(onSessionClick = { sessionClicks++ }, onShareCsv = { shareCsvClicks++ }),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_share_csv)).performClick()

        assertEquals("expected one onShareCsv click, got $shareCsvClicks", 1, shareCsvClicks)
        assertEquals("onSessionClick should not fire from icon click, got $sessionClicks", 0, sessionClicks)
    }
}
