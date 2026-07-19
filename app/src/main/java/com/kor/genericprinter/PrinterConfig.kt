package com.kor.genericprinter

data class PrinterConfig(
    val paperWidthPx: Int = 384,
    val debug: Boolean = false,
    val bluetoothMacAddress: String? = null,
    val bluetoothWriteChunkSize: Int = 2048,
    val usbWriteTimeoutMillis: Int = 5000,
    val usbWriteChunkSize: Int = 16 * 1024,
    val imageThreshold: Int = 160
) {
    init {
        require(paperWidthPx > 0) { "paperWidthPx must be greater than 0" }
        require(bluetoothWriteChunkSize > 0) {
            "bluetoothWriteChunkSize must be greater than 0"
        }
        require(usbWriteTimeoutMillis > 0) { "usbWriteTimeoutMillis must be greater than 0" }
        require(usbWriteChunkSize > 0) { "usbWriteChunkSize must be greater than 0" }
        require(imageThreshold in 0..255) { "imageThreshold must be between 0 and 255" }
    }
}
