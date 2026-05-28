package com.detailline.callfollowcrm.data.repository

import com.detailline.callfollowcrm.data.local.dao.SuggestionEventDao
import com.detailline.callfollowcrm.data.local.entity.SuggestionEventAction
import com.detailline.callfollowcrm.data.local.entity.SuggestionEventEntity

/**
 * 2026-05-29 킬러콘텐츠 3단계 — 채택/수정 데이터 수집 repository.
 *
 * 호출처:
 *   - ChatViewModel.sendMessage 성공 시 (SENT_AS_IS / EDITED / REFINED_THEN_SENT / IGNORED)
 *   - ChatViewModel.onCleared 또는 화면 떠날 때 (DISMISSED)
 *
 * 서버 batch 보고 (POST /api/suggestion-events) 는 다음 sprint 의 cowork 작업.
 * 현재는 클라이언트에 쌓아만 둠.
 */
class SuggestionEventRepository(
    private val dao: SuggestionEventDao
) {

    suspend fun record(event: SuggestionEventEntity): Long = dao.insert(event)

    /** Pending events (서버 보고 안 됨) — batch upload 용. */
    suspend fun pendingForUpload(limit: Int = 100): List<SuggestionEventEntity> =
        dao.pendingForUpload(limit)

    suspend fun markReported(ids: List<Long>) = dao.markReported(ids)

    /**
     * 채택률 / 평균 수정 거리 — Settings 의 "💡 추천 답변 통계" 카드 표시용 (다음 sprint).
     */
    suspend fun statsSince(sinceMs: Long): Stats {
        val counts = dao.actionCountsSince(sinceMs).associate { it.action to it.cnt }
        val total = dao.totalCountSince(sinceMs)
        val avgEdit = dao.avgEditDistanceSince(sinceMs) ?: 0.0
        val adopted = (counts[SuggestionEventAction.SENT_AS_IS] ?: 0) +
            (counts[SuggestionEventAction.REFINED_THEN_SENT] ?: 0)
        val edited = counts[SuggestionEventAction.EDITED] ?: 0
        val ignored = counts[SuggestionEventAction.IGNORED] ?: 0
        val dismissed = counts[SuggestionEventAction.DISMISSED] ?: 0
        return Stats(
            total = total,
            adopted = adopted,
            edited = edited,
            ignored = ignored,
            dismissed = dismissed,
            averageEditDistance = avgEdit
        )
    }

    data class Stats(
        val total: Int,
        val adopted: Int,
        val edited: Int,
        val ignored: Int,
        val dismissed: Int,
        val averageEditDistance: Double
    ) {
        /** "그대로 채택" 비율 (SENT_AS_IS + REFINED_THEN_SENT) / total. */
        val adoptionRate: Double get() = if (total == 0) 0.0 else adopted.toDouble() / total
    }

    // 2026-05-29 킬러콘텐츠 6단계 — 자동 학습 루프.

    /**
     * 시나리오별 채택률. 사장님이 어떤 시나리오에서 가장 만족하는지 발견.
     * 정렬 = 채택률 오름차순 (낮은 거 먼저 — 개선 우선순위).
     */
    suspend fun scenarioBreakdown(sinceMs: Long): List<ScenarioBreakdown> {
        return dao.scenarioStatsSince(sinceMs).map { row ->
            ScenarioBreakdown(
                scenario = row.scenario,
                total = row.total,
                adopted = row.adopted,
                avgEdit = row.avgEdit ?: 0.0
            )
        }.sortedBy { it.adoptionRate }
    }

    /**
     * intent_key 별 채택률 ranking. 어떤 의도를 사장님이 가장 그대로 보내는지.
     * 정렬 = total 내림차순 (자주 쓰는 거 먼저).
     */
    suspend fun intentBreakdown(sinceMs: Long): List<IntentBreakdown> {
        return dao.intentStatsSince(sinceMs).map { row ->
            IntentBreakdown(
                intentKey = row.intent_key,
                intentLabel = row.intent_label,
                total = row.total,
                adopted = row.adopted
            )
        }
    }

    data class ScenarioBreakdown(
        val scenario: String,
        val total: Int,
        val adopted: Int,
        val avgEdit: Double
    ) {
        val adoptionRate: Double get() = if (total == 0) 0.0 else adopted.toDouble() / total
        /** 채택률 40% 미만 = 개선 후보 (사장님 시나리오 답변에 가장 자주 수정/무시함). */
        val needsImprovement: Boolean get() = total >= 5 && adoptionRate < 0.4
    }

    data class IntentBreakdown(
        val intentKey: String,
        val intentLabel: String?,
        val total: Int,
        val adopted: Int
    ) {
        val adoptionRate: Double get() = if (total == 0) 0.0 else adopted.toDouble() / total
    }
}
