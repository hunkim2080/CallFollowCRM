package com.detailline.callfollowcrm.ai

import kotlinx.coroutines.flow.Flow

/**
 * 한국어 문장 다듬기 백엔드 추상화 (RINGGO_BACKEND_BRIEF.md 권장 시그니처).
 *
 * 현재 구현: [OllamaRefineRepository] — 맥미니 Tailnet 100.86.114.49:11434.
 * 향후: GeminiRefineRepository 등으로 교체 가능. AppContainer 의 바인딩만 갈아끼우면 됨.
 */
interface RefineRepository {
    /** 한 번에 결과 받기. 실패 시 Result.failure. */
    suspend fun refine(input: String, system: String? = null): Result<String>

    /** 토큰 단위 스트리밍. 현재는 non-stream 결과를 1회 emit (다음 마일스톤에서 실제 NDJSON 파싱). */
    fun refineStream(input: String, system: String? = null): Flow<String>
}
