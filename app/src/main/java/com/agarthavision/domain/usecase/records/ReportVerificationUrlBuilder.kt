package com.agarthavision.domain.usecase.records

import com.agarthavision.domain.model.ReportVerificationPayload
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Builds the tamper-evident, admin-resolvable URL encoded into a report's QR code.
 *
 * Adheres strictly to Constraint C10: zero signing keys or secrets are embedded in the client
 * APK. The URL directs the physician to the Admin Portal verifier (86d43e3vu), transmitting
 * public identifier references ([reportId], [sessionId] or [patientId], and [generatedAt])
 * that the server resolves and attests directly against the stored Supabase record.
 */
object ReportVerificationUrlBuilder {

    const val DEFAULT_BASE_URL = "https://agarthavision.vercel.app/verify/report"

    fun buildSessionReportUrl(
        reportId: String,
        sessionId: String,
        generatedAt: Instant,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String {
        return build(
            ReportVerificationPayload(
                reportId = reportId,
                sessionId = sessionId,
                patientId = null,
                generatedAt = generatedAt,
                baseUrl = baseUrl,
            ),
        )
    }

    fun buildPatientReportUrl(
        reportId: String,
        patientId: String? = null,
        generatedAt: Instant,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String {
        return build(
            ReportVerificationPayload(
                reportId = reportId,
                sessionId = null,
                patientId = patientId,
                generatedAt = generatedAt,
                baseUrl = baseUrl,
            ),
        )
    }

    fun build(payload: ReportVerificationPayload): String {
        val queryParams = mutableListOf<String>()
        queryParams.add("id=${encode(payload.reportId)}")
        payload.sessionId?.let { queryParams.add("session=${encode(it)}") }
        payload.patientId?.let { queryParams.add("patient=${encode(it)}") }
        queryParams.add("ts=${payload.generatedAt.epochSecond}")

        val separator = if (payload.baseUrl.contains("?")) "&" else "?"
        return "${payload.baseUrl}$separator${queryParams.joinToString("&")}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

