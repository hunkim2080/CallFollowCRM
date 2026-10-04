package com.detailline.callfollowcrm.presentation.util

import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.OutboxRules

/**
 * 📮 우체통이 사장님께 하는 말 — **한 곳**(§7·§12 문구 두 벌 금지).
 *
 *   「저장 실패」(SaveGuard)와 「전송 보류」는 **다른 말**이다. 여기 것은 전부 **보류**다 —
 *   내 폰엔 됐고, 상대에게만 연결되면 간다. 빨간 실패가 아니다.
 */
object OutboxWording {

    /** B(협업자)가 그만둘 때. 내 일정에선 이미 빠졌고, A 에게는 연결되면 알린다. */
    const val leftCollab = "협업을 그만뒀어요 · 상대 사장님껜 연결되면 알려드려요"

    /** A 가 보낸 협업 요청을 취소/해제할 때. */
    const val cancelledCollab = "협업 요청을 취소했어요 · 상대 사장님껜 연결되면 알려드려요"

    fun label(kind: OutboxKind): String = when (kind) {
        OutboxKind.COLLAB_RESCHEDULE -> "협업 일정 변경"
        OutboxKind.COLLAB_ADDRESS -> "협업 주소 변경"
        OutboxKind.COLLAB_END -> "협업 해제"
        OutboxKind.SITE_PHOTO -> "현장 사진"
    }

    /** 저장은 됐고 지금 못 보냄(오프라인·서버 잠깐). 실패가 아니다. */
    fun queued(kind: OutboxKind): String =
        "${label(kind)}은 연결되면 자동으로 보내요"

    /** 하루 넘게 못 보냄(홈 띠·2단계). */
    fun stuck(kind: OutboxKind, createdAtMs: Long, now: Long): String {
        val days = OutboxRules.daysStuck(createdAtMs, now)
        val day = if (days <= 1) "하루째" else "${days}일째"
        return "${label(kind)}을 $day 못 보냈어요 — 눌러서 확인"
    }
}
