package com.scrivtech.powerview.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeystreamDeriverTest {

    private val testKey = FrameCipherTest.hexToBytes("02c2efcbd4064d59409c980e627e2fc7")

    @Test
    fun `deriving from a real capture reproduces the key-derived keystream`() {
        val plaintext = CommandFrameBuilder.build(sequence = 0x00, primaryPercent = 100.0)
        val ciphertext = FrameCipherTest.hexToBytes("1F70847E4CA0AD03100E0FB3DA")

        val derived = KeystreamDeriver.derive(plaintext, ciphertext)
        val expected = FrameCipher.deriveKeystreamFromKey(testKey)

        assertArrayEquals(expected, derived)
    }

    @Test
    fun `a keystream derived from one capture correctly decrypts a different capture from the same home`() {
        // Simulates the real onboarding flow: derive a keystream from one
        // known (command, sniffed ciphertext) pair, then use it on a
        // *different* sniffed ciphertext for the same homeId (fixed
        // key+IV means one keystream covers every frame) and confirm the
        // header comes out looking like a real command frame.
        val plaintext1 = CommandFrameBuilder.build(sequence = 0x00) // blank frame
        val ciphertext1 = FrameCipherTest.hexToBytes("1F70847E5C07AD03100E0FB3DA")
        val keystream = KeystreamDeriver.derive(plaintext1, ciphertext1)

        val ciphertext2 = FrameCipherTest.hexToBytes("1F70847E4CA0AD03100E0FB3DA") // primary = 100%
        val decoded2 = FrameCipher.xorWithKeystream(ciphertext2, keystream)

        assertTrue(KeystreamDeriver.looksLikeValidFrame(decoded2))
        assertArrayEquals(CommandFrameBuilder.build(sequence = 0x00, primaryPercent = 100.0), decoded2)
    }

    @Test
    fun `an incorrect keystream produces a plaintext that fails the header sanity check`() {
        val wrongKeystream = ByteArray(CommandFrameBuilder.FRAME_SIZE) // all-zero: definitely not the real keystream
        val ciphertext = FrameCipherTest.hexToBytes("1F70847E4CA0AD03100E0FB3DA")

        val decoded = FrameCipher.xorWithKeystream(ciphertext, wrongKeystream)

        assertFalse(KeystreamDeriver.looksLikeValidFrame(decoded))
    }

    @Test
    fun `looksLikeValidFrame accepts any frame produced by CommandFrameBuilder`() {
        val plaintext = CommandFrameBuilder.build(sequence = 0x7F, tiltPercent = 40)
        assertTrue(KeystreamDeriver.looksLikeValidFrame(plaintext))
    }

    @Test
    fun `looksLikeValidFrame rejects a frame of the wrong size`() {
        assertFalse(KeystreamDeriver.looksLikeValidFrame(ByteArray(10)))
    }
}
