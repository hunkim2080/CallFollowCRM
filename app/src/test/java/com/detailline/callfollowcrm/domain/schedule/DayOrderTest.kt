package com.detailline.callfollowcrm.domain.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🕐 **그날 갈 곳은 시간순.** (2026-10-01 사장님)
 *
 *   "내 현장(시간 미정)이 첫 번째에 있고 9시 협업 현장이 두 번째에 있더라고.
 *    시간순으로 떠야 하지 않을까? 시간 미정이면 맨 마지막에 와야 하고 말야"
 *
 * 이 확인이 막는 것 —
 *   ① 「오전 9시」를 **못 읽어서** 협업이 맨 뒤로 밀리는 것 (실제로 그랬다)
 *   ② 자정(0시)으로 들어온 **시간 미정**을 「새벽 0시 시공」으로 믿는 것
 *   ③ 시간 미정이 **맨 앞**에 서는 것
 */
class DayOrderTest {

    private fun c(label: String?, at: Long = 0L) = DayOrder.collabMinutes(label, at)

    @Test
    fun `서버가 주는 말을 읽는다 — 실제로 오는 건 이 네 가지뿐이다`() {
        // 2026-09-30 서버 실측: 오전 9시 · 오전 8시 · 오후 1시 · 오후 2시
        assertEquals(9 * 60, c("오전 9시"))
        assertEquals(8 * 60, c("오전 8시"))
        assertEquals(13 * 60, c("오후 1시"))
        assertEquals(14 * 60, c("오후 2시"))
    }

    @Test
    fun `이게 안 읽혀서 협업이 늘 맨 뒤였다`() {
        assertTrue("「오전 9시」를 못 읽으면 9시 현장이 맨 뒤로 간다", c("오전 9시") < DayOrder.UNKNOWN)
    }

    @Test
    fun `분까지 적힌 것도 읽는다`() {
        assertEquals(9 * 60 + 30, c("오전 9시 30분"))
        assertEquals(14 * 60 + 30, c("오후 2시 30분"))
    }

    @Test
    fun `낮 열두시와 밤 열두시를 헷갈리지 않는다`() {
        assertEquals("오후 12시 = 정오", 12 * 60, c("오후 12시"))
        assertEquals("오전 12시 = 자정", 0, c("오전 12시"))
    }

    @Test
    fun `옛 자료의 9 대 00 모양도 읽는다`() {
        assertEquals(9 * 60, c("9:00"))
        assertEquals(14 * 60 + 5, c("14:05"))
    }

    @Test
    fun `시각을 모르면 맨 뒤 — 시간 미정이 맨 앞에 서면 안 된다`() {
        assertEquals(DayOrder.UNKNOWN, c(null))
        assertEquals(DayOrder.UNKNOWN, c(""))
        assertEquals(DayOrder.UNKNOWN, c("   "))
        assertEquals(DayOrder.UNKNOWN, c("협의 후"))
    }

    @Test
    fun `자정으로 들어온 건 시간 미정이다 — 새벽 0시 시공이 아니다`() {
        // 2026-10-01 00:00 KST
        val midnight = java.time.ZonedDateTime
            .of(2026, 10, 1, 0, 0, 0, 0, java.time.ZoneId.of("Asia/Seoul"))
            .toInstant().toEpochMilli()
        assertEquals(DayOrder.UNKNOWN, c(null, midnight))
    }

    @Test
    fun `라벨이 없으면 예정 시각에서 읽는다`() {
        val at14 = java.time.ZonedDateTime
            .of(2026, 10, 1, 14, 0, 0, 0, java.time.ZoneId.of("Asia/Seoul"))
            .toInstant().toEpochMilli()
        assertEquals(14 * 60, c(null, at14))
    }

    @Test
    fun `내 시공도 같은 자로 잰다`() {
        assertEquals(9 * 60, DayOrder.jobMinutes(9 * 60))
        assertEquals(DayOrder.UNKNOWN, DayOrder.jobMinutes(null))
    }

    @Test
    fun `사장님이 본 그 화면 — 시간 미정 내 현장과 오전 9시 협업`() {
        val mine = DayOrder.jobMinutes(null)          // 시간 미정 · 상률로 32
        val collab = c("오전 9시")                     // 오전 9시 · 서초대로 50
        assertTrue("9시 협업이 시간 미정보다 앞에 와야 한다", collab < mine)
        val order = listOf("내현장" to mine, "협업" to collab).sortedBy { it.second }
        assertEquals("협업", order[0].first)
        assertEquals("내현장", order[1].first)
    }
}
