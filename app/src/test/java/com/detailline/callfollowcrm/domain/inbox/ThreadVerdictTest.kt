package com.detailline.callfollowcrm.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버 응답 → 판정 만들기. 빠진 칸·이상한 값은 전부 상담함(consult)으로. */
class ThreadVerdictTest {

    @Test fun `general 0_95 는 문자함`() {
        val v = ThreadVerdict.of("general", 0.95, "택배 안내")
        assertEquals(ThreadVerdict.BUCKET_GENERAL, v.bucket)
        assertTrue(v.isGeneral)
        assertEquals("택배 안내", v.reason)
    }

    @Test fun `consult 는 상담함`() {
        val v = ThreadVerdict.of("consult", 0.99, "시공 문의")
        assertEquals(ThreadVerdict.BUCKET_CONSULT, v.bucket)
        assertFalse(v.isGeneral)
    }

    @Test fun `general 인데 확신 0_9 미만이면 문자함 아님 — 서버 _classify_safe_result 와 같은 문턱`() {
        assertFalse(ThreadVerdict.of("general", 0.89, "").isGeneral)
        assertTrue(ThreadVerdict.of("general", 0.9, "").isGeneral)
    }

    @Test fun `bucket 칸이 없으면 consult`() {
        val v = ThreadVerdict.of(null, 0.99, null)
        assertEquals(ThreadVerdict.BUCKET_CONSULT, v.bucket)
        assertFalse(v.isGeneral)
        assertEquals("", v.reason)
    }

    @Test fun `confidence 칸이 없거나 NaN 이면 0`() {
        assertEquals(0.0, ThreadVerdict.of("general", null, "x").confidence, 0.0)
        assertEquals(0.0, ThreadVerdict.of("general", Double.NaN, "x").confidence, 0.0)
        assertFalse(ThreadVerdict.of("general", null, "x").isGeneral)
    }

    @Test fun `엉뚱한 bucket 글자 · 범위 밖 confidence 도 안전하게`() {
        assertEquals(ThreadVerdict.BUCKET_CONSULT, ThreadVerdict.of("spam", 1.0, "").bucket)
        assertEquals(ThreadVerdict.BUCKET_GENERAL, ThreadVerdict.of(" GENERAL ", 1.7, "").bucket)
        assertEquals(1.0, ThreadVerdict.of("general", 1.7, "").confidence, 0.0)
        assertEquals(0.0, ThreadVerdict.of("general", -3.0, "").confidence, 0.0)
    }

    @Test fun `reason 은 60자에서 자른다`() {
        val long = "가".repeat(100)
        assertEquals(60, ThreadVerdict.of("general", 0.95, long).reason.length)
    }

    @Test fun `안전값은 상담함`() {
        assertFalse(ThreadVerdict.CONSULT_FALLBACK.isGeneral)
    }
}
