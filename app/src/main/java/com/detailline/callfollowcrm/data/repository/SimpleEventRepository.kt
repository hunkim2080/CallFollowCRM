package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.SimpleEventDao
import com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 간단 일정 — 번호 없이 달력에만 적는 메모형 일정. (2026-09-16 사장님)
 * 날짜는 항상 자정으로 정규화해서 저장한다(달력 점·그날 묶기가 전부 자정 기준).
 */
class SimpleEventRepository(private val dao: SimpleEventDao) {

    // 한 일정의 읽기-수정-쓰기 직렬화 — 제목 수정과 캘린더 id 기록이 겹쳐 한쪽이 덮이는 것 방지.
    private val writeMutex = Mutex()

    fun observeAll(): Flow<List<SimpleEventEntity>> = dao.observeAll()

    suspend fun allOnce(): List<SimpleEventEntity> = dao.allOnce()

    suspend fun findById(id: Long): SimpleEventEntity? = dao.findById(id)

    /** @param minutes 자정부터 분. null = 하루 종일. */
    suspend fun add(title: String, dayMs: Long, minutes: Int?, memo: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            SimpleEventEntity(
                title = title.trim(),
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                minutes = minutes,
                memo = memo.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun edit(id: Long, title: String, dayMs: Long, minutes: Int?, memo: String) = writeMutex.withLock {
        val e = dao.findById(id) ?: return@withLock
        dao.update(
            e.copy(
                title = title.trim(),
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                minutes = minutes,
                memo = memo.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 구글 캘린더에 올린 이벤트 id 기록(또는 지움). */
    suspend fun setCalendarEventId(id: Long, eventId: String?) = writeMutex.withLock {
        val e = dao.findById(id) ?: return@withLock
        if (e.calendarEventId == eventId) return@withLock
        // updatedAt 은 건드리지 않는다 — 캘린더에 올린 건 '내용 변경'이 아니다.
        dao.update(e.copy(calendarEventId = eventId))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
