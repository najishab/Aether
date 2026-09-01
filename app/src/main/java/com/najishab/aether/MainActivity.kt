package com.najishab.aether

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.najishab.aether.core.AetherController
import com.najishab.aether.core.IpEndpoint
import com.najishab.aether.core.NetProbe
import com.najishab.aether.core.TunnelConfig
import com.najishab.aether.data.BatteryOptStore
import com.najishab.aether.data.OnboardingStore
import com.najishab.aether.data.ProfileStore
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.ConnectionState
import com.najishab.aether.model.isBusy
import com.najishab.aether.model.isConnected
import com.najishab.aether.ui.HomeScreen
import com.najishab.aether.ui.OnboardingScreen
import com.najishab.aether.ui.components.BatteryOptimizationDialog
import com.najishab.aether.ui.components.batteryOptimizationIntent
import com.najishab.aether.ui.components.isIgnoringBatteryOptimizations
import com.najishab.aether.ui.theme.AetherTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var profileStore: ProfileStore

    /** Feature merge: first-run onboarding gate. */
    private lateinit var onboardingStore: OnboardingStore

    private lateinit var themeStore: ThemeStore

    /** Battery optimization exemption prompt, asked once after first successful connect. */
    private lateinit var batteryOptStore: BatteryOptStore

    // Holds the profile to connect with once VPN consent is granted.
    private var pendingProfile: ConnectionProfile? = null

    // ------------------------------------------------------------------
    // SCRAMBLED-INPUT FIX (root cause): the UI used to render the settings —
    // including the ip:port / CIDR text fields — straight from the DataStore
    // flow while every keystroke was saved asynchronously. Fast typing raced
    // that disk round-trip: a keystroke was applied on top of a STALE value
    // that echoed back a moment later, so digits were dropped/reordered
    // ("127.0.0.1" -> "27.0.0.11") in EVERY locale, English and Persian alike.
    //
    // Fix: the UI owns a synchronous in-memory profile state updated
    // immediately on every change. DataStore is demoted to plain background
    // persistence: a single collector writes the LATEST snapshot (conflated),
    // so saves can never interleave or feed stale values back into the UI.
    // ------------------------------------------------------------------
    private val uiProfile = MutableStateFlow<ConnectionProfile?>(null)
    private val profileSaves = MutableSharedFlow<ConnectionProfile>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingProfile?.let { AetherController.connect(this, it) }
            }
            pendingProfile = null
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        profileStore = ProfileStore(applicationContext)
        onboardingStore = OnboardingStore(applicationContext)
        themeStore = ThemeStore(applicationContext)
        batteryOptStore = BatteryOptStore(applicationContext)

        // Load the persisted profile ONCE as the initial UI state; from then
        // on the in-memory state is the single source of truth for the UI.
        // compareAndSet: if the user already changed something before the
        // initial load finished, never overwrite their edit.
        lifecycleScope.launch {
            uiProfile.compareAndSet(null, profileStore.profile.first())
        }
        // Single background writer persisting the latest profile snapshot.
        lifecycleScope.launch {
            profileSaves.conflate().collect { snapshot -> profileStore.save(snapshot) }
        }

        maybeRequestNotificationPermission()

        // Feature merge: a previous run died with an uncaught JVM
        // exception — open the saved crash report once so the user can see and
        // copy it. The report file deletes itself on dismiss.
        if (savedInstanceState == null && File(filesDir, AetherApp.CRASH_FILE).exists()) {
            startActivity(Intent(this, CrashReportActivity::class.java))
        }

        // Launched from the Quick Settings tile while VPN consent was still
        // missing: run the normal connect flow, which shows the system's VPN
        // consent dialog and then connects.
        if (intent?.getBooleanExtra(EXTRA_CONNECT_ON_LAUNCH, false) == true) {
            intent.removeExtra(EXTRA_CONNECT_ON_LAUNCH)
            lifecycleScope.launch {
                val current = AetherController.state.value
                if (!current.isConnected && !current.isBusy) {
                    toggleConnection(current)
                }
            }
        }

        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            AetherTheme(themeMode = themeMode) {
                // Feature merge: first-run onboarding gate. initial =
                // true so upgrading users never see a flash of the pager; a
                // fresh install flips to the pager as soon as the (fast)
                // DataStore read lands.
                val onboardingDone by onboardingStore.completed.collectAsState(initial = true)
                val state by AetherController.state.collectAsState()
                // Synchronous UI profile state (see uiProfile above); null
                // only until the one-time initial load completes.
                val profile by uiProfile.collectAsState()
                val connectedSince by AetherController.connectedSince.collectAsState()
                val ipInfo by AetherController.ipInfo.collectAsState()
                val ipLoading by AetherController.ipLoading.collectAsState()

                // Battery optimization exemption: asked once, the first time
                // the user reaches a successful connection (see phase-keyed
                // LaunchedEffect below). Re-openable anytime via Advanced
                // settings, so "asked" only gates the automatic first prompt.
                val batteryOptAsked by batteryOptStore.asked.collectAsState(initial = true)
                var showBatteryOptDialog by remember { mutableStateOf(false) }
                val batteryOptContext = LocalContext.current

                // Refresh the shown IP whenever the connection phase flips:
                //  - connected  -> exit server IP (through the SOCKS proxy) + flag
                //  - idle       -> the user's real operator IP (direct)
                // NOTE: any resting, non-busy state (Idle OR Error/failed
                // connect) must show the real IP — previously Error fell into
                // "busy" and the operator IP was never fetched after a failure.
                val phase = when {
                    state.isConnected -> "connected"
                    state.isBusy -> "busy"
                    else -> "idle"
                }
                LaunchedEffect(phase) {
                    when (phase) {
                        "connected" -> {
                            // FLAG-FLICKER FIX: the automatic self-test
                            // (Diagnostics) is the single owner of the exit-IP
                            // lookup. This block used to fire its OWN parallel
                            // lookup; whichever finished last overwrote the
                            // badge, and because geo providers can disagree
                            // about the exit country, the flag flickered or
                            // suddenly changed. Now we only WAIT for the
                            // self-test's result and fetch ourselves purely as
                            // a last-resort fallback (guarded by
                            // offerTunnelIpInfo, so it can never overwrite).
                            AetherController.setIpLoading(true)
                            // 1.2.2 CPU FIX: this used to busy-poll a StateFlow
                            // every 250 ms for up to 100 s — as many as 400
                            // pointless wake-ups on the UI dispatcher right
                            // after connecting, exactly when the device is
                            // already busy. StateFlow is observable, so we now
                            // SUSPEND until the value we are waiting for
                            // actually arrives (zero wake-ups in between) and
                            // simply bound that wait with a timeout.
                            withTimeoutOrNull(100_000L) {
                                AetherController.ipInfo.first { it?.viaTunnel == true }
                            }
                            if (AetherController.ipInfo.value?.viaTunnel != true) {
                                val info = withContext(Dispatchers.IO) {
                                    NetProbe.fetchIpInfoViaSocksWithRetry(
                                        TunnelConfig.SOCKS_HOST,
                                        TunnelConfig.SOCKS_PORT,
                                    )
                                }
                                if (info != null) {
                                    AetherController.offerTunnelIpInfo(
                                        IpEndpoint(info.ip, info.countryCode, true),
                                    )
                                }
                            }
                            AetherController.setIpLoading(false)
                        }
                        "idle" -> {
                            AetherController.setIpInfo(null)
                            AetherController.setIpLoading(true)
                            val info = withContext(Dispatchers.IO) { NetProbe.fetchIpInfoDirectWithRetry() }
                            AetherController.setIpInfo(info?.let { IpEndpoint(it.ip, it.countryCode, false) })
                            AetherController.setIpLoading(false)
                        }
                        else -> {
                            AetherController.setIpInfo(null)
                            AetherController.setIpLoading(false)
                        }
                    }
                }

                // First successful connection this run + never asked before +
                // not already exempted -> show the explainer dialog once.
                // Marked "asked" immediately so it can never fire twice, even
                // if the user backgrounds the app before answering.
                LaunchedEffect(phase, batteryOptAsked) {
                    if (phase == "connected" &&
                        !batteryOptAsked &&
                        !isIgnoringBatteryOptimizations(batteryOptContext)
                    ) {
                        showBatteryOptDialog = true
                        batteryOptStore.markAsked()
                    }
                }

                if (showBatteryOptDialog) {
                    BatteryOptimizationDialog(
                        onAllow = {
                            showBatteryOptDialog = false
                            batteryOptContext.startActivity(batteryOptimizationIntent(batteryOptContext))
                        },
                        onDismiss = { showBatteryOptDialog = false },
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!onboardingDone) {
                        OnboardingScreen(
                            onFinished = {
                                lifecycleScope.launch { onboardingStore.markCompleted() }
                            },
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            profile = profile ?: ConnectionProfile(),
                            connectedSince = connectedSince,
                            ipInfo = ipInfo,
                            ipLoading = ipLoading,
                            onProfileChange = { updated ->
                                // Update the UI synchronously — keystrokes must
                                // never wait for disk I/O — then persist in the
                                // background.
                                uiProfile.value = updated
                                profileSaves.tryEmit(updated)
                            },
                            onToggleConnection = { toggleConnection(state) },
                            themeMode = themeMode,
                            onThemeModeChange = { newMode ->
                                lifecycleScope.launch { themeStore.setMode(newMode) }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun toggleConnection(state: ConnectionState) {
        if (state.isConnected || state.isBusy) {
            AetherController.disconnect(this)
            return
        }
        lifecycleScope.launch {
            val profile = uiProfile.value ?: profileStore.profile.first()
            val consent = AetherController.prepare(this@MainActivity)
            if (consent != null) {
                pendingProfile = profile
                vpnPermissionLauncher.launch(consent)
            } else {
                AetherController.connect(this@MainActivity, profile)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Set by the Quick Settings tile when it needs the consent dialog. */
        const val EXTRA_CONNECT_ON_LAUNCH = "com.najishab.aether.CONNECT_ON_LAUNCH"
    }
}
