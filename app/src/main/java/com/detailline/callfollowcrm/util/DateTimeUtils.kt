package com.detailline.callfollowcrm.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    private val dateTimeFormat by lazy { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }
    private val dateTimeFormatWithYear by lazy { SimpleDateFormat("yyyy.M.d HH:mm", Locale.getDefault()) }
    private val fullFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    private val dateOnly by lazy { SimpleDateFormat("M/d", Locale.getDefault()) }
    private val koreanDate by lazy { SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN) }
    private val monthHeader by lazy { SimpleDateFormat("yyyy년 M월", Locale.KOREAN) }

    /**
     * 채팅/통화 시각 표시.
     * - 올해 메시지: "5/24 13:46" (간결)
     * - 다른 해 메시지: "2025.10.3 09:25" (년도 포함 — 옛 메시지인지 즉판)
     */
    fun formatShort(epoch: Long): String {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = epoch
        val msgYear = cal.get(Calendar.YEAR)
        return if (msgYear == currentYear) dateTimeFormat.format(Date(epoch))
        else dateTimeFormatWithYear.format(Date(epoch))
    }
    fun formatTime(epoch: Long): String = timeFormat.format(Date(epoch))
    fun formatFull(epoch: Long): String = fullFormat.format(Date(epoch))
    fun formatDateOnly(epoch: Long): String = dateOnly.format(Date(epoch))
    fun formatKoreanDate(epoch: Long): String = koreanDate.format(Date(epoch))
    fun formatMonthHeader(epoch: Long): String = monthHeader.format(Date(epoch))

    /** 하루 = 86,400,000 ms (한국은 DST 없음 → 안전). 시공 기간(여러 날) 계산에 사용. */
    const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /** 시공 시작 시각(자정부터 분, 0~1439)을 "오전 9시" / "오후 1시 30분" 으로. */
    fun formatWorkMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val ap = if (h < 12) "오전" else "오후"
        val h12 = (h % 12).let { if (it == 0) 12 else it }
        return buildString {
            append(ap); append(' '); append(h12); append('시')
            if (m > 0) { append(' '); append(m); append('분') }
        }
    }

    /** 입력 시각을 그날 00:00 자정 epoch 로 정규화. 시공 예약 저장에 사용. */
    fun startOfDay(epoch: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epoch }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * targetEpoch 가 오늘 기준 몇 일 전/후인지를 사람이 읽기 좋은 라벨로.
     *   오늘 -> "D-day" / "오늘"
     *   미래 -> "D-N"  (N일 남음)
     *   과거 -> "D+N"  (N일 지남)
     */
    fun dDayLabel(targetEpoch: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val target = startOfDay(targetEpoch)
        val diffDays = ((target - today) / (24L * 60 * 60 * 1000)).toInt()
        return when {
            diffDays == 0 -> "오늘"
            diffDays > 0 -> "D-$diffDays"
            else -> "D+${-diffDays}"
        }
    }

    fun todayBounds(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 24L * 60 * 60 * 1000 - 1
        return start to end
    }

    private val weekdayKor by lazy { SimpleDateFormat("EEEE", Locale.KOREAN) }
    private val dateWithWeekday by lazy { SimpleDateFormat("M월 d일 (E)", Locale.KOREAN) }
    private val dateWithWeekdayYear by lazy { SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN) }

    /** 갤메시지/에이닷 통화기록 헤더 형식 — 명시 날짜 + 요일. "5. 22. 금요일" / 다른 해 "2025. 10. 3. 금요일". */
    private val dayHeaderKor by lazy { SimpleDateFormat("M. d. EEEE", Locale.KOREAN) }
    private val dayHeaderKorYear by lazy { SimpleDateFormat("yyyy. M. d. EEEE", Locale.KOREAN) }

    /** 시공 예약일 — 안내문/다이얼로그 본문용. 다른 해면 yyyy 포함. 예: "5월 26일 (수)" */
    fun formatScheduledDate(epoch: Long, now: Long = System.currentTimeMillis()): String {
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val targetCal = Calendar.getInstance().apply { timeInMillis = epoch }
        val differentYear = nowCal.get(Calendar.YEAR) != targetCal.get(Calendar.YEAR)
        return if (differentYear) dateWithWeekdayYear.format(Date(epoch))
        else dateWithWeekday.format(Date(epoch))
    }

    /**
     * 대시보드 타임라인의 날짜 그룹 헤더 라벨 — 갤메시지/에이닷 통화기록 벤치마킹 (사장님 결정 2026-05-25).
     *   기본 형식: "5. 22. 금요일"
     *   다른 해:  "2025. 10. 3. 금요일"
     *
     * 이전 ("오늘"/"어제"/"그저께"/요일) 자연어 형식 제거 — 사장님이 명시 날짜를 더 직관 인식.
     */
    fun dayGroupLabel(dayStartMs: Long, now: Long = System.currentTimeMillis()): String {
        val target = startOfDay(dayStartMs)
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val targetCal = Calendar.getInstance().apply { timeInMillis = target }
        val differentYear = nowCal.get(Calendar.YEAR) != targetCal.get(Calendar.YEAR)
        return if (differentYear) dayHeaderKorYear.format(Date(target))
        else dayHeaderKor.format(Date(target))
    }

    fun durationLabel(seconds: Long): String {
        if (seconds <= 0) return "0초"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}분 ${s}초" else "${s}초"
    }
}
