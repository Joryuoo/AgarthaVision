package com.agarthavision.ui.sessions

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.usecase.records.PatientReportCandidate
import com.agarthavision.ui.theme.AgarthaVisionTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PatientReportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeCandidate(
        id: String,
        label: String,
        verifiedCount: Int,
        date: LocalDate = LocalDate.of(2026, 9, 24),
    ): PatientReportCandidate {
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return PatientReportCandidate(
            session = Session(
                id = id,
                userId = "user-1",
                patientId = "pat-1",
                deviceId = "dev-1",
                startedAt = millis,
                label = label,
                supabaseStatus = SessionSyncStatus.SYNCED,
            ),
            verifiedSampleCount = verifiedCount,
        )
    }

    @Test
    fun `tapping a disabled row with zero verified samples does not toggle`() {
        var toggled = false
        val zeroCandidate = makeCandidate("s1", "MEIK-M21-S05", verifiedCount = 0)
        val sheetState = PatientReportSheetState(
            candidates = listOf(zeroCandidate),
            selectedSessionIds = emptySet(),
            isLoadingCandidates = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = { toggled = true },
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithText("MEIK-M21-S05").performClick()
        assertFalse(toggled)
    }

    @Test
    fun `tapping a disabled row out of date range does not toggle`() {
        var toggled = false
        val outOfRangeCandidate = makeCandidate(
            "s1",
            "MEIK-M21-S01",
            verifiedCount = 3,
            date = LocalDate.of(2026, 9, 1),
        )
        val sheetState = PatientReportSheetState(
            candidates = listOf(outOfRangeCandidate),
            startDate = LocalDate.of(2026, 9, 20),
            endDate = LocalDate.of(2026, 9, 30),
            selectedSessionIds = emptySet(),
            isLoadingCandidates = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = { toggled = true },
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithText("MEIK-M21-S01").performClick()
        assertFalse(toggled)
    }

    @Test
    fun `tapping an enabled row toggles it`() {
        var toggledId: String? = null
        val eligibleCandidate = makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 3)
        val sheetState = PatientReportSheetState(
            candidates = listOf(eligibleCandidate),
            selectedSessionIds = setOf("s2"),
            isLoadingCandidates = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = { toggledId = it },
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithText("MEIK-M21-S02").performClick()
        assertEquals("s2", toggledId)
    }

    @Test
    fun `generate button is disabled with empty selection`() {
        val eligibleCandidate = makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 3)
        val emptySelectionState = PatientReportSheetState(
            candidates = listOf(eligibleCandidate),
            selectedSessionIds = emptySet(),
            isGenerating = false,
            isLoadingCandidates = false,
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = emptySelectionState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }
        composeRule.onNodeWithTag("patient_report_generate_button").assertIsNotEnabled()
    }

    @Test
    fun `generate button is enabled with non-empty selection`() {
        val eligibleCandidate = makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 3)
        val activeState = PatientReportSheetState(
            candidates = listOf(eligibleCandidate),
            selectedSessionIds = setOf("s2"),
            isGenerating = false,
            isLoadingCandidates = false,
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = activeState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }
        composeRule.onNodeWithTag("patient_report_generate_button").assertIsEnabled()
    }

    @Test
    fun `generate button is disabled while generating`() {
        val eligibleCandidate = makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 3)
        val generatingState = PatientReportSheetState(
            candidates = listOf(eligibleCandidate),
            selectedSessionIds = setOf("s2"),
            isGenerating = true,
            isLoadingCandidates = false,
        )
        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = generatingState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }
        composeRule.onNodeWithTag("patient_report_generate_button").assertIsNotEnabled()
    }

    @Test
    fun `select all button calls onSelectAll callback`() {
        var selectAllCalled = false
        val eligibleCandidate = makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 3)
        val sheetState = PatientReportSheetState(
            candidates = listOf(eligibleCandidate),
            selectedSessionIds = emptySet(),
            isLoadingCandidates = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = { selectAllCalled = true },
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithText("Select all").performClick()
        assertTrue(selectAllCalled)
    }
}
