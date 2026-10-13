@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.qingxi.ledger.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.DayGroup
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.TxFilter
import com.qingxi.ledger.data.tx.splitPaths
import com.qingxi.ledger.widget.refreshTodayWidgets
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.MILLIS_PER_DAY
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顶栏正在翻看的账单月，含用户设置的月度起始日；isCurrent 表示就是「本月」。 */
private data class MonthQuery(val year: Int, val month: Int, val startDay: Int, val isCurrent: Boolean)

/** 一次 Snackbar 提示，token 用于让界面消费后清空。 */
data class FlowMessage(val token: Long, val text: String)

data class FlowUiState(
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = false,
    val expenseMinor: Long = 0L,
    val dailyAverageMinor: Long = 0L,
    val dayDivisor: Int = 1,
    val count: Int = 0,
    val groups: List<DayGroup> = emptyList(),
    val items: List<TransactionEntity> = emptyList(),
    val categoryById: Map<Long, CategoryEntity> = emptyMap(),
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
    val selectedItems: List<TransactionEntity> get() = items.filter { it.id in selectedIds }
    val allIds: List<Long> get() = items.map { it.id }
}

private data class SelectionState(val mode: Boolean = false, val ids: Set<Long> = emptySet())

/**
 * 账单流水页状态：月份翻页 + 当月汇总 + 按日分组列表 + 多选删除。
 */
class FlowViewModel(private val graph: AppGraph) : ViewModel() {

    private val nowMillis: Long = System.currentTimeMillis()

    /** 相对当前账单月的翻页偏移，0 即「本月」。 */
    private val monthOffset = MutableStateFlow(0)
    private val selection = MutableStateFlow(SelectionState())
    private val message = MutableStateFlow<FlowMessage?>(null)

    /** 当前翻看的账单月，含用户设置的月度起始日。 */
    private val monthQuery = combine(monthOffset, graph.settings.settings) { offset, settings ->
        val (year, month) = Dates.periodOf(nowMillis, settings.monthStartDay)
        val index = year * 12 + (month - 1) + offset
        MonthQuery(
            year = index.floorDiv(12),
            month = index.mod(12) + 1,
            startDay = settings.monthStartDay,
            isCurrent = offset == 0
        )
    }

    private var messageToken: Long = 0L

    val uiMessage: StateFlow<FlowMessage?> get() = message

    private val monthTransactions = monthQuery.flatMapLatest { query ->
        graph.transactions.observeFiltered(
            TxFilter(
                start = Dates.periodStart(query.year, query.month, query.startDay),
                end = Dates.periodEnd(query.year, query.month, query.startDay)
            )
        )
    }

    val uiState: StateFlow<FlowUiState> = combine(
        monthTransactions,
        graph.categories.observeAll(),
        monthQuery,
        selection
    ) { transactions, categories, query, sel ->
        buildState(query, sel, transactions, categories.associateBy { it.id })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FlowUiState(monthLabel = "本月", isCurrentMonth = true)
    )

    fun previousMonth() = shiftMonth(-1)

    fun nextMonth() = shiftMonth(1)

    fun goToCurrentMonth() {
        monthOffset.value = 0
        selection.value = SelectionState()
    }

    /** 长按条目进入多选。 */
    fun startSelection(id: Long) {
        selection.value = SelectionState(mode = true, ids = setOf(id))
    }

    fun toggleSelection(id: Long) {
        val current = selection.value
        val ids = if (id in current.ids) current.ids - id else current.ids + id
        selection.value = current.copy(mode = true, ids = ids)
    }

    fun selectAll(ids: List<Long>) {
        selection.value = SelectionState(mode = true, ids = ids.toSet())
    }

    fun cancelSelection() {
        selection.value = SelectionState()
    }

    /** 删除若干笔（左滑单笔与批量删除共用），界面已二次确认，同时清掉附件图片文件。 */
    fun deleteTransactions(items: List<TransactionEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val paths = items.flatMap { splitPaths(it.attachments) }
            withContext(Dispatchers.IO) {
                paths.forEach { path -> runCatching { File(path).delete() } }
            }
            graph.transactions.delete(items.map { it.id })
            refreshTodayWidgets(graph.appContext)
            selection.value = SelectionState()
            messageToken += 1
            message.value = FlowMessage(
                token = messageToken,
                text = "已删除 ${items.size} 笔"
            )
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun shiftMonth(delta: Int) {
        monthOffset.update { it + delta }
        selection.value = SelectionState()
    }

    private fun buildState(
        query: MonthQuery,
        sel: SelectionState,
        transactions: List<TransactionEntity>,
        categoryById: Map<Long, CategoryEntity>
    ): FlowUiState {
        val expense = transactions.sumOf { it.amountMinor }
        val divisor = dayDivisor(query, query.isCurrent)
        return FlowUiState(
            monthLabel = monthLabelOf(query.year, query.month, query.startDay),
            isCurrentMonth = query.isCurrent,
            expenseMinor = expense,
            dailyAverageMinor = if (divisor <= 0) expense else expense / divisor,
            dayDivisor = divisor,
            count = transactions.size,
            groups = buildDayGroups(transactions),
            items = transactions,
            categoryById = categoryById,
            selectionMode = sel.mode,
            selectedIds = sel.ids
        )
    }

    /** 账单月已过的天数作分母，未过完的月份用整段天数。 */
    private fun dayDivisor(query: MonthQuery, isCurrentMonth: Boolean): Int {
        val start = Dates.periodStart(query.year, query.month, query.startDay)
        val end = Dates.periodEnd(query.year, query.month, query.startDay)
        val spanDays = ((Dates.startOfDay(end) - start) / MILLIS_PER_DAY).toInt() + 1
        if (!isCurrentMonth) return spanDays.coerceAtLeast(1)
        val elapsed = ((Dates.startOfDay(nowMillis) - start) / MILLIS_PER_DAY).toInt() + 1
        return elapsed.coerceIn(1, spanDays)
    }

    private fun monthLabelOf(year: Int, month: Int, startDay: Int): String =
        if (startDay <= 1) "${year}年${month}月" else "${year}年${month}月账单"
}
