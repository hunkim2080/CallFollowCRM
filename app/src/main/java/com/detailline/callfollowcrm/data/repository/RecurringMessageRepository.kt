package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.RecurringMessageDao
import com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity
import kotlinx.coroutines.flow.Flow

/** 정기문자 규칙 + 처리 로그 (2026-06-01, DB v25). */
class RecurringMessageRepository(private val dao: RecurringMessageDao) {

    fun observeRules(): Flow<List<RecurringMessageEntity>> = dao.observeRules()
    fun observeEnabledRules(): Flow<List<RecurringMessageEntity>> = dao.observeEnabledRules()
    fun observeLogs(): Flow<List<RecurringLogEntity>> = dao.observeLogs()

    suspend fun add(
        name: String,
        targetCategoryId: Long?,
        intervalDays: Int,
        sendMinutes: Int,
        bodyTemplate: String
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertRule(
            RecurringMessageEntity(
                name = name.trim(),
                targetCategoryId = targetCategoryId,
                intervalDays = intervalDays,
                sendMinutes = sendMinutes,
                bodyTemplate = bodyTemplate.trim(),
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun update(
        id: Long,
        name: String,
        targetCategoryId: Long?,
        intervalDays: Int,
        sendMinutes: Int,
        bodyTemplate: String,
        enabled: Boolean
    ) {
        val cur = dao.findRuleById(id) ?: return
        dao.updateRule(
            cur.copy(
                name = name.trim(),
                targetCategoryId = targetCategoryId,
                intervalDays = intervalDays,
                sendMinutes = sendMinutes,
                bodyTemplate = bodyTemplate.trim(),
                enabled = enabled,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val cur = dao.findRuleById(id) ?: return
        dao.updateRule(cur.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) {
        dao.deleteRule(id)
        dao.deleteLogsForRule(id)
    }

    /** occurrence 처리(보냄/넘김) 기록 → "보낼 때 된" 목록에서 제외. */
    suspend fun logOccurrence(ruleId: Long, customerId: Long, occurrenceDayStartMs: Long, status: String) {
        dao.insertLog(
            RecurringLogEntity(
                ruleId = ruleId,
                customerId = customerId,
                occurrenceDayStartMs = occurrenceDayStartMs,
                status = status,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
