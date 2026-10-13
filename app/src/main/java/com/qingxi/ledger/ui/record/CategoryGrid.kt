package com.qingxi.ledger.ui.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qingxi.ledger.data.category.CategoryEntity

val EMOJI_CHOICES = listOf("🍜", "☕", "🛒", "🚌", "🎬", "🏠", "💊", "🎁", "📚", "✈️", "🐾", "💰")

private val CATEGORY_CELL_WIDTH = 64.dp
private val CATEGORY_CELL_HEIGHT = 58.dp
private val CATEGORY_GRID_SPACING = 8.dp

/** 折叠时只露出一行半，让下一排分类露出半截提示还有更多。 */
private val CATEGORY_GRID_COLLAPSED_HEIGHT = CATEGORY_CELL_HEIGHT * 1.5f + CATEGORY_GRID_SPACING

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = ((maxWidth + CATEGORY_GRID_SPACING) / (CATEGORY_CELL_WIDTH + CATEGORY_GRID_SPACING))
            .toInt()
            .coerceAtLeast(1)
        val overflow = categories.size + 1 > columns * 1.5f

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 开关放在网格上方：展开后网格变高，开关若留在下方会被顶出屏幕，
            // 键盘弹起时更明显，用户就只能展开无法收起。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (overflow) {
                    ToggleLabel(
                        text = if (expanded) "收起 ▲" else "展开 ▼",
                        onClickLabel = if (expanded) "收起分类" else "展开更多分类"
                    ) { expanded = !expanded }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded || !overflow) Modifier
                        else Modifier.height(CATEGORY_GRID_COLLAPSED_HEIGHT).clip(RoundedCornerShape(12.dp))
                    )
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CATEGORY_GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(CATEGORY_GRID_SPACING)
                ) {
                    categories.forEach { category ->
                        CategoryCell(
                            icon = category.icon,
                            name = category.name,
                            selected = category.id == selectedId,
                            onClick = { onSelect(category.id) }
                        )
                    }
                    AddCategoryCell(onAddClick = onAddClick)
                }
            }
        }
    }
}

@Composable
private fun ToggleLabel(text: String, onClickLabel: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun AddCategoryCell(onAddClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.size(width = CATEGORY_CELL_WIDTH, height = CATEGORY_CELL_HEIGHT)
    ) {
        Column(
            modifier = Modifier.clickable(onClickLabel = "添加自定义分类", onClick = onAddClick).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "＋", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Text(text = "新增", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCell(
    icon: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.dp, colors.primary) else null,
        modifier = Modifier
            .size(width = CATEGORY_CELL_WIDTH, height = CATEGORY_CELL_HEIGHT)
            .clickable(onClickLabel = name, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(EMOJI_CHOICES.first()) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, icon) },
                enabled = name.isNotBlank()
            ) { Text(text = "添加", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
            }
        },
        title = { Text(text = "新增自定义分类", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(12) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "分类名称",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EMOJI_CHOICES.forEach { candidate ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (candidate == icon) colors.primaryContainer else colors.surfaceVariant,
                            border = if (candidate == icon) BorderStroke(1.dp, colors.primary) else null,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(onClickLabel = null, onClick = { icon = candidate })
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = candidate, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    )
}
