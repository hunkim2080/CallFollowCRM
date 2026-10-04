package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.httpCodeOf
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/**
 * 협업 해제/취소. targetKey = shareId. payload: {mode}. (TERMINAL — 같은 현장 앞선 명령은 enqueue 때 지워짐)
 *   mode = "end_owner"  : A(주인)가 해제         → endCollab(asOwner=true)
 *          "end_partner": B(협업자)가 그만두기    → endCollab(asOwner=false)
 *          "cancel_then_end": 요청 취소, 안 되면 해제 → cancel → (서버가 거절하면) endCollab(asOwner=true)
 *   409(이미 종료)는 OutboxRules 가 DONE 으로 본다(끝난 것).
 */
class CollabEndHandler(
    private val repo: SharedSiteRepository
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_END

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val mode = JSONObject(payloadJson).optString("mode", "end_owner")
        return when (mode) {
            "end_partner" -> repo.endCollab(targetKey, ownerPhone, asOwner = false).toVerdict(kind)
            "cancel_then_end" -> {
                val c = repo.cancel(targetKey, ownerPhone)
                if (c.isSuccess) return Verdict.DONE to 200
                // 네트워크 실패면 통째로 재시도(end 도 어차피 안 간다). 서버가 거절(4xx)이면 해제로.
                val cCode = httpCodeOf(c.exceptionOrNull())
                    ?: return Verdict.RETRY to null
                repo.endCollab(targetKey, ownerPhone, asOwner = true).toVerdict(kind)
            }
            else -> repo.endCollab(targetKey, ownerPhone, asOwner = true).toVerdict(kind)
        }
    }
}
