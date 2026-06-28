package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.IntakeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeEventDao {
    @Query("SELECT * FROM intake_events WHERE phoneSuffix = :suffix ORDER BY submittedAtMs DESC")
    fun observeBySuffix(suffix: String): Flow<List<IntakeEventEntity>>

    /** token unique 라 같은 제출 재폴링 시 IGNORE — 중복 카드 방지. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: IntakeEventEntity): Long

    /** 사장님이 '확인했어요' 눌러 고객에게 확인 문자 발송 → 확인 시각 기록(버튼 1회·상태 표시). (2026-06-28) */
    @Query("UPDATE intake_events SET confirmedAt = :ts WHERE token = :token")
    suspend fun markConfirmed(token: String, ts: Long)
}
