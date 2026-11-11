package com.detailline.callfollowcrm.service

import com.detailline.callfollowcrm.CallFollowCrmApplication
import kotlinx.coroutines.delay

/**
 * 📮 문자(SMS·LMS) 발송 **실패 시 기록 되돌리기** — [SmsSentReceiver]·[MmsSentReceiver] 공용.
 *
 * 「보냄」 기록을 「실패」로 되돌려 24시간 쿨다운을 풀어 **다음에 다시 보내게** 한다.
 *   (2026-09-28 SMS 에서 시작 — 2026-11-11 장문을 LMS(텍스트 MMS)로 바꾸며 같은 안전망을 LMS 에도.)
 *
 * ⏱ 실패 통보가 기록 저장보다 **먼저** 올 수 있어(비행기 모드 등 즉시 실패) 잠깐씩 쉬며 몇 번 더 본다.
 *   goAsync 가 주는 10초 안에서 끝낸다.
 */
object TextSendFailure {
    /** 기록은 발송 **뒤**에 적힌다. 시계가 살짝 어긋나도 놓치지 않을 만큼만 앞을 본다. */
    private const val CLOCK_SLACK_MS = 5_000L
    /** 기록이 아직 안 저장됐을 수 있어 몇 번 더 본다. 3회 × 1.5초 = 3초 — goAsync 10초 안. */
    private const val FLIP_TRIES = 3
    private const val FLIP_WAIT_MS = 1_500L

    /** 「보냄 → 실패」로 되돌렸으면 true. */
    suspend fun rollbackLastSend(
        app: CallFollowCrmApplication?,
        phone: String,
        sentAt: Long,
        body: String,
    ): Boolean {
        var flipped = false
        repeat(FLIP_TRIES) { attempt ->
            if (flipped) return@repeat
            if (attempt > 0) delay(FLIP_WAIT_MS)
            flipped = runCatching {
                app?.container?.messageHistoryRepository?.markLastSendFailed(
                    phone, sentAt - CLOCK_SLACK_MS, body
                )
            }.getOrNull() == true
        }
        return flipped
    }
}
