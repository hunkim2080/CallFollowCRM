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
    /** 단계 이름 한 곳 — 글자 막대([line])와 커스텀 알림 스텝 그림이 같은 이름을 쓴다(두 벌 금지 §12). */
    val labels = listOf("수락", "출발", "도착", "완료")

    /**
     * 진행 막대 한 줄 — 도달한 단계까지 ●, 아직은 ○.
     *   예) reached=2 → "● 수락 ─ ● 출발 ─ ○ 도착 ─ ○ 완료"
     *   reached 는 1~4(0 이하면 빈 문자열 — 단계 아님).
     */
    fun line(reached: Int): String {
        if (reached <= 0) return ""
        return labels.mapIndexed { i, label ->
            val stepOrder = i + 1
            (if (stepOrder <= reached) "●" else "○") + " " + label
        }.joinToString(" ─ ")
    }

    /** 점 하나의 상태 — 지난 단계(DONE)·지금(CURRENT)·아직(PENDING)·완료(마지막 단계 도달 DONE_FINAL). */
    enum class DotState { PENDING, CURRENT, DONE, DONE_FINAL }

    /** 스텝 그림 한 칸 — 라벨·점 상태·좌/우 연결 막대 켜짐. 양 끝 동강(cell0 왼쪽·cell3 오른쪽)은 막대 없음(false). */
    data class StepCell(
        val label: String,
        val dot: DotState,
        val barLeftOn: Boolean,
        val barRightOn: Boolean,
    )

    /**
     * reached(1~4)까지 찬 스텝 그림 네 칸 — **푸시(RemoteViews)와 알림센터(Compose)가 같이 쓰는 한 곳**(두 벌 금지 §12).
     *   점: order<reached 초록✓(DONE) · 마지막 단계 도달 보라✓(DONE_FINAL) · 지금 단계 초록테두리(CURRENT) · 아직 회색(PENDING).
     *   막대: 구간 s(점 s↔s+1)는 reached≥s+1 이면 켜짐. 그래서 cell 의 왼막대=order>1 && reached≥order, 오른막대=order<MAX && reached≥order+1.
     */
    fun cells(reached: Int): List<StepCell> = (1..MAX).map { order ->
        val dot = when {
            order < reached -> DotState.DONE
            order == reached && order == MAX -> DotState.DONE_FINAL
            order == reached -> DotState.CURRENT
            else -> DotState.PENDING
        }
        StepCell(
            label = labels[order - 1],
            dot = dot,
            barLeftOn = order > 1 && reached >= order,
            barRightOn = order < MAX && reached >= order + 1,
        )
    }
}
