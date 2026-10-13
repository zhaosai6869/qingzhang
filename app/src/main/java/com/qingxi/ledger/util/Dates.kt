package com.qingxi.ledger.util

import java.util.Calendar
import java.util.Locale

const val MILLIS_PER_DAY = 86_400_000L

object Dates {

    fun startOfDay(millis: Long): Long = calendarOf(millis).let {
        it.set(Calendar.HOUR_OF_DAY, 0)
        it.set(Calendar.MINUTE, 0)
        it.set(Calendar.SECOND, 0)
        it.set(Calendar.MILLISECOND, 0)
        it.timeInMillis
    }

    fun endOfDay(millis: Long): Long = startOfDay(millis) + MILLIS_PER_DAY - 1

    fun startOfMonth(year: Int, month: Int): Long = calendarOf(0L).let {
        it.clear()
        it.set(year, month - 1, 1, 0, 0, 0)
        it.timeInMillis
    }

    fun endOfMonth(year: Int, month: Int): Long {
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1
        return startOfMonth(nextYear, nextMonth) - 1
    }

    fun monthSpanBack(year: Int, month: Int, count: Int): Pair<Long, Long> {
        val index = year * 12 + (month - 1) - (count - 1)
        return startOfMonth(index.floorDiv(12), index.mod(12) + 1) to endOfMonth(year, month)
    }

    fun lengthOfMonth(year: Int, month: Int): Int = calendarOf(0L).let {
        it.clear()
        it.set(year, month - 1, 1)
        it.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** 账单月起点：起始日为 monthStartDay（1 即自然月 1 号）。 */
    fun periodStart(year: Int, month: Int, monthStartDay: Int): Long {
        if (monthStartDay <= 1) return startOfMonth(year, month)
        val day = monthStartDay.coerceAtMost(lengthOfMonth(year, month))
        return calendarOf(0L).let {
            it.clear()
            it.set(year, month - 1, day, 0, 0, 0)
            it.timeInMillis
        }
    }

    /** 账单月终点（含）：下一个账单月起点前一毫秒。 */
    fun periodEnd(year: Int, month: Int, monthStartDay: Int): Long {
        if (monthStartDay <= 1) return endOfMonth(year, month)
        val nextYear = if (month == 12) year + 1 else year
        val nextMonth = if (month == 12) 1 else month + 1
        return periodStart(nextYear, nextMonth, monthStartDay) - 1
    }

    /** 从 (year, month) 这个账单月往回 count 个月的区间。 */
    fun periodSpanBack(year: Int, month: Int, count: Int, monthStartDay: Int): Pair<Long, Long> {
        if (monthStartDay <= 1) return monthSpanBack(year, month, count)
        val index = year * 12 + (month - 1) - (count - 1)
        return periodStart(index.floorDiv(12), index.mod(12) + 1, monthStartDay) to
            periodEnd(year, month, monthStartDay)
    }

    /** 某个时刻落在哪个账单月，返回 (year, month)。 */
    fun periodOf(millis: Long, monthStartDay: Int): Pair<Int, Int> {
        val year = yearOf(millis)
        val month = monthOf(millis)
        if (monthStartDay <= 1 || dayOf(millis) >= monthStartDay) return year to month
        return if (month == 1) year - 1 to 12 else year to month - 1
    }

    /** 账单月标签，如「10月账单」。 */
    fun periodLabel(year: Int, month: Int, monthStartDay: Int): String =
        if (monthStartDay <= 1) "${month}月" else "${month}月账单"

    fun weekdayLabels(startOnSunday: Boolean): List<String> =
        if (startOnSunday) listOf("日", "一", "二", "三", "四", "五", "六")
        else listOf("一", "二", "三", "四", "五", "六", "日")

    fun yearOf(millis: Long): Int = calendarOf(millis).get(Calendar.YEAR)

    fun monthOf(millis: Long): Int = calendarOf(millis).get(Calendar.MONTH) + 1

    fun dayOf(millis: Long): Int = calendarOf(millis).get(Calendar.DAY_OF_MONTH)

    fun dayKeyOf(millis: Long): String = calendarOf(millis).let {
        String.format(Locale.US, "%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

    fun monthKeyOf(millis: Long): String = calendarOf(millis).let {
        String.format(Locale.US, "%04d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
    }

    fun dayLabel(millis: Long): String {
        val diffDays = ((startOfDay(System.currentTimeMillis()) - startOfDay(millis)) / MILLIS_PER_DAY).toInt()
        val prefix = when (diffDays) {
            0 -> "今天"
            1 -> "昨天"
            else -> ""
        }
        val datePart = calendarOf(millis).let {
            String.format(Locale.US, "%d月%d日", it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
        return if (prefix.isEmpty()) datePart else "$prefix · $datePart"
    }

    fun weekdayLabel(millis: Long): String = when (calendarOf(millis).get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "周日"
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        else -> "周六"
    }

    fun timeLabel(millis: Long): String = calendarOf(millis).let {
        String.format(Locale.US, "%02d:%02d", it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
    }

    private fun calendarOf(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
    }
}
