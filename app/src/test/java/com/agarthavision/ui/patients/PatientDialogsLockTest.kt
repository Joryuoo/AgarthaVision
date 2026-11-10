package com.agarthavision.ui.patients

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agarthavision.domain.model.Patient
import com.agarthavision.domain.model.Sex
import com.agarthavision.ui.components.BiometricLockHost
import com.agarthavision.ui.components.BiometricLockTestTags
import com.agarthavision.ui.components.SingleDatePickerDialog
import com.agarthavision.ui.theme.AgarthaVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PatientDialogsLockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleDuplicate() = PatientDuplicate(
        patient = Patient(
            id = "patient-1",
            lastname = "Dela Cruz",
            firstname = "Juan",
            middleName = "M",
            sex = Sex.MALE,
            birthdate = LocalDate.of(2000, 1, 15),
            psgcBarangayCode = "072217001",
            createdBy = "user-1",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
        barangayName = "Talamban",
    )

    @Test
    fun `patient discard confirm dialog is hidden while locked and restored on unlock`() {
        var isLocked by mutableStateOf(false)
        var confirmed = 0
        var dismissed = 0

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    DiscardConfirmDialog(
                        onConfirm = { confirmed++ },
                        onDismiss = { dismissed++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Lock app
        isLocked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Keep editing").assertDoesNotExist()
        assertEquals(0, confirmed)
        assertEquals(0, dismissed)

        // Unlock app
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }

    @Test
    fun `duplicate patient dialogs are hidden while locked and restored on unlock`() {
        var isLocked by mutableStateOf(true)
        var sameBarangayProceed = 0
        val duplicate = sampleDuplicate()

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    SameBarangayDuplicateDialog(
                        duplicate = duplicate,
                        onProceed = { sameBarangayProceed++ },
                        onOpenExisting = {},
                        onDismiss = {},
                    )
                }
            }
        }

        // Hidden while locked
        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Patient already registered").assertDoesNotExist()

        // Restored after unlock
        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Patient already registered").assertIsDisplayed()
        composeRule.onNodeWithText("Add anyway").performClick()
        assertEquals(1, sameBarangayProceed)
    }

    @Test
    fun `different barangay duplicate and single date picker dialogs are hidden while locked`() {
        var isLocked by mutableStateOf(true)
        val duplicate = sampleDuplicate()

        composeRule.setContent {
            AgarthaVisionTheme {
                BiometricLockHost(
                    isLocked = isLocked,
                    onUnlockClick = {},
                ) {
                    DifferentBarangayDuplicateDialog(
                        duplicate = duplicate,
                        onProceed = {},
                        onOpenExisting = {},
                        onDismiss = {},
                    )
                    SingleDatePickerDialog(
                        title = "Select start date",
                        initialDate = LocalDate.of(2026, 10, 1),
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BiometricLockTestTags.OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithText("Possible duplicate").assertDoesNotExist()
        composeRule.onNodeWithText("Select start date").assertDoesNotExist()

        isLocked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Possible duplicate").assertIsDisplayed()
        composeRule.onNodeWithText("Select start date").assertIsDisplayed()
    }
}
