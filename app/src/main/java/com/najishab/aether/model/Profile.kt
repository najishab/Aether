package com.najishab.aether.model

/** Transport protocol, mapped 1:1 to the desktop app's CLI flags. */
enum class Protocol { AUTO, MASQUE, MASQUE_H2, WIREGUARD, GOOL }

/** Endpoint scanning strategy. */
enum class ScanMode { TURBO, BALANCED, THOROUGH, STEALTH, IRONCLAD }

/** IP family preference. */
enum class IpVersion { V4, V6, BOTH }

/** Anti-DPI obfuscation profile ("Amnezia"-style). */
enum class Noize { OFF, LIGHT, FIREWALL, BALANCED, GFW, AGGRESSIVE }

/**
 * ساده‌سازی شد: فقط حالت خودکار و آی‌پی دستی
 */
enum class EndpointMode { AUTO, MANUAL_PEER }

/** Per-app tunneling policy (split tunneling). */
enum class SplitMode { OFF, INCLUDE, EXCLUDE }

/** Cloudflare Zero Trust organization authentication. */
enum class TeamAuth { OFF, SERVICE_TOKEN, EMAIL, TOKEN }

/** Engine core log verbosity. */
enum class CoreLogLevel(val raw: String) { OFF("off"), ERROR("error"), WARN("warn"), INFO("info"), DEBUG("debug") }

