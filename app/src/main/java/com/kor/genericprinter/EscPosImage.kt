package com.kor.genericprinter

import android.graphics.Bitmap
import kotlin.math.roundToInt

object EscPosImage {

    /**
     * Converts bitmap to ESC/POS raster command: GS v 0
     * mode 0 = normal
     */
    fun toRasterBytes(
        bitmap: Bitmap,
        targetWidthPx: Int,
        threshold: Int = 160
    ): ByteArray {
        val resized = resizeToWidth(bitmap, targetWidthPx)

        val width = resized.width
        val height = resized.height
        val bytesPerRow = (width + 7) / 8

        val header = ByteArray(8)
        header[0] = 0x1D
        header[1] = 'v'.code.toByte()
        header[2] = '0'.code.toByte()
        header[3] = 0x00 // normal mode
        header[4] = (bytesPerRow and 0xFF).toByte()         // xL
        header[5] = ((bytesPerRow shr 8) and 0xFF).toByte() // xH
        header[6] = (height and 0xFF).toByte()              // yL
        header[7] = ((height shr 8) and 0xFF).toByte()      // yH

        val imageData = ByteArray(bytesPerRow * height)
        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val rowOffset = y * width
            val dataOffset = y * bytesPerRow
            for (xByte in 0 until bytesPerRow) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[rowOffset + x]
                        val isBlack = isBlack(pixel, threshold)
                        if (isBlack) b = b or (1 shl (7 - bit))
                    }
                }
                imageData[dataOffset + xByte] = b.toByte()
            }
        }

        return header + imageData
    }

    private fun resizeToWidth(bitmap: Bitmap, widthPx: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == widthPx) return bitmap

        val scale = widthPx.toFloat() / w.toFloat()
        val newH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, widthPx, newH, true)
    }

    private fun isBlack(pixel: Int, threshold: Int): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 16) return false

        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val gray = ((r * 299) + (g * 587) + (b * 114)) / 1000
        return gray < threshold
    }
}
