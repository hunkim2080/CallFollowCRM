package com.detailline.callfollowcrm.domain.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🏷️ **어느 건이 「대표」인가** — 고객 카드가 가리키는 그 하나. (2026-10-03)
 *
 *   여기서 틀리면 **돈이 어긋난다.** 실제로 두 번 났다:
 *     · 2026-09-17 — 일정만 옮기고 돈을 안 옮겨 **잘못된 미수 알람**
 *     · 2026-09-18 — 2차를 취소했는데 **1차 잔금 기록이 사라짐**
 *   둘 다 "지금 대표가 누구냐" 가 뒤틀린 결과다.
 *
 *   규칙 한 줄: **앞으로 올 가장 가까운 건. 다 지났으면 가장 마지막 건.**
 */
class RepresentativeJobTest {

    private data class J(val id: Long, override val scheduledWorkDate: Long?) :
        RepresentativeJob.Dated

    private val 오늘 = 1_000_000L
    private val 하루 = 86_400_000L

    @Test
    fun `건이 없으면 대표도 없다`() {
        assertNull(RepresentativeJob.pick(emptyList<J>(), 오늘))
    }

    @Test
    fun `하나뿐이면 그게 대표다`() {
        val j = J(1, 오늘 - 하루 * 30)
        assertEquals(j, RepresentativeJob.pick(listOf(j), 오늘))
    }

    @Test
    fun `앞으로 올 가장 가까운 건이 대표다`() {
        val 지난것 = J(1, 오늘 - 하루 * 10)
        val 다음것 = J(2, 오늘 + 하루 * 3)
        val 그다음 = J(3, 오늘 + 하루 * 20)
        assertEquals(다음것, RepresentativeJob.pick(listOf(지난것, 다음것, 그다음), 오늘))
    }

    @Test
    fun `오늘 시공도 앞으로 올 것으로 본다 — 오늘 일하는 중인데 지난 걸 가리키면 안 된다`() {
        val 지난것 = J(1, 오늘 - 하루 * 5)
        val 오늘것 = J(2, 오늘)
        assertEquals(오늘것, RepresentativeJob.pick(listOf(지난것, 오늘것), 오늘))
    }

    @Test
    fun `다 지났으면 가장 마지막 건이 대표다`() {
        val 첫째 = J(1, 오늘 - 하루 * 30)
        val 둘째 = J(2, 오늘 - 하루 * 10)
        // 🔴 여기서 첫째를 고르면 **1차 잔금이 되살아나** 정산이 어긋난다 (2026-09-18 사고)
        assertEquals(둘째, RepresentativeJob.pick(listOf(첫째, 둘째), 오늘))
    }

    @Test
    fun `날짜를 안 잡은 건은 앞으로 올 시공을 못 밀어낸다`() {
        val 날짜없음 = J(1, null)
        val 다음것 = J(2, 오늘 + 하루)
        assertEquals(다음것, RepresentativeJob.pick(listOf(날짜없음, 다음것), 오늘))
    }

    @Test
    fun `날짜를 안 잡은 건 하나뿐이면 그게 대표다`() {
        val 날짜없음 = J(1, null)
        assertEquals(날짜없음, RepresentativeJob.pick(listOf(날짜없음), 오늘))
    }

    @Test
    fun `지난 건들만 있고 마지막이 날짜 없음이면 그것이 대표다`() {
        // 차례대로 들어온다는 약속을 지킨 목록 — 마지막이 곧 '가장 최근'
        val 지난것 = J(1, 오늘 - 하루 * 3)
        val 날짜없음 = J(2, null)
        assertEquals(날짜없음, RepresentativeJob.pick(listOf(지난것, 날짜없음), 오늘))
    }

    @Test
    fun `어제와 내일 사이 — 하루 차이로 갈린다`() {
        val 어제 = J(1, 오늘 - 하루)
        val 내일 = J(2, 오늘 + 하루)
        assertEquals(내일, RepresentativeJob.pick(listOf(어제, 내일), 오늘))
        // 내일 것이 빠지면 어제 것으로 내려온다
        assertEquals(어제, RepresentativeJob.pick(listOf(어제), 오늘))
    }

    @Test
    fun `세 곳에서 같은 답이 나와야 한다 — 같은 목록 같은 오늘이면 늘 같은 건`() {
        val jobs = listOf(J(1, 오늘 - 하루 * 7), J(2, 오늘 + 하루 * 2), J(3, 오늘 + 하루 * 9))
        val a = RepresentativeJob.pick(jobs, 오늘)
        val b = RepresentativeJob.pick(jobs, 오늘)
        assertEquals(a, b)
        assertEquals(2L, a?.id)
    }
}
