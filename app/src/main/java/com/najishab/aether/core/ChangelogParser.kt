package com.najishab.aether.core

/**
 * Parses the release-notes markdown convention used by this repo: an
 * English section, then `<div dir="rtl"> ... </div>` wrapping the Persian
 * translation of the same notes. Both the bundled asset (current version)
 * and a fetched GitHub release body (a newer version) follow this format.
 */
object ChangelogParser {

    /**
     * Removes HTML tags and image badge links from the text.
     */
    private fun cleanHtmlAndBadges(text: String): String {
        return text
            // حذف کامل تگ‌های تصویر و لینک‌های مربوط به Shield Badgeها
            .replace(Regex("\\[!\\[.*?]\\(.*?\\)]\\(.*?\\)"), "")
            // حذف تمام تگ‌های HTML مانند <div>, </div>, <img>, <a> و...
            .replace(Regex("<[^>]*>"), "")
            .trim()
    }

    /** English text, Persian text (or null if no rtl div was found). */
    fun splitByLocale(raw: String): Pair<String, String?> {
        val marker = "<div dir=\"rtl\">"
        val idx = raw.indexOf(marker)

        val enRaw: String
        val faRaw: String?

        if (idx == -1) {
            enRaw = raw
            faRaw = null
        } else {
            enRaw = raw.substring(0, idx)
            faRaw = raw.substring(idx + marker.length)
        }

        val en = cleanHtmlAndBadges(enRaw)
        val fa = faRaw?.let { cleanHtmlAndBadges(it) }

        return en to fa
    }

    /** A single renderable line: a heading, a bullet, or plain text. */
    sealed class Line {
        data class Heading(val text: String) : Line()
        data class Bullet(val text: String) : Line()
        data class Plain(val text: String) : Line()
    }

    /** Splits one locale's section into renderable lines, blank lines dropped. */
    fun toLines(section: String): List<Line> =
        section.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.startsWith("## ") -> Line.Heading(line.removePrefix("## ").trim())
                    line.startsWith("# ") -> Line.Heading(line.removePrefix("# ").trim())
                    line.startsWith("- ") -> Line.Bullet(line.removePrefix("- ").trim())
                    else -> Line.Plain(line)
                }
            }
}