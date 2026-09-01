package com.najishab.aether.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.batteryOptDataStore by preferencesDataStore(name = "aether_battery_opt")

/**
 * Tracks whether the user has already been asked (once, after their first
 * successful connection) to exempt NajiAether from battery optimizations.
 * Kept separate from [OnboardingStore] so it survives independently and can
 * be re-triggered from Advanced settings without touching onboarding state.
 */
class BatteryOptStore(private val context: Context) {
    private val askedKey = booleanPreferencesKey("battery_opt_asked")

    val asked: Flow<Boolean> =
        context.batteryOptDataStore.data.map { it[askedKey] ?: false }

    suspend fun markAsked() {
        context.batteryOptDataStore.edit { it[askedKey] = true }
    }
}
