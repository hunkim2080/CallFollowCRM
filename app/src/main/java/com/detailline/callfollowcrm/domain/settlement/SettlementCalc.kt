package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.util.DateTimeUtils

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
        val depositPaid = c.depositPaidAt != null
        val balancePaid = c.balancePaidAt != null
        // 잔금 규칙 (2026-06-26 사장님 신고: 총 15만인데 잔금 35만으로 뜸 — 옛 45만 시절 balanceAmount 가 stale 로 남음):
        //  - 총액이 있으면 = 항상 (총액 − 계약금). 총액이 헤드라인 진실이므로 저장된 stale balanceAmount 는 무시해
        //    "총액과 잔금이 안 맞는" 모순을 원천 차단. (받음 처리 후에도 동일 — received = 계약금 + 잔금 = 총액 으로 일관)
        //  - 총액이 없으면(계약금/잔금만 박힌 옛 데이터) = 직접 박힌 balanceAmount (없으면 0).
        val balance = if (c.totalAmount != null) {
            (c.totalAmount - deposit).coerceAtLeast(0L)
        } else {
            c.balanceAmount?.coerceAtLeast(0L) ?: 0L
        }
        val total = c.totalAmount ?: (deposit + balance)
        // 잔금(마지막 지불)까지 받았으면 = 전액 받은 것(완납). 계약금 '받음' 표시가 없어도 완납으로 본다.
        //   (완납 원탭이 잔금만 받음처리 → 계약금 10만원이 미수로 남던 버그 fix, 2026-07-15 사장님.
        //    상세화면 allPaid = balPaid 기준과도 일치. 계약금만 받음(잔금 미수)은 그대로 deposit 만 반영.)
        val received = when {
            balancePaid -> total
            depositPaid -> deposit
            else -> 0L
        }
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

    /**
     * 미수 경과일 — 받을 돈(미수)이 남아 있고 시공 후 N일 지났으면 N, 아니면 null.
     *   기준일 = 완료일(workCompletedAt) 우선, 없으면 시공 예약일(scheduledWorkDate). 둘 다 없으면 null(날짜 모름).
     *   N>=1 일 때만 값(=하루라도 지남) → "1일 경과한 미수만 상담함에" (사장님 결정 2026-06-23).
     */
    fun overdueDays(c: CustomerEntity, todayStartMs: Long): Int? {
        if (rowOf(c).outstanding <= 0L) return null
        val base = c.workCompletedAt ?: c.scheduledWorkDate ?: return null
        val days = ((todayStartMs - DateTimeUtils.startOfDay(base)) / DateTimeUtils.DAY_MS).toInt()
        return if (days >= 1) days else null
    }
}
