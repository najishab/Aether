package com.najishab.aether.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najishab.aether.core.AppCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Context.usageDataStore by preferencesDataStore(name = "aether_usage")

enum class TunnelUsageSource(val storageName: String) {
    VPN_TUN("vpn_tun"),
    LOCAL_PROXY("local_proxy"),
    LAN_SHARE("lan_share"),
}

data class DailyUsage(
    val dateKey: String,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val sessionCount: Int = 0,
) {
    val totalBytes: Long get() = uploadBytes + downloadBytes
}

data class TunnelUsageSession(
    val id: Long,
    val source: TunnelUsageSource,
    val startMs: Long,
    val endMs: Long,
    val uploadBytes: Long,
    val downloadBytes: Long,
) {
    val totalBytes: Long get() = uploadBytes + downloadBytes
}

data class UsageSummary(
    val history: List<DailyUsage>,
    val today: DailyUsage,
    val last7Days: List<DailyUsage>,
    val last7TotalBytes: Long,
    val dailyAverageBytes: Long,
    val highestDay: DailyUsage?,
    val sessionsByDay: Map<String, List<TunnelUsageSession>>,
    val thisWeekTotalBytes: Long,
    val thisMonthTotalBytes: Long,
)

class UsageStore(private val context: Context) {
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private fun dayKey(prefix: String, date: String) = longPreferencesKey("tunnel_v1:$prefix:$date")
    private val sessionsKey = stringPreferencesKey("tunnel_v1:sessions")
    private val nextSessionIdKey = longPreferencesKey("tunnel_v1:next_session_id")

    fun history(days: Int = 35): Flow<List<DailyUsage>> =
        context.usageDataStore.data.map { prefs ->
            recentDayKeys(days).map { date ->
                DailyUsage(
                    dateKey = date,
                    uploadBytes = prefs[dayKey("up", date)] ?: 0L,
                    downloadBytes = prefs[dayKey("down", date)] ?: 0L,
                    sessionCount = (prefs[dayKey("sessions", date)] ?: 0L).toInt(),
                )
            }
        }

    fun summary(days: Int = 35): Flow<UsageSummary> =
        context.usageDataStore.data.map { prefs ->
            val history = recentDayKeys(days).map { date ->
                DailyUsage(
                    dateKey = date,
                    uploadBytes = prefs[dayKey("up", date)] ?: 0L,
                    downloadBytes = prefs[dayKey("down", date)] ?: 0L,
                    sessionCount = (prefs[dayKey("sessions", date)] ?: 0L).toInt(),
                )
            }
            val today = history.lastOrNull() ?: DailyUsage(dayFormat.format(Date()))
            val last7 = history.takeLast(7)
            val sessions = decodeSessions(prefs[sessionsKey].orEmpty())
            val locale = Locale.getDefault()
            val weekRange = AppCalendar.thisWeekRange(locale)
            val monthRange = AppCalendar.thisMonthRange(locale)
            val thisWeekTotal = history.filter { it.dateKey in weekRange.first..weekRange.second }
                .sumOf { it.totalBytes }
            val thisMonthTotal = history.filter { it.dateKey in monthRange.first..monthRange.second }
                .sumOf { it.totalBytes }
            UsageSummary(
                history = history,
                today = today,
                last7Days = last7,
                last7TotalBytes = last7.sumOf { it.totalBytes },
                dailyAverageBytes = if (last7.isEmpty()) 0L else last7.sumOf { it.totalBytes } / last7.size,
                highestDay = last7.maxByOrNull { it.totalBytes },
                sessionsByDay = sessions.groupBy { dayFormat.format(Date(it.startMs)) },
                thisWeekTotalBytes = thisWeekTotal,
                thisMonthTotalBytes = thisMonthTotal,
            )
        }

    suspend fun todayTotal(): Long {
        val today = dayFormat.format(Date())
        val prefs = context.usageDataStore.data.first()
        return (prefs[dayKey("up", today)] ?: 0L) + (prefs[dayKey("down", today)] ?: 0L)
    }

    suspend fun startSession(source: TunnelUsageSource, startMs: Long = System.currentTimeMillis()): Long {
        var id = startMs
        context.usageDataStore.edit { prefs ->
            id = prefs[nextSessionIdKey] ?: startMs
            prefs[nextSessionIdKey] = id + 1
            val session = TunnelUsageSession(id, source, startMs, startMs, 0L, 0L)
            prefs[sessionsKey] = encodeSessions((decodeSessions(prefs[sessionsKey].orEmpty()) + session).takeLast(MAX_SESSIONS))
            val day = dayFormat.format(Date(startMs))
            prefs[dayKey("sessions", day)] = (prefs[dayKey("sessions", day)] ?: 0L) + 1L
        }
        return id
    }

    suspend fun addBytes(sessionId: Long, uploadBytes: Long, downloadBytes: Long, atMs: Long = System.currentTimeMillis()) {
        if (uploadBytes <= 0L && downloadBytes <= 0L) return
        context.usageDataStore.edit { prefs ->
            val day = dayFormat.format(Date(atMs))
            prefs[dayKey("up", day)] = (prefs[dayKey("up", day)] ?: 0L) + uploadBytes.coerceAtLeast(0L)
            prefs[dayKey("down", day)] = (prefs[dayKey("down", day)] ?: 0L) + downloadBytes.coerceAtLeast(0L)
            prefs[sessionsKey] = encodeSessions(
                decodeSessions(prefs[sessionsKey].orEmpty()).map { session ->
                    if (session.id == sessionId) {
                        session.copy(
                            endMs = atMs,
                            uploadBytes = session.uploadBytes + uploadBytes.coerceAtLeast(0L),
                            downloadBytes = session.downloadBytes + downloadBytes.coerceAtLeast(0L),
                        )
                    } else {
                        session
                    }
                }.takeLast(MAX_SESSIONS),
            )
        }
    }

    suspend fun endSession(sessionId: Long, endMs: Long = System.currentTimeMillis()) {
        context.usageDataStore.edit { prefs ->
            prefs[sessionsKey] = encodeSessions(
                decodeSessions(prefs[sessionsKey].orEmpty()).map { session ->
                    if (session.id == sessionId) session.copy(endMs = endMs) else session
                }.takeLast(MAX_SESSIONS),
            )
        }
    }

    private fun recentDayKeys(days: Int): List<String> {
        val cal = Calendar.getInstance()
        val out = mutableListOf<String>()
        repeat(days) {
            out.add(0, dayFormat.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out
    }

    private fun encodeSessions(sessions: List<TunnelUsageSession>): String =
        sessions.joinToString("\n") {
            listOf(it.id, it.source.storageName, it.startMs, it.endMs, it.uploadBytes, it.downloadBytes).joinToString("|")
        }

    private fun decodeSessions(raw: String): List<TunnelUsageSession> =
        raw.lineSequence().mapNotNull { line ->
            val p = line.split("|")
            if (p.size != 6) return@mapNotNull null
            val source = TunnelUsageSource.entries.firstOrNull { it.storageName == p[1] } ?: return@mapNotNull null
            TunnelUsageSession(
                id = p[0].toLongOrNull() ?: return@mapNotNull null,
                source = source,
                startMs = p[2].toLongOrNull() ?: return@mapNotNull null,
                endMs = p[3].toLongOrNull() ?: return@mapNotNull null,
                uploadBytes = p[4].toLongOrNull() ?: return@mapNotNull null,
                downloadBytes = p[5].toLongOrNull() ?: return@mapNotNull null,
            )
        }.toList()

    private companion object {
        const val MAX_SESSIONS = 180
    }
}
