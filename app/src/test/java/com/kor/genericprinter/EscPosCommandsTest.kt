package com.kor.genericprinter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosCommandsTest {

    @Test
    fun feedReturnsRequestedLineFeeds() {
        assertArrayEquals(byteArrayOf(0x0A, 0x0A, 0x0A), EscPosCommands.feed(3))
    }

    @Test
    fun textUtf8EncodesText() {
        assertEquals("Receipt", String(EscPosCommands.textUtf8("Receipt"), Charsets.UTF_8))
    }
}
