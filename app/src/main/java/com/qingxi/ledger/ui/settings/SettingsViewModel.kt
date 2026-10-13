package com.qingxi.ledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.settings.AppSettings
import com.qingxi.ledger.data.settings.ThemeMode
import com.qingxi.ledger.data.settings.ThemePalette
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MineUiState(
    val isLoading: Boolean = true,
    val transactionCount: Int = 0,
    val monthExpenseMinor: Long = 0L,
    val categoryCount: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.BLUE,
    val monthLabel: String = "本月"
)

class SettingsViewModel(private val graph: AppGraph) : ViewModel() {

    val settings: StateFlow<AppSettings> = graph.settings.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings()
        )

    val mineState: StateFlow<MineUiState> = combine(
        graph.transactions.observeExpenses(),
        graph.categories.observeExpenses(),
        graph.settings.settings
    ) { expenses, categories, appSettings ->
        val now = System.currentTimeMillis()
        val (year, month) = Dates.periodOf(now, appSettings.monthStartDay)
        val start = Dates.periodStart(year, month, appSettings.monthStartDay)
        val end = Dates.periodEnd(year, month, appSettings.monthStartDay)
        MineUiState(
            isLoading = false,
            transactionCount = expenses.size,
            monthExpenseMinor = graph.transactions.sumExpense(start, end),
            categoryCount = categories.size,
            themeMode = appSettings.themeMode,
            palette = appSettings.palette,
            monthLabel = Dates.periodLabel(year, month, appSettings.monthStartDay)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MineUiState(monthLabel = "本月")
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { graph.settings.setThemeMode(mode) }
    }

    fun setPalette(palette: ThemePalette) {
        viewModelScope.launch { graph.settings.setPalette(palette) }
    }

    fun setWeekStartsOnSunday(startsOnSunday: Boolean) {
        viewModelScope.launch { graph.settings.setWeekStartsOnSunday(startsOnSunday) }
    }

    fun setMonthStartDay(day: Int) {
        viewModelScope.launch { graph.settings.setMonthStartDay(day) }
    }
}
