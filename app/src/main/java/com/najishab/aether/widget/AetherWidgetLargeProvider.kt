package com.najishab.aether.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.najishab.aether.MainActivity
import com.najishab.aether.R
import com.najishab.aether.core.AetherController
import com.najishab.aether.core.Formatters
import com.najishab.aether.core.NetProbe
import com.najishab.aether.core.PingMonitor
import com.najishab.aether.core.TrafficMonitor
import com.najishab.aether.data.ProfileStore
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.isBusy
import com.najishab.aether.model.isConnected

/**
 * Large ("advanced") home-screen widget: flag + status + latency, IP +
 * location, connection duration and live up/down speed. Visually redesigned
 * to match the reference mock (violet glass card, tight layout, coloured
 * up/down icon chips, labelled latency pill).
 *
 * Built as its own [AppWidgetProvider] (separate from [AetherWidgetProvider])
 * so the launcher offers Simple and Advanced as two distinct widgets.
 *
 * BATTERY: same event-driven contract as the simple widget
 * (updatePeriodMillis=0). The only periodic repaint is the hook in
 * [TrafficMonitor], which reuses that object's already-running 1s sampling
 * loop instead of adding a second wake source — see its WIDGET_REPAINT_CYCLES
 * / WIDGET_PING_CYCLES constants for the current cadence. The connection
 * duration itself needs no repaint at all: it is a native
 * [android.widget.Chronometer] driven by the OS.
 */
class AetherWidgetLargeProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.najishab.aether.WIDGET_LARGE_TOGGLE"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, AetherWidgetLargeProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val state = AetherController.state.value
            ids.forEach { id -> paint(context, manager, id, state) }
        }

        private fun paint(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            state: ConnectionState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.aether_widget_large)

            val (statusText, color) = when (state) {
                is ConnectionState.Connected ->
                    context.getString(R.string.state_connected) to 0xFF22C55E.toInt()
                is ConnectionState.Launching, is ConnectionState.Connecting, is ConnectionState.Verifying ->
                    context.getString(R.string.state_connecting) to 0xFF4D8DFF.toInt()
                is ConnectionState.Reconnecting ->
                    context.getString(R.string.state_reconnecting) to 0xFF4D8DFF.toInt()
                is ConnectionState.Disconnecting ->
                    context.getString(R.string.state_disconnecting) to 0xFF4D8DFF.toInt()
                is ConnectionState.Error ->
                    context.getString(R.string.state_error) to 0xFFFF4D67.toInt()
                else ->
                    context.getString(R.string.state_idle) to 0xFFFF4D67.toInt()
            }
            views.setTextViewText(R.id.widget_large_status, statusText)
            views.setTextColor(R.id.widget_large_status, color)

            // -- flag + IP + location -----------------------------------------
            // NOTE: the "Your IP" / "Server IP" / "Location" labels are
            // deliberately plain English literals, not string resources —
            // matching the reference design, which keeps this compact/
            // technical row in English even under a Persian system locale.
            val ipInfo = AetherController.ipInfo.value
            views.setTextViewText(R.id.widget_flag, NetProbe.flagEmoji(ipInfo?.countryCode))
            val ipLabel = if (state.isConnected) "Server IP" else "Your IP"
            val ipValue = when {
                ipInfo != null -> ipInfo.ip
                state.isConnected || state.isBusy -> context.getString(R.string.ip_checking)
                else -> context.getString(R.string.ip_unavailable)
            }
            views.setTextViewText(R.id.widget_ip, "$ipLabel: $ipValue")

            // Full country name instead of the 2-letter code, e.g. "United
            // States" instead of "US" — falls back to the raw code if the
            // platform has no display name for it.
            val countryName = ipInfo?.countryCode?.let { cc ->
                runCatching { Locale.Builder().setRegion(cc).build().displayCountry }.getOrNull()
                    ?.takeIf { it.isNotBlank() && !it.equals(cc, ignoreCase = true) }
                    ?: cc
            }
            views.setTextViewText(R.id.widget_location, if (countryName != null) "Location: $countryName" else "")

            // -- latency pill --------------------------------------------------
            val ping = PingMonitor.state.value
            val latencyText = when {
                !state.isConnected -> "\u2014"
                ping.ms >= 0 -> "${ping.ms} ms"
                ping.running -> "\u2026"
                else -> "\u2014"
            }
            views.setTextViewText(R.id.widget_latency_badge, latencyText)

            // -- duration: native Chronometer, no polling needed ---------------
            val connectedSince = AetherController.connectedSince.value
            if (connectedSince != null) {
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectedSince)
                views.setChronometer(R.id.widget_duration_chronometer, base, null, true)
            } else {
                views.setChronometer(R.id.widget_duration_chronometer, SystemClock.elapsedRealtime(), null, false)
                views.setTextViewText(R.id.widget_duration_chronometer, "00:00:00")
            }

            // -- live speed ------------------------------------------------
            val down = if (state.isConnected) TrafficMonitor.downSpeedBps.value else 0L
            val up = if (state.isConnected) TrafficMonitor.upSpeedBps.value else 0L
            views.setTextViewText(R.id.widget_traffic_down, Formatters.formatRate(down))
            views.setTextViewText(R.id.widget_traffic_up, Formatters.formatRate(up))

            // -- interactions ------------------------------------------------
            val toggle = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AetherWidgetLargeProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle_large, toggle)

            val open = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_large_root, open)

            manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = AetherController.state.value
        appWidgetIds.forEach { paint(context, appWidgetManager, it, state) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        val state = AetherController.state.value
        if (state.isConnected || state.isBusy) {
            AetherController.disconnect(context)
            return
        }
        if (VpnService.prepare(context) != null) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_CONNECT_ON_LAUNCH, true),
            )
            return
        }
        val pending = goAsync()
        Thread {
            try {
                val profile = runBlocking {
                    ProfileStore(context.applicationContext).profile.first()
                }
                AetherController.connect(context.applicationContext, profile)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
