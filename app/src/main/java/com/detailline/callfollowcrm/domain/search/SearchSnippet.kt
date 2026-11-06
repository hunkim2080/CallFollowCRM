package com.detailline.callfollowcrm.domain.search

/**
 * 검색어 둘레를 잘라 **한 줄 스니펫**으로(…앞 걸린말 뒤…). (2026-11-06 두 벌 합침)
 *
 * 전엔 `SearchViewModel` 에만 private 으로 있었다. 대화 안 통화 카드에서 "그 통화 어디가 걸렸나"를
 *   같은 모양으로 보여주려면 복사해야 했는데, 그게 §12 두 벌이다. 그래서 공용으로 뺐다.
 *   전역 검색(`SearchViewModel`)과 대화 안 통화 카드(`CallSegment`)가 **같은 자**를 쓴다.
 */
object SearchSnippet {

    /** 매칭 단어 주변만 잘라 스니펫으로. 못 찾으면 앞 60자. */
    fun around(body: String, q: String, window: Int = 22): String {
        val flat = body.replace("\n", " ").trim()
        val idx = flat.indexOf(q, ignoreCase = true)
        if (idx < 0) return flat.take(60)
        val start = (idx - window).coerceAtLeast(0)
        val end = (idx + q.length + window).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    /**
     * 여러 후보 글 중 **q 가 처음 걸리는 글**에서 스니펫 한 줄을 뽑는다. 아무 데도 없으면 null.
     *   통화 카드는 전사(진짜 말)를 맨 앞에 두고 요약·니즈·태그 순으로 넘겨준다 —
     *   SearchViewModel 이 "진짜 말을 맨 앞에" 두는 것과 같은 뜻. (2026-09-19 사장님)
     */
    fun firstHit(fields: List<String?>, q: String, window: Int = 22): String? {
        val query = q.trim()
        if (query.isEmpty()) return null
        for (f in fields) {
            val s = f?.trim().orEmpty()
            if (s.isNotEmpty() && s.contains(query, ignoreCase = true)) return around(s, query, window)
        }
        return null
    }
}
