package com.detailline.callfollowcrm.domain.intake

/**
 * 접수서가 들어왔을 때 **시공일을 일정(캘린더/건)에 넣은 결과**. (2026-10-21 사장님)
 *   사장님이 "접수서는 들어왔는데 일정이 자동 등록됐는지 안 보여 겁난다"고 하여,
 *   그 결과를 `IntakeEventEntity.scheduleState` 에 담아 채팅 접수 카드에 또렷이 보인다.
 *
 * - 쓰는 곳(판정·저장): `ai/IntakeSyncManager.kt`
 * - 읽는 곳(카드 표시): `presentation/screen/chat/ChatTimelineSegments.kt` (IntakeSegment)
 *   두 곳이 **같은 글자(wire)** 를 쓰도록 여기 한 곳에서 정한다(§12 — 두 벌 금지).
 */
enum class IntakeScheduleState(val wire: String) {
    /** 접수서 날짜를 일정에 넣었다(또는 이미 같은 날로 잡혀 있었다). */
    REGISTERED("applied"),
    /** 이미 다른 날짜가 잡혀 있어 그대로 뒀다 — 사장님이 확인해야 한다. */
    KEPT("kept"),
    /** 접수서에 희망 시공일이 없었다 — 사장님이 잡아야 한다. */
    NONE("none");

    companion object {
        /** 저장된 글자 → 상태. 모르는/빈 값은 null(옛 행 = 표시 안 함). */
        fun ofOrNull(wire: String?): IntakeScheduleState? =
            wire?.let { w -> entries.firstOrNull { it.wire == w } }
    }
}
