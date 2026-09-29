package com.detailline.callfollowcrm.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔗 **묻지 않고 합쳐도 되는 쌍인가.** (2026-09-30 사장님)
 *
 *   "바로 합치고 다시는 이런 일 벌어지지 않도록 해. 굳이 왜 내가 눌러야 해?"
 *
 * 자동으로 합치는 건 **되돌릴 수 없다.** 그래서 조건은 하나뿐이다 —
 *   **번호를 한 모양으로 바꾸면 글자까지 똑같아지는가.**
 *
 * 지금 「같은 사람」 기준인 **끝 8자리**로 자동으로 합치면 남을 붙인다:
 *   `02-1234-5678` 과 `010-1234-5678` 은 끝 8자리가 같지만 **남남**이다.
 *
 * 이 테스트가 그 선을 지킨다. 여기가 뚫리면 손님 둘이 한 줄이 되고 되돌릴 수 없다.
 */
class SureMergeRuleTest {

    /** 앱이 쓰는 규칙과 **같은 셈** — 통일한 글자가 전부 같으면 확실한 쌍. */
    private fun isSure(vararg phones: String): Boolean {
        val shapes = phones.map { PhoneKey.normalize(it) }
        return shapes.toSet().size == 1 && shapes.first().isNotBlank()
    }

    // ── 합쳐도 되는 것 ─────────────────────────────────────────────
    @Test fun `하이픈만 다르면 같은 사람`() {
        assertTrue(isSure("010-3404-5247", "01034045247"))
    }

    @Test fun `국가번호만 다르면 같은 사람`() {
        assertTrue(isSure("+821034045247", "01034045247"))
        assertTrue(isSure("+82 10-3404-5247", "010-3404-5247"))
    }

    @Test fun `공백 괄호만 다르면 같은 사람`() {
        assertTrue(isSure("(010) 3404-5247", "010 3404 5247", "01034045247"))
    }

    @Test fun `서울 유선번호도 모양만 다르면 같은 사람`() {
        assertTrue(isSure("02-1234-5678", "0212345678"))
    }

    // ── 🔴 절대 합치면 안 되는 것 ──────────────────────────────────
    @Test fun `서울 유선과 휴대폰은 남남 - 끝 8자리가 같아도`() {
        // 🔴 이게 뚫리면 남의 기록이 한 줄로 합쳐진다. 되돌릴 수 없다.
        assertFalse(isSure("02-1234-5678", "010-1234-5678"))
    }

    @Test fun `경기 유선과 휴대폰도 남남`() {
        assertFalse(isSure("031-234-5678", "010-1234-5678"))
    }

    @Test fun `인터넷전화와 휴대폰도 남남`() {
        assertFalse(isSure("070-1234-5678", "010-1234-5678"))
    }

    @Test fun `한 자리만 달라도 남남`() {
        assertFalse(isSure("010-3404-5247", "010-3404-5248"))
    }

    @Test fun `앞자리만 다른 구형 번호도 남남`() {
        assertFalse(isSure("011-3404-5247", "010-3404-5247"))
    }

    // ── 애매한 것은 안 합친다 ──────────────────────────────────────
    @Test fun `번호가 비면 안 합친다`() {
        assertFalse(isSure("", ""))
        assertFalse(isSure("010-3404-5247", ""))
    }

    @Test fun `숫자가 없는 글자는 안 합친다`() {
        // 이름 칸에 글자가 들어온 경우. 「번호없음」끼리 묶으면 남남이 합쳐진다.
        assertFalse(isSure("번호없음", "번호모름"))
    }

    @Test fun `82로 시작하는 애매한 번호는 손대지 않으니 안 합쳐진다`() {
        // normalize 가 일부러 안 건드리는 모양 — 그래서 통일해도 글자가 다르다.
        assertFalse(isSure("82012345678", "01012345678"))
    }

    @Test fun `세 줄이 갈라져도 셋 다 같으면 합친다`() {
        assertTrue(isSure("010-3404-5247", "01034045247", "+821034045247"))
    }

    @Test fun `세 줄 중 하나라도 다르면 안 합친다`() {
        assertFalse(isSure("010-3404-5247", "01034045247", "02-3404-5247"))
    }
}
