package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.domain.collab.CollabDayCheck.Kind
import com.detailline.callfollowcrm.domain.collab.CollabDayCheck.MyJob
import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🤝 협업 요청 — 「그날 되나?」. (2026-09-30 사장님)
 *
 * 틀리면 어느 쪽으로 틀리는가 —
 *   **안 겹치는데 겹친다**고 하면 될 일을 거절한다(손해).
 *   **겹치는데 안 겹친다**고 하면 수락해놓고 **못 간다**(손님·사장님 둘 다 손해).
 * 뒤쪽이 훨씬 나빠서, 애매하면 **겹친다고 말하는 쪽**으로 기운다.
 */
class CollabDayCheckTest {

    private val day = DateTimeUtils.DAY_MS
    private val d0 = DateTimeUtils.startOfDay(1_800_000_000_000L)
    private fun at(dayOffset: Int, hour: Int) = d0 + dayOffset * day + hour * 60 * 60 * 1000L
    private fun job(dayOffset: Int, hour: Int?, place: String? = "수원 장안구") =
        MyJob(d0 + dayOffset * day, hour?.let { it * 60 }, place)

    @Test fun `그날 아무것도 없으면 비었다`() {
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(3, 9)))
        assertEquals(Kind.FREE, r.kind)
        assertNull(r.place)
    }

    @Test fun `시간이 가까우면 겹친다`() {
        // 사장님 예: 요청 오후 1시 · 내 일정 오후 2시 → 겹친다.
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(0, 14)))
        assertEquals(Kind.TIME_CLASH, r.kind)
        assertEquals("수원 장안구", r.place)
        assertEquals("오후 2시", r.timeLabel)
    }

    @Test fun `같은 날이라도 멀면 안 겹친다`() {
        // 오전 8시 현장, 오후 1시 요청 → 5시간 차이라 할 수 있다.
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(0, 8)))
        assertEquals(Kind.SAME_DAY, r.kind)
        assertEquals("오전 8시", r.timeLabel)
    }

    @Test fun `내 일정 시간이 미정이면 겹친다고 단정하지 않는다`() {
        // 시간을 모르는데 「겹친다」고 하면 될 일을 거절하게 된다. 같은 날이라고만 말한다.
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(0, null)))
        assertEquals(Kind.SAME_DAY, r.kind)
        assertNull(r.timeLabel)
    }

    @Test fun `요청에 시간이 없으면 같은 날로만 본다`() {
        val r = CollabDayCheck.check(at(0, 0), false, listOf(job(0, 14)))
        assertEquals(Kind.SAME_DAY, r.kind)
    }

    @Test fun `겹치는 게 있으면 그걸 먼저 말한다`() {
        // 오전 8시(안 겹침)와 오후 2시(겹침)가 둘 다 있으면 **겹치는 쪽**을 말해야 한다.
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(0, 8, "이천"), job(0, 14, "수원")))
        assertEquals(Kind.TIME_CLASH, r.kind)
        assertEquals("수원", r.place)
    }

    @Test fun `다른 날 일정은 안 본다`() {
        val r = CollabDayCheck.check(at(0, 13), true, listOf(job(1, 13), job(-1, 13)))
        assertEquals(Kind.FREE, r.kind)
    }

    @Test fun `요청 시각이 없으면 비었다고 한다`() {
        assertEquals(Kind.FREE, CollabDayCheck.check(0L, true, listOf(job(0, 13))).kind)
    }

    @Test fun `시각 글자`() {
        assertEquals("오전 9시", CollabDayCheck.timeLabel(9 * 60))
        assertEquals("오후 1시", CollabDayCheck.timeLabel(13 * 60))
        assertEquals("오후 2시 30분", CollabDayCheck.timeLabel(14 * 60 + 30))
        assertEquals("오전 12시", CollabDayCheck.timeLabel(0))
        assertNull(CollabDayCheck.timeLabel(null))
        assertNull(CollabDayCheck.timeLabel(-5))
    }

    @Test fun `딱 네 시간 차이는 안 겹친다`() {
        // 경계 — 시공 하나가 네 시간이라 보니, 네 시간 뒤 시작은 이어서 할 수 있다.
        val r = CollabDayCheck.check(at(0, 9), true, listOf(job(0, 13)))
        assertEquals(Kind.SAME_DAY, r.kind)
    }

    @Test fun `세 시간 반 차이는 겹친다`() {
        val r = CollabDayCheck.check(at(0, 9), true, listOf(MyJob(d0, 12 * 60 + 30, "이천")))
        assertEquals(Kind.TIME_CLASH, r.kind)
    }

    // ────────────────────────────────────────────────────────────────
    // 🗓️ 지난 날짜 현장은 수락할 수 없다. (2026-10-01 사장님
    //    "날짜가 지난 협업요청이 수락이 되네..? 체크해줘")
    //    9.30 오전 9시 현장이 10/1 에 수락됐다. 수락을 막는 자가
    //    「요청 보낸 지 12시간」 하나뿐이고 시공 날짜는 아무도 안 봤다.
    // ────────────────────────────────────────────────────────────────

    private fun at(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0): Long =
        java.time.ZonedDateTime.of(y, mo, d, h, mi, 0, 0, java.time.ZoneId.of("Asia/Seoul"))
            .toInstant().toEpochMilli()

    @Test
    fun `사장님이 본 그것 — 9월 30일 현장을 10월 1일에 수락하려 하면 지난 것이다`() {
        val site = at(2026, 9, 30, 9, 0)      // 9.30 오전 9시
        val now = at(2026, 10, 1, 9, 1)       // 10/1 오전 9시 1분
        assertTrue(CollabDayCheck.dayPassed(site, now))
    }

    @Test
    fun `오늘 현장은 아직 받을 수 있다 — 아침에 불러 그날 합류하는 일이 흔하다`() {
        val site = at(2026, 10, 1, 9, 0)
        assertFalse("같은 날이면 지난 게 아니다", CollabDayCheck.dayPassed(site, at(2026, 10, 1, 8, 0)))
        assertFalse("시각이 지났어도 그날이면 받는다", CollabDayCheck.dayPassed(site, at(2026, 10, 1, 23, 59)))
    }

    @Test
    fun `내일 현장은 당연히 받을 수 있다`() {
        assertFalse(CollabDayCheck.dayPassed(at(2026, 10, 2), at(2026, 10, 1, 23, 0)))
    }

    @Test
    fun `어제면 하루만 지나도 지난 것이다`() {
        assertTrue(CollabDayCheck.dayPassed(at(2026, 9, 30, 23, 59), at(2026, 10, 1, 0, 1)))
    }

    @Test
    fun `날짜 미정은 막지 않는다 — 날짜부터 잡자는 요청이 있다`() {
        assertFalse(CollabDayCheck.dayPassed(0L, at(2026, 10, 1)))
        assertFalse(CollabDayCheck.dayPassed(-1L, at(2026, 10, 1)))
    }

    @Test
    fun `해를 넘겨도 센다`() {
        assertTrue(CollabDayCheck.dayPassed(at(2026, 12, 31), at(2027, 1, 1, 0, 30)))
        assertFalse(CollabDayCheck.dayPassed(at(2027, 1, 1), at(2026, 12, 31, 23, 30)))
    }
}
