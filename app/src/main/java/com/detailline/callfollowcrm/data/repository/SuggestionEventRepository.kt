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
}
