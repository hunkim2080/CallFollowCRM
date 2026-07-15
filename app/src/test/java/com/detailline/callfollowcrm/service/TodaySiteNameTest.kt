package com.detailline.callfollowcrm.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "오늘의 현장" 상시 알림에서 전화번호가 계속 뜨던 것 검증. (2026-07-15 사장님)
 *
 * 신고: "폰번호는 안 띄워도 될듯 … 그냥 주소만 딱 잘 보이면 좋겠어."
 *   1차로 c.phoneNumber 를 뺐는데도 알림엔 "…24 A동 1호 01054790582" 가 계속 떴다.
 *   원인: **이름 칸에 번호가 들어있는 고객** — 이름 없이 저장되면 번호가 이름 자리를 차지한다.
 *   → 이름이 '번호 모양'이면 그것도 뺀다. 단, 진짜 이름은 살려야 한다.
 */
class TodaySiteNameTest {

    @Test fun `번호만 있는 이름은 번호로 본다 - 알림에서 뺀다`() {
        assertTrue(ReminderWorker.looksLikePhone("01054790582"))
        assertTrue(ReminderWorker.looksLikePhone("010-5479-0582"))
        assertTrue(ReminderWorker.looksLikePhone("010 5479 0582"))
        assertTrue(ReminderWorker.looksLikePhone(" 01054790582 "))
        assertTrue(ReminderWorker.looksLikePhone("+82 10-5479-0582"))
        assertTrue(ReminderWorker.looksLikePhone("(02)123-4567"))
    }

    @Test fun `진짜 이름은 살린다`() {
        assertFalse(ReminderWorker.looksLikePhone("김철수"))
        assertFalse(ReminderWorker.looksLikePhone("하우스픽"))
        assertFalse(ReminderWorker.looksLikePhone("101동 김사장"))
        // 이름 + 번호가 섞였으면 이름으로 본다(사장님이 일부러 적어둔 것 — 지우면 정보 손실).
        assertFalse(ReminderWorker.looksLikePhone("김철수 010-5479-0582"))
    }

    @Test fun `빈 값이나 짧은 숫자는 번호가 아니다`() {
        assertFalse(ReminderWorker.looksLikePhone(""))
        assertFalse(ReminderWorker.looksLikePhone("   "))
        assertFalse(ReminderWorker.looksLikePhone("101"))      // 동·호수
        assertFalse(ReminderWorker.looksLikePhone("1-2"))
    }
}
