package com.qingxi.ledger.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** 月历网格里的一格（含相邻月份补位格）。 */
data class CalendarDayCell(
    val dayStart: Long,
    val dayKey: String,
    val dayOfMonth: Int,
    val inMonth: Boolean,
    val isToday: Boolean,
    val expenseMinor: Long,
    val count: Int,
    /** 支出热力档位：0 无支出（不染色），1..4 依次加深。 */
    val heatLevel: Int
) {
    val hasRecord: Boolean get() = count > 0
}

/** 选中某一日的流水条目。 */
data class DayRecordItem(
    val id: Long,
    val icon: String,
    val categoryName: String,
    val amountMinor: Long,
    val note: String,
    val tags: List<String>,
    val timeMillis: Long
)

data class CalendarUiState(
    val year: Int = 0,
    val month: Int = 0,
    val weekStartsOnSunday: Boolean = false,
    val isCurrentMonth: Boolean = false,
    val cells: List<CalendarDayCell> = emptyList(),
    val monthExpenseMinor: Long = 0L,
    val monthCount: Int = 0,
    val selectedDayStart: Long? = null,
    val selectedDayLabel: String = "",
    val selectedWeekdayLabel: String = "",
    val selectedExpenseMinor: Long = 0L,
    val selectedRecords: List<DayRecordItem> = emptyList()
)

class CalendarViewModel(private val graph: AppGraph) : ViewModel() {

    private val nowMillis: Long = System.currentTimeMillis()
    private val todayStart: Long = Dates.startOfDay(nowMillis)
    private val todayKey: String = Dates.dayKeyOf(nowMillis)
    private val nowYear: Int = Dates.yearOf(nowMillis)
    private val nowMonth: Int = Dates.monthOf(nowMillis)

    private val request = MutableStateFlow(MonthRequest(nowYear, nowMonth, todayStart))

    val uiState: StateFlow<CalendarUiState> = combine(
        request,
        graph.transactions.observeExpenses(),
        graph.categories.observeAll(),
        graph.settings.settings
    ) { req, transactions, categories, settings -> buildState(req, transactions, categories, settings.weekStartsOnSunday) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CalendarUiState(year = nowYear, month = nowMonth)
        )

    fun shiftMonth(delta: Int) {
        val current = request.value
        val index = current.year * 12 + (current.month - 1) + delta
        goToMonth(index.floorDiv(12), index.mod(12) + 1)
    }

    fun goToMonth(year: Int, month: Int) {
        request.value = MonthRequest(year, month, defaultSelection(year, month))
    }

    fun goToday() {
        request.value = MonthRequest(nowYear, nowMonth, todayStart)
    }

    /** 点选某格；若点的是相邻月份的补位格，自动翻到那一天所在的月份。 */
    fun selectDay(cell: CalendarDayCell) {
        val year = Dates.yearOf(cell.dayStart)
        val month = Dates.monthOf(cell.dayStart)
        val current = request.value
        request.value = if (year == current.year && month == current.month) {
            current.copy(selectedDayStart = cell.dayStart)
        } else {
            MonthRequest(year, month, cell.dayStart)
        }
    }

    private fun defaultSelection(year: Int, month: Int): Long =
        if (year == nowYear && month == nowMonth) todayStart else Dates.startOfMonth(year, month)

