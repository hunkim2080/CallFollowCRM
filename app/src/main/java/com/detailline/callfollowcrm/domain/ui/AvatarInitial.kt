package com.detailline.callfollowcrm.domain.ui

/**
 * 🔤 **이름 동그라미(아바타)에 적을 글자 · 색 번호** — 셈은 여기 한 곳. Android 의존성 없음([AvatarInitialTest]).
 *
 *   전엔 여섯 화면이 제각각이었다(§12-B 로 셈, 2026-10-17):
 *     · 고객 목록 `avatarInitial` — 이모지·기호를 건너뛰고 읽을 글자를 찾음(제일 낫다)
 *     · 상담함 `name.trim().first()` · 연락처 `name.take(1)` — 💖로 시작하면 **?** 가 떴던 그 셈
 *     · 검색·팀·정산 `replace("[\\s()]").take(1)` — 괄호만 지움
 *   같은 「첫 글자」인데 네 벌이라 한 곳(고객 목록)만 고쳐지고 나머지는 옛 모양으로 남아 있었다.
 */
object AvatarInitial {

    /**
     * 공백·괄호를 지운 뒤 **읽을 수 있는 첫 글자**(한글·영문·숫자). 없으면 "" → 호출부가 사람 아이콘을 그린다.
     *   "💖사랑하는 김손님" → "사", "(주)디테일라인" → "주", "😀" → "".
     */
    fun of(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val cleaned = name.replace(Regex("[\\s()]"), "")
        val ch = cleaned.firstOrNull { it.isLetterOrDigit() }
        return ch?.toString() ?: ""
    }

    /**
     * 업체(상호)의 **브랜드 첫 글자**. (2026-10-17 사장님 프로토 — 협업 「업체별」 동그라미)
     *   협업 상대 이름은 "김사장, 하우스픽" 처럼 **「사람, 상호」** 꼴로 오기도 한다 — 그러면 마지막 쉼표 뒤가 상호다.
     *   "김사장, 하우스픽" → "하" · "해시줄눈" → "해" · "태그줄눈, " → (뒤가 비면 전체로) "태".
     */
    fun brand(name: String?): String {
        if (name.isNullOrBlank()) return ""
        val afterComma = name.substringAfterLast(',', "").trim()
        val picked = if (name.contains(',') && afterComma.isNotBlank()) afterComma else name
        return of(picked).ifBlank { of(name) }
    }

    /**
     * 이름으로 정하는 **색 번호** — 같은 이름이면 언제나 같은 색(0 until [size]).
     *   해시는 글자 하나하나로 직접 돌린다 — `String.hashCode` 와 같은 식(31 곱)이지만 **여기에 적어 두어** 플랫폼이 바뀌어도 색이 안 바뀐다.
     */
    fun tintIndex(name: String?, size: Int): Int {
        if (size <= 0) return 0
        val key = name?.trim().orEmpty()
        var h = 0
        for (ch in key) h = 31 * h + ch.code
        return ((h % size) + size) % size
    }
}
