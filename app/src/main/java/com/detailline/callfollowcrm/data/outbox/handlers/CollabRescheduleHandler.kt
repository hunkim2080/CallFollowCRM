package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/** 협업 일정 변경 전파. targetKey = shareId. payload: {newAtMs, oldAtMs?, timeLabel?}. */
class CollabRescheduleHandler(
    private val repo: SharedSiteRepository
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_RESCHEDULE

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val o = JSONObject(payloadJson)
        val newAt = o.optLong("newAtMs")
        val oldAt = if (o.has("oldAtMs")) o.optLong("oldAtMs") else null
        val timeLabel = o.optString("timeLabel").takeIf { it.isNotBlank() }
        return repo.reschedule(targetKey, ownerPhone, newAt, oldAt, timeLabel).toVerdict(kind)
    }
}
