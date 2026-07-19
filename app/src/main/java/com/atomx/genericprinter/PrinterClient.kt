package com.atomx.genericprinter

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

class PrinterClient(
    private val connection: PrinterConnection,
    private val config: PrinterConfig = PrinterConfig()
) : Closeable {

    init {
        PrinterLogger.enabled = config.debug
    }

    suspend fun connectAsync(): Boolean = withContext(Dispatchers.IO) {
        connect()
    }

    suspend fun printAsync(block: suspend PrinterClient.() -> Unit) =
        withContext(Dispatchers.IO) {
            ensureConnected()
            block()
        }

    fun connect(): Boolean {
        PrinterLogger.d("connect() called")
        return connection.connect()
    }

    fun isConnected(): Boolean = connection.isConnected()

    fun disconnect() {
        PrinterLogger.d("disconnect() called")
        connection.close()
    }

    override fun close() {
        disconnect()
    }

    fun printText(text: String, newLine: Boolean = true) {
        ensureConnected()
        connection.write(EscPosCommands.textUtf8(text))
        if (newLine) connection.write(EscPosCommands.LF)
    }

    fun printBitmap(bitmap: Bitmap, center: Boolean = true) {
        ensureConnected()
        connection.write(
            if (center) EscPosCommands.ALIGN_CENTER else EscPosCommands.ALIGN_LEFT
        )
        val bytes = EscPosImage.toRasterBytes(
            bitmap = bitmap,
            targetWidthPx = config.paperWidthPx,
            threshold = config.imageThreshold
        )
        connection.write(bytes)
        connection.write(EscPosCommands.LF)
    }

    fun printReceipt(block: ReceiptBuilder.() -> Unit) {
        ensureConnected()
        connection.write(buildReceipt(block))
    }

    fun buildReceipt(block: ReceiptBuilder.() -> Unit): ByteArray {
        return ReceiptBuilder(config).apply(block).build()
    }

    fun writeRaw(bytes: ByteArray) {
        ensureConnected()
        connection.write(bytes)
    }

    fun setAlignment(alignment: PrinterAlignment) {
        ensureConnected()
        connection.write(alignment.command)
    }

    fun feed(lines: Int = 3) {
        ensureConnected()
        connection.write(EscPosCommands.feed(lines))
    }

    fun cut(partial: Boolean = true) {
        ensureConnected()
        connection.write(
            if (partial) EscPosCommands.CUT_PARTIAL else EscPosCommands.CUT_FULL
        )
    }

    fun reset() {
        ensureConnected()
        connection.write(EscPosCommands.INIT)
    }

    private fun ensureConnected() {
        if (!connection.isConnected()) {
            throw IllegalStateException("Printer not connected")
        }
    }
}
