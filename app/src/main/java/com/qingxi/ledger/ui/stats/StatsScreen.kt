package com.qingxi.ledger.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.common.AmountDisplay
import com.qingxi.ledger.ui.theme.ExpenseRed

/**
 * 统计图表页：区间概览 + 支出趋势（自绘柱状 / 折线）+ 分类占比（自绘环形）+ 分类排行（自绘比例条）。
 *
 * @param onOpenCategory 分类下钻到明细页。
 * @param onOpenAnnualReport 打开 C5 年度报告。
 */
@Composable
fun StatsScreen(
    onOpenCategory: (CategoryDrill) -> Unit,
    onOpenAnnualReport: () -> Unit
) {
    val viewModel = appViewModel<StatsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val palette = chartPalette()
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.categoryStats) {
        val current = selectedCategoryId ?: return@LaunchedEffect
        if (state.categoryStats.none { it.category.id == current }) selectedCategoryId = null
    }

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
                .padding(top = 6.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp, bottom = 10.dp)
                )
                AnnualReportLink(onOpenAnnualReport)
            }

            RangeSwitch(selected = state.range, onRangeChange = viewModel::selectRange)

            Spacer(Modifier.height(12.dp))

            OverviewCard(state = state)

            Spacer(Modifier.height(12.dp))

            TrendCard(state = state)

            Spacer(Modifier.height(12.dp))

            ShareCard(
                state = state,
                palette = palette,
                selectedCategoryId = selectedCategoryId,
                onSelect = { selectedCategoryId = it }
            )

            Spacer(Modifier.height(12.dp))

            RankingCard(
                state = state,
                palette = palette,
                selectedCategoryId = selectedCategoryId,
                onOpenCategory = onOpenCategory
            )
        }
    }
}

@Composable
private fun RangeSwitch(selected: StatsRange, onRangeChange: (StatsRange) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsRange.entries.forEach { range ->
            val active = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.surface)
                    .border(
                        width = 1.dp,
                        color = if (active) colors.primary else colors.outline.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = { onRangeChange(range) })
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "区间总支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "¥${AmountDisplay.format(state.totalMinor)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed,
                    maxLines = 1
                )
            }
            ChangeBadge(state = state)
        }
        Text(
            text = state.periodLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
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
                MetricCell(label = "笔数", value = "${state.recordCount} 笔", modifier = Modifier.weight(1f))
                MetricCell(
                    label = "日均支出",
                    value = "¥${AmountDisplay.format(state.dailyAverageMinor)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChangeBadge(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    val ratio = state.changeRatio
    val color = when {
        ratio == null -> colors.onSurfaceVariant
        ratio > 0f -> ExpenseRed
        else -> colors.primary
    }
    val text = if (ratio == null) {
        "暂无对比"
    } else {
        val sign = if (ratio > 0f) "+" else "-"
        "较上期 $sign${percentText(absoluteOfF(ratio))}"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun TrendCard(state: StatsUiState) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "支出趋势",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (state.range.months == 1) "按日" else "按月对比",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> ChartEmptyState("正在统计…")
            state.range.months == 1 -> {
                if (state.dayTrend.isEmpty()) {
                    ChartEmptyState("本月还没有记录")
                } else {
                    DayTrendChart(points = state.dayTrend)
                }
            }
            state.monthTrend.isEmpty() -> ChartEmptyState("这段时间还没有记录")
            else -> MonthTrendChart(points = state.monthTrend)
        }
    }
}

@Composable
private fun ShareCard(
    state: StatsUiState,
    palette: List<Color>,
    selectedCategoryId: Long?,
    onSelect: (Long?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        SectionHeader(title = "分类占比", subtitle = state.range.label)
        Spacer(Modifier.height(10.dp))

        if (state.isLoading) {
            ChartEmptyState("正在统计…")
            return@SectionCard
        }
        val visible = state.categoryStats.take(MAX_DONUT_SLICES)
        if (visible.isEmpty()) {
            ChartEmptyState("这段时间还没有支出")
            return@SectionCard
        }

        val slices = visible.mapIndexed { index, stat ->
            ChartSlice(
                key = stat.category.id,
                label = stat.category.name,
                valueMinor = stat.totalMinor,
                color = palette[index % palette.size]
            )
        }
        val selected = state.categoryStats.firstOrNull { it.category.id == selectedCategoryId }
        val centerTitle = selected?.category?.name ?: "总支出"
        val centerAmount = selected?.totalMinor ?: state.totalMinor
        val centerHint = if (selected != null) {
            "占比 ${shareText(selected.totalMinor, state.totalMinor)}"
        } else {
            "共 ${state.recordCount} 笔"
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryDonutChart(
                slices = slices,
                centerTitle = centerTitle,
                centerValue = "¥${chartAmountLabel(centerAmount)}",
                centerHint = centerHint,
                selectedKey = selectedCategoryId,
                onSliceSelected = onSelect,
                diameter = 132.dp,
                ringWidth = 22.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                slices.forEach { slice ->
                    LegendRow(
                        label = slice.label,
                        value = shareText(slice.valueMinor, state.totalMinor),
                        color = slice.color,
                        highlighted = selectedCategoryId == slice.key,
                        onClick = { onSelect(if (selectedCategoryId == slice.key) null else slice.key) }
                    )
                }
                if (state.categoryStats.size > visible.size) {
                    Text(
                        text = "另有 ${state.categoryStats.size - visible.size} 个分类未显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    label: String,
    value: String,
    color: Color,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = colors.onSurface
        )
    }
}

@Composable
private fun RankingCard(
    state: StatsUiState,
    palette: List<Color>,
    selectedCategoryId: Long?,
    onOpenCategory: (CategoryDrill) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        SectionHeader(title = "分类排行", subtitle = "按支出金额排序")
        Spacer(Modifier.height(6.dp))

        if (state.isLoading) {
            ChartEmptyState("正在统计…")
            return@SectionCard
        }
        if (state.categoryStats.isEmpty()) {
            ChartEmptyState("这个区间没有可统计的记录")
            return@SectionCard
        }

        state.categoryStats.forEachIndexed { index, stat ->
            val color = palette[index % palette.size]
            val highlighted = selectedCategoryId == stat.category.id
            val fraction = if (state.maxAmountMinor > 0L) {
                stat.totalMinor.toFloat() / state.maxAmountMinor.toFloat()
            } else {
                0f
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (highlighted) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable {
                        onOpenCategory(
                            CategoryDrill(
                                categoryId = stat.category.id,
                                start = state.periodStart,
                                end = state.periodEnd
                            )
                        )
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stat.category.icon, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stat.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "¥${AmountDisplay.format(stat.totalMinor)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (highlighted) colors.primary else colors.onSurface,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatioBar(fraction = fraction, color = color, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stat.count} 笔 · ${shareText(stat.totalMinor, state.totalMinor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = "点击分类查看该区间明细",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AnnualReportLink(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.primary.copy(alpha = 0.12f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "年度报告",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
    ) {}
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
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

private fun absoluteOfF(value: Float): Float = if (value < 0f) -value else value

/** 百分比文案（一位小数），分母为 0 时返回 0%。 */
private fun percentText(absRatio: Float): String {
    val tenths = (absRatio * 1000f).toInt()
    return if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"
}

private fun shareText(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    return percentText(absoluteOfF(part.toFloat() / total.toFloat()))
}

private const val MAX_DONUT_SLICES = 6
