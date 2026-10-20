package com.qingxi.ledger.ui.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingxi.ledger.di.appViewModel
import com.qingxi.ledger.ui.theme.ExpenseRed
import com.qingxi.ledger.util.AmountExpr
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(editId: Long?, onClose: () -> Unit) {
    val vm: RecordViewModel = appViewModel(key = "record_${editId ?: "new"}")
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val amountFocus = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var previewIndex by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) { vm.open(editId) }

    LaunchedEffect(state.messageId) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        launch { snackbarHostState.showSnackbar(msg) }
        vm.consumeMessage()
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            launch { snackbarHostState.showSnackbar("已记录") }
            delay(900)
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { amountFocus.requestFocus() }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) vm.importImages(context, uris)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text("←", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = if (editId != null) "编辑账单" else "记一笔",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
            }
        }
    ) { insets ->
        Column(modifier = Modifier.padding(insets).fillMaxSize().imePadding()) {
            AmountBlock(
                amountInput = state.amountInput,
                onValueChange = vm::setAmountInput,
                focusRequester = amountFocus,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateRow(millis = state.timeMillis, onClick = { showDatePicker = true })
                CategoryGrid(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = vm::selectCategory,
                    onAddClick = { showAddCategory = true }
                )
                NoteField(note = state.note, onChange = vm::setNote)
                TagEditor(
                    selectedTags = state.tags,
                    knownTags = state.knownTags,
                    onCommitInput = vm::commitTags,
                    onToggleTag = vm::toggleTag
                )
                Spacer(Modifier.height(4.dp))
                AttachmentRow(
                    attachments = state.attachments,
                    onPickImages = {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPreview = { path -> previewIndex = state.attachments.indexOf(path) },
                    onRemove = vm::removeAttachment
                )
            }
            SaveButton(
                canFinish = state.amountMinor != null && state.selectedCategoryId != 0L,
                onSave = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.save()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    if (showDatePicker) {
        DatePickerOnlyDialog(
            initialMillis = state.timeMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { year, month, day ->
                vm.pickDate(year, month, day)
                showDatePicker = false
            }
        )
    }
    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, icon ->
                vm.addCategory(name, icon)
                showAddCategory = false
            }
        )
    }
    if (previewIndex in state.attachments.indices) {
        ImagePreviewDialog(
            paths = state.attachments,
            startIndex = previewIndex,
            onDismiss = { previewIndex = -1 }
        )
    }
}

@Composable
private fun AmountBlock(
    amountInput: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "¥",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ExpenseRed
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = amountInput,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = colors.onSurface,
                        textAlign = TextAlign.Start
                    ),
                    cursorBrush = SolidColor(ExpenseRed),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (amountInput.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
        if (AmountExpr.hasOperator(amountInput)) {
            Text(
                text = "= ${AmountExpr.preview(amountInput)}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SaveButton(
    canFinish: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (canFinish) colors.primary else colors.surfaceVariant,
        contentColor = if (canFinish) colors.onPrimary else colors.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClickLabel = "保存账单", onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "保存",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DateRow(millis: Long, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable(onClickLabel = "选择日期时间", onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        })
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🗓", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Text(text = formatChineseDate(millis), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoteField(note: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicTextField(
            value = note,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isEmpty()) {
                        Text(
                            text = "备注（点击输入）",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerOnlyDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit
) {
    val utcCal = remember { Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = initialMillis } }
    val datePickerState: DatePickerState = rememberDatePickerState(initialSelectedDateMillis = utcCal.timeInMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = datePickerState.selectedDateMillis ?: utcCal.timeInMillis
                val parts = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = selected }
                onConfirm(
                    parts.get(Calendar.YEAR),
                    parts.get(Calendar.MONTH) + 1,
                    parts.get(Calendar.DAY_OF_MONTH)
                )
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
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

private fun formatChineseDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        Locale.CHINA,
        "%d年%d月%d日",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}
