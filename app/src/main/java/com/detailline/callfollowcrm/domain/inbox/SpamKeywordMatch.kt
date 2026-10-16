package com.detailline.callfollowcrm.domain.inbox

/**
 * 🔤 「이 단어가 있으면」 — 사장님이 적어둔 낱말이 본문에 들어 있으면 광고로 본다. (2026-10-16 사장님)
 *   프로토: docs/PROTOTYPES.md 「스팸·광고 거르기」 ③ 말로 거르기.
 *
 *   **매칭은 여기 한 곳**(§12). 저장은 AppPreferences.spamKeywords, 부르는 자리는 ThreadBucketRepository.classifyLocal.
 *
 *   셈:
 *   - 부분일치 — 낱말이 본문 **어디든** 들어 있으면 걸린다.
 *   - 띄어쓰기는 안 본다 — 「손실 지급」 과 「손실지급」 은 같은 말(광고는 띄어쓰기를 제멋대로 한다).
 *   - 영문은 소문자로 맞춰 본다(한글은 그대로). 빈 낱말·공백만인 낱말은 없는 셈.
 *   - **저장 고객·내가 답장한 번호는 그 낱말을 써도 안 가린다** — 단골 손님 보호(사장님 "모르는 번호만", §13② precision).
 *     지우는 게 아니라 문자함으로 **옮기기만** 한다(상담함에서 다시 풀 수 있다).
 */
object SpamKeywordMatch {

    /** 사장님이 적은 낱말 하나를 저장 모양으로 — 앞뒤 공백 떼고, 비면 null. (pref 저장·화면 추가가 같이 쓴다) */
    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** 본문에 낱말 중 하나라도 들어 있나. 번호·고객 여부는 안 본다 — 그건 [hit]. */
    fun matches(body: String, keywords: Set<String>): Boolean = firstMatch(body, keywords) != null

    /** 걸린 낱말(사장님이 적은 글자 그대로) — 없으면 null. 로그·사유 표시용. */
    fun firstMatch(body: String, keywords: Set<String>): String? {
        if (body.isBlank() || keywords.isEmpty()) return null
        val hay = fold(body)
        if (hay.isEmpty()) return null
        return keywords.asSequence()
            .mapNotNull { normalize(it) }
            .firstOrNull { kw -> fold(kw).let { it.isNotEmpty() && hay.contains(it) } }
    }

    /**
     * 「모르는 번호만」 — 저장 고객이거나 내가 답장한 적 있으면 낱말이 있어도 **안 걸린다.**
     * @return 걸린 낱말, 안 걸리면 null.
     */
    fun hit(body: String, keywords: Set<String>, isSavedCustomer: Boolean, hasOwnerReply: Boolean): String? {
        if (isSavedCustomer || hasOwnerReply) return null
        return firstMatch(body, keywords)
    }

    /** 비교용 접기 — 공백 전부 제거 + 소문자. */
    private fun fold(s: String): String = buildString(s.length) {
        for (c in s) if (!c.isWhitespace()) append(c.lowercaseChar())
    }
}
