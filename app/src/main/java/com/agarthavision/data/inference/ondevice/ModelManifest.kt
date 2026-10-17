package com.agarthavision.data.inference.ondevice

import com.google.gson.annotations.SerializedName

/**
 * Everything the on-device engine needs to know about one exported model, shipped beside it
 * in `assets/models/` as `<model_version>.json`.
 *
 * `inference/export/export_mobile.py` writes it from the model itself, so it cannot drift from
 * the binary it describes and a re-export never needs a code change. Every field has a default
 * only so Gson can construct it; [isUsable] is what decides whether a parsed manifest can drive
 * inference.
 */
data class ModelManifest(
    /** The `inference_model_version` recorded on every sample this model annotates. */
    @SerializedName("model_version") val modelVersion: String = "",
    /** The `.tflite` file beside this manifest in `assets/models/`. */
    @SerializedName("model_file") val modelFile: String = "",
    @SerializedName("precision") val precision: String = "",
    @SerializedName("input_width") val inputWidth: Int = 0,
    @SerializedName("input_height") val inputHeight: Int = 0,
    /** `[1, 4 + classes, anchors]`: box geometry rows, then one score row per class. */
    @SerializedName("output_shape") val outputShape: List<Int> = emptyList(),
    /**
     * `normalized` when the geometry rows are fractions of the input size, which is what
     * Ultralytics' TFLite head emits; `pixels` when they are input-space pixels.
     */
    @SerializedName("box_coordinates") val boxCoordinates: String = BOX_COORDINATES_NORMALIZED,
    /** Divisor applied to each 0..255 channel. 255 gives the 0..1 range the model trained on. */
    @SerializedName("normalization_scale") val normalizationScale: Float = DEFAULT_NORMALIZATION,
    @SerializedName("class_names") val classNames: List<String> = emptyList(),
    /**
     * Decode threshold, not a clinical filter.
     *
     * The head emits thousands of candidate boxes and something has to gate them. The value is
     * the one Ultralytics applies inside the cloud container, so both engines agree. Constraint
     * C7: no filtering happens after this point.
     */
    @SerializedName("conf_threshold") val confThreshold: Float = 0f,
    /** IoU above which two boxes of the same class are treated as one detection. */
    @SerializedName("iou_threshold") val iouThreshold: Float = 0f,
    /** Most detections kept per frame, matching Ultralytics' `max_det`. */
    @SerializedName("max_detections") val maxDetections: Int = 0,
) {
    val rows: Int get() = outputShape.getOrElse(1) { 0 }

    val anchors: Int get() = outputShape.getOrElse(2) { 0 }

    val normalizedBoxes: Boolean get() = boxCoordinates == BOX_COORDINATES_NORMALIZED

    /**
     * Whether this manifest describes a model the decoder can read.
     *
     * The row count is checked against the class list because a mismatch would not crash: it
     * would silently attribute one species' scores to another.
     */
    val isUsable: Boolean
        get() = modelVersion.isNotBlank() &&
            modelFile.isNotBlank() &&
            inputWidth > 0 &&
            inputHeight > 0 &&
            classNames.isNotEmpty() &&
            outputShape.size == OUTPUT_RANK &&
            rows == BOX_GEOMETRY_ROWS + classNames.size &&
            anchors > 0 &&
            maxDetections > 0

    companion object {
        const val BOX_COORDINATES_NORMALIZED = "normalized"

        /** centre-x, centre-y, width, height: the rows before the class scores. */
        const val BOX_GEOMETRY_ROWS = 4

        private const val OUTPUT_RANK = 3
        private const val DEFAULT_NORMALIZATION = 255f
    }
}
