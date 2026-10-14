package com.qingxi.ledger.ui.recurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.recurring.END_COUNT
import com.qingxi.ledger.data.recurring.END_NEVER
import com.qingxi.ledger.data.recurring.END_UNTIL
import com.qingxi.ledger.data.recurring.REPEAT_DAILY
import com.qingxi.ledger.data.recurring.REPEAT_MONTHLY
import com.qingxi.ledger.data.recurring.REPEAT_WEEKLY
import com.qingxi.ledger.data.recurring.RecurringRuleEntity
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.ui.record.AddCategoryDialog
import com.qingxi.ledger.ui.record.CategoryGrid
import com.qingxi.ledger.ui.record.TagEditor
import com.qingxi.ledger.util.Dates
import kotlinx.coroutines.launch

private val WEEKDAY_WIDTH = 36.dp
private val GRID_SPACING = 8.dp

/**
 * 新建/编辑周期规则的居中弹窗。表单状态由本组件持有，
 * 保存不合法时宿主只回写 errorMessage，弹窗不关闭，用户改完可以直接再提交。
 */
@Composable
fun RuleEditorDialog(
    original: RecurringRuleEntity?,
    categories: List<CategoryEntity>,
    knownTags: List<String>,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (RuleDraft) -> Unit
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    var draft by remember(original) { mutableStateOf(RuleDraft.from(original)) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val isEditing = original != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing) {
                    TextButton(onClick = onDelete) {
                        Text(text = "删除", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE2574C))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        },
        title = {
            Text(
                text = if (isEditing) "编辑周期记账" else "新建周期记账",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CategoryGrid(
                    categories = categories,
                    selectedId = draft.categoryId,
                    onSelect = { draft = draft.copy(categoryId = it) },
                    onAddClick = { showAddCategory = true }
                )
                AmountField(
                    amountText = draft.amountText,
                    onValueChange = { draft = draft.copy(amountText = sanitizeAmountText(it)) }
                )
                RepeatField(draft = draft, onChange = { draft = it })
                DateField(
                    label = "起始日期",
                    millis = draft.startMillis,
                    onClick = { showStartPicker = true }
                )
                EndField(
                    draft = draft,
                    onChange = { draft = it },
                    onPickEnd = { showEndPicker = true }
                )
                NoteField(
                    note = draft.note,
                    onValueChange = { draft = draft.copy(note = it.take(60)) }
                )
                TagEditor(
                    selectedTags = splitTags(draft.tags),
                    knownTags = knownTags,
                    onCommitInput = { draft = draft.copy(tags = commitRuleTags(draft.tags, it)) },
                    onToggleTag = { draft = draft.copy(tags = toggleRuleTag(draft.tags, it)) }
                )
                val shownError = localError ?: errorMessage
                shownError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2574C)
                    )
                }
                SubmitButton(
                    isEditing = isEditing,
                    canSave = validateDraft(draft) == null
                ) {
                    localError = validateDraft(draft)
                    if (localError == null) onSave(draft)
                }
            }
        }
    )

    if (showStartPicker) {
        RuleDayPickerDialog(
            title = "起始日期",
            initialMillis = draft.startMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = { millis ->
                draft = draft.copy(startMillis = millis)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        RuleDayPickerDialog(
            title = "截止日期",
            initialMillis = if (draft.endMillis > 0L) draft.endMillis else draft.startMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = { millis ->
                draft = draft.copy(endType = END_UNTIL, endMillis = millis)
                showEndPicker = false
            }
        )
    }
    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, icon ->
                showAddCategory = false
                scope.launch {
                    val newId = runCatching { graph.categories.add(name, icon) }.getOrDefault(0L)
                    if (newId > 0L) draft = draft.copy(categoryId = newId)
                }
            }
        )
    }
}

