package com.detailline.callfollowcrm.data.calendar

import com.detailline.callfollowcrm.data.local.entity.SimpleEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 간단 일정이 **언제 구글 캘린더로 올라가는가**. (2026-09-16 사장님)
 *
 * 시공 일정과 같은 함정을 공유한다: 지문에서 빠뜨린 값은 고쳐도 캘린더가 옛날 그대로 남고,
 * 쓸데없는 값을 넣으면 구글에 헛요청이 쌓인다. 눈으로는 안 보이는 사고라 테스트로 고정한다.
 */
class SimpleEventSyncTest {

    private fun e(
        id: Long = 1L,
        title: String = "자재 받는 날",
        day: Long = 1_760_000_000_000L,
        minutes: Int? = 540,
        memo: String = "케라폭시 20개",
        eventId: String? = null,
    ) = SimpleEventEntity(
        id = id, title = title, dayStartMs = day, minutes = minutes, memo = memo,
        calendarEventId = eventId, createdAt = 1L, updatedAt = 1L
    )

    private fun fp(vararg xs: SimpleEventEntity) =
        CalendarAutoSync.simpleFingerprint(xs.toList())

    @Test fun `제목이 바뀌면 다시 올린다`() =
        assertNotEquals(fp(e()), fp(e(title = "자재 반품")))

    @Test fun `날짜가 바뀌면 다시 올린다`() =
        assertNotEquals(fp(e()), fp(e(day = 1_760_086_400_000L)))

    @Test fun `시각이 바뀌면 다시 올린다`() =
        assertNotEquals(fp(e()), fp(e(minutes = 780)))

    @Test fun `하루 종일로 바꾸면 다시 올린다`() =
        assertNotEquals(fp(e()), fp(e(minutes = null)))

    @Test fun `메모가 바뀌면 다시 올린다`() =
        assertNotEquals(fp(e()), fp(e(memo = "케라폭시 30개")))

    @Test fun `일정이 늘면 다시 올린다`() =
        assertNotEquals(fp(e(id = 1L)), fp(e(id = 1L), e(id = 2L)))

    @Test
    fun `구글 이벤트 id 만 기록돼도 다시 올리지 않는다`() {
        // 올린 결과를 적어두는 것뿐 — 이게 지문을 흔들면 올릴 때마다 또 올리는 무한루프가 된다
        assertEquals(fp(e()), fp(e(eventId = "abc123")))
    }

    @Test
    fun `간단 일정이 하나도 없어도 지문은 안전하다`() {
        assertEquals(fp(), fp())
    }
}
