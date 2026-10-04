package com.detailline.callfollowcrm.data.outbox.handlers

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.outbox.OutboxHandler
import com.detailline.callfollowcrm.data.outbox.toVerdict
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.domain.outbox.Verdict
import org.json.JSONObject

/**
 * 협업 현장 한 줄 댓글. targetKey = shareId. APPEND — 쌓인다.
 *   payload: {authorPhone, authorName?, body}. 화면은 보내기 전까지 「보내는 중」으로 보여준다(2단계·사장님 「바로 보이고」).
 *   opKey(X-Op-Key) 로 재전송해도 서버가 한 번만 → 댓글 두 줄 안 생긴다(§5·§42).
 */
class CollabCommentHandler(
    private val repo: SharedSiteRepository
) : OutboxHandler {
    override val kind = OutboxKind.COLLAB_COMMENT

    override suspend fun send(
        payloadJson: String, ownerPhone: String, targetKey: String, opKey: String
    ): Pair<Verdict, Int?> {
        val o = JSONObject(payloadJson)
        val authorPhone = o.optString("authorPhone")
        val body = o.optString("body")
        if (authorPhone.isBlank() || body.isBlank()) return Verdict.DEAD to 400
        return repo.postComment(
            shareId = targetKey,
            authorPhone = authorPhone,
            authorName = o.optString("authorName").takeIf { it.isNotBlank() },
            body = body,
            opKey = opKey
        ).toVerdict(kind)
    }
}
