package com.scrivtech.powerview.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts a 13-byte command frame. See `docs/PROTOCOL.md` §1.4.
 *
 * The wire cipher is AES-128-CTR with a **fixed, all-zero 16-byte IV**. With
 * a fixed key and fixed IV, CTR mode produces a fixed keystream — so once you
 * have the keystream for a given `homeId`, encryption is just XOR and the
 * AES key itself is never needed again. [xorWithKeystream] is that fast path
 * and is what [ShadeGattClient]-equivalents should call on every write.
 *
 * [deriveKeystreamFromKey] exists only for the "import a known key" onboarding
 * path (§2.4 path 2) and for tests against the spec's key-based test vectors;
 * it is never on the hot path.
 */
public object FrameCipher {

    private const val AES_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CTR/NoPadding"
    private const val AES_KEY_SIZE_BYTES = 16
    private const val AES_BLOCK_SIZE_BYTES = 16

    /**
     * XORs [frame] against [keystream] byte-for-byte. This is both encryption
     * and decryption (XOR is its own inverse) — used for every real command
     * once a keystream is known for the shade's `homeId`.
     *
     * @throws IllegalArgumentException if the sizes don't match.
     */
    public fun xorWithKeystream(frame: ByteArray, keystream: ByteArray): ByteArray {
        require(frame.size == keystream.size) {
            "frame (${frame.size} bytes) and keystream (${keystream.size} bytes) must be the same length"
        }
        return ByteArray(frame.size) { i -> (frame[i].toInt() xor keystream[i].toInt()).toByte() }
    }

    /**
     * Derives the keystream for a 16-byte AES key by AES-CTR-encrypting an
     * all-zero block under the protocol's fixed all-zero IV, then truncating
     * to [frameSize] bytes. Only needed for the "import a known key"
     * onboarding path — see class doc.
     *
     * @throws IllegalArgumentException if [key] is not 16 bytes.
     */
    public fun deriveKeystreamFromKey(key: ByteArray, frameSize: Int = CommandFrameBuilder.FRAME_SIZE): ByteArray {
        require(key.size == AES_KEY_SIZE_BYTES) { "AES-128 key must be $AES_KEY_SIZE_BYTES bytes, was ${key.size}" }
        require(frameSize in 1..AES_BLOCK_SIZE_BYTES) { "frameSize must be 1..$AES_BLOCK_SIZE_BYTES, was $frameSize" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val zeroIv = ByteArray(AES_BLOCK_SIZE_BYTES)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), IvParameterSpec(zeroIv))
        val fullBlockKeystream = cipher.doFinal(ByteArray(AES_BLOCK_SIZE_BYTES))
        return fullBlockKeystream.copyOf(frameSize)
    }
}
