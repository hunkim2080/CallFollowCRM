package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 🙈 **로그에 손님 번호를 안 남긴다** — 시험이 없었다. (2026-10-03)
 *
 *   2026-07-30 보안 감사에서 넣은 것. 릴리즈 빌드가 로그를 안 지우는 동안
 *   (`isMinifyEnabled = false`) **logcat·버그리포트로 손님 번호가 샌다.**
 *
 *   ⚠️ 뒤 4자리만 남긴다 — 사장님이 "아 그 손님" 하고 알아볼 만큼만.
 *      **앞자리(지역·통신사)가 남으면 번호를 좁힐 수 있다.**
 */
class LogRedactTest {

    @Test
    fun `뒤 네 자리만 남는다`() {
        assertEquals("***5678", LogRedact.phone("010-1234-5678"))
    }

    @Test
    fun `하이픈이나 공백이 있어도 같다`() {
        assertEquals("***5678", LogRedact.phone("01012345678"))
        assertEquals("***5678", LogRedact.phone("010 1234 5678"))
        assertEquals("***5678", LogRedact.phone("+82 10-1234-5678"))
    }

    @Test
    fun `앞자리는 한 글자도 안 남는다`() {
        val 가린것 = LogRedact.phone("010-3404-5247")
        assertFalse("앞자리가 남았다: $가린것", 가린것.contains("010"))
        assertFalse("가운데가 남았다: $가린것", 가린것.contains("3404"))
        assertEquals("***5247", 가린것)
    }

    @Test
    fun `짧은 번호는 통째로 가린다 — 114 같은 건 그 자체가 신원이다`() {
        assertEquals("***", LogRedact.phone("114"))
        assertEquals("***", LogRedact.phone("1-2"))
    }

    @Test
    fun `없는 값과 빈 값도 가린 모양으로 돌려준다`() {
        assertEquals("***", LogRedact.phone(null))
        assertEquals("***", LogRedact.phone(""))
        assertEquals("***", LogRedact.phone("이름만 적힘"))
    }

    @Test
    fun `네 자리면 그대로 네 자리가 남는다`() {
        assertEquals("***5678", LogRedact.phone("5678"))
    }

    @Test
    fun `가린 결과엔 별 세 개가 늘 앞에 있다`() {
        listOf("010-1234-5678", "070-0000-1111", "114", null, "").forEach {
            org.junit.Assert.assertTrue("「$it」 이 안 가려졌다", LogRedact.phone(it).startsWith("***"))
        }
    }
}
