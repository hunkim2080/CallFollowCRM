package com.detailline.callfollowcrm.recording

import com.detailline.callfollowcrm.data.repository.CallSummaryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통화요약 중복판정의 '시각 두 개' 검증 — 요약 알림 폭주 + 재과금의 원인. (2026-07-15 사장님/테스터 신고)
 *
 * 신고: "기존에 통화한 내용을 통화요약 성공~ 하는데요?" — 새 APK 를 깔면 옛 통화가 다시 요약됨(서버 LLM=돈).
 *
 * 원인: 저장 시각과 조회 시각의 정밀도가 달랐다.
 *   - 조회(스캐너) = **파일명** 시각 `yyyyMMddHHmmss` → 초까지 정확.
 *   - 저장(예전 importer) = **본문** 시각 "5. 15.(금) 오전 11:16" → 분 단위(초=0).
 *   → 최대 59초 어긋남 vs 중복창 ±20초(2026-06-18 ecbf653) → 못 찾음 → 재요약.
 *
 * 규칙: 파일명 시각이 저장의 진실 = 조회 키와 ±0초로 같아야 한다.
 */
class AdotRecordedAtSkewTest {

    /** 실제 에이닷 공유 텍스트 모양 (통화 11:16, 파일명은 11:16:37). */
    private val body = """
        위생도기 AS 비용 조정 요청 논의
        2026. 5. 15.(금) 오전 11:16
        010-8798-8685
        상세 요약
        AS 비용을 10만원 깎아주기로 함
    """.trimIndent()

    private val filename = "01087988685_20260515111637.txt"

    @Test fun `파일명 시각은 초까지 살아있다`() {
        val p = AdotFilenameParser.parse(filename)!!
        assertEquals("01087988685", p.phoneNumber)
        // 초(37)가 보존되므로 분 정각이 아니다.
        assertNotEquals(CallSummaryRepository.minuteFloor(p.recordedAt), p.recordedAt)
        assertEquals(37_000L, p.recordedAt - CallSummaryRepository.minuteFloor(p.recordedAt))
    }

    @Test fun `본문 시각은 분 단위라 초가 0 이다`() {
        val bodyAt = AdotShareTextParser.parse(body).recordedAt!!
        assertEquals(bodyAt, CallSummaryRepository.minuteFloor(bodyAt))
    }

    @Test fun `본문 시각과 파일명 시각은 중복창(20초)을 넘게 벌어진다 - 이게 재과금의 원인이었다`() {
        val fileAt = AdotFilenameParser.parse(filename)!!.recordedAt
        val bodyAt = AdotShareTextParser.parse(body).recordedAt!!
        val skew = Math.abs(fileAt - bodyAt)
        assertEquals(37_000L, skew)
        assertTrue("본문 시각으로 저장하면 ±20초 창을 벗어나 중복을 못 잡는다", skew > 20_000L)
        // 같은 분이므로 minuteFloor 로는 만난다 → 레거시 row 구제가 성립한다.
        assertEquals(bodyAt, CallSummaryRepository.minuteFloor(fileAt))
    }

    @Test fun `minuteFloor - 초·밀리초를 버린다`() {
        val at = AdotFilenameParser.parse("01012345678_20260515111637.m4a")!!.recordedAt
        val floored = CallSummaryRepository.minuteFloor(at)
        assertEquals(0L, floored % 60_000L)
        assertTrue(at - floored in 0 until 60_000L)
        // 이미 정각이면 그대로.
        assertEquals(floored, CallSummaryRepository.minuteFloor(floored))
    }

    @Test fun `초가 0~20 인 통화는 예전에도 우연히 중복이 잡혔다 - 그래서 일부만 폭주했다`() {
        val at = AdotFilenameParser.parse("01012345678_20260515111612.m4a")!!.recordedAt
        assertTrue(at - CallSummaryRepository.minuteFloor(at) <= 20_000L)
    }
}
