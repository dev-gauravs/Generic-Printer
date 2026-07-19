package com.atomx.genericprinter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterClientTest {

    @Test
    fun connectAndCloseDelegateToConnection() {
        val connection = FakeConnection()
        val client = PrinterClient(connection)

        assertTrue(client.connect())
        assertTrue(client.isConnected())

        client.close()

        assertFalse(client.isConnected())
    }

    @Test
    fun printTextWritesUtf8AndLineFeed() {
        val connection = FakeConnection().also { it.connect() }
        val client = PrinterClient(connection)

        client.printText("Hello")

        assertArrayEquals(
            "Hello".toByteArray(Charsets.UTF_8) + EscPosCommands.LF,
            connection.writtenBytes()
        )
    }

    @Test
    fun writeRawWritesBytesAsProvided() {
        val connection = FakeConnection().also { it.connect() }
        val client = PrinterClient(connection)
        val bytes = byteArrayOf(0x1B, 0x40)

        client.writeRaw(bytes)

        assertArrayEquals(bytes, connection.writtenBytes())
    }

    @Test
    fun printReceiptBatchesReceiptBytes() {
        val connection = FakeConnection().also { it.connect() }
        val client = PrinterClient(connection)

        client.printReceipt {
            reset()
            align(PrinterAlignment.CENTER)
            text("Receipt")
            feed(2)
            cut()
        }

        assertArrayEquals(
            EscPosCommands.INIT +
                PrinterAlignment.CENTER.command +
                "Receipt".toByteArray(Charsets.UTF_8) +
                EscPosCommands.LF +
                EscPosCommands.feed(2) +
                EscPosCommands.CUT_PARTIAL,
            connection.writtenBytes()
        )
    }

    @Test(expected = IllegalStateException::class)
    fun writeRawRequiresConnection() {
        PrinterClient(FakeConnection()).writeRaw(byteArrayOf(0x01))
    }

    private class FakeConnection : PrinterConnection {
        private var connected = false
        private val writes = mutableListOf<ByteArray>()

        override fun connect(): Boolean {
            connected = true
            return true
        }

        override fun isConnected(): Boolean = connected

        override fun write(bytes: ByteArray) {
            writes += bytes
        }

        override fun close() {
            connected = false
        }

        fun writtenBytes(): ByteArray {
            return writes.fold(ByteArray(0)) { allBytes, nextBytes -> allBytes + nextBytes }
        }
    }
}
