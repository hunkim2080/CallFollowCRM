package com.detailline.callfollowcrm.data.outbox

import com.detailline.callfollowcrm.ai.SharedSiteRepository
import com.detailline.callfollowcrm.data.local.entity.OutboxEntity
import com.detailline.callfollowcrm.domain.outbox.OutboxKind
import com.detailline.callfollowcrm.util.PhoneKey
import org.json.JSONObject

/**
 * 📮 협업 댓글 = **서버 댓글 + 우체통 대기(「보내는 중」)**. 한 곳(§12) — 화면 두 곳(SharedSiteScreen·고객 협업탭)이 같이 쓴다.
 *   보낸 건 서버에 있고, 아직 못 보낸 건 우체통(pending)에 있다 → 둘을 합쳐 보여준다.
 */
suspend fun loadCollabCommentsWithPending(
    repo: SharedSiteRepository,
    outbox: Outbox,
    shareId: String,
    myPhone: String,
    fallback: List<SharedSiteRepository.SiteComment> = emptyList()
): List<SharedSiteRepository.SiteComment> {
    val server = repo.comments(shareId, myPhone).getOrDefault(fallback.filter { !it.pending })
    val pending = outbox.pendingFor(OutboxKind.COLLAB_COMMENT, shareId).mapNotNull { parsePendingComment(it) }
    return server + pending
}

/** 우체통 대기 행 → 「보내는 중」 댓글. id 는 음수(서버 id 와 안 겹치게). */
fun parsePendingComment(e: OutboxEntity): SharedSiteRepository.SiteComment? = runCatching {
    val o = JSONObject(e.payloadJson)
    SharedSiteRepository.SiteComment(
        id = -e.id,
        authorPhone = PhoneKey.of(o.optString("authorPhone")),
        authorName = o.optString("authorName").ifBlank { "사장님" },
        body = o.optString("body"),
        createdAtMs = e.createdAtMs,
        pending = true
    )
}.getOrNull()
