package com.detailline.callfollowcrm.data.outbox

import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.OutboxRules
import com.detailline.callfollowcrm.domain.outbox.Verdict

/**
 * 종류별 **보내기**. payload 를 풀어 **기존 Repository 함수를 부르기만** 한다 — HTTP 는 Repository 에 그대로.
 *   설계 §6. 판정·백오프 셈은 하지 않는다(그건 OutboxRules).
 */
interface OutboxHandler {
    val kind: OutboxKind
    /** 보내고 (판정, HTTP코드?) 를 돌려준다. 코드 없음 = 네트워크 실패. */
    suspend fun send(payloadJson: String, ownerPhone: String, targetKey: String, opKey: String): Pair<Verdict, Int?>
}

/** 기존 Repository 의 `Result<Unit>`(실패 시 IOException("HTTP <code>")) 을 (판정, 코드) 로. */
internal fun Result<Unit>.toVerdict(kind: OutboxKind): Pair<Verdict, Int?> {
    if (isSuccess) return Verdict.DONE to 200
    val code = httpCodeOf(exceptionOrNull())
    return OutboxRules.verdict(kind, code) to code
}

/** 예외 메시지 "HTTP 404" 에서 코드만 뽑는다. 네트워크 예외(끊김·타임아웃)는 null → 재시도. */
internal fun httpCodeOf(t: Throwable?): Int? {
    val m = t?.message ?: return null
    return Regex("""HTTP (\d{3})""").find(m)?.groupValues?.get(1)?.toIntOrNull()
}
