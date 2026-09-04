package com.najishab.aether.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.EndpointMode
import com.najishab.aether.model.Noize
import com.najishab.aether.model.Protocol
import com.najishab.aether.model.ScanMode
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * How hostile the current network's filtering (DPI) looks, derived from the
 * direct probes in [SmartAuto.fingerprint]:
 */
enum class DpiClass { OPEN, SNI_FILTERING, UDP_THROTTLED, HOSTILE }

/** Everything Smart Auto learned about the current network before connecting. */
data class NetworkFingerprint(
    val dpiClass: DpiClass,
    val udpOk: Boolean,
    val tlsSniOk: Boolean,
    val operatorName: String,
    /** True when the active transport is cellular AND the MCC is 432 (Iran). */
    val iranCellular: Boolean,
    /** WARP range CIDR -> TCP connect latency in ms (-1 = unreachable). */
    val edgeLatencyMs: Map<String, Long>,
)

/** One concrete, ready-to-launch strategy in the Smart Auto ladder. */
data class AutoCandidate(
    val profile: ConnectionProfile,
    val timeoutMs: Long,
    val label: String,
)

object SmartAuto {
    private const val TAG = "auto"
    private const val PROBE_TIMEOUT_MS = 3_000
    private const val TLS_PROBE_TIMEOUT_MS = 4_000

    /** Built-in Cloudflare WARP ranges + one representative probe host each. */
    private val EDGES = listOf(
        "162.159.192.0/24" to "162.159.192.1",
        "162.159.195.0/24" to "162.159.195.1",
        "188.114.96.0/24" to "188.114.96.1",
        "188.114.97.0/24" to "188.114.97.1",
        "8.6.112.0/24" to "8.6.112.1",
    )

    // ---- Stage 1+2: probe the network and classify its DPI ----------------

    suspend fun fingerprint(context: Context): NetworkFingerprint = withContext(Dispatchers.IO) {
        val (operatorName, iranCellular) = readOperator(context)
        DiagnosticsLog.i(
            TAG,
            "Fingerprinting the network — operator=\"$operatorName\"" +
                if (iranCellular) " (Iranian cellular)" else "",
        )
        val started = System.currentTimeMillis()
        val fp = coroutineScope {
            val udpCf = async { udpDnsProbe("1.1.1.1") }
            val udpGoog = async { udpDnsProbe("8.8.8.8") }
            val tls = async { tlsSniProbe() }
            val edgeJobs = EDGES.map { (cidr, probeIp) ->
                async { cidr to tcpLatencyMs(probeIp, 443) }
            }
            val udpOk = udpCf.await() || udpGoog.await()
            val tlsOk = tls.await()
            val edges = edgeJobs.awaitAll().toMap()
            val cls = when {
                udpOk && tlsOk -> DpiClass.OPEN
                udpOk -> DpiClass.SNI_FILTERING
                tlsOk -> DpiClass.UDP_THROTTLED
                else -> DpiClass.HOSTILE
            }
            NetworkFingerprint(cls, udpOk, tlsOk, operatorName, iranCellular, edges)
        }
        val edgeSummary = fp.edgeLatencyMs.entries.joinToString(", ") { (range, ms) ->
            "$range=${if (ms < 0) "unreachable" else "${ms}ms"}"
        }
        DiagnosticsLog.i(
            TAG,
            "DPI fingerprint ready in ${System.currentTimeMillis() - started} ms: " +
                "udp=${fp.udpOk} tlsSni=${fp.tlsSniOk} → ${fp.dpiClass} | edges: $edgeSummary",
        )
        fp
    }

    // ---- Stage 3: turn the fingerprint into an ordered strategy ladder ----

