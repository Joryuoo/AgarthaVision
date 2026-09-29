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
 * Compose tests for [ReportCard].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReportCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun baseReport(pdfFilePath: String?) = Report(
        id = "report-1",
        sessionId = "session-1",
        userId = "user-1",
        reportType = ReportType.SESSION,
        generatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        totalSamples = 1,
        totalEggsConfirmed = 2,
        positiveSpecies = emptyList(),
        lpfPerSpecies = emptyMap(),
        csvFilePath = null,
        pdfFilePath = pdfFilePath,
        supabaseStatus = ReportSyncStatus.SYNCED,
    )

    private data class CardActions(
        val onSessionClick: () -> Unit = {},
        val onOpenPdf: () -> Unit = {},
        val onSharePdf: () -> Unit = {},
    )

    private fun setCard(report: Report, actions: CardActions = CardActions()) {
        composeRule.setContent {
            AgarthaVisionTheme {
                ReportCard(
                    report = report,
                    onSessionClick = actions.onSessionClick,
                    onOpenPdf = actions.onOpenPdf,
                    onSharePdf = actions.onSharePdf,
                )
            }
        }
    }

    @Test
    fun `report with a pdf renders open and share actions`() {
        setCard(baseReport(pdfFilePath = "/tmp/report.pdf"))

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.report_share_action)).assertExists()
    }

    @Test
    fun `a legacy csv-only report renders no pdf actions`() {
        setCard(baseReport(pdfFilePath = null))

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.report_share_action)).assertDoesNotExist()
    }

    @Test
    fun `clicking Open PDF invokes onOpenPdf only, not onSessionClick`() {
        var openPdfClicks = 0
        var sessionClicks = 0
        setCard(
            baseReport(pdfFilePath = "/tmp/report.pdf"),
            CardActions(onSessionClick = { sessionClicks++ }, onOpenPdf = { openPdfClicks++ }),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.reports_open_pdf)).performClick()

        assertEquals("expected one onOpenPdf click, got $openPdfClicks", 1, openPdfClicks)
        assertEquals("onSessionClick should not fire from action click, got $sessionClicks", 0, sessionClicks)
    }

    @Test
    fun `clicking Share invokes onSharePdf only, not onSessionClick`() {
        var sharePdfClicks = 0
        var sessionClicks = 0
        setCard(
            baseReport(pdfFilePath = "/tmp/report.pdf"),
            CardActions(onSessionClick = { sessionClicks++ }, onSharePdf = { sharePdfClicks++ }),
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.report_share_action)).performClick()

        assertEquals("expected one onSharePdf click, got $sharePdfClicks", 1, sharePdfClicks)
        assertEquals("onSessionClick should not fire from action click, got $sessionClicks", 0, sessionClicks)
    }
}
