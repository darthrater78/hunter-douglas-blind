package com.scrivtech.powerview.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

/**
 * Persists the per-`homeId` write keystream (spec §1.4, §2.4). This is the
 * one piece of shade state that functions as a credential — anyone with it
 * can command every shade in the home — so it's kept out of the plain
 * [ShadeStore] blob and behind [EncryptedSharedPreferences], which wraps the
 * file encryption key in the Android Keystore.
 *
 * Stored as lowercase hex. A keystream is [com.scrivtech.powerview.protocol.CommandFrameBuilder.FRAME_SIZE]
 * (13) bytes, never a raw AES key — see `docs/PROTOCOL.md` §1.4 for why the
 * key itself is never needed once a keystream is known.
 */
public class KeystreamStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "shade_keystreams",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * @throws IllegalStateException if the stored value is not well-formed hex.
     * Callers on a background surface should treat that the same as "no
     * keystream" rather than letting it escape — see `ActionRunner.runCommand`.
     */
    public fun get(homeId: Int): ByteArray? = prefs.getString(keyFor(homeId), null)?.let(::hexToBytes)

    public fun put(homeId: Int, keystream: ByteArray) {
        prefs.edit().putString(keyFor(homeId), bytesToHex(keystream)).apply()
    }

    public fun remove(homeId: Int) {
        prefs.edit().remove(keyFor(homeId)).apply()
    }

    public fun hasKeystream(homeId: Int): Boolean = prefs.contains(keyFor(homeId))

    private fun keyFor(homeId: Int) = "home_$homeId"

    // Locale.ROOT so the digits are always ASCII. java.util.Formatter does not
    // localize %x today, but nothing in the contract promises that, and this
    // string is a round-tripped credential rather than display text.
    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    /**
     * Unvalidated input used to reach here: an odd-length value silently
     * truncated the last nibble, and a non-hex character threw
     * NumberFormatException from deep inside a `let`. Both are now one explicit
     * failure at the boundary.
     */
    private fun hexToBytes(hex: String): ByteArray {
        check(hex.length % 2 == 0) { "stored keystream has an odd number of hex digits (${hex.length})" }
        check(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "stored keystream is not valid hex"
        }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
