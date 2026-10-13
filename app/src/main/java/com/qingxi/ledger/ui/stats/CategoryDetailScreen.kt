package com.qingxi.ledger.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.flow.DayGroupHeader
import com.qingxi.ledger.ui.flow.TransactionRowItem
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.ui.common.AmountDisplay
import com.qingxi.ledger.util.Money

/**
 * 分类下钻明细页：展示该分类在所选区间内的汇总与按日分组的流水。
 *
 * @param request 下钻条件（分类、区间）。
 * @param onBack 返回统计页。
 * @param onEdit 点击某笔进入编辑。
 */
@Composable
fun CategoryDetailScreen(
    request: CategoryDrill,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val viewModel = appViewModel<CategoryDetailViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(request) { viewModel.bind(request) }

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "‹ 返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "分类明细",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }

        SummaryBlock(state = state)
        HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp)

        if (state.isLoading) {
            ChartEmptyState("正在加载…", modifier = Modifier.weight(1f))
        } else if (state.isEmpty) {
            ChartEmptyState("这个区间该分类还没有记录", modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)
            ) {
                state.groups.forEach { group ->
                    stickyHeader(key = "detail_day_${group.dayStart}") {
                        DayGroupHeader(group = group)
                    }
                    items(group.items, key = { it.id }) { tx ->
                        TransactionRowItem(
                            tx = tx,
                            category = state.category,
                            selectionMode = false,
                            checked = false,
                            onClick = { onEdit(tx.id) },
                            onLongClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(state: CategoryDetailUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.category?.icon ?: "🏷",
                fontSize = 26.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.category?.name ?: "已删除分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                text = "-¥${AmountDisplay.format(state.totalMinor)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailMetric(label = "笔数", value = "${state.count}")
            DetailMetric(label = "平均每笔", value = "¥${AmountDisplay.format(state.averageMinor)}")
            DetailMetric(label = "占区间支出", value = percentOf(state.shareOfPeriod))
            DetailMetric(label = "有记录", value = "${state.activeDays} 天")
        }
        val largest = state.largest
        if (largest != null) {
            Spacer(Modifier.height(8.dp))
            val note = largest.note.trim()
            Text(
                text = "最大一笔 ¥${Money.format(largest.amountMinor)} · ${Dates.dayLabel(largest.timeMillis)}" +
                    if (note.isNotEmpty()) " · $note" else "",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 0..1 的比例转成百分比文案（一位小数）。 */
private fun percentOf(ratio: Float): String {
    val tenths = (ratio.coerceIn(0f, 1f) * 1000f).toInt()
    return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}
