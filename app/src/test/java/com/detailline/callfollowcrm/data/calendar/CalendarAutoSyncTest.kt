package com.detailline.callfollowcrm.data.calendar

import com.detailline.callfollowcrm.data.local.entity.CustomerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 캘린더 자동 동기화가 **언제 도는가**. (2026-09-16 사장님)
 *   "고객메모가 바뀌던지, 캘린더에 들어갈 내용에 수정이 있으면 캘린더도 자동으로 동기화되어야할듯"
 *
 * 지문이 바뀌면 = 올린다 / 그대로면 = 안 올린다.
 *   · 빠뜨리면(지문에 없는 값) → 고쳐도 캘린더가 옛날 그대로 남는다
 *   · 너무 넣으면(무관한 값) → 문자 하나 올 때마다 구글에 헛요청을 보낸다
 * 둘 다 눈으로는 안 보이는 사고라 테스트로 고정한다.
 */
class CalendarAutoSyncTest {

    private fun c(
        id: Long = 1L,
        workDate: Long? = 1_760_000_000_000L,
        minutes: Int? = 540,
        days: Int = 1,
        asDate: Long? = null,
        address: String? = "서울 강서구 마곡동 740",
        name: String? = "김사장",
        memo: String = "현관 비번 1234",
        total: Long? = 1_000_000L,
        deposit: Long? = 200_000L,
        balancePaidAt: Long? = null,
        completedAt: Long? = null,
        leadHeat: String? = null,
    ) = CustomerEntity(
        id = id,
        phoneNumber = "0101111$id",
        name = name,
        memo = memo,
        address = address,
        scheduledWorkDate = workDate,
        scheduledWorkMinutes = minutes,
        scheduledWorkDays = days,
        asScheduledDate = asDate,
        totalAmount = total,
        depositAmount = deposit,
        balancePaidAt = balancePaidAt,
        workCompletedAt = completedAt,
        leadHeat = leadHeat,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun fp(vararg cs: CustomerEntity) =
        CalendarAutoSync.calendarFingerprint(cs.toList())

    // ── 바뀌면 올라가야 하는 것들 ────────────────────────────────

    @Test fun `시공일이 바뀌면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(workDate = 1_760_086_400_000L)))

    @Test fun `시공 시각이 바뀌면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(minutes = 780)))

    @Test fun `시공 기간이 바뀌면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(days = 3)))

    @Test fun `고객 메모가 바뀌면 지문이 바뀐다`() =
        // 사장님이 직접 짚은 케이스 — 메모는 캘린더 본문에 들어간다
        assertNotEquals(fp(c()), fp(c(memo = "현관 비번 5678")))

    @Test fun `주소가 바뀌면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(address = "서울 송파구 잠실동 19")))

    @Test fun `금액이 바뀌면 지문이 바뀐다`() =
        // 제목의 [125] 가 금액에서 나온다
        assertNotEquals(fp(c()), fp(c(total = 2_500_000L)))

    @Test fun `잔금을 받으면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(balancePaidAt = 1_760_000_000_000L)))

    @Test fun `AS 일정이 생기면 지문이 바뀐다`() =
        assertNotEquals(fp(c()), fp(c(asDate = 1_761_000_000_000L)))

    @Test fun `일정이 사라져도 지문이 바뀐다`() =
        // 삭제도 캘린더에 반영돼야 한다(이벤트 지우기)
        assertNotEquals(fp(c()), fp(c(workDate = null)))

    // ── 바뀌어도 올라가면 안 되는 것들 ──────────────────────────

    @Test
    fun `캘린더와 무관한 값만 바뀌면 지문은 그대로다`() {
        // 리드 온도 같은 건 캘린더에 안 들어간다. 여기서 돌면 구글에 헛요청만 쌓인다.
        assertEquals(fp(c()), fp(c(leadHeat = "hot")))
    }

    @Test
    fun `일정도 올려둔 이벤트도 없는 고객은 아예 무시한다`() {
        val noSchedule = c(id = 2L, workDate = null, asDate = null, memo = "메모만 바꿈")
        val noSchedule2 = c(id = 2L, workDate = null, asDate = null, memo = "메모 또 바꿈")
        assertEquals("캘린더와 무관한 고객이 지문을 흔들면 안 된다", fp(noSchedule), fp(noSchedule2))
    }

    @Test
    fun `같은 내용이면 순서만 같아도 지문이 같다`() {
        assertEquals(fp(c(id = 1L), c(id = 2L)), fp(c(id = 1L), c(id = 2L)))
    }

    @Test
    fun `고객이 늘면 지문이 바뀐다`() =
        assertNotEquals(fp(c(id = 1L)), fp(c(id = 1L), c(id = 2L)))

    // ── 건(件) 지문 — 취소했을 때 동기화가 **깨어나는지** ─────────────────────────
    //   2026-09-18 실기: 건을 취소했는데 구글 캘린더에 일정이 그대로 남았다.
    //   지우는 코드는 고쳤지만, **지문이 안 바뀌면 동기화 자체가 안 돌아** 영영 안 지워진다.
    //   그래서 "취소 = 지문이 바뀐다" 를 여기서 못 박는다.

    private fun j(
        id: Long = 10L,
        day: Long? = 1_760_000_000_000L,
        eventId: String? = "ev-1"
    ) = com.detailline.callfollowcrm.data.local.entity.JobEntity(
        id = id,
        customerId = 1L,
        scheduledWorkDate = day,
        scheduledWorkDays = 1,
        calendarEventId = eventId,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun jfp(vararg js: com.detailline.callfollowcrm.data.local.entity.JobEntity) =
        CalendarAutoSync.jobFingerprint(js.toList())

    @Test
    fun `건을 취소하면(날짜만 비움) 지문이 바뀐다 - 그래야 구글에서 지우러 간다`() {
        val 예약됨 = j(day = 1_760_000_000_000L, eventId = "ev-1")
        val 취소됨 = j(day = null, eventId = "ev-1")   // 취소해도 일정 번호는 남아 있다
        assertNotEquals("취소가 지문을 안 흔들면 동기화가 안 돌아 일정이 영영 남는다", jfp(예약됨), jfp(취소됨))
    }

    @Test
    fun `날짜도 일정번호도 없는 건은 지문에서 무시한다`() {
        val 빈건 = j(id = 99L, day = null, eventId = null)
        assertEquals("올린 적도 없는 건이 지문을 흔들면 헛요청만 쌓인다", jfp(j()), jfp(j(), 빈건))
    }

    @Test
    fun `건 날짜를 옮기면 지문이 바뀐다`() =
        assertNotEquals(jfp(j(day = 1_760_000_000_000L)), jfp(j(day = 1_760_604_800_000L)))
}
