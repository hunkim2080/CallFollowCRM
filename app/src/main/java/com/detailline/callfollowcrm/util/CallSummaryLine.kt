package com.detailline.callfollowcrm.util

/**
 * 통화 요약 한 줄. (2026-09-24 사장님 "몇 초부터 몇 초까지 요약이랄까 — 되게 좋은 방법이다")
 *
 * 서버가 주는 새 모양은 세 칸을 `|` 로 나눈 것이다:
 *   `0:00-0:35|손님|욕조가 깨졌는데 고칠 수 있냐고 물어봄`
 *
 * 옛 모양(`고객: …` / `사장님 답: …`)도 그대로 들어온다 — 이미 요약해둔 통화들이다.
 * **둘 다 여기서만 읽는다.** 화면마다 따로 쪼개면 한 군데를 빼먹는다.
 */
data class CallSummaryLine(
    /** "0:00-0:35". 시각을 모르는 통화(옛 요약·받아쓰기에 시각 없음)면 빈 문자열. */
    val time: String,
    /** "손님" / "나". 모르면 빈 문자열. ⚠️ 이건 **AI 짐작**이다 — 녹음 소리가 아니라 글을 읽고 고른다. */
    val speaker: String,
    /** 사람이 읽을 한 문장. 머리말("고객:")은 떼어져 있다. */
    val text: String,
) {
    /** 시각·화자 다 떼고 문장만 — 통화 카드처럼 좁은 데서 쓴다. */
    val plain: String get() = text
}

object CallSummaryLines {

    private val OLD_HEADS = listOf("고객 고민:", "사장님 답:", "사장님:", "고객:")

    /**
     * 요약 덩어리를 **자르지 않은 원본 줄**로. (`0:00-0:46|나|…` 그대로)
     *
     * ⚠️ 화면에 그릴 땐 **이걸** 넘겨야 한다. [parse] 로 읽은 뒤 `.map { it.text }` 한 걸
     *   넘기면 시각·화자가 이미 떨어져 나가서, 그리는 쪽이 아무리 잘 그려도 시각이 안 나온다.
     *   (2026-09-25 실사고: 그리는 곳을 한 군데로 합쳐놓고 **깎은 글을 넘겨서** 또 안 나왔다)
     */
    fun rawLines(summaryText: String?): List<String> =
        (summaryText ?: "").split("\n").map { it.trim() }.filter { it.isNotBlank() }

    /** 요약 덩어리(줄바꿈으로 이어진 것)를 줄 단위로. */
    fun parse(summaryText: String?): List<CallSummaryLine> =
        (summaryText ?: "").split("\n")
            .mapNotNull { parseOne(it) }

    /** 한 줄 파싱. 빈 줄이면 null. */
    fun parseOne(raw: String?): CallSummaryLine? {
        val s = (raw ?: "").trim()
        if (s.isBlank()) return null

        // 새 모양 — 0:00-0:35|손님|문장
        if (s.count { it == '|' } >= 2) {
            val a = s.indexOf('|')
            val b = s.indexOf('|', a + 1)
            val time = s.substring(0, a).trim()
            val who = s.substring(a + 1, b).trim()
            val text = s.substring(b + 1).trim()
            if (text.isNotBlank()) {
                return CallSummaryLine(
                    time = if (TIME_RANGE.matches(time)) time else "",
                    speaker = if (who == "손님" || who == "나") who else "",
                    text = text,
                )
            }
        }

        // 옛 모양 — "고객: …" / "사장님 답: …"
        for (h in OLD_HEADS) {
            if (s.startsWith(h)) {
                val body = s.removePrefix(h).trim()
                if (body.isBlank()) return null
                return CallSummaryLine(
                    time = "",
                    speaker = if (h.startsWith("고객")) "손님" else "나",
                    text = body,
                )
            }
        }
        return CallSummaryLine(time = "", speaker = "", text = s)
    }

    /** `m:ss-m:ss` 또는 `m:ss` 만 시각으로 인정한다 — 모델이 엉뚱한 걸 넣어도 안 믿는다. */
    private val TIME_RANGE = Regex("""^\d{1,3}:\d{2}(-\d{1,3}:\d{2})?$""")

    /** 구간의 **시작 밀리초** — 눌러서 그 대목부터 재생할 때. 없으면 null. */
    fun startMsOf(line: CallSummaryLine): Long? {
        val head = line.time.substringBefore("-").trim()
        val p = head.split(":")
        if (p.size != 2) return null
        val m = p[0].toLongOrNull() ?: return null
        val sec = p[1].toLongOrNull() ?: return null
        if (sec >= 60) return null
        return (m * 60 + sec) * 1000L
    }

    /** 좁은 자리(통화 카드·목록)용 — 머리말·시각 뗀 첫 문장. */
    fun firstSentence(summaryText: String?, max: Int = 90): String? {
        val first = parse(summaryText).firstOrNull() ?: return null
        val t = first.text.trim()
        if (t.isBlank()) return null
        return if (t.length > max) t.take(max - 1).trimEnd() + "…" else t
    }
}
