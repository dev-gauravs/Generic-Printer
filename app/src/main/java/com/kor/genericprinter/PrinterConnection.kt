package com.kor.genericprinter

import java.io.Closeable

interface PrinterConnection : Closeable {
    fun connect(): Boolean
    fun isConnected(): Boolean
    fun write(bytes: ByteArray)
    override fun close()
}
