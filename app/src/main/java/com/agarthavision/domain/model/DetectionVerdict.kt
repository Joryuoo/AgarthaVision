package com.agarthavision.domain.model

/**
 * Expert verdict attached to a single model-predicted detection.
 *
 * Local values stay lowercase to match the mobile state model; Supabase receives
 * the uppercase [remoteValue] required by the ADR-004 migration constraint.
 */
enum class DetectionVerdict(
    val value: String,
    val remoteValue: String,
) {
    CONFIRMED("confirmed", "CONFIRMED"),
    FALSE_POSITIVE("false_positive", "FALSE_POSITIVE"),
    WRONG_CLASS("wrong_class", "WRONG_CLASS"),
    BOX_INCORRECT("box_incorrect", "BOX_INCORRECT"),
    ;

    companion object {
        /**
         * Parses either a local lowercase value or remote uppercase value.
         */
        fun fromValue(value: String): DetectionVerdict =
            entries.firstOrNull { verdict ->
                verdict.value == value || verdict.remoteValue == value
            } ?: CONFIRMED
    }
}
