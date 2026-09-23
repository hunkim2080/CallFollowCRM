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

    private fun job(
        id: Long = 1,
        customerId: Long = 1,
        total: Long? = null,
        deposit: Long? = null,
        depositPaidAt: Long? = null,
        balance: Long? = null,
        balancePaidAt: Long? = null,
        scheduled: Long? = null
    ) = com.detailline.callfollowcrm.data.local.entity.JobEntity(
        id = id,
        customerId = customerId,
        scheduledWorkDate = scheduled,
        totalAmount = total,
        depositAmount = deposit,
        depositPaidAt = depositPaidAt,
        balanceAmount = balance,
        balancePaidAt = balancePaidAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    // ── 돈 이중 합산 (2026-09-18 연결부 점검에서 발견) ─────────────────────────
    //   v49 부터 고객 카드의 돈이 '건 장부(jobs)' 에도 **똑같이** 들어간다
    //   (v49 복사 · v52/v53 보정 · CustomerRepository.mutate 미러).
    //   그런데 달력은 customers 와 jobs 를 **그냥 더해서**, 계약금 20만원이 40만원으로 잡혔다.
    //   규칙: **건이 하나라도 있는 고객은 건 장부만 센다.** 건이 없는 고객만 고객 카드로 센다.

    @Test fun `같은 입금이 고객카드와 건장부 양쪽에 있어도 한 번만 센다`() {
        val c = customer(id = 1, total = 1_000_000, deposit = 200_000, depositPaidAt = day1,
                         balance = 800_000, balancePaidAt = day2, scheduled = day2)
        val j = job(id = 10, customerId = 1, total = 1_000_000, deposit = 200_000, depositPaidAt = day1,
                    balance = 800_000, balancePaidAt = day2, scheduled = day2)
        val items = CashFlowCalc.buildItems(listOf(c), emptyList(), emptyList(), 0L, listOf(j))
        val income = items.filter { it.isIncome && it.isDone }.sumOf { it.amount }
        assertEquals("계약금 20만 + 잔금 80만 = 100만. 두 번 세면 200만이 된다", 1_000_000L, income)
    }

    @Test fun `건이 두 개면 각 건의 돈을 따로 센다`() {
        val c = customer(id = 1, total = 600_000, deposit = 200_000, depositPaidAt = day1, scheduled = day2)
        val j1 = job(id = 10, customerId = 1, total = 600_000, deposit = 200_000, depositPaidAt = day1, scheduled = day2)
        val j2 = job(id = 11, customerId = 1, total = 900_000, deposit = 300_000, depositPaidAt = day2,
                     scheduled = day2 + 7L * 24 * 3600 * 1000)
        val items = CashFlowCalc.buildItems(listOf(c), emptyList(), emptyList(), 0L, listOf(j1, j2))
        val income = items.filter { it.isIncome && it.isDone }.sumOf { it.amount }
        assertEquals("1차 계약금 20만 + 2차 계약금 30만 = 50만", 500_000L, income)
    }

    @Test fun `건 장부가 아예 없는 고객은 고객카드로 센다`() {
        // 돈은 넣었는데 시공일을 안 잡은 고객 — 건 행이 안 만들어진다. 이 돈이 사라지면 안 된다.
        val c = customer(id = 1, total = 500_000, deposit = 150_000, depositPaidAt = day1)
        val items = CashFlowCalc.buildItems(listOf(c), emptyList(), emptyList(), 0L, emptyList())
        val income = items.filter { it.isIncome && it.isDone }.sumOf { it.amount }
        assertEquals(150_000L, income)
    }

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
    @Test fun `이름이 없으면 제목은 현장 주소 — 번호가 아니다`() {
        // 2026-09-23 사장님: "돈을 받았으면 번호가 아니라 어떤 현장인지."
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1,
                address = "서울 서초구 반포대로 58 래미안 101동", scheduled = day2)),
            emptyList()
        )
        val dep = items.first { it.tag == "계약금" }
        assertEquals("서초구 반포대로 58", dep.title)      // 시·도는 떼고 앞 세 토막
        assertEquals(true, dep.subtitle?.contains("시공"))     // 시공일은 곁줄에
    }

    @Test fun `이름이 있으면 이름이 제목 — 번호는 곁줄로 작게`() {
        val items = CashFlowCalc.buildItems(
            listOf(customer(total = 1_000_000, deposit = 300_000, depositPaidAt = day1,
                name = "반포 김사장", address = "반포 래미안 101동", scheduled = day2)),
            emptyList()
        )
        val dep = items.first { it.tag == "계약금" }
        assertEquals("반포 김사장", dep.title)
        // 번호는 사라지지 않고 곁줄로 내려간다 — 제목에서만 빠진다.
        assertEquals(true, dep.subtitle?.contains("-") == true || dep.subtitle?.contains("시공") == true)
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
