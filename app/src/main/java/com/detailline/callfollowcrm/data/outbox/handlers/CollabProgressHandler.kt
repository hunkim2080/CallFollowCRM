package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/**
 * 협업 진행(출발/도착/완료) 전파. targetKey = shareId. LATEST — 같은 현장 마지막 단계만 보낸다.
 *   payload: {step, partnerPhone, bank?, accountNo?, holder?, auto?, partnerName?}.
 *   opKey(X-Op-Key) 로 재전송해도 서버가 한 번만 → A 에게 완료 알림·카드 두 번 안 간다(§5·§41).
 */
class CollabProgressHandler(
    private val repo: SharedSiteRepository
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_PROGRESS

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val o = JSONObject(payloadJson)
        val step = runCatching {
            SharedSiteRepository.Progress.valueOf(o.optString("step").uppercase())
        }.getOrNull() ?: return Verdict.DEAD to 400
        val partnerPhone = o.optString("partnerPhone")
        if (partnerPhone.isBlank()) return Verdict.DEAD to 400
        return repo.progress(
            shareId = targetKey,
            partnerPhone = partnerPhone,
            step = step,
            bank = o.optString("bank").takeIf { it.isNotBlank() },
            accountNo = o.optString("accountNo").takeIf { it.isNotBlank() },
            holder = o.optString("holder").takeIf { it.isNotBlank() },
            auto = o.optBoolean("auto", false),
            partnerName = o.optString("partnerName").takeIf { it.isNotBlank() },
            opKey = opKey
        ).toVerdict(kind)
    }
}
