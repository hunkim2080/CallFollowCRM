package com.detailline.callfollowcrm.ai

/**
 * 한국어 문장 다듬기 ✨ 백엔드 추상화.
 *
 * 2026-05-28 사장님 결정 — Gemini API 로 교체 + 컨텍스트 전송으로 품질 향상.
 *   기존 raw 1문장만 전송 → 답변이 generic. 사장님 통점.
 *   새: 사장님이 친 raw + 최근 대화 + 사장님 톤 샘플 + 고객 hint → 그 흐름에 맞는 자연스러운 다듬기.
 *
 * 구현: [com.detailline.callfollowcrm.ai.RemoteRefineRepository] — 맥미니 FastAPI `POST /api/refine`.
 *   서버 endpoint 안에서 실제 LLM (Gemini 2.5 Flash 등) 호출. API 키는 Mac mini 만 보유.
 *
 * Rollback: [OllamaRefineRepository] (gpt-oss:20b) — 코드 유지, 미사용.
 */
/**
 * 다듬기가 **구글 무료 한도**에 걸렸을 때. 고장이 아니라 "오늘 몫을 다 쓴 것".
 *   전엔 이것도 그냥 실패로 뭉뚱그려 "다듬기에 실패했어요" 만 떠서 고장으로 오해했다.
 *   (2026-09-18 사장님: "실패함이 아니라 무료 한도 끝 이러던지, 안내가 달라야 당황을 안 한다")
 *
 * @param daily true = 오늘 하루치 소진(내일 풀림) · false = 순간 몰림(잠시 뒤 됨)
 */
class RefineQuotaException(val daily: Boolean) : java.io.IOException(
    if (daily) "오늘 다듬기 한도를 다 썼어요" else "지금 다듬기가 몰려요"
)

interface RefineRepository {
    /**
     * 다듬기 호출. 실패 시 Result.failure.
     *
     * @param input 사장님이 친 raw 문장
     * @param context 대화 흐름 + 톤 + 고객 정보. 기본값 = 빈 컨텍스트 (raw 만 — 옛 동작).
     */
    suspend fun refine(input: String, context: RefineContext = RefineContext()): Result<String>
}

/**
 * 다듬기 호출 컨텍스트 — 진짜 "사장님 톤" 으로 다듬으려면 이게 다 필요.
 *
 * @param recentMessages 최근 대화 (사장님 보냄 + 고객 받음 섞임, 최신순 reverse 권장)
 * @param ownerToneSamples 사장님이 다른 고객들에게 보낸 메시지 50건 = 톤 학습 코퍼스
 * @param customerName 고객 이름 (있으면 호칭으로 활용 가능)
 * @param customerMemo 사장님이 그 고객에 적은 메모
 */
data class RefineContext(
    val recentMessages: List<HistoryMessage> = emptyList(),
    val ownerToneSamples: List<String> = emptyList(),
    val customerName: String? = null,
    val customerMemo: String? = null
)
