package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 📒 **장부는 하나다.** (2026-09-28)
 *
 * 왜 이 테스트가 있나 —
 *   정산 화면은 2026-09-18 부터 **건마다** 미수를 세는데, 홈 미수금 카드만 **고객 카드 하나**를 봤다.
 *   그래서 1차 잔금이 남았는데 2차를 새로 잡으면
 *     정산 = 「미수 1건 30만원」 / 홈 = 「미수 0건」
 *   으로 갈렸다. 못 받은 돈이 홈에서 사라지는 쪽이라 돈이 새는 방향이다.
 *
 *   이제 둘 다 [SettlementCalc.book] 하나를 본다. 이 테스트가 그 약속을 지킨다 —
 *   폰에 꽂지 않고도 「두 화면이 같은 수를 말하는가」를 매 빌드마다 확인한다.
 */
class SettleBookTest {

    private fun cust(
        id: Long,
        total: Long? = null,
        deposit: Long? = null,
        depositPaid: Boolean = false,
        balancePaid: Boolean = false
    ) = CustomerEntity(
        id = id,
        phoneNumber = "0101234567$id",
        totalAmount = total,
        depositAmount = deposit,
        depositPaidAt = if (depositPaid) 1_000L else null,
        balanceAmount = null,
        balancePaidAt = if (balancePaid) 2_000L else null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun job(
        id: Long,
        customerId: Long,
        total: Long? = null,
        deposit: Long? = null,
        depositPaid: Boolean = false,
        balancePaid: Boolean = false,
        scheduled: Long? = null
    ) = JobEntity(
        id = id,
        customerId = customerId,
        scheduledWorkDate = scheduled,
        totalAmount = total,
        depositAmount = deposit,
        depositPaidAt = if (depositPaid) 1_000L else null,
        balanceAmount = null,
        balancePaidAt = if (balancePaid) 2_000L else null,
        createdAt = 0L,
        updatedAt = 0L
    )

    /** 장부에서 홈 카드가 읽는 두 수 — 정산 화면도 같은 목록에서 읽는다. */
    private fun totals(rows: List<SettleBookRow>): Pair<Long, Int> =
        rows.sumOf { it.calc.outstanding } to rows.count { it.calc.outstanding > 0 }

    @Test fun `1차 미수 + 2차 완납 - 1차가 장부에 남는다`() {
        // 사장님 카드(고객 표)엔 마지막 건인 2차 금액이 들어가 있다 — 완납이라 미수 0으로 보인다.
        val c = cust(1, total = 200_000L, deposit = 200_000L, depositPaid = true, balancePaid = true)
        val jobs = listOf(
            job(10, 1, total = 300_000L, deposit = 100_000L, depositPaid = true), // 1차: 20만 미수
            job(11, 1, total = 200_000L, deposit = 200_000L, depositPaid = true, balancePaid = true)
        )
        val (won, count) = totals(SettlementCalc.book(listOf(c), jobs))
        assertEquals("1차 잔금 20만원이 남아야 한다", 200_000L, won)
        assertEquals("미수는 1건", 1, count)
    }

    @Test fun `건이 있는 고객을 고객 카드로 또 세지 않는다`() {
        // 같은 돈을 두 번 세면 미수가 뻥튀기된다 — 홈과 정산이 같이 틀린다.
        val c = cust(1, total = 300_000L, deposit = 100_000L, depositPaid = true)
        val jobs = listOf(job(10, 1, total = 300_000L, deposit = 100_000L, depositPaid = true))
        val rows = SettlementCalc.book(listOf(c), jobs)
        assertEquals("줄은 하나", 1, rows.size)
        assertEquals(200_000L, rows.single().calc.outstanding)
    }

    @Test fun `시공일을 안 잡아 건이 없는 고객은 고객 카드로 한 줄`() {
        val c = cust(1, total = 500_000L, deposit = 100_000L, depositPaid = true)
        val rows = SettlementCalc.book(listOf(c), emptyList())
        assertEquals(1, rows.size)
        assertEquals("건이 아니라 고객 카드에서 온 줄", null, rows.single().jobId)
        assertEquals(400_000L, rows.single().calc.outstanding)
    }

    @Test fun `돈이 안 적힌 건은 장부에 안 올린다`() {
        // 시공일만 잡아둔 건까지 세면 목록이 「0원」 줄로 지저분해진다.
        val c = cust(1)
        val jobs = listOf(job(10, 1, scheduled = 5_000L))
        assertEquals(0, SettlementCalc.book(listOf(c), jobs).size)
    }

    @Test fun `없는 고객의 건은 버린다`() {
        // 고객이 지워졌는데 건이 남은 경우 — 이름도 못 붙이는 유령 줄이 화면에 뜨면 안 된다.
        val jobs = listOf(job(10, 99, total = 100_000L))
        assertEquals(0, SettlementCalc.book(emptyList(), jobs).size)
    }

    @Test fun `여러 건이 다 미수면 다 더한다`() {
        val c = cust(1)
        val jobs = listOf(
            job(10, 1, total = 300_000L),
            job(11, 1, total = 200_000L, deposit = 50_000L, depositPaid = true)
        )
        val (won, count) = totals(SettlementCalc.book(listOf(c), jobs))
        assertEquals(300_000L + 150_000L, won)
        assertEquals(2, count)
    }

    @Test fun `두 고객이 섞여도 각자 센다`() {
        val a = cust(1)
        val b = cust(2, total = 100_000L)   // 건 없음 → 고객 카드로 한 줄
        val jobs = listOf(job(10, 1, total = 300_000L, deposit = 300_000L, depositPaid = true))
        val (won, count) = totals(SettlementCalc.book(listOf(a, b), jobs))
        assertEquals("A는 완납, B만 10만원 미수", 100_000L, won)
        assertEquals(1, count)
    }
}
