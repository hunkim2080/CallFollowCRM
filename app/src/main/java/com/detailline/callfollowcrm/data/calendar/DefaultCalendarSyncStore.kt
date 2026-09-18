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
    /** 건별 일정 번호 — 없으면(구버전 배선) 고객 표로 폴백. (2026-09-18) */
    private val jobDao: com.detailline.callfollowcrm.data.local.dao.JobDao? = null,
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

    /**
     * 일정 번호 읽기. **시공(WORK)은 건 전표에서** — 건마다 일정이 따로다. (2026-09-18)
     *   전엔 고객 표에 칸이 하나라, 2차를 잡으면 1차 일정이 2차 날짜로 옮겨졌다.
     *   jobId 가 없으면(A/S, 또는 건 없는 옛 데이터) 지금까지처럼 고객 표.
     */
    override suspend fun eventId(customerId: Long, type: ScheduleType, jobId: Long?): String? {
        if (type == ScheduleType.WORK && jobId != null) {
            return runCatching { jobDao?.findById(jobId)?.calendarEventId }.getOrNull()
        }
        val c = customerDao.findById(customerId) ?: return null
        return if (type == ScheduleType.WORK) c.workCalendarEventId else c.asCalendarEventId
    }

    override suspend fun setEventId(customerId: Long, type: ScheduleType, eventId: String?, jobId: Long?) {
        if (type == ScheduleType.WORK && jobId != null) {
            runCatching {
                jobDao?.findById(jobId)?.let { j ->
                    jobDao.update(j.copy(calendarEventId = eventId, updatedAt = System.currentTimeMillis()))
                }
            }
            return
        }
        val c = customerDao.findById(customerId) ?: return
        val updated = if (type == ScheduleType.WORK) {
            c.copy(workCalendarEventId = eventId)
        } else {
            c.copy(asCalendarEventId = eventId)
        }
        customerDao.update(updated)
    }

    /**
     * 시공일이 잡힌 **건**들 — (건 id, 그 건 값을 채운 고객 복사본).
     *   일정 탭(ScheduleViewModel)이 쓰는 것과 같은 방식: 건별 값을 채운 CustomerEntity 복사본을
     *   흘려보내면 기존 이벤트 만들기 코드가 **건 단위로 그대로 동작**한다.
     *   돈·완료도 건 것을 넣는다 — 캘린더 본문에 금액이 들어가기 때문.
     */
    /**
     * 취소돼 날짜가 사라졌는데 일정 번호는 남은 건들 — 날짜를 비운 복사본으로 돌려준다.
     *   받는 쪽(syncOne)이 "일정이 사라졌다"고 보고 구글에서 지운다. (2026-09-18)
     */
    override suspend fun unscheduledWorkJobsWithEvent(): List<Pair<Long, CustomerEntity>> {
        val dao = jobDao ?: return emptyList()
        val jobs = runCatching { dao.allOnce() }.getOrDefault(emptyList())
            .filter { it.scheduledWorkDate == null && !it.calendarEventId.isNullOrBlank() }
        if (jobs.isEmpty()) return emptyList()
        val byId = runCatching { customerDao.allOnce() }.getOrDefault(emptyList()).associateBy { it.id }
        return jobs.mapNotNull { j ->
            val c = byId[j.customerId] ?: return@mapNotNull null
            j.id to c.copy(
                scheduledWorkDate = null,
                scheduledWorkMinutes = null,
                // A/S 는 이 경로로 안 건드린다.
                asScheduledDate = null,
                asCalendarEventId = null
            )
        }
    }

    override suspend fun scheduledWorkJobs(): List<Pair<Long, CustomerEntity>> {
        val dao = jobDao ?: return emptyList()
        val jobs = runCatching { dao.scheduledOnce() }.getOrDefault(emptyList())
        if (jobs.isEmpty()) return emptyList()
        val byId = runCatching { customerDao.allOnce() }.getOrDefault(emptyList()).associateBy { it.id }
        return jobs.mapNotNull { j ->
            val c = byId[j.customerId] ?: return@mapNotNull null
            val day = j.scheduledWorkDate ?: return@mapNotNull null
            j.id to c.copy(
                scheduledWorkDate = day,
                scheduledWorkMinutes = j.scheduledWorkMinutes,
                scheduledWorkDays = j.scheduledWorkDays.coerceAtLeast(1),
                address = j.address?.takeIf { it.isNotBlank() } ?: c.address,
                totalAmount = j.totalAmount,
                depositAmount = j.depositAmount,
                depositPaidAt = j.depositPaidAt,
                balanceAmount = j.balanceAmount,
                balancePaidAt = j.balancePaidAt,
                workCompletedAt = j.workCompletedAt,
                memo = j.memo.takeIf { it.isNotBlank() } ?: c.memo,
                // A/S 는 이 경로로 안 올린다 — 건 복사본에 A/S 를 남기면 같은 A/S 가 건 수만큼 올라간다.
                asScheduledDate = null,
                asCalendarEventId = null
            )
        }
    }

    /** 지문은 prefs 에 (고객,종류[,건]) 별로. DB 마이그레이션 없이 붙이려고 — 지워져도 다시 올릴 뿐 손해 없음. */
    override suspend fun eventHash(customerId: Long, type: ScheduleType, jobId: Long?): String? =
        prefs.calendarEventHash(customerId, hashKey(type, jobId))

    override suspend fun setEventHash(customerId: Long, type: ScheduleType, hash: String?, jobId: Long?) {
        prefs.setCalendarEventHash(customerId, hashKey(type, jobId), hash)
    }

    /** 건마다 다른 지문 칸. 안 나누면 2차를 올린 뒤 1차가 '안 바뀜'으로 오해돼 안 올라간다. (2026-09-18) */
    private fun hashKey(type: ScheduleType, jobId: Long?): String =
        if (type == ScheduleType.WORK && jobId != null) type.key + ":" + jobId else type.key

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
