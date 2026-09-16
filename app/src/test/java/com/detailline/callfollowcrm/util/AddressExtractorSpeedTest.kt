package com.detailline.callfollowcrm.util

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 주소 찾기 **속도** 안전장치. (2026-09-16 사장님 "챗스크린 들어가면 바로 팅겨버려")
 *
 * 왜 필요한가: 이 함수는 채팅 말풍선 **하나하나마다** 돌아간다(MessageEntities.detect → linkifyBody).
 *   대화 하나에 말풍선이 수백 개면 수백 번이다. 정규식이 조금만 느려도 화면이 멈추고,
 *   5초를 넘기면 안드로이드가 앱을 죽인다("응답 없음").
 *
 * 특히 위험한 모양: 중첩 반복 `(?:[가-힣]{1,15}\s+){0,2}` 같은 것 — 주소가 **없는** 긴 문장에서
 *   되돌아가기(backtracking)가 폭발할 수 있다. 그런 문장이 제일 흔하다(주소는 드물다).
 */
class AddressExtractorSpeedTest {

    /** 주소가 없는 긴 한국어 문장 — 가장 흔하고, 가장 느려질 수 있는 입력. */
    private fun longNoAddress(times: Int): String =
        ("안녕하세요 줄눈 시공 문의드립니다 화장실 2개랑 주방 싱크대 테두리도 같이 하고 싶은데요 " +
            "견적이 어떻게 될까요 혹시 이번주 토요일에 가능하실까요 총 얼마나 걸리나요 " +
            "시공하고 나서 몇 시간 뒤부터 쓸 수 있나요 비용은 계좌이체로 드리면 될까요 ").repeat(times)

    private fun elapsedMs(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000
    }

    @Test
    fun `주소 없는 긴 문장도 빨리 끝난다`() {
        val body = longNoAddress(10)   // 약 1,300자
        // 워밍업(JIT) 후 측정 — 첫 호출의 클래스 로딩까지 재면 실제보다 나쁘게 나온다
        AddressExtractor.findOne(body)
        val ms = elapsedMs { repeat(20) { AddressExtractor.findOne(body) } }
        assertTrue("긴 문장 20번에 ${ms}ms — 말풍선마다 도는 함수라 너무 느리다", ms < 500)
    }

    @Test
    fun `아주 긴 문장에서도 멈추지 않는다`() {
        val body = longNoAddress(60)   // 약 8,000자 (사진 설명 등 아주 긴 문자)
        val ms = elapsedMs { AddressExtractor.findOne(body) }
        assertTrue("8천자 한 번에 ${ms}ms — 되돌아가기 폭발 의심", ms < 300)
    }

    @Test
    fun `주소가 문장 끝에 있어도 빨리 찾는다`() {
        // 앞부분을 다 훑고 나서야 찾게 되는 최악 배치
        val body = longNoAddress(10) + " 서울 영등포구 국제금융로 39 브라이튼여의도 103동 1210호 입니다"
        AddressExtractor.findOne(body)
        val ms = elapsedMs { repeat(20) { AddressExtractor.findOne(body) } }
        assertTrue("끝에 주소 있는 긴 문장 20번에 ${ms}ms", ms < 500)
    }

    @Test
    fun `말풍선 200개 분량을 한 번에 훑어도 괜찮다`() {
        // 대화방 하나를 여는 상황과 비슷하게 — 화면 진입 시 이만큼이 한꺼번에 돈다
        val msgs = List(200) { i ->
            if (i % 20 == 0) "경기도 수원시 망포로 14 하늘채아파트 224동 1201호"
            else "네 알겠습니다 감사합니다 그럼 그때 뵙겠습니다 시공 잘 부탁드려요"
        }
        val ms = elapsedMs { msgs.forEach { AddressExtractor.findOne(it) } }
        assertTrue("말풍선 200개에 ${ms}ms — 화면 진입이 느려진다", ms < 300)
    }
}
