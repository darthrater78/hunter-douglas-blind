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
    private val json = Json { ignoreUnknownKeys = true }

    /** All known shades, keyed by MAC address. Empty map (not an error) until anything has been saved. */
    public val shades: Flow<Map<String, ShadeMetadata>> = context.shadeMetadataDataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, ShadeMetadata>>(raw) }.getOrDefault(emptyMap())
    }

    /** Inserts or replaces the metadata for [metadata.macAddress]. */
    public suspend fun upsert(metadata: ShadeMetadata) {
        context.shadeMetadataDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<Map<String, ShadeMetadata>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[key] = json.encodeToString(current + (metadata.macAddress to metadata))
        }
    }

    public suspend fun remove(macAddress: String) {
        context.shadeMetadataDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<Map<String, ShadeMetadata>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[key] = json.encodeToString(current - macAddress)
        }
    }
}
