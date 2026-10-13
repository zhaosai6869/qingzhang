@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.qingxi.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.category.KIND_EXPENSE
import com.qingxi.ledger.data.tx.CategoryStat
import com.qingxi.ledger.data.tx.MonthPoint
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.splitTags
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

/** 一年里记录最密集的一天。 */
data class DayPeak(
    val dayStart: Long,
    val count: Int,
    val expenseMinor: Long
)

/** 标签维度的年度汇总。 */
data class TagStat(
    val tag: String,
    val count: Int,
    val totalMinor: Long
)

data class AnnualReportUiState(
    val year: Int = 0,
    val availableYears: List<Int> = emptyList(),
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val expenseMinor: Long = 0L,
    val count: Int = 0,
    val activeDays: Int = 0,
    val dailyAverageMinor: Long = 0L,
    val categoryCount: Int = 0,
    val largest: TransactionEntity? = null,
    val busiestDay: DayPeak? = null,
    val heaviestMonth: MonthPoint? = null,
    val lightestMonth: MonthPoint? = null,
    val monthPoints: List<MonthPoint> = emptyList(),
    val topExpense: List<CategoryStat> = emptyList(),
    val topTags: List<TagStat> = emptyList()
)

/**
 * 年度报告：按自然年汇总支出、趋势、分类与标签排行。
 * 数据量是个人账本级别，直接在全量支出流水上按年筛选计算，保证记账后即时刷新。
 */
class AnnualReportViewModel(private val graph: AppGraph) : ViewModel() {

    private val nowMillis: Long = System.currentTimeMillis()
    private val currentYear: Int = Dates.yearOf(nowMillis)
    private val selectedYear = MutableStateFlow(currentYear)

    val uiState: StateFlow<AnnualReportUiState> = combine(
        graph.transactions.observeExpenses(),
        graph.categories.observeExpenses(),
        selectedYear
    ) { transactions, categories, year ->
        buildState(year, transactions, categories.associateBy { it.id })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AnnualReportUiState(year = currentYear)
    )

    fun selectYear(year: Int) {
        if (selectedYear.value != year) selectedYear.value = year
    }

    private fun buildState(
        year: Int,
        transactions: List<TransactionEntity>,
        categoryById: Map<Long, CategoryEntity>
    ): AnnualReportUiState {
        val years = transactions.map { Dates.yearOf(it.timeMillis) }
            .distinct()
            .plus(currentYear)
            .sortedDescending()
        val ofYear = transactions.filter { Dates.yearOf(it.timeMillis) == year }

        val expense = ofYear.sumOf { it.amountMinor }

        val monthPoints = (1..12).map { month ->
            val key = String.format(Locale.US, "%04d-%02d", year, month)
            val slice = ofYear.filter { Dates.monthKeyOf(it.timeMillis) == key }
            MonthPoint(
                monthKey = key,
                expenseMinor = slice.sumOf { it.amountMinor }
            )
        }

        val expenseByCategory = categoryTotals(ofYear, categoryById)

        val byDay = ofYear.groupBy { Dates.startOfDay(it.timeMillis) }
        val busiest = byDay.entries.maxByOrNull { it.value.size }?.let { entry ->
            DayPeak(
                dayStart = entry.key,
                count = entry.value.size,
                expenseMinor = entry.value.sumOf { it.amountMinor }
            )
        }

        val heaviest = monthPoints.filter { it.expenseMinor > 0L }.maxByOrNull { it.expenseMinor }
        val lightest = monthPoints.filter { it.expenseMinor > 0L }.minByOrNull { it.expenseMinor }

        val tagTotals = ofYear
            .flatMap { tx -> splitTags(tx.tags).map { it to tx.amountMinor } }
            .groupBy({ it.first }, { it.second })
            .map { (tag, amounts) -> TagStat(tag = tag, count = amounts.size, totalMinor = amounts.sum()) }
            .sortedByDescending { it.totalMinor }
            .take(MAX_TAG_ROWS)

        return AnnualReportUiState(
            year = year,
            availableYears = years,
            isLoading = false,
            hasData = ofYear.isNotEmpty(),
            expenseMinor = expense,
            count = ofYear.size,
            activeDays = byDay.size,
            dailyAverageMinor = if (expense <= 0L) 0L else expense / elapsedDaysOfYear(year),
            categoryCount = ofYear.map { it.categoryId }.distinct().size,
            largest = ofYear.maxByOrNull { it.amountMinor },
            busiestDay = busiest?.takeIf { it.count > 1 },
            heaviestMonth = heaviest,
            lightestMonth = lightest?.takeIf { it != heaviest },
            monthPoints = monthPoints,
            topExpense = expenseByCategory,
            topTags = tagTotals
        )
    }

    private fun categoryTotals(
        items: List<TransactionEntity>,
        categoryById: Map<Long, CategoryEntity>
    ): List<CategoryStat> = items
        .groupBy { it.categoryId }
        .map { (categoryId, group) ->
            CategoryStat(
                category = categoryById[categoryId] ?: CategoryEntity(
                    id = categoryId,
                    name = "未知分类",
                    icon = "🏷",
                    kind = KIND_EXPENSE,
                    sortOrder = Long.MAX_VALUE,
                    isSystem = false
                ),
                totalMinor = group.sumOf { it.amountMinor },
                count = group.size
            )
        }
        .sortedByDescending { it.totalMinor }
        .take(MAX_CATEGORY_ROWS)

    /** 当年的自然年已过天数，用于日均支出。 */
    private fun elapsedDaysOfYear(year: Int): Long {
        val start = Dates.startOfMonth(year, 1)
        val daysInYear = if (isLeapYear(year)) 366L else 365L
        if (year < currentYear) return daysInYear
        if (year > currentYear) return 1L
        return ((Dates.startOfDay(nowMillis) - start) / MILLIS_PER_DAY + 1).coerceIn(1L, daysInYear)
    }

    private fun isLeapYear(year: Int): Boolean =
        Calendar.getInstance().apply {
            clear()
            set(year, Calendar.DECEMBER, 31)
        }.get(Calendar.DAY_OF_YEAR) == 366

    private companion object {
        const val MAX_CATEGORY_ROWS = 5
        const val MAX_TAG_ROWS = 6
    }
}
