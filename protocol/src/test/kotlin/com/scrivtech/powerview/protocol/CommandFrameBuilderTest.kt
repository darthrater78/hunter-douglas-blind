package com.scrivtech.powerview.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandFrameBuilderTest {

    @Test
    fun `blank frame matches the spec's constant header and unset sentinels`() {
        val frame = CommandFrameBuilder.build(sequence = 0x00)

        assertEquals(13, frame.size)
        assertEquals(0xF7.toByte(), frame[0])
        assertEquals(0x01.toByte(), frame[1])
        assertEquals(0x00.toByte(), frame[2]) // sequence
        assertEquals(0x09.toByte(), frame[3])
        // primary/secondary unset sentinel
        assertEquals(0x00.toByte(), frame[4])
        assertEquals(0x80.toByte(), frame[5])
        assertEquals(0x00.toByte(), frame[6])
        assertEquals(0x80.toByte(), frame[7])
        // reserved, always unset
        assertEquals(0x00.toByte(), frame[8])
        assertEquals(0x80.toByte(), frame[9])
        // tilt unset
        assertEquals(0x00.toByte(), frame[10])
        assertEquals(0x80.toByte(), frame[11])
        assertEquals(0x00.toByte(), frame[12])
    }

    @Test
    fun `sequence byte is written verbatim`() {
        val frame = CommandFrameBuilder.build(sequence = 0xA6.toByte())
        assertEquals(0xA6.toByte(), frame[2])
    }

    @Test
    fun `primary percent is scaled by 100 and written little-endian`() {
        val frame = CommandFrameBuilder.build(sequence = 0x00, primaryPercent = 30.0)
        // round(30 * 100) = 3000 = 0x0BB8 -> LE bytes 0xB8, 0x0B
        assertEquals(0xB8.toByte(), frame[4])
        assertEquals(0x0B.toByte(), frame[5])
    }

    @Test
    fun `tilt sets the percent byte and clears the marker byte`() {
        val frame = CommandFrameBuilder.build(sequence = 0x00, tiltPercent = 40)
        assertEquals(40.toByte(), frame[10])
        assertEquals(0x00.toByte(), frame[11])
    }

    @Test
    fun `out-of-range percents are clamped rather than overflowing the frame`() {
        val overPrimary = CommandFrameBuilder.build(sequence = 0x00, primaryPercent = 150.0)
        assertEquals(0x10.toByte(), overPrimary[4]) // round(100*100)=10000=0x2710 -> LE 0x10,0x27
        assertEquals(0x27.toByte(), overPrimary[5])

        val overTilt = CommandFrameBuilder.build(sequence = 0x00, tiltPercent = 250)
        assertEquals(100.toByte(), overTilt[10])
    }
}
