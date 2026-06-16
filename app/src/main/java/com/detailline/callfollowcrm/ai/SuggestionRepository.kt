package com.detailline.callfollowcrm.ai

/**
 * 고객 답장 추천 백엔드 추상화.
 *
 * 현재 구현: [ServerSuggestionRepository] — 맥미니 자체 서버 호출.
 * RINGGO_SERVER_SPEC.md 의 POST /prepare-reply, GET /suggestions/{phone} 사용.
 *
 * 흐름:
 *   1. SMS 수신 → SmsReceiver → requestPrepare (fire-and-forget) → 서버가 백그라운드에서 LLM 호출
 *   2. 사장님이 ChatScreen 진입 → fetch → 캐시에서 즉시 반환
 *   3. 캐시 미존재(MISSING) → 사장님 ↻ 누르면 다시 requestPrepare + 잠시 후 fetch
 */
interface SuggestionRepository {
    /**
     * 서버에 답변 준비 요청 + 즉시 끊김. 서버는 200 응답 후 백그라운드에서 LLM 호출.
     * 실패해도 silent — 사장님이 ChatScreen 에서 fallback 으로 ↻ 누르면 재시도됨.
     */
    suspend fun requestPrepare(context: PrepareContext): Result<Unit>

    /**
     * 캐시 조회. 신선도 판정은 호출자가 basedOnReceivedAtMs 로 직접 비교.
     */
    suspend fun fetch(phone: String): Result<SuggestionFetchResult>

    /**
     * (Phase 2 — 원칙 발견) 추천 ≠ 사장님 실제 답이 확실할 때, 그 답에서 '왜 이렇게 했는지' 한 줄 원칙을
     *   서버 LLM 이 추론. fire-and-forget 아닌 동기 — 결과(후보)를 기다려 카드에 띄움.
     *   서버가 기존 원칙과 중복이거나 무의미하면 null 반환 → 앱은 카드 안 띄움.
     */
    suspend fun inferPrinciple(
        customerMessage: String,
        aiSuggestion: String,
        ownerReply: String,
        scenario: String?,
        existingPrinciples: List<String>,
        deviceId: String?,
        ownerTrade: String?
    ): Result<PrincipleCandidate?>
}
