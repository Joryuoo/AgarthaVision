package com.agarthavision.data.inference.ondevice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloOutputDecoderTest {

    private val decoder = YoloOutputDecoder()

    private val classNames = listOf("Ascaris lumbricoides", "Hookworm", "Trichuris trichiura")

    private fun manifest(anchors: Int, maxDetections: Int = 300, normalized: Boolean = true) = ModelManifest(
        modelVersion = "yolo26n-effv2b0-v1-tflite-fp32",
        modelFile = "yolo26n-effv2b0-v1-tflite-fp32.tflite",
        inputWidth = 640,
        inputHeight = 640,
        outputShape = listOf(1, 4 + classNames.size, anchors),
        boxCoordinates = if (normalized) "normalized" else "pixels",
        classNames = classNames,
        confThreshold = 0.25f,
        iouThreshold = 0.7f,
        maxDetections = maxDetections,
    )

    private val identity = LetterboxTransform.fit(640, 640, 640, 640)

    /** A candidate in model-input pixels, with one class scored and the others at zero. */
    private data class Box(
        val centreX: Float,
        val centreY: Float,
        val width: Float,
        val height: Float,
        val classIndex: Int,
        val score: Float,
    )

    /** Lays [boxes] out as the head does: `[4 + classes][anchors]`, row-major, flattened. */
    private fun headOutput(vararg boxes: Box, normalized: Boolean = true): FloatArray {
        val anchors = boxes.size
        val output = FloatArray((4 + classNames.size) * anchors)
        val divisor = if (normalized) 640f else 1f
        boxes.forEachIndexed { anchor, box ->
            output[anchor] = box.centreX / divisor
            output[anchors + anchor] = box.centreY / divisor
            output[2 * anchors + anchor] = box.width / divisor
            output[3 * anchors + anchor] = box.height / divisor
            output[(4 + box.classIndex) * anchors + anchor] = box.score
        }
        return output
    }

    @Test
    fun `normalised geometry decodes to centre-based source pixels`() {
        val output = headOutput(Box(320f, 240f, 80f, 60f, classIndex = 0, score = 0.9f))

        val prediction = decoder.decode(output, identity, manifest(anchors = 1)).single()

        assertEquals("Ascaris lumbricoides", prediction.classLabel)
        assertEquals(0.9f, prediction.confidence, TOLERANCE)
        assertEquals(320f, prediction.x, TOLERANCE)
        assertEquals(240f, prediction.y, TOLERANCE)
        assertEquals(80f, prediction.width, TOLERANCE)
        assertEquals(60f, prediction.height, TOLERANCE)
    }

    @Test
    fun `pixel geometry is read as is`() {
        val output = headOutput(Box(320f, 240f, 80f, 60f, 1, 0.9f), normalized = false)

        val prediction = decoder.decode(output, identity, manifest(anchors = 1, normalized = false)).single()

        assertEquals("Hookworm", prediction.classLabel)
        assertEquals(320f, prediction.x, TOLERANCE)
        assertEquals(80f, prediction.width, TOLERANCE)
    }

    @Test
    fun `boxes at or below the threshold are gated out`() {
        val output = headOutput(
            Box(100f, 100f, 40f, 40f, 0, 0.25f),
            Box(300f, 300f, 40f, 40f, 0, 0.24f),
            Box(500f, 500f, 40f, 40f, 0, 0.26f),
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 3))

        assertEquals(1, predictions.size)
        assertEquals(500f, predictions.single().x, TOLERANCE)
    }

    @Test
    fun `each box takes its best class, and only that one`() {
        val output = headOutput(Box(320f, 320f, 40f, 40f, 0, 0.4f))
        // Same anchor, a stronger Trichuris score.
        output[(4 + 2) * 1 + 0] = 0.8f

        val prediction = decoder.decode(output, identity, manifest(anchors = 1)).single()

        assertEquals("Trichuris trichiura", prediction.classLabel)
        assertEquals(0.8f, prediction.confidence, TOLERANCE)
    }

    @Test
    fun `NMS keeps the strongest of heavily overlapping boxes of one class`() {
        val output = headOutput(
            Box(320f, 320f, 100f, 100f, 0, 0.9f),
            Box(322f, 321f, 100f, 100f, 0, 0.8f), // IoU ~0.95 with the first
            Box(318f, 320f, 98f, 102f, 0, 0.7f),  // IoU ~0.93 with the first
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 3))

        assertEquals(1, predictions.size)
        assertEquals(0.9f, predictions.single().confidence, TOLERANCE)
    }

    @Test
    fun `NMS keeps overlapping boxes below the IoU threshold`() {
        // Two touching eggs: 100-wide boxes 50 px apart overlap at IoU 1/3.
        val output = headOutput(
            Box(300f, 320f, 100f, 100f, 0, 0.9f),
            Box(350f, 320f, 100f, 100f, 0, 0.8f),
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 2))

        assertEquals(2, predictions.size)
    }

    @Test
    fun `NMS keeps only the strongest species when boxes on one egg disagree`() {
        // One egg read three ways, as on sample ec940c6e: near-identical boxes, one per species.
        val output = headOutput(
            Box(278f, 130f, 197f, 173f, 2, 0.50f),
            Box(276f, 131f, 202f, 181f, 1, 0.44f),
            Box(278f, 129f, 203f, 184f, 0, 0.29f),
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 3))

        assertEquals(listOf("Trichuris trichiura"), predictions.map { it.classLabel })
        assertEquals(0.50f, predictions.single().confidence, TOLERANCE)
    }

    @Test
    fun `NMS keeps separate eggs of different species`() {
        // Two touching eggs of different species: IoU 1/3, under the threshold.
        val output = headOutput(
            Box(300f, 320f, 100f, 100f, 0, 0.9f),
            Box(350f, 320f, 100f, 100f, 2, 0.8f),
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 2))

        assertEquals(listOf("Ascaris lumbricoides", "Trichuris trichiura"), predictions.map { it.classLabel })
    }

    @Test
    fun `results are ordered by score and capped at max detections`() {
        val output = headOutput(
            Box(100f, 100f, 20f, 20f, 0, 0.5f),
            Box(200f, 200f, 20f, 20f, 1, 0.9f),
            Box(300f, 300f, 20f, 20f, 2, 0.7f),
        )

        val predictions = decoder.decode(output, identity, manifest(anchors = 3, maxDetections = 2))

        assertEquals(listOf(0.9f, 0.7f), predictions.map { it.confidence })
    }

    @Test
    fun `boxes are mapped back through the letterbox`() {
        // A 1280x720 source letterboxes at scale 0.5 with 140 px of padding on top.
        val transform = LetterboxTransform.fit(1280, 720, 640, 640)
        val output = headOutput(Box(320f, 320f, 50f, 40f, 0, 0.9f))

        val prediction = decoder.decode(output, transform, manifest(anchors = 1)).single()

        assertEquals(640f, prediction.x, TOLERANCE)
        assertEquals(360f, prediction.y, TOLERANCE)
        assertEquals(100f, prediction.width, TOLERANCE)
        assertEquals(80f, prediction.height, TOLERANCE)
    }

    @Test
    fun `a box overhanging the frame edge is clipped, as the server clips`() {
        // Spans x 600..680 in a 640-wide frame: clipped to 600..640.
        val output = headOutput(Box(640f, 320f, 80f, 40f, 0, 0.9f))

        val prediction = decoder.decode(output, identity, manifest(anchors = 1)).single()

        assertEquals(620f, prediction.x, TOLERANCE)
        assertEquals(40f, prediction.width, TOLERANCE)
        assertTrue(prediction.x + prediction.width / 2 <= 640f + TOLERANCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an output that disagrees with the manifest is rejected, not misread`() {
        decoder.decode(FloatArray(10), identity, manifest(anchors = 8400))
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
