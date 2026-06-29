package com.detailline.callfollowcrm.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM timeline_events WHERE phoneSuffix = :suffix ORDER BY createdAt DESC")
    fun observeBySuffix(suffix: String): Flow<List<TimelineEventEntity>>

    @Insert
    suspend fun insert(event: TimelineEventEntity): Long

    /** 사장님이 '고객에게 알리기' 눌러 문자 발송 → 알림 시각 기록(버튼 1회·상태 표시). */
    @Query("UPDATE timeline_events SET notifiedAt = :ts WHERE id = :id")
    suspend fun markNotified(id: Long, ts: Long)

    /**
     * 방금 만든 일정 카드(날짜만)에 시간을 채워넣음 — 날짜→시간 2단계 UI라 시간은 나중에 옴.
     *   since 이후 만들어진 가장 최근 schedule 카드의 newValue 를 "날짜 시간"으로 갱신. (2026-06-30)
     */
    @Query(
        "UPDATE timeline_events SET newValue = :newValue WHERE id = " +
            "(SELECT id FROM timeline_events WHERE phoneSuffix = :suffix AND type = 'schedule' " +
            "AND createdAt >= :since ORDER BY createdAt DESC LIMIT 1)"
    )
    suspend fun updateLatestScheduleNewValue(suffix: String, newValue: String, since: Long)
}
