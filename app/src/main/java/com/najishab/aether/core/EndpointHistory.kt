package com.najishab.aether.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Endpoint Health Check & History (independent, modular feature — see
 * EndpointHistoryActivity / EndpointHistoryStore). One endpoint the app has
 * actually completed a full self-tested connection to.
 *
 * Ground truth, not a guess: [endpoint] and [protocol] come from
 * [EngineMeta]'s parse of the engine's own "selected ... endpoint" log line
 * (or the pinned manual peer), [pingMs] from a fresh [PingMonitor] reading
 * taken the moment the connection is reported Connected, and [network] from
 * [currentNetworkLabel] at that same moment.
 */
data class EndpointHistoryEntry(
    /** "ip:port", exactly as the engine reported it. */
    val endpoint: String,
    /** [com.najishab.aether.model.Protocol] name, e.g. "WIREGUARD". */
    val protocol: String,
    /** Fresh tunnel-latency reading in ms at the moment of success, -1 if unknown. */
    val pingMs: Long,
    /** "Wi-Fi" on a Wi-Fi network, otherwise the cellular operator name. */
    val network: String,
    /** System.currentTimeMillis() of this success. */
    val lastSuccessMs: Long,
)

/**
 * Network label shown in the history list and used to match Smart Auto's
 * "known good for THIS network" priority lookup. No new permissions: same
 * ConnectivityManager/TelephonyManager reads [SmartAuto.fingerprint] already
 * performs, just distinguishing Wi-Fi first per product decision (Wi-Fi rows
 * never need — or get — an operator name).
 */
fun currentNetworkLabel(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    return when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "Cellular"
        }
        else -> "Unknown"
    }
}
