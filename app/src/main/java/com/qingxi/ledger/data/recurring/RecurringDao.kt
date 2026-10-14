package com.qingxi.ledger.data.recurring

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules ORDER BY enabled DESC, createdMillis DESC, id DESC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY createdMillis DESC, id DESC")
    suspend fun exportAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("SELECT COUNT(*) FROM recurring_rules")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert
    suspend fun insert(entity: RecurringRuleEntity): Long

    @Update
    suspend fun update(entity: RecurringRuleEntity)

    @Delete
    suspend fun delete(entity: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE recurring_rules SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignCategory(sourceId: Long, targetId: Long)

    @Query("DELETE FROM recurring_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAll()
}

@Dao
interface RecurringDismissalDao {

    @Query("SELECT * FROM recurring_dismissals")
    suspend fun exportAll(): List<RecurringDismissalEntity>

    /** 一次读全部再在内存里比对：跳过记录只会在「确认清单」上产生，量级很小。 */
    @Query("SELECT ruleId || '|' || dueDayKey FROM recurring_dismissals")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: RecurringDismissalEntity)

    @Query("DELETE FROM recurring_dismissals WHERE ruleId = :ruleId")
    suspend fun deleteByRule(ruleId: Long)

    @Query("DELETE FROM recurring_dismissals")
    suspend fun deleteAll()
}
