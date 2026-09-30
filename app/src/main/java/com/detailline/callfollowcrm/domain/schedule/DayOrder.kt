package com.detailline.callfollowcrm.domain.schedule

/**
 * 🕐 **그날 갈 곳을 세우는 순서 — 여기 한 곳.** (2026-10-01 사장님)
 *
 *   "내 현장(시간 미정)이 첫 번째에 있고 9시 협업 현장이 두 번째에 있더라고.
 *    **시간순으로 떠야 하지 않을까? 시간 미정이면 맨 마지막에 와야 하고** 말야"
 *
 * 맞다. 아침에 나설 때 보는 건 **먼저 가는 곳**이지 **내 것/남의 것**이 아니다.
 *
 * ## 무엇이 틀렸었나
 *   ① 홈은 아예 **「내 시공 먼저, 그 뒤에 협업」**으로 세웠다.
 *      일정 탭은 2026-09-27 에 시간순으로 고쳤는데(사장님 "시간이 우선이었으면 좋겠어")
 *      **홈은 안 고쳤다.** 같은 규칙을 두 곳에 적었으니 한 곳만 고쳐진 것이다.
 *   ② 🔴 일정 탭도 **사실은 틀려 있었다.** 협업 시각을 `"9:00"` 모양으로만 읽었는데
 *      서버가 주는 건 **「오전 9시」** 뿐이다(실측: 오전 9시·오전 8시·오후 1시·오후 2시).
 *      그래서 **못 읽고 전부 맨 뒤**로 밀었다 — 고쳤다고 적어놓고 안 고쳐져 있었다.
 *
 * ## 규칙
 *   · 자정부터 몇 분인지로 센다(0~1439). 이르면 앞.
 *   · **시간 미정은 맨 뒤** — [UNKNOWN].
 *   · 협업의 「자정(0시)」은 **시간 미정**이다. 서버가 시각 없이 만든 건 다 0시로 들어온다.
 */
object DayOrder {

    /** 시간 미정 — 줄 세울 때 **맨 뒤**로 간다. */
    const val UNKNOWN = Int.MAX_VALUE

    private val HHMM = Regex("""(\d{1,2})\s*:\s*(\d{2})""")
    private val KOR = Regex("""(오전|오후)?\s*(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?""")

    /**
     * 협업 현장이 **몇 시인가** — 자정부터의 분. 모르면 [UNKNOWN].
     *
     * @param timeLabel 서버가 주는 사람 말("오전 9시"). 「9:00」 모양도 받는다(옛 자료 대비).
     * @param scheduledAtMs 라벨이 없을 때의 마지막 수단. **자정이면 시간 미정으로 본다.**
     */
    fun collabMinutes(timeLabel: String?, scheduledAtMs: Long = 0L): Int {
        val t = timeLabel?.trim().orEmpty()
        if (t.isNotEmpty()) {
            HHMM.find(t)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val mi = m.groupValues[2].toIntOrNull()
                if (h != null && mi != null && h in 0..23 && mi in 0..59) return h * 60 + mi
            }
            KOR.find(t)?.let { m ->
                val ap = m.groupValues[1]
                val h0 = m.groupValues[2].toIntOrNull()
                val mi = m.groupValues[3].toIntOrNull() ?: 0
                if (h0 != null && h0 in 0..23 && mi in 0..59) {
                    // 「오후 2시」=14시, 「오후 12시」=12시(정오), 「오전 12시」=0시.
                    val h = when {
                        ap == "오후" && h0 < 12 -> h0 + 12
                        ap == "오전" && h0 == 12 -> 0
                        else -> h0
                    }
                    if (h in 0..23) return h * 60 + mi
                }
            }
        }
        if (scheduledAtMs <= 0L) return UNKNOWN
        val mins = minutesOfDay(scheduledAtMs)
        // ⚠️ 자정은 **시간 미정**이다. 서버가 시각 없이 만든 건 다 0시로 들어온다 —
        //   그걸 「0시 시공」으로 믿으면 새벽 일정이 맨 앞에 선다.
        return if (mins == 0) UNKNOWN else mins
    }

    /** 내 시공이 몇 시인가 — 안 정했으면 [UNKNOWN]. */
    fun jobMinutes(scheduledWorkMinutes: Int?): Int = scheduledWorkMinutes ?: UNKNOWN

    /** epoch ms → 그날 자정부터의 분(서울 시간). */
    private fun minutesOfDay(ms: Long): Int {
        val z = java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.of("Asia/Seoul"))
        return z.hour * 60 + z.minute
    }
}
