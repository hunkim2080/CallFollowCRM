package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산 미수 계산 검증 (정산 Phase 1).
 *
 * 규칙: total = totalAmount ?: (deposit + balance).
 *       balance = balanceAmount ?: (total − deposit).
 *       received = 받음 표시된 계약금/잔금 합. outstanding = total − received (>=0).
 */
class SettlementCalcTest {

    private fun customer(
        total: Long? = null,
        deposit: Long? = null,
        depositPaid: Boolean = false,
        balance: Long? = null,
        balancePaid: Boolean = false,
        scheduled: Long? = null,
        completedAt: Long? = null
    ) = CustomerEntity(
        id = 1,
        phoneNumber = "01012345678",
        depositAmount = deposit,
        depositPaidAt = if (depositPaid) 1_000L else null,
        balanceAmount = balance,
        balancePaidAt = if (balancePaid) 2_000L else null,
        totalAmount = total,
        scheduledWorkDate = scheduled,
        workCompletedAt = completedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test fun `계약금만 받음 - 잔금 미수`() {
        val r = SettlementCalc.rowOf(
            customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000)
        )
        assertEquals(1_000_000, r.total)
        assertEquals(300_000, r.received)
        assertEquals(700_000, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test fun `계약금 + 잔금 모두 받음 - 완납`() {
        val r = SettlementCalc.rowOf(
            customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000, balancePaid = true)
        )
        assertEquals(1_000_000, r.received)
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `총액만 입력 - 아무것도 안받음`() {
        val r = SettlementCalc.rowOf(customer(total = 500_000))
        assertEquals(500_000, r.total)
        assertEquals(0, r.received)
        assertEquals(500_000, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test fun `잔금 금액 미입력시 총액-계약금으로 추정`() {
        val r = SettlementCalc.rowOf(customer(total = 1_000_000, deposit = 400_000))
        assertEquals(600_000, r.balanceAmount)
        assertEquals(1_000_000, r.total)
    }

    @Test fun `계약금만 박혀있고 받음 - 총액 없으면 그게 완납`() {
        val r = SettlementCalc.rowOf(customer(deposit = 200_000, depositPaid = true))
        assertEquals(200_000, r.total)
        assertEquals(200_000, r.received)
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `돈 정보 전혀 없으면 정산 대상 아님`() {
        assertFalse(SettlementCalc.hasMoney(customer()))
        assertTrue(SettlementCalc.hasMoney(customer(total = 100_000)))
        assertTrue(SettlementCalc.hasMoney(customer(deposit = 100_000)))
    }

    @Test fun `총액 줄였는데 옛 잔금이 stale 로 남아도 - 총액-계약금으로 자가복구`() {
        // 사장님 신고(2026-06-26): 총 15만 / 계약금 10만인데 잔금이 35만으로 뜸.
        //   DB: totalAmount=15만, depositAmount=10만, balanceAmount=35만(옛 45만 시절 잔금이 stale).
        //   잔금 미받음(balancePaid=false) → 총액 기준 재계산 = 15-10 = 5만.
        val r = SettlementCalc.rowOf(
            customer(total = 150_000, deposit = 100_000, depositPaid = true, balance = 350_000)
        )
        assertEquals(150_000, r.total)
        assertEquals(50_000, r.balanceAmount)   // 35만(stale) 무시 → 5만
        assertEquals(100_000, r.received)        // 계약금만 받음
        assertEquals(50_000, r.outstanding)
        assertFalse(r.isPaidOff)
    }

    @Test fun `잔금 받음 표시된 건 - 받은 실제 금액 유지`() {
        // 받은 기록은 보존: balancePaid=true 면 저장된 balanceAmount 를 그대로 받은 금액으로.
        val r = SettlementCalc.rowOf(
            customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000, balancePaid = true)
        )
        assertEquals(700_000, r.balanceAmount)
        assertEquals(1_000_000, r.received)
        assertTrue(r.isPaidOff)
    }

    @Test fun `완납 원탭 - 잔금만 받음표시여도 계약금 미수로 안 남고 전액 완납`() {
        // 사장님 신고(2026-07-15): 총 130만 / 계약금 10만, '완납' 원탭은 잔금만 받음처리 →
        //   계약금 10만이 미수로 남던 버그. 잔금(마지막 지불) 받음 = 전액 완납 = 미수 0.
        val r = SettlementCalc.rowOf(
            customer(total = 1_300_000, deposit = 100_000, depositPaid = false, balancePaid = true)
        )
        assertEquals(1_300_000, r.total)
        assertEquals(1_300_000, r.received)
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `받은게 총액 초과해도 미수는 음수 안됨`() {
        // 총액 100k 인데 계약금 300k 받음으로 박힘 (이상 입력) → outstanding 0.
        val r = SettlementCalc.rowOf(
            customer(total = 100_000, deposit = 300_000, depositPaid = true)
        )
        assertEquals(0, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    // ── 미수 경과일(상담함 카드용) — 사장님 결정 2026-06-23 ──
    private val DAY = com.detailline.callfollowcrm.util.DateTimeUtils.DAY_MS
    private val today = com.detailline.callfollowcrm.util.DateTimeUtils.startOfDay(1_700_000_000_000L)

    @Test fun `완료 어제면 1일째`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, completedAt = today - DAY)
        assertEquals(1, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `완료가 오늘이면 아직 안 지남`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, completedAt = today)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    /**
     * 🔴 **시공을 안 했으면 미수가 아니다.** (2026-09-20 사장님)
     *   계약금만 받고 잔금이 남은 채 예약일이 지난 건 — 전엔 미수로 잡혀
     *   **아직 안 한 일의 돈을 독촉**하게 됐다. 실제 업무폰 자료가 전부 이 모양이었다.
     */
    @Test fun `예약일이 지나도 완료 표시가 없으면 미수 아님`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, scheduled = today - 5 * DAY)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `예약이 미래면 당연히 미수 아님`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, scheduled = today + DAY)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `완납이면 완료 지났어도 null`() {
        val c = customer(
            total = 1_000_000, deposit = 300_000, depositPaid = true,
            balance = 700_000, balancePaid = true, completedAt = today - 5 * DAY
        )
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `시공일도 완료일도 없으면 null`() {
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true)
        assertEquals(null, SettlementCalc.overdueDays(c, today))
    }

    @Test fun `예약일이 아무리 오래돼도 완료일로만 센다`() {
        // 예약일은 10일 전이지만 실제 완료일이 2일 전 → 2일째로 계산.
        val c = customer(
            total = 1_000_000, deposit = 300_000, depositPaid = true,
            scheduled = today - 10 * DAY, completedAt = today - 2 * DAY
        )
        assertEquals(2, SettlementCalc.overdueDays(c, today))
    }

    // ── receivedInRange: 재방문 매출 증발(rank1) 방지 — 계약금/잔금 각각 받은 날짜로 귀속 ──

    @Test fun `receivedInRange - 계약금 잔금 각각 받은 날짜 구간에만 잡힘`() {
        // 계약금 30만(paidAt=100), 잔금 70만(paidAt=200).
        val t = 1_000_000L; val d = 300_000L; val b = 700_000L
        assertEquals(300_000, SettlementCalc.receivedInRange(t, d, 100L, b, 200L, 50L, 150L))   // 계약금만
        assertEquals(700_000, SettlementCalc.receivedInRange(t, d, 100L, b, 200L, 150L, 250L))  // 잔금만
        assertEquals(1_000_000, SettlementCalc.receivedInRange(t, d, 100L, b, 200L, 50L, 250L)) // 둘 다
        assertEquals(0, SettlementCalc.receivedInRange(t, d, 100L, b, 200L, 300L, 400L))        // 구간 밖
    }

    @Test fun `receivedInRange - 잔금 미입력이면 총액-계약금으로 귀속`() {
        // 총액 100만, 계약금 40만(paidAt=100), balanceAmount=null·잔금 받음(paidAt=200) → 잔금 60만.
        assertEquals(1_000_000, SettlementCalc.receivedInRange(1_000_000, 400_000, 100L, null, 200L, 50L, 250L))
    }

    @Test fun `receivedInRange - 안 받은 건(paidAt null)은 0`() {
        assertEquals(0, SettlementCalc.receivedInRange(1_000_000, 300_000, null, 700_000, null, 0L, Long.MAX_VALUE))
    }

    @Test fun `receivedInRange - 완납인데 계약금 미표시(옛 완납) 계약금도 잔금날에 귀속`() {
        // 총 130만·계약금 10만·계약금 depositPaidAt=null(원탭 완납이 잔금만 찍음)·잔금 받음(paidAt=200).
        //   구간 [150,250) → 잔금 120만 + 미표시 계약금 10만 = 130만 (전액). 마감브리핑과 동일 규칙(rank7).
        assertEquals(1_300_000, SettlementCalc.receivedInRange(1_300_000, 100_000, null, null, 200L, 150L, 250L))
        // 계약금이 딴 달(paidAt=100)에 표시됐으면 잔금달엔 잔금만(보정 안 함).
        assertEquals(1_200_000, SettlementCalc.receivedInRange(1_300_000, 100_000, 100L, null, 200L, 150L, 250L))
    }

    // ── 금액 입력 창 셈(planAmounts · wonFromMan) — 계약금 → 총액 → 잔금 자동 (2026-10-15 사장님) ──

    @Test fun `planAmounts - 둘 다 적으면 잔금 = 총액 - 계약금`() {
        val p = SettlementCalc.planAmounts(totalWon = 900_000, depositWon = 300_000)
        assertEquals(600_000L, p.balanceWon)
        assertTrue(p.canSave)
        assertFalse(p.depositOverTotal)
        // 창이 보여준 잔금 = 저장 뒤 카드(rowOfFields)의 잔금 — 같은 자.
        assertEquals(p.balanceWon, SettlementCalc.rowOfFields(1L, 900_000, 300_000, null, null, null).balanceAmount)
    }

    @Test fun `planAmounts - 계약금만 적어도 저장은 되고 잔금은 계산 불가(null)`() {
        val p = SettlementCalc.planAmounts(totalWon = null, depositWon = 300_000)
        assertEquals(300_000L, p.depositWon)
        assertEquals(null, p.totalWon)
        assertEquals(null, p.balanceWon)
        assertTrue(p.canSave)
    }

    @Test fun `planAmounts - 총액만 적으면 잔금 = 총액`() {
        val p = SettlementCalc.planAmounts(totalWon = 500_000, depositWon = null)
        assertEquals(500_000L, p.balanceWon)
        assertTrue(p.canSave)
    }

    @Test fun `planAmounts - 계약금이 총액보다 크면 저장 막고 안내`() {
        val p = SettlementCalc.planAmounts(totalWon = 300_000, depositWon = 900_000)
        assertTrue(p.depositOverTotal)
        assertFalse(p.canSave)
        assertEquals(null, p.balanceWon)
    }

    @Test fun `planAmounts - 0원·음수는 안 적은 것(null)`() {
        val p = SettlementCalc.planAmounts(totalWon = 0, depositWon = -5)
        assertEquals(null, p.totalWon)
        assertEquals(null, p.depositWon)
        assertEquals(null, p.balanceWon)
        assertTrue(p.canSave)   // 둘 다 비움 = 지우기 저장
    }

    @Test fun `planAmounts - 계약금 = 총액이면 잔금 0 (완납 가능)`() {
        val p = SettlementCalc.planAmounts(totalWon = 300_000, depositWon = 300_000)
        assertEquals(0L, p.balanceWon)
        assertTrue(p.canSave)
        // 계약금 받음만 찍혀도 받을 돈 0 → 완납.
        val r = SettlementCalc.rowOfFields(1L, 300_000, 300_000, 1L, null, null)
        assertEquals(0L, r.outstanding)
        assertTrue(r.isPaidOff)
    }

    @Test fun `wonFromMan - 만원 칸을 안 바꿨으면 원래 원 값 그대로(절삭 방지)`() {
        assertEquals(375_000L, SettlementCalc.wonFromMan(37L, 375_000L))   // 37만 표시 그대로 → 375,000 유지
        assertEquals(400_000L, SettlementCalc.wonFromMan(40L, 375_000L))   // 바꿨으면 만원→원
        assertEquals(900_000L, SettlementCalc.wonFromMan(90L, null))        // 처음 적음
        assertEquals(null, SettlementCalc.wonFromMan(null, 375_000L))      // 비움 = 지움
        assertEquals(null, SettlementCalc.wonFromMan(0L, 0L))
    }

    @Test fun `depositPaidAfterSave - 계약금 적으면 받음, 이미 찍혔으면 그대로, 지우면 풀림`() {
        assertEquals(500L, SettlementCalc.depositPaidAfterSave(100_000, null, 500L))      // 새로 찍음
        assertEquals(123L, SettlementCalc.depositPaidAfterSave(100_000, 123L, 500L))      // 멱등 — 옛 시각 유지
        assertEquals(null, SettlementCalc.depositPaidAfterSave(null, 123L, 500L))         // 지우면 해제
        assertEquals(null, SettlementCalc.depositPaidAfterSave(0, 123L, 500L))
        assertEquals(null, SettlementCalc.depositPaidAfterSave(null, null, 500L))
    }

    @Test fun `hasMoney(건) - 총액·계약금·잔금 다 없으면 false, 하나라도 있으면 true`() {
        fun job(t: Long? = null, d: Long? = null, b: Long? = null) =
            com.detailline.callfollowcrm.data.local.entity.JobEntity(
                id = 1L, customerId = 1L, scheduledWorkDate = 0L, scheduledWorkDays = 1,
                totalAmount = t, depositAmount = d, balanceAmount = b, workCompletedAt = 10L,
                createdAt = 0L, updatedAt = 0L
            )
        assertFalse(SettlementCalc.hasMoney(job()))                   // 완료만 찍힘 → 「완료 · 금액 미입력」
        assertTrue(SettlementCalc.hasMoney(job(t = 500_000)))
        assertTrue(SettlementCalc.hasMoney(job(d = 100_000)))
        assertTrue(SettlementCalc.hasMoney(job(b = 100_000)))
    }

    @Test fun `receivedInRange - CustomerEntity 오버로드는 raw 와 동일`() {
        // 헬퍼 기본값: depositPaidAt=1000, balancePaidAt=2000.
        val c = customer(total = 1_000_000, deposit = 300_000, depositPaid = true, balance = 700_000, balancePaid = true)
        assertEquals(1_000_000, SettlementCalc.receivedInRange(c, 0L, 3_000L))
        assertEquals(300_000, SettlementCalc.receivedInRange(c, 500L, 1_500L))    // 계약금(1000)만
        assertEquals(700_000, SettlementCalc.receivedInRange(c, 1_500L, 2_500L))  // 잔금(2000)만
    }

    // ── 잔금 도장 ↔ 완료일 (2026-09-18 확정 · 2026-10-17 되돌리기 대칭) ──

    @Test fun `잔금 받으면 완료가 자동 - 이미 완료일이 있으면 존중`() {
        assertEquals(5_000L, SettlementCalc.workCompletedAfterBalance(paidAt = 5_000L, currentCompletedAt = null, currentBalancePaidAt = null))
        assertEquals(3_000L, SettlementCalc.workCompletedAfterBalance(paidAt = 5_000L, currentCompletedAt = 3_000L, currentBalancePaidAt = null))
    }

    @Test fun `완납 취소 - 잔금과 같은 순간에 자동으로 찍힌 완료는 같이 풀린다`() {
        // 2026-10-17 사장님 실기: 「예약금 확인」 한 탭이 55만 완납 + 완료를 같이 찍었다 → 완납 취소로 둘 다 돌아와야 한다.
        assertEquals(null, SettlementCalc.workCompletedAfterBalance(paidAt = null, currentCompletedAt = 5_000L, currentBalancePaidAt = 5_000L))
    }

    @Test fun `완납 취소 - 사장님이 따로 누른 완료(시각 다름)는 그대로`() {
        assertEquals(3_000L, SettlementCalc.workCompletedAfterBalance(paidAt = null, currentCompletedAt = 3_000L, currentBalancePaidAt = 5_000L))
        assertEquals(3_000L, SettlementCalc.workCompletedAfterBalance(paidAt = null, currentCompletedAt = 3_000L, currentBalancePaidAt = null))
        assertEquals(null, SettlementCalc.workCompletedAfterBalance(paidAt = null, currentCompletedAt = null, currentBalancePaidAt = null))
    }
}
