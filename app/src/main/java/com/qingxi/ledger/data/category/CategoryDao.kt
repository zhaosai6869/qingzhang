package com.qingxi.ledger.data.category

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

const val KIND_EXPENSE = 1

// 应用只记支出。历史库里可能仍有 kind = 2 的收入分类与账单，读取时统一按 kind = 1 过滤，
// 数据本身保留，备份与导入仍可完整往返。

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val kind: Int,
    val sortOrder: Long,
    val isSystem: Boolean
)

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE kind = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeExpenses(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun exists(id: Long): Int
}
