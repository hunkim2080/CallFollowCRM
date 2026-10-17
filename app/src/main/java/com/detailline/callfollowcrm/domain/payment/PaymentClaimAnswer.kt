package com.detailline.callfollowcrm.domain.payment

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 💵 **「입금했습니다」 카드의 [네, 받았어요] 가 무엇을 찍을지** — 한 곳. (2026-10-17 사장님 실기)
 *
 * 실제 사고(2026-10-17):
 *   1차 시공 10/23 · 총 65만 · 계약금 10만(받음 표시) 상태에서 손님이 **예약금 10만**을 넣고
 *   "입금했습니다" 라고 보냈다. 카드는 「남은 잔금 55만원을 받음으로 표시할까요? [네, 받았어요]」 를 띄웠고,
 *   그 한 번의 탭으로 **55만 완납 + 시공 완료**가 찍혀 미수금이 조용히 사라졌다.
 *   [PaymentClaimDetector] 는 "돈을 보냈다" 까지만 알아본다 — **어느 돈인지**(계약금 / 잔금)는 몰랐다.
 *
 * 규칙 — 틀리면 어느 쪽으로 틀리는가(§13②): 못 물어보면 손해가 없고(직접 누르면 된다),
 *   **잘못 찍으면 안 받은 돈이 '받음'이 된다.** 그래서 애매하면 **안 찍는 쪽**으로 기운다.
 *   ① 받을 돈이 없거나 이미 완납이면 — 묻지 않는다.
 *   ② **시공 전**(완료 안 찍혔고, 마지막 시공일이 오늘 이후 또는 날짜 미정)에 들어온 돈은 **계약금**이다.
 *      계약금이 아직 '받음'이 아니면 계약금을 묻고, 이미 받음이면 **아무것도 묻지 않는다** — 잔금은 시공 전엔 절대 안 묻는다.
 *   ③ 시공이 끝났거나 시공일이 지났으면 잔금을 묻되,
 *      · 손님이 말한 금액이 **계약금과 같고** 계약금이 아직이면 → 계약금
 *      · 손님이 말한 금액이 **잔금보다 적으면**(일부 입금) → 묻지 않는다(완납으로 찍으면 나머지 미수가 사라진다)
 *      · 그 외 → 잔금(말한 금액이 다르면 카드가 그 사실을 같이 보여준다 — [saidDiffers])
 *
 * 전화·DB·안드로이드에 안 기댄다 → [PaymentClaimAnswerTest] 가 매 빌드 본다.
 */
object PaymentClaimAnswer {

    /** [네, 받았어요] 가 찍는 것. */
    sealed interface Stamp {
        val amountWon: Long
        /** 계약금 받음 — 잔금은 그대로 미수로 남는다. */
        data class Deposit(override val amountWon: Long) : Stamp
        /** 잔금 받음 = 완납(시공 완료 자동 — 2026-09-18 사장님 확정). */
        data class Balance(override val amountWon: Long) : Stamp
    }

    /**
     * @param saidAmountWon 손님 문장에 적힌 금액(원). 없으면 null.
     * @param outstandingWon 지금 남은 받을 돈(SettlementCalc.outstanding).
     * @param depositWon 계약금(원). 없으면 0.
     * @param depositPaid 계약금이 이미 '받음'인가.
     * @param balancePaid 잔금이 이미 '받음'인가.
     * @param scheduledWorkDate 시공 시작일(ms). 날짜 미정이면 null.
     * @param scheduledWorkDays 며칠 공사인가(1 이상).
     * @param workCompletedAt 완료 찍힌 시각. 안 찍혔으면 null.
     * @param todayStartMs 오늘 0시.
     * @return 찍을 것. **물을 일이 없으면 null**(카드를 안 띄운다).
     */
    fun decide(
        saidAmountWon: Long?,
        outstandingWon: Long,
        depositWon: Long,
        depositPaid: Boolean,
        balancePaid: Boolean,
        scheduledWorkDate: Long?,
        scheduledWorkDays: Int,
        workCompletedAt: Long?,
        todayStartMs: Long
    ): Stamp? {
        if (outstandingWon <= 0L || balancePaid) return null                       // ①
        val depositDue = depositWon > 0L && !depositPaid
        if (isBeforeWork(scheduledWorkDate, scheduledWorkDays, workCompletedAt, todayStartMs)) {
            return if (depositDue) Stamp.Deposit(depositWon) else null             // ②
        }
        if (saidAmountWon != null) {
            if (depositDue && saidAmountWon == depositWon) return Stamp.Deposit(depositWon)   // ③-a
            if (saidAmountWon < outstandingWon) return null                                    // ③-b
        }
        return Stamp.Balance(outstandingWon)                                        // ③-c
    }

    /** 손님이 말한 금액이 찍을 금액과 **다른가** — 카드가 "확인해보세요" 를 덧붙일지. */
    fun saidDiffers(saidAmountWon: Long?, stamp: Stamp): Boolean =
        saidAmountWon != null && saidAmountWon != stamp.amountWon

    /**
     * **시공 전인가.** 완료가 안 찍혔고, 마지막 시공일이 오늘 이후(오늘 포함)이거나 날짜를 아직 안 잡았으면 시공 전.
     *   마지막 날 셈은 [com.detailline.callfollowcrm.domain.schedule.WorkDoneAsk] 와 같은 자(시작일 + (일수−1)).
     */
    fun isBeforeWork(
        scheduledWorkDate: Long?,
        scheduledWorkDays: Int,
        workCompletedAt: Long?,
        todayStartMs: Long
    ): Boolean {
        if (workCompletedAt != null) return false
        val day = scheduledWorkDate?.takeIf { it > 0L } ?: return true
        val lastDay = DateTimeUtils.startOfDay(day) +
            (scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        return lastDay >= todayStartMs
    }
}
