package com.qingxi.ledger.data.tx

import com.qingxi.ledger.data.category.CategoryEntity

data class DayGroup(
    val dayStart: Long,
    val label: String,
    val expenseMinor: Long,
    val items: List<TransactionEntity>
)

data class CategoryStat(
    val category: CategoryEntity,
    val totalMinor: Long,
    val count: Int
)

data class MonthPoint(
    val monthKey: String,
    val expenseMinor: Long
)

data class TxFilter(
    val categoryId: Long? = null,
    val start: Long? = null,
    val end: Long? = null,
    val tag: String? = null,
    val keyword: String? = null
)

fun joinTags(tags: List<String>): String =
    tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

fun splitTags(raw: String): List<String> =
    raw.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

fun joinPaths(paths: List<String>): String = paths.filter { it.isNotBlank() }.joinToString(",")

fun splitPaths(raw: String): List<String> = raw.split(',').filter { it.isNotBlank() }
