package com.detailline.callfollowcrm.recording

import java.util.Calendar

/**
 * 에이닷 통화요약 화면의 공유(↑) 버튼으로 들어온 text/plain 페이로드를 파싱한다.
 *
 * 실제 공유 텍스트의 정확한 포맷은 에이닷 버전마다 달라질 수 있어 정규식 기반으로
 * 핵심 필드만 추출하고, 알아내지 못한 부분은 null 로 둔다. 원본 전체는 rawText 로 보존.
 *
 * 스크린샷 기준으로 잡아내는 정보:
 *  - 제목 한 줄 (예: "위생도기 AS 비용 조정 요청 논의")
 *  - 통화 일시 (예: "5. 15.(금) 오전 11:16")
 *  - 통화 상대 번호 (예: "010-8798-8685")
 *  - 상세 요약 블록 (타임스탬프 + 줄)
 *  - 녹음 내용(transcript) 블록
 *
 * 매칭 실패는 죽지 않는다 — 텍스트는 항상 rawText 로 저장된다.
 */
object AdotShareTextParser {

    data class Parsed(
        val title: String?,
        val phoneNumber: String?,   // 숫자만 (01087988685)
        val recordedAt: Long?,      // epoch ms
        val summaryText: String?,   // "상세 요약" 블록
        val transcriptText: String?,// "녹음 내용" 블록
        val rawText: String         // 원본 전체
    )

    private val phoneRegex = Regex("""01[0-9][\s\-]?\d{3,4}[\s\-]?\d{4}""")
    // "5. 15." / "5.15." / "5월 15일" / "2026. 5. 15." / "2026-05-15"
    private val dateRegex1 = Regex("""(\d{4})[.\-/\s]+(\d{1,2})[.\-/\s]+(\d{1,2})""")
    private val dateRegex2 = Regex("""(\d{1,2})[.\-/\s]+(\d{1,2})[.\-/\s]*(?:\(|$|[^\d])""")
    private val dateRegex3 = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
    // "오전 11:16" / "11:16" / "오후 2시 5분" / "14:30"
    private val timeRegex1 = Regex("""(오전|오후)\s*(\d{1,2})\s*[:시]\s*(\d{1,2})""")
    private val timeRegex2 = Regex("""(\d{1,2}):(\d{2})""")

    fun parse(text: String): Parsed {
        val raw = text.trim()
        if (raw.isEmpty()) return Parsed(null, null, null, null, null, raw)

        val phone = phoneRegex.find(raw)?.value?.filter { it.isDigit() }
        val recordedAt = extractDateTime(raw)
        val title = extractTitle(raw)
        val summary = extractBlock(raw, listOf("상세 요약", "상세요약", "요약"))
        val transcript = extractBlock(raw, listOf("녹음 내용", "녹음내용", "통화 내용", "통화내용"))

        return Parsed(
            title = title,
            phoneNumber = phone,
            recordedAt = recordedAt,
            summaryText = summary,
            transcriptText = transcript,
            rawText = raw
        )
    }

    private fun extractTitle(raw: String): String? {
        // 첫 번째 의미 있는 줄을 제목으로. 날짜/번호 줄은 건너뜀.
        for (line in raw.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            // 날짜만 있는 줄, 번호만 있는 줄, '님과의 통화' 줄은 제외
            if (phoneRegex.containsMatchIn(t)) continue
            if (dateRegex1.containsMatchIn(t) || dateRegex3.containsMatchIn(t)) continue
            if (t.contains("님과의 통화")) continue
            if (t.length > 80) return t.take(80)
            return t
        }
        return null
    }

    private fun extractDateTime(raw: String): Long? {
        val cal = Calendar.getInstance()
        val now = Calendar.getInstance()

        // 1) 연-월-일 (있으면)
        val m1 = dateRegex1.find(raw)
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        if (m1 != null) {
            year = m1.groupValues[1].toIntOrNull()
            month = m1.groupValues[2].toIntOrNull()
            day = m1.groupValues[3].toIntOrNull()
        } else {
            val m3 = dateRegex3.find(raw)
            if (m3 != null) {
                month = m3.groupValues[1].toIntOrNull()
                day = m3.groupValues[2].toIntOrNull()
            } else {
                val m2 = dateRegex2.find(raw)
                if (m2 != null) {
                    month = m2.groupValues[1].toIntOrNull()
                    day = m2.groupValues[2].toIntOrNull()
                }
            }
            year = now.get(Calendar.YEAR)
        }
        if (month == null || day == null || year == null) return null
        if (month !in 1..12 || day !in 1..31) return null

        // 2) 시간
        var hour: Int? = null
        var minute: Int? = null
        val t1 = timeRegex1.find(raw)
        if (t1 != null) {
            val ampm = t1.groupValues[1]
            val h = t1.groupValues[2].toIntOrNull() ?: return null
            val mi = t1.groupValues[3].toIntOrNull() ?: return null
            hour = when {
                ampm == "오후" && h < 12 -> h + 12
                ampm == "오전" && h == 12 -> 0
                else -> h
            }
            minute = mi
        } else {
            val t2 = timeRegex2.find(raw)
            if (t2 != null) {
                hour = t2.groupValues[1].toIntOrNull()
                minute = t2.groupValues[2].toIntOrNull()
            }
        }
        if (hour == null || minute == null) return null
        if (hour !in 0..23 || minute !in 0..59) return null

        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        return cal.timeInMillis
    }

    /**
     * 본문에서 라벨로 시작하는 섹션을 추출. 다음 라벨(또는 안내문)이 나오기 전까지를 본문으로.
     */
    private fun extractBlock(raw: String, labels: List<String>): String? {
        val lines = raw.lines()
        var startIdx = -1
        for ((i, line) in lines.withIndex()) {
            val t = line.trim()
            if (labels.any { t == it || t.startsWith("$it ") || t.startsWith("$it:") }) {
                startIdx = i + 1
                break
            }
        }
        if (startIdx < 0) return null

        val stopLabels = listOf("상세 요약", "상세요약", "녹음 내용", "녹음내용", "통화 내용",
                                "통화내용", "AI 제안", "새로운 할 일")
        val collected = mutableListOf<String>()
        for (j in startIdx until lines.size) {
            val t = lines[j].trim()
            if (stopLabels.any { it != labels.first() && (t == it || t.startsWith("$it ")) }) break
            if (t.startsWith("AI가 자동 생성한 정보로")) break
            collected += lines[j]
        }
        val joined = collected.joinToString("\n").trim()
        return joined.takeIf { it.isNotEmpty() }
    }
}
