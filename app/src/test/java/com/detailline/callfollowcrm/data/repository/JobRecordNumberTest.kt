package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.entity.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 현장 번호 매기기 — **시공한 날짜 순**. (2026-09-24 사장님
 *   "근본적으로 고쳐야하는거야 완료된일인데 왜 이것만 안눌러져있냐 이거지")
 *
 * 사장님은 그날 앱을 못 열면 며칠 뒤에 [완료] 를 누르신다. 전처럼 '누른 순서' 로 번호를 박으면
 * 8월 현장이 9월 현장보다 뒤 번호를 받아 목록이 뒤죽박죽이 된다.
 * 그래서 여기 값들은 **사장님 9월 실제 현장**으로 맞춰 놨다.
 */
class JobRecordNumberTest {

    /** yyyy-MM-dd 를 ms 로 — 테스트 읽기 쉬우라고. */
    private fun d(month: Int, day: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.set(2026, month - 1, day, 9, 0, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun job(id: Long, work: Long?, done: Long?) =
        JobEntity(
            id = id, customerId = id, scheduledWorkDate = work, workCompletedAt = done,
            createdAt = 0L, updatedAt = 0L
        )

    @Test
    fun `완료한 건만 시공 날짜 순으로 1번부터`() {
        val jobs = listOf(
            job(3, d(9, 19), d(9, 19)),
            job(1, d(8, 24), d(8, 24)),
            job(2, d(9, 16), d(9, 16))
        )
        val no = recordNumbersByWorkDate(jobs)
        assertEquals(1, no[1])   // 8/24 서초
        assertEquals(2, no[2])   // 9/16 동탄
        assertEquals(3, no[3])   // 9/19 동탄
    }

    @Test
    fun `아직 완료 안 누른 건은 번호가 없다`() {
        val jobs = listOf(
            job(1, d(8, 24), d(8, 24)),
            job(2, d(9, 11), null),     // 덕양 — 완료를 안 눌렀다
            job(3, d(9, 19), d(9, 19))
        )
        val no = recordNumbersByWorkDate(jobs)
        assertEquals(1, no[1])
        assertNull(no[2])
        assertEquals(2, no[3])
    }

    /** 사장님 실제 상황 — 밀린 8/29 를 뒤늦게 누르면 그 자리에 끼어들어야 한다(끝에 붙으면 안 된다). */
    @Test
    fun `밀린 옛 건을 뒤늦게 완료하면 날짜 자리에 끼어든다`() {
        val before = listOf(
            job(1, d(8, 24), d(8, 24)),   // 서초
            job(2, d(8, 29), null),       // 성북 — 아직
            job(3, d(9, 15), d(9, 15))    // 강동
        )
        assertEquals(1, recordNumbersByWorkDate(before)[1])
        assertEquals(2, recordNumbersByWorkDate(before)[3])

        // 이제 성북을 누른다 → 성북이 2번, 강동이 3번으로 밀린다.
        val after = before.map { if (it.id == 2L) it.copy(workCompletedAt = d(9, 24)) else it }
        val no = recordNumbersByWorkDate(after)
        assertEquals(1, no[1])
        assertEquals(2, no[2])
        assertEquals(3, no[3])
    }

    @Test
    fun `완료를 되돌리면 번호를 떼고 뒤가 당겨진다`() {
        val jobs = listOf(
            job(1, d(8, 24), d(8, 24)),
            job(2, d(9, 15), null),       // 되돌린 건
            job(3, d(9, 19), d(9, 19))
        )
        val no = recordNumbersByWorkDate(jobs)
        assertNull(no[2])
        assertEquals(2, no[3])   // 빈 번호(2)를 남기지 않는다
    }

    @Test
    fun `같은 날 두 집이면 먼저 넣은 쪽이 앞 번호`() {
        val jobs = listOf(
            job(7, d(9, 16), d(9, 16)),
            job(4, d(9, 16), d(9, 16))
        )
        val no = recordNumbersByWorkDate(jobs)
        assertEquals(1, no[4])
        assertEquals(2, no[7])
    }

    /** 시공 날짜가 없는 옛 건 — 완료한 시각으로 줄 세운다(번호가 비면 안 된다). */
    @Test
    fun `시공 날짜가 없으면 완료 시각으로 줄 세운다`() {
        val jobs = listOf(
            job(1, null, d(8, 20)),
            job(2, d(9, 1), d(9, 1))
        )
        val no = recordNumbersByWorkDate(jobs)
        assertEquals(1, no[1])
        assertEquals(2, no[2])
    }

    @Test
    fun `완료한 게 하나도 없으면 전부 번호 없음`() {
        val jobs = listOf(job(1, d(9, 1), null), job(2, d(9, 2), null))
        val no = recordNumbersByWorkDate(jobs)
        assertNull(no[1])
        assertNull(no[2])
    }
}