@Composable
private fun AmountField(amountText: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("每次金额（元）")
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "¥", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = amountText,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (amountText.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatField(draft: RuleDraft, onChange: (RuleDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("周期")
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            OptionChip("每天", selected = draft.repeatType == REPEAT_DAILY, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(repeatType = REPEAT_DAILY))
            }
            OptionChip("每周", selected = draft.repeatType == REPEAT_WEEKLY, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(repeatType = REPEAT_WEEKLY))
            }
            OptionChip("每月", selected = draft.repeatType == REPEAT_MONTHLY, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(repeatType = REPEAT_MONTHLY))
            }
        }
        when (draft.repeatType) {
            REPEAT_WEEKLY -> WeekdayRow(selected = draft.weekdays) { next -> onChange(draft.copy(weekdays = next)) }
            REPEAT_MONTHLY -> DayOfMonthRow(selected = draft.dayOfMonth) { day -> onChange(draft.copy(dayOfMonth = day)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayRow(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    val next = { iso: Int ->
        val toggle = if (iso in selected) selected - iso else (selected + iso).sorted()
        toggle
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ISO_WEEKDAY_ORDER.forEach { iso ->
            OptionChip(
                label = isoWeekdayLabel(iso),
                selected = iso in selected,
                modifier = Modifier.weight(1f),
                compact = true
            ) { onChange(next(iso)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfMonthRow(selected: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..31).forEach { day ->
                OptionChip(
                    label = day.toString(),
                    selected = day == selected,
                    compact = true
                ) { onChange(day) }
            }
        }
        Text(
            text = "29-31 号在不足天数的月份按当月最后一天计入",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DateField(label: String, millis: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = label, onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📅", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = Dates.dayLabel(millis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndField(draft: RuleDraft, onChange: (RuleDraft) -> Unit, onPickEnd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("结束方式")
        Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
            OptionChip("永不", selected = draft.endType == END_NEVER, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(endType = END_NEVER, endMillis = 0L, totalCountText = ""))
            }
            OptionChip("到日期", selected = draft.endType == END_UNTIL, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(endType = END_UNTIL, totalCountText = ""))
            }
            OptionChip("共 N 次", selected = draft.endType == END_COUNT, modifier = Modifier.weight(1f)) {
                onChange(draft.copy(endType = END_COUNT, endMillis = 0L))
            }
        }
        when (draft.endType) {
            END_UNTIL -> {
                if (draft.endMillis > 0L) {
                    DateField(label = "截止日期", millis = draft.endMillis, onClick = onPickEnd)
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "选择截止日期", onClick = onPickEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📅", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "还没选截止日期",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            END_COUNT -> CountField(value = draft.totalCountText) { next -> onChange(draft.copy(totalCountText = next)) }
        }
    }
}

@Composable
private fun CountField(value: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "总次数", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(4)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "如 12",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun NoteField(note: String, onValueChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel("备注")
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextField(
                value = note,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (note.isEmpty()) {
                            Text(
                                text = "如「宽带月租」",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .then(if (compact) Modifier.width(WEEKDAY_WIDTH) else Modifier)
            .height(38.dp)
            .clickable(onClickLabel = label, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
}

@Composable
private fun SubmitButton(isEditing: Boolean, canSave: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (canSave) colors.primary else colors.surfaceVariant,
        contentColor = if (canSave) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            // 不完整时也要能点：点了才知道缺什么，错误留在弹窗里而不是把人挡在按钮外
            .clickable(onClickLabel = "保存规则", onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isEditing) "更新规则" else "创建规则",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDayPickerDialog(
    title: String,
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = localDayToUtcMillis(initialMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = pickerState.selectedDateMillis
                if (selected != null) onConfirm(utcMillisToLocalDayStart(selected)) else onDismiss()
            }) {
                Text(text = "确定", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private fun toggleRuleTag(tags: String, tag: String): String {
    val parts = splitTags(tags).toMutableList()
    if (tag in parts) parts.remove(tag) else parts.add(tag)
    return parts.joinToString(",")
}

private fun commitRuleTags(tags: String, input: String): String {
    val parts = splitTags(tags).toMutableList()
    input.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
        if (it !in parts) parts.add(it)
    }
    return parts.joinToString(",")
}
