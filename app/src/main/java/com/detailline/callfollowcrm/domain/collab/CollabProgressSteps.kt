package com.detailline.callfollowcrm.domain.collab

/**
 * 🤝 협업 진행 알림을 **현장마다 하나로** 묶어 **단계로** 보여주기 (쿠팡 배송추적 느낌). (2026-11-05 사장님)
 *
 *   흩어진 문제: 지금은 수락/출발/도착/완료가 **이벤트마다 새 알림 줄**로 쌓여 지저분하다.
 *   바꿈: 같은 현장(shareId)은 **알림 하나**가 제자리에서 단계만 앞으로. 본문에 진행 막대를 한 줄 붙인다.
 *
 *   거절(declined)은 단계가 아니라 **멈춤** — 여기 order=0, 알림도 별도(§ NotificationHelper).
 *   순수 셈 — 어디까지 왔나(reached)의 기억은 부르는 쪽(NotificationHelper)이 prefs 로 든다. 테스트: CollabProgressStepsTest.
 */
object CollabProgressSteps {

    /** 단계 순서 — 수락 1 < 출발 2 < 도착 3 < 완료 4. 그 외(거절·모름)=0. */
    fun orderOf(kind: String): Int = when (kind) {
        "accepted" -> 1
        "departed" -> 2
        "arrived" -> 3
        "completed" -> 4
        else -> 0
    }

    const val MAX = 4
    private val LABELS = listOf("수락", "출발", "도착", "완료")

    /**
     * 진행 막대 한 줄 — 도달한 단계까지 ●, 아직은 ○.
     *   예) reached=2 → "● 수락 ─ ● 출발 ─ ○ 도착 ─ ○ 완료"
     *   reached 는 1~4(0 이하면 빈 문자열 — 단계 아님).
     */
    fun line(reached: Int): String {
        if (reached <= 0) return ""
        return LABELS.mapIndexed { i, label ->
            val stepOrder = i + 1
            (if (stepOrder <= reached) "●" else "○") + " " + label
        }.joinToString(" ─ ")
    }
}
