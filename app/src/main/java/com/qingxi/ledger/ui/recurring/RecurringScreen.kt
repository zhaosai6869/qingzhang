package com.qingxi.ledger.ui.recurring

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.recurring.RecurringRuleEntity
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.di.LocalAppGraph
import com.qingxi.ledger.ui.common.SwipeableDeleteRow
import com.qingxi.ledger.ui.settings.CenteredHint
import com.qingxi.ledger.ui.settings.NoteText
import com.qingxi.ledger.ui.settings.SectionCard
import com.qingxi.ledger.ui.settings.SubPageShell
import com.qingxi.ledger.ui.settings.ThinDivider
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.Money
import kotlinx.coroutines.launch

/**
 * 周期记账规则列表：规则本身不等于已发生的支出，
 * 到期项要在流水页确认后才会写进账单，所以这里只管规则，不显示任何金额合计。
 */
@Composable
fun RecurringScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rules by graph.recurrences.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by graph.categories.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val expenseCategories by graph.categories.observeExpenses().collectAsStateWithLifecycle(initialValue = emptyList())
    val knownTags by produceState(initialValue = emptyList<String>(), graph) {
        value = runCatching { graph.transactions.usedTags() }.getOrDefault(emptyList())
    }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    var editorRule by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    SubPageShell(title = "周期记账", onBack = onBack, snackbarHostState = snackbarHostState) {
        NoteText(
            text = "到期当天在流水页列出，确认后才记账；改规则不会动已经入账的流水。",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClickLabel = "新建周期规则") {
                    isCreating = true
                    editorRule = null
                    editorError = null
                }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Text(text = "＋ 新建周期规则", style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(Modifier.height(14.dp))

        if (rules.isEmpty()) {
            CenteredHint(text = "还没有周期规则")
        } else {
            SectionCard {
                rules.forEachIndexed { index, rule ->
                    if (index > 0) ThinDivider()
                    RuleRow(
                        rule = rule,
                        category = categoryById[rule.categoryId],
                        onClick = {
                            isCreating = false
                            editorRule = rule
                            editorError = null
                        },
                        onToggle = { enabled ->
                            scope.launch { graph.recurrences.setEnabled(rule.id, enabled) }
                        },
                        onDelete = { pendingDelete = rule }
                    )
                }
            }
        }
    }

    if (isCreating || editorRule != null) {
        RuleEditorDialog(
            original = editorRule,
            categories = expenseCategories,
            knownTags = knownTags,
            errorMessage = editorError,
            onDismiss = {
                isCreating = false
                editorRule = null
                editorError = null
            },
            onDelete = {
                isCreating = false
                editorRule?.let { pendingDelete = it }
                editorError = null
            },
            onSave = { draft ->
                val problem = validateDraft(draft)
                if (problem != null) {
                    editorError = problem
                } else {
                    scope.launch {
                        val saved = runCatching { graph.recurrences.save(draft.toEntity()) }.isSuccess
                        if (saved) {
                            isCreating = false
                            editorRule = null
                            editorError = null
                            snackbarHostState.showSnackbar(if (draft.id == 0L) "已创建规则" else "已更新规则")
                        } else {
                            editorError = "保存失败，请再试一次"
                        }
                    }
                }
            }
        )
    }

    pendingDelete?.let { rule ->
        DeleteRuleDialog(
            category = categoryById[rule.categoryId]?.name ?: "未分类",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    graph.recurrences.deleteRule(rule.id)
                    snackbarHostState.showSnackbar("已删除规则，已入账的流水保留")
                }
            }
        )
    }
}

@Composable
private fun RuleRow(
    rule: RecurringRuleEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SwipeableDeleteRow(
        resetKey = rule.id,
        swipeEnabled = true,
        deleteLabel = "删除",
        onClick = onClick,
        onDelete = onDelete
    ) { rowModifier, onRowClick ->
        Row(
            modifier = rowModifier
                .clickable(onClickLabel = "编辑规则", onClick = onRowClick)
                .padding(vertical = 12.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category?.icon ?: "❓",
                fontSize = 19.sp,
                modifier = Modifier.alpha(if (rule.enabled) 1f else 0.4f)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (rule.enabled) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${category?.name ?: "未分类"} ¥${Money.format(rule.amountMinor)}/次",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = ruleDetailLine(rule, category),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

/** 行内尽量把该说的说完：周期、结束方式、备注和标签。 */
private fun ruleDetailLine(rule: RecurringRuleEntity, category: CategoryEntity?): String {
    val parts = mutableListOf(describeRule(rule))
    val note = rule.note.trim()
    if (note.isNotEmpty()) parts += note
    val tags = splitTags(rule.tags)
    if (tags.isNotEmpty()) parts += tags.joinToString("、") { "#$it" }
    if (category == null) parts += "分类已删除"
    return parts.joinToString(" · ")
}

@Composable
private fun DeleteRuleDialog(
    category: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "删除「$category」的周期规则？", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = "只停掉这条规则，之前确认入账的流水会继续留在账单里。",
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
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
