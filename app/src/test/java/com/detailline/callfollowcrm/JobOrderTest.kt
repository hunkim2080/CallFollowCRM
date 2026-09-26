package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.JobOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🔢 차수 셈 — 화면 세 군데(탭·메모 제목·사진 제목)가 같이 쓰는 규칙.
 *   폰에서 눈으로 보기 어려운 경우의 수를 여기서 대신 본다.
 */
class JobOrderTest {

    private val D0918 = 1_758_000_000_000L   // 9/18 쯤
    private val D0923 = 1_758_400_000_000L   // 9/23 쯤
    private val D1010 = 1_760_000_000_000L   // 10/10 쯤

    /** 🔴 사장님이 찾은 것: "1차 시공도 없는데 2차 시공 날짜가 잡히는 건 버그인가?" */
    @Test
    fun `날짜 없는 건은 끝낸 건보다 뒤로 간다`() {
        // 9/23 에 끝낸 건(id 1) + 날짜를 아직 안 잡은 건(id 2)
        val order = JobOrder.order(listOf(D0923 to 1L, null to 2L))
        assertEquals(1, JobOrder.nth(order, 1L))   // 9/23 = 1차
        assertEquals(2, JobOrder.nth(order, 2L))   // 날짜 미정 = 2차
    }

    @Test
    fun `먼저 한 날이 1차다`() {
        val order = JobOrder.order(listOf(D1010 to 7L, D0918 to 3L, D0923 to 5L))
        assertEquals(1, JobOrder.nth(order, 3L))
        assertEquals(2, JobOrder.nth(order, 5L))
        assertEquals(3, JobOrder.nth(order, 7L))
    }

    /** 지금 건(대표)에 날짜가 있으면 **날짜 자리**에 낀다 — 무조건 맨 뒤가 아니다. */
    @Test
    fun `지금 건도 날짜가 있으면 날짜순으로 낀다`() {
        val order = JobOrder.order(listOf(D1010 to 4L, D0918 to 2L, D0923 to JobOrder.CURRENT))
        assertEquals(1, JobOrder.nth(order, 2L))
        assertEquals(2, JobOrder.nth(order, JobOrder.CURRENT))
        assertEquals(3, JobOrder.nth(order, 4L))
    }

    /** 날짜가 둘 다 없으면 **먼저 만든 건**이 앞. 지금 건은 그중 맨 뒤. */
    @Test
    fun `날짜가 둘 다 없으면 먼저 만든 건이 앞이고 지금 건이 맨 뒤다`() {
        val order = JobOrder.order(listOf(null to 9L, null to 2L, null to JobOrder.CURRENT))
        assertEquals(1, JobOrder.nth(order, 2L))
        assertEquals(2, JobOrder.nth(order, 9L))
        assertEquals(3, JobOrder.nth(order, JobOrder.CURRENT))
    }

    /** 같은 날 두 건이면 먼저 만든 것이 1차. 번호가 겹치면 안 된다. */
    @Test
    fun `같은 날 두 건이어도 번호가 겹치지 않는다`() {
        val order = JobOrder.order(listOf(D0923 to 8L, D0923 to 3L))
        assertEquals(1, JobOrder.nth(order, 3L))
        assertEquals(2, JobOrder.nth(order, 8L))
    }

    /** 건이 하나뿐이면 1차. */
    @Test
    fun `건이 하나면 1차다`() {
        val order = JobOrder.order(listOf(null to JobOrder.CURRENT))
        assertEquals(1, JobOrder.nth(order, JobOrder.CURRENT))
    }

    /** 목록에 없는 건을 물으면 맨 뒤 다음 번호 — 0이나 음수가 나오면 안 된다. */
    @Test
    fun `모르는 건은 맨 뒤 다음 번호다`() {
        val order = JobOrder.order(listOf(D0918 to 1L, D0923 to 2L))
        assertEquals(3, JobOrder.nth(order, 99L))
    }

    @Test
    fun `빈 목록이어도 1차부터 센다`() {
        assertEquals(1, JobOrder.nth(emptyList(), 5L))
    }
}
