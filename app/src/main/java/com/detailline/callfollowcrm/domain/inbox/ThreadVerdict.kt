package com.detailline.callfollowcrm.domain.inbox

/**
 * 서버(Haiku) 분류 응답 한 건 — 상담함(consult) / 문자함(general). (2026-10-15 Phase 2 앱 연결)
 *
 *   순수 데이터 + 안전한 만들기(`of`). JSON 은 ai/ThreadClassifyServerRepository 가 풀고,
 *   빠진 칸·이상한 값은 전부 여기서 **상담함(consult)** 으로 떨어진다 — 고객을 숨기는 쪽이 치명적이라.
 */
data class ThreadVerdict(
    val bucket: String,
    val confidence: Double,
    val reason: String
) {
    /**
     * 문자함으로 보내도 되는가 — general **이고** 확신이 문턱 이상일 때만.
     *   서버도 같은 문턱으로 걸러 보내지만(`_classify_safe_result`), 옛/다른 서버가 날것으로 보내도
     *   앱이 한 번 더 지킨다 (§12-I 겹겹의 문 — 앱은 미리 거르는 쪽, 서버가 최종).
     */
    val isGeneral: Boolean get() = bucket == BUCKET_GENERAL && confidence >= GENERAL_MIN_CONFIDENCE

    companion object {
        const val BUCKET_GENERAL = "general"
        const val BUCKET_CONSULT = "consult"

        /** 서버 `server/main.py` `_classify_safe_result` 의 0.9 와 같은 숫자 — 저쪽이 바뀌면 여기도. */
        const val GENERAL_MIN_CONFIDENCE = 0.9

        /** 응답이 없거나 이상할 때의 안전값 — 상담함 유지. */
        val CONSULT_FALLBACK = ThreadVerdict(BUCKET_CONSULT, 0.0, "분류 못 함 — 상담함 유지")

        /**
         * 빠진 칸·이상한 값을 안전하게 — bucket 이 "general" 이 아니면 전부 consult,
         * confidence 는 0.0~1.0 으로 자르고 숫자가 아니면 0.0.
         */
        fun of(bucket: String?, confidence: Double?, reason: String?): ThreadVerdict {
            val b = if (bucket?.trim()?.lowercase() == BUCKET_GENERAL) BUCKET_GENERAL else BUCKET_CONSULT
            val c = confidence?.takeIf { !it.isNaN() }?.coerceIn(0.0, 1.0) ?: 0.0
            val r = reason?.trim().orEmpty().take(60)
            return ThreadVerdict(b, c, r)
        }
    }
}
