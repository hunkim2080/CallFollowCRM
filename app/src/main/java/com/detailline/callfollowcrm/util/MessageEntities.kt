package com.detailline.callfollowcrm.util

import java.util.Calendar

/**
 * 문자 본문에서 "전화번호 / 날짜"를 찾아내는 순수 로직. (2026-08-04 사장님)
 *   채팅 말풍선에서 이걸로 파란 링크를 만들고, 탭하면 전화걸기·복사·시공일/AS 등록을 띄운다.
 *
 * 설계 원칙(사장님): **오탐 0 우선** — 애매하면 안 잡는다(지저분하게 파란 줄 남발 금지).
 *   - 전화: 한국 휴대폰/유선/대표번호(15xx·16xx·18xx)만. 앞뒤가 숫자·하이픈이면 제외(긴 숫자열 오탐 방지).
 *   - 날짜: "8월 5일", "8/5", 오늘/내일/모레/글피, "X요일"(가장 가까운 미래). 상대 날짜는 baseMs(문자 받은 시각) 기준.
 *
 * epoch 는 항상 그 날 자정(startOfDay) 기준. 실제 등록 시각/분은 등록 화면에서 조정.
 */
object MessageEntities {

    enum class Type { PHONE, DATE }

    /**
     * @param start 본문 내 시작 인덱스(포함) / @param end 끝(제외)
     * @param phoneDigits 전화일 때 숫자만(전화걸기·복사용)
     * @param epochMs 날짜일 때 그 날 자정 epoch
     */
    data class Hit(
        val start: Int,
        val end: Int,
        val type: Type,
        val raw: String,
        val phoneDigits: String? = null,
        val epochMs: Long? = null
    )

    // 15xx/16xx/18xx 대표번호(8자리) | 0-시작 휴대폰·유선. 앞뒤 숫자/하이픈이면 제외.
    private val PHONE = Regex("(?<![\\d\\-])(1[568]\\d{2}[- .]?\\d{4}|0\\d{1,2}[- .]?\\d{3,4}[- .]?\\d{4})(?![\\d\\-])")

    private val MONTH_DAY_KO = Regex("(\\d{1,2})월\\s?(\\d{1,2})일")
    private val MONTH_DAY_SLASH = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])")
    private val WEEKDAY = Regex("(다음\\s?주\\s*)?([월화수목금토일])요일")

    /** 본문 전체에서 전화·날짜 hit 을 위치순으로. 겹치면 앞선 것 우선(전화 먼저 잡고 그 구간은 날짜 제외). */
    fun detect(text: String, baseMs: Long): List<Hit> {
        val hits = ArrayList<Hit>()
        PHONE.findAll(text).forEach { m ->
            val digits = m.value.filter { it.isDigit() }
            if (digits.length in 8..11) {
                hits.add(Hit(m.range.first, m.range.last + 1, Type.PHONE, m.value, phoneDigits = digits))
            }
        }
        detectDates(text, baseMs).forEach { hits.add(it) }
        // 위치순 + 겹침 제거(먼저 시작한 것 우선)
        hits.sortBy { it.start }
        val out = ArrayList<Hit>()
        var lastEnd = -1
        for (h in hits) {
            if (h.start >= lastEnd) { out.add(h); lastEnd = h.end }
        }
        return out
    }

    /** 날짜만 (전화 제외). detect() 가 내부에서 씀 + 단위테스트용으로 공개. */
    fun detectDates(text: String, baseMs: Long): List<Hit> {
        val out = ArrayList<Hit>()

        MONTH_DAY_KO.findAll(text).forEach { m ->
            val mo = m.groupValues[1].toInt(); val d = m.groupValues[2].toInt()
            monthDayEpoch(mo, d, baseMs)?.let {
                out.add(Hit(m.range.first, m.range.last + 1, Type.DATE, m.value, epochMs = it))
            }
        }
        MONTH_DAY_SLASH.findAll(text).forEach { m ->
            val mo = m.groupValues[1].toInt(); val d = m.groupValues[2].toInt()
            monthDayEpoch(mo, d, baseMs)?.let {
                out.add(Hit(m.range.first, m.range.last + 1, Type.DATE, m.value, epochMs = it))
            }
        }
        // 오늘/내일/모레/글피
        listOf("오늘" to 0, "내일" to 1, "모레" to 2, "글피" to 3).forEach { (word, add) ->
            var idx = text.indexOf(word)
            while (idx >= 0) {
                out.add(Hit(idx, idx + word.length, Type.DATE, word, epochMs = startOfDay(baseMs) + add * DAY_MS))
                idx = text.indexOf(word, idx + word.length)
            }
        }
        WEEKDAY.findAll(text).forEach { m ->
            val nextWeek = m.groupValues[1].isNotBlank()
            val target = "월화수목금토일".indexOf(m.groupValues[2][0]) // 0=월..6=일
            out.add(Hit(m.range.first, m.range.last + 1, Type.DATE, m.value, epochMs = weekdayEpoch(target, baseMs, nextWeek)))
        }
        return out
    }

    /** 월/일 → 올해 자정 epoch. 유효하지 않은 날짜면 null. 이미 지난 날짜면 내년으로. */
    private fun monthDayEpoch(month: Int, day: Int, baseMs: Long): Long? {
        if (month !in 1..12 || day !in 1..31) return null
        val cal = Calendar.getInstance().apply { timeInMillis = baseMs }
        val year = cal.get(Calendar.YEAR)
        fun build(y: Int): Long? {
            val c = Calendar.getInstance().apply {
                clear(); set(Calendar.YEAR, y); set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, day)
            }
            // 존재하지 않는 날짜(예: 2/30)면 롤오버되어 month 가 바뀜 → 무효 처리
            return if (c.get(Calendar.MONTH) == month - 1) c.timeInMillis else null
        }
        val thisYear = build(year) ?: return null
        return if (thisYear < startOfDay(baseMs)) (build(year + 1) ?: thisYear) else thisYear
    }

    /** target 요일(0=월..6=일)의 가장 가까운 미래(오늘 포함) 자정 epoch. nextWeek 면 +7. */
    private fun weekdayEpoch(target: Int, baseMs: Long, nextWeek: Boolean): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(baseMs) }
        // Calendar: SUNDAY=1..SATURDAY=7 → 0=월..6=일 로 변환
        val todayIdx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        var delta = (target - todayIdx + 7) % 7   // 오늘이면 0
        if (nextWeek) delta += 7
        return startOfDay(baseMs) + delta * DAY_MS
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
}
