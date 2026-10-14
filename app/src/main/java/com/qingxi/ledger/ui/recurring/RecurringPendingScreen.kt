package com.qingxi.ledger.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.recurring.RecurringPendingItem
import com.qingxi.ledger.data.recurring.RecurringPendingStore
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.ui.settings.CenteredHint
import com.qingxi.ledger.ui.settings.NoteText
import com.qingxi.ledger.ui.settings.SectionCard
import com.qingxi.ledger.ui.settings.SubPageShell
import com.qingxi.ledger.ui.settings.ThinDivider
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.Money
import kotlinx.coroutines.launch

/**
 * 冷启动算出来的到期清单：这里的东西还不是花出去的钱，
 * 只有点了确认才会写进账单，跳过则永远不再列出。
 */
@Composable
fun RecurringPendingScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val items by graph.pending.items.collectAsStateWithLifecycle()
    val categories by graph.categories.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val categoryById = remember(categories) { categories.associateBy { it.id } }
    var busy by remember { mutableStateOf(false) }

    SubPageShell(title = "待确认的周期支出", onBack = onBack, snackbarHostState = snackbarHostState) {
        NoteText(
            text = "只列出最近 ${RecurringPendingStore.LOOKBACK_DAYS} 天内到期的项。确认后才写进账单，跳过表示这笔不记，之后不会再提醒。",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (items.isEmpty()) {
            CenteredHint(text = "没有待确认的到期项")
        } else {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "全部确认（${items.size} 笔 · ¥${Money.format(items.sumOf { it.amountMinor })}）",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val inserted = runCatching { graph.pending.confirm(items) }
                                    .getOrDefault(0)
                                busy = false
                                snackbarHostState.showSnackbar(
                                    if (inserted > 0) "已入账 $inserted 笔" else "这些已经在账单里了"
                                )
                            }
                        }
                    ) {
                        Text(text = "确认", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                items.forEachIndexed { index, item ->
                    if (index > 0) ThinDivider()
                    PendingItemRow(
                        item = item,
                        category = categoryById[item.rule.categoryId],
                        busy = busy,
                        onConfirm = {
                            busy = true
                            scope.launch {
                                val inserted = runCatching { graph.pending.confirm(listOf(item)) }
                                    .getOrDefault(0)
                                busy = false
                                snackbarHostState.showSnackbar(
                                    if (inserted > 0) "已记入账单" else "这笔已经在账单里了"
                                )
                            }
                        },
                        onSkip = {
                            busy = true
                            scope.launch {
                                runCatching { graph.pending.skip(item) }
                                busy = false
                                snackbarHostState.showSnackbar("${Dates.monthDayLabel(item.dueDayStart)} 这笔已跳过，之后不再提醒")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingItemRow(
    item: RecurringPendingItem,
    category: CategoryEntity?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp, horizontal = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Dates.dayLabel(item.dueDayStart),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = Dates.weekdayLabel(item.dueDayStart),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "¥${Money.format(item.amountMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = category?.icon ?: "❓", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = detailLine(item, category),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(enabled = !busy, onClick = onSkip) {
                Text(
                    text = "跳过",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(text = "确认入账", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 一行把规则来源说清楚：分类名 + 周期，附上备注和标签。 */
private fun detailLine(item: RecurringPendingItem, category: CategoryEntity?): String {
    val parts = mutableListOf("${category?.name ?: "未分类"} · ${describeRule(item.rule)}")
    val note = item.rule.note.trim()
    if (note.isNotEmpty()) parts += note
    val tags = splitTags(item.rule.tags)
    if (tags.isNotEmpty()) parts += tags.joinToString("、") { "#$it" }
    return parts.joinToString(" · ")
}

/** 流水页和规则页共用入口：把待确认的笔数和合计说清楚，点开进本页面。 */
@Composable
internal fun PendingRecurringBanner(
    count: Int,
    totalMinor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "查看待确认的周期支出", onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔁", fontSize = 17.sp)
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = "$count 笔周期支出待确认 · 合计 ¥${Money.format(totalMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "›", fontSize = 18.sp)
        }
    }
}
