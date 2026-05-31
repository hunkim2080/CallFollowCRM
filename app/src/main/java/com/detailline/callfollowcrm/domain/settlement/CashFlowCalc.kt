package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
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
    val refType: CashRefType,
    val refId: Long
)

enum class CashRefType { CUSTOMER, MANUAL }

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
        manual: List<ManualCashEntity>
    ): List<CashItem> {
        val out = ArrayList<CashItem>()
        for (c in customers) {
            if (!SettlementCalc.hasMoney(c)) continue
            val row = SettlementCalc.rowOf(c)
            val title = c.name?.takeIf { it.isNotBlank() }
                ?: PhoneNumberFormatter.format(c.phoneNumber)

            val depositPaidAt = c.depositPaidAt
            if (depositPaidAt != null && row.depositAmount > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(depositPaidAt),
                    amount = row.depositAmount, isIncome = true, isDone = true,
                    title = title, tag = "계약금", refType = CashRefType.CUSTOMER, refId = c.id
                )
            }
            val balancePaidAt = c.balancePaidAt
            if (balancePaidAt != null && row.balanceAmount > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(balancePaidAt),
                    amount = row.balanceAmount, isIncome = true, isDone = true,
                    title = title, tag = "잔금", refType = CashRefType.CUSTOMER, refId = c.id
                )
            }
            val sched = c.scheduledWorkDate
            if (row.outstanding > 0L && sched != null && sched > 0L) {
                out += CashItem(
                    dayStartMs = DateTimeUtils.startOfDay(sched),
                    amount = row.outstanding, isIncome = true, isDone = false,
                    title = title, tag = "받을 예정", refType = CashRefType.CUSTOMER, refId = c.id
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
}
