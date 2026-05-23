package com.detailline.callfollowcrm.ai

/**
 * Phase 4 AI 요약 인터페이스.
 *
 * 절대 v1 에서 구현하지 않는 것:
 *  - 실제 OpenAI/Anthropic 등 외부 API 직접 호출
 *  - API 키를 앱에 저장
 *  - STT 자체 처리
 *
 * 올바른 구조:
 *  Android 앱 → 사용자 서버 → STT → 요약 → 서버 DB → 앱
 */
interface AiSummaryRepository {
    suspend fun requestSummary(request: AiSummaryRequest): Result<AiSummaryResult>
}

class NoOpAiSummaryRepository : AiSummaryRepository {
    override suspend fun requestSummary(request: AiSummaryRequest): Result<AiSummaryResult> {
        // TODO: Phase 4 — 사용자 서버에 요약 요청을 보낸다. v1 에서는 구현하지 않는다.
        return Result.failure(UnsupportedOperationException("AI summary not available in v1"))
    }
}
