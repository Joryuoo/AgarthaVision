package com.agarthavision.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.domain.model.Session
import com.agarthavision.domain.model.SessionSyncStatus
import com.agarthavision.domain.usecase.records.PatientReportCandidate
import com.agarthavision.ui.components.BiometricLockHost
import com.agarthavision.ui.components.BiometricLockTestTags
import com.agarthavision.ui.records.ReportSheetStep
import com.agarthavision.ui.records.ReportSheetTestTags
import com.agarthavision.ui.theme.AgarthaVisionTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@Suppress("LargeClass")
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

    // -------------------------------------------------------------------------
    // Configuration step: ReportOptionsSection, warnings, and preview trigger
    // -------------------------------------------------------------------------

    @Test
    fun `configuration step toggling include images switch invokes callback`() {
        var updatedConfig: ReportConfig? = null
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(includeSampleImages = false),
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onConfigChange = { updatedConfig = it },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH)
            .performScrollTo()
            .performClick()
        assertEquals(true, updatedConfig?.includeSampleImages)
    }

    @Test
    fun `configuration step selecting name option pills invokes callback`() {
        var selectedOption: PatientNameOption? = null
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onConfigChange = { selectedOption = it.patientNameOption },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR)
            .performScrollTo()
            .performClick()
        assertEquals(PatientNameOption.CENSOR, selectedOption)

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_EXCLUDE)
            .performScrollTo()
            .performClick()
        assertEquals(PatientNameOption.EXCLUDE, selectedOption)

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SHOW)
            .performScrollTo()
            .performClick()
        assertEquals(PatientNameOption.SHOW, selectedOption)
    }

    @Test
    fun `configuration step clicking censor when patient is codename shows toast and does not invoke callback`() {
        var updatedConfig: ReportConfig? = null
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
            isPatientCodename = true,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheetContent(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onConfigChange = { updatedConfig = it },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR)
            .performScrollTo()
            .performClick()
        assertNull(updatedConfig)
        assertEquals("Patient uses a codename and cannot be censored.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `configuration step clicking censor when patient is not codename updates config`() {
        var updatedConfig: ReportConfig? = null
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
            isPatientCodename = false,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheetContent(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onConfigChange = { updatedConfig = it },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR)
            .performScrollTo()
            .performClick()
        assertEquals(PatientNameOption.CENSOR, updatedConfig?.patientNameOption)
    }

    @Test
    fun `configuration step selected patient name caption reflects SHOW option`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .performScrollTo()
            .assertTextEquals("Selected: Dela Cruz, Juan M.")
    }

    @Test
    fun `configuration step selected patient name caption reflects CENSOR option`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.CENSOR),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .performScrollTo()
            .assertTextEquals("Selected: D*** C**z, J**n M.")
    }

    @Test
    fun `configuration step selected patient name caption reflects EXCLUDE option`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.EXCLUDE),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .performScrollTo()
            .assertTextEquals("Selected: Excluded")
    }

    @Test
    fun `configuration step sample warning banner displayed when total verified samples less than 10`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(
                makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3),
                makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 4),
            ),
            selectedSessionIds = setOf("s1", "s2"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Only 7 of 10 recommended samples selected")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `configuration step sample warning banner hidden when total verified samples at least 10`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(
                makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 5),
                makeCandidate("s2", "MEIK-M21-S02", verifiedCount = 5),
            ),
            selectedSessionIds = setOf("s1", "s2"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER).assertDoesNotExist()
    }

    @Test
    fun `configuration step preview report button invokes onPreview callback`() {
        var previewCalled = false
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 3)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onPreview = { previewCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertTrue(previewCalled)
    }

    @Test
    fun `configuration step preview button displays View preview label`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Report Preview")
    }

    // -------------------------------------------------------------------------
    // Preview step: Viewport, options summary, ack card, and generate PDF
    // -------------------------------------------------------------------------

    @Test
    fun `preview step displays viewport and options summary card`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            config = ReportConfig(
                includeSampleImages = true,
                patientNameOption = PatientNameOption.SHOW,
            ),
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithText("Included").assertIsDisplayed()
        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun `preview step warning acknowledgment card displayed and generate disabled when unacknowledged`() {
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 5)),
            selectedSessionIds = setOf("s1"),
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CHECKBOX).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `preview step tapping acknowledgment checkbox invokes onAcknowledgeWarning`() {
        var acknowledged = false
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 5)),
            selectedSessionIds = setOf("s1"),
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onAcknowledgeWarning = { acknowledged = it },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CHECKBOX).performClick()
        assertTrue(acknowledged)
    }

    @Test
    fun `preview step generate button enabled and invokes onGenerate when warning acknowledged`() {
        var generateCalled = false
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 5)),
            selectedSessionIds = setOf("s1"),
            isWarningAcknowledged = true,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = { generateCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsEnabled()
            .performClick()
        assertTrue(generateCalled)
    }

    @Test
    fun `preview step sample ack card hidden and generate button enabled when samples at least 10`() {
        var generateCalled = false
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = { generateCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsEnabled()
            .performClick()
        assertTrue(generateCalled)
    }

    @Test
    fun `preview step clicking edit options invokes onBackToConfig callback`() {
        var backCalled = false
        val sheetState = PatientReportSheetState(
            step = ReportSheetStep.PREVIEW,
            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
            selectedSessionIds = setOf("s1"),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                PatientReportSheet(
                    sheet = sheetState,
                    onDismiss = {},
                    onDateRangeSelected = { _, _ -> },
                    onSelectAll = {},
                    onToggleSession = {},
                    onGenerate = {},
                    onBackToConfig = { backCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.EDIT_OPTIONS_BUTTON).performClick()
        assertTrue(backCalled)
    }

    @Test
    fun `patient report sheet configuration and preview are hidden while locked and resume coherently on unlock`() {
        var isLocked by mutableStateOf(false)
        var step by mutableStateOf(ReportSheetStep.CONFIGURATION)
        var dismissed = false
        var generated = false

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    PatientReportSheet(
                        sheet = PatientReportSheetState(
                            step = step,
                            candidates = listOf(makeCandidate("s1", "MEIK-M21-S01", verifiedCount = 10)),
                            selectedSessionIds = setOf("s1"),
                        ),
                        patientName = "Dela Cruz, Juan M.",
                        onDismiss = { dismissed = true },
                        onDateRangeSelected = { _, _ -> },
                        onSelectAll = {},
                        onToggleSession = {},
                        onPreview = { step = ReportSheetStep.PREVIEW },
                        onBackToConfig = { step = ReportSheetStep.CONFIGURATION },
                        onGenerate = { generated = true },
                    )
                }
            }
        }

        // Open in CONFIGURATION step
        composeRule.onNodeWithTag(PatientReportSheetTestTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("MEIK-M21-S01").assertIsDisplayed()

        // Lock while CONFIGURATION step is open
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(PatientReportSheetTestTags.SHEET).assertDoesNotExist()
        composeRule.onNodeWithText("MEIK-M21-S01").assertDoesNotExist()
        assertFalse(dismissed)

        // Unlock and switch to PREVIEW step
        isLocked = false
        step = ReportSheetStep.PREVIEW
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertIsDisplayed()

        // Lock while Report Preview is open
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(PatientReportSheetTestTags.SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertDoesNotExist()

        // Unlock and verify Report Preview resumes coherently
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsDisplayed()
            .performClick()
        assertTrue(generated)
    }
}
