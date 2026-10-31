package com.detailline.callfollowcrm.domain.collab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 협업 배정 줄 파싱 + "그 건(날짜)에만 붙는다" 판단 검증. (2026-10-09 사장님 — 1차/2차 분리)
 *
 * 2026-10-15 사장님 결정: **날짜 없는 떠돌이 협업은 어느 건에도 자동으로 안 붙는다.** 사장님이 「이 건」을 직접 고른다.
 *   (전엔 대표 건 날짜로 폴백 → 1차·2차 둘 다 완료면 대표=2차라서 1차 협업 사장이 2차 카드로 넘어가 보였다.)
 */
class CollabAssignmentLineTest {

    private val DAY = 24L * 60 * 60 * 1000
    private fun day(n: Int) = n * DAY

    @Test fun `5토큰 파싱 — days 포함`() {
        val l = CollabAssignmentLine.parse("7|01012345678|하우스픽|sh_abc|${day(10)},${day(11)}")!!
        assertEquals(7L, l.customerId)
        assertEquals("01012345678", l.phone)
        assertEquals("하우스픽", l.name)
        assertEquals("sh_abc", l.shareId)
        assertEquals(setOf(day(10), day(11)), l.days)
    }

    @Test fun `4토큰(구버전 days 없음) 파싱`() {
        val l = CollabAssignmentLine.parse("7|01012345678|하우스픽|sh_abc")!!
        assertEquals("sh_abc", l.shareId)
        assertTrue(l.days.isEmpty())
    }

    @Test fun `2토큰(아주 옛날 id name) 파싱`() {
        val l = CollabAssignmentLine.parse("7|하우스픽")!!
        assertEquals(7L, l.customerId)
        assertEquals("하우스픽", l.name)
        assertEquals("", l.shareId)
        assertTrue(l.days.isEmpty())
    }

    @Test fun `customerId 없으면 null`() {
        assertNull(CollabAssignmentLine.parse("|a|b"))
        assertNull(CollabAssignmentLine.parse("abc|a|b"))
    }

    @Test fun `format 왕복 — days 정렬`() {
        val l = CollabAssignmentLine(7, "01012345678", "하우스픽", "sh_abc", setOf(day(11), day(10)))
        assertEquals("7|01012345678|하우스픽|sh_abc|${day(10)},${day(11)}", l.format())
        assertEquals(l.copy(), CollabAssignmentLine.parse(l.format()))
    }

    // ---- coversDay: 핵심 — 그 건(날짜)에만 ----

    @Test fun `days 있으면 그 날에만 — 1차 협업자가 2차에 안 뜸`() {
        val h = CollabAssignmentLine(7, "p", "하우스픽", "sh1", setOf(day(30)))      // 1차 9/30
        assertTrue(h.coversDay(day(30)))
        assertFalse("2차 날짜엔 안 떠야", h.coversDay(day(45)))  // 2차(10/15)
    }

    @Test fun `days 비면 어느 날에도 안 뜸 — 대표 건으로 몰리지 않는다`() {
        val legacy = CollabAssignmentLine(7, "p", "태그줄눈", "sh2", emptySet())
        assertFalse(legacy.coversDay(day(45)))
        assertFalse(legacy.coversDay(day(30)))
        assertTrue("날짜 없는 줄 = 떠돌이", legacy.isUnassigned)
    }

    @Test fun `옛 입구(representativeDay 넘기는 collabCoversDay)도 같은 답 — 폴백 없음`() {
        // ScheduleScreen 이 아직 이 모양으로 부른다. 대표 건 날짜를 넘겨도 days 가 비면 안 걸린다.
        assertFalse(collabCoversDay(emptySet(), day(45), representativeDay = day(45)))
        assertTrue(collabCoversDay(setOf(day(45)), day(45), representativeDay = null))
    }

