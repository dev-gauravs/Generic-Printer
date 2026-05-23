import android.graphics.Bitmap
import com.atomx.genericprinter.EscPosCommands
import com.atomx.genericprinter.EscPosImage
import com.atomx.genericprinter.PrinterConfig
import com.atomx.genericprinter.PrinterConnection
import com.atomx.genericprinter.PrinterLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrinterClient(
    private val connection: PrinterConnection,
    private val config: PrinterConfig = PrinterConfig()
) {

    init {
        PrinterLogger.enabled = config.debug
    }

    suspend fun connectAsync(): Boolean = withContext(Dispatchers.IO) {
        connect()
    }

    suspend fun printAsync(block: PrinterClient.() -> Unit) =
        withContext(Dispatchers.IO) {
            ensureConnected()
            block()
        }

    fun connect(): Boolean {
        PrinterLogger.d("connect() called")
        return connection.connect()
    }

    fun disconnect() {
        PrinterLogger.d("disconnect() called")
        connection.close()
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
        val bytes = EscPosImage.toRasterBytes(bitmap, config.paperWidthPx)
        connection.write(bytes)
        connection.write(EscPosCommands.LF)
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
