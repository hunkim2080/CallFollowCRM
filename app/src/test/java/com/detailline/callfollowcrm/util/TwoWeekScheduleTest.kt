package com.detailline.callfollowcrm.util

import com.detailline.callfollowcrm.util.TwoWeekSchedule.Source
import com.detailline.callfollowcrm.util.TwoWeekSchedule.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 전화 카드 2주 달력 검산. (2026-09-19)
 *
 * 사장님이 "경우의 수까지 디버깅하고 최종본 달라"고 하신 자리라,
 * **직접 손으로 못 쳐보는 경우**(하루 두 탕·A/S·긴 제목·여러 날짜)를 여기서 다 돌린다.
 */
class TwoWeekScheduleTest {

    private fun dayAfter(n: Int): Long =
        DateTimeUtils.startOfDay(System.currentTimeMillis()) + n * DateTimeUtils.DAY_MS

    private fun job(n: Int, addr: String, minutes: Int? = 540, days: Int = 1, who: String = "김○○ 님") =
        Source(Tone.JOB, dayAfter(n), days, minutes, who, addr)

    private fun asJob(n: Int, addr: String, minutes: Int? = 660) =
        Source(Tone.AS, dayAfter(n), 1, minutes, "조○○ 님", addr)

    private fun event(n: Int, title: String, memo: String = "") =
        Source(Tone.EVENT, dayAfter(n), 1, 780, title, memo)

    private fun textAt(days: List<TwoWeekSchedule.Day>, n: Int): List<String> =
        TwoWeekSchedule.cellLines(days[n]).map { it.text }

