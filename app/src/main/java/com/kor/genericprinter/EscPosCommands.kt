package com.kor.genericprinter

object EscPosCommands {
    val INIT = byteArrayOf(0x1B, 0x40)                 // ESC @
    val LF = byteArrayOf(0x0A)                         // \n
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)     // ESC a 0
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)   // ESC a 1
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)    // ESC a 2

    // Cut (may not be supported on some printers)
    val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x10) // GS V B n
    val CUT_FULL = byteArrayOf(0x1D, 0x56, 0x00)          // GS V 0

    fun textUtf8(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)

    fun feed(lines: Int): ByteArray = ByteArray(lines) { 0x0A } // lines of LF
}
