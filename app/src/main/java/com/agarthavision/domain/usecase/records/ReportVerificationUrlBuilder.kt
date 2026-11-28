package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.ReportVerificationPayload
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds the verification URL encoded into a report's QR code.
 *
 * Adheres strictly to Constraint C10: zero signing keys or secrets are embedded in the client
 * APK. The URL transmits only `?id=<reportId>` so the QR stays compact and easy to scan while
 * the admin console's `/verify/report` page resolves the session or patient, findings, and
 * `generated_at` directly from the stored Supabase `reports` row.
 */
object ReportVerificationUrlBuilder {

    const val DEFAULT_BASE_URL = "https://agarthan-admin.vercel.app/verify/report"

    fun buildReportUrl(
        reportId: String,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String = build(
        ReportVerificationPayload(
            reportId = reportId,
            baseUrl = baseUrl,
        ),
    )

    fun build(payload: ReportVerificationPayload): String {
        val separator = if (payload.baseUrl.contains("?")) "&" else "?"
        return "${payload.baseUrl}${separator}id=${encode(payload.reportId)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
