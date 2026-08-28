package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.TimelineEventDao
import com.detailline.callfollowcrm.data.local.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 챗 타임라인 변경/처리 이력 이벤트 저장소 (2026-06-30) — [[IntakeEventRepository]] 와 같은 패턴.
 */
class TimelineEventRepository(private val dao: TimelineEventDao) {

    fun observe(phoneSuffix: String): Flow<List<TimelineEventEntity>> =
        dao.observeBySuffix(phoneSuffix)

    suspend fun record(event: TimelineEventEntity): Long = dao.insert(event)

    /** 사장님이 변경 이력을 고객에게 알린 시각 기록. */
    suspend fun markNotified(id: Long, ts: Long) = dao.markNotified(id, ts)

    /** 특정 타입 이벤트 삭제 — 잔금 '받음 처리' 취소 시 카드 제거. (2026-08-28 사장님) */
    suspend fun deleteByType(suffix: String, type: String) = dao.deleteByType(suffix, type)

    /** 날짜만 기록된 최근 일정 카드에 시간 채워넣기(날짜→시간 2단계). */
    suspend fun updateLatestScheduleNewValue(suffix: String, newValue: String) =
        dao.updateLatestScheduleNewValue(suffix, newValue)
}
