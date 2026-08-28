package com.najishab.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.announcementDataStore by preferencesDataStore(name = "aether_announcements")

/** Which announcement ids (see Announcement/AnnouncementClient) the user has already seen/dismissed. */
class AnnouncementStore(private val context: Context) {
    private val seenKey = stringSetPreferencesKey("seen_ids")

    suspend fun seenIds(): Set<String> =
        context.announcementDataStore.data.first()[seenKey] ?: emptySet()

    suspend fun markSeen(id: String) {
        context.announcementDataStore.edit { prefs ->
            prefs[seenKey] = (prefs[seenKey] ?: emptySet()) + id
        }
    }
}
