package com.atomx.genericprinter

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class ReceiptBuilder(
    private val config: PrinterConfig = PrinterConfig()
) {
    private val out = ByteArrayOutputStream()

    fun reset(): ReceiptBuilder = apply {
        out.write(EscPosCommands.INIT)
    }

    fun align(alignment: PrinterAlignment): ReceiptBuilder = apply {
        out.write(alignment.command)
    }

    fun text(text: String, newLine: Boolean = true): ReceiptBuilder = apply {
        out.write(EscPosCommands.textUtf8(text))
        if (newLine) out.write(EscPosCommands.LF)
    }

    fun line(text: String = ""): ReceiptBuilder = text(text, newLine = true)

    fun bitmap(bitmap: Bitmap, center: Boolean = true): ReceiptBuilder = apply {
        out.write(if (center) EscPosCommands.ALIGN_CENTER else EscPosCommands.ALIGN_LEFT)
        out.write(
            EscPosImage.toRasterBytes(
                bitmap = bitmap,
                targetWidthPx = config.paperWidthPx,
                threshold = config.imageThreshold
            )
        )
        out.write(EscPosCommands.LF)
    }

    fun raw(bytes: ByteArray): ReceiptBuilder = apply {
        out.write(bytes)
    }

    fun feed(lines: Int = 3): ReceiptBuilder = apply {
        out.write(EscPosCommands.feed(lines))
    }

    fun cut(partial: Boolean = true): ReceiptBuilder = apply {
        out.write(if (partial) EscPosCommands.CUT_PARTIAL else EscPosCommands.CUT_FULL)
    }

    fun build(): ByteArray = out.toByteArray()
}
