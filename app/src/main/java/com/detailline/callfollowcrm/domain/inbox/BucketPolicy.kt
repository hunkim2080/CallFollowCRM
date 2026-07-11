package com.detailline.callfollowcrm.domain.inbox

import com.detailline.callfollowcrm.data.local.entity.ThreadBucketEntity

/**
 * 분류 결과를 기존 분류와 합치는 우선순위 정책(순수 함수). (2026-07-11 사장님)
 *
 * 핵심 비대칭:
 *   - 상담함(CONSULT)으로의 '승격'은 싸고 안전(고객을 안 놓침).
 *   - 문자함(GENERAL)으로의 '강등'은 위험(고객을 숨김) → 확실 신호일 때만.
 * 신뢰도: OWNER(사장님) > HAIKU(서버) > LOCAL(폰). **OWNER 결정은 어떤 자동 분류도 못 뒤집는다.**
 *
 * 영속 최소화: 기본(행 없음)=상담함. 그래서
 *   - GENERAL 은 행을 쓴다(상담함에서 빼려면 표식 필요).
 *   - 자동 CONSULT 승격은 기존 GENERAL 행을 '삭제'(=기본 상담함 복귀).
 *   - OWNER CONSULT 는 행을 남긴다(자동 재강등 방지 보호막).
 */
object BucketPolicy {
    const val CONSULT = "CONSULT"
    const val GENERAL = "GENERAL"

    const val SRC_LOCAL = "LOCAL"
    const val SRC_HAIKU = "HAIKU"
    const val SRC_OWNER = "OWNER"

    sealed interface Action {
        data class Write(val entity: ThreadBucketEntity) : Action
        object Delete : Action
        object Noop : Action
    }

    /**
     * @param existing 현재 행(없으면 null)
     * @param verdictBucket 새 판정 버킷(CONSULT/GENERAL)
     * @param source 판정 주체
     */
    fun decide(
        existing: ThreadBucketEntity?,
        suffix: String,
        verdictBucket: String,
        source: String,
        reason: String?,
        bodyHash: Int?,
        now: Long
    ): Action {
        // OWNER 결정은 자동(LOCAL/HAIKU)이 절대 못 덮는다.
        if (existing?.source == SRC_OWNER && source != SRC_OWNER) return Action.Noop

        return when (verdictBucket) {
            GENERAL -> {
                // 이미 같은 내용으로 GENERAL 이면 재기록 불필요(Haiku 재호출 방지의 근거이기도).
                if (existing?.bucket == GENERAL &&
                    existing.classifiedBodyHash == bodyHash &&
                    rank(existing.source) >= rank(source)
                ) {
                    Action.Noop
                } else {
                    Action.Write(ThreadBucketEntity(suffix, GENERAL, source, reason, now, bodyHash))
                }
            }
            CONSULT -> {
                if (source == SRC_OWNER) {
                    // 사장님이 "고객 맞다" → 보호막 행 남김.
                    Action.Write(ThreadBucketEntity(suffix, CONSULT, SRC_OWNER, reason, now, bodyHash))
                } else {
                    // 자동 승격: 기존 GENERAL 행 제거 → 기본(상담함) 복귀. 행 없으면 할 일 없음.
                    if (existing != null) Action.Delete else Action.Noop
                }
            }
            else -> Action.Noop
        }
    }

    private fun rank(source: String): Int = when (source) {
        SRC_OWNER -> 3
        SRC_HAIKU -> 2
        else -> 1
    }
}
