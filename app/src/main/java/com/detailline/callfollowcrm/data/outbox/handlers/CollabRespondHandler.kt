package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/**
 * 🤝 받은 협업 요청 **수락/거절**. targetKey = shareId. payload: {accept}. (TERMINAL — 한 현장 마지막 결정만)
 *   ownerPhone = 내(수락자) 로그인 번호 = 서버가 보는 partner_phone.
 *   서버 POST /api/shared/respond. 만료(409 — 12h 지남)·이미 응답은 OutboxRules 가 DONE 으로 본다(끝난 것).
 */
class CollabRespondHandler(
    private val repo: SharedSiteRepository,
    private val bizName: () -> String
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_RESPOND

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val accept = JSONObject(payloadJson).optBoolean("accept", false)
        return repo.respond(
            targetKey, ownerPhone, accept,
            partnerName = bizName().trim().ifBlank { null }
        ).toVerdict(kind)
    }
}
