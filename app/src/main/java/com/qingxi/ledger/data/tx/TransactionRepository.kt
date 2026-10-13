package com.qingxi.ledger.data.tx

import com.qingxi.ledger.data.category.KIND_EXPENSE
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {

    fun observeFiltered(filter: TxFilter): Flow<List<TransactionEntity>> = dao.observeFiltered(
        categoryId = filter.categoryId,
        start = filter.start,
        end = filter.end,
        tag = filter.tag?.trim()?.takeIf { it.isNotEmpty() },
        keyword = filter.keyword?.trim()?.takeIf { it.isNotEmpty() }
    )

    fun observeExpenses(): Flow<List<TransactionEntity>> = dao.observeExpenses()

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    suspend fun insert(entity: TransactionEntity): Long = dao.insert(entity)

    suspend fun insertAll(entities: List<TransactionEntity>) = dao.insertAll(entities)

    suspend fun update(entity: TransactionEntity) = dao.update(entity)

    suspend fun delete(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun count(): Int = dao.count()

    suspend fun exportAll(): List<TransactionEntity> = dao.exportAll()

    suspend fun dayTotals(start: Long, end: Long): List<DayTotal> = dao.dayTotals(start, end)

    suspend fun categoryTotals(start: Long, end: Long): List<CategoryTotal> =
        dao.categoryTotals(start, end)

    suspend fun sumExpense(start: Long, end: Long): Long = dao.sumExpense(start, end) ?: 0L

    suspend fun usedTags(): List<String> =
        exportAll().filter { it.kind == KIND_EXPENSE }.flatMap { splitTags(it.tags) }.distinct().sorted()
}
