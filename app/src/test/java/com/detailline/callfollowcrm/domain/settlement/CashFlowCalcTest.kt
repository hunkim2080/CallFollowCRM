package com.detailline.callfollowcrm.domain.settlement

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import com.detailline.callfollowcrm.data.local.entity.ManualCashEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 현금흐름 파생/합산 검증 (정산 Phase 2).
 */
class CashFlowCalcTest {

    private val day1 = DateTimeUtils.startOfDay(1_700_000_000_000L) // 어떤 날
    private val day2 = day1 + 24L * 3600 * 1000

    private fun customer(
        id: Long = 1,
        total: Long? = null,
        deposit: Long? = null,
        depositPaidAt: Long? = null,
        balance: Long? = null,
        balancePaidAt: Long? = null,
        scheduled: Long? = null,
        name: String? = null,
        address: String? = null
    ) = CustomerEntity(
        id = id,
        phoneNumber = "01012345678",
        name = name,
        address = address,
        depositAmount = deposit,
        depositPaidAt = depositPaidAt,
        balanceAmount = balance,
        balancePaidAt = balancePaidAt,
        totalAmount = total,
        scheduledWorkDate = scheduled,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun manual(
        id: Long = 1,
        day: Long = 0,
        amount: Long = 0,
        income: Boolean = true,
        done: Boolean = true,
        label: String = ""
    ) = ManualCashEntity(id, day, amount, income, done, label, 0L, 0L)

    @Test fun `계약금 받음은 그날 확정수입`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1, balance = 700_000, scheduled = day2)),
            emptyList()
        )
        // 계약금(확정, day1) + 미수 잔금(예정, 예약일 day2)
        assertEquals(2, items.size)
        val d1 = CashFlowCalc.aggOf(items.filter { it.dayStartMs == day1 })
        assertEquals(300_000, d1.inDone)
        assertEquals(0, d1.inPlan)
        val d2 = CashFlowCalc.aggOf(items.filter { it.dayStartMs == day2 })
        assertEquals(700_000, d2.inPlan) // 받을 예정
        assertEquals(0, d2.inDone)
    }

    @Test fun `미수에 예약일 없으면 달력에 안 올라감`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 500_000)), // 아무것도 안받음 + 예약일 없음
            emptyList()
        )
        assertEquals(0, items.size)
    }

    @Test fun `직접 지출 예정은 outPlan`() {
        val items = CashFlowCalc.buildItems(
            emptyList(),
            listOf(manual(day = day1, amount = 120_000, income = false, done = false, label = "자재비"))
        )
        val agg = CashFlowCalc.aggOf(items)
        assertEquals(120_000, agg.outPlan)
        assertEquals(0, agg.outDone)
    }

    @Test fun `순이익 - 확정과 예상 구분`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1, balance = 700_000, scheduled = day1)),
            listOf(manual(day = day1, amount = 100_000, income = false, done = true, label = "현금지출"))
        )
        val agg = CashFlowCalc.aggOf(items)
        // 들어온 300k - 나간 100k = 200k 확정
        assertEquals(200_000, agg.netDone)
        // (300k + 700k 예정) - (100k) = 900k 예상
        assertEquals(900_000, agg.netPlanned)
    }

    @Test fun `완납 고객은 예정수입 없음`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1, balance = 700_000, balancePaidAt = day2, scheduled = day1)),
            emptyList()
        )
        // 계약금(day1 확정) + 잔금(day2 확정). 예정 수입 없음(미수 0).
        assertEquals(2, items.size)
        assertEquals(0, items.count { !it.isDone })
    }

    // ── 이름 없는(번호로만 뜨는) 고객 단서 — 사장님 결정 2026-06-23 ──
    @Test fun `이름 없는 고객은 번호 밑에 현장·시공일 단서`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1,
                address = "반포 래미안 101동", scheduled = day2)),
            emptyList()
        )
        val dep = items.first { it.tag == "계약금" }
        assertEquals(true, dep.subtitle?.contains("반포 래미안 101동"))
        assertEquals(true, dep.subtitle?.contains("시공"))
    }

    @Test fun `이름 있는 고객은 단서 없이 이름 그대로 - 프로토 유지`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1,
                name = "반포 김사장", address = "반포 래미안 101동", scheduled = day2)),
            emptyList()
        )
        val dep = items.first { it.tag == "계약금" }
        assertEquals("반포 김사장", dep.title)
        assertEquals(null, dep.subtitle)
    }

    @Test fun `주소·시공일 둘 다 없으면 단서 없음`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1)),
            emptyList()
        )
        val dep = items.first { it.tag == "계약금" }
        assertEquals(null, dep.subtitle)
    }
}
