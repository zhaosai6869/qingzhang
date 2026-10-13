package com.qingxi.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.category.KIND_EXPENSE
import com.qingxi.ledger.data.tx.CategoryStat
import com.qingxi.ledger.data.tx.MonthPoint
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.MILLIS_PER_DAY
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 统计区间。 */
enum class StatsRange(val label: String, val months: Int) {
    CURRENT_MONTH("本月", 1),
    THREE_MONTHS("近3月", 3),
    HALF_YEAR("近6月", 6),
    FULL_YEAR("近12月", 12)
}

/** 日趋势上的一个点。 */
data class DayTrendPoint(
    val dayKey: String,
    val label: String,
    val valueMinor: Long
)

data class StatsUiState(
    val range: StatsRange = StatsRange.HALF_YEAR,
    val isLoading: Boolean = true,
    val periodLabel: String = "",
    val periodStart: Long = 0L,
    val periodEnd: Long = 0L,
    val totalMinor: Long = 0L,
    val recordCount: Int = 0,
    val dailyAverageMinor: Long = 0L,
    val maxAmountMinor: Long = 0L,
    val previousTotalMinor: Long = 0L,
    val dayTrend: List<DayTrendPoint> = emptyList(),
    val monthTrend: List<MonthPoint> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    val largestShare: Float = 0f
) {
    val hasData: Boolean get() = totalMinor > 0L
    val changeRatio: Float?
        get() = when {
            previousTotalMinor <= 0L -> null
            else -> (totalMinor - previousTotalMinor).toFloat() / previousTotalMinor.toFloat()
        }
}

class StatsViewModel(private val graph: AppGraph) : ViewModel() {

    private val nowMillis: Long = System.currentTimeMillis()

    private val range = MutableStateFlow(StatsRange.HALF_YEAR)

    val uiState: StateFlow<StatsUiState> = combine(
        range,
        graph.transactions.observeExpenses(),
        graph.settings.settings
    ) { current, transactions, settings -> buildState(current, transactions, settings.monthStartDay) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StatsUiState()
        )

    fun selectRange(newRange: StatsRange) {
        if (range.value == newRange) return
        range.value = newRange
    }

    private suspend fun buildState(
        selected: StatsRange,
        transactions: List<TransactionEntity>,
        monthStartDay: Int
    ): StatsUiState {
        val (anchorYear, anchorMonth) = Dates.periodOf(nowMillis, monthStartDay)
        val anchorIndex = anchorYear * 12 + anchorMonth - 1
        val span = Dates.periodSpanBack(anchorYear, anchorMonth, selected.months, monthStartDay)
        val periodStart = span.first
        val periodEnd = span.second
        val dataEnd = minOf(periodEnd, Dates.endOfDay(nowMillis))
        val rows = transactions.filter { it.timeMillis in periodStart..periodEnd }
        val total = rows.sumOf { it.amountMinor }

        val categoryById = graph.categories.all().associateBy { it.id }
        val stats = rows.groupBy { it.categoryId }
            .map { (categoryId, group) ->
                CategoryStat(
                    category = categoryById[categoryId] ?: fallbackCategory(categoryId),
                    totalMinor = group.sumOf { it.amountMinor },
                    count = group.size
                )
            }
            .filter { it.totalMinor > 0L }
            .sortedByDescending { it.totalMinor }

        val dayTrend = if (selected.months == 1) {
            buildDayTrend(periodStart, dataEnd, rows)
        } else {
            emptyList()
        }

        val monthTrend = buildMonthTrend(transactions, monthStartDay, anchorIndex, selected.months)

        val maxAmount = stats.maxOfOrNull { it.totalMinor } ?: 0L
        val days = elapsedDays(periodStart, dataEnd)

        return StatsUiState(
            range = selected,
            isLoading = false,
            periodLabel = "${formatDate(periodStart)} 至 ${formatDate(dataEnd)}",
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalMinor = total,
            recordCount = rows.size,
            dailyAverageMinor = if (days > 0) total / days else total,
            maxAmountMinor = maxAmount,
            previousTotalMinor = previousPeriodTotal(selected, transactions, monthStartDay),
            dayTrend = dayTrend,
            monthTrend = monthTrend,
            categoryStats = stats,
            largestShare = if (total > 0L && maxAmount > 0L) {
                maxAmount.toFloat() / total.toFloat()
            } else {
                0f
            }
        )
    }

