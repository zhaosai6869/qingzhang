package com.qingxi.ledger.data.category

import com.qingxi.ledger.data.tx.TransactionDao
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao
) {

    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()

    fun observeExpenses(): Flow<List<CategoryEntity>> = dao.observeExpenses()

    suspend fun add(name: String, icon: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分类名不能为空" }
        val nextOrder = (dao.getAll().maxOfOrNull { it.sortOrder } ?: 0L) + 1
        return dao.insert(
            CategoryEntity(
                name = trimmed,
                icon = icon.ifBlank { "🏷" },
                kind = KIND_EXPENSE,
                sortOrder = nextOrder,
                isSystem = false
            )
        )
    }

    suspend fun update(entity: CategoryEntity) = dao.update(entity)

    /** 删除自定义分类；已有账单改挂到“其他”分类上。 */
    suspend fun delete(entity: CategoryEntity) {
        if (entity.isSystem) return
        val fallback = dao.getAll().firstOrNull { it.kind == entity.kind && it.name == "其他" }
            ?: dao.getAll().firstOrNull { it.kind == entity.kind && it.id != entity.id }
        if (fallback != null) {
            transactionDao.reassignCategory(entity.id, fallback.id)
        } else {
            transactionDao.deleteByCategory(entity.id)
        }
        dao.delete(entity)
    }

    suspend fun all(): List<CategoryEntity> = dao.getAll()
}
