package com.qingxi.ledger.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qingxi.ledger.data.category.CategoryEntity
import com.qingxi.ledger.data.db.AppDatabase
import com.qingxi.ledger.data.recurring.END_NEVER
import com.qingxi.ledger.data.recurring.REPEAT_MONTHLY
import com.qingxi.ledger.data.recurring.RecurringDismissalEntity
import com.qingxi.ledger.data.recurring.RecurringRuleEntity
import com.qingxi.ledger.data.tx.TransactionEntity
import com.qingxi.ledger.di.AppGraph
import com.qingxi.ledger.util.CENTS_PER_YUAN
import com.qingxi.ledger.util.Crypto
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.widget.refreshTodayWidgets
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImportMode { REPLACE, MERGE }
data class ImportResult(val imported: Int, val skipped: Int)
data class BackupFile(val name: String, val millis: Long, val sizeBytes: Long, val uri: String)
data class BackupInfo(val version: Int, val exportedAtMillis: Long, val transactionCount: Int, val categoryCount: Int)

private const val TAG = "QingJiBackup"
private const val BACKUP_VERSION = 2
private const val BACKUP_APP = "qingji"
private const val DIR_NAME = "backup"
private const val ENCRYPTED_EXTENSION = "qjbak"
private const val CSV_BOM = "\uFEFF"

/** \u65E7\u7248\u672C\u7559\u4E0B\u7684\u6536\u5165\u8BB0\u5F55\uFF1A\u754C\u9762\u4E0D\u518D\u5C55\u793A\uFF0C\u4F46\u5907\u4EFD\u91CC\u5FC5\u987B\u539F\u6837\u4FDD\u7559\uFF0C\u5BFC\u5165\u540E\u624D\u80FD\u8FD8\u539F\u3002 */
private const val LEGACY_INCOME_KIND = 2

class BackupRepository(private val app: AppGraph) {

    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    private val context: Context get() = app.appContext

