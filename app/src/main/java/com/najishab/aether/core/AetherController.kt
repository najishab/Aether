package com.najishab.aether.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.CoreLogLevel
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.isBusy
import com.najishab.aether.model.EndpointMode
import com.najishab.aether.model.IpVersion
import com.najishab.aether.model.Noize
import com.najishab.aether.model.Protocol
import com.najishab.aether.model.ScanMode
import com.najishab.aether.model.SplitMode
import com.najishab.aether.model.TeamAuth
import com.najishab.aether.vpn.AetherVpnService

/**
 * App-wide singleton that (a) publishes the live [ConnectionState] to the UI and
 * (b) sends connect/disconnect intents to [AetherVpnService].
 */
object AetherController {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Epoch millis of when the current session became Connected, or null. */
    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    /** IP + country shown in the UI (exit server when connected, operator when not). */
    private val _ipInfo = MutableStateFlow<IpEndpoint?>(null)
    val ipInfo: StateFlow<IpEndpoint?> = _ipInfo.asStateFlow()

    /** True while an IP lookup is in flight (drives the “…” placeholder). */
    private val _ipLoading = MutableStateFlow(false)
    val ipLoading: StateFlow<Boolean> = _ipLoading.asStateFlow()

    /** Called by the service to broadcast state changes. */
    fun setState(newState: ConnectionState) {
        val previous = _state.value
        _state.value = newState
        when (newState) {
            is ConnectionState.Connected -> {
                if (_connectedSince.value == null) _connectedSince.value = System.currentTimeMillis()
                if (previous !is ConnectionState.Connected) {
                    AetherAnalytics.logConnect(newState.socksAddr)
                }
            }
            is ConnectionState.Reconnecting -> {
                // Keep the running timer during a transient reconnect.
            }
            is ConnectionState.Error -> {
                _connectedSince.value = null
                AetherAnalytics.logConnectFailed(newState.message)
            }
            else -> {
                if (previous is ConnectionState.Connected || previous.isBusy) {
                    AetherAnalytics.logDisconnect()
                }
                _connectedSince.value = null
            }
        }
    }

    fun setIpInfo(info: IpEndpoint?) {
        _ipInfo.value = info
    }

    /**
     * Sets the tunnel exit IP only when the badge doesn't already show a
     * tunnel IP for this session.
     */
    fun offerTunnelIpInfo(info: IpEndpoint) {
        if (_ipInfo.value?.viaTunnel == true) return
        _ipInfo.value = info
    }

    fun setIpLoading(loading: Boolean) {
        _ipLoading.value = loading
    }

