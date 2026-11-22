package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfChromeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `drawVerificationQrCode renders QR bitmap and clinical attestation copy`() {
        val mockCanvas = mock<Canvas>()
        val testUrl = "https://agarthan-admin.vercel.app/verify/report?id=test-rep-1&ts=1762344000"
        val topY = 200f

        val bottomY = PdfChrome.drawVerificationQrCode(
            context = context,
            canvas = mockCanvas,
            verificationUrl = testUrl,
            topY = topY,
        )

        // 1. Verify QR bitmap is drawn to the designated rectangle
        val rectCaptor = argumentCaptor<RectF>()
        verify(mockCanvas).drawBitmap(any<Bitmap>(), anyOrNull(), rectCaptor.capture(), anyOrNull())
        val drawnRect = rectCaptor.firstValue
        assertEquals(topY, drawnRect.top, 0.01f)
        assertTrue("QR code must have positive width", drawnRect.width() > 0f)
        assertEquals(drawnRect.width(), drawnRect.height(), 0.01f)

        // 2. Verify all clinical attestation texts are drawn
        val scanToVerify = context.getString(R.string.report_pdf_qr_scan_to_verify)
        val authenticity = context.getString(R.string.report_pdf_qr_authenticity_label)
        val notice = context.getString(R.string.report_pdf_qr_tamper_notice)

        verify(mockCanvas).drawText(eq(scanToVerify), any(), any(), any<Paint>())
        verify(mockCanvas).drawText(eq(authenticity), any(), any(), any<Paint>())
        verify(mockCanvas).drawText(eq(notice), any(), any(), any<Paint>())

        // 3. Verify returned bottom coordinate accounts for the QR code display size
        assertTrue(bottomY > topY + drawnRect.height())
    }

    @Test
    fun `drawReviewerSignoff draws reviewer credentials and accommodates QR code`() {
        val mockCanvas = mock<Canvas>()
        val params = ReviewerSignoffParams(
            reviewerName = "Maria Clara Medtech",
            reviewerEmail = "maria@clinic.ph",
            organizationName = "Barangay Health Center",
            verificationUrl = "https://agarthan-admin.vercel.app/verify/report?id=test-rep-1",
        )

        val bottomY = PdfChrome.drawReviewerSignoff(
            context = context,
            canvas = mockCanvas,
            params = params,
            startY = 400f,
        )

        // Verify reviewer credentials
        verify(mockCanvas).drawText(eq("maria@clinic.ph"), any(), any(), any<Paint>())
        verify(mockCanvas).drawText(eq("Barangay Health Center"), any(), any(), any<Paint>())

        // Verify QR code was also drawn
        verify(mockCanvas, atLeastOnce()).drawBitmap(any<Bitmap>(), anyOrNull(), any<RectF>(), anyOrNull())
        assertTrue("Bottom Y must be below startY", bottomY > 400f)
    }

    @Test
    fun `calculateSignoffHeight with verificationUrl expands height for QR block`() {
        val baseHeight = PdfChrome.calculateSignoffHeight(
            organizationName = "Health Center",
            reviewerEmail = "maria@clinic.ph",
            hasVerificationUrl = false,
        )
        val qrHeight = PdfChrome.calculateSignoffHeight(
            organizationName = "Health Center",
            reviewerEmail = "maria@clinic.ph",
            hasVerificationUrl = true,
        )

        assertTrue("Height with QR code must be greater than base height", qrHeight >= baseHeight)
    }
}

