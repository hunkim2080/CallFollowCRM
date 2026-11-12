package com.detailline.callfollowcrm.domain.job

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 📅 **여러 날 시공의 날짜별 완료** 를 세는 한 곳. (2026-11-12 사장님 "1일차 완료했는데 2일차까지 완료돼버림")
 *
 *   저장: `JobEntity.completedDayStarts` = 완료한 날의 **자정 ms** 를 콤마로 이어붙인 문자열.
 *   건 전체 완료(`workCompletedAt`)는 **모든 날이 완료됐을 때만** 찍는다 → 기존 딱지·정산·목록 로직은 그대로.
 *
 *   화면마다 날짜 전개·완료 판정을 새로 짜면 어긋난다(§12) — 전개·토글·전체완료 판정을 전부 여기서.
 */
object JobDayCompletion {

    private const val DAY = 24L * 60 * 60 * 1000

    /** 이 건이 걸친 날들의 자정 ms (시작일 ~ +일수). 일정 없으면 빈 목록. */
    fun dayStarts(scheduledWorkDate: Long?, scheduledWorkDays: Int): List<Long> {
        val start = scheduledWorkDate ?: return emptyList()
        val d0 = DateTimeUtils.startOfDay(start)
        val n = scheduledWorkDays.coerceAtLeast(1)
        return (0 until n).map { DateTimeUtils.startOfDay(d0 + it * DAY) }
    }

    /** 완료 문자열 → 자정 ms 집합. */
    fun completedSet(csv: String?): Set<Long> =
        csv?.split(",").orEmpty()
            .mapNotNull { it.trim().toLongOrNull() }
            .map { DateTimeUtils.startOfDay(it) }
            .toSet()

    /** 그 날이 완료됐나. */
    fun isDayDone(csv: String?, dayStart: Long): Boolean =
        DateTimeUtils.startOfDay(dayStart) in completedSet(csv)

    /**
     * 그 날을 완료/해제 **토글**한 새 문자열(건에 걸친 날만, 정렬).
     *   건 범위 밖 날짜는 무시(잘못 들어오는 것 방지).
     */
    fun withDayToggled(csv: String?, dayStart: Long, scheduledWorkDate: Long?, scheduledWorkDays: Int): String {
        val valid = dayStarts(scheduledWorkDate, scheduledWorkDays).toSet()
        val d = DateTimeUtils.startOfDay(dayStart)
        val cur = completedSet(csv).toMutableSet()
        when {
            d in cur -> cur.remove(d)
            d in valid -> cur.add(d)
        }
        return cur.filter { it in valid }.sorted().joinToString(",")
    }

    /** 걸친 날이 하나라도 있고, **그 날이 전부** 완료됐나(= 건 전체 완료). */
    fun allDone(csv: String?, scheduledWorkDate: Long?, scheduledWorkDays: Int): Boolean {
        val days = dayStarts(scheduledWorkDate, scheduledWorkDays)
        if (days.isEmpty()) return false
        val done = completedSet(csv)
        return days.all { it in done }
    }

    /** 완료한 날 수(건 범위 안). */
    fun doneCount(csv: String?, scheduledWorkDate: Long?, scheduledWorkDays: Int): Int {
        val days = dayStarts(scheduledWorkDate, scheduledWorkDays).toSet()
        return completedSet(csv).count { it in days }
    }

    /** 건 전체를 완료로 표시하는 문자열(모든 날 채움) — [allDone] 이 true 가 되게. */
    fun allDaysCsv(scheduledWorkDate: Long?, scheduledWorkDays: Int): String =
        dayStarts(scheduledWorkDate, scheduledWorkDays).joinToString(",")
}
