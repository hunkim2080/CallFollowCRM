package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.JobCrewDao
import com.detailline.callfollowcrm.data.local.entity.JobCrewEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock

/** 일당 배정 — 시공(고객+날짜)에 일당 배정. 함께한 현장 + 일당 자동차감의 단일 출처. */
class JobCrewRepository(private val dao: JobCrewDao) {

    // assign 의 findAssignment→insert 는 비원자 → 연타/동시호출 시 2행 삽입(일당 이중차감). Mutex 로 직렬화. (2026-07-30 버그감사)
    private val assignMutex = kotlinx.coroutines.sync.Mutex()

    fun observeAll(): Flow<List<JobCrewEntity>> = dao.observeAll()
    fun observeByWorker(workerId: Long): Flow<List<JobCrewEntity>> = dao.observeByWorker(workerId)

    /** 이 시공(고객+날짜)에 지금 배정된 일당들. */
    fun observeByJob(customerId: Long, dayMs: Long): Flow<List<JobCrewEntity>> =
        dao.observeByJob(customerId, DateTimeUtils.startOfDay(dayMs))

    /**
     * 일당 배정 — **같은 사람·같은 시공·같은 날은 한 번만**(멱등). 이미 있으면 일당 금액만 갱신.
     *
     * ⚠️ 왜 멱등이어야 하나: job_crew 테이블엔 (workerId, customerId, dayStartMs) 조합 유니크 인덱스가 없고
     *   DAO 는 그냥 @Insert 라, 두 번 배정하면 **행이 2개** 생긴다.
     *   그러면 CashFlowCalc 가 그 날 일당을 **두 번 지출로 계산**한다(25만원 → 50만원). 돈이 틀어지는 버그.
     *   같은 번호·같은 날로 "일정 추가"를 두 번만 해도 재현된다(saveSchedule 이 upsert 후 assign 하므로).
     *   스키마(유니크 인덱스)로 막는 게 정석이나 마이그레이션 위험이 있어, 우선 여기서 한 곳으로 막는다.
     *   (2026-07-16 — 기존 일정에 일당 배정 UI 를 붙이면서 발견. 배정 UI 는 연타/재배정이 흔하다.)
     */
    suspend fun assign(workerId: Long, workerName: String, customerId: Long, dayMs: Long, wage: Long): Long = assignMutex.withLock {
        val day = DateTimeUtils.startOfDay(dayMs)
        val w = wage.coerceAtLeast(0L)
        val existing = dao.findAssignment(workerId, customerId, day)
        if (existing != null) {
            if (existing.wage != w) dao.updateWage(existing.id, w)
            return@withLock existing.id
        }
        dao.insert(
            JobCrewEntity(
                workerId = workerId,
                workerName = workerName,
                customerId = customerId,
                dayStartMs = day,
                wage = w,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** 배정 빼기 — 배정 화면에서 체크를 끄면. (잘못 부른 사람 정리) */
    suspend fun unassign(workerId: Long, customerId: Long, dayMs: Long) =
        dao.deleteAssignment(workerId, customerId, DateTimeUtils.startOfDay(dayMs))

    suspend fun remove(id: Long) = dao.deleteById(id)

    /** 예약 취소 시 이 고객 현장의 일당 배정(자동지출) 전부 정리 — 없던 시공의 −일당이 현금흐름에 잔존 방지. (2026-07-30 버그감사) */
    suspend fun deleteByCustomer(customerId: Long) = dao.deleteByCustomer(customerId)

    /** 예약 취소 시 '그 시공일'의 일당 배정만 정리 — 과거 이미 지급한 다른 날 일당 지출은 보존. (2026-08-11 돈 감사 rank5) */
    suspend fun deleteByCustomerAndDay(customerId: Long, dayMs: Long) =
        dao.deleteByCustomerAndDay(customerId, DateTimeUtils.startOfDay(dayMs))
}
