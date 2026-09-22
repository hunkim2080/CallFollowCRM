package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 주소 끝에 **낱자만 남은 꼬리**를 떼어낸다. (2026-09-22 사장님)
 *
 *   폰에서 이렇게 떴다: "경기 수원시 권선구 망포로 14 (수원 하늘채 더퍼스트2단지) 101동 2905호ㄴㄴ"
 *   고객이 문자 치다 잘못 누른 "ㄴㄴ" 이 주소에 그대로 실려 다녔다.
 *
 * ⚠️ 여기서 떼는 건 **낱자(ㄱ~ㅎ, ㅏ~ㅣ)** 뿐이다. 완성된 글자(가~힣)는 절대 안 건드린다 —
 *   "…2905호" 의 '호' 를 떼면 주소가 망가진다. [tidyAddress] 는 앱 전체가 쓰는 함수라
 *   한 글자만 잘못 떼도 일정·협업·길찾기가 같이 틀어진다. 그래서 테스트로 고정한다.
 */
class AddressTidyTailTest {

    @Test
    fun `잘못 친 낱자 꼬리는 떼어낸다`() {
        assertEquals(
            "경기 수원시 권선구 망포로 14 (수원 하늘채 더퍼스트2단지) 101동 2905호",
            AddressExtractor.tidyAddress("경기 수원시 권선구 망포로 14 (수원 하늘채 더퍼스트2단지) 101동 2905호ㄴㄴ")
        )
        assertEquals("서울 강동구 고덕로 12", AddressExtractor.tidyAddress("서울 강동구 고덕로 12ㅁ"))
        assertEquals("동탄순환대로20길 104", AddressExtractor.tidyAddress("동탄순환대로20길 104ㅋㅋㅋ"))
        assertEquals("영천동 720", AddressExtractor.tidyAddress("영천동 720ㅜㅜ"))
    }

    @Test
    fun `멀쩡한 주소는 한 글자도 안 건드린다`() {
        val ok = listOf(
            "경기 화성시 동탄구 동탄순환대로20길 104",
            "서울 강동구 천호동 래미안강동팰리스 101동 1502호",
            "경기 이천시 아리역로76번길 14 202동 1502호",
            "부산 해운대구 센텀중앙로 97 A동 3005호",
            "인천 연수구 송도과학로 32 지하 1층"
        )
        ok.forEach { assertEquals(it, AddressExtractor.tidyAddress(it)) }
    }

    @Test
    fun `빈 값과 낱자뿐인 값도 안전하다`() {
        assertEquals("", AddressExtractor.tidyAddress(null))
        assertEquals("", AddressExtractor.tidyAddress(""))
        assertEquals("", AddressExtractor.tidyAddress("   "))
        // 낱자만 있으면 남는 게 없다 — 호출부가 "주소 미입력" 으로 받는다.
        assertEquals("", AddressExtractor.tidyAddress("ㅋㅋㅋ"))
    }

    @Test
    fun `기존 정리 규칙과 같이 동작한다`() {
        // 선행 안내어 + 말미 종결 어미 + 낱자 꼬리가 한꺼번에 있어도 다 떨어진다.
        assertEquals("강동구 천호동 12", AddressExtractor.tidyAddress("주소는 강동구 천호동 12 입니다"))
        assertEquals("강동구 천호동 12", AddressExtractor.tidyAddress("현장: 강동구 천호동 12ㅇ"))
    }
}
