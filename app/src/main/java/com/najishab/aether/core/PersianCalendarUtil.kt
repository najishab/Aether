package com.najishab.aether.core

/**
 * Solar Hijri (Jalali/Shamsi) <-> Gregorian date conversion.
 * Standard public-domain algorithm, valid for the modern era used by this app.
 */
object PersianCalendarUtil {

    private val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    private fun isGregorianLeap(gy: Int): Boolean =
        (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0

    /** Returns [jalaliYear, jalaliMonth(1-12), jalaliDay]. */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        var jy: Int
        val gy2: Int
        if (gy <= 1600) {
            jy = 0
            gy2 = gy - 621
        } else {
            jy = 979
            gy2 = gy - 1600
        }
        val gy3 = if (gm > 2) gy2 + 1 else gy2
        var days = 365 * gy2 + (gy3 + 3) / 4 - (gy3 + 99) / 100 + (gy3 + 399) / 400 -
            80 + gd + gDaysInMonth[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    /** Returns [gregorianYear, gregorianMonth(1-12), gregorianDay]. */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668 + 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val salA = intArrayOf(0, 31, if (isGregorianLeap(gy)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    /** Number of days in the given Jalali month (1-12) for the given Jalali year. */
    fun jalaliMonthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isJalaliLeap(jy)) 30 else 29
    }

    private fun isJalaliLeap(jy: Int): Boolean {
        val jy2 = 474 + ((jy - 474) % 2820 + 2820) % 2820
        return (((jy2 + 38) * 682) % 2816) < 682
    }
}
