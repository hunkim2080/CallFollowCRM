package com.detailline.callfollowcrm.presentation.screen.customer

import com.detailline.callfollowcrm.data.AppContainer
import com.detailline.callfollowcrm.domain.collab.CollabAssignmentLine
import com.detailline.callfollowcrm.domain.collab.CollabTogether
import com.detailline.callfollowcrm.util.PhoneKey

/**
 * 🤝 고객상세의 협업 판정 **세 상태** — 보통 고객 화면을 깜빡 보였다가 홱 바뀌는 걸 막으려고.
 *   [Loading] 서버 답 기다리는 중 · [Partner] 협업 사장(전용 화면) · [NotPartner] 보통 고객(보통 화면).
 *   「기억된 협업 사장」이면 Loading 동안 보통 화면 대신 "불러오는 중"을 보인다(화면 쪽에서). (2026-10-27 사장님)
 */
sealed interface CollabPartnerUi {
    data object Loading : CollabPartnerUi
    data object NotPartner : CollabPartnerUi
    data class Partner(val view: CollabTogether.View) : CollabPartnerUi
}

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
    val view = CollabTogether.build(received, given)
    if (view.isPartner && custKey.isNotBlank()) {
        // 캐시에 저장하고 "협업 사장"으로 기억한다. **빈 답이면 아무것도 안 지운다** — 협업 사장은 보통 고객이 안 된다(사장님).
        CollabSitesCache.save(container.preferences, custKey, view.receivedSites, view.givenSites)
        val known = container.preferences.knownCollabPartners
        if (custKey !in known) container.preferences.knownCollabPartners = known + custKey
    }
    return view.takeIf { it.isPartner }
}

/**
 * 🤝 고객상세의 협업 판정 흐름 — **캐시 먼저(바로), 그다음 서버로 갱신.** VM 은 이걸 그대로 쓴다(VM 안 키우려고 §12-F).
 *   · 캐시가 있으면 [CollabPartnerUi.Partner](캐시)를 바로 → 깜빡임·"불러오는 중" 없음.
 *   · 서버가 현장을 주면 그걸로 갱신. **빈 답이면 캐시 유지**(협업 사장은 보통 고객이 안 됨 — 사장님 2026-10-27).
 *   · 캐시도 없고 서버도 비면 [CollabPartnerUi.NotPartner](= 보통 고객).
 */
fun collabUiFlow(container: AppContainer, phone: String): kotlinx.coroutines.flow.Flow<CollabPartnerUi> =
    kotlinx.coroutines.flow.flow {
        val key = PhoneKey.of(phone)
        val cached = CollabSitesCache.load(container.preferences, key)
        emit(if (cached != null) CollabPartnerUi.Partner(cached) else CollabPartnerUi.Loading)
        val fresh = loadCollabView(container, phone)
        when {
            fresh != null -> emit(CollabPartnerUi.Partner(fresh))
            cached != null -> emit(CollabPartnerUi.Partner(cached))   // 서버 빈 답 — 캐시 유지(안 내림)
            else -> emit(CollabPartnerUi.NotPartner)
        }
    }
