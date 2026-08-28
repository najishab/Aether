package com.najishab.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Three-way theme choice, persisted so it survives restarts. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val Context.themeDataStore by preferencesDataStore(name = "aether_theme")

/** Persists the user's theme choice (dark / light / follow system). */
class ThemeStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")

    val mode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        when (prefs[modeKey]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "system" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
    }

    suspend fun setMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[modeKey] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }
}
