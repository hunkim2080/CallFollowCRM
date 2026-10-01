package com.detailline.callfollowcrm.domain.collab

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🤝 **협업 요청을 받았을 때 「그날 되나?」를 한 줄로.** (2026-09-30 사장님)
 *
 *   "이 부분은 내 스케줄이 있는지 없는지 보고 수락을 해야 한단 말이지?
 *    근데 이 부분에 캘린더가 없으면 확인을 나갔다 와야 해."
 *
 * 수락할지는 **그날 내가 비었는지**로 정해진다. 그 답이 요청 카드에 없어서
 * 일정 탭으로 나갔다 와야 했다 — **결정에 필요한 정보가 결정하는 자리에 없었다.**
 *
 * Android 의존성 없음 → 폰 없이도 매 빌드가 검사한다([CollabDayCheckTest]).
 */
object CollabDayCheck {

    /**
     * 시공 하나가 잡아먹는 시간. 줄눈은 몇 시간짜리라 **시각이 조금 달라도 겹친다.**
     *   사장님 예: 요청 오후 1시 · 내 일정 오후 2시 → 「시간이 겹쳐요」.
     *   짧게 잡으면 겹치는데 안 겹친다고 하게 되고, 그게 더 나쁘다(가서 못 한다).
     */
    const val WORK_SPAN_MS = 4L * 60 * 60 * 1000

    /**
     * 🗓️ **그 현장 날짜가 이미 지났나.** (2026-10-01 사장님 "날짜가 지난 협업요청이 수락이 되네..?")
     *
     *   9.30 오전 9시 현장이 10/1 에 수락됐다. 수락을 막는 자가
     *   **「요청 보낸 지 12시간」 하나뿐**이고 **시공 날짜는 아무도 안 봤다.**
     *
     *   · **어제 이하면 지난 것.** 어제 갔어야 할 현장을 오늘 수락하는 건 뜻이 없다.
     *   · **오늘은 아직 아니다** — 아침에 불러서 그날 합류하는 일이 흔하다.
     *   · **날짜가 없으면(0) 막지 않는다** — 「날짜 미정」으로 먼저 잡는 요청이 있다.
     */
    fun dayPassed(scheduledAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (scheduledAtMs <= 0L) return false
        return ymd(scheduledAtMs) < ymd(nowMs)
    }

    private fun ymd(ms: Long): Int {
        val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Seoul"))
        return z.year * 10000 + z.monthValue * 100 + z.dayOfMonth
    }

    /** 그날 내 현장 하나. */
    data class MyJob(
        val startMs: Long,
        /** 자정부터 몇 분. null = 시간 안 정함. */
        val minutes: Int?,
        /** 동네 이름(「수원 장안구」). 없으면 null. */
        val place: String?
    )

    enum class Kind {
        /** 그날 아무 일정 없음. */
        FREE,
        /** 같은 날 일정이 있지만 **시간은 안 겹침**(또는 한쪽이 시간 미정). */
        SAME_DAY,
        /** **시간까지 겹침** — 가서 못 한다. */
        TIME_CLASH
    }

    data class Result(
        val kind: Kind,
        /** 겹치거나 같은 날인 내 현장의 동네. 없으면 null. */
        val place: String?,
        /** 그 현장 시각(「오후 2시」). 시간 미정이면 null. */
        val timeLabel: String?
    )

    /**
     * @param requestAtMs 요청받은 시공 시각(ms). 시간이 없으면 그 날 자정.
     * @param requestHasTime 요청에 **시각이 있나**. 자정(0시)은 「시간 미정」이라 시간 비교를 안 한다.
     * @param myJobs 내 시공 전부. 이 안에서 같은 날만 고른다.
     */
    fun check(requestAtMs: Long, requestHasTime: Boolean, myJobs: List<MyJob>): Result {
        if (requestAtMs <= 0L) return Result(Kind.FREE, null, null)
        val day = DateTimeUtils.startOfDay(requestAtMs)
        val sameDay = myJobs.filter {
            it.startMs > 0L && DateTimeUtils.startOfDay(it.startMs) == day
        }
        if (sameDay.isEmpty()) return Result(Kind.FREE, null, null)

        // 시간까지 겹치는 게 있으면 그걸 먼저 말한다 — 제일 급한 소식이다.
        if (requestHasTime) {
            val clash = sameDay.firstOrNull { j ->
                val m = j.minutes ?: return@firstOrNull false
                val jobAt = DateTimeUtils.startOfDay(j.startMs) + m * 60_000L
                kotlin.math.abs(jobAt - requestAtMs) < WORK_SPAN_MS
            }
            if (clash != null) {
                return Result(Kind.TIME_CLASH, clash.place, timeLabel(clash.minutes))
            }
        }
        // 같은 날이지만 시간은 안 겹친다(또는 한쪽이 시간 미정) — 할 수 있을지도 모른다.
        val first = sameDay.minByOrNull { it.minutes ?: Int.MAX_VALUE } ?: sameDay.first()
        return Result(Kind.SAME_DAY, first.place, timeLabel(first.minutes))
    }

    /** 자정부터 분 → 「오후 2시」·「오전 9시 30분」. null 이면 null. */
    fun timeLabel(minutes: Int?): String? {
        val m = minutes ?: return null
        if (m !in 0..(24 * 60)) return null
        val h = m / 60
        val mm = m % 60
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (mm == 0) "$ampm ${h12}시" else "$ampm ${h12}시 ${mm}분"
    }
}
