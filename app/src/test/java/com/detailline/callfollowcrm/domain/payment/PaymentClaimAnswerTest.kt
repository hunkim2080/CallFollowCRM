package com.detailline.callfollowcrm.domain.payment

import com.detailline.callfollowcrm.domain.payment.PaymentClaimAnswer.Stamp
import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 💵 「입금했습니다」 카드가 **무엇을 찍을지**. (2026-10-17 사장님 실기 — 예약금 10만이 65만 완납으로 둔갑)
 *
 *   ⚠️ 이 시험의 핵심 두 줄:
 *     ① **계약금을 아직 안 받았으면** 시공 전엔 잔금을 절대 묻지 않는다(10/17 사고 방지).
 *     ② **계약금을 이미 받았으면** 시공 전이어도 잔금을 묻는다 — 단 일부 입금은 막는다. (2026-10-30 사장님)
 */
class PaymentClaimAnswerTest {

    private val today = DateTimeUtils.startOfDay(1_760_000_000_000L)   // 2025-10-09 KST 근처 — 값 자체는 상관없다
    private val day = DateTimeUtils.DAY_MS

    private fun decide(
        said: Long? = null,
        outstanding: Long = 550_000L,
        deposit: Long = 100_000L,
        depositPaid: Boolean = true,
        balancePaid: Boolean = false,
        date: Long? = today + 6 * day,     // 사고 때처럼 시공일은 엿새 뒤
        days: Int = 1,
        completedAt: Long? = null
    ) = PaymentClaimAnswer.decide(
        saidAmountWon = said, outstandingWon = outstanding, depositWon = deposit,
        depositPaid = depositPaid, balancePaid = balancePaid,
        scheduledWorkDate = date, scheduledWorkDays = days, workCompletedAt = completedAt,
        todayStartMs = today
    )

    // ── 사고 모양 (계약금 아직) ────────────────────────────────

    @Test fun `10_17 사고 - 시공 전·계약금 아직·"입금했습니다"(금액 없음) → 계약금을 묻는다(잔금 X)`() {
        // 예약금을 보고 잔금 완납이 찍히던 사고. 계약금이 아직이면 계약금만 묻는다.
        assertEquals(Stamp.Deposit(100_000L), decide(depositPaid = false))
    }

    @Test fun `사고 변형 - 계약금 아직인데 10만원이라 적어도 잔금은 안 묻는다`() {
        // 계약금(10만)과 같은 금액 → 계약금, 절대 잔금 아님.
        assertEquals(Stamp.Deposit(100_000L), decide(said = 100_000L, depositPaid = false))
    }

    @Test fun `계약금 아직이면 시공 전엔 어떤 금액을 말해도 Balance 는 안 나온다`() {
        listOf(null, 10_000L, 100_000L, 550_000L, 650_000L, 1_000_000L).forEach { said ->
            val out = decide(said = said, depositPaid = false)
            assertFalse("계약금 아직인데 시공 전에 잔금을 물었다(said=$said)", out is Stamp.Balance)
        }
    }

    // ── 계약금 이미 받음 → 시공 전이어도 잔금 (2026-10-30 사장님) ──

    @Test fun `계약금 받은 뒤 시공 전 "이체했습니다"(금액 없음) → 잔금을 묻는다`() {
        // 사장님 실제 케이스: 계약금 받음 + 시공 당일 "이체하였습니다~" → 다음 돈은 잔금.
        assertEquals(Stamp.Balance(550_000L), decide(depositPaid = true))
    }

    @Test fun `계약금 받은 뒤 시공 전 일부만 입금(잔금보다 적음) → 안 묻는다`() {
        // 안전장치 유지: 안 받은 돈이 완납으로 찍히지 않게.
        assertNull(decide(said = 200_000L, depositPaid = true))
    }

    @Test fun `계약금 받은 뒤 시공 전 잔금만큼 입금 → 잔금을 묻는다`() {
        assertEquals(Stamp.Balance(550_000L), decide(said = 550_000L, depositPaid = true))
    }

