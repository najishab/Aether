package com.najishab.aether.core

import java.util.Locale

/**
 * Shared byte/rate/duration formatting, factored out of [ui.components.ConnectionCard]
 * (phase 2 of the widget roadmap) so both the Compose UI and the RemoteViews
 * widgets render identical strings from one place instead of duplicating the
 * logic.
 */
object Formatters {
    fun formatBytes(v: Long): String {
        if (v < 1024L) return "$v B"
        val kb = v / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun formatRate(v: Long): String = formatBytes(v) + "/s"

    /** HH:MM:SS from a duration in seconds. */
    fun formatDuration(elapsedSec: Long): String = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        elapsedSec / 3600,
        (elapsedSec % 3600) / 60,
        elapsedSec % 60,
    )
}
