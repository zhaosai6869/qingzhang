package com.qingxi.ledger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.di.LocalAppGraph
import kotlinx.coroutines.launch

private const val ICON_COLUMNS = 8

/** 分类图标库：按主题分组，够覆盖日常收支，也够自定义时挑。 */
private val ICON_GROUPS = linkedMapOf(
    "餐饮" to listOf("🍚", "🍜", "🍔", "☕", "🍰", "🍺", "🥤", "🍎"),
    "出行" to listOf("🚌", "🚇", "🚕", "🚗", "🛵", "✈️", "🚲", "⛽"),
    "生活" to listOf("🏠", "💡", "💧", "🧹", "📱", "💻", "🎧", "🔌"),
    "购物" to listOf("🛒", "👕", "👟", "🎁", "📦", "💄", "🧴", "🪥"),
    "娱乐" to listOf("🎬", "🎮", "🎵", "📚", "🏃", "🏖", "🎡", "🐾"),
    "财务" to listOf("💰", "💳", "🏦", "📈", "🧾", "🎓", "🏥", "🤝")
)

private val ALL_ICONS: List<String> = ICON_GROUPS.values.flatten()

/** 分类管理：内置分类可改名换图标，自定义分类可增删（A4 + I5 图标库）。 */
@Composable
fun CategoryManageScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(ALL_ICONS.first()) }
    var message by remember { mutableStateOf<String?>(null) }

    val categories by graph.categories.observeExpenses().collectAsStateWithLifecycle(initialValue = emptyList())

    SubPageShell(title = "分类管理", onBack = onBack) {
        NoteText(
            text = "删除自定义分类后，它的账单会自动改挂到「其他」分类。",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        SectionCard(title = "现有分类", subtitle = "${categories.size} 个") {
            if (categories.isEmpty()) {
                CenteredHint("还没有分类")
            } else {
                categories.forEach { category ->
                    CategoryRow(
                        category = category,
                        selected = editingId == category.id,
                        onEdit = {
                            editingId = category.id
                            name = category.name
                            icon = category.icon
                        }
                    )
                    if (category != categories.last()) ThinDivider()
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = if (editingId == null) "新增分类" else "编辑分类") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名称") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "图标",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            IconPicker(selected = icon, onSelect = { icon = it })
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) {
                            message = "请先填写分类名称"
                            return@Button
                        }
                        val id = editingId
                        scope.launch {
                            if (id == null) {
                                graph.categories.add(trimmed, icon)
                                message = "已添加「$trimmed」"
                            } else {
                                val existing = categories.firstOrNull { it.id == id }
                                if (existing != null) {
                                    graph.categories.update(existing.copy(name = trimmed, icon = icon))
                                    message = "已更新「$trimmed」"
                                }
                            }
                            editingId = null
                            name = ""
                            icon = ALL_ICONS.first()
                        }
                    }
                ) {
                    Text(if (editingId == null) "添加" else "保存")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        editingId = null
                        name = ""
                        icon = ALL_ICONS.first()
                    }
                ) {
                    Text(if (editingId == null) "清空" else "取消")
                }
            }
            message?.let {
                NoteText(text = it, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    selected: Boolean,
    onEdit: () -> Unit
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClickLabel = "编辑分类") { onEdit() }
            .padding(horizontal = 4.dp, vertical = 9.dp)
    ) {
        Text(text = category.icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (category.isSystem) "内置" else "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!category.isSystem) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "删除分类") {
                        scope.launch { graph.categories.delete(category) }
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ICON_GROUPS.forEach { (group, icons) ->
            Text(
                text = group,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
            IconsFlowRow(icons = icons, selected = selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun IconsFlowRow(icons: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    icons.chunked(ICON_COLUMNS).forEach { rowIcons ->
        Row(modifier = Modifier.fillMaxWidth()) {
            rowIcons.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (emoji == selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceVariant
                        )
                        .clickable(onClickLabel = "选择图标") { onSelect(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
            repeat(ICON_COLUMNS - rowIcons.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
