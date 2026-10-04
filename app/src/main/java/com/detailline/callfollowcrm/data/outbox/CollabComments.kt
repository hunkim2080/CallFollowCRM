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
    // ⚠️ 순서 중요: **우체통(대기)을 먼저** 읽고 서버를 읽는다 — 보내는 사이에 행이 사라져도
    //   방금 쓴 댓글이 한 박자 사라졌다 다시 뜨는 깜빡임을 줄인다. (Fable #5)
    val pending = outbox.pendingFor(OutboxKind.COLLAB_COMMENT, shareId).mapNotNull { parsePendingComment(it) }
    val server = repo.comments(shareId, myPhone).getOrNull() ?: fallback.filter { !it.pending }
    // 그새 전송돼 서버에 이미 뜬 댓글은 pending 에서 뺀다(같은 글이 두 줄 되는 것 방지). 서버에 아직 없으면 「보내는 중」 유지.
    val serverKeys = server.map { it.authorPhone + "" + it.body }.toHashSet()
    val stillPending = pending.filter { (it.authorPhone + "" + it.body) !in serverKeys }
    return server + stillPending
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
