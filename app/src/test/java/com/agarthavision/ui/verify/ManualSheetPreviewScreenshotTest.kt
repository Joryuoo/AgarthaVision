package com.agarthavision.ui.verify

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.agarthavision.domain.model.EggSpecies
import com.agarthavision.domain.model.FlaggedFrame
import com.agarthavision.ui.theme.AgarthaVisionTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Screenshot coverage for the manual capture preview.
 *
 * This exists for one defect the semantics-based suites structurally cannot catch: the
 * preview once used `ContentScale.Crop`, which overflowed a square frame in a landscape
 * container and let the parent clip cut the top and bottom off, so a medtech was labelling
 * a specimen they could only partly see (`ManualSheet.kt`). Scale changes no semantics
 * node, so only a pixel comparison can guard it.
 *
 * Only the preview node is captured, not the whole sheet. The sheet's type is Inter loaded
 * through a Google Fonts provider that does not resolve under Robolectric, so a full-sheet
 * golden would encode whatever fallback face the host JDK happened to pick. The preview
 * contains no text.
 *
 * Record goldens with `./gradlew :app:recordRoborazziDebug`, check them with
 * `./gradlew :app:verifyRoborazziDebug`. Goldens live in `app/src/test/roborazzi/`, not in
 * a `screenshots/` directory: `.gitignore:41` excludes that name, alongside the rules that
 * keep sample imagery out of the repo. These goldens are synthetic - flat colour bands drawn
 * in the test - so they carry no capture data and are committed deliberately.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ManualSheetPreviewScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * A square frame with distinctly coloured bands top and bottom.
     *
     * Square on purpose: the preview container is a landscape 200dp strip, so `Fit`
     * letterboxes this and keeps both bands visible while `Crop` fills the width and clips
     * them away. If the bands survive, the frame is whole.
     */
    private fun squareFrameJpeg(): ByteArray {
        val size = 640
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, size.toFloat(), size * 0.2f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(0f, size * 0.8f, size.toFloat(), size.toFloat(), paint)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        return out.toByteArray()
    }

    private fun frame() = FlaggedFrame(
        sampleId = "sample-1",
        sessionId = "session-1",
        capturedAt = Instant.EPOCH,
        jpegBytes = squareFrameJpeg(),
        predictions = emptyList(),
    )

    private fun noopActions() = ManualSheetActions(
        onSpeciesSelected = {},
        onOtherSpeciesChanged = {},
        onUserNoteChanged = {},
        onFramePrev = {},
        onFrameNext = {},
        onDeleteFrame = {},
        onSubmit = {},
        onCancel = {},
    )

    @Test
    fun `the manual capture preview shows the whole frame`() {
        composeRule.setContent {
            AgarthaVisionTheme {
                ManualSheetContent(
                    state = ManualCaptureUiState(
                        frame = frame(),
                        frameIndexInQueue = 1,
                        queueSize = 1,
                        selectedSpecies = EggSpecies.ASCARIS,
                    ),
                    actions = noopActions(),
                )
            }
        }

        composeRule.onNodeWithTag(VerifyTestTags.FRAME_PREVIEW)
            .performScrollTo()
            .captureRoboImage("src/test/roborazzi/manual_capture_preview.png")
    }
}
