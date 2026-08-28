package com.najishab.aether.core

import android.content.Context
import com.najishab.aether.BuildConfig
import com.najishab.aether.data.AnnouncementStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app announcements. A small JSON array (see docs/announcements.json in
 * the repo, served raw from GitHub via BuildConfig.ANNOUNCEMENTS_URL) is
 * polled periodically. The first entry that (a) this build's versionName
 * satisfies the min/maxVersion bounds of, and (b) hasn't been seen yet
 * (AnnouncementStore, keyed by the entry's own "id") is surfaced through
 * [current] for AnnouncementBanner to render as a dismissible in-app card.
 *
 * Deliberately NOT a system notification: the VPN's own foreground
 * notification is already permanent, and stacking a second one on top of it
 * is exactly the noise this was meant to avoid - see AetherApp's channel.
 */
object AnnouncementManager {
    private val _current = MutableStateFlow<Announcement?>(null)
    val current: StateFlow<Announcement?> = _current.asStateFlow()

    private var store: AnnouncementStore? = null
    private var scope: CoroutineScope? = null

    /** Starts the periodic background poll. Call once, from AetherApp.onCreate. */
    fun start(context: Context, appScope: CoroutineScope) {
        if (scope != null) return // already started
        store = AnnouncementStore(context.applicationContext)
        scope = appScope
        appScope.launch {
            while (true) {
                runCatching { checkNow() }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /** Fetches now and updates [current] if a new, applicable, unseen entry exists. */
    suspend fun checkNow() = withContext(Dispatchers.IO) {
        val list = AnnouncementClient.fetch(BuildConfig.ANNOUNCEMENTS_URL)
        if (list.isEmpty()) return@withContext
        val seen = store?.seenIds() ?: emptySet()
        val version = BuildConfig.VERSION_NAME
        val next = list.firstOrNull { entry ->
            entry.id !in seen &&
                (entry.minVersion == null || versionCompare(version, entry.minVersion) >= 0) &&
                (entry.maxVersion == null || versionCompare(version, entry.maxVersion) <= 0)
        }
        if (next != null) _current.value = next
    }

    /** Marks the current announcement as seen (so it never shows again) and clears it. */
    fun dismiss() {
        val ann = _current.value ?: return
        _current.value = null
        val s = store ?: return
        (scope ?: return).launch(Dispatchers.IO) { s.markSeen(ann.id) }
    }

    /** Same dot-version compare GithubReleaseClient.isNewer uses, exposed as a full comparator. */
    private fun versionCompare(a: String, b: String): Int {
        val pa = a.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.trim().toIntOrNull() }
        val pb = b.removePrefix("v").removePrefix("V").split(".").mapNotNull { it.trim().toIntOrNull() }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6h
}