    // ── forJob / shareIdsForJob — 카드 협업 칩과 주소 전파가 같은 타깃팅 (2026-10-15 사장님 "2차 주소 바꿨는데 B 에 안 감") ──

    private val lines = listOf(
        "7|01011110001|하우스픽|sh1|${day(30)}",            // 1차(9/30) 협업
        "7|01011110002|태그줄눈|sh2|${day(45)}",            // 2차(10/15) 협업
        "7|01011110003|옛줄|sh3|",                          // days 없는 구버전 → 떠돌이(어느 건에도 안 붙음)
        "7|01011110002|태그줄눈|sh2|${day(45)}",            // 같은 사장 중복 줄
        "8|01011110009|딴손님|sh9|${day(45)}"               // 다른 고객
    )

    @Test fun `forJob - 2차 날짜엔 2차 협업만 (1차·다른 고객·떠돌이 X)`() {
        val got = CollabAssignmentLine.forJob(lines, customerId = 7, jobDayStart = day(45))
        assertEquals(listOf("sh2"), got.map { it.shareId })
        assertEquals(listOf("sh2"), CollabAssignmentLine.shareIdsForJob(lines, 7, day(45)))
    }

    @Test fun `forJob - 1차 날짜엔 1차 협업만, 떠돌이는 안 섞임`() {
        assertEquals(listOf("sh1"), CollabAssignmentLine.shareIdsForJob(lines, 7, day(30)))
    }

    @Test fun `forJob - 날짜 모르면(null) 아무도 안 고른다 — 전체로 퍼뜨리지 않음`() {
        assertTrue(CollabAssignmentLine.forJob(lines, 7, jobDayStart = null).isEmpty())
        assertTrue(CollabAssignmentLine.shareIdsForJob(lines, 7, null).isEmpty())
    }

    @Test fun `withDays — 일정 변경 시 날짜 갈아끼우기`() {
        val before = CollabAssignmentLine(7, "p", "하우스픽", "sh1", setOf(day(30)))
        val after = before.withDays(setOf(day(45)))
        assertEquals(setOf(day(45)), after.days)
        assertFalse(after.coversDay(day(30)))
        assertTrue(after.coversDay(day(45)))
    }

    // ── 🐞 사장님 실기 재현 (2026-10-15): 1차(9/30 완료)·2차(10/15 완료) 둘 다 협업. 1차 줄은 날짜 없음(옛 빌드 invite).
    //    대표 건 = 둘 다 완료라 더 나중 = 2차. 옛 규칙이면 1차 줄이 2차 카드로 넘어가 「1차엔 협업 부르기만, 2차엔 두 이름」.

    private val realCase = setOf(
        "7|01011110001|해시줄눈|sh1|",              // 1차 — 날짜 꼬리표 없음 (떠돌이)
        "7|01011110002|하우스픽|sh2|${day(45)}"     // 2차 — 10/15
    )

    @Test fun `재현 - 1차 떠돌이 줄이 2차(대표) 카드로 넘어가지 않는다`() {
        val on2nd = CollabAssignmentLine.forJob(realCase, 7, day(45))
        assertEquals("2차 카드엔 하우스픽 하나만", listOf("하우스픽"), on2nd.map { it.name })
        assertTrue("1차 카드엔 (아직) 아무도 없다 — 사장님이 붙여야 한다", CollabAssignmentLine.forJob(realCase, 7, day(30)).isEmpty())
    }

    @Test fun `재현 - 떠돌이는 unassigned 로 보인다 (사라진 게 아니다)`() {
        val orphans = CollabAssignmentLine.unassigned(realCase, 7)
        assertEquals(listOf("해시줄눈"), orphans.map { it.name })
        assertTrue(CollabAssignmentLine.unassigned(realCase, 8).isEmpty())
    }

