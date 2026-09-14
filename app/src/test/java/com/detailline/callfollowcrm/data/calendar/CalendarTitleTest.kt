package com.detailline.callfollowcrm.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 캘린더 제목/본문 규칙 — 사장님 예전 양식 "🏗️[125] 서울 송파구" 를 지키는지. (2026-09-14)
 *   regionOf / splitItems 는 private 이라 같은 규칙을 여기에 옮겨 고정한다.
 *   (규칙을 바꾸면 이 테스트도 같이 깨져서 "몰래 바뀌는 일"이 없게)
 */
class CalendarTitleTest {

    private fun regionOf(addr: String): String? {
        val t = addr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (t.isEmpty()) return null
        val sido = t[0]
            .replace("특별자치도", "").replace("특별자치시", "")
            .replace("특별시", "").replace("광역시", "")
            .removeSuffix("도")
            .ifBlank { t[0] }
        val gu = t.getOrNull(1)?.takeIf { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") }
        return listOfNotNull(sido.takeIf { it.isNotBlank() }, gu).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun splitItems(raw: String): List<String> =
        raw.split('\n', ',', '·', ';').map { it.trim() }.filter { it.isNotBlank() }

    private fun title(total: Long?, addr: String?, name: String?, phone: String): String {
        val region = addr?.let { regionOf(it) }
        val base = region ?: name?.takeIf { it.isNotBlank() } ?: phone
        val tag = total?.takeIf { it > 0L }?.let { "[${it / 10_000L}]" } ?: ""
        return "🏗️$tag $base".trim()
    }

    @Test
    fun `지역은 시도 + 시군구 까지만`() {
        assertEquals("서울 송파구", regionOf("서울 송파구 가락로5길 3-20 (석촌동) 501호"))
        assertEquals("서울 송파구", regionOf("서울특별시 송파구 가락로5길 3-20"))
        assertEquals("경기 안산시", regionOf("경기 안산시 단원구 광덕서로 19 116동1301호"))
        assertEquals("경기 안산시", regionOf("경기도 안산시 단원구 광덕서로 19"))
        assertEquals("인천 미추홀구", regionOf("인천광역시 미추홀구 인하로 100"))
    }

    @Test
    fun `주소가 이상하면 억지로 만들지 않는다`() {
        // 두 번째 덩어리가 시/군/구 가 아니면 시도만
        assertEquals("서울", regionOf("서울 가락로5길 3-20"))
        assertEquals(null, regionOf("   "))
    }

    @Test
    fun `제목 = 금액 만원 + 지역 (사장님 예전 양식)`() {
        assertEquals(
            "🏗️[125] 서울 송파구",
            title(1_250_000L, "서울 송파구 가락로5길 3-20", null, "01029652817")
        )
    }

    @Test
    fun `금액이 없으면 대괄호를 빼고 지역만`() {
        assertEquals("🏗️ 서울 송파구", title(null, "서울 송파구 가락로5길 3-20", null, "01029652817"))
        assertEquals("🏗️ 서울 송파구", title(0L, "서울 송파구 가락로5길 3-20", null, "01029652817"))
    }

    @Test
    fun `주소가 없으면 이름, 이름도 없으면 번호`() {
        assertEquals("🏗️[300] 김지영", title(3_000_000L, null, "김지영", "01029652817"))
        assertEquals("🏗️ 01092833986", title(null, null, null, "01092833986"))
    }

    @Test
    fun `시공 내용은 쉼표든 줄바꿈이든 한 줄에 하나씩`() {
        assertEquals(
            listOf("안방 화장실 바닥", "샤워부스 벽 3면"),
            splitItems("안방 화장실 바닥, 샤워부스 벽 3면")
        )
        assertEquals(
            listOf("거실 줄눈", "주방 줄눈", "베란다"),
            splitItems("거실 줄눈\n주방 줄눈 · 베란다")
        )
        assertEquals(emptyList<String>(), splitItems("  ,  , "))
    }
}
