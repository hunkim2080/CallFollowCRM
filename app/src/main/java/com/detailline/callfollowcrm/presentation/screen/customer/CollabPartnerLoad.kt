package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine
import com.detailline.callfollowcrm.domain.collab.CollabTogether
import com.detailline.callfollowcrm.util.PhoneKey

/**
 * 🤝 이 고객이 **협업 사장**이면 그 양방향 함께한 현장([CollabTogether.View])을, 아니면 null.
 *   [CustomerDetailViewModel.collabView] 가 부른다 — VM(1,200줄대)을 안 키우려고 로딩만 여기로(§12-F).
 *   · received = `withMe` 에서 `ownerPhone` 를 고객 번호로 매칭(그 사장이 나한테 공유).
 *   · given = `byMe` 엔 번호가 없어(벽) `collabAssignments`(번호↔shareId)로 잇는다(내가 그 사장한테 공유).
 *   서버 실패면 빈 목록(graceful). 판정·묶기는 [CollabTogether] 한 곳.
 */
suspend fun loadCollabView(container: AppContainer, customerPhone: String): CollabTogether.View? {
    val myPhone = container.preferences.bizPhone.filter { it.isDigit() }
    if (myPhone.isBlank()) return null
    val custKey = PhoneKey.of(customerPhone)
    val received = container.sharedSiteRepository.withMe(myPhone).getOrDefault(emptyList())
        .filter { PhoneKey.of(it.ownerPhone) == custKey }
    val myShareIds = container.preferences.collabAssignments
        .mapNotNull { CollabAssignmentLine.parse(it) }
        .filter { PhoneKey.of(it.phone) == custKey }
        .map { it.shareId }.filter { it.isNotBlank() }.toSet()
    val given = if (myShareIds.isEmpty()) emptyList()
        else container.sharedSiteRepository.byMe(myPhone).getOrDefault(emptyList())
            .filter { it.shareId in myShareIds }
    return CollabTogether.build(received, given).takeIf { it.isPartner }
}
