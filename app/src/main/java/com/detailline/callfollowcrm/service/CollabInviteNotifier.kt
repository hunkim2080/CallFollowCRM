package com.detailline.callfollowcrm.service

import com.detailline.callfollowcrm.util.AddressExtractor
import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🤝 협업 요청 알림(받는 쪽) **제목·본문 글자 조립** — 표준 알림이라 어떤 폰에서도 안 잘린다. (2026-11-25 사장님)
 *   제목 = 날짜 · [지역] · 지원 요청  /  본문 = 마감 · 시공시각 · 일당 · 업체.
 *
 *   ⚠️ 어제까지 쓰던 **커스텀 RemoteViews(호박 네모칩)**은 삼성 좁은 배너에서 잘려 뺐다(사장님 실기).
 *      라이브 카운트다운은 **표준**(NotificationHelper 의 setWhen+Chronometer)으로, 마지막 1시간만.
 *   지역·시각·마감 셈이 **여기 한 곳** — 화면/알림이 따로 조립하지 않게(§12). 순수 함수 → 단위테스트.
 */
object CollabInviteNotifier {

    data class Lines(val title: String, val body: String)

    /**
     * @param scheduledAtMs 시공 예정일 epoch ms (0 이하면 날짜 생략).
     * @param addr 현장 주소(지역 "[용인]" 추출원). 비면 title 에서.
     * @param title 현장 표시명.
     * @param timeLabel 시공 시각 "HH:mm"(예 "14:00"). null/"00:00" 이면 생략.
     * @param ownerName 부른 사장 상호("하우스픽"). 빈값이면 "다른 사장님".
     * @param dailyWage 일당(만원, null/0 이면 생략).
     * @param deadlineMs 수락 마감 epoch ms. @param now 기준 시각. 마지막 1시간(라이브)은 본문 마감 생략.
     */
    fun lines(
        scheduledAtMs: Long, addr: String?, title: String, timeLabel: String?,
        ownerName: String, dailyWage: Int?, deadlineMs: Long, now: Long
    ): Lines {
        val who = ownerName.takeIf { it.isNotBlank() }?.let { "$it 사장님" } ?: "다른 사장님"
        val date = scheduledAtMs.takeIf { it > 0L }?.let { DateTimeUtils.formatDateLabel(it) }
        val rgn = AddressExtractor.region(addr ?: title)?.let { "[$it]" }
        val head = listOfNotNull(date, rgn).joinToString(" · ")
        val headTitle = if (head.isBlank()) "협업 지원 요청" else "$head 지원 요청"
        val remainMs = deadlineMs - now
        val remainingHours = if (remainMs > 0L) ((remainMs + 3_599_999L) / 3_600_000L).toInt() else 0
        val body = listOfNotNull(
            remainingHours.takeIf { it > 0 }?.let { "마감 ${it}시간" },
            timeStrOf(timeLabel),
            dailyWage?.takeIf { it > 0 }?.let { "일당 ${it}만" },
            who
        ).joinToString(" · ")
        return Lines(headTitle, body)
    }

    /** "HH:mm" → "오후 2시"(DateTimeUtils 한 곳). 미지정(자정·빈값)이면 null. */
    private fun timeStrOf(timeLabel: String?): String? {
        val tl = timeLabel?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val p = tl.split(":")
        val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        return if (h == 0 && m == 0) null else DateTimeUtils.timeLabel(h, m)
    }
}
