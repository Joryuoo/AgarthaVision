package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import com.agarthavision.domain.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins which detection a sample card is labelled with. A rejected detection must never name the
 * sample: a card reading "Ascaris lumbricoides · 87%" over a frame the medtech ruled negative
 * states the opposite of the verified result.
 */
class PrimaryDetectionTest {

    @Test
    fun `the most confident kept detection names the sample`() {
        val item = item(
            detection("a", DetectionVerdict.CONFIRMED, confidence = 0.6f),
            detection("b", DetectionVerdict.WRONG_CLASS, confidence = 0.8f),
        )

        assertEquals("b", item.primaryDetection?.id)
    }

    @Test
    fun `a rejected detection is passed over even when it is the most confident`() {
        val item = item(
            detection("rejected", DetectionVerdict.FALSE_POSITIVE, confidence = 0.95f),
            detection("kept", DetectionVerdict.CONFIRMED, confidence = 0.4f),
        )

        assertEquals("kept", item.primaryDetection?.id)
    }

    @Test
    fun `a sample whose every detection was rejected has no primary detection`() {
        val item = item(
            detection("a", DetectionVerdict.FALSE_POSITIVE, confidence = 0.87f),
            detection("b", DetectionVerdict.FALSE_POSITIVE, confidence = 0.52f),
        )

        assertNull(item.primaryDetection)
    }

    @Test
    fun `a sample with no detections has no primary detection`() {
        assertNull(item().primaryDetection)
    }

    private fun item(vararg detections: Detection): SampleRecordItem =
        SampleRecordItem(
            sample = Sample(
                id = "sample-1",
                userId = "user-1",
                deviceId = "device-1",
                sessionId = "session-1",
                filePath = "/frames/sample-1.jpg",
            ),
            detections = detections.toList(),
        )

    private fun detection(id: String, verdict: DetectionVerdict, confidence: Float): Detection =
        Detection(
            id = id,
            sampleId = "sample-1",
            classLabel = "Ascaris lumbricoides",
            confidence = confidence,
            bboxX = 320f,
            bboxY = 320f,
            bboxW = 40f,
            bboxH = 40f,
            verdict = verdict,
            expertClass = null,
        )
}
