package com.detailline.callfollowcrm

import com.detailline.callfollowcrm.util.CallSummaryLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통화 요약의 **시간 구간이 화면까지 살아서 가는지** 지킨다.
 *
 * 왜 이 테스트가 생겼나 (2026-09-25, 같은 걸 두 번 놓쳤다):
 *   ① 서버는 `0:00-0:46|나|…` 를 제대로 줬는데 **채팅 통화카드가** `.map { it.text }` 로
 *      시각·화자를 버리고 문장만 그렸다. (통화요약 화면은 멀쩡했다 — 그리는 코드가 두 벌)
 *   ② 그리는 코드를 한 군데(CallSummaryBody)로 합친 뒤에도, **거기에 깎은 글을 넘겨서**
 *      또 시각이 안 나왔다. 그릴 재료를 깎아서 준 것이다.
 *
 * 그래서 눈으로 못 보는 것(폰에 요약된 통화가 없을 때)도 **여기서 걸리게** 묶어둔다.
 */
class CallSummaryRawLinesTest {

    /** 서버가 준 그대로 — 한 줄 요약 + 시간 구간 4줄. */
    private val 서버가준요약 = listOf(
        "업무폰 충전 확인 및 식사 관련 안부 통화",
        "0:00-0:46|나|컴퓨터에 꽂혀 있는 업무용 핸드폰의 위치를 물어봄",
        "0:46-1:25|손님|거치대에 있는 핸드폰을 확인하고 전원이 켜지는지 체크함",
        "1:25-1:43|나|켜지지 않는 핸드폰을 충전기에 꽂아달라고 요청함",
        "1:43-2:20|손님|식사 여부를 물어보며 곧 들어가겠다고 이야기함"
    ).joinToString("\n")

    @Test
    fun `원본 줄은 시각과 화자를 그대로 달고 나온다`() {
        val lines = CallSummaryLines.rawLines(서버가준요약)
        assertEquals(5, lines.size)
        // 화면에 그릴 때 넘기는 것 = 이것. 파이프가 살아 있어야 한다.
        assertEquals(4, lines.count { it.count { c -> c == '|' } >= 2 })
    }

    @Test
    fun `그 원본 줄을 다시 읽으면 시각이 나온다 — 화면이 그리는 그 길`() {
        // CallSummaryBody 가 하는 일 그대로: rawLines → parseOne
        val rows = CallSummaryLines.rawLines(서버가준요약).mapNotNull { CallSummaryLines.parseOne(it) }
        assertEquals(5, rows.size)
        assertEquals("", rows[0].time)                 // 한 줄 요약엔 시각이 없다
        assertEquals("0:00-0:46", rows[1].time)
        assertEquals("나", rows[1].speaker)
        assertEquals("1:43-2:20", rows[4].time)
        assertEquals("손님", rows[4].speaker)
        assertTrue(rows.count { it.time.isNotBlank() } == 4)
    }

    @Test
    fun `깎아서 넘기면 시각이 사라진다 — 이게 두 번 밟은 함정이다`() {
        // ❌ 하면 안 되는 것: parse 로 읽은 뒤 문장만 뽑아서 그리는 쪽에 넘기기
        val 깎은것 = CallSummaryLines.parse(서버가준요약).map { it.text }
        val rows = 깎은것.mapNotNull { CallSummaryLines.parseOne(it) }
        // 시각이 전부 사라진다 — 그리는 쪽이 아무리 잘 그려도 안 나온다.
        assertEquals(0, rows.count { it.time.isNotBlank() })
    }

    @Test
    fun `시각이 없는 옛 요약도 그대로 나온다`() {
        val 옛요약 = "고객: 화장실 줄눈 문의\n사장님 답: 25만원 안내"
        val rows = CallSummaryLines.rawLines(옛요약).mapNotNull { CallSummaryLines.parseOne(it) }
        assertEquals(2, rows.size)
        assertEquals("손님", rows[0].speaker)
        assertEquals("나", rows[1].speaker)
        assertEquals("", rows[0].time)
        assertEquals("화장실 줄눈 문의", rows[0].text)
    }

    @Test
    fun `빈 줄과 공백은 걸러진다`() {
        assertEquals(emptyList<String>(), CallSummaryLines.rawLines(null))
        assertEquals(emptyList<String>(), CallSummaryLines.rawLines("  \n \n"))
        assertEquals(listOf("가", "나"), CallSummaryLines.rawLines(" 가 \n\n 나 "))
    }
}
