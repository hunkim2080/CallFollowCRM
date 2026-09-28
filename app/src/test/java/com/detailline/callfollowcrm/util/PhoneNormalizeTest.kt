package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🔒 **저장할 땐 한 모양.** (2026-09-28 사장님)
 *
 *   "우리 앱에서는 번호가 두 갈래 세 갈래로 나뉘면 안 돼. 무조건 한 번호로 통일해야 흩어지지 않지."
 *
 * 전엔 들어온 글자 그대로 저장했다. 사람 눈엔 같은 번호인데 컴퓨터엔 다른 글자라,
 * `010-3404-5247` 과 `01034045247` 이 **손님 두 줄**로 갈라질 수 있었다.
 *
 * ⚠️ 이건 **저장하는 값**이다. 화면에 보이는 건 그대로 하이픈이 붙는다
 *    ([PhoneHyphenTest] 참고). 두 가지를 헷갈리면 안 된다.
 */
class PhoneNormalizeTest {

    private fun n(s: String?) = PhoneKey.normalize(s)

    @Test fun `같은 사람은 무조건 같은 글자가 된다`() {
        val want = "01034045247"
        assertEquals(want, n("01034045247"))
        assertEquals(want, n("010-3404-5247"))
        assertEquals(want, n("010 3404 5247"))
        assertEquals(want, n("+82 10-3404-5247"))
        assertEquals(want, n("+821034045247"))
        assertEquals(want, n("(010) 3404-5247"))
        assertEquals(want, n("  010-3404-5247  "))
    }

    @Test fun `서울 번호도 숫자만`() {
        assertEquals("0212345678", n("02-1234-5678"))
    }

    @Test fun `대표번호는 그대로`() {
        assertEquals("15881234", n("1588-1234"))
        assertEquals("114", n("114"))
    }

    @Test fun `82로 시작해도 휴대폰 모양이 아니면 안 건드린다`() {
        // 🔴 지어내면 **남의 번호**가 된다. 애매하면 손대지 않는다.
        //   (전엔 여덟 곳이 각자 떼다가 0012345678 · 012345678 · 82012345678 로 갈렸다)
        assertEquals("82012345678", n("82012345678"))
        assertEquals("8231234567", n("8231234567"))
    }

    @Test fun `플러스가 붙었으면 82를 뗀다`() {
        assertEquals("0212345678", n("+82 2-1234-5678"))
    }

    @Test fun `숫자가 없으면 원본을 지키지 않고 버리지 않는다`() {
        // 이름 칸에 번호 대신 글자가 들어오는 일이 있다 — 지우면 그 정보가 사라진다.
        assertEquals("번호없음", n("번호없음"))
        assertEquals("", n(""))
        assertEquals("", n(null))
        assertEquals("", n("   "))
    }

    @Test fun `이미 통일된 번호는 다시 통일해도 그대로`() {
        // 두 번 돌려도 안 바뀌어야 한다 — 저장할 때마다 값이 흔들리면 안 된다.
        val once = n("010-3404-5247")
        assertEquals(once, n(once))
        assertEquals(n("1588-1234"), n(n("1588-1234")))
        assertEquals(n("82012345678"), n(n("82012345678")))
    }

    @Test fun `열쇠는 그대로 맞는다`() {
        // 통일한 값이든 원본이든 [PhoneKey.of] 로 찾는 결과는 같아야 한다 —
        //   안 그러면 통일하는 순간 지난 기록을 못 찾는다.
        assertEquals(PhoneKey.of("010-3404-5247"), PhoneKey.of(n("010-3404-5247")))
        assertEquals(PhoneKey.of("+821034045247"), PhoneKey.of(n("+821034045247")))
    }
}
