package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobCrewEntity
import com.detailline.callfollowcrm.data.local.entity.ManualCashEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import com.detailline.callfollowcrm.util.PhoneNumberFormatter

/** 현금흐름 한 항목 (날짜에 잡히는 돈). */
data class CashItem(
    val dayStartMs: Long,
    val amount: Long,
    val isIncome: Boolean,
    /** true=확정(이미 오감), false=예정(추정). */
    val isDone: Boolean,
    val title: String,
    /** "계약금" / "잔금" / "받을 예정" / "직접". */
    val tag: String,
    /**
     * 제목(title) 밑에 붙는 식별 단서 — "현장 주소 · M/d 시공".
     * 이름이 없어 title 이 전화번호로 떨어진 고객 카드에서만 채워진다(번호만으론 누군지 모름).
     * 이름이 있으면 null = 프로토 그대로 이름+금액. (사장님 결정 2026-06-23)
     */
    val subtitle: String? = null,
    val refType: CashRefType,
    val refId: Long
)

enum class CashRefType { CUSTOMER, MANUAL, WORKER }

/**
 * 하루(또는 한 달) 합계 — 4색 표시용.
 *  inDone  = 들어온 돈(확정 수입)   · 진한 파랑
 *  inPlan  = 들어올 돈(예정 수입)   · 연한 파랑
 *  outDone = 나간 돈(확정 지출)     · 진한 빨강
 *  outPlan = 나갈 돈(예정 지출)     · 연한 빨강
 */
data class CashDayAgg(
    val inDone: Long = 0,
    val inPlan: Long = 0,
    val outDone: Long = 0,
    val outPlan: Long = 0
) {
    val hasAny: Boolean get() = inDone != 0L || inPlan != 0L || outDone != 0L || outPlan != 0L
    /** 확정 순이익(실현) = 들어온 − 나간. */
    val netDone: Long get() = inDone - outDone
    /** 예상 순이익(추정) = (들어온+들어올) − (나간+나갈). */
    val netPlanned: Long get() = (inDone + inPlan) - (outDone + outPlan)
}

/**
 * 현금흐름 계산 — settle(고객 입금) 파생 + 직접 기록(manual) 합산.
 * Android/Compose 의존 없음 → 단위 테스트 대상 ([CashFlowCalcTest]).
 *
 * 고객 파생 규칙:
 *  - 계약금 받음(depositPaidAt) → 그 날 확정 수입.
 *  - 잔금 받음(balancePaidAt) → 그 날 확정 수입.
 *  - 아직 못 받은 미수(outstanding>0) + 시공 예약일 있음 → 그 날 예정 수입.
 *    (예약일 없는 미수는 날짜가 없어 달력엔 안 올라감 — 미수금 목록[Phase1]에서 관리.)
 */
object CashFlowCalc {

    fun buildItems(
        customers: List<CustomerEntity>,
        manual: List<ManualCashEntity>,
        crew: List<JobCrewEntity> = emptyList(),
        todayStartMs: Long = 0L
    ): List<CashItem> {
        val out = ArrayList<CashItem>()
        for (c in customers) {
            if (!SettlementCalc.hasMoney(c)) continue
            val row = SettlementCalc.rowOf(c)
            val hasName = c.name?.isNotBlank() == true
            val title = if (hasName) c.name!!.trim()
                else PhoneNumberFormatter.format(c.phoneNumber)
            // 이름이 없어 번호로만 뜨면 "어딘지" 모름 → 현장 주소·시공일을 단서로 붙인다.
            // 이름 있으면 null = 프로토 그대로(이름+금액). (사장님 결정 2026-06-23)
            val hint = if (hasName) null else identifyHint(c.address, c.scheduledWorkDate)

            val depositPaidAt = c.depositPaidAt
            if (depositPaidAt != null && row.depositAmount > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(depositPaidAt),
                    amount = row.depositAmount, isIncome = true, isDone = true,
                    title = title, tag = "계약금", subtitle = hint,
                    refType = CashRefType.CUSTOMER, refId = c.id
                )
            }
            val balancePaidAt = c.balancePaidAt
            if (balancePaidAt != null && row.balanceAmount > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(balancePaidAt),
                    amount = row.balanceAmount, isIncome = true, isDone = true,
                    title = title, tag = "잔금", subtitle = hint,
                    refType = CashRefType.CUSTOMER, refId = c.id
                )
            }
            val sched = c.scheduledWorkDate
            if (row.outstanding > 0L && sched != null && sched > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(sched),
                    amount = row.outstanding, isIncome = true, isDone = false,
                    title = title, tag = "받을 예정", subtitle = hint,
                    refType = CashRefType.CUSTOMER, refId = c.id
                )
            }
        }
        for (m in manual) {
            out += CashItem(
                dayStartMs = DateTimeUtils.startOfDay(m.dayStartMs),
                amount = m.amount, isIncome = m.isIncome, isDone = m.isDone,
                title = m.label.ifBlank { if (m.isIncome) "수입" else "지출" },
                tag = "직접", refType = CashRefType.MANUAL, refId = m.id
            )
        }
        // 일당 배정 → 자동 지출. 시공일이 지났으면 확정(나간), 미래면 예정(나갈).
        for (jc in crew) {
            val day = DateTimeUtils.startOfDay(jc.dayStartMs)
            out += CashItem(
                dayStartMs = day,
                amount = jc.wage, isIncome = false,
                isDone = todayStartMs > 0L && day <= todayStartMs,
                title = jc.workerName, tag = "일당", refType = CashRefType.WORKER, refId = jc.workerId
            )
        }
        return out
    }

    fun aggOf(items: List<CashItem>): CashDayAgg {
        var inD = 0L; var inP = 0L; var outD = 0L; var outP = 0L
        for (it in items) {
            when {
                it.isIncome && it.isDone -> inD += it.amount
                it.isIncome && !it.isDone -> inP += it.amount
                !it.isIncome && it.isDone -> outD += it.amount
                else -> outP += it.amount
            }
        }
        return CashDayAgg(inD, inP, outD, outP)
    }

    /** day(자정 ms) → 그 날 항목들. */
    fun byDay(items: List<CashItem>): Map<Long, List<CashItem>> =
        items.groupBy { it.dayStartMs }

    /**
     * 이름 없는(번호로만 뜨는) 고객을 알아볼 단서 — "현장 주소 · M/d 시공".
     * 둘 다 없으면 null = 붙일 단서가 없어 번호만 남는다(주소·시공일 미등록 고객의 한계).
     */
    private fun identifyHint(address: String?, scheduledWorkDate: Long?): String? {
        val parts = ArrayList<String>(2)
        address?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        scheduledWorkDate?.takeIf { it > 0L }
            ?.let { parts.add(DateTimeUtils.formatDateOnly(it) + " 시공") }
        return parts.joinToString("  ·  ").ifEmpty { null }
    }
}