    val backupDir: File
        get() = (context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME))
            .apply { if (!exists()) mkdirs() }

    fun suggestedFileName(kind: String, suffix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val tail = suffix.trim().removePrefix(".").filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val knownExtensions = setOf("json", "csv", ENCRYPTED_EXTENSION)
        val explicitExtension = tail.lowercase(Locale.US).takeIf { it in knownExtensions }
        val extension = explicitExtension ?: when (kind.trim().lowercase(Locale.US)) {
            "csv" -> "csv"
            "enc" -> ENCRYPTED_EXTENSION
            else -> "json"
        }
        val label = if (explicitExtension != null || tail.isEmpty()) "" else "_$tail"
        return "qingji_backup_${stamp}$label.$extension"
    }

    suspend fun buildJson(): String = withContext(Dispatchers.IO) {
        val categories = JsonArray().apply {
            app.categories.all().forEach { category ->
                add(
                    JsonObject().apply {
                        addProperty("id", category.id)
                        addProperty("name", category.name)
                        addProperty("icon", category.icon)
                        addProperty("kind", category.kind)
                        addProperty("sortOrder", category.sortOrder)
                        addProperty("isSystem", category.isSystem)
                    }
                )
            }
        }
        val transactions = JsonArray().apply {
            app.transactions.exportAll().forEach { entity ->
                add(
                    JsonObject().apply {
                        addProperty("id", entity.id)
                        addProperty("kind", entity.kind)
                        addProperty("amountMinor", entity.amountMinor)
                        addProperty("categoryId", entity.categoryId)
                        addProperty("note", entity.note)
                        addProperty("tags", entity.tags)
                        addProperty("timeMillis", entity.timeMillis)
                        addProperty("createdAtMillis", entity.createdAtMillis)
                        addProperty("attachments", entity.attachments)
                        addProperty("origin", entity.origin)
                        entity.ruleId?.let { addProperty("ruleId", it) }
                        entity.dueDayKey?.takeIf { it.isNotEmpty() }?.let { addProperty("dueDayKey", it) }
                    }
                )
            }
        }
        val rules = JsonArray().apply {
            app.recurrences.exportRules().forEach { rule ->
                add(
                    JsonObject().apply {
                        addProperty("id", rule.id)
                        addProperty("categoryId", rule.categoryId)
                        addProperty("amountMinor", rule.amountMinor)
                        addProperty("note", rule.note)
                        addProperty("tags", rule.tags)
                        addProperty("repeatType", rule.repeatType)
                        addProperty("weekdays", rule.weekdays)
                        addProperty("dayOfMonth", rule.dayOfMonth)
                        addProperty("startMillis", rule.startMillis)
                        addProperty("endType", rule.endType)
                        addProperty("endMillis", rule.endMillis)
                        addProperty("totalCount", rule.totalCount)
                        addProperty("enabled", rule.enabled)
                        addProperty("createdMillis", rule.createdMillis)
                    }
                )
            }
        }
        val dismissals = JsonArray().apply {
            app.recurrences.exportDismissals().forEach { dismissal ->
                add(
                    JsonObject().apply {
                        addProperty("ruleId", dismissal.ruleId)
                        addProperty("dueDayKey", dismissal.dueDayKey)
                        addProperty("dismissedMillis", dismissal.dismissedMillis)
                    }
                )
            }
        }
        val root = JsonObject().apply {
            addProperty("version", BACKUP_VERSION)
            addProperty("app", BACKUP_APP)
            addProperty("exportedAt", System.currentTimeMillis())
            add("categories", categories)
            add("transactions", transactions)
            add("rules", rules)
            add("dismissals", dismissals)
        }
        prettyGson.toJson(root)
    }

    suspend fun buildCsv(): String = withContext(Dispatchers.IO) {
        val categoryNames = app.categories.all().associate { it.id to it.name }
        val builder = StringBuilder(CSV_BOM)
        builder.append("日期,时间,类型,分类,金额,备注,标签,来源\n")
        app.transactions.exportAll().sortedBy { it.timeMillis }.forEach { entity ->
            builder.append(csvCell(Dates.dayKeyOf(entity.timeMillis))).append(',')
            builder.append(csvCell(Dates.timeLabel(entity.timeMillis))).append(',')
            builder.append(csvCell(if (entity.kind == LEGACY_INCOME_KIND) "收入" else "支出")).append(',')
            builder.append(csvCell(categoryNames[entity.categoryId] ?: "未分类")).append(',')
            builder.append(csvCell(String.format(Locale.US, "%.2f", entity.amountMinor.toDouble() / CENTS_PER_YUAN))).append(',')
            builder.append(csvCell(entity.note)).append(',')
            builder.append(csvCell(entity.tags)).append(',')
            builder.append(csvCell(originLabel(entity.origin))).append('\n')
        }
        builder.toString()
    }

    suspend fun writeJsonToFile(target: File, password: String?): Int = withContext(Dispatchers.IO) {
        val json = buildJson()
        val count = app.transactions.count()
        if (password.isNullOrEmpty()) {
            target.writeText(json, Charsets.UTF_8)
        } else {
            target.writeBytes(Crypto.encryptStream(password, json.toByteArray(Charsets.UTF_8)))
        }
        count
    }

    suspend fun writeCsvToFile(target: File): Int = withContext(Dispatchers.IO) {
        val csv = buildCsv()
        val count = app.transactions.count()
        target.writeText(csv, Charsets.UTF_8)
        count
    }

    suspend fun listBackups(): List<BackupFile> = withContext(Dispatchers.IO) {
        val extensions = setOf("json", "csv", ENCRYPTED_EXTENSION)
        backupDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) in extensions }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupFile(it.name, it.lastModified(), it.length(), Uri.fromFile(it).toString()) }
            ?: emptyList()
    }

    suspend fun readFromUri(uri: Uri, password: String?): String? = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext null
        } catch (t: Throwable) {
            Log.w(TAG, "读取备份失败: ${t.message}")
            return@withContext null
        }
        val text = String(bytes, Charsets.UTF_8)
        if (looksLikeJson(text)) return@withContext text
        if (password.isNullOrEmpty()) {
            Log.w(TAG, "加密备份需要口令")
            return@withContext null
        }
        val decrypted = Crypto.decryptStream(password, bytes) ?: return@withContext null
        String(decrypted, Charsets.UTF_8)
    }

    suspend fun importJson(raw: String, mode: ImportMode): ImportResult = withContext(Dispatchers.IO) {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: return@withContext ImportResult(0, 0)
        if (!root.has("transactions")) return@withContext ImportResult(0, 0)
        val categoryDao = AppDatabase.instance.categoryDao()

        if (mode == ImportMode.REPLACE) {
            app.transactions.deleteAll()
            app.recurrences.deleteAllRules()
        }

        val existingCategories = app.categories.all()
        val existingCategoryIds = existingCategories.map { it.id }.toHashSet()
        val existingNameKinds = existingCategories.map { it.name to it.kind }.toHashSet()
        root.optArray("categories").forEach { element ->
            val category = element.asObjectOrNull() ?: return@forEach
            val id = category.optLong("id")
            val name = category.optString("name")
            val kind = category.optInt("kind").takeIf { it == 1 || it == 2 } ?: 1
            if (id <= 0L || name.isEmpty()) return@forEach
            if (id in existingCategoryIds) return@forEach
            if (mode == ImportMode.MERGE && (name to kind) in existingNameKinds) return@forEach
            runCatching {
                categoryDao.insert(
                    CategoryEntity(
                        id = id,
                        name = name,
                        icon = category.optString("icon").ifEmpty { "🏷" },
                        kind = kind,
                        sortOrder = category.optLong("sortOrder"),
                        isSystem = category.optBoolean("isSystem")
                    )
                )
            }.onFailure { Log.w(TAG, "分类导入失败 $name: ${it.message}") }
        }

        val seenKeys = if (mode == ImportMode.MERGE) {
            app.transactions.exportAll().map { it.dedupeKey() }.toHashSet()
        } else {
            hashSetOf<String>()
        }
        val entities = mutableListOf<TransactionEntity>()
        var skipped = 0
        root.optArray("transactions").forEach { element ->
            val json = element.asObjectOrNull()
            val entity = json?.toTransactionEntity(keepId = mode == ImportMode.REPLACE)
            if (entity == null) {
                skipped++
                return@forEach
            }
            if (mode == ImportMode.MERGE && !seenKeys.add(entity.dedupeKey())) {
                skipped++
                return@forEach
            }
            entities.add(entity)
        }
        if (entities.isNotEmpty()) app.transactions.insertAll(entities)

        // 旧版本备份没有这两段，缺段只当「没有周期规则」，恢复照常。
        val existingRuleIds = app.recurrences.exportRules().map { it.id }.toHashSet()
        val keepRuleIds = mode == ImportMode.REPLACE
        val pendingRules = mutableListOf<Pair<Long, RecurringRuleEntity>>()
        root.optArray("rules").forEach { element ->
            val source = element.asObjectOrNull() ?: return@forEach
            val sourceId = source.optLong("id")
            val entity = source.toRecurringRuleEntity(keepId = keepRuleIds) ?: return@forEach
            if (sourceId <= 0L) return@forEach
            if (mode == ImportMode.MERGE && sourceId in existingRuleIds) return@forEach
            pendingRules += sourceId to entity
        }
        // 合并导入会重新发号，跳过记录得跟着改指向，否则 ruleId 会指到别的规则上。
        val ruleIdMap = pendingRules.associate { (sourceId, entity) ->
            sourceId to app.recurrences.insertRule(entity)
        }

        val dismissalEntities = mutableListOf<RecurringDismissalEntity>()
        root.optArray("dismissals").forEach { element ->
            val json = element.asObjectOrNull() ?: return@forEach
            val sourceRuleId = json.optLong("ruleId")
            val dueDayKey = json.optString("dueDayKey")
            if (sourceRuleId <= 0L || dueDayKey.isEmpty()) return@forEach
            val mappedRuleId = ruleIdMap[sourceRuleId] ?: sourceRuleId
            if (mappedRuleId <= 0L) return@forEach
            dismissalEntities.add(
                RecurringDismissalEntity(mappedRuleId, dueDayKey, json.optLong("dismissedMillis"))
            )
        }
        if (dismissalEntities.isNotEmpty()) app.recurrences.insertDismissals(dismissalEntities)

        refreshTodayWidgets(app.appContext)
        ImportResult(entities.size, skipped)
    }

    fun parseBackupInfo(raw: String): BackupInfo? {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
        if (!root.has("transactions") && !root.has("categories")) return null
        return BackupInfo(
            version = root.optInt("version"),
            exportedAtMillis = root.optLong("exportedAt"),
            transactionCount = root.optArray("transactions").size(),
            categoryCount = root.optArray("categories").size()
        )
    }

    private fun looksLikeJson(text: String): Boolean {
        val head = text.removePrefix(CSV_BOM).trim { it <= ' ' }
        return head.startsWith("{") || head.startsWith("[")
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val needQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needQuote) "\"$escaped\"" else escaped
    }

    private fun originLabel(origin: Int): String = when (origin) {
        1 -> "语音"
        2 -> "拍照识别"
        3 -> "识别"
        4 -> "周期"
        else -> "手动"
    }

    private fun JsonObject.toTransactionEntity(keepId: Boolean): TransactionEntity? {
        val time = optLong("timeMillis")
        val amount = optLong("amountMinor")
        if (time <= 0L || amount < 0L) return null
        val created = optLong("createdAtMillis")
        val ruleId = optLong("ruleId")
        val dueDayKey = optString("dueDayKey")
        return TransactionEntity(
            id = if (keepId) optLong("id") else 0L,
            kind = optInt("kind").takeIf { it == 1 || it == 2 } ?: 1,
            amountMinor = amount,
            categoryId = optLong("categoryId"),
            note = optString("note"),
            tags = optString("tags"),
            timeMillis = time,
            createdAtMillis = if (created > 0L) created else time,
            attachments = optString("attachments"),
            origin = optInt("origin"),
            ruleId = ruleId.takeIf { it > 0L },
            dueDayKey = dueDayKey.takeIf { it.isNotEmpty() }
        )
    }

    /** 频率与结束条件都按原始 int 存，非法值当无限/每月处理，不让一份坏备份把导入流程卡死。 */
    private fun JsonObject.toRecurringRuleEntity(keepId: Boolean): RecurringRuleEntity? {
        val start = optLong("startMillis")
        val amount = optLong("amountMinor")
        if (start <= 0L || amount <= 0L) return null
        val repeatType = optInt("repeatType").takeIf { it in 0..2 } ?: REPEAT_MONTHLY
        val endType = optInt("endType").takeIf { it in 0..2 } ?: END_NEVER
        val created = optLong("createdMillis")
        return RecurringRuleEntity(
            id = if (keepId) optLong("id") else 0L,
            categoryId = optLong("categoryId"),
            amountMinor = amount,
            note = optString("note"),
            tags = optString("tags"),
            repeatType = repeatType,
            weekdays = optString("weekdays"),
            dayOfMonth = optInt("dayOfMonth").coerceIn(1, 31),
            startMillis = start,
            endType = endType,
            endMillis = optLong("endMillis"),
            totalCount = optInt("totalCount").coerceAtLeast(0),
            enabled = has("enabled").not() || optBoolean("enabled"),
            createdMillis = if (created > 0L) created else start
        )
    }

    private fun TransactionEntity.dedupeKey(): String =
        "$timeMillis|$amountMinor|$kind|$categoryId|$note"
}

private fun JsonObject.optArray(name: String): JsonArray {
    val element = get(name)
    return if (element != null && element.isJsonArray) element.asJsonArray else JsonArray()
}

private fun JsonObject.optLong(name: String, fallback: Long = 0L): Long =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull() ?: fallback

private fun JsonObject.optInt(name: String, fallback: Int = 0): Int =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull() ?: fallback

private fun JsonObject.optBoolean(name: String, fallback: Boolean = false): Boolean =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull() ?: fallback

private fun JsonObject.optString(name: String): String =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull() ?: ""

private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
