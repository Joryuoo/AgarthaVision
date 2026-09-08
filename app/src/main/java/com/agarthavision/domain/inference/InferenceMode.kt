package com.agarthavision.domain.inference

/**
 * The medtech's inference backend preference, persisted in DataStore.
 *
 * [AUTO] is the default: the cloud model is more accurate, so it is preferred whenever it is
 * reachable, and the device takes over when it is not. The explicit modes exist for
 * benchmarking and for deployments that deliberately pin one backend.
 */
enum class InferenceMode(val value: String) {
    /** Cloud when reachable, on-device otherwise. Falls back on a connection failure. */
    AUTO("auto"),

    /** Never use the device, even when the container is unreachable. */
    CLOUD_ONLY("cloud_only"),

    /** Never call the container, even when online. */
    ON_DEVICE_ONLY("on_device_only"),
    ;

    companion object {
        val DEFAULT = AUTO

        fun fromValue(value: String?): InferenceMode = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
