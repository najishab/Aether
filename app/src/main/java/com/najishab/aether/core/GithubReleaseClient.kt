package com.najishab.aether.core

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** The bits of a GitHub Releases API response the Changelog screen needs. */
data class GithubRelease(
    val tagName: String,
    val body: String?,
    val htmlUrl: String?,
)

/**
 * Minimal client for GitHub's public Releases API - no JSON library, in
 * keeping with the rest of the engine-facing code (see NetProbe.kt), just a
 * handful of fields out of a small, well-known response shape.
 */
object GithubReleaseClient {

    /** Fetches the latest published release of [repoSlug] ("owner/name"), or null on any failure. */
    fun fetchLatestRelease(repoSlug: String, timeoutMs: Int = 8000): GithubRelease? = runCatching {
        val url = URL("https://api.github.com/repos/$repoSlug/releases/latest")
        val conn = url.openConnection() as HttpsURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Aether-Android")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tagName = extractJsonString(body, "tag_name") ?: return null
            val notes = extractJsonString(body, "body")
            val htmlUrl = extractJsonString(body, "html_url")
            GithubRelease(tagName = tagName, body = notes, htmlUrl = htmlUrl)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * True if [remoteTag] (e.g. "v1.3.0" or "1.3.0") is a newer version than
     * [localVersion] (e.g. "1.2.6"). Compares numeric dot components;
     * anything that doesn't parse falls back to "not newer" (never nags the
     * user about a release it can't actually compare).
     */
    fun isNewer(remoteTag: String, localVersion: String): Boolean {
        val remote = remoteTag.removePrefix("v").removePrefix("V")
            .split(".").mapNotNull { it.trim().toIntOrNull() }
        val local = localVersion.removePrefix("v").removePrefix("V")
            .split(".").mapNotNull { it.trim().toIntOrNull() }
        if (remote.isEmpty() || local.isEmpty()) return false
        val len = maxOf(remote.size, local.size)
        for (i in 0 until len) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /**
     * Extracts the string value of a top-level JSON field by name, honouring
     * JSON string escapes (\n, \", \\, \t, \r, \uXXXX) - handwritten instead
     * of pulling in a JSON dependency, matching the rest of the codebase.
     */
    private fun extractJsonString(json: String, field: String): String? {
        val key = "\"$field\":\""
        val start = json.indexOf(key)
        if (start == -1) return null
        var i = start + key.length
        val out = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            when {
                c == '\\' && i + 1 < json.length -> {
                    when (val next = json[i + 1]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> {}
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'u' -> {
                            val hex = json.substring(i + 2, minOf(i + 6, json.length))
                            hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                            i += 4
                        }
                        else -> out.append(next)
                    }
                    i += 2
                }
                c == '"' -> return out.toString()
                else -> {
                    out.append(c)
                    i += 1
                }
            }
        }
        return null
    }
}
