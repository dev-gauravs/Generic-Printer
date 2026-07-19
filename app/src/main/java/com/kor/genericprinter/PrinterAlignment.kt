package com.kor.genericprinter

enum class PrinterAlignment(
    internal val command: ByteArray
) {
    LEFT(EscPosCommands.ALIGN_LEFT),
    CENTER(EscPosCommands.ALIGN_CENTER),
    RIGHT(EscPosCommands.ALIGN_RIGHT)
}
