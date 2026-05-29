package com.detailline.callfollowcrm.ai

/**
 * 고객이 마지막으로 보낸 메시지에 대해 맥미니 LLM 이 미리 준비한 답변 후보.
 *
 * 신선도 판정용: basedOnReceivedAtMs == 화면이 보고 있는 최신 수신 메시지 timestamp 여야 유효.
 * 그보다 더 늦게 도착한 메시지가 있으면 stale → 사장님이 ↻ 눌러서 재생성해야 함.
 *
 * 2026-05-28 v2 (킬러 콘텐츠 1단계 — 의도 분화):
 *   suggestions: List<String> → List<ReplyChoice> 로 확장.
 *   서버 응답에 scenario / scenario_confidence / scenario_reason 포함 (UI 미노출, 로깅용).
 *   옛 스키마 (List<String>) 가 들어오면 parser 가 ReplyChoice(intentKey="", label=null) 로 래핑.
 */
data class ReplySuggestions(
    val phone: String,
    val basedOnMessage: String,
    val basedOnReceivedAtMs: Long,
    val generatedAtMs: Long,
    val suggestions: List<ReplyChoice>,
    /** AI 가 분류한 시나리오 key (e.g., "price_inquiry", "fallback_default"). 없으면 null. */
    val scenario: String? = null,
    /** 0.0~1.0. 낮으면 fallback_default 로 빠졌을 가능성. 측정용. */
    val scenarioConfidence: Double? = null,
    /** AI 가 이 시나리오로 분류한 한 줄 이유. 로그/디버그용. */
    val scenarioReason: String? = null
)

/**
 * 답변 한 개 = 상담 전략 한 개.
 *
 * - text: 사장님이 chip 탭 시 composer 에 채워질 본문.
 * - label: chip 상단에 보일 짧은 라벨 ("💰 견적 안내"). 옛 스키마면 null → UI 가 chip 숨김.
 * - intentKey: 어떤 의도인지 식별 ("quote", "info", "booking" 등). 추후 분석/로깅용.
 * - why: AI 가 이 답변을 추천한 이유. UI 미노출, 추후 학습 loop / 품질 개선용.
 */
data class ReplyChoice(
    val text: String,
    val label: String? = null,
    val intentKey: String? = null,
    val why: String? = null
)

enum class SuggestionStatus {
    /** 캐시에 준비된 답변 있음. suggestions != null. */
    READY,
    /** 서버가 LLM 호출 중. suggestions == null. */
    GENERATING,
    /** 캐시에 없음 (서버가 트리거 못 받았거나 캐시 만료). 사장님 ↻ 로 재시도. */
    MISSING
}

data class SuggestionFetchResult(
    val status: SuggestionStatus,
    val suggestions: ReplySuggestions?
)

/** 서버에 보내는 prepare 컨텍스트. */
data class PrepareContext(
    val phone: String,
    val latestMessage: String,
    val latestMessageReceivedAtMs: Long,
    val recentHistory: List<HistoryMessage>,
    val customer: CustomerHint?,
    /**
     * 사장님 톤 코퍼스 — 사장님이 다른 고객들에게 보낸 최근 메시지들 (sent=true 만).
     * 서버가 시스템 프롬프트의 "사장님 톤 예시" 섹션에 few-shot 으로 박는다.
     * recentHistory 와 별개. 이 고객과의 대화가 아닌, 사장님 톤 학습용 일반 샘플.
     */
    val ownerToneSamples: List<String> = emptyList(),
    /**
     * P3 — 사장님의 다른 시공 일정 (현재 고객 제외, 앞으로 14일 내).
     * 고객 이름은 leak 되면 안 되므로 epoch ms 만 보냄.
     * 서버는 "토요일 가능?" 같은 일정 질문에 이 정보를 근거로 답변.
     */
    val otherUpcomingSchedulesMs: List<Long> = emptyList()
)

data class HistoryMessage(
    /** "customer" 또는 "owner" */
    val role: String,
    val body: String,
    val timestampMs: Long
)

data class CustomerHint(
    val name: String?,
    val memo: String?,
    /** "HOT" | "WARM" | "COLD" 등 leadHeat enum name */
    val leadHeat: String?,
    val depositPaid: Boolean,
    /** P3 — 이 고객의 시공 예약일 (있으면). 일정 관련 답변 추천에서 "확정된 일정 기준" 으로 사용. */
    val scheduledWorkDateMs: Long? = null
)

/**
 * 2026-05-30 사장님 #2 통점 — MMS 분할 / 짧은 SMS 다발 시 latestMessage 묶음 헬퍼.
 *
 * 사장님 의도: "내 말풍선 끝나고 고객 말풍선을 전부 확인하고 답변 준비".
 * 즉 "마지막 owner 발신 이후 모든 customer 수신 메시지" 를 join 해서 latestMessage 로 보냄.
 *
 * 옛 동작 = latestMessage 가 마지막 받은 메시지 1개 → LLM 이 맥락 없이 답변.
 * 새 동작 = streak 전체 묶음 → LLM 이 전체 맥락 보고 답변.
 */
object PrepareContextHelpers {
    /**
     * @param history 옛→최신 순으로 정렬된 메시지 (SmsReceiver/MmsDownloadService/ChatViewModel 패턴).
     * @param newIncomingBody history 에 아직 안 들어간 막 도착한 메시지 본문.
     *                        null = history 가 이미 다 포함 (regenerate 케이스).
     * @return 마지막 owner 이후의 customer 본문들 + newIncomingBody 를 \n\n 으로 join.
     *         streak 비어있으면 newIncomingBody, 그것도 null 이면 history 마지막 메시지 body.
     */
    fun joinCustomerStreakAfterLastOwner(
        history: List<HistoryMessage>,
        newIncomingBody: String? = null
    ): String {
        val streak = mutableListOf<String>()
        for (i in history.indices.reversed()) {
            if (history[i].role == "owner") break
            val body = history[i].body
            if (body.isNotBlank()) streak.add(0, body)
        }
        val all = if (newIncomingBody != null) streak + newIncomingBody else streak
        val joined = all.joinToString(separator = "\n\n").trim()
        return joined.ifBlank { newIncomingBody ?: history.lastOrNull()?.body.orEmpty() }
    }
}
