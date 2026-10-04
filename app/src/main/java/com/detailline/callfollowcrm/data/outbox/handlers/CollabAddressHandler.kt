package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/** 협업 주소 변경 전파. targetKey = shareId. payload: {addr, label?}. (오늘 만든 /api/shared/update-address) */
class CollabAddressHandler(
    private val repo: SharedSiteRepository
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_ADDRESS

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val o = JSONObject(payloadJson)
        val addr = o.optString("addr")
        if (addr.isBlank()) return Verdict.DEAD to 400
        val label = o.optString("label").takeIf { it.isNotBlank() }
        return repo.updateAddress(targetKey, ownerPhone, addr, label).toVerdict(kind)
    }
}
