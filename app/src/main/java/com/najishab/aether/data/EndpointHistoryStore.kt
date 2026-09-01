package com.najishab.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najishab.aether.core.DiagnosticsLog
import com.najishab.aether.core.EndpointHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.endpointHistoryDataStore by preferencesDataStore(name = "aether_endpoint_history")

/**
 * Endpoint Health Check & History (independent, modular feature — see
 * EndpointHistoryActivity). Persists a capped, most-recent-first list of
 * endpoints the app has actually completed a full self-tested connection to.
 * JSON-encoded via org.json (no new dependency), matching the style already
 * used by AnnouncementClient/AnnouncementStore.
 */
class EndpointHistoryStore(private val context: Context) {
    private val key = stringPreferencesKey("entries_json")

    /** Most-recent-first. */
    val history: Flow<List<EndpointHistoryEntry>> =
        context.endpointHistoryDataStore.data.map { prefs -> parse(prefs[key].orEmpty()) }

    suspend fun recent(): List<EndpointHistoryEntry> =
        parse(context.endpointHistoryDataStore.data.first()[key].orEmpty())


    /**
     * Records (or refreshes) one successful endpoint. Deduped by [entry].endpoint:
     * a repeat success updates its ping/timestamp and bumps it back to the
     * front instead of adding a duplicate row. Capped at [MAX_ENTRIES].
     */
    suspend fun recordSuccess(entry: EndpointHistoryEntry) {
        if (entry.endpoint.isBlank()) return
        context.endpointHistoryDataStore.edit { prefs ->
            val current = parse(prefs[key].orEmpty()).toMutableList()
            current.removeAll { it.endpoint == entry.endpoint }
            current.add(0, entry)
            prefs[key] = serialize(current.take(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        context.endpointHistoryDataStore.edit { it[key] = "[]" }
    }


    private fun parse(json: String): List<EndpointHistoryEntry> = runCatching {
        val arr = JSONArray(json.ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val endpoint = o.optString("endpoint").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EndpointHistoryEntry(
                endpoint = endpoint,
                protocol = o.optString("protocol", "WIREGUARD"),
                pingMs = o.optLong("pingMs", -1L),
                network = o.optString("network", "Unknown"),
                lastSuccessMs = o.optLong("lastSuccessMs", 0L),
            )
        }
    }.getOrElse {
        DiagnosticsLog.w("endpoint-history", "Failed to parse stored history: ${it.message}")
        emptyList()
    }

    private fun serialize(entries: List<EndpointHistoryEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("endpoint", e.endpoint)
                    put("protocol", e.protocol)
                    put("pingMs", e.pingMs)
                    put("network", e.network)
                    put("lastSuccessMs", e.lastSuccessMs)
                },
            )
        }
        return arr.toString()
    }

    companion object {
        /** Cap on retained rows (per product decision). */
        const val MAX_ENTRIES = 10
    }
}
