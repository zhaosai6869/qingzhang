package com.qingxi.ledger.data.recurring

import kotlinx.coroutines.flow.Flow

class RecurringRepository(
    private val ruleDao: RecurringRuleDao,
    private val dismissalDao: RecurringDismissalDao
) {

    fun observeRules(): Flow<List<RecurringRuleEntity>> = ruleDao.observeAll()

    suspend fun enabledRules(): List<RecurringRuleEntity> = ruleDao.enabledRules()

    suspend fun dismissedKeys(): List<String> = dismissalDao.keys()

    fun observeEnabledCount(): Flow<Int> = ruleDao.observeEnabledCount()

    suspend fun getRule(id: Long): RecurringRuleEntity? = ruleDao.getById(id)

    suspend fun save(entity: RecurringRuleEntity): Long =
        if (entity.id == 0L) ruleDao.insert(entity) else entity.id.also { ruleDao.update(entity) }

    suspend fun setEnabled(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)

    /** 删规则连带它的跳过记录：留下的话 ruleId 会被自增主键复用，将来新规则可能撞上旧标记。 */
    suspend fun deleteRule(ruleId: Long) {
        ruleDao.getById(ruleId)?.let { ruleDao.delete(it) }
        dismissalDao.deleteByRule(ruleId)
    }

    /** 分类被删时与流水同一套策略：能改指向就改指向，否则整条规则作废。 */
    suspend fun reassignCategory(sourceId: Long, targetId: Long) = ruleDao.reassignCategory(sourceId, targetId)

    suspend fun deleteByCategory(categoryId: Long) = ruleDao.deleteByCategory(categoryId)

    fun observeDismissedKeys(): Flow<List<String>> = dismissalDao.observeKeys()

    suspend fun dismiss(ruleId: Long, dueDayKey: String) =
        dismissalDao.insert(RecurringDismissalEntity(ruleId, dueDayKey, System.currentTimeMillis()))

    suspend fun exportRules(): List<RecurringRuleEntity> = ruleDao.exportAll()

    suspend fun exportDismissals(): List<RecurringDismissalEntity> = dismissalDao.exportAll()

    suspend fun countRules(): Int = ruleDao.count()

    suspend fun insertRule(entity: RecurringRuleEntity): Long = ruleDao.insert(entity)

    suspend fun insertDismissals(entities: List<RecurringDismissalEntity>) =
        entities.forEach { dismissalDao.insert(it) }

    suspend fun deleteAllRules() {
        ruleDao.deleteAll()
        dismissalDao.deleteAll()
    }

    companion object {
        fun keyOf(ruleId: Long, dueDayKey: String): String = "$ruleId|$dueDayKey"
    }
}
