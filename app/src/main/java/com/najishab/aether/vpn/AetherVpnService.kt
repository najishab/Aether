package com.najishab.aether.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.najishab.aether.AetherApp
import com.najishab.aether.MainActivity
import com.najishab.aether.R
import com.najishab.aether.core.AetherController
import com.najishab.aether.core.AetherProcess
import com.najishab.aether.core.Diagnostics
import com.najishab.aether.core.DiagnosticsLog
import com.najishab.aether.core.EngineMeta
import com.najishab.aether.core.AutoCandidate
import com.najishab.aether.core.EndpointHistoryEntry
import com.najishab.aether.core.PingMonitor
import com.najishab.aether.core.PortProbe
import com.najishab.aether.core.currentNetworkLabel
import com.najishab.aether.data.EndpointHistoryStore
import com.najishab.aether.core.ProfileCodec
import com.najishab.aether.core.HevTunnel
import com.najishab.aether.core.RoutingEngine
import com.najishab.aether.core.ShareBridge
import com.najishab.aether.core.SmartAuto
import com.najishab.aether.core.SocksTunBridge
import com.najishab.aether.core.TunnelUsageTracker
import com.najishab.aether.core.TunnelConfig
import com.najishab.aether.data.TunnelUsageSource
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.IpVersion
import com.najishab.aether.model.Noize
import com.najishab.aether.model.Protocol
import com.najishab.aether.model.SplitMode
import com.najishab.aether.widget.AetherWidgetLargeProvider
import com.najishab.aether.widget.AetherWidgetProvider
import java.io.File

/**
 * The heart of the app. On connect it:
 *   1. launches the bundled `aether` engine (opens SOCKS5 on 127.0.0.1:1819),
 *   2. waits until that port is actually reachable (ground-truth check),
 *   3. builds the VPN TUN interface,
 *   4. starts the embedded hev-socks5-tunnel core (libhev-socks5-tunnel.so) to forward all
 *      traffic through the proxy — replacing the need for v2rayNG entirely,
 *   5. supervises both processes and auto-reconnects on failure.
 */
class AetherVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var engine: AetherProcess? = null
    private var tunnelStarted: Boolean = false
    private var runJob: Job? = null

    /**
     * The teardown coroutine of the PREVIOUS session, if one is still
     * finishing. A new connect waits for it instead of racing it (1.2.2
     * protocol-switch fix).
     */
    private var stopJob: Job? = null

    /** Active userspace filter bridge (only when per-app blocking is on). */
    private var tunBridge: SocksTunBridge? = null
    private val usageTracker by lazy { TunnelUsageTracker(applicationContext, scope) }
    private var hevUsageJob: Job? = null

    /** Last profile the service ran with (kill-switch decisions). */
    private var lastProfile: ConnectionProfile? = null

    /** True while the kill-switch blackhole TUN is up. */
    @Volatile
    private var lockdownTunActive = false

    /** Consecutive failed watchdog probes (1.2.4 stability watchdog). */
    private var probeFailures = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                // ANDROID FGS CONTRACT FIX: this branch is reached via
                // ContextCompat.startForegroundService() (see
                // AetherController.disconnect()) — e.g. from the widgets'
                // toggle button. Android requires startForeground() to be
                // called within a few seconds of EVERY startForegroundService()
                // call, even when the service is already in the foreground
                // state from a previous connect. Skipping it here (the old
                // code went straight to stopEverything()) let the system kill
                // the process with ForegroundServiceDidNotStartInTimeException
                // right as it was tearing down anyway — harmless in effect
                // (disconnect still completed) but a crash in the logs.
                startForeground(NOTIF_ID, buildNotification(getString(R.string.state_disconnecting)))
                // STRICT KILL SWITCH (1.2.4): a manual disconnect must not
                // open a leak window. With strict mode on, the first
                // disconnect engages lockdown instead; disconnecting FROM
                // lockdown lifts it.
                val last = lastProfile
                when {
                    lockdownTunActive -> stopEverything()
                    last != null && last.strictKillSwitch -> enterLockdown(last)
                    else -> stopEverything()
                }
                return START_NOT_STICKY
            }
            else -> {
                val profile = ProfileCodec.decode(intent?.getStringExtra(EXTRA_PROFILE))
                startForeground(NOTIF_ID, buildNotification(getString(R.string.state_launching)))
                startTunnel(profile)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(profile: ConnectionProfile) {
        lastProfile = profile
        // 1.2.2 PROTOCOL-SWITCH FIX: this used to bail out silently whenever a
        // previous run coroutine was still winding down ("if active, return"),
        // so a connect tapped right after a disconnect — or right after
        // switching protocol — was simply DROPPED. The user then waited,
        // tapped again, and the app looked like it took forever to start.
        // Now the new session takes ownership: it waits for the old one to
        // finish, tears its natives down, and only then launches the engine.
        val previousRun = runJob
        val previousStop = stopJob
        runJob = scope.launch {
            if (previousRun != null) {
                // Same ordering rule as the disconnect path: cancel, kill the
                // natives (which unblocks the old session immediately), and
                // only then wait for it to finish. Joining first would stall
                // the new connect for as long as the old session's engine wait
                // still had to run.
                previousRun.cancel()
                cleanupNativeOnly()
                previousRun.join()
            }
            previousStop?.join()
            try {
                connectFlow(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AetherController.setState(
                    ConnectionState.Error(e.message ?: getString(R.string.state_error)),
                )
                updateNotification(getString(R.string.state_error))
                cleanupNativeOnly()
            }
        }
    }

    private suspend fun connectFlow(profile: ConnectionProfile) {
        DiagnosticsLog.clear()
        // STALE-CIRCLES ROOT-CAUSE FIX: the four self-test circles were only
        // reset inside Diagnostics.run(), which starts AFTER the engine has
        // launched AND finished its endpoint scan — so on a reconnect the
        // previous session's green circles sat on screen for the entire scan
        // and appeared to "reset late". Reset them the INSTANT a new connect
        // starts, so the panel always reflects the current attempt on time.
        Diagnostics.resetChecks()
        EngineMeta.reset()
        DiagnosticsLog.i(TAG, "Connect requested — protocol=${profile.protocol} scan=${profile.scanMode} ip=${profile.ipVersion}")

        val resolved: ConnectionProfile =
            if (profile.protocol == Protocol.AUTO) {
                connectSmartAuto(profile)
            } else {
                // An explicitly chosen protocol keeps that protocol; the
                // engine still selects its own endpoint (see [directPlan]).
                AetherController.setState(ConnectionState.Launching)
                runLadder(directPlan(profile), getString(R.string.err_protocol_failed))
            }

        // Desktop-parity info row (1.2.4): publish the protocol that actually
        // won (Smart Auto resolves AUTO to a concrete protocol). The endpoint
        // arrives through EngineMeta's engine-log parser; for a pinned peer we
        // already know it here (no selection line is logged).
        EngineMeta.setProtocol(resolved.protocol.name)
        if (resolved.manualPeer.isNotBlank()) EngineMeta.setEndpoint(resolved.manualPeer)

        AetherController.setState(ConnectionState.Connected("$SOCKS_HOST:$SOCKS_PORT"))
        updateNotification(getString(R.string.state_connected))
        DiagnosticsLog.i(TAG, "All checks passed — tunnel is ready.")

        recordEndpointHistory(resolved)

        superviseEngine(resolved)
    }

    /**
     * Endpoint Health Check & History (independent, modular feature — see
     * EndpointHistoryActivity): the instant a connection is reported
     * Connected, snapshot the ground-truth endpoint EngineMeta parsed from
     * the engine's own log (or the pinned manual peer), a fresh tunnel ping,
     * and the current network label, and persist it. Fire-and-forget on the
     * service's own scope so it can never delay reporting Connected.
     */
    private fun recordEndpointHistory(resolved: ConnectionProfile) {
        scope.launch {
            val endpoint = awaitEndpointForHistory() ?: run {
                DiagnosticsLog.w(TAG, "Endpoint history skipped: engine did not publish an endpoint.")
                return@launch
            }
            runCatching { PingMonitor.pingOnce(viaTunnel = true) }
            val ping = PingMonitor.state.value.ms
            runCatching {
                EndpointHistoryStore(applicationContext).recordSuccess(
                    EndpointHistoryEntry(
                        endpoint = endpoint,
                        protocol = resolved.protocol.name,
                        pingMs = ping,
                        network = currentNetworkLabel(applicationContext),
                        lastSuccessMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun awaitEndpointForHistory(): String? {
        repeat(10) {
            EngineMeta.state.value.endpoint?.takeIf { it.isNotBlank() }?.let { return it }
            delay(100L)
        }
        return null
    }

    /**
     * SMART AUTO (root-cause rework of the broken Auto protocol): fingerprint
     * the network's DPI first (see [SmartAuto]), then walk an ordered ladder
     * of concrete strategies — protocol + obfuscation + the IP ranges that
     * actually answered on THIS network — until one passes the full 4-step
     * self-test. Returns the strategy that won so the supervisor restarts the
     * engine with the SAME working configuration.
     */
    private suspend fun connectSmartAuto(userProfile: ConnectionProfile): ConnectionProfile {
        AetherController.setState(ConnectionState.Launching)
        updateNotification(getString(R.string.state_analyzing))
        val fingerprint = SmartAuto.fingerprint(this)
        // Smart Priority: known-good endpoints for THIS network get a shot
        // before the fresh DPI ladder (see EndpointHistoryStore / SmartAuto).
        val networkLabel = currentNetworkLabel(this)
        val history = runCatching { EndpointHistoryStore(applicationContext).recent() }.getOrDefault(emptyList())
        val plan = SmartAuto.buildPlan(userProfile, fingerprint, history, networkLabel)
        return runLadder(plan, getString(R.string.err_auto_failed))
    }

    /**
     * Two-pass plan for a protocol the user picked by hand (MASQUE, WireGuard
     * or Gool).
     *
     * 1.2.2 "MASQUE hangs forever" FIX: a hand-picked protocol used to get ONE
     * attempt with the full scan budget of the selected scan mode — up to 150 s
     * on Balanced and 300 s on Thorough — with no second chance. On a network
     * where QUIC/UDP is throttled that means the user stares at "Connecting"
     * for minutes and then just fails, while Smart mode (which walks a ladder
     * of shorter, hardened attempts) connects in seconds. So the chosen
     * protocol now gets:
     *   1. a first pass exactly as configured, on a capped budget, and
     *   2. if that fails, the SAME protocol again with anti-DPI hardening
     *      (obfuscation on, plus HTTP/2 + TLS fragmentation + ECH for MASQUE)
     *      on the full budget.
     * The protocol the user chose is never swapped for another one.
     */
    private fun directPlan(profile: ConnectionProfile): List<AutoCandidate> {
        val fullBudget = profile.connectTimeoutMs()
        val hardenedNoize = if (profile.noize == Noize.OFF) Noize.FIREWALL else profile.noize
        val masque = profile.protocol == Protocol.MASQUE || profile.protocol == Protocol.MASQUE_H2
        val isH2 = profile.protocol == Protocol.MASQUE_H2 || profile.masqueHttp2
        val hardened = profile.copy(
            noize = hardenedNoize,
            masqueHttp2 = isH2 || masque,
            fragment = profile.fragment || masque,
            ech = profile.ech || masque,
        )
        if (hardened == profile) {
            return listOf(
                AutoCandidate(profile, fullBudget, "${profile.protocol.name} · as configured"),
            )
        }
        return listOf(
            AutoCandidate(
                profile,
                fullBudget.coerceAtMost(FIRST_PASS_MAX_MS),
                "${profile.protocol.name} · as configured",
            ),
            AutoCandidate(
                hardened,
                fullBudget,
                "${profile.protocol.name} · noize=${hardenedNoize.name.lowercase()}" +
                    (if (masque) " · h2 · fragment · ech" else "") + " (anti-DPI pass)",
            ),
        )
    }

    /**
     * Walks a ladder of strategies until one comes up and passes the full
     * self-test. Each failed rung is torn down before the next is tried.
     */
    private suspend fun runLadder(
        plan: List<AutoCandidate>,
        failureMessage: String,
    ): ConnectionProfile {
        var lastError: Exception? = null

        plan.forEachIndexed { index, candidate ->
            DiagnosticsLog.i(TAG, "Attempt ${index + 1}/${plan.size} → ${candidate.label}")
            try {
                connectAttempt(candidate.profile, candidate.timeoutMs)
                DiagnosticsLog.i(TAG, "Connected using ${candidate.label}")
                return candidate.profile
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                DiagnosticsLog.w(
                    TAG,
                    "${candidate.label} failed (${e.message}) — moving to the next strategy.",
                )
                cleanupNativeOnly()
                Diagnostics.resetChecks()
            }
        }

        throw IllegalStateException(failureMessage, lastError)
    }

    /**
     * One full connect attempt with a CONCRETE protocol: launch engine, wait
     * for SOCKS5, bring up TUN/proxy, and gate on the 4-step self-test.
     * Throws on any failure; the caller decides whether to retry differently.
     */
    private suspend fun connectAttempt(
        profile: ConnectionProfile,
        timeoutMs: Long,
    ) {
        AetherController.setState(ConnectionState.Launching)
        updateNotification(getString(R.string.state_launching))
        // 1.2.2 PROTOCOL-SWITCH FIX: never start an engine on top of a dying
        // one. Tear the previous natives down and wait for the local SOCKS5
        // port to be released first, otherwise the probe below can "see" the
        // old listener and the whole attempt is verified against a socket that
        // is about to disappear.
        // Leaving lockdown (if any): the blackhole TUN is torn down here.
        lockdownTunActive = false
        cleanupNativeOnly()
        if (!PortProbe.awaitClosed(SOCKS_HOST, SOCKS_PORT, PORT_RELEASE_WAIT_MS)) {
            DiagnosticsLog.w(
                TAG,
                "Local port $SOCKS_PORT is still busy after ${PORT_RELEASE_WAIT_MS / 1000}s — starting anyway.",
            )
        }
        DiagnosticsLog.i(TAG, "Launching engine (libaether.so)…")
        engine = AetherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }

        AetherController.setState(ConnectionState.Connecting)
        updateNotification(getString(R.string.state_connecting))
        // Timeout comes from the caller: the profile's scan-mode budget for a
        // direct connect, or the per-candidate budget in the Smart Auto ladder.
        DiagnosticsLog.i(
            TAG,
            "Waiting for SOCKS5 on $SOCKS_HOST:$SOCKS_PORT… (scan=${profile.scanMode}, timeout=${timeoutMs / 1000}s)",
        )
        val opened = PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, timeoutMs) { engine?.isAlive() == true }
        if (!opened) {
            val engineDied = engine?.isAlive() != true
            if (engineDied) {
                DiagnosticsLog.e(TAG, "Engine exited before it opened the SOCKS5 port.")
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            DiagnosticsLog.e(TAG, "Engine still scanning after ${timeoutMs / 1000}s — SOCKS5 port never opened.")
            throw IllegalStateException(getString(R.string.err_engine_timeout))
        }
        DiagnosticsLog.i(TAG, "SOCKS5 port is up.")

        if (profile.proxyMode) {
            // Proxy mode: DON'T capture the whole device through a system TUN.
            // Instead expose the engine's SOCKS5 + an HTTP proxy so individual
            // apps (or the Wi-Fi proxy setting) can opt in. This is ideal when
            // only one app (e.g. Telegram) needs the tunnel. LAN exposure only
            // happens when the user explicitly turned sharing on.
            //
            // startSync is ground truth: in proxy mode these listeners ARE the
            // product, so a bind failure must fail the connection loudly
            // instead of claiming "Local proxy ready" over dead ports (the old
            // fire-and-forget start swallowed EADDRINUSE and still reported
            // 1080/8118 as ready — external apps then couldn't connect).
            val shareReady = ShareBridge.startSync(localOnly = !profile.lanShare, tracker = usageTracker)
            if (!shareReady) {
                DiagnosticsLog.e(TAG, "Proxy mode: the fixed local proxy ports could not be opened (see errors above).")
                throw IllegalStateException(getString(R.string.err_proxy_ports))
            }
            // Ports are FIXED (v2rayNG-style standard) — the same values are
            // shown as copyable rows under the Proxy-mode toggle in the UI.
            DiagnosticsLog.i(
                TAG,
                "Proxy mode: system TUN skipped. Local proxy ready — " +
                    "SOCKS5 127.0.0.1:${ShareBridge.SOCKS_SHARE_PORT}, HTTP 127.0.0.1:${ShareBridge.HTTP_SHARE_PORT}",
            )
        } else {
            establishTun(profile)
            startTun2Socks(profile)
            // LAN sharing: if the user enabled it, expose the tunnel to other
            // devices on the same Wi-Fi/hotspot (HTTP + SOCKS5 bridge).
            if (profile.lanShare) ShareBridge.start(localOnly = false, tracker = usageTracker)
        }

        // GATING FIX: the app used to report Connected the moment the TUN /
        // proxy was up while the 4-step self-test still ran in the background —
        // users saw "Connected" long before the tunnel could actually carry
        // traffic (and before the IP + flag appeared). The state is now held at
        // Verifying, and Connected is reported ONLY after all four checks pass,
        // so Connected == genuinely ready to browse.
        AetherController.setState(ConnectionState.Verifying)
        updateNotification(getString(R.string.state_verifying))
        DiagnosticsLog.i(
            TAG,
            if (profile.proxyMode) "Proxy started. Verifying end-to-end connectivity…"
            else "TUN + hev tunnel started. Verifying end-to-end connectivity…",
        )

        // In proxy mode, test THROUGH the shared SOCKS5 listener — the exact
        // endpoint external apps connect to — so a dead bridge can no longer
        // hide behind a passing engine-port (1819) self-test.
        val diagPort =
            if (profile.proxyMode) ShareBridge.socksPort.value ?: SOCKS_PORT
            else SOCKS_PORT
        val healthy = runCatching { Diagnostics.run(port = diagPort) }.getOrDefault(false)
        if (!healthy) {
            DiagnosticsLog.e(TAG, "Self-test failed — refusing to report Connected.")
            throw IllegalStateException(getString(R.string.err_selftest))
        }
        beginUsageAccounting(profile)

        // Informational only: report where the tunnel actually came out.
        // WARP edges are anycast, so the exit location is decided by the
        // engine's endpoint selection and the operator's routing, not by the
        // app. Nothing here can reject or override that choice.
        val exit = AetherController.ipInfo.value?.takeIf { it.viaTunnel }
        if (exit != null) {
            DiagnosticsLog.i(
                TAG,
                "Exit verified through the tunnel: ${exit.ip} (${exit.countryCode ?: "??"})",
            )
        }
    }

    /** Keeps the engine alive; retries with backoff if it dies. */
    private suspend fun superviseEngine(profile: ConnectionProfile) {
        var attempt = 0
        while (currentScopeActive()) {
            if (engine?.isAlive() == true) {
                attempt = 0
                // 1.2.2 CPU FIX: the supervisor used to wake up every 2 s for
                // the ENTIRE lifetime of the tunnel just to ask "is the engine
                // still alive?" — 1,800 wake-ups per hour of a healthy,
                // otherwise idle connection, each one preventing the CPU from
                // settling into a deep idle state and quietly draining the
                // battery. Instead we now BLOCK on the process itself: the OS
                // wakes us the instant the engine exits and never before, so a
                // healthy tunnel costs exactly zero polling.
                engine?.awaitExit(WATCHDOG_INTERVAL_MS)
                // STABILITY WATCHDOG (1.2.4, hardened): the engine process can
                // stay alive while its session silently dies -- the classic
                // "connected, but after a minute or two no site opens"
                // symptom. Probe end-to-end THROUGH the local SOCKS5 port and
                // restart the engine only on SUSTAINED failure; see
                // probeTunnelCycle() for why the bar is deliberately high.
                if (engine?.isAlive() == true) {
                    if (probeTunnelCycle()) {
                        probeFailures = 0
                    } else if (++probeFailures >= WATCHDOG_FAIL_CYCLES) {
                        DiagnosticsLog.w(
                            TAG,
                            "Watchdog: tunnel dead across $WATCHDOG_FAIL_CYCLES consecutive checks -- restarting the engine.",
                        )
                        probeFailures = 0
                        engine?.stop()
                    }
                }
                continue
            }

            if (attempt >= maxRetries(profile)) {
                // KILL SWITCH (1.2.4): instead of tearing the VPN down and
                // leaking direct, engage the blackhole lockdown.
                if (profile.killSwitch || profile.strictKillSwitch) {
                    enterLockdown(profile)
                    return
                }
                throw IllegalStateException(getString(R.string.err_engine_died))
            }
            val backoff = BACKOFF[attempt.coerceAtMost(BACKOFF.size - 1)]
            attempt++
            AetherController.setState(ConnectionState.Reconnecting(attempt, maxRetries(profile)))
            updateNotification(getString(R.string.state_reconnecting))
            delay(backoff)

            engine = AetherProcess(applicationInfo.nativeLibraryDir, filesDir).also { it.start(profile) }
            if (PortProbe.awaitOpen(SOCKS_HOST, SOCKS_PORT, profile.connectTimeoutMs()) { engine?.isAlive() == true }) {
                // Same gate as the initial connect: never claim Connected after
                // a silent engine restart until traffic really flows again.
                AetherController.setState(ConnectionState.Verifying)
                updateNotification(getString(R.string.state_verifying))
                if (runCatching { Diagnostics.run() }.getOrDefault(false)) {
                    attempt = 0
                    beginUsageAccounting(profile)
                    AetherController.setState(ConnectionState.Connected("$SOCKS_HOST:$SOCKS_PORT"))
                    updateNotification(getString(R.string.state_connected))
                } else {
                    DiagnosticsLog.w(TAG, "Self-test failed after engine restart — retrying.")
                    engine?.stop()
                }
            }
        }
    }

    private fun currentScopeActive(): Boolean = runJob?.isActive ?: false

    private fun establishTun(profile: ConnectionProfile) {
        // User-tunable MTU (defaults to 1280 — safe for Iranian mobile/DPI).
        // Clamped to a sane range so a bad saved value can't break establish().
        val mtu = profile.mtu.coerceIn(576, 9000)
        val builder = Builder()
            .setSession("Aether")
            .setMtu(mtu)
            // The TUN address MUST match hev's tunnel.ipv4/ipv6 (see writeHevConfig).
            .addAddress(TunnelConfig.TUN_IPV4, TunnelConfig.TUN_IPV4_PREFIX)
            .addAddress(TunnelConfig.TUN_IPV6, TunnelConfig.TUN_IPV6_PREFIX)
            .addRoute("0.0.0.0", 0)

        // IPv6 LEAK PROTECTION (1.2.4): on by default -- the v6 default
        // route keeps IPv6 traffic inside the tunnel. Can be disabled for
        // networks where a default v6 route breaks connectivity.
        //
        // Also forced on when the user explicitly picked V6/BOTH in the IP
        // Version setting -- otherwise that setting only changed which
        // family the engine used to scan for an endpoint, while the device's
        // own default route stayed IPv4-only, so the exit IP shown to the
        // user never actually changed. See AetherController IPv6 bug report.
        if (profile.ipv6LeakProtection || profile.ipVersion != IpVersion.V4) {
            builder.addRoute("::", 0)
        }

        // KILL SWITCH (1.2.4): a blocking interface never falls back to
        // direct traffic while the tunnel is not forwarding.
        if (profile.killSwitch || profile.strictKillSwitch) {
            builder.setBlocking(true)
        }

        TunnelConfig.DNS_SERVERS.forEach { builder.addDnsServer(it) }

        // Split tunneling + loop prevention (keeps the engine's own traffic off
        // the TUN, equivalent to v2rayNG's in-process protect()).
        applyAppFilter(builder, profile)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        tun = builder.establish()
            ?: throw IllegalStateException("Failed to establish the VPN interface")
        DiagnosticsLog.i(
            TAG,
            "TUN established: ipv4=${TunnelConfig.TUN_IPV4}/${TunnelConfig.TUN_IPV4_PREFIX} " +
                "ipv6=${TunnelConfig.TUN_IPV6}/${TunnelConfig.TUN_IPV6_PREFIX} mtu=$mtu " +
                "split=${profile.splitMode} apps=${profile.splitApps.size} dns=${TunnelConfig.DNS_SERVERS}",
        )
    }

    /**
     * Applies the split-tunnel policy and always keeps the app's own engine
     * traffic off the TUN (loop prevention).
     *
     * - OFF     : everything routes through the VPN except our own package.
     * - INCLUDE : ONLY the chosen apps route through the VPN. Our own package is
     *             implicitly excluded because it is never added to the allow-list.
     * - EXCLUDE : everything routes through the VPN except the chosen apps + us.
     */
    private fun applyAppFilter(builder: Builder, profile: ConnectionProfile) {
        val apps = profile.splitApps.filter { it.isNotBlank() && it != packageName }
        when (profile.splitMode) {
            SplitMode.INCLUDE -> {
                if (apps.isEmpty()) {
                    // Nothing selected -> fall back to OFF so we don't build a
                    // tunnel that carries no traffic at all.
                    safeDisallow(builder, packageName)
                    return
                }
                apps.forEach { safeAllow(builder, it) }
            }
            SplitMode.EXCLUDE -> {
                safeDisallow(builder, packageName)
                // Blocked apps must stay INSIDE the TUN so the filter bridge
                // can drop their traffic; excluding them would give them
                // direct internet instead of none.
                apps.filter { it !in profile.blockedApps }.forEach { safeDisallow(builder, it) }
            }
            SplitMode.OFF -> safeDisallow(builder, packageName)
        }
    }

    private fun safeAllow(builder: Builder, pkg: String) {
        try {
            builder.addAllowedApplication(pkg)
        } catch (_: Exception) {
            DiagnosticsLog.w(TAG, "addAllowedApplication failed for $pkg (not installed?)")
        }
    }

    private fun safeDisallow(builder: Builder, pkg: String) {
        try {
            builder.addDisallowedApplication(pkg)
        } catch (_: Exception) {
            if (pkg != packageName) DiagnosticsLog.w(TAG, "addDisallowedApplication failed for $pkg")
        }
    }

    private fun startTun2Socks(profile: ConnectionProfile) {
        if (profile.blockedApps.isNotEmpty()) {
            // PER-APP BLOCKING (1.2.4): hev-socks5-tunnel cannot filter per
            // UID, so a userspace filter bridge (merged into Aether's
            // SocksTunBridge) reads the TUN itself, resolves each flow's
            // owning app and drops blocked apps' packets. It is activated
            // ONLY when blocking is configured; the battle-tested hev path
            // below stays the default for everyone else.
            val pfd = tun ?: throw IllegalStateException("TUN descriptor is null")
            val bridge = SocksTunBridge(
                vpnService = this,
                tunDescriptor = pfd,
                socksHost = SOCKS_HOST,
                socksPort = SOCKS_PORT,
                mtu = profile.mtu.coerceIn(576, 9000),
                blockedPackagesProvider = { profile.blockedApps.toSet() },
                routingEngine = RoutingEngine(emptyList()),
                usageTracker = usageTracker,
            )
            DiagnosticsLog.i(TAG, "Starting userspace filter bridge (blocked apps=${profile.blockedApps.size})")
            bridge.start()
            tunBridge = bridge
            return
        }
        val config = writeHevConfig(profile.mtu.coerceIn(576, 9000))
        // Use the LIVE fd of the ParcelFileDescriptor (do NOT detach): hev uses it
        // while running and we close the pfd ourselves on teardown. The fd is only
        // valid inside THIS process, which is exactly why hev must run in-process.
        val fd = tun?.fd ?: throw IllegalStateException("TUN descriptor is null")
        DiagnosticsLog.i(TAG, "Starting hev-socks5-tunnel in-process (fd=$fd)")
        HevTunnel.start(config.absolutePath, fd)
        tunnelStarted = true
    }

    /**
     * Writes the hev-socks5-tunnel config in the exact shape v2rayNG uses.
     *
     * The critical difference from the previous (broken) version is the
     * `tunnel.ipv4` / `tunnel.ipv6` fields. hev configures its internal lwIP
     * netif from these; without them packets are pulled off the TUN fd but have
     * nowhere to be routed, so the tunnel "connects" but no site ever loads.
     * These MUST equal the VpnService addAddress values.
     */
    private fun writeHevConfig(mtu: Int): File {
        val file = File(filesDir, "hev.yaml")
        val yaml = """
            tunnel:
              mtu: $mtu
              ipv4: ${TunnelConfig.TUN_IPV4}
              ipv6: '${TunnelConfig.TUN_IPV6}'
            socks5:
              address: $SOCKS_HOST
              port: $SOCKS_PORT
              udp: 'udp'
            misc:
              task-stack-size: 86016
              connect-timeout: 5000
              # 1.2.4 stability: the old 60s idle timeout killed long-lived
              # sessions ("works 1-2 minutes, then no site opens").
              tcp-read-write-timeout: 300000
              udp-read-write-timeout: 120000
              log-level: warn
        """.trimIndent()
        file.writeText(yaml)
        DiagnosticsLog.i(TAG, "hev.yaml written:\n$yaml")
        return file
    }

    private fun stopEverything() {
        AetherController.setState(ConnectionState.Disconnecting)
        updateNotification(getString(R.string.state_disconnecting))
        val job = runJob
        runJob = null
        // DISCONNECT MUST BE INSTANT. Order matters:
        //   1. cancel the session coroutine (does not wait for it),
        //   2. kill the natives right away — this is what actually makes the
        //      tunnel stop, and it also unblocks any wait the session
        //      coroutine is parked in,
        //   3. flip the UI to Idle and drop the foreground notification,
        //   4. only THEN join the finished coroutine, off the critical path.
        // The previous order (join → cleanup) made the button sit on
        // "Disconnecting…" for as long as the supervisor's engine wait had
        // left to run — up to a full minute.
        job?.cancel()
        stopJob = scope.launch(Dispatchers.IO) {
            cleanupNativeOnly()
            // STALE-CIRCLES FIX (part 2): clear the finished session's results
            // right at disconnect, so the panel never carries green circles
            // from a dead session into the next connect.
            Diagnostics.resetChecks()
            EngineMeta.reset()
            AetherController.setState(ConnectionState.Idle)
            AetherTileService.requestUpdate(this@AetherVpnService)
            stopForegroundCompat()
            stopSelf()
            job?.join()
        }
    }

    /** Max automatic engine restarts (Smart Reconnect, 1.2.4). */
    private fun maxRetries(profile: ConnectionProfile): Int =
        if (profile.smartReconnect) profile.reconnectRetryLimit.coerceIn(1, 50) else 50

    /**
     * WATCHDOG PROBE, hardened (1.2.4 periodic-outage root-cause fix).
     *
     * The old probe was a single TCP connect to 1.1.1.1:53 with a 5 s
     * timeout. On high-RTT, lossy links (the tunnel's own baseline RTT is
     * 350-550 ms and DPI throttling causes multi-second UDP stalls that heal
     * by themselves) that lone probe fails SPURIOUSLY -- two unlucky probes
     * 30 s apart were enough to kill a perfectly healthy engine and force a
     * full endpoint rescan, which is itself a 30-90 s total outage. The cure
     * had become the disease: the periodic "no site opens, then it works
     * again" the user saw every few minutes was the watchdog restarting a
     * tunnel that was only briefly stalled.
     *
     * A check now only counts as failed when THREE attempts in a row --
     * spread over three different anycast resolvers, 8 s timeout each, 1.5 s
     * apart -- all fail, and the engine is restarted only after THREE
     * consecutive failed checks (90 s+ of continuously proven dead tunnel).
     * Brief self-healing stalls no longer trigger restarts, a genuinely dead
     * session still recovers automatically, and MASQUE's in-engine reconnect
     * loop gets room to finish before the app steps in.
     */
    private suspend fun probeTunnelCycle(): Boolean {
        repeat(PROBE_ATTEMPTS) { attempt ->
            if (probeTunnelOnce(PROBE_TARGETS[attempt % PROBE_TARGETS.size])) return true
            if (attempt < PROBE_ATTEMPTS - 1) delay(PROBE_RETRY_GAP_MS)
        }
        return false
    }

    /** Single TCP connect to [target] ("host:port") THROUGH the engine's local SOCKS5 listener. */
    private fun probeTunnelOnce(target: String): Boolean = runCatching {
        val proxy = java.net.Proxy(
            java.net.Proxy.Type.SOCKS,
            java.net.InetSocketAddress(SOCKS_HOST, SOCKS_PORT),
        )
        java.net.Socket(proxy).use {
            it.connect(
                java.net.InetSocketAddress(target.substringBefore(':'), target.substringAfter(':').toInt()),
                PROBE_TIMEOUT_MS,
            )
        }
        true
    }.getOrDefault(false)

    /**
     * KILL SWITCH lockdown (1.2.4): stop the engine and the forwarder but
     * KEEP a blocking full-tunnel TUN up, so every packet is blackholed
     * instead of leaking direct. The service stays foreground; connecting
     * again or disconnecting lifts the lockdown.
     */
    private fun enterLockdown(profile: ConnectionProfile) {
        val job = runJob
        runJob = null
        job?.cancel()
        stopJob = scope.launch(Dispatchers.IO) {
            cleanupForwardingOnly()
            ensureLockdownTun(profile)
            lockdownTunActive = true
            Diagnostics.resetChecks()
            EngineMeta.reset()
            AetherController.setState(ConnectionState.Error(getString(R.string.state_killswitch)))
            updateNotification(getString(R.string.state_killswitch))
            AetherTileService.requestUpdate(this@AetherVpnService)
            job?.join()
        }
    }

    /** Stops sharing, the forwarder and the engine but deliberately KEEPS [tun]. */
    private fun cleanupForwardingOnly() {
        try {
            ShareBridge.stop()
        } catch (_: Throwable) {
        }
        tunBridge?.let { runCatching { it.stop() } }
        tunBridge = null
        if (tunnelStarted) {
            try {
                HevTunnel.stop()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        stopUsageAccounting()
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        engine = null
    }

    /** (Re)builds the TUN as a full-tunnel blackhole: routes everything, reads nothing. */
    private fun ensureLockdownTun(profile: ConnectionProfile) {
        runCatching { tun?.close() }
        tun = null
        val builder = Builder()
            .setSession("Aether KillSwitch")
            .setMtu(profile.mtu.coerceIn(576, 9000))
            .addAddress(TunnelConfig.TUN_IPV4, TunnelConfig.TUN_IPV4_PREFIX)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)
        if (profile.ipv6LeakProtection || profile.ipVersion != IpVersion.V4) {
            builder.addAddress(TunnelConfig.TUN_IPV6, TunnelConfig.TUN_IPV6_PREFIX)
            builder.addRoute("::", 0)
        }
        tun = runCatching { builder.establish() }.getOrNull()
    }

    private fun cleanupNativeOnly() {
        // Stop sharing first: without the tunnel the bridge would leak direct.
        try {
            ShareBridge.stop()
        } catch (_: Throwable) {
        }
        tunBridge?.let { runCatching { it.stop() } }
        tunBridge = null
        if (tunnelStarted) {
            try {
                HevTunnel.stop()
            } catch (_: Throwable) {
            }
            tunnelStarted = false
        }
        stopUsageAccounting()
        try {
            engine?.stop()
        } catch (_: Throwable) {
        }
        engine = null
        try {
            tun?.close()
        } catch (_: Throwable) {
        }
        tun = null
    }

    private fun beginUsageAccounting(profile: ConnectionProfile) {
        val source = when {
            profile.proxyMode -> if (profile.lanShare) TunnelUsageSource.LAN_SHARE else TunnelUsageSource.LOCAL_PROXY
            else -> TunnelUsageSource.VPN_TUN
        }
        usageTracker.start(source)
        if (profile.proxyMode || profile.blockedApps.isNotEmpty()) return
        hevUsageJob?.cancel()
        hevUsageJob = scope.launch(Dispatchers.IO) {
            var lastUpload = 0L
            var lastDownload = 0L
            while (currentScopeActive() && HevTunnel.isAlive()) {
                HevTunnel.traffic()?.let { traffic ->
                    val uploadDelta = (traffic.uploadBytes - lastUpload).coerceAtLeast(0L)
                    val downloadDelta = (traffic.downloadBytes - lastDownload).coerceAtLeast(0L)
                    usageTracker.add(uploadDelta, downloadDelta)
                    lastUpload = traffic.uploadBytes
                    lastDownload = traffic.downloadBytes
                }
                delay(HEV_USAGE_SAMPLE_MS)
            }
            usageTracker.flush()
        }
    }

    private fun stopUsageAccounting() {
        hevUsageJob?.cancel()
        hevUsageJob = null
        usageTracker.end()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        runJob?.cancel()
        cleanupNativeOnly()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        usageTracker.flush()
        super.onTrimMemory(level)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AetherApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.state_disconnecting), disconnectIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
        // Keep the Quick Settings tile in sync with every state transition.
        AetherTileService.requestUpdate(this)
        // Keep the home-screen widgets (simple + large) in sync too.
        // Cheap: returns immediately when no widget is placed.
        AetherWidgetProvider.updateAllWidgets(this)
        AetherWidgetLargeProvider.updateAllWidgets(this)
    }

    companion object {
        const val ACTION_CONNECT = "com.najishab.aether.CONNECT"
        const val ACTION_DISCONNECT = "com.najishab.aether.DISCONNECT"
        const val EXTRA_PROFILE = "profile"

        private const val NOTIF_ID = 0x4145
        private const val TAG = "vpn"
        private const val SOCKS_HOST = TunnelConfig.SOCKS_HOST
        private const val SOCKS_PORT = TunnelConfig.SOCKS_PORT
        private const val MTU = TunnelConfig.MTU
        private const val MAX_RETRIES = 3
        private val BACKOFF = longArrayOf(2000L, 5000L, 10000L)

        /**
         * Upper bound for one blocking wait on the engine process (1.2.2).
         * The supervisor no longer polls; it parks on the process itself and
         * only wakes up this often to re-check its own cancellation state.
         */
        private const val SUPERVISOR_WAIT_MS = 60_000L

        /** Watchdog probe cadence while the tunnel is up (1.2.4). */
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val HEV_USAGE_SAMPLE_MS = 5_000L

        /**
         * Consecutive failed checks before the engine is restarted (1.2.4
         * hardening): three failed checks = 90 s+ of proven dead tunnel, so
         * only a genuinely dead session is restarted.
         */
        private const val WATCHDOG_FAIL_CYCLES = 3

        /**
         * Attempts per watchdog check, rotating over anycast resolvers so one
         * blocked or slow target can never fake a dead tunnel (1.2.4 fix).
         */
        private const val PROBE_ATTEMPTS = 3
        private val PROBE_TARGETS = arrayOf("1.1.1.1:53", "1.0.0.1:53", "9.9.9.9:53")
        private const val PROBE_TIMEOUT_MS = 8_000
        private const val PROBE_RETRY_GAP_MS = 1_500L

        /**
         * How long to wait for the previous engine to release the local SOCKS5
         * port before starting a new one (1.2.2 protocol-switch fix).
         */
        private const val PORT_RELEASE_WAIT_MS = 3_000L

        /**
         * Cap for the FIRST attempt of a hand-picked protocol, so a throttled
         * network cannot hold the user on "Connecting" for the whole scan
         * budget before the hardened second pass is even tried.
         */
        private const val FIRST_PASS_MAX_MS = 75_000L
    }
}
