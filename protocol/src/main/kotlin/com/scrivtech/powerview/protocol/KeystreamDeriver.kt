package com.scrivtech.powerview.protocol

/**
 * Derives a per-`homeId` keystream from a single known (plaintext, ciphertext)
 * pair — the "derive from a capture" onboarding path (§2.4 path 3).
 *
 * This is the whole point of the fixed-IV weakness described in
 * `docs/PROTOCOL.md` §1.4: `keystream = ciphertext XOR plaintext`. The caller
 * is responsible for knowing (or guessing and verifying) the plaintext that
 * produced a sniffed ciphertext write — see [CommandFrameBuilder.build] and
 * the sanity check below.
 */
public object KeystreamDeriver {

    /**
     * @param plaintext the command frame you believe was sent (built via
     *   [CommandFrameBuilder.build] with a guessed sequence byte).
     * @param ciphertext the sniffed bytes actually written to the
     *   characteristic.
     * @throws IllegalArgumentException if the sizes don't match.
     */
    public fun derive(plaintext: ByteArray, ciphertext: ByteArray): ByteArray {
        require(plaintext.size == ciphertext.size) {
            "plaintext (${plaintext.size} bytes) and ciphertext (${ciphertext.size} bytes) must be the same length"
        }
        return FrameCipher.xorWithKeystream(ciphertext, plaintext)
    }

    /**
     * Sanity-checks a plaintext frame recovered via [derive]: bytes 0, 1 and
     * 3 of any valid command frame must be `0xF7 0x01 .. 0x09` (see
     * [CommandFrameBuilder]). If this returns false, the guessed plaintext
     * (most likely the guessed sequence byte) was wrong and the derived
     * keystream is not trustworthy — prompt the user to retry the capture.
     */
    public fun looksLikeValidFrame(plaintext: ByteArray): Boolean {
        if (plaintext.size != CommandFrameBuilder.FRAME_SIZE) return false
        return plaintext[0] == 0xF7.toByte() &&
            plaintext[1] == 0x01.toByte() &&
            plaintext[3] == 0x09.toByte()
    }
}
