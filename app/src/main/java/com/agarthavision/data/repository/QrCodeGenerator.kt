package com.agarthavision.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Utility for rendering black-and-white QR code [Bitmap]s using ZXing.
 *
 * Configured with [ErrorCorrectionLevel.M] (15% error recovery) and a 1-module margin
 * to maximize print readability on direct smear laboratory PDF reports.
 */
object QrCodeGenerator {

    private const val DEFAULT_QR_SIZE_PX = 200
    private const val QUIET_ZONE_MODULES = 1

    fun generateQrBitmap(
        content: String,
        sizePx: Int = DEFAULT_QR_SIZE_PX,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE,
    ): Bitmap {
        require(content.isNotBlank()) { "QR content must not be blank" }
        require(sizePx > 0) { "sizePx must be positive" }

        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