    fun buildPlan(
        user: ConnectionProfile,
        fp: NetworkFingerprint,
        history: List<EndpointHistoryEntry> = emptyList(),
        networkLabel: String? = null,
    ): List<AutoCandidate> {
        // NEVER override an endpoint the user pinned manually in Settings.
        val keepUserEndpoint = user.endpointMode != EndpointMode.AUTO

        val knownGood = if (!keepUserEndpoint && networkLabel != null) {
            history.asSequence()
                .filter { it.network == networkLabel }
                .sortedByDescending { it.lastSuccessMs }
                .take(2)
                .mapNotNull { entry ->
                    val proto = runCatching { Protocol.valueOf(entry.protocol) }.getOrNull()
                        ?: return@mapNotNull null
                    val p = user.copy(
                        protocol = proto,
                        endpointMode = EndpointMode.MANUAL_PEER,
                        manualPeer = entry.endpoint,
                        scanMode = ScanMode.TURBO,
                    )
                    AutoCandidate(p, p.connectTimeoutMs(), "${proto.name} · known-good ${entry.endpoint} (history)")
                }
                .toList()
        } else {
            emptyList()
        }

        fun cand(
            proto: Protocol,
            noize: Noize,
            h2: Boolean = false,
            frag: Boolean = false,
            ech: Boolean = false,
        ): AutoCandidate {
            var mergedNoize = if (user.noize.ordinal >= noize.ordinal) user.noize else noize
            if (mergedNoize == Noize.OFF && fp.iranCellular) mergedNoize = Noize.LIGHT
            val p = user.copy(
                protocol = proto,
                noize = mergedNoize,
                masqueHttp2 = user.masqueHttp2 || (h2 && proto == Protocol.MASQUE),
                fragment = user.fragment || frag,
                ech = user.ech || ech,
                scanMode = ScanMode.TURBO,
            )
            val label = buildString {
                append(proto.name)
                append(" · noize=").append(p.noize.name.lowercase())
                if (p.masqueHttp2) append(" · h2")
                if (p.fragment) append(" · fragment")
                if (p.ech) append(" · ech")
                append(" · scan=turbo")
            }
            return AutoCandidate(p, p.connectTimeoutMs(), label)
        }

        val ladder = when (fp.dpiClass) {
            DpiClass.OPEN -> listOf(
                cand(Protocol.WIREGUARD, Noize.OFF),
                cand(Protocol.MASQUE, Noize.OFF),
                cand(Protocol.GOOL, Noize.LIGHT),
            )
            DpiClass.SNI_FILTERING -> listOf(
                cand(Protocol.WIREGUARD, Noize.BALANCED),
                cand(Protocol.GOOL, Noize.BALANCED),
                cand(Protocol.MASQUE, Noize.FIREWALL, frag = true, ech = true),
            )
            DpiClass.UDP_THROTTLED -> listOf(
                cand(Protocol.MASQUE, Noize.LIGHT, h2 = true, frag = true, ech = true),
                cand(Protocol.GOOL, Noize.AGGRESSIVE),
                cand(Protocol.WIREGUARD, Noize.GFW),
            )
            DpiClass.HOSTILE -> listOf(
                cand(Protocol.MASQUE, Noize.GFW, h2 = true, frag = true, ech = true),
                cand(Protocol.GOOL, Noize.AGGRESSIVE),
                cand(Protocol.WIREGUARD, Noize.AGGRESSIVE),
            )
        }

        // Last resort: the top strategy again
        val first = ladder.first()
        val fbProfile = first.profile.copy(
            scanMode = user.scanMode,
            endpointMode = if (keepUserEndpoint) user.endpointMode else EndpointMode.AUTO,
        )
        val fallback = AutoCandidate(
            fbProfile,
            fbProfile.connectTimeoutMs(),
            "${fbProfile.protocol.name} · noize=${fbProfile.noize.name.lowercase()} · full built-in ranges " +
                "· scan=${user.scanMode.name.lowercase()} (last resort)",
        )

        val plan = (knownGood + ladder + fallback).distinctBy { it.profile }
        DiagnosticsLog.i(TAG, "Strategy ladder for ${fp.dpiClass} (${plan.size} steps, ${knownGood.size} from history):")
        plan.forEachIndexed { i, c -> DiagnosticsLog.i(TAG, "  ${i + 1}. ${c.label}") }
        return plan
    }

    // ---- Probes ------------------------------------------------------------

    private fun udpDnsProbe(server: String, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean = runCatching {
        DatagramSocket().use { sock ->
            sock.soTimeout = timeoutMs
            val query = byteArrayOf(
                0x1A, 0x2B,
                0x01, 0x00,
                0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
                'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
                3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
                0,
                0x00, 0x01,
                0x00, 0x01,
            )
            sock.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(512)
            sock.receive(DatagramPacket(buf, buf.size))
            val ok = buf[0] == 0x1A.toByte() && buf[1] == 0x2B.toByte()
            DiagnosticsLog.d(TAG, "udp53 probe $server → ${if (ok) "answered" else "bad reply"}")
            ok
        }
    }.getOrElse {
        DiagnosticsLog.d(TAG, "udp53 probe $server → no answer (${it.message})")
        false
    }

    private fun tcpLatencyMs(ip: String, port: Int, timeoutMs: Int = PROBE_TIMEOUT_MS): Long = runCatching {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
        (System.nanoTime() - start) / 1_000_000
    }.getOrDefault(-1L)

    private fun tlsSniProbe(timeoutMs: Int = TLS_PROBE_TIMEOUT_MS): Boolean = runCatching {
        Socket().use { raw ->
            raw.connect(InetSocketAddress("1.1.1.1", 443), timeoutMs)
            raw.soTimeout = timeoutMs
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, "www.cloudflare.com", 443, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()
            val ok = HttpsURLConnection.getDefaultHostnameVerifier().verify("www.cloudflare.com", ssl.session)
            runCatching { ssl.close() }
            DiagnosticsLog.d(TAG, "tls-sni probe → ${if (ok) "handshake ok" else "hostname mismatch"}")
            ok
        }
    }.getOrElse {
        DiagnosticsLog.d(TAG, "tls-sni probe → failed (${it.message})")
        false
    }

    private fun readOperator(context: Context): Pair<String, Boolean> = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val name = tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "unknown"
        val mcc = tm?.networkOperator?.take(3)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        name to (cellular && mcc == "432")
    }.getOrDefault("unknown" to false)
}