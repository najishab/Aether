package com.najishab.aether.core

/**
 * One in-app announcement entry (see AnnouncementClient / announcements.json
 * in the repo). Shown as a dismissible in-app card (AnnouncementBanner) -
 * never a system notification, since the VPN's own foreground notification
 * is already permanent and a second one would just add noise.
 */
data class Announcement(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val textEn: String,
    val textFa: String,
    val url: String?,
    /** Inclusive versionName bounds this entry applies to, e.g. "1.2.6". Null = no bound. */
    val minVersion: String?,
    val maxVersion: String?,
)
