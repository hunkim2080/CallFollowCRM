package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.detailline.callfollowcrm.data.local.entity.RecurringLogEntity
import com.detailline.callfollowcrm.data.local.entity.RecurringMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringMessageDao {

    // ── 규칙 ──────────────────────────────────────────────
    @Query("SELECT * FROM recurring_messages ORDER BY createdAt DESC")
    fun observeRules(): Flow<List<RecurringMessageEntity>>

    @Query("SELECT * FROM recurring_messages WHERE enabled = 1 ORDER BY createdAt DESC")
    fun observeEnabledRules(): Flow<List<RecurringMessageEntity>>

    @Query("SELECT * FROM recurring_messages WHERE id = :id")
    suspend fun findRuleById(id: Long): RecurringMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurringMessageEntity): Long

    @Update
    suspend fun updateRule(rule: RecurringMessageEntity)

    @Query("DELETE FROM recurring_messages WHERE id = :id")
    suspend fun deleteRule(id: Long)

    // ── 처리 로그 (보냄/넘김 중복 방지) ───────────────────────
    @Query("SELECT * FROM recurring_message_log")
    fun observeLogs(): Flow<List<RecurringLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RecurringLogEntity): Long

    /** 규칙 삭제 시 그 로그도 정리 (고아 로그 방지). */
    @Query("DELETE FROM recurring_message_log WHERE ruleId = :ruleId")
    suspend fun deleteLogsForRule(ruleId: Long)
}
