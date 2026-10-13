package com.qingxi.ledger.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.tx.CategoryStat
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.ui.common.AmountDisplay
import com.qingxi.ledger.util.Money

/**
 * C5 年度报告：自然年支出总览、月度趋势、分类与标签排行、年度「名场面」。
 *
 * @param onBack 返回统计页。
 * @param onOpenCategory 下钻某分类在该年度的明细。
 */
@Composable
fun AnnualReportScreen(
    onBack: () -> Unit,
    onOpenCategory: (CategoryDrill) -> Unit
) {
    val viewModel = appViewModel<AnnualReportViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val palette = chartPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 4.dp),
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
                text = "年度报告",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.availableYears.isNotEmpty()) {
            YearChips(
                years = state.availableYears,
                selected = state.year,
                onSelect = viewModel::selectYear
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            ChartEmptyState("正在生成年度报告…")
            return@Column
        }
        if (!state.hasData) {
            ChartEmptyState("${state.year} 年还没有任何记录", height = 200.dp)
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp)
        ) {
            HeroCard(state = state)
            Spacer(Modifier.height(12.dp))
            MonthTrendCard(state = state)
            Spacer(Modifier.height(12.dp))
            CategoryRankCard(
                title = "支出榜",
                stats = state.topExpense,
                totalMinor = state.expenseMinor,
                palette = palette,
                state = state,
                onOpenCategory = onOpenCategory
            )
            Spacer(Modifier.height(12.dp))
            HighlightsCard(state = state)
        }
    }
}

@Composable
private fun YearChips(years: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        years.forEach { year ->
            val active = year == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant
                    )
                    .clickable { onSelect(year) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "${year}年",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeroCard(state: AnnualReportUiState) {
    val colors = MaterialTheme.colorScheme
    ReportCard {
        Text(
            text = "${state.year} 年，你一共记了 ${state.count} 笔",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "全年支出",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        Text(
            text = "¥${AmountDisplay.format(state.expenseMinor)}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ExpenseRed,
            maxLines = 1
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .padding(vertical = 10.dp)
        ) {
            Row {
                HeroMetric("日均支出", "¥${AmountDisplay.format(state.dailyAverageMinor)}", Modifier.weight(1f))
                HeroMetric("记账天数", "${state.activeDays} 天", Modifier.weight(1f))
                HeroMetric("涉及分类", "${state.categoryCount} 个", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

@Composable
private fun MonthTrendCard(state: AnnualReportUiState) {
    val colors = MaterialTheme.colorScheme
    ReportCard {
        Text(
            text = "月度支出",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Spacer(Modifier.height(10.dp))
        MonthTrendChart(points = state.monthPoints)
    }
}

@Composable
private fun CategoryRankCard(
    title: String,
    stats: List<CategoryStat>,
    totalMinor: Long,
    palette: List<Color>,
    state: AnnualReportUiState,
    onOpenCategory: (CategoryDrill) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ReportCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Spacer(Modifier.height(4.dp))
        if (stats.isEmpty()) {
            Text(
                text = "这一年没有记录",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            return@ReportCard
        }
        stats.forEachIndexed { index, stat ->
            val fraction = if (totalMinor > 0L) stat.totalMinor.toFloat() / totalMinor.toFloat() else 0f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        onOpenCategory(
                            CategoryDrill(
                                categoryId = stat.category.id,
                                start = Dates.startOfMonth(state.year, 1),
                                end = Dates.endOfMonth(state.year, 12)
                            )
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (index < 3) listOf("🥇", "🥈", "🥉")[index] else "${index + 1}",
                        fontSize = 14.sp,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(text = stat.category.icon, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stat.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "¥${AmountDisplay.format(stat.totalMinor)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatioBar(
                        fraction = fraction,
                        color = palette[index % palette.size],
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stat.count} 笔 · ${shareOf(stat.totalMinor, totalMinor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = "点击查看分类明细",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun HighlightsCard(state: AnnualReportUiState) {
    val colors = MaterialTheme.colorScheme
    ReportCard {
        Text(
            text = "年度名场面",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        val largest = state.largest
        if (largest != null) {
            HighlightRow(
                emoji = "💸",
                title = "最大一笔",
                desc = "¥${Money.format(largest.amountMinor)} · ${Dates.dayLabel(largest.timeMillis)}" +
                    if (largest.note.isNotBlank()) " · ${largest.note.trim()}" else ""
            )
        }
        val busiest = state.busiestDay
        if (busiest != null) {
            HighlightRow(
                emoji = "🔥",
                title = "最忙的一天",
                desc = "${Dates.dayLabel(busiest.dayStart)} 记了 ${busiest.count} 笔，支出 ¥${AmountDisplay.format(busiest.expenseMinor)}"
            )
        }
        val heaviest = state.heaviestMonth
        if (heaviest != null) {
            HighlightRow(
                emoji = "📈",
                title = "支出最多的月",
                desc = "${monthName(heaviest.monthKey)} 支出 ¥${AmountDisplay.format(heaviest.expenseMinor)}"
            )
        }
        val lightest = state.lightestMonth
        if (lightest != null) {
            HighlightRow(
                emoji = "🍃",
                title = "最省的月",
                desc = "${monthName(lightest.monthKey)} 支出 ¥${AmountDisplay.format(lightest.expenseMinor)}"
            )
        }
        if (state.topTags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "这一年的标签",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.topTags.forEach { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${tag.tag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${tag.count} 笔 · ¥${AmountDisplay.format(tag.totalMinor)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightRow(emoji: String, title: String, desc: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = emoji, fontSize = 16.sp, modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReportCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), content = content)
    }
}

/** 部分占整体的百分比文案，分母为 0 时返回 0%。 */
private fun shareOf(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val tenths = ((part.toFloat() / total.toFloat()).coerceIn(0f, 1f) * 1000f).toInt()
    return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}

private fun monthName(monthKey: String): String {
    val month = monthKey.substringAfter('-', "").toIntOrNull() ?: return monthKey
    return "${month}月"
}
