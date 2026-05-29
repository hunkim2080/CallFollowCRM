package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 전화번호 포맷 검증.
 *
 * 사장님이 자주 받는 번호 패턴:
 *  - 휴대폰 11자리 (01x-xxxx-xxxx) — 90%
 *  - 서울 국번 (02-xxxx-xxxx)
 *  - 그 외 지역 (031, 032, 033 등)
 *  - 8자리 단축 (지역번호 없음)
 */
class PhoneNumberFormatterTest {

    // ---------- format() — 완성된 번호 ----------

    @Test fun `휴대폰 11자리`() {
        assertEquals("010-1234-5678", PhoneNumberFormatter.format("01012345678"))
    }

    @Test fun `휴대폰 11자리 — 하이픈 이미 있어도 다시 포맷`() {
        assertEquals("010-1234-5678", PhoneNumberFormatter.format("010-1234-5678"))
    }

    @Test fun `서울 국번 10자리`() {
        assertEquals("02-1234-5678", PhoneNumberFormatter.format("0212345678"))
    }

    @Test fun `서울 국번 9자리`() {
        assertEquals("02-123-4567", PhoneNumberFormatter.format("021234567"))
    }

    @Test fun `경기 국번 031`() {
        assertEquals("031-123-4567", PhoneNumberFormatter.format("0311234567"))
    }

    @Test fun `070 인터넷전화`() {
        assertEquals("070-1234-5678", PhoneNumberFormatter.format("07012345678"))
    }

    @Test fun `8자리 단축`() {
        assertEquals("1234-5678", PhoneNumberFormatter.format("12345678"))
    }

    @Test fun `빈 문자열`() {
        assertEquals("", PhoneNumberFormatter.format(""))
    }

    @Test fun `숫자 없는 입력은 원본 그대로`() {
        assertEquals("ABC", PhoneNumberFormatter.format("ABC"))
    }

    @Test fun `알 수 없는 길이 — 원본 그대로`() {
        // 12자리 같은 미지 케이스는 안전하게 원본
        assertEquals("123456789012", PhoneNumberFormatter.format("123456789012"))
    }

    // ---------- formatProgressive() — 입력 도중 ----------

    @Test fun `진행 포맷 — 휴대폰 입력 도중`() {
        assertEquals("01", PhoneNumberFormatter.formatProgressive("01"))
        assertEquals("010", PhoneNumberFormatter.formatProgressive("010"))
        assertEquals("010-1", PhoneNumberFormatter.formatProgressive("0101"))
        assertEquals("010-1234", PhoneNumberFormatter.formatProgressive("0101234"))
        assertEquals("010-1234-5", PhoneNumberFormatter.formatProgressive("01012345"))
        assertEquals("010-1234-5678", PhoneNumberFormatter.formatProgressive("01012345678"))
    }

    @Test fun `진행 포맷 — 11자리 초과는 잘림`() {
        assertEquals("010-1234-5678", PhoneNumberFormatter.formatProgressive("010123456789"))
    }

    @Test fun `진행 포맷 — 서울 02`() {
        assertEquals("02", PhoneNumberFormatter.formatProgressive("02"))
        assertEquals("02-1", PhoneNumberFormatter.formatProgressive("021"))
        assertEquals("02-123", PhoneNumberFormatter.formatProgressive("02123"))
        assertEquals("02-123-4", PhoneNumberFormatter.formatProgressive("021234"))
        assertEquals("02-1234-5678", PhoneNumberFormatter.formatProgressive("0212345678"))
    }

    @Test fun `진행 포맷 — 빈 입력은 빈 문자열`() {
        assertEquals("", PhoneNumberFormatter.formatProgressive(""))
    }

    @Test fun `진행 포맷 — + 국제전화 보존`() {
        // 사장님이 +82 시작하는 번호 받을 때 (해외)
        assertEquals("+010-1234-5678", PhoneNumberFormatter.formatProgressive("+01012345678"))
    }

    @Test fun `진행 포맷 — 숫자 외 문자 제거`() {
        assertEquals("010-1234", PhoneNumberFormatter.formatProgressive("010-abc-1234"))
    }
}
