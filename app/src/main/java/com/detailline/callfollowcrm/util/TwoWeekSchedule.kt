package com.detailline.callfollowcrm.util

import java.util.Calendar

/**
 * 전화 카드에 얹을 **2주 일정**. (2026-09-19 사장님 · 프로토 artifact/63gZ21x4Q48qHFAWg9rPaB)
 *
 * 계기: *"전화 와서 언제 스케줄되냐 가장 많이 물어보거든? 내 스케줄이 바로 보였으면 좋겠더라고."*
 *
 * ## 정해진 규칙 — 어긴 적이 있어서 이유까지 적는다
 * - **앱은 '여유'를 판단하지 않는다.** 사장님: *"어떤 사람은 하루에 한 집만, 어떤 사람은
 *   5~6집도 할 수 있을걸? 경우의 수가 다양해."* → "한 자리 남음 / 꽉 참" 같은 건 만들지 않는다.
 *   곳수·동네만 보여주고 몇 집을 받을지는 **회원이 판단**한다.
 * - **일 있는 날을 빨갛게 칠하지 않는다.** "받지 마라"는 말이 되기 때문.
 * - **'같은 동네 가는 날' 표시도 안 만든다.** 사장님: *"통화상에서 나온 지역이라 체크가 어려울 듯."*
 *   → 손님 동네를 앱은 모른다. **모르는 걸 아는 척하면 틀린다.**
 * - **빈 날 = 시공도 A/S도 일정도 없는 날.** 장모님댁 가는 날을 "비어요" 라고 하면 **앱이 거짓말을 한 것**이다.
 * - **일요일도 똑같이 보여준다** — 요일로 미리 빼지 않는다.
 *
 * 화면 그리는 쪽은 [days] 로 칸을 만들고, 칸 글자는 [cellLines] 가 정한다.
 * 계산이 전부 순수 함수라 단위 테스트로 검산한다(TwoWeekScheduleTest).
 */
object TwoWeekSchedule {

    /** 2주 = 7칸 × 2줄. */
    const val DAY_COUNT = 14

    /** 칸 하나(47dp)에 8sp 글씨로 들어가는 글자 수. */
    const val MAX_CHARS = 5

    enum class Tone { JOB, AS, EVENT, EMPTY }

    /** 그날 있는 일 하나. 칸을 누르면 이 내용이 펴진다. */
    data class Item(
        val tone: Tone,
        /** "오전 10시" / "하루 종일" */
        val time: String,
        /** 시공·A/S = 손님 이름, 내 일정 = 제목 */
        val who: String,
        /** 시공·A/S = 주소, 내 일정 = 메모. 동네 이름도 여기서 뽑는다. */
        val detail: String,
        /** 줄 세우기용 — 자정부터 분. 하루 종일은 0 이라 맨 위. 글자("오전 10시")로 정렬하면 9시가 뒤로 간다. */
        val sortKey: Int = 0
    )

    data class Day(
        val dayStartMs: Long,
        val dayOfMonth: Int,
        /** "금" */
        val weekday: String,
        val isToday: Boolean,
        val items: List<Item>
    ) {
        val isFree: Boolean get() = items.isEmpty()
        fun itemsOf(tone: Tone): List<Item> = items.filter { it.tone == tone }
    }

    data class Line(val text: String, val tone: Tone)

    /** 시공 한 건 / A/S 한 건 / 일정 한 건 — 부르는 쪽이 DB 모양에 안 묶이게 얇게 받는다. */
    data class Source(
        val tone: Tone,
        val dayStartMs: Long,
        /** 며칠짜리인지. 이틀이면 이틀 다 칸에 찍힌다. */
        val days: Int,
        /** 자정부터 분. null = 하루 종일 */
        val minutes: Int?,
        val who: String,
        val detail: String
    )

    private val WEEKDAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")

