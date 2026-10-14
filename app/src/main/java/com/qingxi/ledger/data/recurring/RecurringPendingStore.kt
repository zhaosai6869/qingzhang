package com.qingxi.ledger.data.recurring

import com.qingxi.ledger.data.category.KIND_EXPENSE
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.MILLIS_PER_DAY
import com.qingxi.ledger.widget.refreshTodayWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一条待确认的到期项：某条规则在某个该记没记。不落库，确认后才变成流水。 */
data class RecurringPendingItem(
    val rule: RecurringRuleEntity,
    val dueDayKey: String,
    val dueDayStart: Long
) {
    val amountMinor: Long get() = rule.amountMinor
}

/**
 * 冷启动时算一遍「到今天该记哪些」，结果只放在进程内。
 *
 * 不排队、不写库、也不在后台唤醒：没确认的到期项压根不是花出去的钱，
 * 直接进账单会把统计算虚，所以只能列出来等人点。
 */
class RecurringPendingStore(private val graph: AppGraph) {

    private val _items = MutableStateFlow<List<RecurringPendingItem>>(emptyList())
    val items: StateFlow<List<RecurringPendingItem>> = _items.asStateFlow()

    private var coldStartDone = false

    /** 一个进程只主动算一次，之后的变化由确认/跳过等动作显式刷新。 */
    suspend fun refreshOnColdStart() {
        if (coldStartDone) return
        coldStartDone = true
        refresh()
    }

    suspend fun refresh() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        val windowStart = today - (LOOKBACK_DAYS - 1) * MILLIS_PER_DAY
        val rules = graph.recurrences.enabledRules()
        if (rules.isEmpty()) {
            _items.value = emptyList()
            return
        }
        val recordedKeys = graph.transactions.recurringKeys().toSet()
        val dismissedKeys = graph.recurrences.dismissedKeys().toSet()
        val recordedCount = HashMap<Long, Int>()
        recordedKeys.forEach { key ->
            key.substringBefore('|').toLongOrNull()?.let { ruleId ->
                recordedCount[ruleId] = (recordedCount[ruleId] ?: 0) + 1
            }
        }

        val result = mutableListOf<RecurringPendingItem>()
        for (rule in rules) {
            val days = RecurringDue.dayStarts(
                rule = rule,
                fromMillis = maxOf(windowStart, rule.startMillis),
                toMillis = today,
                recordedCount = recordedCount[rule.id] ?: 0,
                maxCount = LOOKBACK_DAYS
            )
            for (dayStart in days) {
                val dayKey = Dates.dayKeyOf(dayStart)
                val idempotentKey = RecurringRepository.keyOf(rule.id, dayKey)
                if (idempotentKey in recordedKeys || idempotentKey in dismissedKeys) continue
                result += RecurringPendingItem(rule = rule, dueDayKey = dayKey, dueDayStart = dayStart)
            }
        }
        _items.value = result.sortedWith(compareBy({ it.dueDayStart }, { it.rule.id }))
    }

    /**
     * 确认入账：今天到期的用当下时间，往期的落到那天正午，
     * 免得补记的账单在列表里挂着一个突兀的凌晨时刻。
     */
    suspend fun confirm(targets: List<RecurringPendingItem>): Int {
        if (targets.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val today = Dates.startOfDay(now)
        var inserted = 0
        for (item in targets) {
            val rowId = graph.transactions.insertIgnoringConflict(
                TransactionEntity(
                    kind = KIND_EXPENSE,
                    amountMinor = item.rule.amountMinor,
                    categoryId = item.rule.categoryId,
                    note = item.rule.note,
                    tags = item.rule.tags,
                    timeMillis = if (item.dueDayStart == today) now else Dates.middayOf(item.dueDayStart),
                    createdAtMillis = now,
                    origin = ORIGIN_RECURRING,
                    ruleId = item.rule.id,
                    dueDayKey = item.dueDayKey
                )
            )
            if (rowId > 0L) inserted += 1
        }
        refreshTodayWidgets(graph.appContext)
        refresh()
        return inserted
    }

    /** 跳过只写标记，不动规则也不改时间；下一次冷启动不会再列出来。 */
    suspend fun skip(item: RecurringPendingItem) {
        graph.recurrences.dismiss(item.rule.id, item.dueDayKey)
        refresh()
    }

    companion object {
        /** 只在最近这些天里补记；再往前的到期项就当没发生过，不然清单会无限堆。 */
        const val LOOKBACK_DAYS = 90
    }
}
