package com.detailline.callfollowcrm.presentation.screen.report

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 리포트 순수 계산(ReportCalc) 검증 — 기간 귀속(입금일 기준) 그룹핑 + 미수 정렬.
 */
class ReportCalcTest {

    // 기간 [1000, 5000). paidAt 을 명시적으로 넣어 귀속 테스트.
    private fun cust(
        id: Long, name: String? = null, categoryId: Long? = null, address: String? = null,
        total: Long? = null, deposit: Long? = null, depositPaidAt: Long? = null,
        balance: Long? = null, balancePaidAt: Long? = null, scheduled: Long? = null
    ) = CustomerEntity(
        id = id, phoneNumber = "010$id", name = name, categoryId = categoryId, address = address,
        depositAmount = deposit, depositPaidAt = depositPaidAt,
        balanceAmount = balance, balancePaidAt = balancePaidAt, totalAmount = total,
        scheduledWorkDate = scheduled, createdAt = 0L, updatedAt = 0L
    )

    @Test fun `카테고리 그룹 - 입금일이 기간 안인 금액만 매출로`() {
        val cats = mapOf(1L to "줄눈", 2L to "탄성코트")
        val rows = ReportCalc.groupByCategory(
            listOf(
                // 줄눈: 계약금 40만 입금(기간 내 2000) + 잔금 30만(기간 밖 9999 → 제외)
                cust(1, categoryId = 1, total = 700_000, deposit = 400_000, depositPaidAt = 2000, balance = 300_000, balancePaidAt = 9999),
                // 줄눈: 계약금 30만(기간 내)
                cust(2, categoryId = 1, total = 300_000, deposit = 300_000, depositPaidAt = 3000),
                // 탄성코트: 20만(기간 내)
                cust(3, categoryId = 2, total = 200_000, deposit = 200_000, depositPaidAt = 1500),
                // 미분류(기타 시공): 10만
                cust(4, categoryId = null, total = 100_000, deposit = 100_000, depositPaidAt = 4000),
                // 기간 밖 입금(9999) → 어디에도 안 잡힘
                cust(5, categoryId = 2, total = 500_000, deposit = 500_000, depositPaidAt = 9999),
            ),
            from = 1000, to = 5000, catNames = cats
        )
        // 매출 desc: 줄눈 70만(40+30) > 탄성코트 20만 > 기타 시공 10만
        assertEquals(listOf("줄눈", "탄성코트", "기타 시공"), rows.map { it.name })
        assertEquals(700_000L, rows[0].amount)
        assertEquals(2, rows[0].count)
        assertEquals(200_000L, rows[1].amount)
    }

    @Test fun `그룹 6개 이상이면 상위 5 + 그 외`() {
        val cats = (1L..7L).associateWith { "종류$it" }
        val customers = (1L..7L).map { i ->
            cust(i, categoryId = i, total = i * 100_000, deposit = i * 100_000, depositPaidAt = 2000)
        }
        val rows = ReportCalc.groupByCategory(customers, from = 1000, to = 5000, catNames = cats)
        assertEquals(6, rows.size)                 // 상위 5 + 그 외
        assertEquals("그 외", rows.last().name)
        assertEquals((2L * 100_000) + (1L * 100_000), rows.last().amount)  // 6·7위(가장 작은 2개=10만·20만)
        assertEquals(2, rows.last().count)
    }

    @Test fun `미수 정렬 - 경과일 desc, 동률이면 금액 desc, 완납은 제외`() {
        val rows = ReportCalc.overdueRows(
            listOf(
                cust(1, name = "A", total = 1_000_000, deposit = 300_000, depositPaidAt = 1),   // 미수 70만, 날짜없음
                cust(2, name = "B", total = 500_000, deposit = 500_000, depositPaidAt = 1),      // 완납 → 제외
                cust(3, name = "C", total = 800_000, deposit = 100_000, depositPaidAt = 1),      // 미수 70만, 날짜없음
            ),
            todayStart = 10_000_000L
        )
        // 완납(B) 제외 → 2건. days 모두 null(scheduled 없음)이라 동률 → 금액 desc. A(70만),C(70만) 동률 → 금액 같으니 순서 안정.
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.amount == 700_000L })
        assertTrue(rows.none { it.name == "B" })
    }
}
