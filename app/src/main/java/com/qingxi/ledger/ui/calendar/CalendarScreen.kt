package com.qingxi.ledger.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.ui.common.AmountDisplay
import com.qingxi.ledger.util.Money

/**
 * 日历视图页：月度支出热力网格 + 选中日的流水明细。
 *
 * @param onRecord 跳转到记账页的回调，由导航壳提供。
 */
@Composable
fun CalendarScreen(onRecord: () -> Unit) {
    val viewModel = appViewModel<CalendarViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp, bottom = 110.dp)
        ) {
            Text(
                text = "日历",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
            )

            MonthSummaryCard(
                state = state,
                onPreviousMonth = { viewModel.shiftMonth(-1) },
                onNextMonth = { viewModel.shiftMonth(1) },
                onToday = viewModel::goToday
            )

            Spacer(Modifier.height(14.dp))

            WeekdayHeader(startOnSunday = state.weekStartsOnSunday)
            Spacer(Modifier.height(4.dp))
            MonthGrid(
                cells = state.cells,
                selectedDayStart = state.selectedDayStart,
                onSelect = viewModel::selectDay
            )

            Spacer(Modifier.height(16.dp))

            SelectedDayCard(state = state, onRecord = onRecord)
        }

        QuickAddButton(
            onClick = onRecord,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp)
        )
    }
}

@Composable
private fun MonthSummaryCard(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrowButton(label = "‹", onClick = onPreviousMonth)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${state.year}年${state.month}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "本月 ${state.monthCount} 笔记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                MonthArrowButton(label = "›", onClick = onNextMonth, enabled = !state.isCurrentMonth)
                PlainActionButton(text = "今天", onClick = onToday)
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.padding(horizontal = 10.dp)) {
                AmountCell(
                    label = "支出",
                    amountMinor = state.monthExpenseMinor,
                    modifier = Modifier.weight(1f)
                )
                CountCell(
                    label = "笔数",
                    value = state.monthCount,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthArrowButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun PlainActionButton(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.primary)
    }
}

@Composable
private fun AmountCell(
    label: String,
    amountMinor: Long,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "¥${AmountDisplay.format(amountMinor)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (amountMinor > 0L) ExpenseRed else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekdayHeader(startOnSunday: Boolean) {
    val colors = MaterialTheme.colorScheme
    val labels = Dates.weekdayLabels(startOnSunday)
    val sundayIndex = if (startOnSunday) 0 else 6
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val isWeekend = index == sundayIndex || index == (sundayIndex + 6) % 7
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant.copy(alpha = if (isWeekend) 0.80f else 1f)
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    cells: List<CalendarDayCell>,
    selectedDayStart: Long?,
    onSelect: (CalendarDayCell) -> Unit
) {
    Column {
        cells.chunked(COLUMNS).forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowCells.forEach { cell ->
                    DayCell(
                        cell = cell,
                        selected = cell.dayStart == selectedDayStart,
                        onSelect = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .height(CELL_HEIGHT_DP.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarDayCell,
    selected: Boolean,
    onSelect: (CalendarDayCell) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val numberColor = when {
        !cell.inMonth -> colors.onSurfaceVariant.copy(alpha = 0.40f)
        cell.isToday -> colors.primary
        else -> colors.onSurface
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(shape)
            .background(heatColor(cell.heatLevel, colors.primary))
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) colors.primary else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = { onSelect(cell) }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (cell.isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = numberColor
            )
            when {
                cell.hasRecord && cell.inMonth -> {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = compactCellAmount(cell.expenseMinor),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (cell.heatLevel >= 4) colors.onSurface else colors.onSurfaceVariant
                    )
                }
                cell.hasRecord -> {
                    Spacer(Modifier.height(5.dp))
                    Canvas(modifier = Modifier.size(4.dp)) {
                        drawCircle(color = colors.outline.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/** 热力档位染色：只用 primary + 透明度，深浅色模式都可读。 */
private fun heatColor(level: Int, primary: Color): Color = when (level) {
    1 -> primary.copy(alpha = 0.10f)
    2 -> primary.copy(alpha = 0.22f)
    3 -> primary.copy(alpha = 0.38f)
    4 -> primary.copy(alpha = 0.58f)
    else -> Color.Transparent
}

/** 格子里的紧凑金额：万位以上折成「x.x万」，小额保留一位角分。 */
private fun compactCellAmount(minor: Long): String {
    val safe = if (minor < 0L) 0L else minor
    return when {
        safe >= 1_000_000L -> {
            val tenths = safe / 100_000L
            "${tenths / 10}.${tenths % 10}万"
        }
        safe >= 10_000L -> "${(safe + 50L) / 100L}"
        else -> {
            val yuan = safe / 100L
            val tenths = (safe % 100L) / 10L
            if (tenths == 0L) "$yuan" else "$yuan.$tenths"
        }
    }
}

@Composable
private fun SelectedDayCard(state: CalendarUiState, onRecord: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.selectedDayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.selectedWeekdayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
                    .padding(vertical = 10.dp)
            ) {
                Row {
                    AmountCell(
                        label = "支出",
                        amountMinor = state.selectedExpenseMinor,
                        modifier = Modifier.weight(1f)
                    )
                    CountCell(
                        label = "笔数",
                        value = state.selectedRecords.size,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.selectedRecords.isEmpty()) {
                EmptyDay(onRecord = onRecord)
            } else {
                state.selectedRecords.forEachIndexed { index, record ->
                    DayRecordRow(record = record)
                    if (index != state.selectedRecords.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.outline.copy(alpha = 0.20f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountCell(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (value > 0) colors.onSurface else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun DayRecordRow(record: DayRecordItem) {
    val colors = MaterialTheme.colorScheme
    val detail = recordDetail(record)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = record.icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.categoryName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1
            )
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.formatSigned(record.amountMinor),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = ExpenseRed,
                maxLines = 1
            )
            Text(
                text = Dates.timeLabel(record.timeMillis),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

private fun recordDetail(record: DayRecordItem): String {
    val note = record.note.trim()
    val tags = if (record.tags.isEmpty()) "" else record.tags.joinToString(" ") { "#$it" }
    return listOf(note, tags).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun EmptyDay(onRecord: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "这一天还没有记账",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.12f))
                .clickable(onClick = onRecord)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = "记一笔", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}

@Composable
private fun QuickAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = colors.onPrimary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary
            )
        }
    }
}

private const val COLUMNS = 7
private const val CELL_HEIGHT_DP = 62
