package com.agarthavision.data.local.mapper

import com.agarthavision.domain.inference.Prediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The conversion between a frame's `predictions_json` list and `predictions` rows.
 *
 * The list index is the ordinal every detection id is derived from, so the only property that
 * matters here is that no conversion ever lets a prediction change position.
 */
class SamplePredictionTest {

    private fun prediction(x: Float) = Prediction(
        classLabel = "Ascaris",
        confidence = 0.8f,
        x = x,
        y = 1f,
        width = 2f,
        height = 3f,
    )

    @Test
    fun `rows take the list index as their ordinal and link to that ordinal's detection`() {
        val rows = listOf(prediction(10f), prediction(20f)).toSamplePredictions("sample-1")

        assertEquals(listOf(0, 1), rows.map { it.ordinal })
        assertEquals(detectionIdFor("sample-1", 1), rows[1].detectionId)
        assertEquals(predictionIdFor("sample-1", 1), rows[1].id)
    }

    @Test
    fun `a whole set folds back into the list it came from`() {
        val original = listOf(prediction(10f), prediction(20f), prediction(30f))

        val roundTripped = original.toSamplePredictions("sample-1").toFramePredictionsOrNull()

        assertEquals(original, roundTripped)
    }

    /** The server returns rows in no promised order. */
    @Test
    fun `rows arriving out of order are put back in ordinal order`() {
        val rows = listOf(prediction(10f), prediction(20f), prediction(30f))
            .toSamplePredictions("sample-1")
            .reversed()

        assertEquals(listOf(10f, 20f, 30f), rows.toFramePredictionsOrNull()?.map { it.x })
    }

    /**
     * A hole would shift every later prediction one index early onto its neighbour's
     * detection. No list is the safe answer.
     */
    @Test
    fun `a set with a missing ordinal yields no list`() {
        val rows = listOf(
            SamplePrediction("sample-1", 0, prediction(10f)),
            SamplePrediction("sample-1", 2, prediction(30f)),
        )

        assertNull(rows.toFramePredictionsOrNull())
    }

    @Test
    fun `a set that does not start at zero yields no list`() {
        assertNull(listOf(SamplePrediction("sample-1", 1, prediction(10f))).toFramePredictionsOrNull())
    }

    @Test
    fun `no rows yields no list`() {
        assertNull(emptyList<SamplePrediction>().toFramePredictionsOrNull())
    }
}
