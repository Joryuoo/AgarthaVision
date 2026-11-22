package com.agarthavision.domain.usecase.records

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportVerificationUrlBuilderTest {

    @Test
    fun `buildSessionReportUrl formats complete verification URL`() {
        val instant = Instant.ofEpochSecond(1763630400L)
        val url = ReportVerificationUrlBuilder.buildSessionReportUrl(
            reportId = "rep-1234-uuid",
            sessionId = "ses-5678-uuid",
            generatedAt = instant,
        )

        assertEquals(
            "https://agarthan-admin.vercel.app/verify/report?id=rep-1234-uuid&session=ses-5678-uuid&ts=1763630400",
            url,
        )
    }

    @Test
    fun `buildPatientReportUrl with patientId formats complete verification URL`() {
        val instant = Instant.ofEpochSecond(1763630400L)
        val url = ReportVerificationUrlBuilder.buildPatientReportUrl(
            reportId = "rep-patient-1",
            patientId = "pat-uuid-99",
            generatedAt = instant,
        )

        assertEquals(
            "https://agarthan-admin.vercel.app/verify/report?id=rep-patient-1&patient=pat-uuid-99&ts=1763630400",
            url,
        )
    }

    @Test
    fun `buildPatientReportUrl without patientId omits patient param`() {
        val instant = Instant.ofEpochSecond(1763630400L)
        val url = ReportVerificationUrlBuilder.buildPatientReportUrl(
            reportId = "rep-patient-1",
            patientId = null,
            generatedAt = instant,
        )

        assertEquals(
            "https://agarthan-admin.vercel.app/verify/report?id=rep-patient-1&ts=1763630400",
            url,
        )
    }

    @Test
    fun `custom baseUrl containing existing query separator uses ampersand`() {
        val instant = Instant.ofEpochSecond(1763630400L)
        val url = ReportVerificationUrlBuilder.buildSessionReportUrl(
            reportId = "rep-1",
            sessionId = "ses-1",
            generatedAt = instant,
            baseUrl = "https://example.com/api?env=staging",
        )

        assertTrue(url.startsWith("https://example.com/api?env=staging&id=rep-1"))
    }

    @Test
    fun `special characters in report identifiers are URL encoded`() {
        val instant = Instant.ofEpochSecond(1763630400L)
        val url = ReportVerificationUrlBuilder.buildSessionReportUrl(
            reportId = "rep+123/special&char",
            sessionId = "ses 123",
            generatedAt = instant,
        )

        assertTrue(url.contains("id=rep%2B123%2Fspecial%26char"))
        assertTrue(url.contains("session=ses+123"))
    }
}