    /**
     * Returns a consent Intent if the user must still grant VPN permission,
     * or null if permission was already granted.
     */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    fun connect(context: Context, profile: ConnectionProfile) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_CONNECT
            putExtra(AetherVpnService.EXTRA_PROFILE, ProfileCodec.encode(profile))
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, AetherVpnService::class.java).apply {
            action = AetherVpnService.ACTION_DISCONNECT
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

/**
 * Serialises a [ConnectionProfile] into a compact `key=value` list (one pair per
 * line) for Intent transport.
 */
object ProfileCodec {
    fun encode(p: ConnectionProfile): String = buildList {
        add("protocol=${p.protocol.name}")
        add("scan=${p.scanMode.name}")
        add("ip=${p.ipVersion.name}")
        add("quick=${p.quickReconnect}")
        add("h2=${p.masqueHttp2}")
        add("share=${p.lanShare}")
        add("noize=${p.noize.name}")
        add("endpoint=${p.endpointMode.name}")
        addString("peer", p.manualPeer)
        add("keepalive=${p.keepalive}")
        add("fragment=${p.fragment}")
        add("ech=${p.ech}")
        add("mtu=${p.mtu}")
        add("proxy=${p.proxyMode}")
        add("split=${p.splitMode.name}")
        add("splitApps=${p.splitApps.joinToString(",")}")
        addString("dns", p.dnsServers)
        addString("team", p.team)
        add("teamAuth=${p.teamAuth.name}")
        addString("accessId", p.accessClientId)
        addString("accessSecret", p.accessClientSecret)
        addString("accessEmail", p.accessEmail)
        addString("accessToken", p.accessToken)
        add("gateway=${p.gateway}")
        addString("routeBlock", p.routeBlock)
        addString("routeDirect", p.routeDirect)
        add("kill=${p.killSwitch}")
        add("strictKill=${p.strictKillSwitch}")
        add("v6leak=${p.ipv6LeakProtection}")
        add("smartRe=${p.smartReconnect}")
        add("reLimit=${p.reconnectRetryLimit}")
        addString("fSize", p.fragmentSize)
        addString("fDelay", p.fragmentDelay)
        add("noDataCheck=${p.noDataCheck}")
        addString("tlsGroups", p.tlsGroups)
        add("valSecs=${p.validateSecs}")
        add("recSecs=${p.reconnectSecs}")
        add("noProfRetry=${p.noProfileRetry}")
        add("coreLog=${p.coreLogLevel.name}")
        add("blockedApps=${p.blockedApps.joinToString(",")}")
        addString("upstreamProxy", p.upstreamProxy)
        add("routeSniff=${p.routeSniff}")
        add("routeSniffMs=${p.routeSniffMs}")
        add("autoReprovision=${p.autoReprovision}")
    }.joinToString("\n")

    fun decode(raw: String?): ConnectionProfile {
        if (raw.isNullOrBlank()) return ConnectionProfile()
        if (!raw.contains('=') && raw.contains('|')) return decodeLegacy(raw)

        val map = raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
        val d = ConnectionProfile()
        return runCatching {
            ConnectionProfile(
                protocol = map["protocol"]?.let { enumOr<Protocol>(it) } ?: d.protocol,
                scanMode = map["scan"]?.let { enumOr<ScanMode>(it) } ?: d.scanMode,
                ipVersion = map["ip"]?.let { enumOr<IpVersion>(it) } ?: d.ipVersion,
                quickReconnect = map["quick"]?.toBooleanStrictOrNull() ?: d.quickReconnect,
                masqueHttp2 = map["h2"]?.toBooleanStrictOrNull() ?: d.masqueHttp2,
                lanShare = map["share"]?.toBooleanStrictOrNull() ?: d.lanShare,
                noize = map["noize"]?.let { enumOr<Noize>(it) } ?: d.noize,
                endpointMode = map["endpoint"]?.let { enumOr<EndpointMode>(it) } ?: d.endpointMode,
                manualPeer = map.string("peer", d.manualPeer),
                keepalive = map["keepalive"]?.toIntOrNull() ?: d.keepalive,
                fragment = map["fragment"]?.toBooleanStrictOrNull() ?: d.fragment,
                ech = map["ech"]?.toBooleanStrictOrNull() ?: d.ech,
                mtu = map["mtu"]?.toIntOrNull() ?: d.mtu,
                proxyMode = map["proxy"]?.toBooleanStrictOrNull() ?: d.proxyMode,
                splitMode = map["split"]?.let { enumOr<SplitMode>(it) } ?: d.splitMode,
                splitApps = map["splitApps"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.splitApps,
                dnsServers = map.string("dns", d.dnsServers),
                team = map.string("team", d.team),
                teamAuth = map["teamAuth"]?.let { enumOr<TeamAuth>(it) } ?: d.teamAuth,
                accessClientId = map.string("accessId", d.accessClientId),
                accessClientSecret = map.string("accessSecret", d.accessClientSecret),
                accessEmail = map.string("accessEmail", d.accessEmail),
                accessToken = map.string("accessToken", d.accessToken),
                gateway = map["gateway"]?.toBooleanStrictOrNull() ?: d.gateway,
                routeBlock = map.string("routeBlock", d.routeBlock),
                routeDirect = map.string("routeDirect", d.routeDirect),
                killSwitch = map["kill"]?.toBooleanStrictOrNull() ?: d.killSwitch,
                strictKillSwitch = map["strictKill"]?.toBooleanStrictOrNull() ?: d.strictKillSwitch,
                ipv6LeakProtection = map["v6leak"]?.toBooleanStrictOrNull() ?: d.ipv6LeakProtection,
                smartReconnect = map["smartRe"]?.toBooleanStrictOrNull() ?: d.smartReconnect,
                reconnectRetryLimit = map["reLimit"]?.toIntOrNull() ?: d.reconnectRetryLimit,
                fragmentSize = map.string("fSize", d.fragmentSize),
                fragmentDelay = map.string("fDelay", d.fragmentDelay),
                noDataCheck = map["noDataCheck"]?.toBooleanStrictOrNull() ?: d.noDataCheck,
                tlsGroups = map.string("tlsGroups", d.tlsGroups),
                validateSecs = map["valSecs"]?.toIntOrNull() ?: d.validateSecs,
                reconnectSecs = map["recSecs"]?.toIntOrNull() ?: d.reconnectSecs,
                noProfileRetry = map["noProfRetry"]?.toBooleanStrictOrNull() ?: d.noProfileRetry,
                coreLogLevel = map["coreLog"]?.let { enumOr<CoreLogLevel>(it) } ?: d.coreLogLevel,
                blockedApps = map["blockedApps"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: d.blockedApps,
                upstreamProxy = map.string("upstreamProxy", d.upstreamProxy),
                routeSniff = map["routeSniff"]?.toBooleanStrictOrNull() ?: d.routeSniff,
                routeSniffMs = map["routeSniffMs"]?.toIntOrNull() ?: d.routeSniffMs,
                autoReprovision = map["autoReprovision"]?.toBooleanStrictOrNull() ?: d.autoReprovision,
            )
        }.getOrDefault(d)
    }

    private fun decodeLegacy(raw: String): ConnectionProfile {
        val parts = raw.split("|")
        if (parts.size < 5) return ConnectionProfile()
        return runCatching {
            ConnectionProfile(
                protocol = Protocol.valueOf(parts[0]),
                scanMode = ScanMode.valueOf(parts[1]),
                ipVersion = IpVersion.valueOf(parts[2]),
                quickReconnect = parts[3].toBoolean(),
                masqueHttp2 = parts[4].toBoolean(),
                lanShare = parts.getOrNull(5)?.toBoolean() ?: false,
            )
        }.getOrDefault(ConnectionProfile())
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()

    private fun MutableList<String>.addString(key: String, value: String) {
        val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        add("$key=b64:$encoded")
    }

    private fun Map<String, String>.string(key: String, default: String): String {
        val value = get(key) ?: return default
        return if (value.startsWith("b64:")) {
            val encoded = value.removePrefix("b64:")
            runCatching {
                String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault(default)
        } else {
            value
        }
    }
}