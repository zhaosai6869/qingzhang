@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.qingxi.ledger.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.DayGroup
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.TxFilter
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.MILLIS_PER_DAY
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 日期范围快捷项。 */
enum class SearchRangeOption(val label: String) {
    Last7("近7天"),
    ThisMonth("本月"),
    Last3Months("近3月"),
    LastYear("近1年"),
    All("全部"),
    Custom("自定义")
}

/** 搜索页的筛选条件集合（关键词单独走防抖通道）。 */
data class SearchFilters(
    val categoryId: Long? = null,
    val tag: String? = null,
    val range: SearchRangeOption = SearchRangeOption.All,
    val customStart: Long? = null,
    val customEnd: Long? = null
) {
    /** 换算成 DAO 可用的过滤条件；自定义范围只选了一端时该端不设限。 */
    fun toTxFilter(keyword: String, nowMillis: Long, monthStartDay: Int): TxFilter {
        val span = resolveSpan(nowMillis, monthStartDay)
        return TxFilter(
            categoryId = categoryId,
            start = span?.first,
            end = span?.second,
            tag = tag,
            keyword = keyword.trim().takeIf { it.isNotEmpty() }
        )
    }

    private fun resolveSpan(nowMillis: Long, monthStartDay: Int): Pair<Long?, Long?>? {
        val todayStart = Dates.startOfDay(nowMillis)
        return when (this.range) {
            SearchRangeOption.All -> null
            SearchRangeOption.Last7 -> todayStart to Dates.endOfDay(nowMillis)
            SearchRangeOption.ThisMonth -> {
                val (year, month) = Dates.periodOf(nowMillis, monthStartDay)
                Dates.periodStart(year, month, monthStartDay) to
                    Dates.periodEnd(year, month, monthStartDay)
            }
            SearchRangeOption.Last3Months -> {
                val (year, month) = Dates.periodOf(todayStart, monthStartDay)
                val index = year * 12 + (month - 1) - 2
                Dates.periodStart(index.floorDiv(12), index.mod(12) + 1, monthStartDay) to
                    Dates.endOfDay(nowMillis)
            }
            SearchRangeOption.LastYear ->
                todayStart - 364L * MILLIS_PER_DAY to Dates.endOfDay(nowMillis)
            SearchRangeOption.Custom -> {
                val start = customStart?.let { Dates.startOfDay(it) }
                val end = customEnd?.let { Dates.endOfDay(it) }
                if (start != null && end != null && start > end) {
                    Dates.startOfDay(end) to Dates.endOfDay(start)
                } else {
                    start to end
                }
            }
        }
    }
}

data class SearchUiState(
    val keyword: String = "",
    val filters: SearchFilters = SearchFilters(),
    val categories: List<CategoryEntity> = emptyList(),
    val categoryById: Map<Long, CategoryEntity> = emptyMap(),
    val tagOptions: List<String> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val totalCount: Int = 0,
    val expenseMinor: Long = 0L,
    val categoryLabel: String = "全部分类",
    val rangeLabel: String = SearchRangeOption.All.label,
    val customRangeHint: String = ""
) {
    val noResult: Boolean get() = groups.isEmpty()
}

/**
 * 搜索筛选页状态：关键词防抖 250ms，其余筛选即时生效。
 */
class SearchViewModel(private val graph: AppGraph) : ViewModel() {

    private val nowMillis: Long = System.currentTimeMillis()

    private val rawKeyword = MutableStateFlow("")
    private val keyword = MutableStateFlow("")
    private val filters = MutableStateFlow(SearchFilters())
    private val tagTick = MutableStateFlow(0L)

    private val results = combine(filters, keyword, graph.settings.settings) { f, text, settings ->
        f.toTxFilter(text, nowMillis, settings.monthStartDay)
    }
        .flatMapLatest { filter -> graph.transactions.observeFiltered(filter) }

    private val tagOptions = tagTick.flatMapLatest {
        flow { emit(graph.transactions.usedTags()) }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        results,
        graph.categories.observeExpenses(),
        tagOptions,
        filters,
        rawKeyword
    ) { transactions, categories, tags, f, text ->
        buildState(transactions, categories, tags, f, text)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SearchUiState()
    )

    init {
        viewModelScope.launch {
            rawKeyword
                .drop(1)
                .distinctUntilChanged()
                .debounce(250.milliseconds)
                .collect { keyword.value = it }
        }
    }

    fun onKeywordChange(text: String) {
        rawKeyword.value = text
    }

    fun clearKeyword() {
        rawKeyword.value = ""
        keyword.value = ""
    }

    fun setCategory(categoryId: Long?) {
        filters.value = filters.value.copy(categoryId = categoryId)
    }

    fun toggleTag(tag: String) {
        val current = filters.value
        filters.value = current.copy(tag = if (current.tag == tag) null else tag)
    }

    fun setRange(option: SearchRangeOption) {
        filters.value = filters.value.copy(range = option)
    }

    fun setCustomStart(millisOfDay: Long) {
        filters.value = filters.value.copy(range = SearchRangeOption.Custom, customStart = millisOfDay)
    }

    fun setCustomEnd(millisOfDay: Long) {
        filters.value = filters.value.copy(range = SearchRangeOption.Custom, customEnd = millisOfDay)
    }

    fun resetFilters() {
        filters.value = SearchFilters()
        clearKeyword()
    }

    fun refreshTags() {
        tagTick.value = tagTick.value + 1
    }

    private fun buildState(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        tags: List<String>,
        f: SearchFilters,
        text: String
    ): SearchUiState {
        val categoryById = categories.associateBy { it.id }
        val expense = transactions.sumOf { it.amountMinor }
        val selectedCategory = f.categoryId?.let { categoryById[it] }
        return SearchUiState(
            keyword = text,
            filters = f,
            categories = categories,
            categoryById = categoryById,
            tagOptions = tags,
            groups = buildDayGroups(transactions),
            totalCount = transactions.size,
            expenseMinor = expense,
            categoryLabel = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "全部分类",
            rangeLabel = if (f.range == SearchRangeOption.Custom) "自定义" else f.range.label,
            customRangeHint = customRangeHintOf(f)
        )
    }

    private fun customRangeHintOf(f: SearchFilters): String {
        if (f.range != SearchRangeOption.Custom) return ""
        val start = f.customStart?.let { dayLabelOf(it) } ?: "未选"
        val end = f.customEnd?.let { dayLabelOf(it) } ?: "未选"
        return "$start → $end"
    }

    private fun dayLabelOf(millis: Long): String =
        String.format(
            Locale.CHINA,
            "%d月%d日",
            Dates.monthOf(millis),
            Dates.dayOf(millis)
        )

    companion object {
        /** DatePicker 返回的是 UTC 零点的毫秒值，转成本地当天零点。 */
        fun utcMillisToLocalDayStart(utcMillis: Long): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis
            }
            return Calendar.getInstance().apply {
                clear()
                set(
                    utc.get(Calendar.YEAR),
                    utc.get(Calendar.MONTH),
                    utc.get(Calendar.DAY_OF_MONTH)
                )
            }.timeInMillis
        }
    }
}
