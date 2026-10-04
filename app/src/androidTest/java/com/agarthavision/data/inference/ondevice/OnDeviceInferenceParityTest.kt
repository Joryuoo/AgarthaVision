package com.agarthavision.data.inference.ondevice

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agarthavision.domain.inference.Prediction
import com.agarthavision.domain.usecase.inference.InferenceConnectionException
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The on-device engine against the cloud model, on the phone, on byte-identical frames.
 *
 * Frames and the cloud reference come from `inference/export/make_parity_fixture.py`, which
 * writes them into this source set's gitignored `assets/fixtures/`. Without them the parity
 * tests are skipped, not failed.
 *
 * The model also logs its per-frame latency (tag `OnDeviceParity`), which is what a choice
 * between precisions or backbones is made from. Read it with
 * `adb logcat -d -s OnDeviceParity OnDeviceInference`.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceInferenceParityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val gson = Gson()

    private fun engine(modelName: String) = OnDeviceInferenceEngine(
        ModelStore(instrumentation.targetContext, gson),
        FramePreprocessor(),
        YoloOutputDecoder(),
        modelName,
    )

    @Test
    fun shippedModelMatchesTheCloudModel() = assertParity(OnDeviceModels.SHIPPED)

    @Test
    fun aMissingModelReportsUnavailableInsteadOfCrashing() = runBlocking {
        val engine = engine("no-such-model")

        assertFalse(engine.isAvailable())
        val thrown = runCatching { engine.infer(ByteArray(0)) }.exceptionOrNull()
        assertTrue("Expected InferenceConnectionException, got $thrown", thrown is InferenceConnectionException)
    }

    private fun assertParity(modelName: String) = runBlocking {
        val reference = loadReference()
        assumeTrue("No parity fixtures; run make_parity_fixture.py first", reference != null)
        reference!!

        val engine = engine(modelName)
        assertTrue("$modelName is not bundled or failed to load", engine.isAvailable())

        // The first run pays for GPU program compilation. Keep it out of the latency numbers.
        engine.infer(frameBytes(reference.frames.first().file))

        val tally = Tally()
        val inferMs = ArrayList<Long>()
        val totalMs = ArrayList<Long>()
        var modelVersion = ""
        reference.frames.forEach { frame ->
            val result = engine.infer(frameBytes(frame.file))
            modelVersion = result.modelVersion.orEmpty()
            inferMs += result.timings.inferMs
            totalMs += result.timings.totalMs
            tally.compare(frame.predictions.map { it.toPrediction() }, result.predictions)
        }

        Log.i(
            TAG,
            "$modelVersion: ${reference.frames.size} frames, ${tally.summary()}, " +
                "infer median=${inferMs.median()} p90=${inferMs.p90()} ms, " +
                "total median=${totalMs.median()} p90=${totalMs.p90()} ms",
        )

        assertEquals(modelName, modelVersion)
        assertTrue("No reference boxes to compare", tally.referenceTotal > 0)
        assertTrue("Miss rate ${tally.missRate} above $MAX_MISS_RATE", tally.missRate <= MAX_MISS_RATE)
        assertTrue("Mean IoU ${tally.meanIou} below $MIN_MEAN_IOU", tally.meanIou >= MIN_MEAN_IOU)
        assertTrue("Class agreement ${tally.classAgreement} below 1", tally.classAgreement == 1f)
        assertTrue(
            "Mean confidence delta ${tally.meanConfidenceDelta} above $MAX_MEAN_CONFIDENCE_DELTA",
            tally.meanConfidenceDelta <= MAX_MEAN_CONFIDENCE_DELTA,
        )
    }

    private fun loadReference(): Reference? = runCatching {
        instrumentation.context.assets.open("$FIXTURE_DIR/parity_reference.json")
            .bufferedReader().use { gson.fromJson(it, Reference::class.java) }
    }.getOrNull()?.takeIf { it.frames.isNotEmpty() }

    private fun frameBytes(file: String): ByteArray =
        instrumentation.context.assets.open("$FIXTURE_DIR/$file").use { it.readBytes() }

    /**
     * Pairs each cloud box with its best-overlapping unclaimed device box. Missed and invented
     * boxes are counted separately on purpose: a miss silently lowers an egg count, an invented
     * box is caught during verification, and netting them off would hide the dangerous one.
     */
    private class Tally {
        var referenceTotal = 0
        private var matched = 0
        private var missed = 0
        private var invented = 0
        private var classAgreements = 0
        private val ious = ArrayList<Float>()
        private val confidenceDeltas = ArrayList<Float>()

        val missRate: Float get() = if (referenceTotal == 0) 0f else missed.toFloat() / referenceTotal
        val meanIou: Float get() = ious.averageOrZero()
        val meanConfidenceDelta: Float get() = confidenceDeltas.averageOrZero()
        val classAgreement: Float get() = if (matched == 0) 0f else classAgreements.toFloat() / matched

        fun compare(cloud: List<Prediction>, device: List<Prediction>) {
            referenceTotal += cloud.size
            val unclaimed = device.toMutableList()
            cloud.forEach { reference ->
                // Same species first, so a box whose species differs between the engines only
                // pairs when nothing of its own species is left.
                val sameClass = unclaimed.filter { it.classLabel == reference.classLabel }
                val best = sameClass.bestMatch(reference) ?: unclaimed.bestMatch(reference)
                val overlap = best?.let { iou(reference, it) } ?: 0f
                if (best != null) {
                    unclaimed.remove(best)
                    matched++
                    ious += overlap
                    confidenceDeltas += kotlin.math.abs(reference.confidence - best.confidence)
                    if (reference.classLabel == best.classLabel) classAgreements++
                } else {
                    missed++
                }
            }
            invented += unclaimed.size
        }

        fun summary(): String =
            "reference=$referenceTotal matched=$matched missed=$missed invented=$invented " +
                "meanIoU=${"%.4f".format(meanIou)} meanConfDelta=${"%.4f".format(meanConfidenceDelta)} " +
                "classAgreement=${"%.3f".format(classAgreement)}"

        private fun List<Prediction>.bestMatch(reference: Prediction): Prediction? =
            maxByOrNull { iou(reference, it) }?.takeIf { iou(reference, it) >= MATCH_IOU }

        private fun iou(a: Prediction, b: Prediction): Float {
            val overlapWidth = minOf(a.x + a.width / 2, b.x + b.width / 2) - maxOf(a.x - a.width / 2, b.x - b.width / 2)
            val overlapHeight =
                minOf(a.y + a.height / 2, b.y + b.height / 2) - maxOf(a.y - a.height / 2, b.y - b.height / 2)
            if (overlapWidth <= 0f || overlapHeight <= 0f) return 0f
            val intersection = overlapWidth * overlapHeight
            return intersection / (a.width * a.height + b.width * b.height - intersection)
        }

        private fun List<Float>.averageOrZero(): Float = if (isEmpty()) 0f else average().toFloat()
    }

    private data class Reference(
        @SerializedName("model_version") val modelVersion: String,
        @SerializedName("frames") val frames: List<ReferenceFrame>,
    )

    private data class ReferenceFrame(
        @SerializedName("file") val file: String,
        @SerializedName("predictions") val predictions: List<ReferenceBox>,
    )

    private data class ReferenceBox(
        @SerializedName("class") val classLabel: String,
        @SerializedName("confidence") val confidence: Float,
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("width") val width: Float,
        @SerializedName("height") val height: Float,
    ) {
        fun toPrediction() = Prediction(classLabel, confidence, x, y, width, height)
    }

    private companion object {
        const val TAG = "OnDeviceParity"
        const val FIXTURE_DIR = "fixtures"

        // Same pairing rule and limits as inference/export/parity_check.py, plus a tolerance on
        // confidence, since the medtech sees it.
        const val MATCH_IOU = 0.5f
        const val MAX_MISS_RATE = 0.10f
        const val MIN_MEAN_IOU = 0.90f
        const val MAX_MEAN_CONFIDENCE_DELTA = 0.05f

        fun List<Long>.median(): Long = sorted()[size / 2]

        @Suppress("MagicNumber")
        fun List<Long>.p90(): Long = sorted()[((size - 1) * 0.9).toInt()]
    }
}