data class ConnectionProfile(
    val protocol: Protocol = Protocol.AUTO,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val ipVersion: IpVersion = IpVersion.V4,
    val quickReconnect: Boolean = true,
    val masqueHttp2: Boolean = false,
    val lanShare: Boolean = false,

    val noize: Noize = Noize.OFF,
    val endpointMode: EndpointMode = EndpointMode.AUTO,
    val manualPeer: String = "",
    val keepalive: Int = 0,
    val fragment: Boolean = false,
    val ech: Boolean = false,

    val mtu: Int = DEFAULT_MTU,
    val proxyMode: Boolean = false,
    val splitMode: SplitMode = SplitMode.OFF,
    val splitApps: List<String> = emptyList(),

    val dnsServers: String = "",
    val team: String = "",
    val teamAuth: TeamAuth = TeamAuth.OFF,
    val accessClientId: String = "",
    val accessClientSecret: String = "",
    val accessEmail: String = "",
    val accessToken: String = "",
    val gateway: Boolean = false,
    val routeBlock: String = "",
    val routeDirect: String = "",

    val killSwitch: Boolean = false,
    val strictKillSwitch: Boolean = false,
    val ipv6LeakProtection: Boolean = true,
    val smartReconnect: Boolean = true,
    val reconnectRetryLimit: Int = 5,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    val noDataCheck: Boolean = false,
    val tlsGroups: String = "",
    val validateSecs: Int = 0,
    val reconnectSecs: Int = 0,
    val noProfileRetry: Boolean = false,
    val coreLogLevel: CoreLogLevel = CoreLogLevel.WARN,
    val blockedApps: List<String> = emptyList(),

    val upstreamProxy: String = "",
    val routeSniff: Boolean = true,
    val routeSniffMs: Int = 0,
    val autoReprovision: Boolean = true,
) {
    val hasTeam: Boolean
        get() = teamAuth != TeamAuth.OFF && team.isNotBlank()

    val hasManualPeer: Boolean
        get() = endpointMode == EndpointMode.MANUAL_PEER && manualPeer.isNotBlank()

    fun toArgs(): List<String> {
        val args = mutableListOf<String>()

        when (protocol) {
            Protocol.AUTO -> { }
            Protocol.MASQUE, Protocol.MASQUE_H2 -> args += "--masque"
            Protocol.WIREGUARD -> args += "--wg"
            Protocol.GOOL -> args += "--gool"
        }

        if (!hasManualPeer) {
            when (scanMode) {
                ScanMode.TURBO -> args += "--turbo"
                ScanMode.BALANCED -> args += "--balanced"
                ScanMode.THOROUGH -> args += "--thorough"
                ScanMode.STEALTH -> args += "--stealth"
                ScanMode.IRONCLAD -> args += "--ironclad"
            }
        }

        when (ipVersion) {
            IpVersion.V4 -> args += "-4"
            IpVersion.V6 -> args += "-6"
            IpVersion.BOTH -> args += "--dual"
        }

        args += if (quickReconnect) "--quick-reconnect" else "--no-quick-reconnect"

        if (noize != Noize.OFF) {
            args += "--noize"
            args += noize.name.lowercase()
        }

        if (hasManualPeer) {
            args += "--peer"
            args += manualPeer.trim()
        }

        if (fragment) args += "--fragment"
        if (ech) { args += "--ech"; args += "auto" }
        if (keepalive > 0) { args += "--keepalive"; args += keepalive.toString() }

        sanitizedDns().takeIf { it.isNotEmpty() }?.let {
            args += "--dns"
            args += it.joinToString(",")
        }

        if (hasTeam) {
            args += "--team"
            args += team.trim()
            if (gateway) args += "--gateway"
        }

        sanitizedRules(routeBlock).takeIf { it.isNotEmpty() }?.let {
            args += "--route-block"
            args += it.joinToString(",")
        }
        sanitizedRules(routeDirect).takeIf { it.isNotEmpty() }?.let {
            args += "--route-direct"
            args += it.joinToString(",")
        }

        if (fragment) {
            sanitizedRange(fragmentSize)?.let { args += "--fragment-size"; args += it }
            sanitizedRange(fragmentDelay)?.let { args += "--fragment-delay"; args += it }
        }
        sanitizedTlsGroups()?.let { args += "--tls-groups"; args += it }
        if (validateSecs > 0) { args += "--validate-secs"; args += validateSecs.coerceIn(1, 3600).toString() }
        if (reconnectSecs > 0) { args += "--reconnect-secs"; args += reconnectSecs.coerceIn(1, 600).toString() }
        if (noProfileRetry) args += "--no-profile-retry"

        return args
    }

    fun toEnv(): Map<String, String> = buildMap {
        val httpUpstream = sanitizedUpstream()?.startsWith("http://") == true
        put("AETHER_MASQUE_HTTP2", if (protocol == Protocol.MASQUE_H2 || masqueHttp2 || httpUpstream) "1" else "0")

        if (hasTeam) {
            when (teamAuth) {
                TeamAuth.SERVICE_TOKEN -> {
                    accessClientId.trim().takeIf { it.isNotEmpty() }
                        ?.let { put("AETHER_ACCESS_CLIENT_ID", it) }
                    accessClientSecret.trim().takeIf { it.isNotEmpty() }
                        ?.let { put("AETHER_ACCESS_CLIENT_SECRET", it) }
                }
                TeamAuth.EMAIL ->
                    accessEmail.trim().takeIf { it.isNotEmpty() }
                        ?.let { put("AETHER_ACCESS_EMAIL", it) }
                TeamAuth.TOKEN ->
                    accessToken.trim().takeIf { it.isNotEmpty() }
                        ?.let { put("AETHER_ACCESS_TOKEN", it) }
                TeamAuth.OFF -> Unit
            }
        }

        if (noDataCheck) {
            put("AETHER_MASQUE_NO_DATA_CHECK", "1")
            put("AETHER_WG_NO_DATA_CHECK", "1")
        }
        if (validateSecs > 0) {
            put("AETHER_MASQUE_VALIDATE_SECS", validateSecs.coerceIn(1, 3600).toString())
        }
        if (reconnectSecs > 0) {
            put("AETHER_MASQUE_RECONNECT_SECS", reconnectSecs.coerceIn(1, 600).toString())
            put("AETHER_WG_RECONNECT_SECS", reconnectSecs.coerceIn(1, 600).toString())
        }
        if (noProfileRetry) put("AETHER_WG_NO_PROFILE_RETRY", "1")
        sanitizedTlsGroups()?.let { put("AETHER_TLS_GROUPS", it) }
        if (coreLogLevel != CoreLogLevel.WARN) put("AETHER_LOG_LEVEL", coreLogLevel.raw)

        if (!routeSniff) put("AETHER_ROUTE_SNIFF", "0")
        if (routeSniff && routeSniffMs > 0) {
            put("AETHER_ROUTE_SNIFF_MS", routeSniffMs.coerceIn(50, 5_000).toString())
        }
        if (!autoReprovision) put("AETHER_REPROVISION", "0")

        sanitizedUpstream()?.let { put("AETHER_UPSTREAM", it) }
    }

    fun sanitizedDns(): List<String> = dnsServers
        .split(',', ' ', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && DNS_ENTRY.matches(it) }
        .distinct()
        .take(MAX_DNS_SERVERS)

    fun sanitizedRules(raw: String): List<String> = raw
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() && RULE_ENTRY.matches(it) }
        .distinct()
        .take(MAX_ROUTE_RULES)

    fun sanitizedUpstream(): String? = upstreamProxy.trim()
        .takeIf { it.length in 1..200 && UPSTREAM_ENTRY.matches(it) }

    fun connectTimeoutMs(): Long {
        if (hasManualPeer) return 45_000L
        return when (scanMode) {
            ScanMode.TURBO -> 60_000L
            ScanMode.BALANCED -> 150_000L
            ScanMode.STEALTH -> 240_000L
            ScanMode.THOROUGH -> 300_000L
            ScanMode.IRONCLAD -> 360_000L
        }
    }

    private fun sanitizedRange(raw: String): String? =
        raw.trim().takeIf { it.matches(Regex("^\\d{1,5}(-\\d{1,5})?$")) }

    private fun sanitizedTlsGroups(): String? =
        tlsGroups.trim().takeIf { it.matches(Regex("^[A-Za-z0-9:_-]{1,64}$")) }

    companion object {
        const val DEFAULT_MTU = 1280
        val MTU_PRESETS = listOf(1280, 1380, 1420, 1500, 8500)
        val KEEPALIVE_PRESETS = listOf(0, 10, 25, 45)
        const val MAX_DNS_SERVERS = 8
        const val MAX_ROUTE_RULES = 256

        private val DNS_ENTRY =
            Regex("^(?:\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:]+])(?::\\d{1,5})?$")

        private val UPSTREAM_ENTRY = Regex(
            "^(?:(?:socks5|http)://)?" +
                "(?:[^\\s:@/]{1,64}(?::[^\\s:@/]{0,64})?@)?" +
                "(?:\\[[0-9A-Fa-f:]{2,45}]|[A-Za-z0-9._-]{1,253}):\\d{1,5}$"
        )

        private val RULE_ENTRY = Regex("^[A-Za-z0-9_.:/*\\-\\[\\]^\$+?()|{}\\\\]{1,200}$")
    }
}