package com.najishab.aether.core

import android.content.Context
import com.najishab.aether.data.TunnelUsageSource
import com.najishab.aether.data.UsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class TunnelUsageTracker(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = UsageStore(context.applicationContext)
    private val uploadPending = AtomicLong(0L)
    private val downloadPending = AtomicLong(0L)

    @Volatile
    private var activeSessionId: Long? = null
    @Volatile
    private var activeSource: TunnelUsageSource? = null
    @Volatile
    private var pausedSessionId: Long? = null
    @Volatile
    private var pausedSource: TunnelUsageSource? = null
    @Volatile
    private var lastEndedAtMs: Long = 0L

    private var flushJob: Job? = null

    fun start(source: TunnelUsageSource) {
        val now = System.currentTimeMillis()
        val previous = activeSessionId
        if (previous != null && activeSource == source) return

        if (previous != null) {
            flush()
            end()
            return start(source)
        }

        scope.launch(Dispatchers.IO) {
            val resumeId = pausedSessionId
            val resume = resumeId != null && pausedSource == source && now - lastEndedAtMs < RECONNECT_MERGE_MS
            if (resume) {
                activeSessionId = resumeId
                activeSource = source
                pausedSessionId = null
                pausedSource = null
            } else {
                activeSessionId = store.startSession(source, now)
                activeSource = source
            }
            ensurePeriodicFlush()
        }
    }

    fun addUpload(bytes: Long) {
        if (activeSessionId == null) return
        if (bytes > 0L) uploadPending.addAndGet(bytes)
    }

    fun addDownload(bytes: Long) {
        if (activeSessionId == null) return
        if (bytes > 0L) downloadPending.addAndGet(bytes)
    }

    fun add(uploadBytes: Long, downloadBytes: Long) {
        addUpload(uploadBytes)
        addDownload(downloadBytes)
    }

    fun flush() {
        val sessionId = activeSessionId ?: return
        val upload = uploadPending.getAndSet(0L)
        val download = downloadPending.getAndSet(0L)
        if (upload <= 0L && download <= 0L) return
        scope.launch(Dispatchers.IO) {
            runCatching { store.addBytes(sessionId, upload, download) }
        }
    }

    fun end() {
        val sessionId = activeSessionId ?: return
        val source = activeSource
        flush()
        activeSessionId = null
        activeSource = null
        pausedSessionId = sessionId
        pausedSource = source
        lastEndedAtMs = System.currentTimeMillis()
        flushJob?.cancel()
        flushJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { store.endSession(sessionId) }
        }
    }

    private fun ensurePeriodicFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            while (activeSessionId != null) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    companion object {
        const val RECONNECT_MERGE_MS = 5_000L
        private const val FLUSH_INTERVAL_MS = 7_000L
    }
}
