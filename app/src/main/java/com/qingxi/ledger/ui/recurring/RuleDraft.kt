package com.qingxi.ledger.ui.recurring

import com.qingxi.ledger.data.recurring.END_COUNT
import com.qingxi.ledger.data.recurring.END_NEVER
import com.qingxi.ledger.data.recurring.END_UNTIL
import com.qingxi.ledger.data.recurring.REPEAT_DAILY
import com.qingxi.ledger.data.recurring.REPEAT_MONTHLY
import com.qingxi.ledger.data.recurring.REPEAT_WEEKLY
import com.qingxi.ledger.data.recurring.RecurringRuleEntity
import com.qingxi.ledger.data.tx.splitTags
import com.qingxi.ledger.util.Dates
import com.qingxi.ledger.util.Money
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 规则编辑表单的临时状态：金额和次数按用户输入的原始字符串存着，
 * 只在通过校验后才换算成分，避免边打字边改写数值。
 */
data class RuleDraft(
    val id: Long = 0L,
    val categoryId: Long = 0L,
    val amountText: String = "",
    val note: String = "",
    val tags: String = "",
    val repeatType: Int = REPEAT_MONTHLY,
    val weekdays: List<Int> = emptyList(),
    val dayOfMonth: Int = 1,
    val startMillis: Long = Dates.startOfDay(System.currentTimeMillis()),
    val endType: Int = END_NEVER,
    val endMillis: Long = 0L,
    val totalCountText: String = "",
    val enabled: Boolean = true,
    val createdMillis: Long = 0L
) {

    fun toEntity(): RecurringRuleEntity = RecurringRuleEntity(
        id = id,
        categoryId = categoryId,
        amountMinor = Money.toMinor(amountText) ?: 0L,
        note = note.trim(),
        tags = splitTags(tags).joinToString(","),
        repeatType = repeatType,
        weekdays = weekdays.sorted().joinToString(","),
        dayOfMonth = if (repeatType == REPEAT_MONTHLY) dayOfMonth else 1,
        startMillis = Dates.startOfDay(startMillis),
        endType = endType,
        endMillis = if (endType == END_UNTIL) Dates.startOfDay(endMillis) else 0L,
        totalCount = if (endType == END_COUNT) totalCountText.toIntOrNull() ?: 0 else 0,
        enabled = enabled,
        createdMillis = if (createdMillis > 0L) createdMillis else System.currentTimeMillis()
    )

    companion object {
        fun from(entity: RecurringRuleEntity?): RuleDraft {
            if (entity == null) return RuleDraft()
            return RuleDraft(
                id = entity.id,
                categoryId = entity.categoryId,
                amountText = minorToText(entity.amountMinor),
                note = entity.note,
                tags = entity.tags,
                repeatType = entity.repeatType,
                weekdays = entity.weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .distinct()
                    .sorted(),
                dayOfMonth = entity.dayOfMonth.coerceIn(1, 31),
                startMillis = entity.startMillis,
                endType = entity.endType,
                endMillis = entity.endMillis,
                totalCountText = if (entity.endType == END_COUNT) entity.totalCount.toString() else "",
                enabled = entity.enabled,
                createdMillis = entity.createdMillis
            )
        }

        fun minorToText(minor: Long): String =
            if (minor % 100L == 0L) (minor / 100L).toString()
            else String.format(Locale.US, "%.2f", minor / 100.0)
    }
}

/** 校验规则草稿，返回要显示给用户的错误；全部通过返回 null。 */
fun validateDraft(draft: RuleDraft): String? {
    val amount = Money.toMinor(draft.amountText)
    return when {
        draft.categoryId == 0L -> "先选一个分类"
        amount == null || amount <= 0L -> "金额要大于 0"
        draft.repeatType == REPEAT_WEEKLY && draft.weekdays.isEmpty() -> "每周至少选一天"
        draft.endType == END_UNTIL && draft.endMillis < draft.startMillis -> "截止日期不能早于起始日期"
        draft.endType == END_COUNT && (draft.totalCountText.toIntOrNull() ?: 0) < 1 -> "总次数至少 1 次"
        else -> null
    }
}

/** 只允许数字和一个小数点，整数 8 位、小数 2 位以内。 */
fun sanitizeAmountText(raw: String): String {
    val builder = StringBuilder()
    var seenDot = false
    var decimals = 0
    var digits = 0
    for (ch in raw) {
        when {
            ch == '.' && !seenDot -> {
                seenDot = true
                if (builder.isEmpty()) builder.append('0')
                builder.append('.')
            }
            ch.isDigit() && decimals == 0 && digits < 8 -> {
                digits++
                builder.append(ch)
            }
            ch.isDigit() && decimals > 0 && decimals < 3 -> {
                decimals++
                builder.append(ch)
            }
        }
    }
    return builder.toString()
}

fun isoWeekdayLabel(iso: Int): String = when (iso) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

/** ISO 星期顺序固定从周一开始，与「周起始日」偏好无关——规则本身是按周几定义的。 */
val ISO_WEEKDAY_ORDER = listOf(1, 2, 3, 4, 5, 6, 7)

/** 列表行里的一句话说明，如「每月 15 号 · 到 10月31日」。 */
fun describeRule(rule: RecurringRuleEntity): String {
    val repeat = when (rule.repeatType) {
        REPEAT_DAILY -> "每天"
        REPEAT_WEEKLY -> {
            val days = rule.weekdays.split(',').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .distinct()
                .sorted()
            if (days.isEmpty()) "每周" else "每周 " + days.joinToString("、") { isoWeekdayLabel(it) }
        }
        else -> {
            val clamped = rule.dayOfMonth > 28
            "每月 ${rule.dayOfMonth} 号" + if (clamped) "（小月按月末）" else ""
        }
    }
    val end = when (rule.endType) {
        END_UNTIL -> "到 ${Dates.dayLabel(rule.endMillis)}"
        END_COUNT -> "共 ${rule.totalCount} 次"
        else -> "长期有效"
    }
    return "$repeat · $end"
}

/** DatePicker 用 UTC 零点表示日期，这里把本地当天毫秒换算过去。 */
fun localDayToUtcMillis(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** 反向换算回本地当天零点，与存进规则里的毫秒口径一致。 */
fun utcMillisToLocalDayStart(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