    @Test fun `계약금 받은 뒤 시공 전 잔금보다 많이 적음 → 잔금 묻되 다르다고 표시`() {
        val out = decide(said = 600_000L, depositPaid = true)
        assertEquals(Stamp.Balance(550_000L), out)
        assertTrue(PaymentClaimAnswer.saidDiffers(600_000L, out!!))
    }

    // ── 시공 전 = 계약금 ───────────────────────────────────────

    @Test fun `시공 전·계약금 아직이면 계약금을 묻는다`() {
        assertEquals(Stamp.Deposit(100_000L), decide(depositPaid = false))
    }

    @Test fun `날짜를 아직 안 잡았어도 시공 전 — 계약금 아직이면 계약금, 받았으면 잔금`() {
        assertEquals(Stamp.Deposit(100_000L), decide(date = null, depositPaid = false))
        assertEquals(Stamp.Balance(550_000L), decide(date = null, depositPaid = true))
    }

    @Test fun `여러 날 공사 - 마지막 날이 오늘이면 아직 시공 전`() {
        // 3일 공사, 그저께 시작 → 마지막 날 = 오늘 → 시공 전(WorkDoneAsk 와 같은 자)
        // 계약금 아직 → 계약금 / 계약금 받음 → 잔금
        assertEquals(Stamp.Deposit(100_000L), decide(date = today - 2 * day, days = 3, depositPaid = false))
        assertEquals(Stamp.Balance(550_000L), decide(date = today - 2 * day, days = 3, depositPaid = true))
    }

    @Test fun `계약금이 0이고 아직 안 받았으면 시공 전엔 아무것도 안 묻는다`() {
        // 계약금 개념이 없으면(0원) '받음'이 아니므로 잔금을 열지 않는다 — 10/17 안전장치.
        assertNull(decide(deposit = 0L, depositPaid = false, outstanding = 650_000L))
    }

    // ── 시공 뒤 = 잔금 ────────────────────────────────────────

    @Test fun `완료 찍힌 뒤 "입금했습니다" → 잔금을 묻는다`() {
        assertEquals(Stamp.Balance(550_000L), decide(completedAt = today - day))
    }

    @Test fun `시공일이 지났으면(완료 안 찍어도) 잔금을 묻는다`() {
        assertEquals(Stamp.Balance(550_000L), decide(date = today - day))
    }

    @Test fun `시공 뒤라도 말한 금액이 계약금과 같고 계약금이 아직이면 계약금`() {
        assertEquals(Stamp.Deposit(100_000L), decide(said = 100_000L, depositPaid = false, date = today - day))
    }

    @Test fun `시공 뒤 일부만 입금(잔금보다 적음) → 완납으로 안 묻는다`() {
        assertNull(decide(said = 300_000L, date = today - day))
    }

    @Test fun `시공 뒤 말한 금액이 잔금보다 많으면 잔금을 묻되 다르다고 표시`() {
        val out = decide(said = 600_000L, date = today - day)
        assertEquals(Stamp.Balance(550_000L), out)
        assertTrue(PaymentClaimAnswer.saidDiffers(600_000L, out!!))
        assertFalse(PaymentClaimAnswer.saidDiffers(550_000L, out))
        assertFalse(PaymentClaimAnswer.saidDiffers(null, out))
    }

    // ── 물을 일이 없는 것 ─────────────────────────────────────

    @Test fun `이미 완납이면 안 묻는다`() {
        assertNull(decide(balancePaid = true, outstanding = 0L, date = today - day))
        assertNull(decide(outstanding = 0L, date = today - day))
    }

    @Test fun `isBeforeWork - 완료가 찍히면 날짜가 미래여도 시공 뒤`() {
        assertFalse(PaymentClaimAnswer.isBeforeWork(today + 6 * day, 1, today, today))
        assertTrue(PaymentClaimAnswer.isBeforeWork(today + 6 * day, 1, null, today))
        assertTrue(PaymentClaimAnswer.isBeforeWork(today, 1, null, today))
        assertFalse(PaymentClaimAnswer.isBeforeWork(today - day, 1, null, today))
        assertTrue(PaymentClaimAnswer.isBeforeWork(null, 1, null, today))
    }
}
