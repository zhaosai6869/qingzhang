@file:OptIn(ExperimentalMaterial3Api::class)

package com.qingxi.ledger.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.Money
import java.util.Calendar
import java.util.TimeZone

/**
 * 搜索筛选页：关键词即时搜索（250ms 防抖）+ 分类 / 标签 / 日期范围筛选，
 * 结果沿用流水页的按日分组展示。
 *
 * @param onBack 返回上一页。
 * @param onEdit 点击结果条目进入编辑。
 */
@Composable
fun SearchScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = appViewModel<SearchViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        SearchInputRow(
            keyword = state.keyword,
            onKeywordChange = viewModel::onKeywordChange,
            onClear = { viewModel.clearKeyword() },
            onBack = onBack
        )
        FilterSection(
            state = state,
            onCategoryChange = viewModel::setCategory,
            onTagToggle = viewModel::toggleTag,
            onRangeChange = viewModel::setRange,
            onPickStartDate = { showStartPicker = true },
            onPickEndDate = { showEndPicker = true },
            onReset = { viewModel.resetFilters() }
        )
        HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
        ResultSummary(
            count = state.totalCount,
            expenseMinor = state.expenseMinor
        )
        if (state.noResult) {
            NoResultHint(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
            ) {
                state.groups.forEach { group ->
                    stickyHeader(key = "search_day_${group.dayStart}") {
                        DayGroupHeader(group = group)
                    }
                    items(items = group.items, key = { it.id }) { tx ->
                        TransactionRowItem(
                            tx = tx,
                            category = state.categoryById[tx.categoryId],
                            selectionMode = false,
                            checked = false,
                            onClick = { onEdit(tx.id) },
                            onLongClick = { onEdit(tx.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        RangeDateDialog(
            title = "选择起始日期",
            initialMillis = state.filters.customStart,
            onDismiss = { showStartPicker = false },
            onConfirm = { dayStart ->
                if (dayStart != null) viewModel.setCustomStart(dayStart)
                showStartPicker = false
                showEndPicker = true
            }
        )
    }
    if (showEndPicker) {
        RangeDateDialog(
            title = "选择截止日期",
            initialMillis = state.filters.customEnd ?: state.filters.customStart,
            onDismiss = { showEndPicker = false },
            onConfirm = { dayStart ->
                if (dayStart != null) viewModel.setCustomEnd(dayStart)
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun SearchInputRow(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "‹ 返回",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 76.dp),
            placeholder = { Text(text = "搜索备注、标签或金额") },
            leadingIcon = { Text(text = "🔍", fontSize = 15.sp) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(text = "✕", style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}

@Composable
private fun FilterSection(
    state: SearchUiState,
    onCategoryChange: (Long?) -> Unit,
    onTagToggle: (String) -> Unit,
    onRangeChange: (SearchRangeOption) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) {
                Text(
                    text = "重置",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDropdownChip(
                label = state.categoryLabel,
                active = state.filters.categoryId != null,
                categories = state.categories,
                selectedId = state.filters.categoryId,
                onPick = onCategoryChange
            )
            SearchRangeOption.entries.forEach { option ->
                val chipLabel =
                    if (option == SearchRangeOption.Custom && state.customRangeHint.isNotEmpty()) {
                        "自定义 ${state.customRangeHint}"
                    } else {
                        option.label
                    }
                FilterChip(
                    selected = state.filters.range == option,
                    onClick = { onRangeChange(option) },
                    label = { Text(text = chipLabel, style = MaterialTheme.typography.labelMedium) }
                )
            }
            if (state.filters.range == SearchRangeOption.Custom) {
                FilterChip(
                    selected = state.filters.customStart != null,
                    onClick = onPickStartDate,
                    label = {
                        Text(
                            text = state.filters.customStart?.let { "起 ${Dates.dayLabel(it)}" }
                                ?: "选起始日",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                FilterChip(
                    selected = state.filters.customEnd != null,
                    onClick = onPickEndDate,
                    label = {
                        Text(
                            text = state.filters.customEnd?.let { "止 ${Dates.dayLabel(it)}" }
                                ?: "选截止日",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        if (state.tagOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                state.tagOptions.forEach { tag ->
                    FilterChip(
                        selected = state.filters.tag == tag,
                        onClick = { onTagToggle(tag) },
                        label = { Text(text = "#$tag", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

/** 分类筛选入口：点开后是「全部分类」加现有分类（emoji + 名称）。 */
@Composable
private fun CategoryDropdownChip(
    label: String,
    active: Boolean,
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onPick: (Long?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "🗂 全部分类", style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onPick(null)
                    expanded = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${category.icon} ${category.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (category.id == selectedId) colors.primary else colors.onSurface
                        )
                    },
                    onClick = {
                        onPick(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(count: Int, expenseMinor: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "共 $count 笔",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "支出 ${Money.format(expenseMinor)}",
            style = MaterialTheme.typography.labelMedium,
            color = ExpenseRed
        )
    }
}

@Composable
private fun NoResultHint(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🔍", fontSize = 44.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "没有符合条件的账单",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "换个关键词，或放宽筛选条件试试",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

/** Material3 日期选择对话框；确认时把 UTC 零点毫秒换成本地当天零点。 */
@Composable
private fun RangeDateDialog(
    title: String,
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val fallback = Dates.startOfDay(System.currentTimeMillis())
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = toUtcDayMillis(initialMillis ?: fallback)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = pickerState.selectedDateMillis
                onConfirm(selected?.let { SearchViewModel.utcMillisToLocalDayStart(it) })
            }) {
                Text(text = "确定", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

/** 本地当天毫秒 -> UTC 当天零点毫秒（DatePicker 内部按 UTC 解释日期）。 */
private fun toUtcDayMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}
