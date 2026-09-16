package com.detailline.callfollowcrm.domain.reminder

import com.detailline.callfollowcrm.data.local.entity.JobEntity

/**
 * 알람을 **건(件)별로** 발사하기 위한 순수 규칙. (2026-09-17, 재방문 Stage B)
 *
 * 왜 필요한가 — 지금까지 D-1·잔금 알람은 고객 표의 **대표 건 하나**만 봤다.
 *   한 고객이 두 날짜를 잡으면(인테리어 업체가 1차·2차·3차 주는 경우),
 *   **두 번째 날짜의 D-1 알람이 아예 안 울렸다.** 현장을 놓치는 사고다.
 *   (docs/PLAN_repeat_jobs.md — Stage B "알람 건별")
 *
 * 돈·알람 경로라 화면 없이 **순수 함수 + 단위테스트**로 고정한다. 눈으로는 "안 울린 것"을 못 본다.
 */
object JobReminderCalc {

    /** 알림 한 건. */
    data class Due(val job: JobEntity, val key: String)

    /**
     * 내일 시작하는 시공 건들.
     *
     * @param jobs 시공일이 잡힌 모든 건
     * @param tomorrowStart 내일 00:00
     * @param dayMs 하루 길이(ms)
     * @param notified 이미 알린 키 모음
     */
    fun d1Due(
        jobs: List<JobEntity>,
        tomorrowStart: Long,
        dayMs: Long,
        notified: Set<String>,
        startOfDay: (Long) -> Long
    ): List<Due> {
        val end = tomorrowStart + dayMs
        val out = ArrayList<Due>()
        for (j in jobs) {
            val s = j.scheduledWorkDate ?: continue
            val day = startOfDay(s)
            if (day < tomorrowStart || day >= end) continue
            val key = d1Key(j.id, tomorrowStart)
            if (key in notified) continue
            // 옛 키(고객 기준)로 이미 알린 건은 건너뛴다 — 업데이트 직후 하루치 중복 알림 방지.
            //   앞으로는 건 키만 쓴다. 같은 날 한 고객에게 두 건이 있는 드문 경우만
            //   이 한 번의 밤에 두 번째 알림이 눌릴 수 있다(다음날부터 정상).
            if (legacyD1Key(j.customerId, tomorrowStart) in notified) continue
            out.add(Due(j, key))
        }
        return out
    }

    /**
     * 잔금이 남은 채 **시공이 끝난** 건들. (시공일 + 기간이 지났고, 잔금 미수)
     *
     * @param afterDays 시공 종료 후 며칠 지난 것부터 알릴지
     */
    fun balanceDue(
        jobs: List<JobEntity>,
        now: Long,
        dayMs: Long,
        afterDays: Int,
        notified: Set<String>,
        startOfDay: (Long) -> Long
    ): List<Due> {
        val out = ArrayList<Due>()
        for (j in jobs) {
            val s = j.scheduledWorkDate ?: continue
            if (j.balancePaidAt != null) continue           // 이미 받음
            val balance = j.balanceAmount ?: 0L
            if (balance <= 0L) continue                     // 받을 게 없음
            val lastDay = startOfDay(s) + (j.scheduledWorkDays.coerceAtLeast(1) - 1) * dayMs
            if (now < lastDay + afterDays * dayMs) continue // 아직 이르다
            val key = balanceKey(j.id)
            if (key in notified) continue
            out.add(Due(j, key))
        }
        return out
    }

    fun d1Key(jobId: Long, tomorrowStart: Long) = "d1j:$jobId:$tomorrowStart"
    fun legacyD1Key(customerId: Long, tomorrowStart: Long) = "d1:$customerId:$tomorrowStart"
    fun balanceKey(jobId: Long) = "balj:$jobId"

    // ⚠️ '그 날 자정'은 **밖에서 받는다**(startOfDay). 여기서 ms % 하루 로 계산하면
    //    한국 시간(UTC+9)에서 하루가 어긋난다 — 9시 이전 시공이 전날로 밀려 알람이 하루 빨리/늦게 간다.
}
