package com.agarthavision.domain.usecase.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportVerificationUrlBuilderTest {

    @Test
    fun `buildReportUrl formats compact verification URL with report id only`() {
        val url = ReportVerificationUrlBuilder.buildReportUrl(
            reportId = "rep-1234-uuid",
        )

        assertEquals(
            "https://agarthan-admin.vercel.app/verify/report?id=rep-1234-uuid",
            url,
        )
    }

    @Test
    fun `custom baseUrl without query uses question mark separator`() {
        val url = ReportVerificationUrlBuilder.buildReportUrl(
            reportId = "rep-patient-1",
            baseUrl = "https://agarthan-admin-staging.vercel.app/verify/report",
        )

        assertEquals(
            "https://agarthan-admin-staging.vercel.app/verify/report?id=rep-patient-1",
            url,
        )
    }

    @Test
    fun `custom baseUrl containing existing query separator uses ampersand`() {
        val url = ReportVerificationUrlBuilder.buildReportUrl(
            reportId = "rep-1",
            baseUrl = "https://example.com/api?env=staging",
        )

        assertEquals("https://example.com/api?env=staging&id=rep-1", url)
    }

    @Test
    fun `special characters in report identifiers are URL encoded`() {
        val url = ReportVerificationUrlBuilder.buildReportUrl(
            reportId = "rep+123/special&char",
        )

        assertTrue(url.endsWith("?id=rep%2B123%2Fspecial%26char"))
    }
}
