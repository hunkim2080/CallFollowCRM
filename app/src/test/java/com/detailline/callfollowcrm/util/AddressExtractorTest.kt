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

    // ---------- 패턴 2b: 시 + 동 + 단지명(띄어쓴 두 낱말) + 동호수 (2026-10-17 사장님 실기) ----------

    @Test
    fun `안양시 박달동 한라 비발디 105동 1704호 - 시까지 통째로, 밑줄도 시부터`() {
        val body = "입금했습니다\n안양시 박달동 한라 비발디 105동 1704호"
        val f = AddressExtractor.findOne(body)
        assertNotNull("주소 추출 실패", f)
        // 전엔 '박달동'부터 잡혀 '안양시'가 링크 밖이었다 — 그 앞을 누르면 아무 일도 안 났다.
        assertEquals("안양시 박달동 한라 비발디 105동 1704호", f!!.text)
        assertEquals(body.indexOf("안양시"), f.start)
        assertEquals(body.length, f.end)
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
    fun `호만 있는 케이스 — 2026-09-16 해결됨`() {
        val r = AddressExtractor.extractOne("강남구 역삼동 502호 와주세요")
        assertNotNull(r)
        // 2026-09-16 해결: 번지 뒤에 바로 '호' 가 붙으면 이어붙인다 → "강남구 역삼동 502호"
        assertTrue("역삼동까지는 매칭. 실제: $r", r!!.contains("역삼동"))
        assertTrue("'호' 가 잘리면 안 된다. 실제: $r", r.contains("502호"))
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
    fun `힐스테이트 단독 + 호 — 2026-09-16 해결됨`() {
        // 예전엔 pattern3 이 브랜드 **앞말이 붙어 있어야** 매칭돼서(={2,15}) 통째로 놓쳤다.
        //   앞말을 optional({0,15})로 바꿔 해결. 넉 달간 TODO 로만 적혀 있던 것.
        val r = AddressExtractor.extractOne("동대문구 답십리 힐스테이트 305호")
        assertNotNull("이제 잡혀야 한다", r)
        assertTrue("힐스테이트와 호수가 들어가야 함. 실제: $r",
            r!!.contains("힐스테이트") && r.contains("305호"))
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

    // ---------- roughSite: "대충 어디" 짧은 표시 (사장님 2026-06-23) ----------

    @Test fun `풀주소 - 시 + 아파트명만 남김`() {
        // 사장님 실제 케이스
        assertEquals(
            "수원 대동아파트",
            AddressExtractor.roughSite("경기 수원시 장안구 대평로39번길 8 (꽃뫼노을마을 대동아파트)")
        )
    }

    @Test fun `광역시는 구 + 아파트명`() {
        assertEquals("강남 래미안아파트", AddressExtractor.roughSite("서울 강남구 역삼동 래미안아파트"))
    }

    @Test fun `광역시 아파트 없으면 구만`() {
        assertEquals("송파", AddressExtractor.roughSite("서울특별시 송파구 잠실동"))
    }

    @Test fun `아파트명만 있으면 그것만`() {
        assertEquals("대동아파트", AddressExtractor.roughSite("꽃뫼 대동아파트"))
    }

    @Test fun `주소 없으면 빈 문자열`() {
        assertEquals("", AddressExtractor.roughSite(null))
        assertEquals("", AddressExtractor.roughSite(""))
    }

    @Test fun `시구 건물 다 없으면 siteLabel 로 fallback`() {
        // 동만 있는 케이스 — 빈 것보단 동네라도.
        assertTrue(AddressExtractor.roughSite("천호동 어딘가").contains("천호동"))
    }

    // ---------- 패턴 J: 시/구 없는 순수 지번 "동+번지" (2026-10-12 사장님 학의동) ----------

    @Test fun `동 + 하이픈 번지 - 시구 없이도 잡는다`() {
        val r = AddressExtractor.extractOne("학의동 632-32 시공범위보내드립니다. 목요일에 입금드릴게요")
        assertEquals("학의동 632-32", r)
    }

    @Test fun `동 + 세자리 번지 - 시구 없이도 잡는다`() {
        assertEquals("영천동 720", AddressExtractor.extractOne("영천동 720 입니다"))
    }

    @Test fun `읍면리 + 하이픈 번지`() {
        assertEquals("오산리 15-3", AddressExtractor.extractOne("오산리 15-3 현장이에요"))
    }

    @Test fun `오탐 방지 - 동으로 끝나는 낱말 + 작은 숫자는 주소 아님`() {
        assertNull(AddressExtractor.extractOne("노동 5시간 했어요"))
        assertNull(AddressExtractor.extractOne("운동 30분 하고 왔어요"))
    }

    @Test fun `오탐 방지 - 숫자동은 동호수지 지번 아님`() {
        // "3동 101호" 를 지번으로 오해해 "동 101" 같은 걸 뱉으면 안 된다.
        val r = AddressExtractor.extractOne("3동 101호로 와주세요")
        assertTrue(r == null || r!!.contains("호"))
    }

    // ---------- twoLine: 협업 「업체 내역」 줄 — 1줄 단지명 / 2줄 도로명 · 동호수 (2026-10-17 사장님 프로토) ----------

    @Test fun `twoLine - 도로명, 단지명 동호수 (쉼표) → 단지명 위, 도로명 · 동호수 아래`() {
        val (a, b) = AddressExtractor.twoLine("인천 연수구 컨벤시아대로 42번길 7, 더샵 센트럴시티 101동 1203호")
        assertEquals("더샵 센트럴시티", a)
        assertEquals("인천 연수구 컨벤시아대로 42번길 7 · 101동 1203호", b)
    }

    @Test fun `twoLine - 아파트 없는 도로명은 (전체, null) 로 내려간다`() {
        val (a, b) = AddressExtractor.twoLine("서울 서대문구 응암로 28")
        assertEquals("서울 서대문구 응암로 28", a)
        assertNull(b)
    }

    @Test fun `twoLine - 시 동 띄어 쓴 단지명 동호수 (쉼표 없음)`() {
        val (a, b) = AddressExtractor.twoLine("안양시 박달동 한라 비발디 105동 1704호")
        assertEquals("한라 비발디", a)
        assertEquals("안양시 박달동 · 105동 1704호", b)
    }

    @Test fun `twoLine - 구 동 붙여 쓴 단지명 동호수`() {
        val (a, b) = AddressExtractor.twoLine("강동구 천호동 래미안강동팰리스 101동 1502호")
        assertEquals("래미안강동팰리스", a)
        assertEquals("강동구 천호동 · 101동 1502호", b)
    }

    @Test fun `twoLine - 괄호 단지명은 괄호를 벗긴다`() {
        val (a, b) = AddressExtractor.twoLine("경기 수원시 장안구 대평로39번길 8 (꽃뫼노을마을 대동아파트)")
        assertEquals("꽃뫼노을마을 대동아파트", a)
        assertEquals("경기 수원시 장안구 대평로39번길 8", b)
    }

    @Test fun `twoLine - 단지명만 있고 지역 없으면 (단지명, 동호수)`() {
        val (a, b) = AddressExtractor.twoLine("마곡엠밸리 7단지 705동 1203호")
        assertEquals("마곡엠밸리 7단지", a)
        assertEquals("705동 1203호", b)
    }

    @Test fun `twoLine - 단지명 없는 동호수는 통째로 1줄`() {
        val (a, b) = AddressExtractor.twoLine("강남구 역삼동 502호")
        assertEquals("강남구 역삼동 502호", a)
        assertNull(b)
    }

    @Test fun `twoLine - 빈 값·문장 꼬리는 안 깨진다`() {
        assertEquals("" to null, AddressExtractor.twoLine(null))
        assertEquals("" to null, AddressExtractor.twoLine("   "))
        val (a, b) = AddressExtractor.twoLine("경기 시흥시 가마길 2 입니다")
        assertEquals("경기 시흥시 가마길 2", a)
        assertNull(b)
    }

    @Test fun `twoLine - 2줄에 빈 「·」 가 절대 안 나온다`() {
        for (raw in listOf("힐스테이트 1502호", "인천 연수구, 더샵 센트럴시티", "송파구 잠실엘스 101동 1503호", "학의동 632-32")) {
            val (a, b) = AddressExtractor.twoLine(raw)
            assertTrue("1줄 비면 안 됨: $raw", a.isNotBlank())
            if (b != null) {
                assertTrue("빈 조각: $raw → $b", !b.startsWith("·") && !b.endsWith("·") && !b.contains("·  ·"))
            }
        }
    }
}