    // ── 하루 두 탕 · 같은 지역 묶기 ─────────────────────────────
    /** 사장님: "하루 두 탕 뛰는 경우도 있어." — 곳수를 색으로 막지 않고 **동네를 나란히** 보여준다. */
    @Test
    fun `하루 두 곳이면 동네를 둘 다 적는다`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(1, "서울특별시 강서구 화곡동 삼성아파트 101동", 600),
                job(1, "경기도 수원시 영통구 e편한세상 204동", 900)
            )
        )
        assertEquals(listOf("강서·영통"), textAt(days, 1))
    }

    /**
     * 같은 동네로 두 곳 가면 한 번만 적는다 — "화성·화성" 은 말이 안 된다.
     * 같은 시(市) 안에서 동만 다른 건 **묶어 가는 판단엔 같은 동네**라 한 줄로 합치는 게 맞다.
     */
    @Test
    fun `같은 시 안에서 동만 다르면 한 번만`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(2, "화성시 반송동 시범한빛 302동", 540),
                job(2, "화성시 청계동 시범우남퍼스트빌", 840)
            )
        )
        assertEquals(listOf("화성"), textAt(days, 2))
    }

    @Test
    fun `세 곳 넘으면 외 N`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(3, "서울 양천구 목동 신시가지 7단지"),
                job(3, "서울 강동구 고덕그라시움 112동"),
                job(3, "서울 송파구 헬리오시티 305동")
            )
        )
        assertEquals(listOf("양천 외 2"), textAt(days, 3))
    }

    // ── A/S ────────────────────────────────────────────────
    /** 사장님: "a/s있는 날은 [A/s]지역 이렇게 나오면 좋을듯" — 괄호·슬래시는 칸을 다 먹어서 뺐다. */
    @Test
    fun `A_S 만 있는 날은 AS 동네`() {
        val days = TwoWeekSchedule.days(listOf(asJob(4, "서울 강서구 화곡동 삼성아파트")))
        assertEquals(listOf("AS 강서"), textAt(days, 4))
    }

    @Test
    fun `시공과 A_S 가 같은 날이면 두 줄`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(5, "화성시 청계동 시범우남퍼스트빌"),
                asJob(5, "성남시 분당구 서현동 시범삼성 9동")
            )
        )
        assertEquals(listOf("화성", "AS 분당"), textAt(days, 5))
    }

    /** 세 가지가 다 있으면 두 줄만 쓰고 뒤에 외1. 시공 → A/S → 내 일정 순. */
    @Test
    fun `시공 A_S 일정이 다 있으면 두 줄에 외1`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(6, "화성시 청계동 아파트"),
                asJob(6, "성남시 분당구 서현동"),
                event(6, "장모님댁 생신 모임")
            )
        )
        assertEquals(listOf("화성", "AS 분당 외1"), textAt(days, 6))
    }

    // ── 내 일정 ─────────────────────────────────────────────
    @Test
    fun `긴 제목은 첫 낱말만`() {
        assertEquals("장모님댁", TwoWeekSchedule.firstWord("장모님댁 생신 모임"))
        assertEquals("병원", TwoWeekSchedule.firstWord("병원 물리치료"))
        assertEquals("부가세", TwoWeekSchedule.firstWord("부가세 신고 마감"))
        assertEquals("처남", TwoWeekSchedule.firstWord("처남 결혼식 사진"))
    }

    /** 띄어쓰기가 아예 없으면 어쩔 수 없이 자른다 — 어느 방법을 써도 같다. */
    @Test
    fun `띄어쓰기 없는 제목은 자른다`() {
        assertEquals("어머니모…", TwoWeekSchedule.firstWord("어머니모시고병원가기"))
    }

    @Test
    fun `빈 제목도 안 깨진다`() {
        assertEquals("일정", TwoWeekSchedule.firstWord("   "))
    }

    @Test
    fun `일정 두 개면 외 1`() {
        val days = TwoWeekSchedule.days(listOf(event(7, "병원 물리치료"), event(7, "부가세 신고")))
        assertEquals(listOf("병원 외 1"), textAt(days, 7))
    }

    // ── 빈 날 판정 ───────────────────────────────────────────
    /** ⚠️ 장모님댁 가는 날을 "비어요" 라고 하면 **앱이 거짓말을 한 것**이다. */
    @Test
    fun `일정만 있어도 빈 날이 아니다`() {
        val days = TwoWeekSchedule.days(listOf(event(8, "장모님댁 생신 모임")))
        assertFalse(days[8].isFree)
        assertEquals(listOf("장모님댁"), textAt(days, 8))
    }

    @Test
    fun `A_S 만 있어도 빈 날이 아니다`() {
        val days = TwoWeekSchedule.days(listOf(asJob(9, "서울 강서구 화곡동")))
        assertFalse(days[9].isFree)
    }

    @Test
    fun `아무것도 없으면 비었음`() {
        val days = TwoWeekSchedule.days(emptyList())
        assertTrue(days.all { it.isFree })
        assertEquals(listOf("비었음"), textAt(days, 5))
    }

    // ── 일요일 ──────────────────────────────────────────────
    /** 사장님: "일요일까지 다 보여야함." — 요일로 미리 빼지 않는다. */
    @Test
    fun `일요일도 빈 날에 들어간다`() {
        val days = TwoWeekSchedule.days(emptyList())
        val sunday = days.firstOrNull { it.weekday == "일" }
        requireNotNull(sunday) { "2주 안에 일요일이 없을 수 없다" }
        assertTrue(sunday.isFree)
        val label = TwoWeekSchedule.freeDaysLabel(days, max = 14)
        assertTrue("일요일이 빈 날 목록에 있어야 한다", label!!.contains("${sunday.dayOfMonth}일(일)"))
    }

    // ── 날짜 범위 · 여러 날짜 시공 ───────────────────────────────
    @Test
    fun `오늘부터 열나흘이고 첫 칸이 오늘이다`() {
        val days = TwoWeekSchedule.days(emptyList())
        assertEquals(14, days.size)
        assertTrue(days[0].isToday)
        assertFalse(days[1].isToday)
    }

    @Test
    fun `어제 일과 2주 뒤 일은 안 들어온다`() {
        val days = TwoWeekSchedule.days(listOf(job(-1, "서울 강서구"), job(14, "서울 강서구")))
        assertTrue(days.all { it.isFree })
    }

    /** 이틀짜리 시공은 이틀 다 찍힌다. 둘째 날은 시작 시각 대신 "2일째". */
    @Test
    fun `이틀짜리 시공은 이틀 다 찍힌다`() {
        val days = TwoWeekSchedule.days(listOf(job(3, "화성시 반송동 한빛마을", 540, days = 2)))
        assertEquals(listOf("화성"), textAt(days, 3))
        assertEquals(listOf("화성"), textAt(days, 4))
        assertEquals("오전 9시", days[3].items[0].time)
        assertEquals("2일째", days[4].items[0].time)
    }

    /** 어제 시작한 이틀짜리는 **오늘 칸에** 찍혀야 한다 — 어제만 보고 버리면 오늘이 빈 날로 둔갑한다. */
    @Test
    fun `어제 시작해 오늘 걸치는 시공은 오늘 칸에 남는다`() {
        val days = TwoWeekSchedule.days(listOf(job(-1, "화성시 반송동 한빛마을", 540, days = 2)))
        assertFalse(days[0].isFree)
        assertEquals("2일째", days[0].items[0].time)
    }

    /**
     * 시간을 안 적은 **시공**을 "하루 종일" 이라 하면 온종일 묶인 것처럼 보인다 — 안 정한 것뿐이다.
     * 반대로 **내 일정**은 원래 종일인 게 많다.
     */
    @Test
    fun `시간 안 적은 시공은 시간 미정 일정은 하루 종일`() {
        val days = TwoWeekSchedule.days(
            listOf(
                job(10, "서울 강서구 화곡동", minutes = null),
                asJob(11, "서울 송파구 잠실동", minutes = null),
                event(12, "장모님댁 생신").copy(minutes = null)
            )
        )
        assertEquals("시간 미정", days[10].items[0].time)
        assertEquals("시간 미정", days[11].items[0].time)
        assertEquals("하루 종일", days[12].items[0].time)
    }

    // ── 정렬 ────────────────────────────────────────────────
    /** 글자로 정렬하면 "오전 10시" 가 "오전 9시" 보다 앞에 온다. 분으로 세워야 맞다. */
    @Test
    fun `같은 날 이른 시간이 먼저`() {
        val days = TwoWeekSchedule.days(
            listOf(job(2, "서울 강서구 화곡동", 600), job(2, "서울 송파구 잠실동", 540))
        )
        assertEquals(listOf("오전 9시", "오전 10시"), days[2].items.map { it.time })
    }

    @Test
    fun `시공이 A_S 보다 먼저 A_S 가 일정보다 먼저`() {
        val days = TwoWeekSchedule.days(
            listOf(event(1, "병원"), asJob(1, "서울 강서구"), job(1, "서울 송파구"))
        )
        assertEquals(listOf(Tone.JOB, Tone.AS, Tone.EVENT), days[1].items.map { it.tone })
    }

    // ── 빈 날 줄 ────────────────────────────────────────────
    @Test
    fun `빈 날 줄은 셋까지만 적고 나머지는 외 N일`() {
        val days = TwoWeekSchedule.days(emptyList())
        val label = TwoWeekSchedule.freeDaysLabel(days)
        assertTrue(label!!.endsWith("외 11일"))
        assertEquals(3, label.split(" · ").size)
    }

    @Test
    fun `2주가 꽉 차면 빈 날 줄이 없다`() {
        val all = (0 until 14).map { job(it, "서울 강서구 화곡동") }
        assertNull(TwoWeekSchedule.freeDaysLabel(TwoWeekSchedule.days(all)))
    }

    // ── 주소가 없을 때 ──────────────────────────────────────────
    /** 주소를 아직 안 적은 건이라도 **그날 뭔가 있다는 건 사실**이라 칸을 비우면 안 된다. */
    @Test
    fun `주소 없는 시공도 빈 날로 두지 않는다`() {
        val days = TwoWeekSchedule.days(listOf(job(4, ""), job(4, "  ")))
        assertFalse(days[4].isFree)
        assertEquals(listOf("시공 2"), textAt(days, 4))
    }

    @Test
    fun `주소 없는 A_S 는 AS 만`() {
        val days = TwoWeekSchedule.days(listOf(asJob(4, "")))
        assertEquals(listOf("AS"), textAt(days, 4))
    }

    /** 한 건만 주소가 풀리면 그 동네를 쓴다 — 못 푼 건 때문에 통째로 버리지 않는다. */
    @Test
    fun `일부만 주소가 있으면 있는 쪽을 쓴다`() {
        val days = TwoWeekSchedule.days(listOf(job(4, ""), job(4, "서울 강서구 화곡동")))
        assertEquals(listOf("강서"), textAt(days, 4))
    }

    // ── 요일 ────────────────────────────────────────────────
    @Test
    fun `요일 글자가 달력과 맞는다`() {
        val days = TwoWeekSchedule.days(emptyList())
        val cal = Calendar.getInstance()
        for (d in days) {
            cal.timeInMillis = d.dayStartMs
            val expected = arrayOf("일", "월", "화", "수", "목", "금", "토")[cal.get(Calendar.DAY_OF_WEEK) - 1]
            assertEquals(expected, d.weekday)
        }
    }
}
