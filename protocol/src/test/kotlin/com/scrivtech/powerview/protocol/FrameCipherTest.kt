package com.scrivtech.powerview.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Verifies [CommandFrameBuilder] + [FrameCipher] against the five sniffed
 * (command, ciphertext) test vectors from the spec, using the real AES-128
 * key rather than a hand-derived keystream — this exercises the whole write
 * path (frame layout + AES-CTR keystream derivation + XOR) end to end.
 */
class FrameCipherTest {

    // From the spec: test key used by the openHAB binding's own unit tests.
    private val testKey = hexToBytes("02c2efcbd4064d59409c980e627e2fc7")

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    fun `builds and encrypts the sniffed command vectors`(
        @Suppress("UNUSED_PARAMETER") description: String,
        sequence: Byte,
        primaryPercent: Double?,
        secondaryPercent: Double?,
        tiltPercent: Int?,
        expectedCiphertextHex: String,
    ) {
        val plaintext = CommandFrameBuilder.build(
            sequence = sequence,
            primaryPercent = primaryPercent,
            secondaryPercent = secondaryPercent,
            tiltPercent = tiltPercent,
        )
        val keystream = FrameCipher.deriveKeystreamFromKey(testKey)
        val ciphertext = FrameCipher.xorWithKeystream(plaintext, keystream)

        assertArrayEquals(hexToBytes(expectedCiphertextHex), ciphertext)
    }

    @Test
    fun `xor is its own inverse`() {
        val keystream = FrameCipher.deriveKeystreamFromKey(testKey)
        val plaintext = CommandFrameBuilder.build(sequence = 0x42, primaryPercent = 77.5)
        val ciphertext = FrameCipher.xorWithKeystream(plaintext, keystream)
        val roundTripped = FrameCipher.xorWithKeystream(ciphertext, keystream)
        assertArrayEquals(plaintext, roundTripped)
    }

    companion object {
        @JvmStatic
        fun vectors(): List<Arguments> = listOf(
            Arguments.of("blank frame", 0x00.toByte(), null, null, null, "1F70847E5C07AD03100E0FB3DA"),
            Arguments.of("sequence = 0x01", 0x01.toByte(), null, null, null, "1F70857E5C07AD03100E0FB3DA"),
            Arguments.of("primary = 100%", 0x00.toByte(), 100.0, null, null, "1F70847E4CA0AD03100E0FB3DA"),
            Arguments.of("tilt = 40%", 0x00.toByte(), null, null, 40, "1F70847E5C07AD03100E2733DA"),
            Arguments.of(
                "seq 0xA6, primary 30%, secondary 10%",
                0xA6.toByte(),
                30.0,
                10.0,
                null,
                "1F70227EE48C4580100E0FB3DA",
            ),
        )

        @JvmStatic
        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "hex string must have an even length, was: $hex" }
            return ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
