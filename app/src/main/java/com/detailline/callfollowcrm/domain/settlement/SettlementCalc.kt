package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity

/**
 * 한 고객의 정산(미수) 계산 결과. 화면/홈 카드/테스트가 공유하는 단일 계산.
 *
 * 정의 (2026-06-01, 정산 Phase 1):
 *  - total  = 받을 돈 (시공비 총액).
 *  - received = 받은 돈 (계약금/잔금 중 "받음" 표시된 것 합).
 *  - outstanding = 미수 = total − received (음수 방지).
 *  - isPaidOff = 받을 돈을 다 받음.
 */
data class SettleRow(
    val customerId: Long,
    val total: Long,
    val received: Long,
    val outstanding: Long,
    val depositAmount: Long,
    val balanceAmount: Long,
    val depositPaid: Boolean,
    val balancePaid: Boolean,
    val isPaidOff: Boolean
)

/**
 * 정산 계산 — `CustomerEntity` 의 돈 필드(total/deposit/balance + paidAt)만 사용.
 * Android/Compose 의존성 없음 → 순수 단위 테스트 대상 ([SettlementCalcTest]).
 *
 * 잔금 추정 규칙 (CustomerEntity 주석과 일치): balance = totalAmount − depositAmount.
 *   사장님이 잔금 금액을 직접 박았으면 그게 우선(수동 우선).
 */
object SettlementCalc {

    /** 돈 정보가 하나라도 있으면 정산 대상 (아무 금액도 없는 고객은 정산 목록에서 제외). */
    fun hasMoney(c: CustomerEntity): Boolean =
        (c.totalAmount ?: 0L) > 0L ||
            (c.depositAmount ?: 0L) > 0L ||
            (c.balanceAmount ?: 0L) > 0L

    fun rowOf(c: CustomerEntity): SettleRow {
        val deposit = (c.depositAmount ?: 0L).coerceAtLeast(0L)
        // 잔금: 직접 박힌 값 우선 → 없으면 (총액 − 계약금) 으로 추정 → 그것도 없으면 0.
        val balance = c.balanceAmount?.coerceAtLeast(0L)
            ?: c.totalAmount?.let { (it - deposit).coerceAtLeast(0L) }
            ?: 0L
        val total = c.totalAmount ?: (deposit + balance)

        val depositPaid = c.depositPaidAt != null
        val balancePaid = c.balancePaidAt != null
        val received = (if (depositPaid) deposit else 0L) + (if (balancePaid) balance else 0L)
        val outstanding = (total - received).coerceAtLeast(0L)
        val isPaidOff = total > 0L && received >= total

        return SettleRow(
            customerId = c.id,
            total = total,
            received = received,
            outstanding = outstanding,
            depositAmount = deposit,
            balanceAmount = balance,
            depositPaid = depositPaid,
            balancePaid = balancePaid,
            isPaidOff = isPaidOff
        )
    }
}
