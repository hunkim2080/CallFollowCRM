package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 📦 택배 가르기. (2026-09-20 사장님 "택배 / 광고 이걸로 나눠주는게 좋겠다")
 * 제일 중요한 건 **광고를 택배로 잘못 넣지 않는 것** — 택배 칩은 깨끗해야 쓴다.
 */
class ParcelHeuristicsTest {

    @Test
    fun `택배사 번호면 낱말이 없어도 택배`() {
        assertTrue(ParcelHeuristics.isParcel("1588-1255", "[Web발신] 고객님 상품이 준비되었습니다"))
        assertTrue(ParcelHeuristics.isParcel("15882121", "안내드립니다"))
    }

    @Test
    fun `운송장 송장 집화는 택배`() {
        assertTrue(ParcelHeuristics.isParcel("1644-1234", "[Web발신] 운송장번호 123456789 등록되었습니다"))
        assertTrue(ParcelHeuristics.isParcel("1644-1234", "송장번호 안내드립니다"))
        assertTrue(ParcelHeuristics.isParcel("1644-1234", "집화가 완료되었습니다"))
    }

    @Test
    fun `배송 상태 알림은 택배`() {
        assertTrue(ParcelHeuristics.isParcel("1600-0000", "고객님의 상품이 배송출발 하였습니다"))
        assertTrue(ParcelHeuristics.isParcel("1600-0000", "오늘 도착예정입니다"))
        assertTrue(ParcelHeuristics.isParcel("1600-0000", "배송완료 되었습니다. 문앞에 두었습니다"))
    }

    /** 🔴 여기가 핵심 — 광고가 택배 칩에 들어가면 칩을 안 쓰게 된다. */
    @Test
    fun `무료배송 광고는 택배가 아니다`() {
        assertFalse(ParcelHeuristics.isParcel("1577-0000", "(광고) 오늘만 무료배송! 지금 주문하세요"))
        assertFalse(ParcelHeuristics.isParcel("1577-0000", "[Web발신] 전 상품 배송비 0원 이벤트"))
    }

    @Test
    fun `인증문자는 택배가 아니다`() {
        assertFalse(ParcelHeuristics.isParcel("15771577", "[Web발신] 인증번호 [428193] 를 입력해주세요"))
        assertFalse(ParcelHeuristics.isParcel("1644-0000", "[Web발신] 본인확인 인증번호 902133"))
    }

    @Test
    fun `카드 청구 안내는 택배가 아니다`() {
        assertFalse(ParcelHeuristics.isParcel("1588-0000", "[Web발신] 9월 청구금액 안내 452,300원"))
        assertFalse(ParcelHeuristics.isParcel("1800-2000", "[Web발신] 고객님 한도 조회 안내"))
    }

    @Test
    fun `빈 본문은 택배가 아니다`() {
        assertFalse(ParcelHeuristics.isParcel("1644-0000", ""))
    }
}
