package com.detailline.callfollowcrm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 고객이 **도로명**으로 줬나 **지번**으로 줬나. (2026-09-16 사장님)
 *
 *   "고객이 지번으로 주면 지번으로 하고 도로명으로 주면 도로명으로 받아오고…
 *    2개 주소 다 받아오고 그냥 회색글씨로 참고용으로 보여주는 방법도 좋겠네."
 *
 * 왜 중요한가: 등록되는 주소가 사장님이 **늘 보던 모양**이어야 헷갈리지 않는다.
 *   고객이 "동탄대로24길 199" 라고 줬는데 "영천동 720" 으로 저장되면
 *   같은 곳인데 다른 주소처럼 보인다(실제로 그렇게 나와서 사장님이 지적).
 *
 * ⚠️ 판별을 잘못하면 매번 엉뚱한 형태로 저장된다 — 눈으로는 알아채기 어려워 테스트로 고정한다.
 */
class AddressFormTest {

    private fun road(s: String?) = AddressExtractor.looksLikeRoadAddress(s)

    @Test
    fun `로 길 뒤에 번호가 오면 도로명`() {
        assertTrue(road("화성시 동탄구 동탄대로24길 199 475동 901호"))
        assertTrue(road("경기 시흥시 은행로 108 벽산3차 304동 1105호"))
        assertTrue(road("서울 강남구 테헤란로 152"))
        assertTrue(road("경기 수원시 장안구 대평로39번길 8"))
        assertTrue(road("송파대로345 헬리오시티 418동201호"))
    }

    @Test
    fun `동 읍 면 뒤에 번지가 오면 지번`() {
        assertFalse(road("서울 강서구 마곡동 740"))
        assertFalse(road("서울특별시 강서구 화곡동 398-3 천우진영빌라 302호"))
        assertFalse(road("의왕시 학의동 632-32"))
        assertFalse(road("강서구 등촌동 660-1 이마트 뒤편"))
        assertFalse(road("저희 집 주소 보내드릴게요 서울시 관악구 봉천동 1690-5"))
    }

    @Test
    fun `동호수의 숫자동을 지번으로 오해하지 않는다`() {
        // "101동 1502호" 의 101동은 **건물 동**이지 행정동+번지가 아니다
        assertTrue("동호수만 있는 건 도로명 기본이어야 함", road("천호동 래미안 101동 1502호"))
    }

    @Test
    fun `아파트명만 있으면 도로명을 기본으로`() {
        // 내비가 도로명을 더 잘 찾는다
        assertTrue(road("힐스테이트 1502호"))
        assertTrue(road("동탄역대방디엠시티 101동 3702호"))
    }

    @Test
    fun `빈 값도 안전하다`() {
        assertTrue(road(null))
        assertTrue(road("   "))
    }
}
