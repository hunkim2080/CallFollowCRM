package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🔨 「시공일이 지났는데 끝났나요?」를 **언제 묻는지**. (2026-09-28 사장님)
 *
 * 이 테스트가 왜 있나 — 만든 날 테스트폰엔 **지난 날짜의 안 끝난 현장이 하나도 없었다.**
 * 눈으로 볼 수가 없다. 그래서 규칙을 순수 함수로 빼서 **매 빌드가 대신 보게** 했다.
 */
class WorkDoneAskTest {

    private val day = DateTimeUtils.DAY_MS
    /** 오늘 0시. */
    private val today = DateTimeUtils.startOfDay(1_800_000_000_000L)

    private fun ask(
        scheduled: Long? = today - 3 * day,
        days: Int = 1,
        completed: Long? = null,
        balancePaid: Long? = null,
        asked: Set<Long> = emptySet()
    ) = WorkDoneAsk.lastDayToAsk(scheduled, days, completed, balancePaid, asked, today)

    @Test fun `사흘 전 시공인데 완료가 안 찍혔으면 묻는다`() {
        assertEquals(today - 3 * day, ask())
    }

    @Test fun `오늘 시공은 안 묻는다`() {
        // 오늘은 아직 일하는 중일 수 있다. 밤에 물으면 현장에서 폰을 보게 된다.
        assertNull(ask(scheduled = today))
    }

    @Test fun `앞으로 할 시공은 안 묻는다`() {
        assertNull(ask(scheduled = today + 2 * day))
    }

    @Test fun `여러 날 공사는 끝나는 날을 지나야 묻는다`() {
        // 어제 시작한 3일 공사 → 끝나는 날이 내일이라 아직 안 묻는다.
        assertNull(ask(scheduled = today - day, days = 3))
        // 나흘 전 시작한 3일 공사 → 끝나는 날이 그저께라 묻는다.
        assertEquals(today - 2 * day, ask(scheduled = today - 4 * day, days = 3))
    }

    @Test fun `이미 완료를 찍었으면 안 묻는다`() {
        assertNull(ask(completed = today))
    }

    @Test fun `잔금까지 받았으면 안 묻는다`() {
        // 돈을 다 받았는데 「끝났나요?」는 말이 안 된다.
        assertNull(ask(balancePaid = today))
    }

    @Test fun `아직이에요 한 날은 다시 안 묻는다`() {
        val d = today - 3 * day
        assertNull(ask(asked = setOf(d)))
    }

    @Test fun `날짜를 새로 잡으면 다시 묻는다`() {
        // 미뤄진 일도 언젠가 끝난다. 예전 날짜로 「아직이에요」 했다고 영영 안 물으면 안 된다.
        val old = today - 10 * day
        assertEquals(today - 3 * day, ask(asked = setOf(old)))
    }

    @Test fun `시공일이 없으면 안 묻는다`() {
        assertNull(ask(scheduled = null))
        assertNull(ask(scheduled = 0L))
    }

    @Test fun `시공일이 자정이 아니어도 그 날로 센다`() {
        // 오후 2시로 저장된 시공일도 「그 날」이다. 시·분 때문에 하루가 밀리면 안 된다.
        val afternoon = today - 3 * day + 14 * 60 * 60 * 1000L
        assertEquals(today - 3 * day, ask(scheduled = afternoon))
    }

    @Test fun `공사 일수가 0이나 음수여도 하루로 본다`() {
        assertEquals(today - 3 * day, ask(days = 0))
        assertEquals(today - 3 * day, ask(days = -5))
    }
}
