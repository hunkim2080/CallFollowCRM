package com.detailline.callfollowcrm.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 가격 온보딩(2026-07-02) 핵심 계약 검증: price 는 항상 '원 단위'.
 *   서버 스타터/문자추출이 원 단위(예 250000)로 주고, 앱은 ×10000 을 절대 두 번 하지 않는다.
 *   이 표시 함수가 원 단위 입력을 올바로 "N만원"/"N,NNN원" 으로 바꾸는지로 그 계약을 못박음.
 */
class PricingItemFormatTest {

    @Test
    fun `만원 배수는 만원 표기`() {
        assertEquals("25만원", PricingItemRepository.formatWon(250_000))
        assertEquals("40만원", PricingItemRepository.formatWon(400_000))
        assertEquals("150만원", PricingItemRepository.formatWon(1_500_000))
        assertEquals("5만원", PricingItemRepository.formatWon(50_000))
    }

    @Test
    fun `만원 배수 아니면 원 표기(콤마)`() {
        assertEquals("15,000원", PricingItemRepository.formatWon(15_000))
        assertEquals("5,000원", PricingItemRepository.formatWon(5_000))
        assertEquals("999원", PricingItemRepository.formatWon(999))
    }

    @Test
    fun `이중곱 방지 — 30만원 값은 300000원이어야 30만원으로 표시`() {
        // 만약 어딘가 ×10000 을 한 번 더 하면 30만원 입력이 3000000000 이 되어 "30만원"이 안 나옴.
        val thirtyManWon = 300_000L   // 정상: 30만원 = 300,000원
        assertEquals("30만원", PricingItemRepository.formatWon(thirtyManWon))
        // 이중곱된 값(버그)은 절대 "30만원"이 아님을 확인.
        val doubled = 30_0000_0000L   // 30 * 10000 * 10000 (버그 시나리오)
        assertEquals("300000만원", PricingItemRepository.formatWon(doubled))
    }
}
