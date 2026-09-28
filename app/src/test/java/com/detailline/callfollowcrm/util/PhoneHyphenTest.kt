package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 📞 **번호에 하이픈 붙이는 법은 하나다.** (2026-09-28)
 *
 * 사장님: "전화번호에 하이픈이 있는 게 가독성이 좋은데... 프로그래밍적으로 좋아서 빼고 제안하는 건가?"
 *
 * 그 물음에 코드를 확인해보니 **하이픈 넣는 코드가 네 군데**서 각자 돌고 있었고, 답이 갈렸다:
 *   0212345678 → 알림 `021-234-5678`(틀림) / 합치기 `0212345678`(안 붙음) / 사업자칸 `02-1234-5678`(맞음)
 *   15881234   → 사업자칸 `158-8-1234`(틀림)
 *
 * 이제 전부 [PhoneNumberFormatter] 하나를 쓴다. 이 테스트가 그걸 지킨다.
 * (하이픈을 없앤 게 아니다 — **보이는 건 그대로 하이픈**이고, 만드는 곳만 하나가 됐다.)
 */
class PhoneHyphenTest {

    // ── 다 적은 번호 (알림·목록·합치기 미리보기) ──────────────────
    @Test fun `휴대폰 11자리`() {
        assertEquals("010-3404-5247", PhoneNumberFormatter.format("01034045247"))
    }

    @Test fun `서울 02 번호는 02로 끊는다`() {
        // 🔴 알림이 여기서 `021-234-5678` 이라고 적고 있었다.
        assertEquals("02-1234-5678", PhoneNumberFormatter.format("0212345678"))
        assertEquals("02-123-4567", PhoneNumberFormatter.format("021234567"))
    }

    @Test fun `경기 031 같은 지역번호`() {
        assertEquals("031-123-4567", PhoneNumberFormatter.format("0311234567"))
    }

    @Test fun `1588 대표번호는 4-4`() {
        assertEquals("1588-1234", PhoneNumberFormatter.format("15881234"))
    }

    @Test fun `이미 하이픈이 있어도 다시 맞춘다`() {
        assertEquals("010-3404-5247", PhoneNumberFormatter.format("010-3404-5247"))
        assertEquals("02-1234-5678", PhoneNumberFormatter.format("02 1234 5678"))
    }

    @Test fun `모르는 모양은 건드리지 않는다`() {
        // 억지로 끊는 것보다 원본이 낫다 — 사장님이 적어둔 그대로 보인다.
        assertEquals("1234567", PhoneNumberFormatter.format("1234567"))
        assertEquals("", PhoneNumberFormatter.format(""))
    }

    // ── 입력 도중 (사업자 정보·일정 추가 등 입력칸) ───────────────
    @Test fun `치는 동안 휴대폰`() {
        assertEquals("010", PhoneNumberFormatter.formatProgressive("010"))
        assertEquals("010-3404", PhoneNumberFormatter.formatProgressive("0103404"))
        assertEquals("010-3404-5247", PhoneNumberFormatter.formatProgressive("01034045247"))
    }

    @Test fun `치는 동안 서울 02`() {
        assertEquals("02-123", PhoneNumberFormatter.formatProgressive("02123"))
        assertEquals("02-1234-5678", PhoneNumberFormatter.formatProgressive("0212345678"))
    }

    @Test fun `치는 동안 1588 대표번호`() {
        // 🔴 전엔 `158-8-1234`(사업자칸) 또는 `158-812-34`(공용) 로 쪼갰다.
        assertEquals("1588", PhoneNumberFormatter.formatProgressive("1588"))
        assertEquals("1588-1234", PhoneNumberFormatter.formatProgressive("15881234"))
        assertEquals("1577-11", PhoneNumberFormatter.formatProgressive("157711"))
    }

    @Test fun `011 구형 휴대폰은 대표번호로 안 본다`() {
        // 다 친 번호는 3-3-4 로 정확히 끊는다.
        assertEquals("011-123-4567", PhoneNumberFormatter.format("0111234567"))
        // 치는 도중엔 010 을 치는 중인지 011 을 다 친 건지 **알 수가 없다** —
        //   그래서 휴대폰 모양(3-4-4)으로 둔다. 중요한 건 1588 처럼 4-4 로 안 쪼갠다는 것.
        //   다 치고 저장하면 위 format 이 제대로 끊는다.
        assertEquals("011-1234-567", PhoneNumberFormatter.formatProgressive("0111234567"))
    }

    @Test fun `국제전화 표시는 지키고 뒤만 다듬는다`() {
        assertEquals("+010-3404-5247", PhoneNumberFormatter.formatProgressive("+01034045247"))
    }

    @Test fun `빈 칸은 빈 칸`() {
        assertEquals("", PhoneNumberFormatter.formatProgressive(""))
    }
}
