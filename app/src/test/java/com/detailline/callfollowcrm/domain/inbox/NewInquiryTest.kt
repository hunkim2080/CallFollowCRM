package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 💬 「신규 문의」 판정 — 세 수신 경로(SMS·MMS 훅·MMS 폴링)가 같은 답을 쓰게 한 곳.
 * 핵심: **대화 중인(전에 메시지 왕래가 있던) 번호는 미저장이어도 신규가 아니다.**
 *   (2026-11-06 사장님: 010-7226-2352 와 통화+문자 왕래 중인데 마지막 사진에서 「신규」음이 울림)
 */
class NewInquiryTest {

    private val t = 1_730_000_000_000L   // 이 메시지 시각 기준

    @Test fun `저장 고객이면 늘 신규 아님`() {
        assertFalse(NewInquiry.isNew(customerExists = true, cachedFirstMs = null, thisMs = t))
        assertFalse(NewInquiry.isNew(customerExists = true, cachedFirstMs = t - 10_000, thisMs = t))
    }

    @Test fun `캐시에 아예 없는 번호 = 처음 = 신규`() {
        assertTrue(NewInquiry.isNew(customerExists = false, cachedFirstMs = null, thisMs = t))
    }

    @Test fun `이 메시지가 가장 오래된 것이면(폴링이 방금 upsert) 신규`() {
        // 진짜 신규 사진: syncMmsContacts 가 먼저 넣어 캐시 firstDate == 이 메시지 → 그래도 신규.
        assertTrue(NewInquiry.isNew(customerExists = false, cachedFirstMs = t, thisMs = t))
    }

    @Test fun `전에 더 오래된 메시지가 있으면(대화 중) 신규 아님`() {
        // ← 버그 케이스: 통화+문자 왕래로 캐시 firstDate 가 5분 전. 미저장이어도 신규 아님.
        assertFalse(NewInquiry.isNew(customerExists = false, cachedFirstMs = t - 5 * 60_000, thisMs = t))
    }

    @Test fun `캐시 첫 시각이 이 메시지보다 미래라도(시계 밀림) 신규로 본다`() {
        assertTrue(NewInquiry.isNew(customerExists = false, cachedFirstMs = t + 1_000, thisMs = t))
    }
}
