package com.agarthavision.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.agarthavision.R
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfChromeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `drawVerificationQrCode renders QR bitmap and record check copy`() {
        val mockCanvas = mock<Canvas>()
        val testUrl = "https://agarthan-admin-staging.vercel.app/verify/report?id=test-rep-1"
        val topY = 200f

        val bottomY = PdfChrome.drawVerificationQrCode(
            context = context,
            canvas = mockCanvas,
            verificationUrl = testUrl,
            topY = topY,
        )

        // 1. Verify QR bitmap is drawn at >= 72 pt (~25.4 mm) square
        val rectCaptor = argumentCaptor<RectF>()
        verify(mockCanvas).drawBitmap(any<Bitmap>(), anyOrNull(), rectCaptor.capture(), anyOrNull())
        val drawnRect = rectCaptor.firstValue
        assertEquals(topY, drawnRect.top, 0.01f)
        assertTrue("QR code must be at least 72 pt wide", drawnRect.width() >= 72f)
        assertEquals(drawnRect.width(), drawnRect.height(), 0.01f)

        // 2. Verify softened record-check copy is drawn
        val scanToVerify = context.getString(R.string.report_pdf_qr_scan_to_verify)
        val recordCheck = context.getString(R.string.report_pdf_qr_record_check)
        val textCaptor = argumentCaptor<String>()
        verify(mockCanvas, atLeastOnce()).drawText(textCaptor.capture(), any(), any(), any<Paint>())
        assertTrue(textCaptor.allValues.contains(scanToVerify))
        val drawnBody = textCaptor.allValues.filterNot { it == scanToVerify }.joinToString(" ")
        assertEquals(recordCheck, drawnBody)

        // 3. Verify returned bottom coordinate accounts for the QR code display size
        assertTrue(bottomY > topY + drawnRect.height())
    }

    @Test
    fun `drawReviewerSignoff QR code decodes with ZXing at 72 dpi`() {
        val stagingUrl =
            "https://agarthan-admin-staging.vercel.app/verify/report?id=12345678-1234-1234-1234-1234567890ab"
        val bitmap = Bitmap.createBitmap(PdfChrome.PAGE_WIDTH, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        PdfChrome.drawReviewerSignoff(
            context = context,
            canvas = canvas,
            params = ReviewerSignoffParams(
                reviewerName = "Maria Clara Medtech",
                reviewerEmail = "maria@clinic.ph",
                organizationName = "Barangay Health Center",
                verificationUrl = stagingUrl,
            ),
            startY = 20f,
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        bitmap.recycle()

        assertEquals(stagingUrl, decoded.text)
    }

    @Test
    fun `drawReviewerSignoff draws reviewer credentials and accommodates QR code`() {
        val mockCanvas = mock<Canvas>()
        val params = ReviewerSignoffParams(
            reviewerName = "Maria Clara Medtech",
            reviewerEmail = "maria@clinic.ph",
            organizationName = "Barangay Health Center",
            verificationUrl = "https://agarthan-admin-staging.vercel.app/verify/report?id=test-rep-1",
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
