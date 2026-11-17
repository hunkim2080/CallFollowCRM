package com.detailline.callfollowcrm.domain.search

/**
 * 초성(ㅅ→실리콘)·글자 둘 다 되는 한국어 검색 맞춤. (2026-11-16 사장님 — 견적 항목 검색)
 *
 * 견적 만들기에서 가격표 항목이 많으면 일일이 찾기 힘들어 "ㅅ"만 쳐도 "실리콘 시공"이 뜨게 하려는 것.
 * 연락처·토스가 하는 그 초성 검색과 같은 기본 UX. 순수 함수라 **단위 테스트가 대신 눈으로 본다**
 * (폰에서 글자 한 자씩 쳐보기 어려운 자리라 [HangulSearchTest] 에 실제 맞춤을 박아둠).
 */
object HangulSearch {

    // 가나다 초성 19개. 완성형 한글(가 0xAC00 ~ 힣 0xD7A3)의 초성 index = (code - 0xAC00) / 588.
    private val CHO = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /** 완성형 한글은 초성 한 글자로, 나머지(영문·숫자·낱자음)는 그대로 둔다. */
    fun initials(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = ch.code
            if (c in 0xAC00..0xD7A3) sb.append(CHO[(c - 0xAC00) / 588])
            else sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * text 가 query 에 맞나 — 빈 query 는 전부 맞음(= 검색 안 함, 목록 그대로).
     *   ① 글자 포함: "실리" → "실리콘 시공"
     *   ② 초성 포함: "ㅅ"·"ㅅㄹ" → "실리콘 시공"
     *   공백은 양쪽 다 무시해 "벽면 줄눈"을 "벽면줄눈"·"ㅂㅁㅈㄴ"으로도 찾는다.
     */
    fun matches(text: String, query: String): Boolean {
        val q = query.filterNot { it.isWhitespace() }
        if (q.isEmpty()) return true
        val t = text.filterNot { it.isWhitespace() }
        if (t.contains(q, ignoreCase = true)) return true   // 글자 매칭
        return initials(t).contains(q)                       // 초성 매칭
    }
}
