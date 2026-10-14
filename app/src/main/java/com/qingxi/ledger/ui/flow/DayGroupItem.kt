package com.qingxi.ledger.ui.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.tx.DayGroup
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.splitPaths
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.Money

private const val FALLBACK_ICON = "🏷"
private const val FALLBACK_NAME = "未分类"
private const val MAX_VISIBLE_TAGS = 3

/**
 * 纯函数：把若干流水按自然日分组，组间按日期倒序、组内保持入参顺序（DAO 已按时间倒序）。
 * 流水页与搜索页共用同一套分组与展示逻辑。
 */
fun buildDayGroups(items: List<TransactionEntity>): List<DayGroup> {
    if (items.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<Long, MutableList<TransactionEntity>>()
    for (tx in items) {
        val dayStart = Dates.startOfDay(tx.timeMillis)
        buckets.getOrPut(dayStart) { mutableListOf() }.add(tx)
    }
    return buckets.entries
        .sortedByDescending { it.key }
        .map { entry ->
            DayGroup(
                dayStart = entry.key,
                label = Dates.dayLabel(entry.key),
                expenseMinor = entry.value.sumOf { it.amountMinor },
                items = entry.value
            )
        }
}

/** 某一日的分组头，用作 LazyColumn 的 stickyHeader 内容。 */
@Composable
fun DayGroupHeader(
    group: DayGroup,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = Dates.weekdayLabel(group.dayStart),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "支出 ${Money.format(group.expenseMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = ExpenseRed
            )
        }
    }
}

/**
 * 单笔账单条目：分类 emoji + 分类名、备注、标签、附件数、时间与带符号金额。
 * 多选模式下左侧出现勾选框，点击用于勾选；普通模式点击进入编辑。
 */
@Composable
fun TransactionRowItem(
    tx: TransactionEntity,
    category: CategoryEntity?,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val note = tx.note.trim()
    val tags = splitTags(tx.tags)
    val attachmentCount = splitPaths(tx.attachments).size

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (checked) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface
            )
            .combinedClickable(
                enabled = true,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = category?.icon ?: FALLBACK_ICON,
            fontSize = 22.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: FALLBACK_NAME,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tags.take(MAX_VISIBLE_TAGS).forEach { tag ->
                        TagChip(text = "#$tag")
                    }
                    if (tags.size > MAX_VISIBLE_TAGS) {
                        TagChip(text = "+${tags.size - MAX_VISIBLE_TAGS}")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.formatSigned(tx.amountMinor),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tx.ruleId != null) {
                    Text(
                        text = "🔁",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (attachmentCount > 0) {
                    Text(
                        text = "📎$attachmentCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = Dates.timeLabel(tx.timeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

/** 小标签胶囊，仅用于展示。 */
@Composable
private fun TagChip(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 2.dp)
        )
    }
}
