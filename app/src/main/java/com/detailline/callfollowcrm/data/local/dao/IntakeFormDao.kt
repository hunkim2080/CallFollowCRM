package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.IntakeFormEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeFormDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: IntakeFormEntity)

    @Query("SELECT * FROM intake_forms WHERE token = :token LIMIT 1")
    suspend fun byToken(token: String): IntakeFormEntity?

    @Query("SELECT * FROM intake_forms WHERE phoneSuffix = :suffix ORDER BY issuedAtMs DESC")
    fun observeByPhoneSuffix(suffix: String): Flow<List<IntakeFormEntity>>

    /** 홈 폴링 대상: 만료 안 됐고, 아직 settled 안 된 것들 (미작성 OR 제출됐는데 미확정). */
    @Query("SELECT * FROM intake_forms WHERE expiresAtMs > :nowMs AND settledAtMs IS NULL ORDER BY issuedAtMs DESC")
    suspend fun activeOnce(nowMs: Long): List<IntakeFormEntity>

    /** 홈 상단 "들어왔어요" 카드 후보 — 제출 완료 + 미확정. */
    @Query("SELECT * FROM intake_forms WHERE submittedAtMs IS NOT NULL AND settledAtMs IS NULL ORDER BY submittedAtMs DESC")
    fun observeSubmittedNotSettled(): Flow<List<IntakeFormEntity>>

    /** 홈 상단 "작성 대기" 카드 후보 — 발급됐고, 미작성, 만료 전. */
    @Query("SELECT * FROM intake_forms WHERE submittedAtMs IS NULL AND expiresAtMs > :nowMs ORDER BY issuedAtMs DESC")
    fun observePendingNotExpired(nowMs: Long): Flow<List<IntakeFormEntity>>

    @Query("DELETE FROM intake_forms WHERE token = :token")
    suspend fun deleteByToken(token: String)
}
