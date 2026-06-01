package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.ai.IntakeFormRepository
import com.detailline.callfollowcrm.data.local.dao.IntakeFormDao
import com.detailline.callfollowcrm.data.local.entity.IntakeFormEntity
import kotlinx.coroutines.flow.Flow

/**
 * 시공접수서 동기화 — 발급 시 즉시 DB 박음 + 홈 진입 시 활성 phone 들 status() 폴.
 *
 * 1단계 (현재): DB 캐시 + status() 폴링만. UI 표시 X.
 * 2단계 (다음 sprint): 홈 상단 "들어왔어요" / "작성 대기" 카드 + 고객상세 payload 표시.
 * 3단계: 사장님 [1탭 확정] → settledAtMs 박힘 + 일정/정산/고객 자동 등록 (프로토 confirmQuoteBooking 1:1).
 */
class IntakeFormSyncRepository(
    private val dao: IntakeFormDao,
    private val remote: IntakeFormRepository
) {
    /** 발급 직후 호출 — 로컬 DB 에 즉시 박음 (status() 폴 없이도 "작성 대기" 카드 표시 가능). */
    suspend fun onIssued(
        token: String,
        url: String,
        phone: String,
        customerName: String?,
        issuedAtMs: Long,
        expiresAtMs: Long,
        scheduledAtMs: Long,
        scheduledDays: Int,
        totalMan: Int,
        depositAmountKrw: Long,
        depositMode: String,
        depositRatioPct: Int?,
        bizName: String?,
        estimateItemsJson: String?
    ) {
        val now = System.currentTimeMillis()
        dao.upsert(
            IntakeFormEntity(
                token = token,
                phoneSuffix = phoneSuffix(phone),
                phone = phone,
                customerName = customerName,
                url = url,
                issuedAtMs = issuedAtMs,
                expiresAtMs = expiresAtMs,
                submittedAtMs = null,
                payloadJson = null,
                scheduledAtMs = scheduledAtMs,
                scheduledDays = scheduledDays,
                totalMan = totalMan,
                depositAmountKrw = depositAmountKrw,
                depositMode = depositMode,
                depositRatioPct = depositRatioPct,
                bizName = bizName,
                estimateItemsJson = estimateItemsJson,
                settledAtMs = null,
                lastSyncedAtMs = now,
                createdAt = now
            )
        )
    }

    /**
     * 활성(만료 안 됐고 settled 안 된) 발급분 모두 status() 폴 → DB 갱신.
     *   네트워크 실패는 개별 무시 (best-effort). 호출자가 IO 디스패처 책임.
     */
    suspend fun syncActive(deviceId: String? = null): Int {
        val now = System.currentTimeMillis()
        val targets = dao.activeOnce(now)
        var updated = 0
        for (e in targets) {
            val status = remote.status(e.phone, deviceId).getOrNull() ?: continue
            dao.upsert(
                e.copy(
                    submittedAtMs = status.submittedAtMs ?: e.submittedAtMs,
                    payloadJson = status.payloadJson ?: e.payloadJson,
                    scheduledAtMs = status.scheduledAtMs.takeIf { it > 0 } ?: e.scheduledAtMs,
                    scheduledDays = status.scheduledDays,
                    totalMan = status.totalMan.takeIf { it > 0 } ?: e.totalMan,
                    depositAmountKrw = status.depositAmountKrw,
                    depositMode = status.depositMode,
                    depositRatioPct = status.depositRatioPct,
                    bizName = status.bizName ?: e.bizName,
                    estimateItemsJson = status.estimateItemsJson ?: e.estimateItemsJson,
                    lastSyncedAtMs = now
                )
            )
            updated++
        }
        return updated
    }

    fun observeSubmittedNotSettled(): Flow<List<IntakeFormEntity>> =
        dao.observeSubmittedNotSettled()

    fun observePendingNotExpired(): Flow<List<IntakeFormEntity>> =
        dao.observePendingNotExpired(System.currentTimeMillis())

    fun observeByPhoneSuffix(phone: String): Flow<List<IntakeFormEntity>> =
        dao.observeByPhoneSuffix(phoneSuffix(phone))

    suspend fun markSettled(token: String) {
        val e = dao.byToken(token) ?: return
        dao.upsert(e.copy(settledAtMs = System.currentTimeMillis()))
    }

    private fun phoneSuffix(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(8)
}