    /**
     * 오늘부터 14일. [sources] 는 순서 상관없이 넣으면 된다.
     * 하루 안에서는 **시공 → A/S → 내 일정** 순, 같은 종류끼리는 이른 시간 순.
     */
    fun days(sources: List<Source>, now: Long = System.currentTimeMillis()): List<Day> {
        val today = DateTimeUtils.startOfDay(now)
        val byDay = HashMap<Long, MutableList<Item>>()

        for (s in sources) {
            val start = DateTimeUtils.startOfDay(s.dayStartMs)
            val span = if (s.days < 1) 1 else s.days
            for (i in 0 until span) {
                val d = start + i * DateTimeUtils.DAY_MS
                if (d < today || d >= today + DAY_COUNT * DateTimeUtils.DAY_MS) continue
                // 이틀짜리 둘째 날은 시작 시각을 말하면 거짓이 된다.
                //   시간을 안 적은 시공·A/S 를 "하루 종일" 이라 하면 **온종일 묶인 것처럼 보인다.**
                //   안 정한 것뿐이니 "시간 미정". 일정은 원래 종일인 게 많아서 "하루 종일" 이 맞다.
                val time = when {
                    i > 0 -> "${i + 1}일째"
                    s.minutes != null -> DateTimeUtils.formatWorkMinutes(s.minutes)
                    s.tone == Tone.EVENT -> "하루 종일"
                    else -> "시간 미정"
                }
                byDay.getOrPut(d) { ArrayList() }.add(
                    Item(
                        tone = s.tone, time = time, who = s.who, detail = s.detail,
                        sortKey = if (i > 0) 0 else (s.minutes ?: 0)
                    )
                )
            }
        }

        val cal = Calendar.getInstance()
        return (0 until DAY_COUNT).map { i ->
            val d = today + i * DateTimeUtils.DAY_MS
            cal.timeInMillis = d
            Day(
                dayStartMs = d,
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                weekday = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1],
                isToday = i == 0,
                items = (byDay[d] ?: emptyList<Item>()).sortedWith(
                    compareBy({ toneOrder(it.tone) }, { it.sortKey })
                )
            )
        }
    }

    private fun toneOrder(t: Tone) = when (t) {
        Tone.JOB -> 0; Tone.AS -> 1; Tone.EVENT -> 2; Tone.EMPTY -> 3
    }

    /**
     * 칸에 적을 글 — **최대 두 줄**.
     *   시공 `동탄` / `동탄·수원` / `동탄 외 2`
     *   A/S  `AS 동탄`  ← `[A/S]동탄` 은 그것만 다섯 칸이라 지역이 안 들어간다
     *   일정 `장모님댁` ← **첫 낱말만**
     * 세 가지가 다 있으면 둘째 줄 뒤에 `외1` 을 붙인다.
     */
    fun cellLines(day: Day): List<Line> {
        val sig = ArrayList<Line>(3)

        val jobs = day.itemsOf(Tone.JOB)
        if (jobs.isNotEmpty()) {
            val r = RegionName.joinRegions(jobs.map { it.detail })
                ?: if (jobs.size > 1) "시공 ${jobs.size}" else "시공"
            sig += Line(r, Tone.JOB)
        }

        val asList = day.itemsOf(Tone.AS)
        if (asList.isNotEmpty()) {
            // 여기선 "외 2" 의 빈칸도 아까워서 붙여 쓴다 — "AS " 가 이미 세 칸을 먹는다.
            val r = RegionName.joinRegions(asList.map { it.detail })?.replace(" 외 ", "외")
            sig += Line(if (r == null) "AS" else "AS $r", Tone.AS)
        }

        val events = day.itemsOf(Tone.EVENT)
        if (events.isNotEmpty()) {
            val head = firstWord(events.first().who)
            sig += Line(if (events.size > 1) "$head 외 ${events.size - 1}" else head, Tone.EVENT)
        }

        if (sig.isEmpty()) return listOf(Line("비었음", Tone.EMPTY))

        val over = sig.size - 2
        val shown = if (over > 0) sig.subList(0, 2).toMutableList() else sig
        if (over > 0) shown[1] = shown[1].copy(text = shown[1].text + " 외$over")
        return shown
    }

    /**
     * 긴 제목은 **첫 낱말만**. (2026-09-19 사장님 "첫낱말 나도 그거 괜찮은듯")
     *   "장모님댁 생신 모임" → 장모님댁 · "병원 물리치료" → 병원 · "부가세 신고 마감" → 부가세
     * 왜 통째로 안 자르나: **"부가세 신…" 은 읽다가 걸리는데 "부가세" 는 안 걸린다.**
     * 정확한 건 칸을 눌러서 본다. 띄어쓰기가 아예 없는 제목은 어쩔 수 없이 자른다.
     */
    fun firstWord(title: String): String {
        val t = title.trim()
        if (t.isEmpty()) return "일정"
        val w = t.split(Regex("[\\s·,()\\[\\]\\-–—/]+")).firstOrNull { it.isNotBlank() } ?: t
        return if (w.length > MAX_CHARS) w.take(MAX_CHARS - 1) + "…" else w
    }

    /** 맨 윗줄 — "빈 날 — 20일(일) · 22일(화) · 1일(목)". 아무 날도 없으면 null. */
    fun freeDaysLabel(days: List<Day>, max: Int = 3): String? {
        val free = days.filter { it.isFree }
        if (free.isEmpty()) return null
        val head = free.take(max).joinToString(" · ") { "${it.dayOfMonth}일(${it.weekday})" }
        return if (free.size > max) "$head 외 ${free.size - max}일" else head
    }
}
