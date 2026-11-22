package com.agarthavision.domain.model

import java.time.Instant

/**
 * Encapsulates the public identification reference encoded into a clinical report's QR code.
 *
 * Per consultation with Dr. Bayron, reports delivered directly to patients bypass physical
 * laboratory handoff. The QR code provides a cryptographic/server-resolvable reference so that
 * the receiving physician can confirm the printed/PDF findings match the authentic record in
 * Supabase without relying on easily falsified client-only claims.
 */
data class ReportVerificationPayload(
    val reportId: String,
    val sessionId: String? = null,
    val patientId: String? = null,
    val generatedAt: Instant,
    val baseUrl: String = "https://agarthan-admin.vercel.app/verify/report",
)

