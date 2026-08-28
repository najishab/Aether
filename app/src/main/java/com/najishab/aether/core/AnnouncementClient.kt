package com.najishab.aether.core

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches and parses announcements.json (see repo docs/announcements.json)
 * from a raw GitHub URL. Uses org.json (built into Android, no dependency
 * added) since the payload is a real JSON array, unlike the single-field
 * scrape GithubReleaseClient does against the Releases API.
 */
object AnnouncementClient {

    fun fetch(url: String, timeoutMs: Int = 8000): List<Announcement> = runCatching {
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "Aether-Android")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(emptyList())

    private fun parse(json: String): List<Announcement> {
        val arr = JSONArray(json)
        val out = mutableListOf<Announcement>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val title = o.optJSONObject("title")
            val text = o.optJSONObject("text")
            out += Announcement(
                id = id,
                titleEn = title?.optString("en").orEmpty(),
                titleFa = title?.optString("fa").orEmpty(),
                textEn = text?.optString("en").orEmpty(),
                textFa = text?.optString("fa").orEmpty(),
                url = o.optString("url").takeIf { it.isNotBlank() },
                minVersion = o.optString("minVersion").takeIf { it.isNotBlank() },
                maxVersion = o.optString("maxVersion").takeIf { it.isNotBlank() },
            )
        }
        return out
    }
}
