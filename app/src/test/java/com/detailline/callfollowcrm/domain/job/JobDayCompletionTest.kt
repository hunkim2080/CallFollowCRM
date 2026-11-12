package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobDayCompletionTest {
    private val day = 24L * 60 * 60 * 1000
    private val d1 = DateTimeUtils.startOfDay(System.currentTimeMillis())
    private val d2 = DateTimeUtils.startOfDay(d1 + day)

    @Test fun `2일 건 - 1일차만 완료하면 전체완료 아님`() {
        val csv = JobDayCompletion.withDayToggled("", d1, d1, 2)
        assertTrue(JobDayCompletion.isDayDone(csv, d1))
        assertFalse(JobDayCompletion.isDayDone(csv, d2))
        assertFalse(JobDayCompletion.allDone(csv, d1, 2))
        assertEquals(1, JobDayCompletion.doneCount(csv, d1, 2))
    }

    @Test fun `2일 건 - 둘 다 완료하면 전체완료`() {
        var csv = JobDayCompletion.withDayToggled("", d1, d1, 2)
        csv = JobDayCompletion.withDayToggled(csv, d2, d1, 2)
        assertTrue(JobDayCompletion.allDone(csv, d1, 2))
        assertEquals(2, JobDayCompletion.doneCount(csv, d1, 2))
    }

    @Test fun `토글로 완료 해제된다`() {
        var csv = JobDayCompletion.withDayToggled("", d1, d1, 2)
        csv = JobDayCompletion.withDayToggled(csv, d1, d1, 2)
        assertFalse(JobDayCompletion.isDayDone(csv, d1))
        assertEquals("", csv)
    }

    @Test fun `건 범위 밖 날짜는 완료로 안 들어간다`() {
        val csv = JobDayCompletion.withDayToggled("", d1 + 10 * day, d1, 2)
        assertEquals("", csv)
    }

    @Test fun `1일 건 - 그 하루 완료하면 전체완료`() {
        val csv = JobDayCompletion.withDayToggled("", d1, d1, 1)
        assertTrue(JobDayCompletion.allDone(csv, d1, 1))
    }

    @Test fun `일정 없는 건 - 전체완료 아님`() {
        assertFalse(JobDayCompletion.allDone("", null, 2))
        assertTrue(JobDayCompletion.dayStarts(null, 2).isEmpty())
    }

    @Test fun `allDaysCsv 는 전체완료가 된다`() {
        val csv = JobDayCompletion.allDaysCsv(d1, 3)
        assertTrue(JobDayCompletion.allDone(csv, d1, 3))
        assertEquals(3, JobDayCompletion.doneCount(csv, d1, 3))
    }
}
