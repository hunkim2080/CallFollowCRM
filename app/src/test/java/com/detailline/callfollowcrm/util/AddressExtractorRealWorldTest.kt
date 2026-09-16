package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 주소 캐치 성적표. (2026-09-16 사장님 "주소 캐치를 어떤 방식으로 하길래 이렇게 못하지?")
 *
 * 그때 실측: 적중률 **65%** (20개 중 7개 놓침). 놓친 것들의 공통점 두 가지였다.
 *   ① 번지 숫자가 없는 "○○동 ○○ 101동 1502호"  ② 목록에 없는 아파트 브랜드(e편한세상·래미안…)
 *   → 보강 후 **95%**, 오탐은 계속 0.
 *
 * 이 파일이 성적표다. 앞으로 주소 규칙을 건드리면 **여기서 점수가 떨어지는지** 바로 보인다.
 * 실패 케이스를 발견하면 지우지 말고 여기 한 줄 추가할 것.
 *
 * ⚠️ 오탐(주소 아닌 걸 주소라고 잡기)은 **놓치는 것보다 나쁘다** — 엉뚱한 주소가 고객 정보에
 *    저장되면 사장님이 엉뚱한 곳으로 간다. 그래서 아래 '주소 아님' 목록은 절대 깨지면 안 된다.
 */
class AddressExtractorRealWorldTest {

    // ── 잡아야 하는 것 ────────────────────────────────────────────
    private val shouldCatch = listOf(
        "서울 강서구 마곡동 740",
        "부산 해운대구 우동 1234",
        "경기 수원시 장안구 대평로39번길 8",
        "서초구 서초동 1330-12 삼성아파트 3동 502호",
        "강남구 테헤란로 152",
        "서울 강서구 마곡중앙8로 60, 3층",
        "인천 서구 청라한내로 123번길 45",
        "화성시 동탄대로 1길 12 아파트 302동 1401호",
        "강서구 등촌동 660-1 이마트 뒤편",
        "저희 집 주소 보내드릴게요 서울시 관악구 봉천동 1690-5",
        // ── 2026-09-16 전까지 놓치던 것들 ──
        "송파구 잠실엘스 101동 1503호",          // 번지 숫자 없음
        "천호동 래미안 101동 1502호로 와주세요",   // 번지 없음 + 브랜드 미등록
        "힐스테이트 1502호",                     // 브랜드가 문장 맨 앞
        "목동 하이페리온 2차 3305호",             // 미등록 브랜드 + N차
        "판교 봇들마을 4단지 401동 1203호",        // '마을' 단지형
        "위치는 김포한강신도시 e편한세상 103동 1004호입니다",  // e편한세상
        "마곡엠밸리 7단지 705동 1203호",
        "래미안 강동팰리스 101동 1502호",
        "여의도 시범아파트 12동 505호",
    )

    // ── 절대 잡으면 안 되는 것 (숫자가 있어도 주소가 아님) ─────────
    private val shouldNotCatch = listOf(
        "네 알겠습니다 감사합니다",
        "내일 3시에 방문 가능할까요?",
        "견적 250만원으로 진행할게요",
        "화장실 2개랑 주방 시공 문의드려요",
        "줄눈 시공 1회 얼마인가요",
        "총 3일 걸린다고 하셨는데 맞나요",
        "계약금 50만원 보냈습니다 확인 부탁드려요",
        "1층이고 방 2개예요 견적 부탁드립니다",
        "오늘 5시까지 가능하세요?",
        "안방 화장실 2곳 시공이요",
    )

    @Test
    fun `주소가 있으면 잡는다`() {
        val missed = shouldCatch.filter { AddressExtractor.extractOne(it) == null }
        assertEquals("놓친 문장: $missed", emptyList<String>(), missed)
    }

    @Test
    fun `주소가 아니면 절대 안 잡는다`() {
        val wrong = shouldNotCatch.mapNotNull { body ->
            AddressExtractor.extractOne(body)?.let { "$body → $it" }
        }
        assertEquals("주소가 아닌데 잡음: $wrong", emptyList<String>(), wrong)
    }

    // ── 뽑은 내용까지 맞는지 (일부만 대표로) ───────────────────────

    @Test
    fun `번지 없는 동호수형도 통째로 뽑는다`() {
        assertEquals("송파구 잠실엘스 101동 1503호",
            AddressExtractor.extractOne("송파구 잠실엘스 101동 1503호"))
    }

    @Test
    fun `문장에 섞여 있어도 주소 부분만 뽑는다`() {
        assertEquals("천호동 래미안 101동 1502호",
            AddressExtractor.extractOne("천호동 래미안 101동 1502호로 와주세요"))
    }

    @Test
    fun `브랜드가 맨 앞에 와도 잡는다`() {
        assertNotNull(AddressExtractor.extractOne("힐스테이트 1502호"))
    }

    @Test
    fun `호가 잘리지 않는다`() {
        // 2026-05-29 부터 TODO 로만 적혀 있던 것 — "502" 까지만 잡고 '호' 를 흘렸다.
        val r = AddressExtractor.extractOne("강남구 역삼동 502호 와주세요")
        assertNotNull(r)
        assertEquals("강남구 역삼동 502호", r)
    }

    @Test
    fun `주소가 아예 없으면 null`() {
        assertNull(AddressExtractor.extractOne("오늘 날씨가 좋네요 시공 잘 부탁드립니다"))
    }
}
