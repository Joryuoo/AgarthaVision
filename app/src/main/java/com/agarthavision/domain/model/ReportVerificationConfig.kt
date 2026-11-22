package com.agarthavision.domain.model

import com.agarthavision.domain.usecase.records.ReportVerificationUrlBuilder

/**
 * Configuration for the external report authenticity verifier.
 *
 * Configurable via `REPORT_VERIFICATION_BASE_URL` in `local.properties` / `BuildConfig`.
 */
data class ReportVerificationConfig(
    val baseUrl: String = ReportVerificationUrlBuilder.DEFAULT_BASE_URL,
)

