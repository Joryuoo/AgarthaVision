package com.agarthavision.ui.records

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.agarthavision.domain.model.PatientNameOption
import com.agarthavision.domain.model.ReportConfig
import com.agarthavision.ui.components.BiometricLockHost
import com.agarthavision.ui.components.BiometricLockTestTags
import com.agarthavision.ui.theme.AgarthaVisionTheme
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
class SessionReportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    // -------------------------------------------------------------------------
    // Step 1: CONFIGURATION
    // -------------------------------------------------------------------------

    @Test
    fun `configuration step toggling include images switch invokes callback`() {
        var updatedConfig: ReportConfig? = null
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(includeSampleImages = false),
            verifiedSampleCount = 12,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = { updatedConfig = it },
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH).performClick()
        assertEquals(true, updatedConfig?.includeSampleImages)
    }

    @Test
    fun `configuration step estimated pages shows single page when images not included`() {
        val stateWithoutImages = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(includeSampleImages = false),
            verifiedSampleCount = 12,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = stateWithoutImages,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithText("A4 · 1 page").assertIsDisplayed()
    }

    @Test
    fun `configuration step estimated pages calculates pages when images included`() {
        val stateWithImages = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(includeSampleImages = true),
            verifiedSampleCount = 12,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = stateWithImages,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithText("A4 · 4 pages").assertIsDisplayed()
    }

    @Test
    fun `configuration step selecting name option pills invokes callback`() {
        var selectedOption: PatientNameOption? = null
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = { selectedOption = it.patientNameOption },
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR).performClick()
        assertEquals(PatientNameOption.CENSOR, selectedOption)

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_EXCLUDE).performClick()
        assertEquals(PatientNameOption.EXCLUDE, selectedOption)

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SHOW).performClick()
        assertEquals(PatientNameOption.SHOW, selectedOption)
    }

    @Test
    fun `configuration step clicking censor when patient is codename shows toast and does not invoke callback`() {
        var updatedConfig: ReportConfig? = null
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            isPatientCodename = true,
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = { updatedConfig = it },
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR).performClick()
        assertNull(updatedConfig)
        assertEquals("Patient uses a codename and cannot be censored.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `configuration step clicking censor when patient is not codename updates config`() {
        var updatedConfig: ReportConfig? = null
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            isPatientCodename = false,
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = { updatedConfig = it },
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR).performClick()
        assertEquals(PatientNameOption.CENSOR, updatedConfig?.patientNameOption)
    }

    @Test
    fun `configuration step selected patient name caption reflects SHOW option`() {
        val showState = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = showState,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .assertTextEquals("Selected: Dela Cruz, Juan M.")
    }

    @Test
    fun `configuration step selected patient name caption reflects CENSOR option`() {
        val censorState = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.CENSOR),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = censorState,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .assertTextEquals("Selected: D*** C**z, J**n M.")
    }

    @Test
    fun `configuration step selected patient name caption reflects EXCLUDE option`() {
        val excludeState = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.EXCLUDE),
            patientDisplayName = "Dela Cruz, Juan M.",
            patientMaskedName = "D*** C**z, J**n M.",
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = excludeState,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .assertTextEquals("Selected: Excluded")
    }

    @Test
    fun `configuration step patient name caption uses fallback when name is blank`() {
        val blankState = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            config = ReportConfig(patientNameOption = PatientNameOption.SHOW),
            patientDisplayName = "",
            patientMaskedName = "",
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = blankState,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }
        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SELECTED_CAPTION)
            .assertTextEquals("Selected: Show")
    }

    @Test
    fun `configuration step sample warning banner displayed when sample count is less than 10`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Only 5 of 10 recommended samples selected").assertIsDisplayed()
        composeRule.onNodeWithText("You can still preview and continue.").assertIsDisplayed()
    }

    @Test
    fun `configuration step sample warning banner displays singular plural for 1 sample`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 1,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Only 1 of 10 recommended sample selected").assertIsDisplayed()
    }

    @Test
    fun `configuration step sample warning banner not displayed when sample count is 10 or more`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_WARNING_BANNER).assertDoesNotExist()
    }

    @Test
    fun `configuration step preview button triggers onPreview when samples exist`() {
        var previewTriggered = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = { previewTriggered = true },
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON)
            .assertIsEnabled()
            .performClick()
        assertTrue(previewTriggered)
    }

    @Test
    fun `configuration step preview button displays View preview label`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON)
            .assertIsDisplayed()
            .assertTextEquals("Report Preview")
    }

    @Test
    fun `configuration step preview button is disabled when sample count is 0`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 0,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `configuration step preview button is disabled when loading preview`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 10,
            isLoadingPreview = true,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `configuration step cancel button triggers onDismiss`() {
        var dismissed = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = { dismissed = true },
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.CANCEL_BUTTON).performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `header close button triggers onDismiss`() {
        var dismissed = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = { dismissed = true },
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Close").performClick()
        assertTrue(dismissed)
    }

    // -------------------------------------------------------------------------
    // Step 2: PREVIEW
    // -------------------------------------------------------------------------

    @Test
    fun `preview step displays viewport and options summary card`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            config = ReportConfig(
                includeSampleImages = true,
                patientNameOption = PatientNameOption.SHOW,
            ),
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithText("Included").assertIsDisplayed()
        composeRule.onNodeWithText("Show").assertIsDisplayed()
    }

    @Test
    fun `preview step displays summary card with not included and censor options`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            config = ReportConfig(
                includeSampleImages = false,
                patientNameOption = PatientNameOption.CENSOR,
            ),
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithText("Not included").assertIsDisplayed()
        composeRule.onNodeWithText("Censor").assertIsDisplayed()
    }

    @Test
    fun `preview step warning acknowledgment card displayed and generate disabled when unacknowledged`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 5,
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CHECKBOX).assertIsDisplayed()
        composeRule.onNodeWithText("You can continue with these samples.").assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `preview step acknowledgment card shows single sample subtitle for 1 sample`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 1,
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithText("You can continue with this sample.").assertIsDisplayed()
    }

    @Test
    fun `preview step tapping acknowledgment checkbox invokes callback`() {
        var acknowledgedResult: Boolean? = null
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 5,
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = { acknowledgedResult = it },
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CHECKBOX).performClick()
        assertEquals(true, acknowledgedResult)
    }

    @Test
    fun `preview step generate button enabled when warning acknowledged`() {
        var generateClicked = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 5,
            isWarningAcknowledged = true,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = { generateClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsEnabled()
            .performClick()
        assertTrue(generateClicked)
    }

    @Test
    fun `preview step sample ack card not displayed and generate enabled when samples at least 10`() {
        var generateClicked = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 10,
            isWarningAcknowledged = false,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = { generateClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.SAMPLE_ACK_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsEnabled()
            .performClick()
        assertTrue(generateClicked)
    }

    @Test
    fun `preview step generate button disabled while generating`() {
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 10,
            isGenerating = true,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `preview step clicking edit options invokes onBackToConfig`() {
        var backClicked = false
        val state = SessionReportSheetState(
            step = ReportSheetStep.PREVIEW,
            verifiedSampleCount = 10,
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = { backClicked = true },
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReportSheetTestTags.EDIT_OPTIONS_BUTTON).performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `error text is rendered when error message is present in state`() {
        val stateWithError = SessionReportSheetState(
            step = ReportSheetStep.CONFIGURATION,
            verifiedSampleCount = 5,
            error = "Unable to connect to printer service",
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheetContent(
                    state = stateWithError,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = {},
                    onConfigChange = {},
                    onAcknowledgeWarning = {},
                    onPreview = {},
                    onBackToConfig = {},
                    onGeneratePdf = {},
                )
            }
        }

        composeRule.onNodeWithText("Unable to connect to printer service").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // SessionReportSheetState unit logic
    // -------------------------------------------------------------------------

    @Test
    fun `SessionReportSheetState computed properties evaluate correctly`() {
        val underTen = SessionReportSheetState(verifiedSampleCount = 9, isWarningAcknowledged = false)
        assertTrue(underTen.requiresWarningAck)
        assertFalse(underTen.canGeneratePdf)

        val acked = underTen.copy(isWarningAcknowledged = true)
        assertTrue(acked.requiresWarningAck)
        assertTrue(acked.canGeneratePdf)

        val tenPlus = SessionReportSheetState(verifiedSampleCount = 10, isWarningAcknowledged = false)
        assertFalse(tenPlus.requiresWarningAck)
        assertTrue(tenPlus.canGeneratePdf)

        val generating = tenPlus.copy(isGenerating = true)
        assertFalse(generating.canGeneratePdf)

        val noImages = SessionReportSheetState(
            config = ReportConfig(includeSampleImages = false),
            verifiedSampleCount = 10,
        )
        assertEquals(1, noImages.estimatedPages)

        val zeroSamplesWithImages = SessionReportSheetState(
            config = ReportConfig(includeSampleImages = true),
            verifiedSampleCount = 0,
        )
        assertEquals(1, zeroSamplesWithImages.estimatedPages)

        val fourSamplesWithImages = SessionReportSheetState(
            config = ReportConfig(includeSampleImages = true),
            verifiedSampleCount = 4,
        )
        assertEquals(2, fourSamplesWithImages.estimatedPages) // 1 + (4+3)/4 = 2

        val fiveSamplesWithImages = SessionReportSheetState(
            config = ReportConfig(includeSampleImages = true),
            verifiedSampleCount = 5,
        )
        assertEquals(3, fiveSamplesWithImages.estimatedPages) // 1 + (5+3)/4 = 3
    }

    @Test
    fun `report preview and configuration sheet are hidden while locked and resume coherently on unlock`() {
        var isLocked by mutableStateOf(false)
        var darkTheme by mutableStateOf(false)
        var step by mutableStateOf(ReportSheetStep.CONFIGURATION)
        var dismissed = false
        var generated = false

        composeRule.setContent {
            AgarthaVisionTheme(darkTheme = darkTheme) {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {
                        // Simulates failed/cancelled biometric auth not unlocking automatically
                    },
                ) {
                    SessionReportSheet(
                        state = SessionReportSheetState(
                            step = step,
                            config = ReportConfig(
                                includeSampleImages = true,
                                patientNameOption = PatientNameOption.CENSOR,
                            ),
                            verifiedSampleCount = 12,
                            patientDisplayName = "Dela Cruz, Juan M.",
                            patientMaskedName = "D*** C**z, J**n M.",
                        ),
                        sessionLabel = "Session #1",
                        sessionDate = "Oct 24, 2026",
                        onDismiss = { dismissed = true },
                        onConfigChange = {},
                        onAcknowledgeWarning = {},
                        onPreview = { step = ReportSheetStep.PREVIEW },
                        onBackToConfig = { step = ReportSheetStep.CONFIGURATION },
                        onGeneratePdf = { generated = true },
                    )
                }
            }
        }

        // Open in CONFIGURATION step ("Generate Report")
        composeRule.onNodeWithTag(ReportSheetTestTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).assertIsDisplayed()

        // Lock while Generate Report configuration is open
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).assertDoesNotExist()
        assertFalse(dismissed)

        // Unlock and proceed to Report Preview
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertIsDisplayed()

        // Background/resume while Report Preview is open (including dark mode transition)
        darkTheme = true
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.SHEET).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(ReportSheetTestTags.EDIT_OPTIONS_BUTTON).assertDoesNotExist()

        // Cancelled/failed biometric prompt click keeps report content hidden
        composeRule.onNodeWithTag(BiometricLockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertDoesNotExist()
        assertFalse(dismissed)
        assertFalse(generated)

        // Successful biometric unlock restores Report Preview in coherent state
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_VIEWPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.GENERATE_PDF_BUTTON)
            .assertIsDisplayed()
            .performClick()
        assertTrue(generated)
    }

    @Test
    fun `changed session options request confirmation on dismiss and preserve state on keep`() {
        var dismissed = false
        var darkTheme by mutableStateOf(false)
        var sheetWidthDp by mutableStateOf(411.dp)
        var state by mutableStateOf(
            SessionReportSheetState(
                step = ReportSheetStep.CONFIGURATION,
                config = ReportConfig(),
                verifiedSampleCount = 10,
                patientDisplayName = "Dela Cruz, Juan M.",
                patientMaskedName = "D*** C**z, J**n M.",
            ),
        )
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AgarthaVisionTheme(darkTheme = darkTheme) {
                Box(modifier = Modifier.size(width = sheetWidthDp, height = 891.dp)) {
                    SessionReportSheet(
                        state = state,
                        sessionLabel = "Session #1",
                        sessionDate = "Oct 24, 2026",
                        onDismiss = { dismissed = true },
                        onConfigChange = { state = state.copy(config = it) },
                        onAcknowledgeWarning = { state = state.copy(isWarningAcknowledged = it) },
                        onPreview = { state = state.copy(step = ReportSheetStep.PREVIEW) },
                        onBackToConfig = { state = state.copy(step = ReportSheetStep.CONFIGURATION) },
                        onGeneratePdf = {},
                    )
                }
            }
        }

        // 1. Change image inclusion -> header X requests confirmation
        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH).performClick()
        assertTrue(state.config.includeSampleImages)
        assertTrue(state.hasUnsavedChanges)

        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        assertFalse(dismissed)
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Keep editing preserves configuration
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_DISMISS).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertDoesNotExist()
        assertFalse(dismissed)
        assertTrue(state.config.includeSampleImages)

        // 2. Cancel button also requests confirmation when changed
        composeRule.onNodeWithTag(ReportSheetTestTags.CANCEL_BUTTON).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_DISMISS).performClick()
        assertFalse(dismissed)

        // 3. System Back requests confirmation when changed
        composeRule.runOnIdle { backDispatcher?.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_DISMISS).performClick()
        assertFalse(dismissed)

        // 4. Downward swipe in dark mode and compact/expanded sheet sizes requests confirmation
        darkTheme = true
        sheetWidthDp = 600.dp
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReportSheetTestTags.SHEET).performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_DISMISS).performClick()
        assertFalse(dismissed)

        // 5. Returning all settings to initial values removes the discard prompt
        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH).performClick()
        assertFalse(state.config.includeSampleImages)
        assertFalse(state.hasUnsavedChanges)

        // Now changing patient name to CENSOR re-enables protection, and reverting to SHOW removes it
        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR).performClick()
        assertTrue(state.hasUnsavedChanges)
        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_DISMISS).performClick()

        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_SHOW).performClick()
        assertFalse(state.hasUnsavedChanges)
        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertDoesNotExist()
        assertTrue(dismissed)
    }

    @Test
    fun `preview close button returns to edit options without discard prompt and preserves settings`() {
        var dismissed = false
        var state by mutableStateOf(
            SessionReportSheetState(
                step = ReportSheetStep.CONFIGURATION,
                config = ReportConfig(),
                verifiedSampleCount = 12,
                patientDisplayName = "Dela Cruz, Juan M.",
                patientMaskedName = "D*** C**z, J**n M.",
            ),
        )

        composeRule.setContent {
            AgarthaVisionTheme {
                SessionReportSheet(
                    state = state,
                    sessionLabel = "Session #1",
                    sessionDate = "Oct 24, 2026",
                    onDismiss = { dismissed = true },
                    onConfigChange = { state = state.copy(config = it) },
                    onAcknowledgeWarning = { state = state.copy(isWarningAcknowledged = it) },
                    onPreview = { state = state.copy(step = ReportSheetStep.PREVIEW) },
                    onBackToConfig = { state = state.copy(step = ReportSheetStep.CONFIGURATION) },
                    onGeneratePdf = {},
                )
            }
        }

        // Change options (includeSampleImages: false -> true, patientNameOption: SHOW -> CENSOR) and open Preview
        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_CENSOR).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).performClick()

        assertEquals(ReportSheetStep.PREVIEW, state.step)
        composeRule.onNodeWithText("Included").assertIsDisplayed()
        composeRule.onNodeWithText("Censor").assertIsDisplayed()

        // Tap X in Report Preview -> returns to CONFIGURATION without discard prompt and without closing wizard
        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertDoesNotExist()
        assertFalse(dismissed)
        assertEquals(ReportSheetStep.CONFIGURATION, state.step)
        assertTrue(state.config.includeSampleImages)
        assertEquals(PatientNameOption.CENSOR, state.config.patientNameOption)

        // Further edit options and reopen preview -> preview reflects updated settings
        composeRule.onNodeWithTag(ReportSheetTestTags.INCLUDE_IMAGES_SWITCH).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.PATIENT_NAME_EXCLUDE).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.PREVIEW_BUTTON).performClick()

        assertEquals(ReportSheetStep.PREVIEW, state.step)
        composeRule.onNodeWithText("Not included").assertIsDisplayed()
        composeRule.onNodeWithText("Exclude").assertIsDisplayed()

        // Confirming discard when exiting the changed wizard closes it
        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        assertEquals(ReportSheetStep.CONFIGURATION, state.step)
        composeRule.onNodeWithTag(ReportSheetTestTags.HEADER_CLOSE_BUTTON).performClick()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ReportSheetTestTags.DISCARD_DIALOG_CONFIRM).performClick()
        assertTrue(dismissed)
    }
}
