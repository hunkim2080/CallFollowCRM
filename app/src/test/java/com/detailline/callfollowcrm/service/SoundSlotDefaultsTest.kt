package com.detailline.callfollowcrm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알림음 기본값 검증 — **베타테스터가 앱을 처음 깔았을 때 나는 소리**.
 *
 * 사장님 지시(2026-07-15): "지금 내가 설정해둔 값을 기본값으로. 베타테스터가 다운받을 때 이 상태로."
 *   → 아래 표가 사장님 폰(S23U) 설정을 그대로 읽어 박은 값이다(NTFSND 로그 기준).
 *   기본값은 아무도 안 건드리면 조용히 틀어지기 쉬워서(오타·파일명 변경) 여기서 고정한다.
 *   소리를 새로 넣거나 기본값을 바꿀 땐 이 표도 같이 고칠 것.
 */
class SoundSlotDefaultsTest {

    /** 사장님이 고른 기본 소리 (slot key → raw 리소스명). */
    private val expected = mapOf(
        "new_inquiry" to "sound_new_inquiry_2",
        "reply" to "sound_reply_sabu",
        "auto_reply" to "sound_auto_reply",
        "intake" to "sound_intake_arrived",
        "reminder" to "sound_auto_reply",
        "install_d1" to "sound_install_d1",
        "daily_brief" to "sound_daily_brief",
        "recurring" to "sound_reminder",
        "call_summary" to "sound_call_summary_2",
        "collab_accepted" to "sound_collab_accepted",
        "collab_declined" to "sound_collab_declined_ppaenji",
        "collab_comment" to "sound_collab_comment",
        "collab_invite" to "sound_collab_invite",
        "collab_departed" to "sound_collab_departed",
        "collab_arrived" to "sound_collab_arrived",
        "collab_completed" to "sound_collab_completed",
        "collab_ended" to "sound_collab_ended",
        "collab_paid" to "sound_collab_paid",
    )

    @Test fun `기본값이 사장님이 설정한 소리와 정확히 같다`() {
        NotificationHelper.SOUND_SLOTS.forEach { slot ->
            val want = expected[slot.key]
            assertEquals("slot '${slot.key}' 의 기본 소리가 사장님 설정과 다름", want, slot.defaultRes)
        }
    }

    @Test fun `슬롯 목록과 기대표가 1대1 - 슬롯을 추가하면 기본값도 정해야 한다`() {
        assertEquals(expected.keys, NotificationHelper.SOUND_SLOTS.map { it.key }.toSet())
        // slot key 중복 금지 (prefs 키·채널 매핑이 겹치면 엉뚱한 소리가 난다)
        assertEquals(NotificationHelper.SOUND_SLOTS.size, NotificationHelper.SOUND_SLOTS.map { it.key }.toSet().size)
    }

    @Test fun `모든 기본 소리는 고를 수 있는 목록에 있다 - 오타 방지`() {
        // SOUND_OPTIONS 에 없는 이름이면 raw 에도 없을 가능성이 크고, 그러면 조용히 폴백돼 버린다.
        val options = NotificationHelper.SOUND_OPTIONS.map { it.first }.toSet()
        NotificationHelper.SOUND_SLOTS.forEach { slot ->
            assertTrue(
                "slot '${slot.key}' 의 기본 소리 '${slot.defaultRes}' 가 SOUND_OPTIONS 에 없음",
                slot.defaultRes in options
            )
        }
    }

    @Test fun `고를 수 있는 소리 목록에 중복이 없다`() {
        val names = NotificationHelper.SOUND_OPTIONS.map { it.first }
        assertEquals(names.size, names.toSet().size)
    }
}
