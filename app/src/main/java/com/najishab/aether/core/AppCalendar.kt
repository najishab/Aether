package com.najishab.aether.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Locale-aware week/month range helpers used by the usage calendar.
 * Persian (fa): week is Saturday..Friday, month follows the Jalali (Shamsi) calendar.
 * Other locales: week follows the locale's Gregorian first-day-of-week rule,
 * month follows the Gregorian calendar.
 * Ranges are returned as inclusive "yyyy-MM-dd" Gregorian date-key pairs, matching
 * UsageStore's DailyUsage.dateKey format, so they can be compared/filtered directly.
 */
object AppCalendar {

    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    private val jalaliMonthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun isPersian(locale: Locale): Boolean = locale.language == "fa"

    fun thisWeekRange(locale: Locale, now: Calendar = Calendar.getInstance()): Pair<String, String> {
        val cal = now.clone() as Calendar
        val firstDow = if (isPersian(locale)) Calendar.SATURDAY else Calendar.getInstance(locale).firstDayOfWeek
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        var back = currentDow - firstDow
        if (back < 0) back += 7
        cal.add(Calendar.DAY_OF_YEAR, -back)
        val start = keyFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val end = keyFormat.format(cal.time)
        return start to end
    }

    fun thisMonthRange(locale: Locale, now: Calendar = Calendar.getInstance()): Pair<String, String> {
        if (isPersian(locale)) {
            val j = PersianCalendarUtil.gregorianToJalali(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            )
            val jy = j[0]
            val jm = j[1]
            val startG = PersianCalendarUtil.jalaliToGregorian(jy, jm, 1)
            val endG = PersianCalendarUtil.jalaliToGregorian(jy, jm, PersianCalendarUtil.jalaliMonthLength(jy, jm))
            return gregorianKey(startG) to gregorianKey(endG)
        }
        val cal = now.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = keyFormat.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = keyFormat.format(cal.time)
        return start to end
    }

    fun formatDayLabel(dateKey: String, locale: Locale): String = runCatching {
        val parsed = keyFormat.parse(dateKey)!!
        if (isPersian(locale)) {
            val cal = Calendar.getInstance().apply { time = parsed }
            val j = PersianCalendarUtil.gregorianToJalali(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            )
            "${j[2]} ${jalaliMonthNames[j[1] - 1]}"
        } else {
            SimpleDateFormat("MMM d", locale).format(parsed)
        }
    }.getOrDefault(dateKey)

    private fun gregorianKey(ymd: IntArray): String =
        String.format(Locale.US, "%04d-%02d-%02d", ymd[0], ymd[1], ymd[2])
}
