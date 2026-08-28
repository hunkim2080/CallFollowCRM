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

    /** 특정 타입 이벤트 삭제 — 예: 잔금 '받음 처리' 취소 시 그 카드 제거(잔상 방지). (2026-08-28 사장님) */
    @Query("DELETE FROM timeline_events WHERE phoneSuffix = :suffix AND type = :type")
    suspend fun deleteByType(suffix: String, type: String)

    /**
     * 방금 만든 일정 카드(날짜만)에 시간을 채워넣음 — 날짜→시간 2단계 UI라 시간은 나중에 옴.
     *   이 고객(suffix)의 '가장 최근 schedule 카드'를 갱신. (2026-06-30)
     *   ⚠️ 2026-08-08 stale 감사: 예전엔 createdAt >= since(5분) 창으로 제한해, 시간을 5분 넘겨/다시 넣으면
     *      옛 카드만 남고 시간이 영영 안 붙었음 → 시간창 제거하고 늘 최신 카드를 갱신.
     */
    @Query(
        "UPDATE timeline_events SET newValue = :newValue WHERE id = " +
            "(SELECT id FROM timeline_events WHERE phoneSuffix = :suffix AND type = 'schedule' " +
            "ORDER BY createdAt DESC LIMIT 1)"
    )
    suspend fun updateLatestScheduleNewValue(suffix: String, newValue: String)
}