    private fun buildState(
        req: MonthRequest,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        weekStartsOnSunday: Boolean
    ): CalendarUiState {
        val daysInMonth = daysInMonthOf(req.year, req.month)
        val monthStart = Dates.startOfMonth(req.year, req.month)
        val leading = weekIndexOf(monthStart, weekStartsOnSunday)
        val rows = (leading + daysInMonth + ROW_COLUMNS - 1) / ROW_COLUMNS
        val cellCount = rows * ROW_COLUMNS
        val gridStart = plusDays(monthStart, -leading)
        val gridEndTime = Dates.endOfDay(plusDays(gridStart, cellCount - 1))
        val gridStartTime = Dates.startOfDay(gridStart)

        // 逐日聚合：索引 0=支出、1=笔数
        val aggregates = HashMap<String, LongArray>()
        for (tx in transactions) {
            val time = tx.timeMillis
            if (time < gridStartTime || time > gridEndTime) continue
            val bucket = aggregates.getOrPut(Dates.dayKeyOf(time)) { LongArray(2) }
            bucket[0] += tx.amountMinor
            bucket[1] += 1L
        }

        var monthMaxExpense = 0L
        for (offset in 0 until daysInMonth) {
            val key = Dates.dayKeyOf(plusDays(monthStart, offset))
            val expense = aggregates[key]?.get(0) ?: 0L
            if (expense > monthMaxExpense) monthMaxExpense = expense
        }

        val cells = (0 until cellCount).map { slot ->
            val dayStart = plusDays(gridStart, slot)
            val key = Dates.dayKeyOf(dayStart)
            val bucket = aggregates[key]
            val expense = bucket?.get(0) ?: 0L
            val count = (bucket?.get(1) ?: 0L).toInt()
            val inMonth = slot >= leading && slot < leading + daysInMonth
            CalendarDayCell(
                dayStart = dayStart,
                dayKey = key,
                dayOfMonth = Dates.dayOf(dayStart),
                inMonth = inMonth,
                isToday = key == todayKey,
                expenseMinor = expense,
                count = count,
                heatLevel = if (inMonth) heatLevelOf(expense, monthMaxExpense) else 0
            )
        }

        val monthExpense = cells.filter { it.inMonth }.sumOf { it.expenseMinor }
        val monthCount = cells.filter { it.inMonth }.sumOf { it.count }

        val selectedStart = Dates.startOfDay(req.selectedDayStart ?: monthStart)
        val selectedEnd = Dates.endOfDay(selectedStart)
        val categoryById = categories.associateBy { it.id }
        val records = transactions
            .filter { it.timeMillis in selectedStart..selectedEnd }
            .map { tx ->
                val category = categoryById[tx.categoryId]
                DayRecordItem(
                    id = tx.id,
                    icon = category?.icon ?: DEFAULT_ICON,
                    categoryName = category?.name ?: UNCATEGORIZED,
                    amountMinor = tx.amountMinor,
                    note = tx.note,
                    tags = splitTags(tx.tags),
                    timeMillis = tx.timeMillis
                )
            }

        return CalendarUiState(
            year = req.year,
            month = req.month,
            weekStartsOnSunday = weekStartsOnSunday,
            isCurrentMonth = req.year == nowYear && req.month == nowMonth,
            cells = cells,
            monthExpenseMinor = monthExpense,
            monthCount = monthCount,
            selectedDayStart = selectedStart,
            selectedDayLabel = Dates.dayLabel(selectedStart),
            selectedWeekdayLabel = Dates.weekdayLabel(selectedStart),
            selectedExpenseMinor = records.sumOf { it.amountMinor },
            selectedRecords = records
        )
    }

    private data class MonthRequest(val year: Int, val month: Int, val selectedDayStart: Long?)

    private companion object {
        const val ROW_COLUMNS = 7
        const val DEFAULT_ICON = "🏷"
        const val UNCATEGORIZED = "未分类"

        fun heatLevelOf(expense: Long, monthMax: Long): Int = when {
            expense <= 0L || monthMax <= 0L -> 0
            else -> {
                val ratio = expense.toFloat() / monthMax.toFloat()
                when {
                    ratio <= 0.25f -> 1
                    ratio <= 0.50f -> 2
                    ratio <= 0.75f -> 3
                    else -> 4
                }
            }
        }

        /** 当月第一天在周行中的列序；周起始日可设为周日或周一。 */
        fun weekIndexOf(millis: Long, startOnSunday: Boolean): Int {
            val dow = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
            return if (startOnSunday) dow - Calendar.SUNDAY else (dow + ROW_COLUMNS - 2) % ROW_COLUMNS
        }

        fun plusDays(millis: Long, days: Int): Long =
            Calendar.getInstance().apply {
                timeInMillis = millis
                add(Calendar.DAY_OF_MONTH, days)
            }.timeInMillis

        fun daysInMonthOf(year: Int, month: Int): Int =
            Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
