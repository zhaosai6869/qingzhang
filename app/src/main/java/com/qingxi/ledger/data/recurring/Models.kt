package com.qingxi.ledger.data.recurring

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val REPEAT_DAILY = 0
const val REPEAT_WEEKLY = 1
const val REPEAT_MONTHLY = 2

const val END_NEVER = 0
const val END_UNTIL = 1
const val END_COUNT = 2

/** 周期规则产生的流水在 `transactions.origin` 上的取值。 */
const val ORIGIN_RECURRING = 4

/**
 * 一条周期支出规则。只描述「该在哪些天记多少」，本身不代表任何已发生的支出——
 * 到期项要用户在待确认清单里确认后才写进 transactions，改规则不会追溯已入账的流水。
 */
@Entity(
    tableName = "recurring_rules",
    indices = [Index("categoryId")]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amountMinor: Long,
    val note: String = "",
    val tags: String = "",
    val repeatType: Int,
    /** ISO 星期：1=周一 … 7=周日，逗号分隔；只在 [REPEAT_WEEKLY] 下有意义。 */
    val weekdays: String = "",
    /** 只在 [REPEAT_MONTHLY] 下有意义，1..31；超出当月天数时钳到月末。 */
    val dayOfMonth: Int = 1,
    val startMillis: Long,
    val endType: Int = END_NEVER,
    /** [END_UNTIL] 的截止日期，当天仍算到期。 */
    val endMillis: Long = 0,
    /** [END_COUNT] 的总次数，含已确认入账的那几笔。 */
    val totalCount: Int = 0,
    val enabled: Boolean = true,
    val createdMillis: Long
)

/**
 * 「这笔到期项我不记了」的落点。不写它的话每次打开应用都会重弹同一笔，
 * 也不能把它塞进 transactions——没花的钱进了账单会污染统计。
 * 主键即幂等保证：同一个规则的同一天只可能被跳过一次。
 */
@Entity(tableName = "recurring_dismissals", primaryKeys = ["ruleId", "dueDayKey"])
data class RecurringDismissalEntity(
    val ruleId: Long,
    val dueDayKey: String,
    val dismissedMillis: Long
)
