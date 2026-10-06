package com.detailline.callfollowcrm.presentation.component

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 태그 표시 규칙. (2026-09-16 사장님 지시)
 *
 *   ① "시공대기는 시공 디데이랑 같이 안붙으면 되지. **시공 디데이만 나오는거야 예외적으로.**"
 *      → 자동 분류(시공 대기/완료)는 날짜 태그가 "시공 D-…" 일 때만 숨긴다. 그 외엔 보여준다.
 *      (전엔 **항상** 숨겼다. 제가 댄 이유 "일당이 파묻힌다"는 **틀린 이유**였다 —
 *       한 사람은 분류를 하나만 가지므로 일당 사장과 고객은 애초에 다른 줄이다.)
 *
 *   ② "신규는 계속 떠있던데 착각하게되더라"
 *      → 날짜 태그의 「신규」(시공일만 없으면 14일 내내)는 목록에서 뺀다.
 *        「신규」라는 말은 '오늘 처음 연락 온 사람'에게만 남긴다.
 *
 * 화면(홈 목록·기다려요·대화방)이 각자 규칙을 다시 쓰면 또 어긋나므로 규칙은 한 곳에만 둔다.
 * 이 테스트가 그 한 곳을 지킨다.
 */
class TagRuleTest {

    private val DAY = 86_400_000L
    private val today0 = 1_757_980_800_000L
    private val now = today0 + 10 * 3_600_000L

    private fun customer(
        workDate: Long? = null,
        total: Long? = null,
        deposit: Long? = null,
        balancePaidAt: Long? = null,
        completedAt: Long? = null,
        createdAt: Long = now
    ) = CustomerEntity(
        phoneNumber = "01011112222",
        scheduledWorkDate = workDate,
        totalAmount = total,
        depositAmount = deposit,
        balancePaidAt = balancePaidAt,
        workCompletedAt = completedAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun label(c: CustomerEntity) = scheduleTagLabel(c, today0, now)

    /** 화면이 쓰는 것과 **같은 판정** — CustomerTags 안의 규칙을 그대로 옮긴 것. */
    private fun showsDday(c: CustomerEntity) = label(c).startsWith("시공 D")
    private fun hiddenInList(c: CustomerEntity) =
        label(c).let { it.isBlank() || it == "미전환" || it == "신규" }

    // ── ① 겹칠 때만 숨긴다 ──────────────────────────────────────

    @Test
    fun `시공일이 잡혀 있으면 자동 분류를 숨긴다`() {
        // "[시공 대기] [시공 D-3]" 은 같은 말 두 번 → 날짜 쪽만 남긴다
        assertTrue(showsDday(customer(workDate = today0 + 3 * DAY)))
        assertTrue(showsDday(customer(workDate = today0)))
    }

    @Test
    fun `잔금미수면 자동 분류를 숨기지 않는다`() {
        // 날짜 태그가 '잔금미수' 라 겹치지 않는다 → 분류는 그대로 보여준다
        // 🔴 완료(workCompletedAt)가 찍혀야 잔금미수다. (2026-10-07 — SettlementCalc 와 통일)
        val c = customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, completedAt = now)
        assertEquals("잔금미수", label(c))
        assertFalse("겹치지 않는데 숨기면 안 된다", showsDday(c))
    }

    @Test
    fun `예약일 지났는데 완료 안 찍혔으면 시공확인이다`() {
        // 예약일만 지나고 완료 표시 없는 미수는 '잔금미수'가 아니라 '시공확인' — 돈 독촉 금지. (2026-10-07 사장님)
        //   완료(카드 '아직 못 받았어요')를 찍으면 그때 잔금미수로 바뀌어 잔금 대기 칩에 뜬다.
        val c = customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L)
        assertEquals("시공확인", label(c))
        assertFalse("겹치지 않는데 숨기면 안 된다", showsDday(c))
    }

    @Test
    fun `시공일이 없으면 자동 분류를 숨기지 않는다`() {
        assertFalse(showsDday(customer()))
    }

    // ── ② 신규는 '오늘 온 사람' 에게만 ──────────────────────────

    @Test
    fun `오늘 온 사람의 신규도 목록에선 숨긴다`() {
        // 파란 「신규」(오늘 처음 연락 온 사람)가 이미 홈 목록에 있다 → 노란 것까지 붙으면 두 번 말한다
        val todayOne = customer(createdAt = now)
        assertEquals("신규", label(todayOne))
        assertTrue(hiddenInList(todayOne))
    }

    @Test
    fun `열흘 된 고객은 아예 딱지가 없다`() {
        // 2026-09-27 부터 「신규」는 **오늘뿐**이다. 전엔 14일이라 열흘째 고객도 '신규' 였다.
        val tenDaysAgo = customer(createdAt = now - 10 * DAY)
        assertEquals("", label(tenDaysAgo))
        assertTrue(hiddenInList(tenDaysAgo))
    }

    @Test
    fun `오래 조용한 사람도 딱지가 없다`() {
        // 「미전환」을 없앤 자리. **빈 글자를 그리면 글자 없는 상자**가 남는다 —
        //   실제로 그렇게 났다 (2026-09-27 사장님 "태그가 짤린거같은데.. 뭐지").
        val quiet = customer(createdAt = now - 30 * DAY)
        assertEquals("", label(quiet))
        assertTrue(hiddenInList(quiet))
    }

    @Test
    fun `진짜 쓸모 있는 태그는 목록에 남는다`() {
        assertFalse(hiddenInList(customer(workDate = today0 + 2 * DAY)))          // 시공 D-2
        assertFalse(hiddenInList(customer(                                       // 시공확인(완료 전)
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L)))
        assertFalse(hiddenInList(customer(                                       // 잔금미수(완료 찍힘)
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, completedAt = now)))
        assertFalse(hiddenInList(customer(                                       // 완료
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, balancePaidAt = now)))
    }

    @Test
    fun `고객관리 필터 값은 그대로 유지된다`() {
        // 목록에서 숨기는 건 **표시**뿐 — 상태 계산은 그대로여야 필터가 안 깨진다
        assertEquals("신규", customerStatusOf(customer(createdAt = now), today0, now))
        assertEquals("예약", customerStatusOf(customer(workDate = today0 + DAY), today0, now))
        assertEquals("시공확인", customerStatusOf(customer(
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L
        ), today0, now))
        assertEquals("잔금미수", customerStatusOf(customer(
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, completedAt = now
        ), today0, now))
        assertEquals("완료", customerStatusOf(customer(
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, balancePaidAt = now
        ), today0, now))
    }

    @Test
    fun `고객관리 칩에 없는 상태를 만들지 않는다`() {
        // 칩 목록: 전체·신규·예약·시공확인·잔금미수·완료. 계산이 그 밖의 말을 내놓으면
        //   어느 칩에도 안 잡히는 **유령 고객**이 생긴다. 빈 글자만 예외(딱지 없음).
        val chips = setOf("신규", "예약", "시공확인", "잔금미수", "완료", "")
        val cases = listOf(
            customer(createdAt = now),
            customer(createdAt = now - 30 * DAY),
            customer(workDate = today0 + DAY),
            customer(workDate = today0),
            customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L),                    // 시공확인
            customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, completedAt = now), // 잔금미수
            customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, balancePaidAt = now)
        )
        cases.forEach { c ->
            val st = customerStatusOf(c, today0, now)
            assertTrue("칩에 없는 상태: " + st, st in chips)
        }
    }
}
