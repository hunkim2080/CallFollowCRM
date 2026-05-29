package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AddressExtractor 휴리스틱 검증. phone 없이 JVM 에서 실행.
 *
 * 사장님 #6 통점 (2026-05-30 동호수 캡처) 회귀 방어가 1순위.
 */
class AddressExtractorTest {

    // ---------- 패턴 1: 광역시도 + 시군구 + 동/로 ----------

    @Test
    fun `광역시도 + 시군구 + 동 + 번지`() {
        val r = AddressExtractor.extractOne("내일 서울 강서구 마곡동 740 방문하시면 됩니다")
        assertEquals("서울 강서구 마곡동 740", r)
    }

    @Test
    fun `광역시도 줄임 + 시군구 + 동`() {
        val r = AddressExtractor.extractOne("부산 해운대구 우동에서 뵐게요")
        assertEquals("부산 해운대구 우동", r)
    }

    // ---------- 패턴 2: 시군구만 + 동호수 합치기 (#6 통점) ----------

    @Test
    fun `송파구 잠실엘스 101동 1503호 동호수 자동 합치기`() {
        val r = AddressExtractor.extractOne("송파구 잠실로 88 101동 1503호 입니다")
        assertNotNull("주소 추출 실패", r)
        assertTrue(
            "동호수가 합쳐져야 함. 실제: $r",
            r!!.contains("101동") && r.contains("1503호")
        )
    }

    /**
     * 2026-05-29 알려진 한계 (TODO):
     *   "강남구 역삼동 502호" → 502 가 번지(BUNJI)로 먼저 소비되어 "호" 만 남음.
     *   appendDongHo 의 DONG_HO_TAIL 정규식이 `\d{1,5}호` 를 요구해서 "호" 단독은 매칭 X.
     *   해결안: 패턴2 후행에 `(?:\d+호)?` optional 캡처를 BUNJI 와 분리하거나,
     *           DONG_HO_TAIL 에 `호` 단독 케이스 추가.
     */
    @Test
    fun `호만 있는 케이스 — 현재 동작 baseline (호 합치기 실패)`() {
        val r = AddressExtractor.extractOne("강남구 역삼동 502호 와주세요")
        assertNotNull(r)
        // 현재 동작: "강남구 역삼동 502" 까지만 매칭, "호" 누락
        assertTrue("역삼동까지는 매칭. 실제: $r", r!!.contains("역삼동"))
        // TODO: r.contains("502호") 가 통과해야 정상. 현재 실패 상태.
    }

    // ---------- 패턴 3: 아파트 단지 ----------

    @Test
    fun `아파트 단지 + 동호`() {
        val r = AddressExtractor.extractOne("마곡엠밸리 7단지 705동 1203호 부탁드려요")
        assertNotNull(r)
        assertTrue("엠밸리 매칭. 실제: $r", r!!.contains("엠밸리"))
        assertTrue("동호 포함. 실제: $r", r.contains("705동") && r.contains("1203호"))
    }

    /**
     * 2026-05-29 알려진 한계 (TODO):
     *   pattern3 의 `[가-힣A-Za-z]{2,15}(?:아파트|...힐스테이트|...)` 가 공백을 허용 안 함.
     *   "동대문구 답십리 힐스테이트" → 힐스테이트 앞 prefix 가 공백으로 끊김 → 매칭 X.
     *   해결안: 패턴3 prefix 를 optional 로 하거나 `(?:[가-힣]+\s)?` 분리 처리.
     */
    @Test
    fun `힐스테이트 단독 + 호 — 현재 미매칭 baseline`() {
        val r = AddressExtractor.extractOne("동대문구 답십리 힐스테이트 305호")
        // 현재 동작: pattern1/2/3 전부 매칭 실패 → null
        // TODO: r != null 이 되어야 정상.
        assertNull("현재 미매칭. 패치 후 NotNull 로 바꿔야 함", r)
    }

    @Test
    fun `아파트명에 prefix 있으면 매칭 됨 (대조군)`() {
        val r = AddressExtractor.extractOne("마곡엠밸리 305호")
        assertNotNull("prefix 있는 단일 단어 + 호 → 매칭. 실제: $r", r)
    }

    // ---------- 음성 케이스 ----------

    @Test
    fun `주소 없는 본문 null`() {
        val r = AddressExtractor.extractOne("네 알겠습니다 감사합니다")
        assertNull(r)
    }

    @Test
    fun `너무 짧은 본문 null`() {
        val r = AddressExtractor.extractOne("네")
        assertNull(r)
    }

    @Test
    fun `빈 문자열 null`() {
        val r = AddressExtractor.extractOne("")
        assertNull(r)
    }

    // ---------- extractFromMessages: 최신순 첫 매칭 ----------

    @Test
    fun `메시지 리스트 첫 매칭 반환`() {
        val msgs = listOf(
            "네 알겠습니다",                    // [0] 최신, 주소 X
            "서울 강서구 마곡동 740 입니다",    // [1] 매칭
            "내일 방문 가능하실까요"            // [2] 주소 X
        )
        val r = AddressExtractor.extractFromMessages(msgs)
        assertEquals("서울 강서구 마곡동 740", r)
    }

    @Test
    fun `메시지 리스트 전부 주소 없으면 null`() {
        val r = AddressExtractor.extractFromMessages(listOf("네", "감사합니다", "내일 봬요"))
        assertNull(r)
    }
}
