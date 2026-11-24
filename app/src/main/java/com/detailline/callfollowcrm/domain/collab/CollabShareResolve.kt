package com.detailline.callfollowcrm.domain.collab

/**
 * 🧩 **빈 shareId 채우기** — 로컬 협업 배정 줄에 shareId 가 안 적혀 있으면(옛 기록·받은 협업),
 *   서버 이벤트(ownerEvents)에서 **같은 현장 제목 + 같은 상호**인 것을 찾아 실제 shareId 로 채운다.
 *
 * 왜: 「현장 사진」 토글은 shareId 가 있어야 협업 사진을 불러오는데, 빈 줄이면 토글에 안 떴다.
 *   아래 협업 카드(CollabAfterCard)는 이미 같은 방식으로 빈 shareId 를 서버에서 채워 사진을 띄운다 —
 *   **그 매칭을 한 곳으로** 모아 토글도 같은 걸 쓰게 한다. (2026-11-25 사장님 "토글에도 떠야")
 *
 * ⚠️ 이미 shareId 가 있으면 **그대로 둔다**(정상 건은 안 건드림). 못 찾으면 빈 채로 둔다.
 */
object CollabShareResolve {
    data class EventLite(val shareId: String, val title: String, val partnerName: String, val atMs: Long)

    /** 이 고객의 협업 배정 줄 — 같은 사장(shareId/번호) 하나로 추리고, 빈 shareId 는 [fill] 로 채워 돌려준다. */
    fun resolveForCustomer(
        rawAssignments: Collection<String>, customerId: Long, siteTitle: String, events: List<EventLite>
    ): List<CollabAssignmentLine> =
        rawAssignments.mapNotNull { CollabAssignmentLine.parse(it) }
            .filter { it.customerId == customerId }
            .distinctBy { it.shareId.ifBlank { it.phone } }
            .map { it.copy(shareId = fill(it.name, it.shareId, siteTitle, events)) }

    /** [shareId] 가 비면 [events] 에서 (제목=siteTitle · 상호=name) 최신 것의 shareId 로 채워 돌려준다. */
    fun fill(name: String, shareId: String, siteTitle: String, events: List<EventLite>): String {
        if (shareId.isNotBlank()) return shareId
        if (siteTitle.isBlank()) return ""
        return events
            .filter {
                it.shareId.isNotBlank() && it.title == siteTitle &&
                    (name.isBlank() || name == "협업 사장님" || it.partnerName == name)
            }
            .maxByOrNull { it.atMs }
            ?.shareId
            .orEmpty()
    }
}
