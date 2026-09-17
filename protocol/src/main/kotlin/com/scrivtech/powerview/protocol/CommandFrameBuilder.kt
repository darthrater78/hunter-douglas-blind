package com.scrivtech.powerview.protocol

import kotlin.math.roundToInt

/**
 * Builds the 13-byte plaintext command frame written to characteristic
 * `CAFE1001-...` (service `FDC1-...`). See `docs/PROTOCOL.md` §1.3.
 *
 * The exact byte layout below was reverse-derived from the five sniffed
 * (plaintext, ciphertext) pairs in the spec by XOR-ing each ciphertext
 * against the AES-CTR keystream for the shared test key — see
 * `CommandFrameBuilderTest` and `FrameCipherTest`, which reproduce all five
 * ciphertexts exactly. That derivation disagrees slightly with the spec's
 * inline hex-template annotation (which groups the primary field one byte
 * later than it actually sits); the field-offset table elsewhere in the same
 * spec, and the empirical decode, agree with each other and with what's
 * implemented here.
 *
 * Frame layout (13 bytes, 0-indexed):
 * ```
 * 0     : 0xF7        constant
 * 1     : 0x01        constant
 * 2     : sequence    uint8, increments per command
 * 3     : 0x09        constant
 * 4..5  : primary     uint16 LE, round(percent * 100); unset = [0x00, 0x80]
 * 6..7  : secondary   uint16 LE, round(percent * 100); unset = [0x00, 0x80]
 * 8..9  : reserved    always [0x00, 0x80] — no known field uses this
 * 10    : tilt        uint8 percent (0..100) when active
 * 11    : tilt marker 0x00 when tilt is set, 0x80 when unset
 * 12    : 0x00        constant
 * ```
 */
public object CommandFrameBuilder {

    public const val FRAME_SIZE: Int = 13

    private const val BYTE_HEADER_0: Byte = 0xF7.toByte()
    private const val BYTE_HEADER_1: Byte = 0x01
    private const val BYTE_HEADER_3: Byte = 0x09
    private const val BYTE_UNSET_LOW: Byte = 0x00
    private const val BYTE_UNSET_HIGH: Byte = 0x80.toByte()
    private const val BYTE_TILT_MARKER_UNSET: Byte = 0x80.toByte()
    private const val BYTE_TILT_MARKER_SET: Byte = 0x00

    private const val RAW_MAX = 10000 // round(100% * 100)

    /**
     * @param sequence per-command sequence byte. Increment (wrapping) for
     *   every command sent; openHAB starts at `Byte.MIN_VALUE`. Not currently
     *   known to be validated by the shade, but always set it correctly.
     * @param primaryPercent 0.0..100.0, or null to leave the primary rail
     *   untouched.
     * @param secondaryPercent 0.0..100.0, or null to leave the secondary
     *   rail untouched.
     * @param tiltPercent 0..100, or null to leave tilt untouched.
     */
    public fun build(
        sequence: Byte,
        primaryPercent: Double? = null,
        secondaryPercent: Double? = null,
        tiltPercent: Int? = null,
    ): ByteArray {
        val frame = ByteArray(FRAME_SIZE)
        frame[0] = BYTE_HEADER_0
        frame[1] = BYTE_HEADER_1
        frame[2] = sequence
        frame[3] = BYTE_HEADER_3

        writeRailField(frame, offset = 4, percent = primaryPercent)
        writeRailField(frame, offset = 6, percent = secondaryPercent)

        // Bytes 8-9: reserved, always the unset sentinel.
        frame[8] = BYTE_UNSET_LOW
        frame[9] = BYTE_UNSET_HIGH

        if (tiltPercent != null) {
            frame[10] = tiltPercent.coerceIn(0, 100).toByte()
            frame[11] = BYTE_TILT_MARKER_SET
        } else {
            frame[10] = BYTE_UNSET_LOW
            frame[11] = BYTE_TILT_MARKER_UNSET
        }

        frame[12] = 0x00

        return frame
    }

    private fun writeRailField(frame: ByteArray, offset: Int, percent: Double?) {
        if (percent == null) {
            frame[offset] = BYTE_UNSET_LOW
            frame[offset + 1] = BYTE_UNSET_HIGH
            return
        }
        val raw = (percent * 100.0).roundToInt().coerceIn(0, RAW_MAX)
        frame[offset] = (raw and 0xFF).toByte()
        frame[offset + 1] = ((raw ushr 8) and 0xFF).toByte()
    }
}
