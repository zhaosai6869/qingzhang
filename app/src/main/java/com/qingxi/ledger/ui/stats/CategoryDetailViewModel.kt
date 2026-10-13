@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.qingxi.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.DayGroup
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.TxFilter
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.ui.flow.buildDayGroups
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 从统计页下钻某个分类明细时携带的查询条件。 */
data class CategoryDrill(
    val categoryId: Long,
    val start: Long,
    val end: Long
)

data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    val category: CategoryEntity? = null,
    val periodLabel: String = "",
    val totalMinor: Long = 0L,
    val count: Int = 0,
    val averageMinor: Long = 0L,
    val shareOfPeriod: Float = 0f,
    val activeDays: Int = 0,
    val largest: TransactionEntity? = null,
    val groups: List<DayGroup> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

/**
 * 分类下钻明细：某一分类在给定区间内的全部支出流水，按日分组，并给出该分类的小计与占比。
 */
class CategoryDetailViewModel(private val graph: AppGraph) : ViewModel() {

    private val drill = MutableStateFlow<CategoryDrill?>(null)

    val uiState: StateFlow<CategoryDetailUiState> = drill
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(CategoryDetailUiState())
            } else {
                combine(
                    graph.transactions.observeFiltered(
                        TxFilter(
                            categoryId = current.categoryId,
                            start = current.start,
                            end = current.end
                        )
                    ),
                    graph.transactions.observeFiltered(
                        TxFilter(start = current.start, end = current.end)
                    ),
                    graph.categories.observeExpenses()
                ) { picked, periodTotal, categories ->
                    buildState(
                        request = current,
                        picked = picked,
                        periodTotalMinor = periodTotal.sumOf { it.amountMinor },
                        category = categories.firstOrNull { it.id == current.categoryId }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CategoryDetailUiState()
        )

    fun bind(request: CategoryDrill) {
        if (drill.value != request) drill.value = request
    }

    private fun buildState(
        request: CategoryDrill,
        picked: List<TransactionEntity>,
        periodTotalMinor: Long,
        category: CategoryEntity?
    ): CategoryDetailUiState {
        val total = picked.sumOf { it.amountMinor }
        val groups = buildDayGroups(picked)
        return CategoryDetailUiState(
            isLoading = false,
            category = category,
            periodLabel = "${formatDate(request.start)} 至 ${formatDate(request.end)}",
            totalMinor = total,
            count = picked.size,
            averageMinor = if (picked.isEmpty()) 0L else total / picked.size,
            shareOfPeriod = if (periodTotalMinor > 0L) total.toFloat() / periodTotalMinor.toFloat() else 0f,
            activeDays = groups.size,
            largest = picked.maxByOrNull { it.amountMinor },
            groups = groups
        )
    }

    private fun formatDate(millis: Long): String = Calendar.getInstance().apply {
        timeInMillis = millis
    }.let {
        "${it.get(Calendar.YEAR)}年${it.get(Calendar.MONTH) + 1}月${it.get(Calendar.DAY_OF_MONTH)}日"
    }
}
