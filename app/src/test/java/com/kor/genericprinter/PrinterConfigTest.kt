package com.kor.genericprinter

import org.junit.Assert.assertThrows
import org.junit.Test

class PrinterConfigTest {

    @Test
    fun paperWidthMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PrinterConfig(paperWidthPx = 0)
        }
    }

    @Test
    fun usbTimeoutMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PrinterConfig(usbWriteTimeoutMillis = 0)
        }
    }

    @Test
    fun writeChunksMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PrinterConfig(bluetoothWriteChunkSize = 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            PrinterConfig(usbWriteChunkSize = 0)
        }
    }

    @Test
    fun imageThresholdMustBeInByteRange() {
        assertThrows(IllegalArgumentException::class.java) {
            PrinterConfig(imageThreshold = 300)
        }
    }
}
