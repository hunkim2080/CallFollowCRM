package com.detailline.callfollowcrm.domain.customer

import com.detailline.callfollowcrm.data.local.entity.IssuedDocEntity
import com.detailline.callfollowcrm.data.local.entity.JobEntity
import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 접수서를 차수(건)에 붙이는 규칙 검증 — 재설계 2단계. (2026-10-14 사장님: 접수서를 차수 안으로)
 * 사장님 지시: "버그 생길 지점 경우의 수를 미리 파악해 테스트".
 */
class IssuedDocAssignTest {

    private val base = 1_700_000_000_000L
    private val day1 = DateTimeUtils.startOfDay(base)                       // 1차
    private val day2 = DateTimeUtils.startOfDay(base + 20 * DateTimeUtils.DAY_MS)  // 2차
    private val day3 = DateTimeUtils.startOfDay(base + 40 * DateTimeUtils.DAY_MS)  // 3차
    private val otherDay = DateTimeUtils.startOfDay(base + 99 * DateTimeUtils.DAY_MS)

    private fun job(id: Long, date: Long?) = JobEntity(id = id, customerId = 1L, scheduledWorkDate = date, createdAt = 0L, updatedAt = 0L)
    private fun doc(id: Long, workDateMs: Long?) = IssuedDocEntity(
        id = id, phoneSuffix = "12345678", kind = "intake", docJson = "{}",
        workDateMs = workDateMs, issuedAtMs = base
    )

    @Test fun `C1 문서가 2차 날짜면 2차에만 붙는다`() {
        val docs = listOf(doc(10, day2))
        assertEquals(listOf(10L), IssuedDocAssign.forJobDay(docs, day2).map { it.id })
        assertTrue(IssuedDocAssign.forJobDay(docs, day1).isEmpty())
        assertTrue(IssuedDocAssign.forJobDay(docs, day3).isEmpty())
    }

    @Test fun `시공예정일은 0시 기준 — 시간이 붙어 있어도 같은 날이면 매칭`() {
        val docAtNoon = doc(11, day2 + 13 * 3_600_000L)   // 2차 당일 오후 1시
        assertEquals(listOf(11L), IssuedDocAssign.forJobDay(listOf(docAtNoon), day2).map { it.id })
    }

    @Test fun `C2 날짜 없는 문서는 미지정`() {
        val docs = listOf(doc(20, null))
        val jobDays = IssuedDocAssign.jobDaySet(listOf(job(1, day1), job(2, day2)))
        assertEquals(listOf(20L), IssuedDocAssign.unmatched(docs, jobDays).map { it.id })
        assertTrue(IssuedDocAssign.forJobDay(docs, day1).isEmpty())
    }

    @Test fun `workDateMs 0도 미지정으로 본다`() {
        val docs = listOf(doc(21, 0L))
        val jobDays = IssuedDocAssign.jobDaySet(listOf(job(1, day1)))
        assertEquals(listOf(21L), IssuedDocAssign.unmatched(docs, jobDays).map { it.id })
    }

    @Test fun `C3 어느 건과도 안 맞는 날짜면 미지정`() {
        val docs = listOf(doc(30, otherDay))
        val jobDays = IssuedDocAssign.jobDaySet(listOf(job(1, day1), job(2, day2)))
        assertEquals(listOf(30L), IssuedDocAssign.unmatched(docs, jobDays).map { it.id })
    }

    @Test fun `C4 같은 날 1차·2차면 그 날 문서는 그 날에 붙고 미지정이 아니다`() {
        // 사장님이 겪은 11_2 중복(1차·2차 같은 날). 문서는 그 날에 속하고(미지정 아님),
        // 화면은 한 번에 한 건만 펼치므로 동시 중복 노출은 없다.
        val jobs = listOf(job(1, day1), job(2, day1))   // 둘 다 같은 날
        val docs = listOf(doc(40, day1))
        val jobDays = IssuedDocAssign.jobDaySet(jobs)
        assertEquals("같은 날은 집합에서 하나로", 1, jobDays.size)
        assertEquals(listOf(40L), IssuedDocAssign.forJobDay(docs, day1).map { it.id })
        assertTrue("그 날에 붙으므로 미지정이 아니다", IssuedDocAssign.unmatched(docs, jobDays).isEmpty())
    }

    @Test fun `C5 건이 하나도 없으면 모든 문서가 미지정`() {
        val docs = listOf(doc(50, day1), doc(51, null))
        val jobDays = IssuedDocAssign.jobDaySet(emptyList())
        assertEquals(listOf(50L, 51L), IssuedDocAssign.unmatched(docs, jobDays).map { it.id })
    }

    @Test fun `섞인 경우 — 매칭과 미지정이 갈린다`() {
        val jobs = listOf(job(1, day1), job(2, day2))
        val docs = listOf(
            doc(1, day1),       // 1차
            doc(2, day2),       // 2차
            doc(3, null),       // 미지정(협의)
            doc(4, otherDay)    // 미지정(안 맞음)
        )
        val jobDays = IssuedDocAssign.jobDaySet(jobs)
        assertEquals(listOf(1L), IssuedDocAssign.forJobDay(docs, day1).map { it.id })
        assertEquals(listOf(2L), IssuedDocAssign.forJobDay(docs, day2).map { it.id })
        assertEquals(listOf(3L, 4L), IssuedDocAssign.unmatched(docs, jobDays).map { it.id })
    }

    @Test fun `날짜 없는 건은 jobDaySet·forJobDay 모두 안전`() {
        assertTrue(IssuedDocAssign.jobDaySet(listOf(job(1, null))).isEmpty())
        assertTrue(IssuedDocAssign.forJobDay(listOf(doc(1, day1)), null).isEmpty())
    }
}
