package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.PhoneKind
import com.detailline.callfollowcrm.util.PhoneKind.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ☎️ 번호 종류 — 전화가 왔을 때 빨갛게 세울지 말지를 가르는 셈이다.
 *   잘못 가르면 **진짜 손님 전화를 광고로 세운다.** 폰에서 못 보는 경우의 수를 여기서 본다.
 */
class PhoneKindTest {

    @Test fun `휴대폰은 평소대로`() {
        assertEquals(Kind.MOBILE, PhoneKind.of("010-2442-2809"))
        assertEquals(Kind.MOBILE, PhoneKind.of("01198765432"))
        assertEquals(Kind.MOBILE, PhoneKind.of("+82 10 2442 2809"))
    }

    @Test fun `070 050 0507 060 은 광고로 본다`() {
        assertEquals(Kind.AD, PhoneKind.of("070-5275-4139"))
        assertEquals(Kind.AD, PhoneKind.of("0505-123-4567"))
        assertEquals(Kind.AD, PhoneKind.of("0507-1234-5678"))
        assertEquals(Kind.AD, PhoneKind.of("060-700-1234"))
    }

    @Test fun `8자리 대표번호도 광고로 본다`() {
        assertEquals(Kind.AD, PhoneKind.of("1588-1588"))
        assertEquals(Kind.AD, PhoneKind.of("1644-0000"))
        assertEquals(Kind.AD, PhoneKind.of("1899-1234"))
    }

    @Test fun `지역번호는 집·사무실 전화 — 광고로 단정하지 않는다`() {
        assertEquals(Kind.LANDLINE, PhoneKind.of("02-1234-5678"))
        assertEquals(Kind.LANDLINE, PhoneKind.of("031-123-4567"))
        assertEquals(Kind.LANDLINE, PhoneKind.of("064-700-1234"))
    }

    /** 🔴 02 로 시작하는 **휴대폰은 없다.** 하지만 010 이 02 검사에 먼저 걸리면 안 된다. */
    @Test fun `010 이 지역번호로 새지 않는다`() {
        assertEquals(Kind.MOBILE, PhoneKind.of("010-0000-0000"))
        assertEquals(Kind.MOBILE, PhoneKind.of("0102345678"))
    }

    @Test fun `너무 짧거나 이상하면 모르겠다고 한다`() {
        assertEquals(Kind.UNKNOWN, PhoneKind.of("114"))
        assertEquals(Kind.UNKNOWN, PhoneKind.of(""))
        assertEquals(Kind.UNKNOWN, PhoneKind.of(null))
    }

    @Test fun `머리말과 이유는 광고·집전화에만 붙는다`() {
        assertEquals("광고 전화로 의심", PhoneKind.headline(Kind.AD))
        assertEquals("집·사무실 전화", PhoneKind.headline(Kind.LANDLINE))
        assertEquals(null, PhoneKind.headline(Kind.MOBILE))
        assertEquals(null, PhoneKind.headline(Kind.UNKNOWN))
        assertEquals("손님은 보통 휴대폰으로 걸어요", PhoneKind.reason(Kind.LANDLINE))
    }
}
