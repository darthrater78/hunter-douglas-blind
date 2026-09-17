package com.scrivtech.powerview.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.shadeMetadataDataStore: DataStore<Preferences> by preferencesDataStore(name = "shade_metadata")

/**
 * Non-sensitive per-shade metadata: label, room, capability, battery/sighting
 * history, mains-powered flag. Persisted as one JSON blob in Preferences
 * DataStore — fine at the scale of a single home's worth of shades.
 *
 * Keystreams are handled separately by [KeystreamStore], which backs onto
 * encrypted storage — never persist a keystream through this class.
 */
@Serializable
public data class ShadeMetadata(
    public val macAddress: String,
    public val label: String,
    public val room: String? = null,
    public val homeId: Int? = null,
    public val capabilityId: Int? = null,
    public val lastSeenAtEpochMillis: Long? = null,
    public val batteryBucket: Int? = null,
    public val batteryReadAtEpochMillis: Long? = null,
    public val mainsPowered: Boolean = false,
)

public class ShadeStore(private val context: Context) {

    private val key = stringPreferencesKey("shades_json")
    private val quarantineKey = stringPreferencesKey("shades_json_unreadable")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * All known shades, keyed by MAC address. Empty map (not an error) until
     * anything has been saved, and also if the stored blob cannot be parsed —
     * see [upsert] for what happens to an unparseable blob rather than it
     * simply being overwritten.
     */
    public val shades: Flow<Map<String, ShadeMetadata>> = context.shadeMetadataDataStore.data.map { prefs ->
        decodeOrNull(prefs[key]) ?: emptyMap()
    }

    /** Inserts or replaces the metadata for [metadata.macAddress]. */
    public suspend fun upsert(metadata: ShadeMetadata) {
        edit { current -> current + (metadata.macAddress to metadata) }
    }

    public suspend fun remove(macAddress: String) {
        edit { current -> current - macAddress }
    }

    /**
     * Reads, transforms and writes back the stored map.
     *
     * The important part is what happens when the stored blob does not parse
     * (corruption, or a schema change `ignoreUnknownKeys` cannot absorb, such
     * as a newly added non-optional field). Treating that as "no data" and
     * writing the transformed empty map back would destroy every label, room
     * and capability the user has entered, silently and unrecoverably. Instead
     * the unreadable blob is moved aside under [quarantineKey] first, so it
     * survives for a future migration or manual recovery, and only then does
     * the store start fresh.
     */
    private suspend fun edit(transform: (Map<String, ShadeMetadata>) -> Map<String, ShadeMetadata>) {
        context.shadeMetadataDataStore.edit { prefs ->
            val raw = prefs[key]
            val current = decodeOrNull(raw)
            if (current == null && raw != null) {
                prefs[quarantineKey] = raw
            }
            prefs[key] = json.encodeToString(transform(current ?: emptyMap()))
        }
    }

    /** Null means "present but unreadable"; an empty map means "nothing stored yet". */
    private fun decodeOrNull(raw: String?): Map<String, ShadeMetadata>? {
        if (raw == null) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, ShadeMetadata>>(raw) }.getOrNull()
    }
}
