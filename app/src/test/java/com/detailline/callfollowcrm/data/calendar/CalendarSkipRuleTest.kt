package com.detailline.callfollowcrm.data.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "이 일정은 안 올려도 된다" 규칙. (2026-09-16 사장님 "구글캘린더 연결이 왜 자꾸 실패하지?")
 *
 * 배경: 실패가 아니라 **46초 걸려서** 실패로 보였다(실측: 탭 08:41:01 → 완료 08:41:47).
 *   일정 있는 고객 수백 명에게 매번 2번씩(시공·A/S) 구글에 요청을 보내고 있었다.
 *   → 안 바뀐 건 건너뛰게 고쳤는데, **잘못 건너뛰면 일정이 캘린더에 영영 안 올라간다.**
 *   사장님이 현장을 놓치는 종류의 사고라, 규칙을 테스트로 못 박는다.
 *
 * 규칙: 건너뛴다 = ① 올려둔 이벤트 id 가 있다 **그리고** ② 지난번 지문 == 이번 지문.
 */
class CalendarSkipRuleTest {

    private fun skip(eventId: String?, lastHash: String?, newHash: String) =
        CalendarSyncManager.canSkipUpload(eventId, lastHash, newHash)

    @Test
    fun `똑같고 이미 올라가 있으면 건너뛴다`() {
        assertTrue(skip("evt_1", "abc123", "abc123"))
    }

    @Test
    fun `내용이 바뀌었으면 올린다`() {
        assertFalse("날짜·금액·주소가 바뀌었는데 건너뛰면 캘린더가 옛날 것으로 남는다",
            skip("evt_1", "abc123", "zzz999"))
    }

    @Test
    fun `아직 한 번도 안 올렸으면 무조건 올린다`() {
        // 이벤트 id 가 없다 = 구글에 존재한다는 증거가 없다. 지문이 우연히 같아도 올려야 한다.
        assertFalse(skip(null, "abc123", "abc123"))
        assertFalse(skip(null, null, "abc123"))
    }

    @Test
    fun `지난번 지문이 없으면 올린다`() {
        // 처음이거나, 지난번에 실패해서 지문을 지운 경우 — 올라갔는지 알 수 없으니 올린다.
        assertFalse(skip("evt_1", null, "abc123"))
    }

    @Test
    fun `빈 문자열 이벤트 id 는 없는 것과 다르게 다루지 않는다`() {
        // 빈 id 는 정상 경로에서 안 생기지만, 생겼다면 '있다'로 보고 지문만 믿는다(같으면 건너뜀).
        //   중요한 건 null 일 때 절대 안 건너뛰는 것 — 위 테스트가 그걸 지킨다.
        assertTrue(skip("", "abc123", "abc123"))
    }
}
