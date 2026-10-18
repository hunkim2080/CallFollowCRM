package com.detailline.callfollowcrm.domain.ui

/**
 * 🍞 **확인 띠(스낵바)가 몇 초 떠 있나** — 셈은 여기 한 곳.
 *
 * 사장님(2026-10-19): 확인 띠가 화면을 가리고 **4초나 떠서 다음 동작을 막는다** → 짧고·가볍게.
 *   · 그냥 「됐어요」 확인 → **1.5초** — 읽히면 바로 치운다.
 *   · 「못」·「실패」·「안 돼」·「다시」·「오류」·「없어요」 가 들어간 **실패·주의** 말 → **3초** — 놓치면 안 된다.
 *   · 「되돌리기」 같은 **누를 것이 붙은** 띠 → **3초** — 손이 갈 시간은 줘야 한다.
 *
 * 순수 함수라 폰 없이 단위 테스트([SnackbarTimingTest])가 본다.
 * 입구는 `presentation/util/AppSnackbar.kt` 의 `SnackbarHostState.showBrief()` 하나.
 */
object SnackbarTiming {
    /** 확인용 — 읽히면 치운다. */
    const val BRIEF_MS = 1_500L
    /** 실패·주의·누를 것 있음 — 조금 더 둔다. (옛 Material 기본 Short = 4초보다 짧다) */
    const val IMPORTANT_MS = 3_000L

    /** 이 낱말이 들어 있으면 **실패·주의** 로 본다. 서버 응답 문구가 아니라 우리가 적는 안내문 기준. */
    private val importantWords = listOf("못", "실패", "안 돼", "안돼", "다시", "오류", "없어요")

    fun isImportant(message: String): Boolean = importantWords.any { message.contains(it) }

    /**
     * @param message   띠에 적힐 말
     * @param hasAction 「되돌리기」 같은 누를 것이 붙어 있나
     */
    fun snackbarDurationMs(message: String, hasAction: Boolean = false): Long =
        if (hasAction || isImportant(message)) IMPORTANT_MS else BRIEF_MS
}
