package com.qingxi.ledger.data.recurring

import com.qingxi.ledger.util.Dates

/**
 * 规则在某个时间区间内「应该哪天记一笔」。纯函数，不读数据库也不写：
 * 已确认入账的、被跳过的都只影响这里算出来的候选怎么过滤，算法本身不变。
 */
object RecurringDue {

    /**
     * 返回 [fromMillis] 到 [toMillis]（含首尾当天）之间到期的日期零点，升序。
     *
     * @param recordedCount 这条规则已经入账的笔数，只用来兑现 [END_COUNT] 的总次数上限。
     * @param maxCount 安全上限，防止误配的区间把循环撑爆。
     */
    fun dayStarts(
        rule: RecurringRuleEntity,
        fromMillis: Long,
        toMillis: Long,
        recordedCount: Int = 0,
        maxCount: Int = DEFAULT_MAX_COUNT
    ): List<Long> {
        if (maxCount <= 0 || !rule.enabled) return emptyList()
        var cursor = Dates.startOfDay(maxOf(fromMillis, rule.startMillis))
        val lastDay = Dates.startOfDay(toMillis)
        val until = if (rule.endType == END_UNTIL) Dates.startOfDay(rule.endMillis) else Long.MAX_VALUE
        if (cursor > lastDay || cursor > until) return emptyList()

        var remaining = when (rule.endType) {
            END_COUNT -> rule.totalCount - recordedCount
            else -> Int.MAX_VALUE
        }
        if (remaining <= 0) return emptyList()

        val result = mutableListOf<Long>()
        while (cursor <= lastDay && cursor <= until && result.size < maxCount && remaining > 0) {
            if (matches(rule, cursor)) {
                result += cursor
                remaining -= 1
            }
            cursor = Dates.nextDay(cursor)
        }
        return result
    }

    /** 某一天是否落在这条规则的周期上。 */
    fun matches(rule: RecurringRuleEntity, dayStartMillis: Long): Boolean = when (rule.repeatType) {
        REPEAT_DAILY -> true
        REPEAT_WEEKLY -> rule.weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }
            .contains(Dates.isoWeekdayOf(dayStartMillis))
        REPEAT_MONTHLY -> {
            val year = Dates.yearOf(dayStartMillis)
            val month = Dates.monthOf(dayStartMillis)
            // 29-31 号在不足天数的月份顺延到月末，否则这些月份会整月漏掉。
            Dates.dayOf(dayStartMillis) == rule.dayOfMonth.coerceAtMost(Dates.lengthOfMonth(year, month))
        }
        else -> false
    }

    const val DEFAULT_MAX_COUNT = 400
}
