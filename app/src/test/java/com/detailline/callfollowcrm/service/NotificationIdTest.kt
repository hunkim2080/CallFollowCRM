package com.detailline.callfollowcrm.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 알림 번호(ID) 안 겹치기 — 기계가 지키는 규칙. (2026-09-15 사장님 "원인 찾기 어려운 버그 개선해줘")
 *
 * 왜 테스트까지 만드나:
 *   알림 번호가 겹치면 나중 알림이 앞 알림을 **조용히** 덮어쓴다 = "그 알림이 안 왔다"로만 보이고
 *   로그도 에러도 안 남는다. 사람 눈으로는 절대 못 잡는 종류라, 규칙을 코드가 아니라 테스트가 지키게 한다.
 *   새 알림 종류를 추가할 때 ID_FAMILIES 에 한 줄 안 넣으면 여기서 걸린다.
 */
class NotificationIdTest {

    /** 종류 번호는 서로 달라야 한다 — 같으면 두 종류가 같은 칸을 쓴다. */
    @Test
    fun `종류 번호는 중복이 없다`() {
        val fams = NotificationHelper.ID_FAMILIES
        val dup = fams.groupBy { it.second }.filter { it.value.size > 1 }
        assertTrue("같은 칸을 쓰는 종류: $dup", dup.isEmpty())
    }

    /** 어떤 키가 들어와도 그 종류의 칸(100만) 밖으로 못 나간다 — 음수 hash 포함. */
    @Test
    fun `어떤 hash 가 와도 자기 칸 안에 머문다`() {
        val band = NotificationHelper.ID_BAND
        val keys = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, -999_999, 1_000_000, 123_456_789) +
            List(500) { Random.nextInt() }
        for ((label, fam) in NotificationHelper.ID_FAMILIES) {
            val low = fam.toLong() * band
            for (k in keys) {
                val id = NotificationHelper.famId(fam, k)
                assertTrue("$label 칸 밖으로 나감: key=$k id=$id", id >= low && id < low + band)
                assertTrue("알림 번호는 양수여야 함: $label key=$k id=$id", id > 0)
            }
        }
    }

    /** 서로 다른 종류면 같은 키를 써도 번호가 절대 안 겹친다(이게 진짜 목적). */
    @Test
    fun `다른 종류끼리는 같은 키를 써도 안 겹친다`() {
        val keys = listOf(0, 1, -7, 42, Int.MIN_VALUE, Int.MAX_VALUE) + List(200) { Random.nextInt() }
        for (k in keys) {
            val ids = NotificationHelper.ID_FAMILIES.map { (label, fam) ->
                label to NotificationHelper.famId(fam, k)
            }
            val dup = ids.groupBy { it.second }.filter { it.value.size > 1 }
            assertTrue("key=$k 에서 번호 충돌: $dup", dup.isEmpty())
        }
    }

    /** 하나짜리 알림(브리핑·정기문자·오늘현장…)들도 서로 달라야 한다. */
    @Test
    fun `하나짜리 알림 번호들도 서로 다르다`() {
        val singles = listOf(
            "마감 브리핑" to NotificationHelper.BRIEF_ID,
            "정기문자" to NotificationHelper.RECUR_ID,
            "오늘의 현장" to NotificationHelper.TODAY_SITE_ID,
            "본폰 공유신청" to NotificationHelper.MIRROR_SHARE_ID,
            "MMS 발송실패" to NotificationHelper.MMS_FAIL_ID,
            "통화 후속 폴백" to NotificationHelper.FALLBACK_NOTIFICATION_ID,
            "통화요약 묶음" to NotificationHelper.CALL_SUMMARY_GROUP_ID,
        )
        val dup = singles.groupBy { it.second }.filter { it.value.size > 1 }
        assertTrue("번호가 같은 알림: $dup", dup.isEmpty())
        // 전부 '하나짜리' 칸 안에 있어야 한다 — 다른 종류 칸을 침범하면 안 됨.
        val low = NotificationHelper.FAM_SINGLE.toLong() * NotificationHelper.ID_BAND
        singles.forEach { (label, id) ->
            assertTrue("$label 가 칸 밖: $id", id >= low && id < low + NotificationHelper.ID_BAND)
        }
    }

    /** 같은 고객·같은 현장이면 같은 번호(= 알림 갱신) — 이건 의도된 동작이라 깨지면 안 된다. */
    @Test
    fun `같은 종류 같은 키면 같은 번호가 나온다`() {
        val fam = NotificationHelper.FAM_COLLAB_COMMENT
        assertEquals(
            NotificationHelper.famId(fam, "site-123".hashCode()),
            NotificationHelper.famId(fam, "site-123".hashCode())
        )
    }
}
