package com.detailline.callfollowcrm.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val timeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    private val dateTimeFormat by lazy { SimpleDateFormat("M/d HH:mm", Locale.getDefault()) }
    private val fullFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    private val dateOnly by lazy { SimpleDateFormat("M/d", Locale.getDefault()) }
    private val koreanDate by lazy { SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN) }
    private val monthHeader by lazy { SimpleDateFormat("yyyy년 M월", Locale.KOREAN) }

    fun formatShort(epoch: Long): String = dateTimeFormat.format(Date(epoch))
    fun formatTime(epoch: Long): String = timeFormat.format(Date(epoch))
    fun formatFull(epoch: Long): String = fullFormat.format(Date(epoch))
    fun formatDateOnly(epoch: Long): String = dateOnly.format(Date(epoch))
    fun formatKoreanDate(epoch: Long): String = koreanDate.format(Date(epoch))
    fun formatMonthHeader(epoch: Long): String = monthHeader.format(Date(epoch))

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

    /**
     * 대시보드 타임라인의 날짜 그룹 헤더 라벨.
     *   오늘 → "오늘"
     *   어제 → "어제"
     *   그저께 → "그저께"
     *   이번 주 (3~6일 전) → "수요일"
     *   그 외 → "5월 12일 (수)"
     */
    fun dayGroupLabel(dayStartMs: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val target = startOfDay(dayStartMs)
        val diff = ((today - target) / (24L * 60 * 60 * 1000)).toInt()
        return when (diff) {
            0 -> "오늘"
            1 -> "어제"
            2 -> "그저께"
            in 3..6 -> weekdayKor.format(Date(target))
            else -> dateWithWeekday.format(Date(target))
        }
    }

    fun durationLabel(seconds: Long): String {
        if (seconds <= 0) return "0초"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}분 ${s}초" else "${s}초"
    }
}