    /** 与当前区间等长、紧邻其前的账单月区间总额，用于环比。 */
    private fun previousPeriodTotal(
        selected: StatsRange,
        transactions: List<TransactionEntity>,
        monthStartDay: Int
    ): Long {
        val (anchorYear, anchorMonth) = Dates.periodOf(nowMillis, monthStartDay)
        val anchorIndex = anchorYear * 12 + anchorMonth - 1
        val months = selected.months
        val currentIndex = anchorIndex - months + 1
        val previousLast = currentIndex - 1
        val previousFirst = previousLast - months + 1
        if (previousFirst < 0) return 0L
        val start = Dates.periodStart(previousFirst.floorDiv(12), previousFirst.mod(12) + 1, monthStartDay)
        val end = Dates.periodEnd(previousLast.floorDiv(12), previousLast.mod(12) + 1, monthStartDay)
        return transactions.filter { it.timeMillis in start..end }.sumOf { it.amountMinor }
    }

    private companion object {

        fun buildDayTrend(
            start: Long,
            end: Long,
            rows: List<TransactionEntity>
        ): List<DayTrendPoint> {
            val totalsByDay = rows.groupBy { Dates.dayKeyOf(it.timeMillis) }
            val points = ArrayList<DayTrendPoint>()
            var day = Dates.startOfDay(start)
            val lastDay = Dates.startOfDay(end)
            while (day <= lastDay) {
                val key = Dates.dayKeyOf(day)
                points.add(
                    DayTrendPoint(
                        dayKey = key,
                        label = "${Dates.dayOf(day)}日",
                        valueMinor = totalsByDay[key].orEmpty().sumOf { it.amountMinor }
                    )
                )
                day = plusDays(day, 1)
            }
            return points
        }

        /** 按账单月分桶的最近 months 个月。 */
        fun buildMonthTrend(
            transactions: List<TransactionEntity>,
            monthStartDay: Int,
            anchorIndex: Int,
            months: Int
        ): List<MonthPoint> {
            val buckets = transactions.groupBy { periodKeyOf(it.timeMillis, monthStartDay) }
            return ((months - 1) downTo 0).map { back ->
                val index = anchorIndex - back
                val key = String.format(Locale.US, "%04d-%02d", index.floorDiv(12), index.mod(12) + 1)
                MonthPoint(
                    monthKey = key,
                    expenseMinor = buckets[key].orEmpty().sumOf { it.amountMinor }
                )
            }
        }

        fun periodKeyOf(millis: Long, monthStartDay: Int): String {
            val (year, month) = Dates.periodOf(millis, monthStartDay)
            return String.format(Locale.US, "%04d-%02d", year, month)
        }

        fun fallbackCategory(id: Long): CategoryEntity = CategoryEntity(
            id = id,
            name = "未知分类",
            icon = "🏷",
            kind = KIND_EXPENSE,
            sortOrder = Long.MAX_VALUE,
            isSystem = false
        )

        fun elapsedDays(start: Long, end: Long): Long {
            val days = (Dates.startOfDay(end) - Dates.startOfDay(start)) / MILLIS_PER_DAY + 1
            return if (days < 1) 1 else days
        }

        fun formatDate(millis: Long): String = Calendar.getInstance().apply {
            timeInMillis = millis
        }.let { calendar ->
            "${calendar.get(Calendar.YEAR)}年${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
        }

        fun plusDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis
    }
}
