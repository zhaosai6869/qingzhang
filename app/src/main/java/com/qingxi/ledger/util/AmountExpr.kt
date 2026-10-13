package com.qingxi.ledger.util

import java.util.Locale

/**
 * 记账算式求值：支持 + - × ÷，先乘除后加减；允许以运算符结尾（忽略未完成的尾部）。
 */
object AmountExpr {

    private val OPERATORS = charArrayOf('+', '-', '×', '÷')

    fun eval(text: String): Double? {
        val tokens = tokenize(text) ?: return null
        if (tokens.isEmpty()) return null

        val reduced = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token == "×" || token == "÷") {
                val left = reduced.removeAt(reduced.lastIndex).toDouble()
                val right = tokens.getOrNull(index + 1)?.toDoubleOrNull() ?: return null
                val result = if (token == "×") left * right else {
                    if (right == 0.0) return null else left / right
                }
                reduced.add(result.toString())
                index += 2
            } else {
                reduced.add(token)
                index++
            }
        }

        var total = reduced.firstOrNull()?.toDoubleOrNull() ?: return null
        var cursor = 1
        while (cursor + 1 < reduced.size) {
            val operator = reduced[cursor]
            val operand = reduced[cursor + 1].toDoubleOrNull() ?: return null
            total = if (operator == "-") total - operand else total + operand
            cursor += 2
        }
        return if (cursor == reduced.size) total else null
    }

    fun evalMinor(text: String): Long? = eval(text)
        ?.takeIf { it.isFinite() && it >= 0 }
        ?.let { Math.round(it * CENTS_PER_YUAN) }

    /** 输入过程中实时显示的合计，无效或未输入时返回 "0"。 */
    fun preview(text: String): String =
        String.format(Locale.US, "%.2f", eval(text) ?: 0.0)

    fun hasOperator(text: String): Boolean = text.any { it in OPERATORS || it == '*' || it == '/' }

    private fun tokenize(text: String): List<String>? {
        val normalized = text.trim().replace('*', '×').replace('/', '÷').trimEnd(*OPERATORS)
        if (normalized.isEmpty()) return null

        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        var expectNumber = true
        for (char in normalized) {
            if (char.isDigit() || char == '.') {
                if (!expectNumber) return null
                number.append(char)
            } else if (char in OPERATORS) {
                if (number.isNotEmpty()) {
                    if (!isNumber(number.toString())) return null
                    tokens.add(number.toString())
                    number.setLength(0)
                    expectNumber = true
                } else if (char == '-' && tokens.isEmpty()) {
                    number.append('0')
                    tokens.add("0")
                    expectNumber = true
                } else {
                    return null
                }
                tokens.add(char.toString())
            } else {
                return null
            }
        }
        if (number.isNotEmpty()) {
            if (!isNumber(number.toString())) return null
            tokens.add(number.toString())
        } else if (tokens.isNotEmpty()) {
            tokens.removeAt(tokens.lastIndex)
        }
        if (tokens.isEmpty()) return null
        if (tokens.count() % 2 != 1) return null
        return tokens
    }

    private fun isNumber(text: String): Boolean =
        text != "." && text.count { it == '.' } <= 1 && text.all { it.isDigit() || it == '.' }
}
