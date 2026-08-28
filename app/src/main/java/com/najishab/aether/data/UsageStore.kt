package com.najishab.aether.data

import android.content.Context
import android.net.TrafficStats
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val Context.usageDataStore by preferencesDataStore(name = "aether_usage")

/** One day's total device data usage (device-wide, not per-app - see [UsageStore]). */
data class DailyUsage(val dateKey: String, val bytes: Long)

/**
 * Tracks device-wide data usage per calendar day, for the Usage Calendar
 * screen. Backed by [TrafficStats.getTotalRxBytes]/[getTotalTxBytes] - the
 * same counters Android's own Settings > Data usage reads - since the engine
 * runs as a native process outside the VpnService's own UID and doesn't
 * expose per-tunnel byte counts. This means the numbers reflect the whole
 * device's traffic while the app is running, not just the tunnel.
 *
 * [recordTick] should be called periodically (see AetherApp) while the
 * process is alive; it diffs the running device totals against the last
 * checkpoint and adds the delta to today's bucket, so usage isn't lost
 * between app launches.
 */
class UsageStore(private val context: Context) {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    private val checkpointKey = longPreferencesKey("checkpoint_total_bytes")

    private fun dayKey(prefix: String, date: String) = longPreferencesKey("$prefix:$date")

    /** Flow of the last ~35 days of usage, oldest first, missing days as zero. */
    fun history(days: Int = 35): Flow<List<DailyUsage>> =
        context.usageDataStore.data.map { prefs ->
            val cal = java.util.Calendar.getInstance()
            val out = mutableListOf<DailyUsage>()
            repeat(days) { i ->
                val key = dayFormat.format(cal.time)
                val bytes = prefs[dayKey("day", key)] ?: 0L
                out.add(0, DailyUsage(key, bytes))
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            out
        }

    suspend fun todayTotal(): Long {
        val today = dayFormat.format(java.util.Date())
        return context.usageDataStore.data.first()[dayKey("day", today)] ?: 0L
    }

    /**
     * Diffs the current device-wide TrafficStats total against the saved
     * checkpoint and adds the (non-negative) delta to today's bucket. Safe to
     * call repeatedly; a device reboot (counters reset to ~0) is detected and
     * simply re-checkpoints without a negative/garbage delta.
     */
    suspend fun recordTick() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return
        val currentTotal = rx + tx
        val today = dayFormat.format(java.util.Date())

        context.usageDataStore.edit { prefs ->
            val checkpoint = prefs[checkpointKey]
            if (checkpoint != null && currentTotal >= checkpoint) {
                val delta = currentTotal - checkpoint
                val key = dayKey("day", today)
                prefs[key] = (prefs[key] ?: 0L) + delta
            }
            prefs[checkpointKey] = currentTotal
        }
    }
}
