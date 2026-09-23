package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 요약 한 줄 읽기 — 새 모양(시간 구간)과 옛 모양이 **같이** 들어온다. (2026-09-24 사장님) */
class CallSummaryLinesTest {

    @Test
    fun `새 모양 - 시간 구간과 화자를 떼어낸다`() {
        val l = CallSummaryLines.parseOne("0:00-0:35|손님|욕조가 깨졌는데 고칠 수 있냐고 물어봄")!!
        assertEquals("0:00-0:35", l.time)
        assertEquals("손님", l.speaker)
        assertEquals("욕조가 깨졌는데 고칠 수 있냐고 물어봄", l.text)
    }

    @Test
    fun `옛 모양 - 머리말을 떼고 화자로 옮긴다`() {
        val a = CallSummaryLines.parseOne("고객: 24평 화장실 줄눈 견적 문의")!!
        assertEquals("", a.time); assertEquals("손님", a.speaker)
        assertEquals("24평 화장실 줄눈 견적 문의", a.text)

        val b = CallSummaryLines.parseOne("사장님 답: 65만원 안내")!!
        assertEquals("나", b.speaker); assertEquals("65만원 안내", b.text)
    }

    @Test
    fun `머리말도 시각도 없으면 문장 그대로`() {
        val l = CallSummaryLines.parseOne("견적 65만원 / 첫입주 시기 확인 필요")!!
        assertEquals("", l.time); assertEquals("", l.speaker)
        assertEquals("견적 65만원 / 첫입주 시기 확인 필요", l.text)
    }

    @Test
    fun `시각 칸이 비었거나 이상하면 안 믿는다`() {
        assertEquals("", CallSummaryLines.parseOne("|손님|세입자가 산다고")!!.time)
        assertEquals("", CallSummaryLines.parseOne("나중에|손님|세입자가 산다고")!!.time)
        assertEquals("", CallSummaryLines.parseOne("0:00-0:35|아무개|문장")!!.speaker)
    }

    @Test
    fun `시작 밀리초 - 눌러서 그 대목부터 듣기`() {
        val l = CallSummaryLines.parseOne("2:05-2:47|나|깨진 데만 15~20만원")!!
        assertEquals(125_000L, CallSummaryLines.startMsOf(l))
        assertEquals(0L, CallSummaryLines.startMsOf(CallSummaryLines.parseOne("0:00-0:35|손님|첫 줄")!!))
        assertNull(CallSummaryLines.startMsOf(CallSummaryLines.parseOne("고객: 옛 요약")!!))
    }

    @Test
    fun `덩어리를 줄로 - 빈 줄은 버린다`() {
        val rows = CallSummaryLines.parse("0:00-0:35|손님|첫 줄\n\n0:35-1:20|나|둘째 줄")
        assertEquals(2, rows.size)
        assertEquals("나", rows[1].speaker)
    }

    @Test
    fun `좁은 자리용 첫 문장 - 머리말과 시각이 안 보인다`() {
        assertEquals("욕조가 깨졌는데 고칠 수 있냐고",
            CallSummaryLines.firstSentence("0:00-0:35|손님|욕조가 깨졌는데 고칠 수 있냐고\n0:35-1:20|나|설명"))
        assertEquals("24평 견적 문의",
            CallSummaryLines.firstSentence("고객: 24평 견적 문의\n사장님 답: 65만원"))
        assertNull(CallSummaryLines.firstSentence("   "))
    }
}
