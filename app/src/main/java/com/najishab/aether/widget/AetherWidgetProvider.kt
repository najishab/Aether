package com.najishab.aether.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.najishab.aether.MainActivity
import com.najishab.aether.R
import com.najishab.aether.core.AetherController
import com.najishab.aether.data.ProfileStore
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.isBusy
import com.najishab.aether.model.isConnected

/**
 * Home-screen widget, ported from the merged AetherWidgetProvider and adapted
 * to Aether Mobile's [AetherController] / [ProfileStore] architecture.
 *
 * Shows the live connection state and offers one-tap connect/disconnect.
 * Tapping the body opens the app. Connect reuses the last saved profile; if
 * VPN consent is still missing the app is opened instead so the system
 * consent dialog can be shown (the tile uses the same flow).
 *
 * BATTERY: the provider metadata sets updatePeriodMillis=0, so the system
 * never wakes the app on a timer — repaints happen only on real connection
 * state changes via [updateAllWidgets], called from the VPN service's
 * existing state-change hook.
 */
class AetherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.najishab.aether.WIDGET_TOGGLE"

        /** Repaints every placed widget; called on each connection-state change. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, AetherWidgetProvider::class.java)
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
            val views = RemoteViews(context.packageName, R.layout.aether_widget)

            // Same "Dark Tech" green/red the app itself uses, so the widget
            // reads as the same product instead of its own colour scheme.
            val (text, color) = when (state) {
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
            views.setTextViewText(R.id.widget_status, text)
            views.setTextColor(R.id.widget_status, color)

            // Power button toggles the tunnel.
            val toggle = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AetherWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)

            // Tapping the body opens the app.
            val open = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

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
        // VPN consent missing -> open the app so the system dialog can show.
        if (VpnService.prepare(context) != null) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_CONNECT_ON_LAUNCH, true),
            )
            return
        }
        // Connect with the last saved profile. goAsync: the DataStore read
        // must not block the broadcast's main thread.
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
