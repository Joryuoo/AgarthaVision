package com.agarthavision.domain.model

enum class SampleStatus(val value: String) {
    CAPTURE_STARTED("capture_started"),
    CAPTURED("captured"),
    CAPTURE_FAILED("capture_failed"),
    CAPTURE_INVALID("capture_invalid"),
    PAYLOAD_PACKAGED("payload_packaged"),
    QUEUED_FOR_UPLOAD("queued_for_upload"),
    UPLOADED("uploaded"),
    PROCESSING("processing"),
    PROCESSED("processed"),
    INFERENCE_FAILED("inference_failed"),
    PENDING_VALIDATION("pending_validation"),
    VALIDATED("validated"),
    FINDINGS_REJECTED("findings_rejected"),
    UNUSABLE("unusable"),
    REPORT_QUEUED("report_queued"),
    REPORT_GENERATED("report_generated"),
    REPORT_FAILED("report_failed"),
    QUEUED_FOR_SYNC("queued_for_sync"),
    SYNCED("synced");
}