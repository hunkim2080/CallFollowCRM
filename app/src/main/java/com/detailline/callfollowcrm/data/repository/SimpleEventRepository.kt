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

    /** @param minutes 자정부터 분. null = 하루 종일. @param days 며칠(1=하루). (v64) */
    suspend fun add(title: String, dayMs: Long, minutes: Int?, memo: String, days: Int = 1): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            SimpleEventEntity(
                title = title.trim(),
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                minutes = minutes,
                days = days.coerceAtLeast(1),
                memo = memo.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun edit(id: Long, title: String, dayMs: Long, minutes: Int?, memo: String, days: Int = 1) = writeMutex.withLock {
        val e = dao.findById(id) ?: return@withLock
        dao.update(
            e.copy(
                title = title.trim(),
                dayStartMs = DateTimeUtils.startOfDay(dayMs),
                minutes = minutes,
                days = days.coerceAtLeast(1),
                memo = memo.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 📥 외부 캘린더에서 가져온 일정들을 간단 일정으로 넣는다. (2026-10-31 사장님)
     *   · **이미 가져온 건(importSourceId) 제외** → 두 번 눌러도 안 겹침.
     *   · 날짜는 자정 정규화(add 가 해줌). 가져온 건 importSourceId 를 박아 **재export 안 되게**(루프 방지).
     *   @return 이번에 새로 들어온 개수.
     */
    suspend fun importFromDevice(events: List<com.detailline.callfollowcrm.util.DeviceCalendars.ImportedEvent>): Int = writeMutex.withLock {
        if (events.isEmpty()) return@withLock 0
        val already = runCatching { dao.importedSourceIds() }.getOrDefault(emptyList()).toHashSet()
        val now = System.currentTimeMillis()
        var n = 0
        for (e in events) {
            if (e.sourceId in already) continue
            already += e.sourceId   // 같은 가져오기 안의 중복도 막음
            dao.insert(
                SimpleEventEntity(
                    title = e.title.trim(),
                    dayStartMs = DateTimeUtils.startOfDay(e.dayStartMs),
                    minutes = e.minutes,
                    days = e.days.coerceAtLeast(1),
                    memo = e.memo.trim(),
                    importSourceId = e.sourceId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            n++
        }
        n
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