    @Test fun `재현 - 이 건에 붙이기 → 1차 카드에 뜨고 떠돌이 목록에서 빠진다`() {
        val orphan = CollabAssignmentLine.unassigned(realCase, 7).single()
        val after = CollabAssignmentLine.assignDays(realCase, orphan, setOf(day(30)))
        assertEquals(listOf("해시줄눈"), CollabAssignmentLine.forJob(after, 7, day(30)).map { it.name })
        assertEquals(listOf("하우스픽"), CollabAssignmentLine.forJob(after, 7, day(45)).map { it.name })
        assertTrue(CollabAssignmentLine.unassigned(after, 7).isEmpty())
        assertEquals("줄 수는 그대로(바꿔 끼움)", 2, after.size)
    }

    @Test fun `assignDays - shareId 없는 아주 옛 줄은 번호로 맞춰 바꾼다, 다른 줄은 손 안 댐`() {
        val raw = setOf("7|01011110001|해시줄눈", "7|01011110002|하우스픽|sh2|${day(45)}")
        val orphan = CollabAssignmentLine.unassigned(raw, 7).single()
        val after = CollabAssignmentLine.assignDays(raw, orphan, setOf(day(30)))
        assertTrue(after.contains("7|01011110001|해시줄눈||${day(30)}"))
        assertTrue(after.contains("7|01011110002|하우스픽|sh2|${day(45)}"))
    }

    @Test fun `assignDays - 빈 days 로는 못 붙인다(떠돌이를 떠돌이로)`() {
        val orphan = CollabAssignmentLine.unassigned(realCase, 7).single()
        assertEquals(realCase, CollabAssignmentLine.assignDays(realCase, orphan, emptySet()))
    }

    @Test fun `daysOfJob - 시공일 + 기간 → 그 건의 날짜들, 날짜 없으면 빈 집합`() {
        assertEquals(setOf(day(30), day(31), day(32)), CollabAssignmentLine.daysOfJob(day(30), 3))
        assertEquals(setOf(day(30)), CollabAssignmentLine.daysOfJob(day(30), 0))
        assertTrue(CollabAssignmentLine.daysOfJob(null, 3).isEmpty())
    }

    // ── 🐞 2026-10-31 사장님: 2일짜리 건(11/12~13)에 **2일차(11/13)만** 협업 불렀는데 건 카드에 안 떴다.
    //    forJob 이 시작일(11/12) 하나만 봐서 11/13 협업을 놓쳤다. → **건 전체 날짜 집합**으로 본다.

    @Test fun `forJob(날짜집합) - 멀티데이 건 2일차만 맡은 협업도 그 건에 뜬다`() {
        val raw = setOf("7|01011110002|협업사장|sh2|${day(13)}")   // 2일차(day13)만 맡음
        val jobDays = CollabAssignmentLine.daysOfJob(day(12), 2)   // 11/12~13 = {12,13}
        assertEquals(setOf(day(12), day(13)), jobDays)
        assertEquals(listOf("sh2"), CollabAssignmentLine.forJob(raw, 7, jobDays).map { it.shareId })
        assertEquals(listOf("sh2"), CollabAssignmentLine.shareIdsForJob(raw, 7, jobDays))
        // 시작일 하나만 보면(옛 방식) 놓친다 — 이게 버그였다
        assertTrue(CollabAssignmentLine.forJob(raw, 7, jobDayStart = day(12)).isEmpty())
    }

    @Test fun `forJob(날짜집합) - 날짜 안 겹치는 다른 건엔 안 샌다`() {
        val raw = setOf("7|01011110002|협업사장|sh2|${day(13)}")
        assertTrue(CollabAssignmentLine.forJob(raw, 7, CollabAssignmentLine.daysOfJob(day(20), 1)).isEmpty())
    }

    @Test fun `forJob(날짜집합) - 떠돌이(days 빈 것)는 안 붙는다`() {
        val raw = setOf("7|01011110003|떠돌이|sh3|")
        assertTrue(CollabAssignmentLine.forJob(raw, 7, CollabAssignmentLine.daysOfJob(day(12), 2)).isEmpty())
    }
}
