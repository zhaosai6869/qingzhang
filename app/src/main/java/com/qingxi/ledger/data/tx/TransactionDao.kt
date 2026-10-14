package com.qingxi.ledger.data.tx

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

const val ORIGIN_MANUAL = 0
const val ORIGIN_VOICE = 1
const val ORIGIN_OCR = 2
const val ORIGIN_RECOGNIZED = 3

/**
 * `ruleId` + `dueDayKey` 是周期入账的幂等键：同一条规则的同一天只可能存在一条流水，
 * 重复确认、恢复备份后再确认都被唯一索引拦住。手工记录的这两列为 NULL，
 * SQLite 里 NULL 互不相等，所以不会互相冲突。
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["ruleId", "dueDayKey"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,
    val amountMinor: Long,
    val categoryId: Long,
    val note: String = "",
    val tags: String = "",
    val timeMillis: Long,
    val createdAtMillis: Long,
    val attachments: String = "",
    val origin: Int = ORIGIN_MANUAL,
    val ruleId: Long? = null,
    val dueDayKey: String? = null
)

data class DayTotal(
    val dayKey: String,
    val expenseMinor: Long
)

data class CategoryTotal(
    val categoryId: Long,
    val totalMinor: Long,
    val count: Int
)

@Dao
interface TransactionDao {

    // 所有面向界面的读取都限定 kind = 1（支出）。库里的历史收入记录不再展示，
    // 但 exportAll / count 保持全量，备份与导入仍能完整往返。
    @Query(
        """SELECT * FROM transactions
           WHERE kind = 1
             AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (:start IS NULL OR timeMillis >= :start)
             AND (:end IS NULL OR timeMillis <= :end)
             AND (:tag IS NULL OR ',' || tags || ',' LIKE '%,' || :tag || ',%')
             AND (:keyword IS NULL OR note LIKE '%' || :keyword || '%'
                  OR tags LIKE '%' || :keyword || '%'
                  OR CAST(amountMinor AS TEXT) LIKE '%' || :keyword || '%')
           ORDER BY timeMillis DESC, id DESC"""
    )
    fun observeFiltered(
        categoryId: Long?,
        start: Long?,
        end: Long?,
        tag: String?,
        keyword: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE kind = 1 ORDER BY timeMillis DESC, id DESC")
    fun observeExpenses(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """SELECT strftime('%Y-%m-%d', timeMillis / 1000, 'unixepoch', 'localtime') AS dayKey,
                  SUM(amountMinor) AS expenseMinor
           FROM transactions
           WHERE kind = 1 AND timeMillis BETWEEN :start AND :end
           GROUP BY dayKey ORDER BY dayKey"""
    )
    suspend fun dayTotals(start: Long, end: Long): List<DayTotal>

    @Query(
        """SELECT categoryId, SUM(amountMinor) AS totalMinor, COUNT(*) AS count
           FROM transactions
           WHERE kind = 1 AND timeMillis BETWEEN :start AND :end
           GROUP BY categoryId ORDER BY totalMinor DESC"""
    )
    suspend fun categoryTotals(start: Long, end: Long): List<CategoryTotal>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE kind = 1 AND timeMillis BETWEEN :start AND :end")
    suspend fun sumExpense(start: Long, end: Long): Long?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions")
    suspend fun exportAll(): List<TransactionEntity>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert
    suspend fun insertAll(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE transactions SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignCategory(sourceId: Long, targetId: Long)

    @Query("DELETE FROM transactions WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
