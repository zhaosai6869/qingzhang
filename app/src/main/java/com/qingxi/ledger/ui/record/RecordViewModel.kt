package com.qingxi.ledger.ui.record

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.category.KIND_EXPENSE
import com.qingxi.ledger.data.tx.ORIGIN_MANUAL
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.data.tx.joinPaths
import com.qingxi.ledger.data.tx.joinTags
import com.qingxi.ledger.data.tx.splitPaths
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.AmountExpr
import com.qingxi.ledger.widget.refreshTodayWidgets
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MAX_AMOUNT_MINOR = 9_999_999_999L

private val OPERATORS = charArrayOf('+', '-', '×', '÷')

data class RecentPreset(
    val categoryId: Long,
    val categoryName: String,
    val amountMinor: Long,
    val note: String,
    val tags: List<String>
)

data class RecordUiState(
    val amountInput: String = "",
    val amountMinor: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: Long = 0L,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val knownTags: List<String> = emptyList(),
    val timeMillis: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList(),
    val origin: Int = ORIGIN_MANUAL,
    val recents: List<RecentPreset> = emptyList(),
    val message: String? = null,
    val messageId: Int = 0,
    val saved: Boolean = false
)

class RecordViewModel(private val app: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    private var opened = false
    private var editing: TransactionEntity? = null
    private var categoriesJob: Job? = null

    fun open(editId: Long?) {
        if (opened) return
        opened = true
        viewModelScope.launch {
            val entity = editId?.let { app.transactions.getById(it) }
            if (entity != null) {
                editing = entity
                _state.update { s ->
                    s.copy(
                        amountMinor = entity.amountMinor.takeIf { it > 0 },
                        amountInput = minorToInput(entity.amountMinor),
                        selectedCategoryId = entity.categoryId,
                        note = entity.note,
                        tags = splitTags(entity.tags),
                        timeMillis = entity.timeMillis,
                        attachments = splitPaths(entity.attachments),
                        origin = entity.origin
                    )
                }
            }
            observeCategories()
        }
        viewModelScope.launch {
            app.transactions.observeExpenses().collect { list ->
                val tags = list.flatMap { splitTags(it.tags) }.distinct().take(12)
                val recents = list.distinctBy { it.categoryId }.take(6).map {
                    RecentPreset(it.categoryId, "", it.amountMinor, it.note, splitTags(it.tags))
                }
                val allNameMap = runCatching { app.categories.all().associate { it.id to it.name } }.getOrNull()
                _state.update {
                    it.copy(
                        knownTags = tags,
                        recents = recents.map { r -> r.copy(categoryName = allNameMap?.get(r.categoryId) ?: "分类") }
                    )
                }
            }
        }
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            val fallbackId = runCatching { app.settings.settings.first().lastCategoryId }.getOrDefault(0L)
            app.categories.observeExpenses().collect { list ->
                _state.update { s ->
                    val selected = s.selectedCategoryId.takeIf { id -> id != 0L && list.any { it.id == id } }
                        ?: fallbackId.takeIf { id -> id != 0L && list.any { it.id == id } }
                        ?: list.firstOrNull()?.id
                        ?: 0L
                    s.copy(categories = list, selectedCategoryId = selected)
                }
            }
        }
    }

    fun setAmountInput(raw: String) {
        _state.update { withAmount(it, sanitizeAmount(raw)) }
    }

    fun selectCategory(id: Long) {
        _state.update { it.copy(selectedCategoryId = id) }
    }

    fun setNote(value: String) {
        _state.update { it.copy(note = value) }
    }

    fun commitTags(raw: String) {
        val added = splitTags(raw)
        if (added.isEmpty()) return
        _state.update { s -> s.copy(tags = (s.tags + added).distinct()) }
    }

    fun toggleTag(tag: String) {
        _state.update { s ->
            s.copy(tags = if (tag in s.tags) s.tags - tag else s.tags + tag)
        }
    }

    fun pickDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        _state.update { s ->
            val cal = Calendar.getInstance().apply { timeInMillis = s.timeMillis }
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            s.copy(timeMillis = cal.timeInMillis)
        }
    }

    fun applyRecent(preset: RecentPreset) {
        _state.update { it.copy(selectedCategoryId = preset.categoryId, note = preset.note, tags = preset.tags) }
    }

    fun importImages(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val paths = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val file = uniqueAttachmentFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        } ?: return@runCatching null
                        file.absolutePath
                    }.getOrNull()
                }
            }
            if (paths.isNotEmpty()) {
                _state.update { s -> s.copy(attachments = (s.attachments + paths).distinct().take(4)) }
            }
        }
    }

    fun removeAttachment(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
            _state.update { it.copy(attachments = it.attachments - path) }
        }
    }

    fun addCategory(name: String, icon: String) {
        viewModelScope.launch {
            try {
                val id = app.categories.add(name, icon)
                if (id > 0L) _state.update { it.copy(selectedCategoryId = id) }
                emitMessage("已添加分类")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitMessage("添加分类失败：名称不能为空")
            }
        }
    }

    fun emitMessage(text: String) {
        _state.update { it.copy(message = text, messageId = it.messageId + 1) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val snapshot = _state.value
        val minor = snapshot.amountMinor
        if (minor == null || minor <= 0L) {
            emitMessage("请输入有效金额")
            return
        }
        if (snapshot.selectedCategoryId == 0L) {
            emitMessage("请先选择一个分类")
            return
        }
        viewModelScope.launch {
            val entity = editing
            val tagsJoined = joinTags(snapshot.tags)
            val attachmentsJoined = joinPaths(snapshot.attachments)
            val note = snapshot.note.trim()
            if (entity != null) {
                app.transactions.update(
                    entity.copy(
                        kind = KIND_EXPENSE,
                        amountMinor = minor,
                        categoryId = snapshot.selectedCategoryId,
                        note = note,
                        tags = tagsJoined,
                        timeMillis = snapshot.timeMillis,
                        attachments = attachmentsJoined,
                        origin = snapshot.origin
                    )
                )
            } else {
                app.transactions.insert(
                    TransactionEntity(
                        kind = KIND_EXPENSE,
                        amountMinor = minor,
                        categoryId = snapshot.selectedCategoryId,
                        note = note,
                        tags = tagsJoined,
                        timeMillis = snapshot.timeMillis,
                        createdAtMillis = System.currentTimeMillis(),
                        attachments = attachmentsJoined,
                        origin = snapshot.origin
                    )
                )
            }
            app.settings.rememberLastCategory(snapshot.selectedCategoryId)
            refreshTodayWidgets(app.appContext)
            _state.update { it.copy(saved = true) }
        }
    }

    private fun withAmount(s: RecordUiState, input: String): RecordUiState =
        s.copy(amountInput = input, amountMinor = AmountExpr.evalMinor(input)?.takeIf { it > 0L })

    /** 系统键盘可以一次性输入任意字符（含粘贴），逐字套用同一套校验规则丢弃非法部分。 */
    private fun sanitizeAmount(raw: String): String {
        var acc = ""
        for (ch in raw) {
            val normalized = when (ch) {
                '*' -> '×'
                '/' -> '÷'
                'x', 'X' -> '×'
                else -> ch
            }
            acc = appendKey(acc, normalized) ?: continue
        }
        return acc
    }

    private fun appendKey(input: String, ch: Char): String? {
        if (input.length >= 24) return null
        var segment = input
        for (op in OPERATORS) segment = segment.substringAfterLast(op)
        val intPart = segment.substringBefore('.')
        val fracPart = segment.substringAfter('.', "")
        return when {
            ch.isDigit() -> {
                if (fracPart.length >= 2) null
                else if (!segment.contains('.') && intPart.length >= 8) null
                else (input + ch).takeIf { next ->
                    val parsed = AmountExpr.evalMinor(next)
                    parsed == null || parsed <= MAX_AMOUNT_MINOR
                }
            }
            ch == '.' -> {
                if (segment.contains('.')) null
                else if (segment.isEmpty()) "${input}0."
                else "$input."
            }
            ch in OPERATORS -> {
                if (input.isEmpty() || segment.isEmpty() || input.last() in OPERATORS) null
                else "$input$ch"
            }
            else -> null
        }
    }

    private fun minorToInput(minor: Long): String =
        if (minor % 100L == 0L) (minor / 100L).toString()
        else String.format(Locale.US, "%.2f", minor / 100.0)

    private fun uniqueAttachmentFile(): File {
        var ts = System.currentTimeMillis()
        while (true) {
            val candidate = File(app.attachmentDir, "att_$ts.jpg")
            if (!candidate.exists()) return candidate
            ts += 1
        }
    }
}
