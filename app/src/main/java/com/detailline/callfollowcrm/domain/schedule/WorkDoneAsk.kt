package com.detailline.callfollowcrm.domain.schedule

import com.detailline.callfollowcrm.util.DateTimeUtils

/**
 * 🔨 **「시공일이 지났는데, 끝났나요?」를 언제 물을지.** (2026-09-28 사장님)
 *
 * 사장님 말: "예약일이 지난 건 완료보다, 고객 문자 창에 「시공일이 지났는데 완료된 고객이냐」
 *            이런 식으로 **고를 수 있게** 하는 게 어떨까? 지금 고객한테 입금이란 단어가
 *            나오면 나한테 묻게 되어 있잖아 그런 식으로 말야"
 *
 * 왜 묻나 — 전엔 「이 시공 끝났나?」에 앱이 **세 가지로 답했다**:
 *   · 고객 딱지        → 예약일이 지났으면 끝난 걸로 **짐작**
 *   · 정산 화면        → 돈만 봄
 *   · 미수 카드·알림   → [완료]를 눌러야 끝난 것
 * 시공이 미뤄졌는데 날짜를 안 고치면 딱지는 「끝났다」는데 알림은 조용했다.
 * 짐작을 없애고 **사장님이 고르시게** 한다.
 *
 * ⚠️ 틀리면 어느 쪽으로 틀리는가 — 「입금했습니다」 카드와 같은 저울이다.
 *   못 물어보면 손해가 없다(예전처럼 홈에서 [완료]를 누르면 된다).
 *   **잘못 찍으면 안 한 일이 「끝난 일」이 되고, 손님에게 잔금 독촉이 나간다.**
 *
 * Android 의존성 없음 → 폰에 그런 손님이 없어도 매 빌드가 검사한다([WorkDoneAskTest]).
 */
object WorkDoneAsk {

    /**
     * 물어볼 **시공 마지막 날**(0시 ms). 물을 일이 없으면 null.
     *
     * @param scheduledWorkDays 여러 날 공사면 2 이상. **끝나는 날**을 지나야 묻는다 —
     *        첫날 밤에 물으면 사장님이 아직 현장에 계신다.
     * @param alreadyAsked 「아직이에요」 하신 마지막 날들. 날짜를 새로 잡으면 키가 바뀌어 다시 묻는다.
     */
    fun lastDayToAsk(
        scheduledWorkDate: Long?,
        scheduledWorkDays: Int,
        workCompletedAt: Long?,
        balancePaidAt: Long?,
        alreadyAsked: Set<Long>,
        todayStartMs: Long
    ): Long? {
        val day = scheduledWorkDate?.takeIf { it > 0L } ?: return null
        // 이미 끝난 일은 물을 이유가 없다. 잔금까지 받았으면 당연히 끝난 것이다.
        if (workCompletedAt != null || balancePaidAt != null) return null
        val lastDay = DateTimeUtils.startOfDay(day) +
            (scheduledWorkDays.coerceAtLeast(1) - 1) * DateTimeUtils.DAY_MS
        if (lastDay >= todayStartMs) return null      // 아직 안 지났다(오늘 포함 — 오늘은 안 묻는다)
        if (lastDay in alreadyAsked) return null      // 「아직이에요」 하신 날
        return lastDay
    }
}
