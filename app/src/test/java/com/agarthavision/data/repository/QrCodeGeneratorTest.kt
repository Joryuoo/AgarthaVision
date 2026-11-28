package com.agarthavision.data.repository

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrCodeGeneratorTest {

    @Test
    fun `generateQrBitmap with valid URL produces square bitmap with expected dimensions`() {
        val url = "https://agarthan-admin-staging.vercel.app/verify/report?id=123"
        val bitmap = QrCodeGenerator.generateQrBitmap(url, sizePx = 150)

        assertNotNull(bitmap)
        assertEquals(150, bitmap.width)
        assertEquals(150, bitmap.height)
    }

    @Test
    fun `generateQrBitmap with blank content throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            QrCodeGenerator.generateQrBitmap("   ")
        }
    }

    @Test
    fun `generateQrBitmap with non-positive size throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            QrCodeGenerator.generateQrBitmap("https://example.com", sizePx = 0)
        }
    }

    @Test
    fun `generateQrBitmap contains both dark and light pixels`() {
        val url = "https://agarthan-admin.vercel.app/verify/report?id=rep-test"
        val bitmap = QrCodeGenerator.generateQrBitmap(
            content = url,
            sizePx = 100,
            darkColor = Color.BLACK,
            lightColor = Color.WHITE,
        )

        var hasDark = false
        var hasLight = false

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel == Color.BLACK) hasDark = true
                if (pixel == Color.WHITE) hasLight = true
            }
        }

        assertTrue("Generated QR code must contain black modules", hasDark)
        assertTrue("Generated QR code must contain white background modules", hasLight)
    }
}

