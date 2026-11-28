package com.agarthavision.domain.model

import com.agarthavision.domain.usecase.records.ReportVerificationUrlBuilder

/**
 * Encapsulates the public report identifier encoded into a clinical report's QR code.
 *
 * The QR code carries only the persisted [reportId] so a clinician can open the laboratory's
 * stored record and compare it against the printed or shared PDF findings.
 */
data class ReportVerificationPayload(
    val reportId: String,
    val baseUrl: String = ReportVerificationUrlBuilder.DEFAULT_BASE_URL,
)
