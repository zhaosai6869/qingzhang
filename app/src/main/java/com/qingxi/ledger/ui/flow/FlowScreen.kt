package com.qingxi.ledger.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.common.SwipeableDeleteRow
import com.qingxi.ledger.ui.recurring.PendingRecurringBanner
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Money

/**
 * 账单流水页：月份翻页 + 当月汇总 + 按日分组列表 + 左滑露出删除按钮 + 长按多选批量删除。
 * 所有删除都要二次确认，确认后不可撤销。
 * 顶部「待确认的周期支出」横幅由冷启动算出来的到期项驱动，确认后才变成流水。
 * 悬浮「记一笔」按钮由导航壳底栏提供，本页不再放 FAB。
 *
 * @param onRecord 跳转记一笔。
 * @param onEdit 点击某笔账单进入编辑。
 * @param onSearch 跳转搜索筛选页。
 * @param onOpenPending 跳转周期支出的待确认清单。
 */
@Composable
fun FlowScreen(
    onRecord: () -> Unit,
    onEdit: (Long) -> Unit,
    onSearch: () -> Unit,
    onOpenPending: () -> Unit
) {
    val viewModel = appViewModel<FlowViewModel>()
    val graph = LocalAppGraph.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.uiMessage.collectAsStateWithLifecycle()
    val pending by graph.pending.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var pendingDelete by remember { mutableStateOf<List<TransactionEntity>?>(null) }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        viewModel.onMessageShown()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FlowTopBar(
                state = state,
                viewModel = viewModel,
                onSearch = onSearch,
                onDeleteSelected = { pendingDelete = state.selectedItems }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()
        if (state.isEmpty && pending.isEmpty()) {
            FlowEmptyState(
                onRecord = onRecord,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 80.dp
                )
            ) {
                if (pending.isNotEmpty()) {
                    item(key = "recurring_pending_banner") {
                        PendingRecurringBanner(
                            count = pending.size,
                            totalMinor = pending.sumOf { it.amountMinor },
                            onClick = onOpenPending,
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                }
                state.groups.forEach { group ->
                    stickyHeader(key = "day_${group.dayStart}") {
                        DayGroupHeader(group = group)
                    }
                    items(items = group.items, key = { it.id }) { tx ->
                        val category = state.categoryById[tx.categoryId]
                        SwipeableDeleteRow(
                            resetKey = tx.id,
                            swipeEnabled = !state.selectionMode,
                            deleteLabel = "删除",
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(tx.id) else onEdit(tx.id)
                            },
                            onDelete = { pendingDelete = listOf(tx) }
                        ) { rowModifier, onRowClick ->
                            TransactionRowItem(
                                tx = tx,
                                category = category,
                                selectionMode = state.selectionMode,
                                checked = tx.id in state.selectedIds,
                                onClick = onRowClick,
                                onLongClick = {
                                    if (state.selectionMode) {
                                        viewModel.toggleSelection(tx.id)
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.startSelection(tx.id)
                                    }
                                },
                                modifier = rowModifier
                            )
                        }
                    }
                }
            }
        }
    }

    val targets = pendingDelete
    if (!targets.isNullOrEmpty()) {
        DeleteConfirmDialog(
            count = targets.size,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                viewModel.deleteTransactions(targets)
            }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (count > 1) "删除这 $count 笔记录？" else "删除这笔记录？", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = "删除后无法恢复，附件图片也会一并清掉。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除", style = MaterialTheme.typography.labelLarge, color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun FlowTopBar(
    state: FlowUiState,
    viewModel: FlowViewModel,
    onSearch: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = colors.background
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.selectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    onSelectAll = { viewModel.selectAll(state.allIds) },
                    onDeleteSelected = onDeleteSelected,
                    onCancel = { viewModel.cancelSelection() }
                )
            } else {
                MonthTopBar(
                    state = state,
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onBackToCurrentMonth = { viewModel.goToCurrentMonth() },
                    onSearch = onSearch
                )
            }
            MonthSummaryCard(state = state)
        }
    }
}

@Composable
private fun MonthTopBar(
    state: FlowUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBackToCurrentMonth: () -> Unit,
    onSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(symbol = "‹", label = "上一月", onClick = onPreviousMonth)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.clickable(
                        enabled = !state.isCurrentMonth,
                        onClickLabel = "回到本月",
                        onClick = onBackToCurrentMonth
                    )
                )
                if (state.isCurrentMonth) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ) {
                        Text(
                            text = "本月",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        BarButton(symbol = "›", label = "下一月", onClick = onNextMonth)
        BarButton(symbol = "🔍", label = "搜索账单", onClick = onSearch)
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp)
        )
        TextButton(onClick = onSelectAll) {
            Text(text = "全选", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onDeleteSelected) {
            Text(text = "删除", style = MaterialTheme.typography.labelLarge, color = ExpenseRed)
        }
        TextButton(onClick = onCancel) {
            Text(text = "取消", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarButton(symbol: String, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 19.sp,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthSummaryCard(state: FlowUiState) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryCell(
                    title = "本月支出",
                    value = Money.format(state.expenseMinor),
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f)
                )
                SummaryCell(
                    title = "本月笔数",
                    value = "${state.count}",
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "日均支出 ${Money.format(state.dailyAverageMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            if (state.count > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "日均按 ${state.dayDivisor} 天计算",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun FlowEmptyState(onRecord: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🗒", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "这个月还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "翻到别的月份看看，或者记下第一笔",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRecord) {
            Text(text = "记一笔", style = MaterialTheme.typography.labelLarge)
        }
    }
}
