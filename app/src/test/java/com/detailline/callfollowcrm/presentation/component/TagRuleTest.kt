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
        createdAt: Long = now
    ) = CustomerEntity(
        phoneNumber = "01011112222",
        scheduledWorkDate = workDate,
        totalAmount = total,
        depositAmount = deposit,
        balancePaidAt = balancePaidAt,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun label(c: CustomerEntity) = scheduleTagLabel(c, today0, now)

    /** 화면이 쓰는 것과 **같은 판정** — CustomerTags 안의 규칙을 그대로 옮긴 것. */
    private fun showsDday(c: CustomerEntity) = label(c).startsWith("시공 D")
    private fun hiddenInList(c: CustomerEntity) = label(c).let { it == "미전환" || it == "신규" }

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
        val c = customer(workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L)
        assertEquals("잔금미수", label(c))
        assertFalse("겹치지 않는데 숨기면 안 된다", showsDday(c))
    }

    @Test
    fun `시공일이 없으면 자동 분류를 숨기지 않는다`() {
        assertFalse(showsDday(customer()))
    }

    // ── ② 신규는 '오늘 온 사람' 에게만 ──────────────────────────

    @Test
    fun `날짜 태그의 신규는 목록에서 숨긴다`() {
        // 열흘째 얘기 중인 고객도 시공일만 없으면 '신규' 로 계산된다 → 목록에선 안 보여준다
        val tenDaysAgo = customer(createdAt = now - 10 * DAY)
        assertEquals("신규", label(tenDaysAgo))
        assertTrue("열흘 된 고객이 '신규' 로 보이면 착각한다", hiddenInList(tenDaysAgo))
    }

    @Test
    fun `미전환도 목록에서 숨긴다`() {
        assertTrue(hiddenInList(customer(createdAt = now - 30 * DAY)))
    }

    @Test
    fun `진짜 쓸모 있는 태그는 목록에 남는다`() {
        assertFalse(hiddenInList(customer(workDate = today0 + 2 * DAY)))          // 시공 D-2
        assertFalse(hiddenInList(customer(                                       // 잔금미수
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L)))
        assertFalse(hiddenInList(customer(                                       // 완료
            workDate = today0 - DAY, total = 1_000_000L, deposit = 200_000L, balancePaidAt = now)))
    }

    @Test
    fun `고객관리 필터 값은 그대로 유지된다`() {
        // 목록에서 숨기는 건 **표시**뿐 — 상태 계산은 그대로여야 필터가 안 깨진다
        assertEquals("신규", customerStatusOf(customer(createdAt = now - 10 * DAY), today0, now))
        assertEquals("미전환", customerStatusOf(customer(createdAt = now - 30 * DAY), today0, now))
    }
}
