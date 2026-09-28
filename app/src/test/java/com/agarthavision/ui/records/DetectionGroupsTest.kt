package com.agarthavision.ui.records

import com.agarthavision.domain.model.Detection
import com.agarthavision.domain.model.DetectionVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how the Sample Data Screen sorts a sample's detections by what the medtech ruled
 * (14zcqnthrx4). The split is read off the stored verdict and box; nothing is filtered out.
 */
class DetectionGroupsTest {

    @Test
    fun `a confirmed egg is confirmed`() {
        assertEquals(DetectionGroup.CONFIRMED, detection(DetectionVerdict.CONFIRMED).group)
    }

    @Test
    fun `a wrong-class egg is still a confirmed egg`() {
        assertEquals(DetectionGroup.CONFIRMED, detection(DetectionVerdict.WRONG_CLASS).group)
    }

    @Test
    fun `a rejected detection is rejected even though it keeps the model's box`() {
        val rejected = detection(DetectionVerdict.FALSE_POSITIVE)

        assertEquals(true, rejected.hasBox)
        assertEquals(DetectionGroup.REJECTED, rejected.group)
    }

    @Test
    fun `a misplaced box that was never redrawn is misplaced`() {
        assertEquals(
            DetectionGroup.MISPLACED,
            detection(DetectionVerdict.BOX_INCORRECT, boxed = false).group,
        )
    }

    @Test
    fun `a misplaced box the medtech redrew is confirmed, because the box is theirs`() {
        assertEquals(DetectionGroup.CONFIRMED, detection(DetectionVerdict.BOX_INCORRECT).group)
    }

    @Test
    fun `an added egg with no box drawn is confirmed, not misplaced`() {
        assertEquals(DetectionGroup.CONFIRMED, detection(DetectionVerdict.CONFIRMED, boxed = false).group)
    }

    @Test
    fun `a partial box is no box`() {
        val partial = detection(DetectionVerdict.BOX_INCORRECT).copy(bboxH = null)

        assertEquals(false, partial.hasBox)
        assertEquals(DetectionGroup.MISPLACED, partial.group)
    }

    @Test
    fun `groups keep each detection's index, so no confirmed egg is recoloured`() {
        val detections = listOf(
            detection(DetectionVerdict.FALSE_POSITIVE),
            detection(DetectionVerdict.CONFIRMED),
            detection(DetectionVerdict.BOX_INCORRECT, boxed = false),
            detection(DetectionVerdict.WRONG_CLASS),
            detection(DetectionVerdict.FALSE_POSITIVE),
        )

        val groups = detections.indicesByGroup()

        assertEquals(listOf(1, 3), groups[DetectionGroup.CONFIRMED])
        assertEquals(listOf(2), groups[DetectionGroup.MISPLACED])
        assertEquals(listOf(0, 4), groups[DetectionGroup.REJECTED])
        assertEquals(detections.size, groups.values.sumOf { it.size })
    }

    @Test
    fun `only rejected boxes start hidden`() {
        val detections = listOf(
            detection(DetectionVerdict.CONFIRMED),
            detection(DetectionVerdict.FALSE_POSITIVE),
            detection(DetectionVerdict.BOX_INCORRECT, boxed = false),
        )

        assertEquals(setOf(1), detections.hiddenByDefault())
    }

    @Test
    fun `nothing starts hidden on a sample with no rejections`() {
        val detections = listOf(detection(DetectionVerdict.CONFIRMED), detection(DetectionVerdict.WRONG_CLASS))

        assertEquals(emptySet<Int>(), detections.hiddenByDefault())
    }

    private fun detection(verdict: DetectionVerdict, boxed: Boolean = true): Detection =
        Detection(
            id = "detection-${verdict.name}",
            sampleId = "sample-1",
            classLabel = "Ascaris lumbricoides",
            confidence = 0.87f,
            bboxX = if (boxed) 320f else null,
            bboxY = if (boxed) 320f else null,
            bboxW = if (boxed) 40f else null,
            bboxH = if (boxed) 40f else null,
            verdict = verdict,
            expertClass = null,
        )
}
