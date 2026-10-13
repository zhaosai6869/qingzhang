package com.qingxi.ledger.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingxi.ledger.data.tx.MonthPoint
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.CENTS_PER_YUAN
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 全部用 Compose Canvas 自绘的图表组件：月度收支柱状图、日趋势面积折线、分类占比环形图、比例条。
 * 文字刻度使用 Compose 文本叠层，保证浅色/深色下都清晰可读。
 */

/** 环形图的一段。 */
data class ChartSlice(
    val key: Long,
    val label: String,
    val valueMinor: Long,
    val color: Color
)

/** 图表配色：只由 colorScheme 字段与支出色派生，靠透明度制造层次。 */
@Composable
fun chartPalette(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val secondary = MaterialTheme.colorScheme.secondary
    return listOf(
        primary,
        ExpenseRed,
        secondary,
        primary.copy(alpha = 0.70f),
        ExpenseRed.copy(alpha = 0.66f),
        secondary.copy(alpha = 0.62f),
        neutral,
        primary.copy(alpha = 0.42f),
        ExpenseRed.copy(alpha = 0.42f),
        secondary.copy(alpha = 0.40f),
        neutral.copy(alpha = 0.60f),
        primary.copy(alpha = 0.24f)
    )
}

/** 月度支出柱状图：每个月一根柱子。 */
@Composable
fun MonthTrendChart(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 168.dp
) {
    val colors = MaterialTheme.colorScheme
    val gridColor = colors.outline.copy(alpha = 0.32f)
    val labelColor = colors.onSurfaceVariant
    val maxValue = remember(points) { niceCeilMinor(points.maxOfOrNull { it.expenseMinor } ?: 0L) }
    val labelEvery = if (points.size > 8) 2 else 1

    Row(modifier = modifier.fillMaxWidth()) {
        AxisColumn(
            topLabel = chartAmountLabel(maxValue),
            middleLabel = chartAmountLabel(maxValue / 2),
            bottomLabel = "0",
            height = chartHeight
        )
        Column(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                drawHorizontalGrid(gridColor, rows = 2)
                if (points.isEmpty()) return@Canvas
                val slot = size.width / points.size
                val barWidth = (slot * 0.5f).coerceAtMost(22.dp.toPx())
                val corner = CornerRadius(3.dp.toPx())
                points.forEachIndexed { index, point ->
                    val center = slot * index + slot / 2f
                    val expenseHeight = ratioHeight(point.expenseMinor, maxValue, size.height)
                    if (expenseHeight > 0f) {
                        drawRoundRect(
                            color = ExpenseRed,
                            topLeft = Offset(center - barWidth / 2f, size.height - expenseHeight),
                            size = Size(barWidth, expenseHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEachIndexed { index, point ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (index % labelEvery == 0) {
                            Text(
                                text = monthLabelText(point.monthKey),
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 日趋势面积折线图。 */
@Composable
fun DayTrendChart(
    points: List<DayTrendPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp
) {
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.primary
    val gridColor = colors.outline.copy(alpha = 0.32f)
    val maxValue = remember(points) { niceCeilMinor(points.maxOfOrNull { it.valueMinor } ?: 0L) }
    val axisLabels = remember(points) { pickAxisLabels(points.map { it.label }) }

    Row(modifier = modifier.fillMaxWidth()) {
        AxisColumn(
            topLabel = chartAmountLabel(maxValue),
            middleLabel = chartAmountLabel(maxValue / 2),
            bottomLabel = "0",
            height = chartHeight
        )
        Column(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
                drawHorizontalGrid(gridColor, rows = 2)
                if (points.size < 2) return@Canvas
                val stepX = size.width / (points.size - 1)
                val line = Path()
                val area = Path()
                points.forEachIndexed { index, point ->
                    val x = stepX * index
                    val y = size.height - ratioHeight(point.valueMinor, maxValue, size.height * 0.9f) - size.height * 0.05f
                    if (index == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, size.height)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(stepX * (points.size - 1), size.height)
                area.close()
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
                    )
                )
                drawPath(path = line, color = lineColor, alpha = 1f, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                if (points.size <= 34) {
                    points.forEachIndexed { index, point ->
                        val x = stepX * index
                        val y = size.height - ratioHeight(point.valueMinor, maxValue, size.height * 0.9f) - size.height * 0.05f
                        drawCircle(
                            color = lineColor,
                            radius = if (point.valueMinor > 0L) 2.6.dp.toPx() else 1.6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                axisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 分类占比环形图，支持点击扇区选中。 */
@Composable
fun CategoryDonutChart(
    slices: List<ChartSlice>,
    centerTitle: String,
    centerValue: String,
    centerHint: String,
    selectedKey: Long?,
    onSliceSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    ringWidth: Dp = 26.dp
) {
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.surfaceVariant
    val totalValue = slices.sumOf { it.valueMinor }.coerceAtLeast(0L)
    val gapAngle = if (slices.size > 1) 2f else 0f

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices, totalValue, ringWidth) {
                    detectTapGestures { position ->
                        if (totalValue <= 0L) {
                            onSliceSelected(null)
                            return@detectTapGestures
                        }
                        val stroke = ringWidth.toPx()
                        val centerRadius = (size.width.coerceAtMost(size.height) - stroke) / 2f
                        val dx = position.x - size.width / 2f
                        val dy = position.y - size.height / 2f
                        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (distance > centerRadius + stroke / 2f || distance < centerRadius - stroke / 2f) {
                            onSliceSelected(null)
                        } else {
                            var angle = toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            angle = (angle + 90f + 360f) % 360f
                            onSliceSelected(sliceKeyAt(slices, totalValue, angle))
                        }
                    }
                }
        ) {
            val stroke = ringWidth.toPx()
            val centerRadius = (size.width.coerceAtMost(size.height) - stroke) / 2f
            val arcSize = Size(centerRadius * 2f, centerRadius * 2f)
            val topLeft = Offset(size.width / 2f - centerRadius, size.height / 2f - centerRadius)
            if (totalValue <= 0L || slices.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.6f)
                )
                return@Canvas
            }
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.valueMinor / totalValue
                if (sweep > 0f) {
                    val dimmed = selectedKey != null && selectedKey != slice.key
                    drawArc(
                        color = slice.color.copy(alpha = if (dimmed) 0.30f else 1f),
                        startAngle = start + gapAngle / 2f,
                        sweepAngle = (sweep - gapAngle).coerceAtLeast(0.4f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                }
                start += sweep
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerTitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1
            )
            Text(
                text = centerHint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** 排行条：一条已填充比例的圆角横条。 */
@Composable
fun RatioBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barHeight: Dp = 8.dp,
    cornerRadius: Dp = 4.dp
) {
    Canvas(modifier = modifier.fillMaxWidth().height(barHeight)) {
        val corner = CornerRadius(cornerRadius.toPx())
        drawRoundRect(color = trackColor, size = Size(size.width, size.height), cornerRadius = corner)
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 1f) {
            drawRoundRect(color = color, size = Size(filled, size.height), cornerRadius = corner)
        }
    }
}

/** 图表占位：区间没有数据时展示。 */
@Composable
fun ChartEmptyState(text: String, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun AxisColumn(
    topLabel: String,
    middleLabel: String,
    bottomLabel: String,
    height: Dp
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.width(48.dp).height(height)) {
        Text(
            text = topLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).offset(y = (-7).dp)
        )
        Text(
            text = middleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart).offset(y = (-7).dp)
        )
        Text(
            text = bottomLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).offset(y = 6.dp)
        )
    }
}

private fun DrawScope.drawHorizontalGrid(
    color: Color,
    rows: Int
) {
    val strokeWidth = 1.dp.toPx()
    for (index in 0..rows) {
        val y = size.height * index / rows
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeWidth)
    }
}

private fun ratioHeight(valueMinor: Long, maxValueMinor: Long, available: Float): Float {
    if (valueMinor <= 0L || maxValueMinor <= 0L) return 0f
    val ratio = valueMinor.toFloat() / maxValueMinor.toFloat()
    return (available * ratio).coerceIn(2f, available)
}

private fun sliceKeyAt(slices: List<ChartSlice>, totalValue: Long, angle: Float): Long? {
    var start = 0f
    slices.forEach { slice ->
        val sweep = 360f * slice.valueMinor / totalValue
        if (angle >= start && angle < start + sweep) return slice.key
        start += sweep
    }
    return slices.lastOrNull()?.key
}

private fun monthLabelText(monthKey: String): String {
    val month = monthKey.substringAfter('-', "").toIntOrNull()
    return if (month == null) monthKey else "${month}月"
}

private fun pickAxisLabels(labels: List<String>): List<String> {
    if (labels.size <= 5) return labels
    val last = labels.size - 1
    return listOf(0, last / 4, last / 2, last * 3 / 4, last)
        .distinct()
        .map { labels[it] }
}

/** 把金额向上取到「好看」的刻度值，用于纵轴最大值。 */
private fun niceCeilMinor(value: Long): Long {
    if (value <= 0L) return CENTS_PER_YUAN * 50L
    val yuan = value.toDouble() / CENTS_PER_YUAN
    var magnitude = 1.0
    while (yuan / magnitude >= 10.0) magnitude *= 10.0
    val normalized = yuan / magnitude
    val step = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    val nice = step * magnitude
    return if (nice < 0.01) CENTS_PER_YUAN else (nice * CENTS_PER_YUAN).toLong()
}

/** 纵轴刻度文字：大额折算成「万」，小额保留必要精度。 */
internal fun chartAmountLabel(minor: Long): String {
    val safe = if (minor < 0L) 0L else minor
    return when {
        safe >= 1_000_000L -> {
            val tenths = safe / 100_000L
            if (tenths % 10L == 0L) "${tenths / 10L}万" else "${tenths / 10L}.${tenths % 10L}万"
        }
        safe >= 10_000L -> "${(safe + 50L) / 100L}"
        else -> {
            val yuan = safe / CENTS_PER_YUAN
            val tenths = (safe % CENTS_PER_YUAN) / 10L
            if (tenths == 0L) "$yuan" else "$yuan.$tenths"
        }
    }
}
