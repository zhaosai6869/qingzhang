package com.qingxi.ledger.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

const val CENTS_PER_YUAN = 100L

private val moneyFormat = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale.getDefault())
)

object Money {

    fun format(minor: Long): String = moneyFormat.format(minor / 100.0)

    /** 账单只有支出，流水里的金额统一带负号。 */
    fun formatSigned(minor: Long): String = "-" + moneyFormat.format(minor / 100.0)

    fun toMinor(yuanText: String): Long? {
        val cleaned = yuanText.trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0) return null
        return Math.round(value * CENTS_PER_YUAN)
    }
}
