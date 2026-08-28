package com.najishab.aether.core

import android.net.TrafficStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.najishab.aether.model.isConnected

/**
 * App-wide, lifecycle-independent traffic sampler for the Live Graph screen.
 *
 * Sampling runs continuously from [AetherApp]'s process-wide scope (started
 * once via [start]) rather than from the Activity, so the speed history
 * keeps accumulating whether or not the Live Graph screen is on screen -
 * opening it just attaches to the numbers that were already running instead
 * of resetting them to zero.
 *
 * [sessionBytes] tracks total bytes moved since the current VPN connection
 * began, and resets to zero the moment a NEW connection starts - so it
 * freezes at the finished total after a disconnect and shows "usage during
 * the last connection" until the next connect begins.
 *
 * As with [com.najishab.aether.data.UsageStore], samples come from
 * [TrafficStats]'s device-wide counters (the engine runs as a separate
 * native process outside the app's own UID), so this reflects whole-device
 * throughput while connected, not a strictly per-tunnel figure.
 */
object TrafficMonitor {
    const val HISTORY_SIZE = 60
    private const val SAMPLE_INTERVAL_MS = 1000L

    private val _downHistory = MutableStateFlow(List(HISTORY_SIZE) { 0f })
    val downHistory: StateFlow<List<Float>> = _downHistory.asStateFlow()

    private val _upHistory = MutableStateFlow(List(HISTORY_SIZE) { 0f })
    val upHistory: StateFlow<List<Float>> = _upHistory.asStateFlow()

    private val _downSpeedBps = MutableStateFlow(0L)
    val downSpeedBps: StateFlow<Long> = _downSpeedBps.asStateFlow()

    private val _upSpeedBps = MutableStateFlow(0L)
    val upSpeedBps: StateFlow<Long> = _upSpeedBps.asStateFlow()

    /** Bytes moved since the current (or most recently finished) connection started. */
    private val _sessionBytes = MutableStateFlow(0L)
    val sessionBytes: StateFlow<Long> = _sessionBytes.asStateFlow()

    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            var lastRx = TrafficStats.getTotalRxBytes()
            var lastTx = TrafficStats.getTotalTxBytes()
            var lastTime = System.currentTimeMillis()
            var wasConnected = false

            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val now = System.currentTimeMillis()
                val elapsedSec = ((now - lastTime).coerceAtLeast(1)) / 1000f

                val deltaRx = if (rx >= lastRx) rx - lastRx else 0L
                val deltaTx = if (tx >= lastTx) tx - lastTx else 0L
                val downBps = (deltaRx / elapsedSec).toLong()
                val upBps = (deltaTx / elapsedSec).toLong()

                _downSpeedBps.value = downBps
                _upSpeedBps.value = upBps
                _downHistory.value = _downHistory.value.drop(1) + downBps.toFloat()
                _upHistory.value = _upHistory.value.drop(1) + upBps.toFloat()

                val isConnected = AetherController.state.value.isConnected
                if (isConnected && !wasConnected) {
                    // A new connection just started - begin a fresh session total.
                    _sessionBytes.value = 0L
                }
                if (isConnected) {
                    _sessionBytes.value += deltaRx + deltaTx
                }
                wasConnected = isConnected

                lastRx = rx
                lastTx = tx
                lastTime = now
            }
        }
    }
}
