package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.CallSummaryDao
import com.detailline.callfollowcrm.data.local.dao.CustomerDao
import com.detailline.callfollowcrm.data.local.dao.RecordingAttachmentDao
import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.domain.model.LeadHeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CustomerRepository(
    private val dao: CustomerDao,
    // 새 Customer 가 만들어질 때 같은 번호의 orphan 첨부/요약을 자동 연결하기 위해 주입.
    // 테스트 편의를 위해 nullable 로 두고, 운영 코드(AppContainer)에서는 항상 채워준다.
    private val recordingDao: RecordingAttachmentDao? = null,
    private val callSummaryDao: CallSummaryDao? = null
) {

    fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<CustomerEntity?> = dao.observeById(id)

    // 2026-05-25: observeByStatusLabel 제거 — PipelineScreen 폐기 + status enum 폐기.

    /** 시공 예약일이 설정된 모든 고객을 예약일 오름차순으로. */
    fun observeScheduled(): Flow<List<CustomerEntity>> = dao.observeScheduled()

    /** A/S 예약 잡힌 고객 — 캘린더 A/S 주황 마커·A/S 목록용. 시공 예약과 별개. (DB v43) */
    fun observeAsScheduled(): Flow<List<CustomerEntity>> = dao.observeAsScheduled()

    /**
     * 이름이 비어있는 고객의 이름을 기기 연락처(삼성)에서 채운다 — "저장돼 있으면 그대로 반영". 2026-07-21 사장님.
     *   READ_CONTACTS 있을 때만. **자체 저장한 이름(비어있지 않음)은 절대 안 건드림**(수동 우선).
     *   name 을 쓰는 표시 자리(고객목록·카드·알림 등 40여 곳)가 이걸로 자동 반영된다.
     *   @return 채운 건수.
     */
    suspend fun fillBlankNamesFromContacts(context: android.content.Context): Int {
        if (!com.detailline.callfollowcrm.util.ContactNameResolver.hasPermission(context)) return 0
        val all = runCatching { dao.allOnce() }.getOrDefault(emptyList())
        var filled = 0
        for (c in all) {
            if (!c.name.isNullOrBlank()) continue
            val nm = com.detailline.callfollowcrm.util.ContactNameResolver.lookup(context, c.phoneNumber) ?: continue
            runCatching { dao.update(c.copy(name = nm, updatedAt = System.currentTimeMillis())) }.onSuccess { filled++ }
        }
        return filled
    }

    /**
     * P3 — 사장님의 다른 시공 일정 (현재 고객 제외, 오늘부터 N일 내) 만 epoch ms 로.
     * AI 답변 추천 / 대화 요약의 일정 응답 근거.
     * 다른 고객 이름은 leak 금지 → ms 만 반환. 서버가 요일/오전오후로 가공.
     */
    suspend fun getOtherUpcomingScheduleDates(
        excludePhone: String,
        daysAhead: Int = 14
    ): List<Long> {
        val now = System.currentTimeMillis()
        val cutoff = now + daysAhead * 24L * 3600L * 1000L
        return runCatching {
            observeScheduled().first()
                .asSequence()
                .filter { it.phoneNumber != excludePhone }
                .mapNotNull { it.scheduledWorkDate }
                .filter { it in now..cutoff }
                .sorted()
                .toList()
        }.getOrDefault(emptyList())
    }

    suspend fun findByPhone(phoneNumber: String): CustomerEntity? = dao.findByPhone(phoneNumber)
    suspend fun findById(id: Long): CustomerEntity? = dao.findById(id)
    /** 2026-05-30 #7 — AutoCategoryClassifier.backfillAll 용. */
    suspend fun allOnce(): List<CustomerEntity> = dao.allOnce()

    /**
     * 같은 전화번호로 들어오면 기존 Customer를 재사용한다.
     * 2026-05-25: status 컬럼 v13 마이그레이션에서 drop — 카테고리 시스템으로 통일.
     */
    suspend fun upsertByPhone(
        phoneNumber: String,
        name: String? = null,
        memo: String? = null,
        leadHeat: LeadHeat? = null
    ): CustomerEntity {
        val now = System.currentTimeMillis()
        val existing = dao.findByPhone(phoneNumber)
        return if (existing == null) {
            val entity = CustomerEntity(
                phoneNumber = phoneNumber,
                name = name,
                memo = memo.orEmpty(),
                leadHeat = leadHeat?.name,
                createdAt = now,
                updatedAt = now
            )
            val id = dao.insert(entity)
            if (id == -1L) {
                // 동시 생성 경합(unique phoneNumber 충돌 → @Insert(IGNORE) 가 -1 반환). 이미 만들어진 행을 재조회해 반환
                //   — id=-1 로 orphan 연결·이후 일정/금액 update 가 무음 증발하던 것 방지. (2026-07-30 버그감사)
                dao.findByPhone(phoneNumber) ?: entity.copy(id = id)
            } else {
                // 같은 번호로 먼저 들어와 있던 orphan 녹음/요약을 이 고객으로 자동 연결.
                // (정책: 녹음/요약 import 는 Customer 를 자동 생성하지 않는다.)
                recordingDao?.linkOrphansToCustomer(phoneNumber, id)
                callSummaryDao?.linkOrphansToCustomer(phoneNumber, id)
                entity.copy(id = id)
            }
        } else {
            val mergedMemo = when {
                memo.isNullOrBlank() -> existing.memo
                existing.memo.isBlank() -> memo
                else -> existing.memo + "\n---\n" + memo
            }
            val updated = existing.copy(
                name = name ?: existing.name,
                memo = mergedMemo,
                leadHeat = leadHeat?.name ?: existing.leadHeat,
                updatedAt = now
            )
            dao.update(updated)
            updated
        }
    }

    // 2026-05-25: updateStatus 제거 — 카테고리 시스템으로 통일.

    suspend fun updateMemo(id: Long, memo: String) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(memo = memo, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateName(id: Long, name: String?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    /** 리드 온도 설정/변경/해제(null). 통화 직후 카드에서 optimistic 저장 호출. */
    suspend fun updateLeadHeat(id: Long, leadHeat: LeadHeat?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(leadHeat = leadHeat?.name, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 계약금 정보 갱신. amount 또는 paidAt 둘 중 하나만 변경하고 싶을 때 다른 인자에
     * `Unchanged` 를 넘기면 보존 (Kotlin 의 null 은 "값 지움" 으로 쓰이기 때문).
     */
    suspend fun updateDepositAmount(id: Long, amount: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(depositAmount = amount, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 계약금 이중곱 오염 self-heal (2026-07-09) — 앱 시작 시 1회. 2026-07-04~ intake fixed 계약금이 서버왕복 후
     *   ×10000 이중적용돼 원값이 10만원→10억(1e9)으로 저장된 row 를 ÷10000 보정. 오탐0 조건(모두 AND):
     *   만원 배수 + 1억(1e8) 이상(이 도메인 계약금으로 비현실적=이중곱 산물만) + 총액 초과(진짜 계약금은 총액 못 넘음).
     *   ※ @Query UPDATE 가 기기(S9/안드10) 에서 조용히 실행 안 되던 문제로 Kotlin(allOnce+update)으로 구현 + 로그. (2026-07-10)
     * @return 보정된 row 수 (정상 폰은 0)
     */
    suspend fun healCorruptedDeposits(): Int {
        val corrupted = dao.allOnce().filter { c ->
            val d = c.depositAmount
            val t = c.totalAmount
            d != null && t != null && d % 10000L == 0L && d >= 100_000_000L && d > t
        }
        for (c in corrupted) {
            dao.update(c.copy(depositAmount = c.depositAmount!! / 10000L, updatedAt = System.currentTimeMillis()))
        }
        android.util.Log.i("DepositHeal", "healed ${corrupted.size} double-mul deposits: ids=${corrupted.map { it.id }}")
        return corrupted.size
    }

    /** paidAt = null 이면 "안 받음" 상태로 되돌리기. */
    suspend fun updateDepositPaidAt(id: Long, paidAt: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(depositPaidAt = paidAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBalanceAmount(id: Long, amount: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(balanceAmount = amount, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBalancePaidAt(id: Long, paidAt: Long?) {
        val c = dao.findById(id) ?: return
        // 완납(잔금 받음) 처리 시, 계약금이 아직 '받음' 미표시(depositPaidAt=null)면서 계약금이 있으면 같이 찍는다.
        //   안 그러면 완납인데도 계약금 몫이 '입금일 기준' 집계(현금흐름·이번 달 받은 돈·리포트·마감브리핑)에서
        //   누락돼 증발함(정산 목록만 30만, 나머지는 20만). 이미 받은 날짜가 있으면 존중(안 덮음). (2026-07-30 버그감사)
        val alsoDeposit = paidAt != null && c.depositPaidAt == null && (c.depositAmount ?: 0L) > 0L
        // 완납 '되돌리기'(paidAt=null) 대칭 처리: 완납 때 잔금과 '함께 자동으로' 찍힌 계약금(두 입금시각이 동일)이면
        //   계약금도 같이 '안 받음'으로 되돌린다. 안 그러면 안 받은 계약금이 매출·현금흐름에 '유령'으로 남아 과대 집계.
        //   단 사장님이 계약금을 따로(다른 날) 받아 표시한 경우(두 시각 다름)는 존중해 안 건드림. (2026-08-11 돈 정확성 감사 rank4)
        val undoAutoDeposit = paidAt == null && c.depositPaidAt != null && c.depositPaidAt == c.balancePaidAt
        dao.update(
            c.copy(
                balancePaidAt = paidAt,
                depositPaidAt = when {
                    alsoDeposit -> paidAt
                    undoAutoDeposit -> null
                    else -> c.depositPaidAt
                },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 2026-05-30 사장님 #4 — 총금액 설정. null = 미입력.
     * 잔금 자동 계산 기준: balance = totalAmount - depositAmount.
     */
    suspend fun updateTotalAmount(id: Long, amount: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(totalAmount = amount, updatedAt = System.currentTimeMillis()))
    }

    /** 시공 예약일 설정/변경/취소(null). 자정 epoch ms 권장. */
    suspend fun updateScheduledWorkDate(id: Long, scheduledWorkDate: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(scheduledWorkDate = scheduledWorkDate, updatedAt = System.currentTimeMillis()))
    }

    /** 시공 완료 처리/취소 (2026-06-08 #2). null = 완료 취소(되돌리기). 오늘 시공 히어로에서 호출. */
    suspend fun updateWorkCompletedAt(id: Long, completedAt: Long?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(workCompletedAt = completedAt, updatedAt = System.currentTimeMillis()))
    }

    /** 시공 시간만 설정/변경(자정부터 분, null=미정). 기간(days)은 그대로 보존. 채팅·고객정보 날짜선택 후 시간 칩에서 호출. (2026-06-23 사장님) */
    suspend fun updateScheduledWorkMinutes(id: Long, minutes: Int?) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(scheduledWorkMinutes = minutes, updatedAt = System.currentTimeMillis()))
    }

    /** 시공 시간(자정부터 분, null=미정) + 기간(일) 설정. 셀프 일정 등록/수정에서 호출. DB v24. */
    suspend fun updateScheduledWorkTiming(id: Long, minutes: Int?, days: Int) {
        val c = dao.findById(id) ?: return
        dao.update(c.copy(
            scheduledWorkMinutes = minutes,
            scheduledWorkDays = days.coerceAtLeast(1),
            updatedAt = System.currentTimeMillis()
        ))
    }

    /** A/S 예약(시공과 별개, 무료) 설정/변경/취소. date=null 이면 A/S 지움. 기간(며칠)도 함께. DB v43. (2026-08-01 사장님) */
    suspend fun updateAsSchedule(id: Long, date: Long?, days: Int) {
        val c = dao.findById(id) ?: return
        val normalized = date?.let { com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(it) }
        dao.update(c.copy(
            asScheduledDate = normalized,
            asScheduledDays = days.coerceAtLeast(1),
            updatedAt = System.currentTimeMillis()
        ))
    }

    /**
     * 현장 주소 설정/변경/지움(null) — 사장님 수동 등록 (2026-05-28, DB v15).
     *   AddressExtractor 자동 추출보다 우선. 길찾기/§13 가 이 값을 1순위로 활용.
     */
    suspend fun updateAddress(id: Long, address: String?) {
        val c = dao.findById(id) ?: return
        val trimmed = address?.trim()?.takeIf { it.isNotEmpty() }
        dao.update(c.copy(address = trimmed, updatedAt = System.currentTimeMillis()))
    }

}
