package com.detailline.callfollowcrm.data.calendar

import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.preferences.AppPreferences

/**
 * [CalendarSyncStore] 구현.
 * - calendarId → prefs(SharedPreferences)
 * - 이벤트 id → customers 테이블(v47 컬럼 workCalendarEventId / asCalendarEventId)
 *
 * 이벤트 id 저장은 **read + @Update**(전체 행) 로 한다 — CustomerDao 주석대로 @Query UPDATE 가
 * 일부 기기에서 조용히 실행 안 되던 이력이 있어, 검증된 안전 패턴을 따른다.
 */
class DefaultCalendarSyncStore(
    private val prefs: AppPreferences,
    private val customerDao: CustomerDao,
    private val issuedDocDao: com.detailline.callfollowcrm.data.local.dao.IssuedDocDao? = null,
    private val intakeEventDao: com.detailline.callfollowcrm.data.local.dao.IntakeEventDao? = null,
    /** 간단 일정 — 없으면(구버전 배선) 그냥 안 올린다. (2026-09-16) */
    private val simpleEventDao: com.detailline.callfollowcrm.data.local.dao.SimpleEventDao? = null,
) : CalendarSyncStore {

    /**
     * 캘린더 본문 재료 — 접수서(고객이 직접 적은 것) 우선, 없으면 내가 발행한 견적서.
     *   (2026-09-14 사장님: "접수서를 받으면 시공 내용도 있고 금액도 다 있을 거니까")
     */
    override suspend fun workDetail(c: CustomerEntity): WorkDetail? {
        val suffix = c.phoneNumber.filter { it.isDigit() }.takeLast(4)
        val intake = if (suffix.length == 4) {
            runCatching { intakeEventDao?.latestBySuffix(suffix) }.getOrNull()
        } else null
        val doc = runCatching { issuedDocDao?.latestByCustomer(c.id) }.getOrNull()
        val items = intake?.itemsText?.takeIf { it.isNotBlank() }
            ?: doc?.itemsText?.takeIf { it.isNotBlank() }
        val memo = intake?.customerMemo?.takeIf { it.isNotBlank() }
        val addr = intake?.address?.takeIf { it.isNotBlank() }
        return if (items == null && memo == null && addr == null) null
        else WorkDetail(itemsText = items, customerMemo = memo, address = addr)
    }

    override suspend fun getCalendarId(): String? = prefs.googleCalendarId
    override suspend fun setCalendarId(id: String?) { prefs.googleCalendarId = id }

    override suspend fun eventId(customerId: Long, type: ScheduleType): String? {
        val c = customerDao.findById(customerId) ?: return null
        return if (type == ScheduleType.WORK) c.workCalendarEventId else c.asCalendarEventId
    }

    override suspend fun setEventId(customerId: Long, type: ScheduleType, eventId: String?) {
        val c = customerDao.findById(customerId) ?: return
        val updated = if (type == ScheduleType.WORK) {
            c.copy(workCalendarEventId = eventId)
        } else {
            c.copy(asCalendarEventId = eventId)
        }
        customerDao.update(updated)
    }

    /** 지문은 prefs 에 (고객,종류) 별로. DB 마이그레이션 없이 붙이려고 — 지워져도 다시 올릴 뿐 손해 없음. */
    override suspend fun eventHash(customerId: Long, type: ScheduleType): String? =
        prefs.calendarEventHash(customerId, type.key)

    override suspend fun setEventHash(customerId: Long, type: ScheduleType, hash: String?) {
        prefs.setCalendarEventHash(customerId, type.key, hash)
    }

    override suspend fun simpleEvents(): List<com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity> =
        simpleEventDao?.allOnce().orEmpty()

    override suspend fun setSimpleEventId(id: Long, eventId: String?) {
        val dao = simpleEventDao ?: return
        val e = dao.findById(id) ?: return
        if (e.calendarEventId == eventId) return
        dao.update(e.copy(calendarEventId = eventId))
    }

    override suspend fun scheduledCustomers(): List<CustomerEntity> =
        customerDao.allOnce().filter {
            it.scheduledWorkDate != null || it.asScheduledDate != null ||
                it.workCalendarEventId != null || it.asCalendarEventId != null
        }
}
