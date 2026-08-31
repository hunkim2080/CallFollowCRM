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
) : CalendarSyncStore {

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

    override suspend fun scheduledCustomers(): List<CustomerEntity> =
        customerDao.allOnce().filter {
            it.scheduledWorkDate != null || it.asScheduledDate != null ||
                it.workCalendarEventId != null || it.asCalendarEventId != null
        }
}
