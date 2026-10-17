package com.detailline.callfollowcrm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 문자 속 전화·날짜 감지 순수함수 테스트. (2026-08-04 사장님 — 문자 링크)
 *   상대 날짜는 baseMs 기준이라 baseMs 를 고정해 검증. 로컬/CI 타임존 차이는 같은 Calendar(default TZ)로 기대값을 만들어 상쇄.
 */
class MessageEntitiesTest {

    private fun dayStart(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis

    @Test fun phone_mobile_with_hyphen() {
        val hits = MessageEntities.detect("연락처 010-1234-5678 로 주세요", dayStart(2026, 8, 5))
        val p = hits.first { it.type == MessageEntities.Type.PHONE }
        assertEquals("01012345678", p.phoneDigits)
    }

    /** 2026-10-17 사장님 실기 — "입금했습니다" 다음 줄 주소가 **시부터** 파란 밑줄이어야 탭이 산다. */
    @Test fun address_link_covers_city_prefix() {
        val body = "입금했습니다\n안양시 박달동 한라 비발디 105동 1704호"
        val hits = MessageEntities.detect(body, dayStart(2026, 10, 17))
        val a = hits.first { it.type == MessageEntities.Type.ADDRESS }
        assertEquals(body.indexOf("안양시"), a.start)
        assertEquals(body.length, a.end)
        assertEquals("안양시 박달동 한라 비발디 105동 1704호", a.raw)
        // '입금했습니다' 는 날짜·전화가 아니다 — 엉뚱한 링크가 생기면 안 된다.
        assertEquals(1, hits.size)
    }

    @Test fun phone_representative_number() {
        val hits = MessageEntities.detect("1588-1234 로 문의주세요", dayStart(2026, 8, 5))
        assertTrue(hits.any { it.type == MessageEntities.Type.PHONE && it.phoneDigits == "15881234" })
    }

    @Test fun date_korean_month_day() {
        val hits = MessageEntities.detectDates("8월 5일 시공 가능해요", dayStart(2026, 8, 1))
        assertEquals(dayStart(2026, 8, 5), hits.first().epochMs)
    }

    @Test fun date_slash() {
        val hits = MessageEntities.detectDates("8/5 가능할까요?", dayStart(2026, 8, 1))
        assertEquals(dayStart(2026, 8, 5), hits.first().epochMs)
    }

    @Test fun date_tomorrow() {
        val hits = MessageEntities.detectDates("내일 방문할게요", dayStart(2026, 8, 4))
        assertEquals(dayStart(2026, 8, 5), hits.first().epochMs)
    }

    @Test fun date_weekday_nearest_future() {
        // 2026-08-05 = 수요일 → "금요일" = 2026-08-07
        val hits = MessageEntities.detectDates("금요일에 뵐게요", dayStart(2026, 8, 5))
        assertEquals(dayStart(2026, 8, 7), hits.first().epochMs)
    }

    @Test fun date_next_week_weekday() {
        // 2026-08-05(수) → "다음주 수요일" = 2026-08-12
        val hits = MessageEntities.detectDates("다음주 수요일 어때요", dayStart(2026, 8, 5))
        assertEquals(dayStart(2026, 8, 12), hits.first().epochMs)
    }

    @Test fun date_past_month_day_rolls_to_next_year() {
        // base 8/10, "8월 5일" 은 이미 지남 → 내년
        val hits = MessageEntities.detectDates("8월 5일", dayStart(2026, 8, 10))
        assertEquals(dayStart(2027, 8, 5), hits.first().epochMs)
    }

    @Test fun invalid_month_day_ignored() {
        // 13월 40일 = 무효 → 감지 없음
        val hits = MessageEntities.detectDates("13/40", dayStart(2026, 8, 5))
        assertTrue(hits.isEmpty())
    }

    @Test fun plain_text_no_hits() {
        val hits = MessageEntities.detect("안녕하세요 반갑습니다", dayStart(2026, 8, 5))
        assertTrue(hits.isEmpty())
    }
}
